package com.bifel.voxreader.presentation.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bifel.voxreader.domain.analysis.CharacterAnalyzer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SettingsRepository
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.text.SentenceSegmenter
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val importBook: ImportBookUseCase,
    private val loadLatestBook: LoadLatestBookUseCase,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val characterAnalyzer: CharacterAnalyzer,
    private val speechEngine: SpeechEngine,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var initialized = false
    private var storedBook: StoredBook? = null
    private var analysisPreparedForBookId: String? = null
    private var currentUtteranceId: String? = null
    private var preparationJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(
                        darkTheme = settings.darkTheme,
                        speechControlsEnabled = settings.speechControlsEnabled,
                        playbackState = when {
                            !settings.speechControlsEnabled -> PlaybackState.DISABLED
                            it.playbackState == PlaybackState.DISABLED -> PlaybackState.PAUSED
                            else -> it.playbackState
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            speechEngine.events.collect(::handleSpeechEvent)
        }
    }

    fun initialize(sourceUri: String?) {
        if (initialized) {
            if (!sourceUri.isNullOrBlank()) openBook(sourceUri)
            return
        }
        initialized = true
        if (sourceUri.isNullOrBlank()) {
            loadLatest()
        } else {
            openBook(sourceUri)
        }
    }

    fun openBook(sourceUri: String) {
        stopPlayback(clearSelection = true)
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val document = importBook(sourceUri)
                val saved = libraryRepository.currentLibrary()
                    .books
                    .first { it.id == document.id }
                showBook(document, saved)
            }.onFailure(::showError)
        }
    }

    fun toggleChrome() {
        mutableUiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun hideChrome() {
        mutableUiState.update { it.copy(chromeVisible = false) }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(!mutableUiState.value.darkTheme)
        }
    }

    fun enableSpeechControls() {
        viewModelScope.launch {
            settingsRepository.setSpeechControlsEnabled(true)
        }
    }

    fun disableSpeechControls() {
        stopPlayback(clearSelection = false)
        viewModelScope.launch {
            settingsRepository.setSpeechControlsEnabled(false)
        }
    }

    fun playOrPause() {
        when (mutableUiState.value.playbackState) {
            PlaybackState.PLAYING,
            PlaybackState.PREPARING,
            -> pause()

            PlaybackState.PAUSED -> prepareAndPlay()
            PlaybackState.DISABLED -> enableSpeechControls()
        }
    }

    fun nextPhrase() {
        movePhrase(1)
    }

    fun previousPhrase() {
        movePhrase(-1)
    }

    fun onPageChanged(
        start: Int,
        end: Int,
        userInitiated: Boolean,
    ) {
        val book = mutableUiState.value.book ?: return
        val safeStart = start.coerceIn(0, book.text.length)
        val safeEnd = end.coerceIn(safeStart, book.text.length)
        if (userInitiated && mutableUiState.value.playbackState == PlaybackState.PLAYING) {
            pause()
        }
        mutableUiState.update {
            it.copy(
                pageStart = safeStart,
                pageEnd = safeEnd,
            )
        }
        viewModelScope.launch {
            libraryRepository.updateReadingPosition(book.id, safeStart, safeEnd)
        }
    }

    fun dismissError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    private fun loadLatest() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { loadLatestBook() }
                .onSuccess { result ->
                    if (result == null) {
                        mutableUiState.update { it.copy(isLoading = false) }
                    } else {
                        showBook(result.first, result.second)
                    }
                }
                .onFailure(::showError)
        }
    }

    private fun showBook(
        document: BookDocument,
        stored: StoredBook,
    ) {
        storedBook = stored
        analysisPreparedForBookId = null
        val phrases = SentenceSegmenter.segment(document.text)
        val restoredPosition = stored.readingPosition.coerceIn(0, document.text.length)
        mutableUiState.update { current ->
            current.copy(
                isLoading = false,
                book = document,
                phrases = phrases,
                characters = stored.characters.ifEmpty { listOf(NarratorProfile) },
                pageStart = restoredPosition,
                pageEnd = stored.pageEndPosition.coerceIn(restoredPosition, document.text.length),
                playbackState = if (current.speechControlsEnabled) {
                    PlaybackState.PAUSED
                } else {
                    PlaybackState.DISABLED
                },
                activePhraseIndex = null,
                highlightStart = null,
                highlightEnd = null,
                errorMessage = null,
            )
        }
    }

    private fun prepareAndPlay() {
        val state = mutableUiState.value
        val book = state.book ?: return
        if (state.phrases.isEmpty()) return

        preparationJob?.cancel()
        mutableUiState.update { it.copy(playbackState = PlaybackState.PREPARING) }
        preparationJob = viewModelScope.launch {
            runCatching {
                if (analysisPreparedForBookId != book.id) {
                    val analysis = characterAnalyzer.analyze(
                        text = book.text,
                        knownCharacters = storedBook?.characters.orEmpty(),
                    )
                    analysisPreparedForBookId = book.id
                    storedBook = storedBook?.copy(characters = analysis.characters)
                    mutableUiState.update {
                        it.copy(
                            phrases = analysis.phrases,
                            characters = analysis.characters,
                        )
                    }
                    libraryRepository.updateCharacters(book.id, analysis.characters)
                }
                speechEngine.awaitReady()
            }.onSuccess {
                val latest = mutableUiState.value
                val index = latest.activePhraseIndex
                    ?.coerceIn(latest.phrases.indices)
                    ?: firstVisiblePhraseIndex(latest)
                speakPhrase(index)
            }.onFailure(::speechFailure)
        }
    }

    private fun pause() {
        preparationJob?.cancel()
        preparationJob = null
        currentUtteranceId = null
        speechEngine.stop()
        mutableUiState.update {
            it.copy(
                playbackState = if (it.speechControlsEnabled) {
                    PlaybackState.PAUSED
                } else {
                    PlaybackState.DISABLED
                },
            )
        }
    }

    private fun movePhrase(delta: Int) {
        val state = mutableUiState.value
        if (state.phrases.isEmpty()) return
        val wasPlaying = state.playbackState == PlaybackState.PLAYING ||
            state.playbackState == PlaybackState.PREPARING
        preparationJob?.cancel()
        currentUtteranceId = null
        speechEngine.stop()

        val base = state.activePhraseIndex ?: firstVisiblePhraseIndex(state)
        val target = (base + delta).coerceIn(state.phrases.indices)
        val phrase = state.phrases[target]
        mutableUiState.update {
            it.copy(
                activePhraseIndex = target,
                highlightStart = phrase.start,
                highlightEnd = phrase.end,
                playbackState = if (wasPlaying) PlaybackState.PREPARING else PlaybackState.PAUSED,
            )
        }
        if (wasPlaying) {
            viewModelScope.launch {
                runCatching { speechEngine.awaitReady() }
                    .onSuccess { speakPhrase(target) }
                    .onFailure(::speechFailure)
            }
        }
    }

    private fun speakPhrase(index: Int) {
        val state = mutableUiState.value
        val phrase = state.phrases.getOrNull(index) ?: run {
            pause()
            return
        }
        val profile = state.characters.firstOrNull { it.id == phrase.speakerId }
            ?: NarratorProfile
        val utteranceId = "${state.book?.id}:${phrase.index}:${System.nanoTime()}"
        currentUtteranceId = utteranceId
        mutableUiState.update {
            it.copy(
                activePhraseIndex = index,
                highlightStart = phrase.start,
                highlightEnd = phrase.end,
                playbackState = PlaybackState.PREPARING,
            )
        }
        speechEngine.speak(
            SpeechRequest(
                utteranceId = utteranceId,
                text = phrase.text,
                voiceSlot = profile.voiceSlot,
                pitch = profile.pitch,
                speechRate = profile.speechRate,
            ),
        )
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> Unit
            is SpeechEvent.Started -> if (event.utteranceId == currentUtteranceId) {
                mutableUiState.update { it.copy(playbackState = PlaybackState.PLAYING) }
            }

            is SpeechEvent.Range -> if (event.utteranceId == currentUtteranceId) {
                val phrase = activePhrase() ?: return
                mutableUiState.update {
                    it.copy(
                        highlightStart = (phrase.start + event.start).coerceIn(phrase.start, phrase.end),
                        highlightEnd = (phrase.start + event.end).coerceIn(phrase.start, phrase.end),
                    )
                }
            }

            is SpeechEvent.Completed -> if (event.utteranceId == currentUtteranceId) {
                currentUtteranceId = null
                val state = mutableUiState.value
                val currentIndex = state.activePhraseIndex ?: return
                if (state.playbackState == PlaybackState.PLAYING &&
                    currentIndex < state.phrases.lastIndex
                ) {
                    speakPhrase(currentIndex + 1)
                } else {
                    mutableUiState.update { it.copy(playbackState = PlaybackState.PAUSED) }
                }
            }

            is SpeechEvent.Stopped -> Unit
            is SpeechEvent.Error -> {
                if (event.utteranceId == null || event.utteranceId == currentUtteranceId) {
                    speechFailure(IllegalStateException(event.message))
                }
            }
        }
    }

    private fun firstVisiblePhraseIndex(state: ReaderUiState): Int {
        val index = state.phrases.indexOfFirst { it.end > state.pageStart }
        return if (index >= 0) index else state.phrases.lastIndex.coerceAtLeast(0)
    }

    private fun activePhrase(): SpeechPhrase? =
        mutableUiState.value.activePhraseIndex?.let(mutableUiState.value.phrases::getOrNull)

    private fun speechFailure(error: Throwable) {
        Log.e(TAG, "Speech playback failed", error)
        currentUtteranceId = null
        speechEngine.stop()
        mutableUiState.update {
            it.copy(
                playbackState = PlaybackState.PAUSED,
                errorMessage = error.message ?: "Speech playback failed.",
            )
        }
    }

    private fun stopPlayback(clearSelection: Boolean) {
        preparationJob?.cancel()
        preparationJob = null
        currentUtteranceId = null
        speechEngine.stop()
        mutableUiState.update {
            it.copy(
                playbackState = if (it.speechControlsEnabled) {
                    PlaybackState.PAUSED
                } else {
                    PlaybackState.DISABLED
                },
                activePhraseIndex = if (clearSelection) null else it.activePhraseIndex,
                highlightStart = if (clearSelection) null else it.highlightStart,
                highlightEnd = if (clearSelection) null else it.highlightEnd,
            )
        }
    }

    private fun showError(error: Throwable) {
        Log.e(TAG, "Book import or load failed", error)
        mutableUiState.update {
            it.copy(
                isLoading = false,
                playbackState = if (it.speechControlsEnabled) {
                    PlaybackState.PAUSED
                } else {
                    PlaybackState.DISABLED
                },
                errorMessage = error.message ?: "The book could not be opened.",
            )
        }
    }

    private companion object {
        const val TAG = "VoxReader"
    }

    class Factory(
        private val importBook: ImportBookUseCase,
        private val loadLatestBook: LoadLatestBookUseCase,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
        private val characterAnalyzer: CharacterAnalyzer,
        private val speechEngine: SpeechEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                importBook = importBook,
                loadLatestBook = loadLatestBook,
                libraryRepository = libraryRepository,
                settingsRepository = settingsRepository,
                characterAnalyzer = characterAnalyzer,
                speechEngine = speechEngine,
            ) as T
    }
}
