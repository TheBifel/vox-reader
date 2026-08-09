package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.application.reader.PlaybackStatus
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.ReaderSettings
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.speech.VoiceModel
import com.bifel.voxreader.domain.speech.VoiceOption

typealias PlaybackState = PlaybackStatus

data class ReaderUiState(
    val session: ReaderSessionUiState = ReaderSessionUiState(),
    val settings: ReaderSettings = ReaderSettings(),
    val playback: ReaderPlaybackUiState = ReaderPlaybackUiState(),
    val voices: ReaderVoiceUiState = ReaderVoiceUiState(),
    val chrome: ReaderChromeUiState = ReaderChromeUiState(),
) {
    val isLoading get() = session.isLoading
    val book get() = session.book
    val phrases get() = session.phrases
    val characters get() = session.characters
    val pageStart get() = session.pageStart
    val pageEnd get() = session.pageEnd
    val cachedSpeakerPhraseIndices get() = session.cachedSpeakerPhraseIndices
    val phraseEmotions get() = session.phraseEmotions
    val cachedAttributionProvider get() = session.cachedAttributionProvider
    val analysisMessage get() = session.analysisMessage
    val errorMessage get() = session.errorMessage

    val darkTheme get() = settings.darkTheme
    val speechControlsEnabled get() = settings.speechControlsEnabled
    val playbackSpeed get() = settings.playbackSpeed
    val fontSizeSp get() = settings.fontSizeSp
    val lineHeightSp get() = settings.lineHeightSp
    val horizontalMarginDp get() = settings.horizontalMarginDp
    val topPaddingDp get() = settings.topPaddingDp
    val bottomPaddingDp get() = settings.bottomPaddingDp
    val fontFamily get() = settings.fontFamily
    val textAlignment get() = settings.textAlignment
    val paragraphSpacingSp get() = settings.paragraphSpacingSp
    val firstLineIndentSp get() = settings.firstLineIndentSp
    val orientation get() = settings.orientation
    val keepScreenOn get() = settings.keepScreenOn
    val imageSize get() = settings.imageSize
    val imageAlignment get() = settings.imageAlignment
    val customReaderColors get() = settings.customReaderColors
    val readerBackgroundColor get() = settings.readerBackgroundColor
    val readerTextColor get() = settings.readerTextColor
    val readerLinkColor get() = settings.readerLinkColor
    val readerHeadingColor get() = settings.readerHeadingColor
    val readerHighlightColor get() = settings.readerHighlightColor
    val sleepCheckMinutes get() = settings.sleepCheckMinutes
    val sleepGraceSeconds get() = settings.sleepGraceSeconds
    val readingMode get() = settings.readingMode
    val speechEngineMode get() = settings.speechEngineMode
    val speechPostProcessing get() = settings.speechPostProcessing

    val playbackState get() = playback.status
    val activePhraseIndex get() = playback.activePhraseIndex
    val highlightStart get() = playback.highlightStart
    val highlightEnd get() = playback.highlightEnd
    val sleepTimerEnabled get() = playback.sleepTimerEnabled
    val sleepTimerWarning get() = playback.sleepTimerWarning

    val localGenerativeVoiceAvailable get() = voices.localGenerativeAvailable
    val localVoicePacks get() = voices.localModels
    val advancedVoiceModel get() = voices.advancedModel
    val advancedVoices get() = voices.advancedVoices
    val advancedVoiceAvailable get() = voices.advancedAvailable
    val proceduralVoices get() = voices.proceduralVoices
    val chromeVisible get() = chrome.visible

    val readingProgress: Float
        get() {
            val length = book?.text?.length ?: return 0f
            if (length == 0) return 0f
            return pageStart.toFloat().div(length).coerceIn(0f, 1f)
        }
}

data class ReaderSessionUiState(
    val isLoading: Boolean = true,
    val book: BookDocument? = null,
    val phrases: List<SpeechPhrase> = emptyList(),
    val characters: List<CharacterProfile> = emptyList(),
    val pageStart: Int = 0,
    val pageEnd: Int = 0,
    val cachedSpeakerPhraseIndices: Set<Int> = emptySet(),
    val phraseEmotions: Map<Int, PhraseEmotion> = emptyMap(),
    val cachedAttributionProvider: String? = null,
    val analysisMessage: String? = null,
    val errorMessage: String? = null,
)

data class ReaderPlaybackUiState(
    val status: PlaybackState = PlaybackState.DISABLED,
    val activePhraseIndex: Int? = null,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerWarning: Boolean = false,
)

data class ReaderVoiceUiState(
    val localGenerativeAvailable: Boolean = false,
    val localModels: List<VoiceModel> = emptyList(),
    val advancedModel: VoiceModel? = null,
    val advancedVoices: List<VoiceOption> = emptyList(),
    val advancedAvailable: Boolean = false,
    val proceduralVoices: List<VoiceOption> = emptyList(),
)

data class ReaderChromeUiState(val visible: Boolean = false)
