package com.bifel.voxreader.domain.repository

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.LibraryState
import com.bifel.voxreader.domain.model.ReaderSettings
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.StoredBook
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun importBook(sourceUri: String): BookDocument
    suspend fun loadBook(book: StoredBook): BookDocument
}

interface LibraryRepository {
    val library: Flow<LibraryState>

    suspend fun currentLibrary(): LibraryState
    suspend fun saveImportedBook(book: BookDocument)
    suspend fun markOpened(bookId: String)
    suspend fun updateReadingPosition(bookId: String, pageStart: Int, pageEnd: Int)
    suspend fun updateVoiceOverride(bookId: String, profile: CharacterProfile)
}

interface SettingsRepository {
    val settings: Flow<ReaderSettings>

    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setSpeechControlsEnabled(enabled: Boolean)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setReadingAppearance(fontSizeSp: Float, lineHeightSp: Float, horizontalMarginDp: Float)
    suspend fun setSpeechEngineMode(mode: SpeechEngineMode)
}
