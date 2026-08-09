package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings

data class ReaderActions(
    val chrome: ReaderChromeActions,
    val reading: ReaderReadingActions,
    val playback: ReaderPlaybackActions,
    val voices: ReaderVoiceActions,
    val settings: ReaderSettingsActions,
    val analysis: ReaderAnalysisActions,
    val platform: ReaderPlatformActions,
)

data class ReaderChromeActions(
    val toggle: () -> Unit,
    val hide: () -> Unit,
    val dismissError: () -> Unit,
)

data class ReaderReadingActions(
    val pageChanged: (Int, Int, Boolean) -> Unit,
    val setMode: (ReadingMode) -> Unit,
    val historyBack: () -> Unit,
    val historyForward: () -> Unit,
    val openInternalLink: (Int) -> Unit,
    val registerUserActivity: () -> Unit,
    val loadImage: suspend (BookDocument, BookImage) -> ByteArray?,
)

data class ReaderPlaybackActions(
    val enable: () -> Unit,
    val disable: () -> Unit,
    val playPause: () -> Unit,
    val previousPhrase: () -> Unit,
    val nextPhrase: () -> Unit,
    val setSpeed: (Float) -> Unit,
    val toggleSleepTimer: () -> Unit,
)

data class ReaderVoiceActions(
    val setEngineMode: (SpeechEngineMode) -> Unit,
    val setPostProcessing: (SpeechPostProcessingSettings) -> Unit,
    val downloadLocal: (String) -> Unit,
    val removeLocal: (String) -> Unit,
    val previewLocal: (String) -> Unit,
    val downloadAdvanced: () -> Unit,
    val removeAdvanced: () -> Unit,
    val previewAdvanced: (String) -> Unit,
    val loadProcedural: () -> Unit,
    val previewProcedural: (String) -> Unit,
    val updateCharacter: (CharacterProfile) -> Unit,
    val previewCharacter: (CharacterProfile) -> Unit,
)

data class ReaderSettingsActions(
    val toggleTheme: () -> Unit,
    val setReadingAppearance: (Float, Float, Float, Float, Float) -> Unit,
    val setTypography: (ReaderFontFamily, ReaderTextAlignment, Float, Float) -> Unit,
    val setDisplayBehavior: (ReaderOrientation, Boolean) -> Unit,
    val setImageAppearance: (ReaderImageSize, ReaderImageAlignment) -> Unit,
    val setColors: (Boolean, Int, Int, Int, Int, Int) -> Unit,
    val setSleepTimer: (Int, Int) -> Unit,
)

data class ReaderAnalysisActions(
    val import: () -> Unit,
    val speakerAt: (Int) -> String?,
)

data class ReaderPlatformActions(
    val openBook: () -> Unit,
    val openExternalLink: (String) -> Unit,
)
