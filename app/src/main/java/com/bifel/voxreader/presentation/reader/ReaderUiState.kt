package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.SpeechPhrase

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
    val chromeVisible: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.DISABLED,
    val activePhraseIndex: Int? = null,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null,
    val errorMessage: String? = null,
) {
    val readingProgress: Float
        get() {
            val length = book?.text?.length ?: return 0f
            if (length == 0) return 0f
            return pageStart.toFloat().div(length).coerceIn(0f, 1f)
        }
}

