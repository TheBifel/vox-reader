package com.bifel.voxreader.domain.preanalysis.sidecar

import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.preanalysis.PhraseManifestBuilder
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunId
import com.bifel.voxreader.domain.preanalysis.model.AttributionSource
import com.bifel.voxreader.domain.preanalysis.model.EvidenceKind
import com.bifel.voxreader.domain.model.VoiceGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSidecarTsvTest {

    @Test
    fun `round trips escaped book text and speaker metadata`() {
        val phrase = SpeechPhrase(
            index = 0,
            start = 0,
            end = 22,
            text = "\u201cHello\tthere.\\Again\u201d\n",
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

        val decoded = AnalysisSidecarTsv.decode(AnalysisSidecarTsv.encode(bundle))

        assertEquals(bundle, decoded)
        assertTrue(
            decoded.validateAgainst(
                bookId = "book-id",
                manifest = manifest,
                textByPhraseId = mapOf(manifest.single().phraseId to phrase.text),
            ).isEmpty(),
        )
        val result = decoded.toProviderResult(AnalysisRunId("run"))
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
            manifest = manifest,
            textByPhraseId = mapOf(manifest.single().phraseId to phrase.text),
        )

        assertTrue(errors.any { "different book" in it })
    }
}
