package com.bifel.voxreader.domain.repository

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechPhrase

data class AnalysisImportResult(
    val providerId: String,
    val totalPhrases: Int,
    val attributedPhrases: Int,
    val characterCount: Int,
)

data class ActiveSpeakerAnalysis(
    val phrases: List<SpeechPhrase>,
    val characters: List<CharacterProfile>,
    val cachedPhraseIndices: Set<Int>,
    val emotionsByPhraseIndex: Map<Int, PhraseEmotion>,
    val providerId: String?,
) {
    companion object {
        fun empty(phrases: List<SpeechPhrase>) = ActiveSpeakerAnalysis(
            phrases = phrases,
            characters = emptyList(),
            cachedPhraseIndices = emptySet(),
            emotionsByPhraseIndex = emptyMap(),
            providerId = null,
        )
    }
}

/** Import/read boundary shared by manual sidecars and future analysis producers. */
interface SpeakerAnalysisRepository {
    suspend fun clearBook(bookId: String)
    suspend fun import(book: BookDocument, content: String): AnalysisImportResult
    suspend fun active(
        book: BookDocument,
        fallbackPhrases: () -> List<SpeechPhrase>,
    ): ActiveSpeakerAnalysis
}
