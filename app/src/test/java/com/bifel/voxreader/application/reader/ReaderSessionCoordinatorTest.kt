package com.bifel.voxreader.application.reader

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.LibraryState
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.ActiveSpeakerAnalysis
import com.bifel.voxreader.domain.repository.AnalysisImportResult
import com.bifel.voxreader.domain.repository.BookRepository
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SpeakerAnalysisRepository
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderSessionCoordinatorTest {
    @Test
    fun `session projects persisted voice overrides and always exposes narrator`() {
        val override = CharacterProfile(id = "alice", displayName = "Alice", pitch = 0.8f)
        val analysis = ActiveSpeakerAnalysis(
            phrases = emptyList(),
            characters = listOf(CharacterProfile(id = "alice", displayName = "Alice")),
            cachedPhraseIndices = emptySet(),
            emotionsByPhraseIndex = emptyMap(),
            providerId = "test",
        )
        val session = ReaderBookSession(
            document = BOOK,
            storedBook = STORED.copy(voiceOverrides = listOf(override)),
            analysis = analysis,
        )

        assertEquals(0.8f, session.characters.first { it.id == "alice" }.pitch)
        assertNotNull(session.characters.firstOrNull { it.id == NARRATOR_ID })
    }

    @Test
    fun `voice override is persisted once and returned in updated session`() = runBlocking {
        val library = FakeLibraryRepository()
        val coordinator = coordinator(library)
        val profile = CharacterProfile(id = "alice", displayName = "Alice", speechRate = 1.2f)

        val updated = coordinator.updateVoiceOverride(ReaderBookSession(BOOK, STORED), profile)

        assertEquals(listOf(BOOK.id to profile), library.voiceUpdates)
        assertEquals(profile, updated.storedBook.voiceOverrides.single())
    }

    private fun coordinator(library: FakeLibraryRepository): ReaderSessionCoordinator {
        val books = object : BookRepository {
            override suspend fun importBook(sourceUri: String) = BOOK
            override suspend fun loadBook(book: StoredBook) = BOOK
        }
        return ReaderSessionCoordinator(
            importBook = ImportBookUseCase(books, library),
            loadLatestBook = LoadLatestBookUseCase(books, library),
            libraryRepository = library,
            analysisRepository = object : SpeakerAnalysisRepository {
                override suspend fun clearBook(bookId: String) = Unit
                override suspend fun import(book: BookDocument, content: String) =
                    AnalysisImportResult("test", 0, 0, 0)
                override suspend fun active(
                    book: BookDocument,
                    fallbackPhrases: () -> List<com.bifel.voxreader.domain.model.SpeechPhrase>,
                ) = ActiveSpeakerAnalysis.empty(fallbackPhrases())
            },
        )
    }

    private companion object {
        val BOOK = BookDocument("book", "Book", "book.epub", "Text")
        val STORED = StoredBook("book", "Book", "book.epub")
    }
}

private class FakeLibraryRepository : LibraryRepository {
    val voiceUpdates = mutableListOf<Pair<String, CharacterProfile>>()
    override suspend fun currentLibrary() = LibraryState()
    override suspend fun saveImportedBook(book: BookDocument) = Unit
    override suspend fun markOpened(bookId: String) = Unit
    override suspend fun updateReadingPosition(bookId: String, pageStart: Int, pageEnd: Int) = Unit
    override suspend fun updateVoiceOverride(bookId: String, profile: CharacterProfile) {
        voiceUpdates += bookId to profile
    }
}
