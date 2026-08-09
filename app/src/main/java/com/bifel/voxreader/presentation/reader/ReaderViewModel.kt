package com.bifel.voxreader.presentation.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bifel.voxreader.application.reader.PlaybackContext
import com.bifel.voxreader.application.reader.ReaderPlaybackControllerFactory
import com.bifel.voxreader.application.reader.ReaderBookSession
import com.bifel.voxreader.application.reader.ReaderSessionCoordinator
import com.bifel.voxreader.application.reader.ReaderNavigationController
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.repository.SettingsRepository
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.speech.VoiceCatalogRepository
import com.bifel.voxreader.domain.speech.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val settingsRepository: SettingsRepository,
    private val voiceCatalogRepository: VoiceCatalogRepository,
    private val readerSessionCoordinator: ReaderSessionCoordinator,
    playbackControllerFactory: ReaderPlaybackControllerFactory,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var initialized = false
    private var readerSession: ReaderBookSession? = null
    private val navigation = ReaderNavigationController(MAX_HISTORY_ENTRIES)
    private val playbackController = playbackControllerFactory.create(viewModelScope)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(settings = settings)
                }
            }
        }
        viewModelScope.launch {
            voiceCatalogRepository.catalog.collect { catalog ->
                val simpleModels = catalog.models.filter {
                    it.engineMode == SpeechEngineMode.LOCAL_GENERATIVE
                }
                val advancedModel = catalog.models.firstOrNull {
                    it.engineMode == SpeechEngineMode.LOCAL_ADVANCED
                }
                mutableUiState.update {
                    it.copy(
                        voices = ReaderVoiceUiState(
                            proceduralVoices = catalog.voices.filter { voice ->
                                voice.engineMode == SpeechEngineMode.PROCEDURAL
                            },
                            localModels = simpleModels,
                            localGenerativeAvailable = simpleModels.any { model -> model.installed },
                            advancedModel = advancedModel,
                            advancedVoices = catalog.voices.filter { voice ->
                                voice.engineMode == SpeechEngineMode.LOCAL_ADVANCED
                            },
                            advancedAvailable = advancedModel?.installed == true,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                mutableUiState.update {
                    it.copy(
                        playback = ReaderPlaybackUiState(
                            status = playback.status,
                            activePhraseIndex = playback.activePhraseIndex,
                            highlightStart = playback.highlightStart,
                            highlightEnd = playback.highlightEnd,
                            sleepTimerEnabled = playback.sleepTimerEnabled,
                            sleepTimerWarning = playback.sleepTimerWarning,
                        ),
                        session = it.session.copy(
                            errorMessage = playback.errorMessage ?: it.errorMessage,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            uiState
                .map(::playbackContext)
                .distinctUntilChanged()
                .collect(playbackController::updateContext)
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
        playbackController.stop(clearSelection = true)
        mutableUiState.update { it.copy(session = it.session.copy(isLoading = true, errorMessage = null)) }
        viewModelScope.launch {
            runCatching {
                showBook(readerSessionCoordinator.open(sourceUri))
            }.onFailure(::showError)
        }
    }

    fun toggleChrome() {
        mutableUiState.update { it.copy(chrome = it.chrome.copy(visible = !it.chromeVisible)) }
    }

    fun hideChrome() {
        mutableUiState.update { it.copy(chrome = it.chrome.copy(visible = false)) }
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
        playbackController.disable()
        viewModelScope.launch {
            settingsRepository.setSpeechControlsEnabled(false)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val normalized = speed.coerceIn(0.5f, 2f)
        mutableUiState.update { it.copy(settings = it.settings.copy(playbackSpeed = normalized)) }
        playbackController.updateContext(playbackContext(mutableUiState.value))
        viewModelScope.launch {
            settingsRepository.setPlaybackSpeed(normalized)
        }
        playbackController.restartActivePhrase()
    }

    fun setReadingAppearance(fontSizeSp: Float, lineHeightSp: Float, horizontalMarginDp: Float, topPaddingDp: Float, bottomPaddingDp: Float) {
        viewModelScope.launch {
            settingsRepository.setReadingAppearance(fontSizeSp, lineHeightSp, horizontalMarginDp, topPaddingDp, bottomPaddingDp)
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch { settingsRepository.setReadingMode(mode) }
    }

    fun setTypography(fontFamily: ReaderFontFamily, alignment: ReaderTextAlignment, paragraphSpacingSp: Float, firstLineIndentSp: Float) {
        viewModelScope.launch { settingsRepository.setTypography(fontFamily, alignment, paragraphSpacingSp, firstLineIndentSp) }
    }

    fun setDisplayBehavior(orientation: ReaderOrientation, keepScreenOn: Boolean) {
        viewModelScope.launch { settingsRepository.setDisplayBehavior(orientation, keepScreenOn) }
    }

    fun setImageAppearance(size: ReaderImageSize, alignment: ReaderImageAlignment) {
        viewModelScope.launch { settingsRepository.setImageAppearance(size, alignment) }
    }

    fun setReaderColors(enabled: Boolean, background: Int, text: Int, link: Int, heading: Int, highlight: Int) {
        viewModelScope.launch { settingsRepository.setReaderColors(enabled, background, text, link, heading, highlight) }
    }

    fun setSleepTimerSettings(checkMinutes: Int, graceSeconds: Int) {
        mutableUiState.update {
            it.copy(settings = it.settings.copy(
                sleepCheckMinutes = checkMinutes.coerceIn(1, 60),
                sleepGraceSeconds = graceSeconds.coerceIn(10, 120),
            ))
        }
        playbackController.updateContext(playbackContext(mutableUiState.value))
        viewModelScope.launch { settingsRepository.setSleepTimerSettings(checkMinutes, graceSeconds) }
        playbackController.registerUserActivity()
    }

    fun toggleSleepTimer() {
        playbackController.toggleSleepTimer()
    }

    fun registerUserActivity() {
        playbackController.registerUserActivity()
    }

    fun openInternalLink(targetOffset: Int) {
        val book = mutableUiState.value.book ?: return
        onPageChanged(targetOffset.coerceIn(0, book.text.length), targetOffset.coerceIn(0, book.text.length), true)
    }

    fun setSpeechEngineMode(mode: SpeechEngineMode) {
        playbackController.pause()
        mutableUiState.update { it.copy(settings = it.settings.copy(speechEngineMode = mode)) }
        playbackController.updateContext(playbackContext(mutableUiState.value))
        viewModelScope.launch { settingsRepository.setSpeechEngineMode(mode) }
    }

    fun setSpeechPostProcessing(settings: SpeechPostProcessingSettings) {
        mutableUiState.update { it.copy(settings = it.settings.copy(speechPostProcessing = settings)) }
        playbackController.updateContext(playbackContext(mutableUiState.value))
        viewModelScope.launch { settingsRepository.setSpeechPostProcessing(settings) }
        playbackController.restartActivePhrase()
    }

    fun downloadLocalVoice(id: String) {
        viewModelScope.launch { voiceCatalogRepository.download(id) }
    }

    fun removeLocalVoice(id: String) {
        playbackController.stop(clearSelection = false)
        viewModelScope.launch { voiceCatalogRepository.remove(id) }
    }

    fun downloadAdvancedModel() {
        val id = mutableUiState.value.advancedVoiceModel?.id ?: return
        viewModelScope.launch { voiceCatalogRepository.download(id) }
    }

    fun removeAdvancedModel() {
        playbackController.stop(clearSelection = false)
        val id = mutableUiState.value.advancedVoiceModel?.id ?: return
        viewModelScope.launch { voiceCatalogRepository.remove(id) }
    }

    fun previewAdvancedVoice(id: String) {
        val voice = mutableUiState.value.advancedVoices.firstOrNull { it.id == id } ?: return
        playbackController.preview(
            SpeechEngineMode.LOCAL_ADVANCED,
            SpeechRequest(
                utteranceId = "advanced-preview:${voice.id}:${System.nanoTime()}",
                text = "Hello, this is how the advanced ${voice.displayName} voice sounds.",
                voiceSlot = voice.speakerId,
                voiceGroup = voice.voiceGroup,
                pitch = 1f,
                speechRate = 1f,
                advancedVoiceId = voice.id,
            ),
        )
    }
    fun previewLocalVoice(id: String) {
        val pack = mutableUiState.value.localVoicePacks
            .firstOrNull { it.id == id && it.installed } ?: return
        playbackController.preview(
            SpeechEngineMode.LOCAL_GENERATIVE,
            SpeechRequest(
                utteranceId = "model-preview:${pack.id}:${System.nanoTime()}",
                text = if (pack.language == VoiceLanguage.UKRAINIAN) {
                    "Привіт, це україномовна модель ${pack.displayName}."
                } else {
                    "Hello, this is how ${pack.displayName} sounds."
                },
                voiceSlot = pack.speakerId,
                voiceGroup = pack.voiceGroup,
                pitch = 1f,
                speechRate = 1f,
                localVoicePackId = pack.id,
            ),
        )
    }
    fun updateCharacterVoice(profile: CharacterProfile) {
        val state = mutableUiState.value
        val book = state.book ?: return
        val updatedCharacters = (state.characters.filterNot { it.id == profile.id } + profile)
            .sortedWith(
                compareBy<CharacterProfile>({ it.id != NarratorProfile.id }, { it.displayName }),
            )
        readerSession = readerSession?.let { session ->
            session.copy(storedBook = session.storedBook.copy(
                voiceOverrides = session.storedBook.voiceOverrides.filterNot { it.id == profile.id } + profile,
            ))
        }
        mutableUiState.update { it.copy(session = it.session.copy(characters = updatedCharacters)) }
        viewModelScope.launch {
            readerSession?.let { session ->
                readerSession = readerSessionCoordinator.updateVoiceOverride(session, profile)
            }
        }
    }

    fun previewCharacterVoice(profile: CharacterProfile) {
        playbackController.preview(
            mutableUiState.value.speechEngineMode,
            SpeechRequest(
                utteranceId = "voice-preview:${profile.id}:${System.nanoTime()}",
                text = "Hello. This is how ${profile.displayName}'s voice sounds.",
                voiceSlot = profile.voiceSlot,
                voiceGroup = profile.voiceGroup,
                pitch = profile.pitch,
                speechRate = profile.speechRate,
                proceduralVoiceId = profile.proceduralVoiceId,
                localVoicePackId = profile.localVoicePackId,
                advancedVoiceId = profile.advancedVoiceId,
            ),
        )
    }
    fun loadProceduralVoices() {
        viewModelScope.launch { runCatching { voiceCatalogRepository.loadProceduralVoices() }.onFailure(::showError) }
    }

    fun previewProceduralVoice(id: String) {
        val voice = mutableUiState.value.proceduralVoices.firstOrNull { it.id == id } ?: return
        playbackController.preview(
            SpeechEngineMode.PROCEDURAL,
            SpeechRequest(
                utteranceId = "procedural-preview:${voice.id}:${System.nanoTime()}",
                text = if (voice.language == VoiceLanguage.UKRAINIAN) {
                    "Привіт, це україномовний системний голос ${voice.displayName}."
                } else {
                    "Hello, this is how ${voice.displayName} sounds."
                },
                voiceSlot = voice.speakerId,
                voiceGroup = voice.voiceGroup,
                pitch = 1f,
                speechRate = 1f,
                proceduralVoiceId = voice.id,
            ),
        )
    }
    fun playOrPause() {
        if (mutableUiState.value.playbackState == PlaybackState.DISABLED) enableSpeechControls()
        else playbackController.playOrPause()
    }

    fun nextPhrase() {
        playbackController.movePhrase(1)
    }

    fun previousPhrase() {
        playbackController.movePhrase(-1)
    }

    fun onPageChanged(
        start: Int,
        end: Int,
        userInitiated: Boolean,
    ) {
        val book = mutableUiState.value.book ?: return
        val position = navigation.move(start, end, recordJump = userInitiated)
        val safeStart = position.start
        val safeEnd = position.end
        if (userInitiated) {
            playbackController.onUserNavigation()
        }
        mutableUiState.update {
            it.copy(session = it.session.copy(
                pageStart = safeStart,
                pageEnd = safeEnd,
            ))
        }
        viewModelScope.launch {
            readerSessionCoordinator.updateReadingPosition(book.id, safeStart, safeEnd)
        }
    }

    fun goBackInHistory() = navigation.back()?.let {
        onPageChanged(it.start, it.end, false)
    }

    fun goForwardInHistory() = navigation.forward()?.let {
        onPageChanged(it.start, it.end, false)
    }

    fun speakerAt(offset: Int): String? {
        val state = mutableUiState.value
        val phrase = PhraseLookup.containing(state.phrases, offset) ?: return null
        return state.characters.firstOrNull { it.id == phrase.speakerId }?.displayName
            ?: when (phrase.speakerId) {
                NarratorProfile.id -> NarratorProfile.displayName
                UNKNOWN_SPEAKER_ID -> "Unknown"
                else -> null
            }
    }

    fun dismissError() {
        mutableUiState.update { it.copy(session = it.session.copy(errorMessage = null)) }
        playbackController.clearError()
    }

    fun importAnalysisSidecar(content: String) {
        val state = mutableUiState.value
        state.book ?: run {
            showError(IllegalStateException("Open a book before importing analysis."))
            return
        }
        val currentSession = readerSession ?: run {
            showError(IllegalStateException("The current book session is unavailable."))
            return
        }
        playbackController.stop(clearSelection = false)
        mutableUiState.update {
            it.copy(session = it.session.copy(analysisMessage = "Validating analysis file…"))
        }
        viewModelScope.launch {
            runCatching {
                readerSessionCoordinator.importAnalysis(currentSession, content)
            }.onSuccess { imported ->
                if (mutableUiState.value.book?.id != imported.session.document.id) return@onSuccess
                readerSession = imported.session
                val cached = requireNotNull(imported.session.analysis)
                val result = imported.result
                mutableUiState.update {
                    it.copy(session = it.session.copy(
                        phrases = cached.phrases,
                        characters = imported.session.characters,
                        cachedSpeakerPhraseIndices = cached.cachedPhraseIndices,
                        phraseEmotions = cached.emotionsByPhraseIndex,
                        cachedAttributionProvider = cached.providerId,
                        analysisMessage = buildString {
                            append("Loaded ")
                            append(result.attributedPhrases)
                            append('/')
                            append(result.totalPhrases)
                            append(" cached phrases and ")
                            append(result.characterCount)
                            append(" character")
                            if (result.characterCount != 1) append('s')
                            append('.')
                        },
                        errorMessage = null,
                    ))
                }
            }.onFailure(::showError)
        }
    }

    fun reportAnalysisFileError(error: Throwable) {
        showError(error)
    }

    private fun loadLatest() {
        mutableUiState.update {
            it.copy(session = it.session.copy(isLoading = true, errorMessage = null))
        }
        viewModelScope.launch {
            runCatching { readerSessionCoordinator.latest() }
                .onSuccess { session ->
                    if (session == null) {
                        mutableUiState.update {
                            it.copy(session = it.session.copy(isLoading = false))
                        }
                    } else {
                        showBook(session)
                    }
                }
                .onFailure(::showError)
        }
    }

    private suspend fun showBook(session: ReaderBookSession) {
        val document = session.document
        val stored = session.storedBook
        readerSession = session
        val restoredPosition = stored.readingPosition.coerceIn(0, document.text.length)
        navigation.reset(
            textLength = document.text.length,
            start = restoredPosition,
            end = stored.pageEndPosition,
        )
        mutableUiState.update { current ->
            current.copy(
                session = ReaderSessionUiState(
                    isLoading = false,
                    book = document,
                    phrases = emptyList(),
                    characters = session.characters,
                    pageStart = restoredPosition,
                    pageEnd = stored.pageEndPosition.coerceIn(restoredPosition, document.text.length),
                    analysisMessage = "Preparing speech phrases in the background…",
                ),
            )
        }
        val resolved = readerSessionCoordinator.resolve(session)
        if (mutableUiState.value.book?.id != document.id) return
        readerSession = resolved
        val cached = requireNotNull(resolved.analysis)
        mutableUiState.update {
            it.copy(session = it.session.copy(
                phrases = cached.phrases,
                characters = resolved.characters,
                cachedSpeakerPhraseIndices = cached.cachedPhraseIndices,
                phraseEmotions = cached.emotionsByPhraseIndex,
                cachedAttributionProvider = cached.providerId,
                analysisMessage = cached.providerId?.let { provider ->
                    "Using cached speaker analysis from $provider."
                },
            ))
        }
    }

    private fun showError(error: Throwable) {
        Log.e(TAG, "Book import or load failed", error)
        playbackController.pause()
        mutableUiState.update {
            it.copy(session = it.session.copy(
                isLoading = false,
                errorMessage = error.message ?: "The book could not be opened.",
            ))
        }
    }

    private fun playbackContext(state: ReaderUiState): PlaybackContext = PlaybackContext(
        bookId = state.book?.id,
        bookTitle = state.book?.title.orEmpty(),
        phrases = state.phrases,
        characters = state.characters,
        emotionsByPhraseIndex = state.phraseEmotions,
        hasSpeakerMap = state.cachedAttributionProvider != null,
        firstVisibleOffset = state.pageStart,
        controlsEnabled = state.speechControlsEnabled,
        engineMode = state.speechEngineMode,
        playbackSpeed = state.playbackSpeed,
        postProcessing = state.speechPostProcessing,
        sleepCheckMinutes = state.sleepCheckMinutes,
        sleepGraceSeconds = state.sleepGraceSeconds,
    )

    override fun onCleared() {
        playbackController.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "VoxReader"
        const val MAX_HISTORY_ENTRIES = 100
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val voiceCatalogRepository: VoiceCatalogRepository,
        private val readerSessionCoordinator: ReaderSessionCoordinator,
        private val playbackControllerFactory: ReaderPlaybackControllerFactory,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                settingsRepository = settingsRepository,
                voiceCatalogRepository = voiceCatalogRepository,
                readerSessionCoordinator = readerSessionCoordinator,
                playbackControllerFactory = playbackControllerFactory,
            ) as T
    }
}
