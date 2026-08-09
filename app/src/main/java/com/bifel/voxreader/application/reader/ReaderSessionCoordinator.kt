package com.bifel.voxreader.application.reader

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.ActiveSpeakerAnalysis
import com.bifel.voxreader.domain.repository.AnalysisImportResult
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SpeakerAnalysisRepository
import com.bifel.voxreader.domain.text.SentenceSegmenter
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReaderBookSession(
    val document: BookDocument,
    val storedBook: StoredBook,
    val analysis: ActiveSpeakerAnalysis? = null,
) {
    val characters: List<CharacterProfile>
        get() = applyVoiceOverrides(
            analysis?.characters.orEmpty().ifEmpty { listOf(NarratorProfile) },
            storedBook.voiceOverrides,
        )
}

data class ImportedReaderAnalysis(
    val result: AnalysisImportResult,
    val session: ReaderBookSession,
)

/** Owns the reader's book, attribution, and per-book voice projection workflow. */
class ReaderSessionCoordinator(
    private val importBook: ImportBookUseCase,
    private val loadLatestBook: LoadLatestBookUseCase,
    private val libraryRepository: LibraryRepository,
    private val analysisRepository: SpeakerAnalysisRepository,
) {

    suspend fun open(sourceUri: String): ReaderBookSession {
        val document = importBook(sourceUri)
        analysisRepository.clearBook(document.id)
        val stored = libraryRepository.currentLibrary().books.first { it.id == document.id }
        return ReaderBookSession(document, stored)
    }

    suspend fun latest(): ReaderBookSession? =
        loadLatestBook()?.let { (document, stored) -> ReaderBookSession(document, stored) }

    suspend fun resolve(session: ReaderBookSession): ReaderBookSession = withContext(Dispatchers.Default) {
        session.copy(
            analysis = analysisRepository.active(session.document) {
                SentenceSegmenter.segment(session.document.text)
            },
        )
    }

    suspend fun importAnalysis(
        session: ReaderBookSession,
        content: String,
    ): ImportedReaderAnalysis = withContext(Dispatchers.Default) {
        val result = analysisRepository.import(session.document, content)
        ImportedReaderAnalysis(result, resolve(session))
    }

    suspend fun updateVoiceOverride(
        session: ReaderBookSession,
        profile: CharacterProfile,
    ): ReaderBookSession {
        libraryRepository.updateVoiceOverride(session.document.id, profile)
        return session.copy(
            storedBook = session.storedBook.copy(
                voiceOverrides = session.storedBook.voiceOverrides
                    .filterNot { it.id == profile.id } + profile,
            ),
        )
    }

    suspend fun updateReadingPosition(bookId: String, start: Int, end: Int) {
        libraryRepository.updateReadingPosition(bookId, start, end)
    }
}

private fun applyVoiceOverrides(
    profiles: List<CharacterProfile>,
    overrides: List<CharacterProfile>,
): List<CharacterProfile> {
    val overrideById = overrides.associateBy(CharacterProfile::id)
    val withNarrator = if (profiles.any { it.id == NarratorProfile.id }) profiles else profiles + NarratorProfile
    return withNarrator.map { profile ->
        val override = overrideById[profile.id] ?: return@map profile
        profile.copy(
            voiceGroup = override.voiceGroup,
            voiceSlot = override.voiceSlot,
            pitch = override.pitch,
            speechRate = override.speechRate,
            proceduralVoiceId = override.proceduralVoiceId,
            localVoicePackId = override.localVoicePackId,
            advancedVoiceId = override.advancedVoiceId,
        )
    }
}
