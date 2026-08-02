package com.bifel.voxreader.domain.repository

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.LibraryState
import com.bifel.voxreader.domain.model.ReaderSettings
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.StoredBook
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun importBook(sourceUri: String): BookDocument
    suspend fun loadBook(book: StoredBook): BookDocument
}

interface LibraryRepository {
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
    suspend fun setReadingAppearance(fontSizeSp: Float, lineHeightSp: Float, horizontalMarginDp: Float, topPaddingDp: Float, bottomPaddingDp: Float)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setTypography(fontFamily: ReaderFontFamily, alignment: ReaderTextAlignment, paragraphSpacingSp: Float, firstLineIndentSp: Float)
    suspend fun setDisplayBehavior(orientation: ReaderOrientation, keepScreenOn: Boolean)
    suspend fun setImageAppearance(size: ReaderImageSize, alignment: ReaderImageAlignment)
    suspend fun setReaderColors(enabled: Boolean, background: Int, text: Int, link: Int, heading: Int, highlight: Int)
    suspend fun setSleepTimerSettings(checkMinutes: Int, graceSeconds: Int)
    suspend fun setSpeechEngineMode(mode: SpeechEngineMode)
}
