package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.domain.analysis.CharacterAnalysisRequest
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.text.SentenceSegmenter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedCharacterAnalyzerTest {

    @Test
    fun `attributes explicit dialogue and preserves narrator sentences`() = runTest {
        val text = "\"Stay here,\" Alice said. \"I will not,\" Bob replied. The door closed."

        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
            ),
        )

        assertEquals("alice", result.phrases.first { it.text.startsWith("\"Stay") }.speakerId)
        assertEquals("bob", result.phrases.first { it.text.startsWith("\"I will") }.speakerId)
        assertTrue(
            result.phrases
                .filterNot { it.text.startsWith('"') }
                .all { it.speakerId == NARRATOR_ID },
        )
        assertTrue(result.characters.any { it.id == "alice" })
        assertTrue(result.characters.any { it.id == "bob" })
        assertTrue(result.characters.filterNot { it.id == NARRATOR_ID }.all {
            it.voiceGroup == VoiceGroup.NEUTRAL
        })
    }

    @Test
    fun `keeps the same speaker inside a multi sentence quotation`() = runTest {
        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = "\"First sentence. Second sentence,\" Alice said.",
                knownCharacters = emptyList(),
            ),
        )

        assertEquals(3, result.phrases.size)
        assertEquals("alice", result.phrases[0].speakerId)
        assertEquals("alice", result.phrases[1].speakerId)
        assertEquals(NARRATOR_ID, result.phrases[2].speakerId)
    }

    @Test
    fun `focused analysis updates only the requested existing phrase`() = runTest {
        val text = "The door opened. \"Come in,\" Alice said."
        val existing = SentenceSegmenter.segment(text)

        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
                existingPhrases = existing,
                focusPosition = text.indexOf('"'),
            ),
        )

        assertEquals(1..1, result.analyzedPhraseRange)
        assertEquals(NARRATOR_ID, result.phrases[0].speakerId)
        assertEquals("alice", result.phrases[1].speakerId)
    }

    @Test
    fun `new quote in the same paragraph inherits the previous speaker`() = runTest {
        val text = "\"Hello,\" Alice said. \"Still me.\""
        val initial = SentenceSegmenter.segment(text)
        val first = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
                existingPhrases = initial,
            ),
        )
        val secondPhrase = first.phrases.last()

        val second = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = first.characters,
                existingPhrases = first.phrases,
                focusPosition = secondPhrase.start,
            ),
        )

        assertEquals("alice", second.phrases.last().speakerId)
    }

    @Test
    fun `focused analysis keeps speaker for sentence without quote mark inside open quotation`() =
        runTest {
            val text = "\"First sentence. Second sentence. Third sentence,\" Alice said."
            val initial = SentenceSegmenter.segment(text)
            val first = RuleBasedCharacterAnalyzer().analyze(
                CharacterAnalysisRequest(
                    text = text,
                    knownCharacters = emptyList(),
                    existingPhrases = initial,
                    focusPosition = initial[0].start,
                ),
            )
            val second = RuleBasedCharacterAnalyzer().analyze(
                CharacterAnalysisRequest(
                    text = text,
                    knownCharacters = first.characters,
                    existingPhrases = first.phrases,
                    focusPosition = initial[1].start,
                ),
            )

            assertEquals("alice", first.phrases[0].speakerId)
            assertEquals("alice", second.phrases[1].speakerId)
        }

    @Test
    fun `untagged quote after an explicit tag keeps that speaker across a paragraph`() = runTest {
        val text = "\"First,\" Alice said.\n\n\"Still Alice.\""
        val initial = SentenceSegmenter.segment(text)
        val first = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
                existingPhrases = initial,
                focusPosition = initial[0].start,
            ),
        )
        val second = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = first.characters,
                existingPhrases = first.phrases,
                focusPosition = initial.last().start,
            ),
        )

        assertEquals("alice", second.phrases.last().speakerId)
    }

    @Test
    fun `named action beat assigns the following dialogue`() = runTest {
        val text = "\"Earlier,\" Alice said.\n\nAlice paused. \"Now?\""
        val initial = SentenceSegmenter.segment(text)
        val first = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = emptyList(),
                existingPhrases = initial,
                focusPosition = initial[0].start,
            ),
        )
        val target = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = first.characters,
                existingPhrases = first.phrases,
                focusPosition = initial.last().start,
            ),
        )

        assertEquals("alice", target.phrases.last().speakerId)
    }

    @Test
    fun `pronoun action beat selects the matching recent character voice`() = runTest {
        val text =
            "\"First,\" Alice said. \"Second,\" Bob replied.\n\nShe turned. \"Third.\""
        val initial = SentenceSegmenter.segment(text)
            .map { phrase ->
                phrase.copy(
                    speakerId = when {
                        phrase.text.startsWith("\"First") -> "alice"
                        phrase.text.startsWith("\"Second") -> "bob"
                        else -> NARRATOR_ID
                    },
                )
            }
        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(
                    profile("Alice", VoiceGroup.FEMALE),
                    profile("Bob", VoiceGroup.MALE),
                ),
                existingPhrases = initial,
                focusPosition = initial.last().start,
            ),
        )

        assertEquals("alice", result.phrases.last().speakerId)
    }

    @Test
    fun `silent listener reaction keeps the previous dialogue speaker`() = runTest {
        val text = "\"Question?\" Alice asked.\n\nHe shook his head. \"I will explain.\""
        val initial = SentenceSegmenter.segment(text)
            .map { phrase ->
                phrase.copy(
                    speakerId = if (phrase.text.startsWith("\"Question")) {
                        "alice"
                    } else {
                        NARRATOR_ID
                    },
                )
            }
        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = text,
                knownCharacters = listOf(
                    profile("Alice", VoiceGroup.FEMALE),
                    profile("Bob", VoiceGroup.MALE),
                ),
                existingPhrases = initial,
                focusPosition = initial.last().start,
            ),
        )

        assertEquals("alice", result.phrases.last().speakerId)
    }

    @Test
    fun `does not create a character from a lowercase word after said`() = runTest {
        val result = RuleBasedCharacterAnalyzer().analyze(
            CharacterAnalysisRequest(
                text = "\"Hello,\" she said with a smile.",
                knownCharacters = emptyList(),
            ),
        )

        assertEquals(listOf(NARRATOR_ID), result.characters.map { it.id })
    }

    private fun profile(name: String, group: VoiceGroup) = CharacterProfile(
        id = name.lowercase(),
        displayName = name,
        aliases = setOf(name),
        voiceGroup = group,
    )
}
