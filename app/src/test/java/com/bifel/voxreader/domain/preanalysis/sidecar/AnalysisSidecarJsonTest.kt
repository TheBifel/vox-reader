package com.bifel.voxreader.domain.preanalysis.sidecar

import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.preanalysis.PhraseManifestBuilder
import com.bifel.voxreader.domain.preanalysis.model.AttributionSource
import com.bifel.voxreader.domain.preanalysis.model.EvidenceKind
import com.bifel.voxreader.domain.model.VoiceGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSidecarJsonTest {

    @Test
    fun `round trips escaped book text and speaker metadata`() {
        val phraseText = "\u201cHello\tthere.\\Again\u201d\n"
        val phrase = SpeechPhrase(
            index = 0,
            start = 0,
            end = phraseText.length,
            text = phraseText,
        )
        val manifest = PhraseManifestBuilder.build("book-id", listOf(phrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "book-id",
                providerId = "manual-test",
                createdAt = 1234L,
            ),
            rows = listOf(
                unknownSidecarRow(manifest.single(), phrase.text).copy(
                    speakerId = "alice",
                    speakerName = "Alice",
                    voiceGroup = VoiceGroup.FEMALE,
                    voiceSlot = 3,
                    pitch = 1.1f,
                    confidence = 0.95f,
                    evidence = EvidenceKind.CONTEXTUAL,
                    source = AttributionSource.PREANALYSIS_CACHE,
                ),
            ),
        )

        val encoded = AnalysisSidecarJson.encode(bundle)
        val decoded = AnalysisSidecarJson.decode(encoded)

        assertEquals(bundle, decoded)
        assertTrue(encoded.trimStart().startsWith("{"))
        assertTrue("\"phrases\"" in encoded)
        assertTrue(
            decoded.validateAgainst(
                bookId = "book-id",
                bookText = phrase.text,
            ).isEmpty(),
        )
        val result = decoded.toProviderResult()
        assertEquals("alice", result.attributions.single().characterId)
        assertEquals(VoiceGroup.FEMALE, result.characters.single().voiceGroup)
    }

    @Test
    fun `validation rejects a sidecar for another book`() {
        val phrase = SpeechPhrase(0, 0, 8, "\u201cHello.\u201d")
        val manifest = PhraseManifestBuilder.build("book-id", listOf(phrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "different-book",
                providerId = "manual-test",
                createdAt = 1234L,
            ),
            rows = listOf(unknownSidecarRow(manifest.single(), phrase.text)),
        )

        val errors = bundle.validateAgainst(
            bookId = "book-id",
            bookText = phrase.text,
        )

        assertTrue(errors.any { "different book" in it })
    }

    @Test
    fun `accepts a complete producer segmentation instead of requiring Android boundaries`() {
        val bookText = "One. Two."
        val producerPhrase = SpeechPhrase(0, 0, bookText.length, bookText)
        val manifest = PhraseManifestBuilder.build("book-id", listOf(producerPhrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "book-id",
                providerId = "desktop-test",
                createdAt = 1234L,
            ),
            rows = listOf(
                unknownSidecarRow(manifest.single(), producerPhrase.text).copy(
                    speakerId = "alice",
                    speakerName = "Alice",
                    voiceGroup = VoiceGroup.FEMALE,
                    source = AttributionSource.PREANALYSIS_CACHE,
                ),
            ),
        )

        assertTrue(bundle.validateAgainst("book-id", bookText).isEmpty())
        assertEquals(manifest, bundle.toManifest())
    }

    @Test
    fun `rejects a sidecar that leaves book text uncovered`() {
        val bookText = "One. Two."
        val partialPhrase = SpeechPhrase(0, 0, 4, "One.")
        val manifest = PhraseManifestBuilder.build("book-id", listOf(partialPhrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "book-id",
                providerId = "desktop-test",
                createdAt = 1234L,
            ),
            rows = listOf(unknownSidecarRow(manifest.single(), partialPhrase.text)),
        )

        val errors = bundle.validateAgainst("book-id", bookText)

        assertTrue(errors.any { "does not cover the end" in it })
    }

    @Test
    fun `keeps unknown rows as authoritative cached decisions`() {
        val phrase = SpeechPhrase(0, 0, 4, "One.")
        val manifest = PhraseManifestBuilder.build("book-id", listOf(phrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "book-id",
                providerId = "desktop-test",
                createdAt = 1234L,
            ),
            rows = listOf(unknownSidecarRow(manifest.single(), phrase.text)),
        )

        val result = bundle.toProviderResult()

        assertEquals(1, result.attributions.size)
        assertEquals("unknown", result.attributions.single().characterId)
        assertEquals("unknown", result.characters.single().id)
    }

    @Test
    fun `round trips optional emotion data`() {
        val phrase = SpeechPhrase(0, 0, 6, "\"Run!\"")
        val manifest = PhraseManifestBuilder.build("book-id", listOf(phrase))
        val bundle = AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = "book-id",
                providerId = "emotion-test",
                createdAt = 1234L,
            ),
            rows = listOf(
                unknownSidecarRow(manifest.single(), phrase.text).copy(
                    speakerId = "alice",
                    speakerName = "Alice",
                    voiceGroup = VoiceGroup.FEMALE,
                    source = AttributionSource.PREANALYSIS_CACHE,
                    emotion = PhraseEmotion(
                        type = EmotionType.FEAR,
                        intensity = 0.7f,
                        confidence = 0.9f,
                    ),
                ),
            ),
        )
        val withEmotion = AnalysisSidecarJson.encode(bundle)

        val decoded = AnalysisSidecarJson.decode(withEmotion)

        assertEquals(bundle, decoded)
        assertEquals(EmotionType.FEAR, decoded.rows.single().emotion?.type)
        assertTrue(decoded.validateAgainst("book-id", phrase.text).isEmpty())
    }
}
