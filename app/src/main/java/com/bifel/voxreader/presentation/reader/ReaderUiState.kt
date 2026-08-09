package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.data.speech.PiperVoicePack
import com.bifel.voxreader.data.speech.AdvancedVoice
import com.bifel.voxreader.data.speech.AdvancedVoiceModel
import com.bifel.voxreader.domain.speech.ProceduralVoice

enum class PlaybackState {
    DISABLED,
    PAUSED,
    PREPARING,
    PLAYING,
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookDocument? = null,
    val phrases: List<SpeechPhrase> = emptyList(),
    val characters: List<CharacterProfile> = emptyList(),
    val pageStart: Int = 0,
    val pageEnd: Int = 0,
    val darkTheme: Boolean = true,
    val speechControlsEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val fontSizeSp: Float = 20f,
    val lineHeightSp: Float = 31f,
    val horizontalMarginDp: Float = 24f,
    val topPaddingDp: Float = 22f,
    val bottomPaddingDp: Float = 22f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.LEFT,
    val paragraphSpacingSp: Float = 0f,
    val firstLineIndentSp: Float = 0f,
    val orientation: ReaderOrientation = ReaderOrientation.SYSTEM,
    val keepScreenOn: Boolean = false,
    val imageSize: ReaderImageSize = ReaderImageSize.FIT_WIDTH,
    val imageAlignment: ReaderImageAlignment = ReaderImageAlignment.CENTER,
    val customReaderColors: Boolean = false,
    val readerBackgroundColor: Int = 0xFF090807.toInt(),
    val readerTextColor: Int = 0xFFF4E9D6.toInt(),
    val readerLinkColor: Int = 0xFF78A9FF.toInt(),
    val readerHeadingColor: Int = 0xFFD8A35D.toInt(),
    val readerHighlightColor: Int = 0x66D79A47,
    val sleepCheckMinutes: Int = 5,
    val sleepGraceSeconds: Int = 30,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerWarning: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.PAGINATED,
    val speechEngineMode: SpeechEngineMode = SpeechEngineMode.PROCEDURAL,
    val speechPostProcessing: SpeechPostProcessingSettings = SpeechPostProcessingSettings(),
    val localGenerativeVoiceAvailable: Boolean = false,
    val localVoicePacks: List<PiperVoicePack> = emptyList(),
    val advancedVoiceModel: AdvancedVoiceModel? = null,
    val advancedVoices: List<AdvancedVoice> = emptyList(),
    val advancedVoiceAvailable: Boolean = false,
    val proceduralVoices: List<ProceduralVoice> = emptyList(),
    val chromeVisible: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.DISABLED,
    val activePhraseIndex: Int? = null,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null,
    val cachedSpeakerPhraseIndices: Set<Int> = emptySet(),
    val phraseEmotions: Map<Int, PhraseEmotion> = emptyMap(),
    val cachedAttributionProvider: String? = null,
    val cachedAttributionCoverage: Float = 0f,
    val analysisMessage: String? = null,
    val errorMessage: String? = null,
) {
    val readingProgress: Float
        get() {
            val length = book?.text?.length ?: return 0f
            if (length == 0) return 0f
            return pageStart.toFloat().div(length).coerceIn(0f, 1f)
        }
}
