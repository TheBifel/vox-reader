package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.domain.analysis.CharacterAnalysisRequest
import com.bifel.voxreader.domain.analysis.SpeakerScorer
import com.bifel.voxreader.domain.model.CharacterAnalysisSource
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.text.SentenceSegmenter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyBertCharacterAnalyzerTest {

    @Test
    fun `explicit dialogue tags bypass the classifier`() = runTest {
        val scorer = RecordingScorer(mapOf("Alice" to 0.01f, "Bob" to 0.99f))
        val analyzer = analyzer(scorer)

        val result = analyzer.analyze(
            CharacterAnalysisRequest(
                text = "\"Wait,\" Alice said.",
                knownCharacters = emptyList(),
            ),
        )

        assertEquals(CharacterAnalysisSource.RULES, result.source)
        assertEquals("alice", result.phrases.first().speakerId)
        assertEquals(NARRATOR_ID, result.phrases.last().speakerId)
        assertEquals(0, scorer.callCount)
    }

    @Test
    fun `classifier selects a dynamic candidate for ambiguous dialogue`() = runTest {
        val text = """
            "First," Alice said. "Second," Bob replied.

            The room grew quiet.

            "Third."
        """.trimIndent()
        val phrases = SentenceSegmenter.segment(text)
        val scorer = RecordingScorer(mapOf("Alice" to 0.91f, "Bob" to 0.20f))
        val analyzer = analyzer(scorer)

        val result = analyzer.analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(profile("Alice"), profile("Bob")),
                existingPhrases = phrases,
                focusPosition = phrases.last().start,
            ),
        )

        assertEquals(CharacterAnalysisSource.TINY_BERT, result.source)
        assertEquals("alice", result.phrases.last().speakerId)
        assertEquals(1, scorer.callCount)
        assertTrue(scorer.scene.contains("Target dialogue:"))
    }

    @Test
    fun `low classifier confidence keeps the deterministic result`() = runTest {
        val text = "\"First,\" Alice said. \"Second,\" Bob replied. \"Third.\""
        val phrases = SentenceSegmenter.segment(text)
        val scorer = RecordingScorer(mapOf("Alice" to 0.65f, "Bob" to 0.60f))
        val analyzer = analyzer(scorer)

        val result = analyzer.analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(profile("Alice"), profile("Bob")),
                existingPhrases = phrases,
                focusPosition = phrases.last().start,
            ),
        )

        assertEquals(CharacterAnalysisSource.RULES, result.source)
    }

    @Test
    fun `adjacent explicit tag cannot be overridden by the classifier`() = runTest {
        val text = "\"First,\" Alice said.\n\n\"Still Alice.\""
        val phrases = SentenceSegmenter.segment(text)
            .map { phrase ->
                phrase.copy(
                    speakerId = if (phrase.text.startsWith("\"First")) {
                        "alice"
                    } else {
                        NARRATOR_ID
                    },
                )
            }
        val scorer = RecordingScorer(mapOf("Alice" to 0.01f, "Bob" to 0.99f))

        val result = analyzer(scorer).analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(profile("Alice"), profile("Bob")),
                existingPhrases = phrases,
                focusPosition = phrases.last().start,
            ),
        )

        assertEquals("alice", result.phrases.last().speakerId)
        assertEquals(0, scorer.callCount)
    }

    @Test
    fun `open quotation continuation reuses the resolved speaker`() = runTest {
        val text = "\"First sentence. Second sentence,\" Alice said."
        val scorer = RecordingScorer(mapOf("Alice" to 0.99f, "Bob" to 0.01f))
        val analyzer = analyzer(scorer)
        val first = analyzer.analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
            ),
        )

        val second = analyzer.analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = first.characters,
                existingPhrases = first.phrases,
                focusPosition = first.phrases[1].start,
                preservedPhraseRanges = listOf(0..0),
            ),
        )

        assertEquals("alice", second.phrases[1].speakerId)
        assertEquals(0, scorer.callCount)
    }

    @Test
    fun `book evidence corrects a previously wrong Lindon voice`() = runTest {
        val text = """
            Lindon tightened his pack. He walked on.

            Lindon checked his tools. He was ready.

            Lindon lifted his head. He heard a sound.

            "Ready," Lindon said.
        """.trimIndent()
        val saved = profile("Lindon").copy(
            voiceGroup = VoiceGroup.FEMALE,
            voiceSlot = 3,
            pitch = 1.1f,
        )
        val scorer = RecordingScorer(emptyMap())

        val result = analyzer(scorer).analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(saved),
                focusPosition = text.lastIndexOf('"'),
            ),
        )

        val lindon = result.characters.first { it.id == "lindon" }
        assertEquals(VoiceGroup.MALE, lindon.voiceGroup)
        assertTrue(lindon.pitch < 1f)
        assertEquals(0, scorer.callCount)
    }

    @Test
    fun `following subject pronoun grounds an explicitly tagged speaker`() = runTest {
        val text = "\"Won't need it,\" Yerin said. She rapped her knuckles on her arm."

        val result = analyzer(RecordingScorer(emptyMap())).analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
            ),
        )

        assertEquals(
            VoiceGroup.FEMALE,
            result.characters.first { it.id == "yerin" }.voiceGroup,
        )
    }

    @Test
    fun `classifier failure falls back without failing playback`() = runTest {
        val text = "\"First,\" Alice said. \"Second,\" Bob replied. \"Third.\""
        val phrases = SentenceSegmenter.segment(text)
        val analyzer = analyzer(
            object : SpeakerScorer {
                override suspend fun scoreCandidates(
                    scene: String,
                    candidates: List<String>,
                ): List<Float> = error("Inference failed")
            },
        )

        val result = analyzer.analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(profile("Alice"), profile("Bob")),
                existingPhrases = phrases,
                focusPosition = phrases.last().start,
            ),
        )

        assertEquals(CharacterAnalysisSource.RULES, result.source)
        assertTrue(result.phrases.last().speakerId.isNotBlank())
    }

    private fun analyzer(scorer: SpeakerScorer) = TinyBertCharacterAnalyzer(
        fallback = RuleBasedCharacterAnalyzer(),
        scorer = scorer,
    )

    private fun profile(name: String) = CharacterProfile(
        id = name.lowercase(),
        displayName = name,
        aliases = setOf(name),
    )

    private class RecordingScorer(
        private val scoresByCandidate: Map<String, Float>,
    ) : SpeakerScorer {
        lateinit var scene: String
        var callCount = 0

        override suspend fun scoreCandidates(
            scene: String,
            candidates: List<String>,
        ): List<Float> {
            callCount++
            this.scene = scene
            return candidates.map { scoresByCandidate[it] ?: 0f }
        }
    }
}
