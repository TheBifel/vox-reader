package com.bifel.voxreader.domain.preanalysis

import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunStatus
import com.bifel.voxreader.domain.preanalysis.model.canTransitionTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreAnalysisModelsTest {

    @Test
    fun `run state machine permits durable happy path and rejects skips`() {
        assertTrue(AnalysisRunStatus.QUEUED.canTransitionTo(AnalysisRunStatus.PREPARING))
        assertTrue(AnalysisRunStatus.PREPARING.canTransitionTo(AnalysisRunStatus.PROCESSING))
        assertTrue(AnalysisRunStatus.PROCESSING.canTransitionTo(AnalysisRunStatus.VALIDATING))
        assertTrue(AnalysisRunStatus.VALIDATING.canTransitionTo(AnalysisRunStatus.RECONCILING))
        assertTrue(AnalysisRunStatus.RECONCILING.canTransitionTo(AnalysisRunStatus.READY))

        assertFalse(AnalysisRunStatus.QUEUED.canTransitionTo(AnalysisRunStatus.READY))
        assertFalse(AnalysisRunStatus.READY.canTransitionTo(AnalysisRunStatus.PROCESSING))
        assertFalse(AnalysisRunStatus.CANCELED.canTransitionTo(AnalysisRunStatus.QUEUED))
    }

    @Test
    fun `phrase identity is stable for the same book and manifest`() {
        val phrases = listOf(
            SpeechPhrase(
                index = 0,
                start = 4,
                end = 12,
                text = "\u201cHello.\u201d",
            ),
        )

        val first = PhraseManifestBuilder.build("book-hash", phrases).single()
        val second = PhraseManifestBuilder.build("book-hash", phrases).single()
        val anotherBook = PhraseManifestBuilder.build("other-book", phrases).single()
        val changedOffset = PhraseManifestBuilder.build(
            "book-hash",
            phrases.map { it.copy(start = 5) },
        ).single()

        assertEquals(first, second)
        assertNotEquals(first.phraseId, anotherBook.phraseId)
        assertNotEquals(first.phraseId, changedOffset.phraseId)
    }
}
