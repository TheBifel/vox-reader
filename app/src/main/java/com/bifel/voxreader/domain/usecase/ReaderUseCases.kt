package com.bifel.voxreader.domain.usecase

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.BookRepository
import com.bifel.voxreader.domain.repository.LibraryRepository

class ImportBookUseCase(
    private val bookRepository: BookRepository,
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(sourceUri: String): BookDocument {
        val book = bookRepository.importBook(sourceUri)
        libraryRepository.saveImportedBook(book)
        return book
    }
}

class LoadLatestBookUseCase(
    private val bookRepository: BookRepository,
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(): Pair<BookDocument, StoredBook>? {
        val stored = libraryRepository.currentLibrary().latestBook ?: return null
        val document = bookRepository.loadBook(stored)
        libraryRepository.markOpened(stored.id)
        return document to stored
    }
}

