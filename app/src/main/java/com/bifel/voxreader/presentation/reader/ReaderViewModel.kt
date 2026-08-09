package com.bifel.voxreader.presentation.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.reader.ReadingHistory
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SettingsRepository
import com.bifel.voxreader.domain.speech.EmotionDelivery
import com.bifel.voxreader.domain.speech.BufferedPhrase
import com.bifel.voxreader.domain.speech.PhrasePlaybackBuffer
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.speech.PlaybackCommand
import com.bifel.voxreader.domain.speech.PlaybackNotificationController
import com.bifel.voxreader.domain.speech.ProceduralVoiceCatalog
import com.bifel.voxreader.domain.speech.ProceduralVoiceLanguage
import com.bifel.voxreader.data.speech.PiperVoicePackManager
import com.bifel.voxreader.data.speech.VoicePackLanguage
import com.bifel.voxreader.data.speech.AdvancedVoiceModelManager
import com.bifel.voxreader.domain.speech.SpeechText
import com.bifel.voxreader.domain.text.SentenceSegmenter
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import com.bifel.voxreader.data.preanalysis.PortableAnalysisService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val importBook: ImportBookUseCase,
    private val loadLatestBook: LoadLatestBookUseCase,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val speechEngine: SpeechEngine,
    private val proceduralVoiceCatalog: ProceduralVoiceCatalog,
    private val generativeSpeechEngine: SpeechEngine,
    private val piperVoicePackManager: PiperVoicePackManager,
    private val advancedSpeechEngine: SpeechEngine,
    private val advancedVoiceModelManager: AdvancedVoiceModelManager,
    private val portableAnalysisService: PortableAnalysisService,
    private val playbackNotificationController: PlaybackNotificationController,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var initialized = false
    private var storedBook: StoredBook? = null
    private var currentUtteranceId: String? = null
    private var speechQueueJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val speechBuffer = PhrasePlaybackBuffer(SPEECH_LOOK_AHEAD_PHRASES)
    private val recentPhraseCache = ArrayDeque<Int>()
    private var playbackSession = 0L
    private val readingHistory = ReadingHistory(MAX_HISTORY_ENTRIES)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(
                        darkTheme = settings.darkTheme,
                        speechControlsEnabled = settings.speechControlsEnabled,
                        playbackSpeed = settings.playbackSpeed,
                        fontSizeSp = settings.fontSizeSp,
                        lineHeightSp = settings.lineHeightSp,
                        horizontalMarginDp = settings.horizontalMarginDp,
                        topPaddingDp = settings.topPaddingDp,
                        bottomPaddingDp = settings.bottomPaddingDp,
                        fontFamily = settings.fontFamily,
                        textAlignment = settings.textAlignment,
                        paragraphSpacingSp = settings.paragraphSpacingSp,
                        firstLineIndentSp = settings.firstLineIndentSp,
                        orientation = settings.orientation,
                        keepScreenOn = settings.keepScreenOn,
                        imageSize = settings.imageSize,
                        imageAlignment = settings.imageAlignment,
                        customReaderColors = settings.customReaderColors,
                        readerBackgroundColor = settings.readerBackgroundColor,
                        readerTextColor = settings.readerTextColor,
                        readerLinkColor = settings.readerLinkColor,
                        readerHeadingColor = settings.readerHeadingColor,
                        readerHighlightColor = settings.readerHighlightColor,
                        sleepCheckMinutes = settings.sleepCheckMinutes,
                        sleepGraceSeconds = settings.sleepGraceSeconds,
                        readingMode = settings.readingMode,
                        speechEngineMode = settings.speechEngineMode,
                        speechPostProcessing = settings.speechPostProcessing,
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
        viewModelScope.launch {
            proceduralVoiceCatalog.proceduralVoices.collect { voices ->
                mutableUiState.update { it.copy(proceduralVoices = voices) }
            }
        }
        viewModelScope.launch {
            generativeSpeechEngine.events.collect(::handleSpeechEvent)
        }
        viewModelScope.launch {
            advancedSpeechEngine.events.collect(::handleSpeechEvent)
        }
        viewModelScope.launch {
            piperVoicePackManager.packs.collect { packs ->
                mutableUiState.update {
                    it.copy(
                        localVoicePacks = packs,
                        localGenerativeVoiceAvailable = packs.any { pack -> pack.installed },
                    )
                }
            }
        }
        viewModelScope.launch {
            advancedVoiceModelManager.model.collect { model ->
                mutableUiState.update {
                    it.copy(
                        advancedVoiceModel = model,
                        advancedVoices = advancedVoiceModelManager.voices,
                        advancedVoiceAvailable = model.installed,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackNotificationController.commands.collect { command ->
                when (command) {
                    PlaybackCommand.PLAY -> playOrPause()
                    PlaybackCommand.PAUSE -> pause()
                    PlaybackCommand.NEXT -> nextPhrase()
                    PlaybackCommand.PREVIOUS -> previousPhrase()
                }
            }
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
                portableAnalysisService.clearBook(document.id)
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
        playbackNotificationController.stop()
        viewModelScope.launch {
            settingsRepository.setSpeechControlsEnabled(false)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val normalized = speed.coerceIn(0.5f, 2f)
        val state = mutableUiState.value
        val restartIndex = state.activePhraseIndex?.takeIf {
            state.playbackState == PlaybackState.PLAYING ||
                state.playbackState == PlaybackState.PREPARING
        }
        mutableUiState.update { it.copy(playbackSpeed = normalized) }
        viewModelScope.launch {
            settingsRepository.setPlaybackSpeed(normalized)
        }
        restartIndex?.let(::preparePhraseAndPlay)
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
        viewModelScope.launch { settingsRepository.setSleepTimerSettings(checkMinutes, graceSeconds) }
        if (mutableUiState.value.sleepTimerEnabled) restartSleepTimer()
    }

    fun toggleSleepTimer() {
        val enabled = !mutableUiState.value.sleepTimerEnabled
        mutableUiState.update { it.copy(sleepTimerEnabled = enabled, sleepTimerWarning = false) }
        if (enabled) restartSleepTimer() else sleepTimerJob?.cancel()
    }

    fun registerUserActivity() {
        if (mutableUiState.value.sleepTimerEnabled) restartSleepTimer()
    }

    fun openInternalLink(targetOffset: Int) {
        val book = mutableUiState.value.book ?: return
        onPageChanged(targetOffset.coerceIn(0, book.text.length), targetOffset.coerceIn(0, book.text.length), true)
    }

    fun setSpeechEngineMode(mode: SpeechEngineMode) {
        pause()
        mutableUiState.update { it.copy(speechEngineMode = mode) }
        viewModelScope.launch { settingsRepository.setSpeechEngineMode(mode) }
    }

    fun setSpeechPostProcessing(settings: SpeechPostProcessingSettings) {
        val restartIndex = mutableUiState.value.activePhraseIndex?.takeIf {
            mutableUiState.value.playbackState == PlaybackState.PLAYING ||
                mutableUiState.value.playbackState == PlaybackState.PREPARING
        }
        mutableUiState.update { it.copy(speechPostProcessing = settings) }
        viewModelScope.launch { settingsRepository.setSpeechPostProcessing(settings) }
        restartIndex?.let(::preparePhraseAndPlay)
    }

    fun downloadLocalVoice(id: String) {
        viewModelScope.launch { piperVoicePackManager.download(id) }
    }

    fun removeLocalVoice(id: String) {
        resetSpeechPipeline()
        viewModelScope.launch { piperVoicePackManager.remove(id) }
    }

    fun downloadAdvancedModel() {
        viewModelScope.launch { advancedVoiceModelManager.download() }
    }

    fun removeAdvancedModel() {
        resetSpeechPipeline()
        viewModelScope.launch { advancedVoiceModelManager.remove() }
    }

    fun previewAdvancedVoice(id: String) {
        val voice = advancedVoiceModelManager.voices.firstOrNull { it.id == id } ?: return
        resetSpeechPipeline()
        mutableUiState.update {
            it.copy(playbackState = if (it.speechControlsEnabled) PlaybackState.PAUSED else PlaybackState.DISABLED)
        }
        viewModelScope.launch {
            runCatching {
                advancedSpeechEngine.awaitReady()
                advancedSpeechEngine.enqueue(
                    SpeechRequest(
                        utteranceId = "advanced-preview:${voice.id}:${System.nanoTime()}",
                        text = "Hello, this is how the advanced ${voice.displayName} voice sounds.",
                        voiceSlot = voice.speakerId,
                        voiceGroup = voice.voiceGroup,
                        pitch = 1f,
                        speechRate = 1f,
                        advancedVoiceId = voice.id,
                    ),
                    SpeechQueueMode.FLUSH,
                )
            }.onFailure(::showError)
        }
    }

    fun previewLocalVoice(id: String) {
        val installed = piperVoicePackManager.installedPacks()
        val pack = installed.firstOrNull { it.id == id } ?: return
        val voiceSlot = installed.filter { it.voiceGroup == pack.voiceGroup }.indexOf(pack)
        resetSpeechPipeline()
        mutableUiState.update {
            it.copy(playbackState = if (it.speechControlsEnabled) PlaybackState.PAUSED else PlaybackState.DISABLED)
        }
        viewModelScope.launch {
            runCatching {
                generativeSpeechEngine.awaitReady()
                generativeSpeechEngine.enqueue(
                    SpeechRequest(
                        utteranceId = "model-preview:${pack.id}:${System.nanoTime()}",
                        text = if (pack.language == VoicePackLanguage.UKRAINIAN) {
                            "Привіт, це україномовна модель ${pack.displayName}."
                        } else {
                            "Hello, this is how ${pack.displayName} sounds."
                        },
                        voiceSlot = voiceSlot,
                        voiceGroup = pack.voiceGroup,
                        pitch = 1f,
                        speechRate = 1f,
                        localVoicePackId = pack.id,
                    ),
                    SpeechQueueMode.FLUSH,
                )
            }.onFailure(::showError)
        }
    }

    fun updateCharacterVoice(profile: CharacterProfile) {
        val state = mutableUiState.value
        val book = state.book ?: return
        val updatedCharacters = (state.characters.filterNot { it.id == profile.id } + profile)
            .sortedWith(
                compareBy<CharacterProfile>({ it.id != NarratorProfile.id }, { it.displayName }),
            )
        storedBook = storedBook?.let { stored ->
            stored.copy(
                voiceOverrides = stored.voiceOverrides.filterNot { it.id == profile.id } + profile,
            )
        }
        mutableUiState.update { it.copy(characters = updatedCharacters) }
        viewModelScope.launch {
            libraryRepository.updateVoiceOverride(book.id, profile)
        }
    }

    fun previewCharacterVoice(profile: CharacterProfile) {
        resetSpeechPipeline()
        mutableUiState.update {
            it.copy(playbackState = if (it.speechControlsEnabled) PlaybackState.PAUSED else PlaybackState.DISABLED)
        }
        viewModelScope.launch {
            runCatching {
                val engine = activeSpeechEngine()
                engine.awaitReady()
                engine.enqueue(
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
                    SpeechQueueMode.FLUSH,
                )
            }.onFailure(::showError)
        }
    }

    fun loadProceduralVoices() {
        viewModelScope.launch { runCatching { proceduralVoiceCatalog.loadVoiceCatalog() }.onFailure(::showError) }
    }

    fun previewProceduralVoice(id: String) {
        val voice = mutableUiState.value.proceduralVoices.firstOrNull { it.id == id } ?: return
        resetSpeechPipeline()
        viewModelScope.launch {
            runCatching {
                speechEngine.awaitReady()
                speechEngine.enqueue(
                    SpeechRequest(
                        utteranceId = "procedural-preview:${voice.id}:${System.nanoTime()}",
                        text = if (voice.language == ProceduralVoiceLanguage.UKRAINIAN) {
                            "Привіт, це україномовний системний голос ${voice.displayName}."
                        } else {
                            "Hello, this is how ${voice.displayName} sounds."
                        },
                        voiceSlot = 0,
                        voiceGroup = VoiceGroup.NEUTRAL,
                        pitch = 1f,
                        speechRate = 1f,
                        proceduralVoiceId = voice.id,
                    ),
                    SpeechQueueMode.FLUSH,
                )
            }.onFailure(::showError)
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
        if (userInitiated && safeStart != mutableUiState.value.pageStart) {
            readingHistory.recordJump(mutableUiState.value.pageStart, safeStart)
        }
        if (userInitiated) {
            if (mutableUiState.value.playbackState == PlaybackState.PLAYING ||
                mutableUiState.value.playbackState == PlaybackState.PREPARING
            ) {
                pause()
            }
        }
        mutableUiState.update {
            it.copy(
                pageStart = safeStart,
                pageEnd = safeEnd,
                activePhraseIndex = if (userInitiated) null else it.activePhraseIndex,
                highlightStart = if (userInitiated) null else it.highlightStart,
                highlightEnd = if (userInitiated) null else it.highlightEnd,
            )
        }
        viewModelScope.launch {
            libraryRepository.updateReadingPosition(book.id, safeStart, safeEnd)
        }
    }

    fun goBackInHistory() = readingHistory.goBack(mutableUiState.value.pageStart)?.let {
        onPageChanged(it, it, false)
    }

    fun goForwardInHistory() = readingHistory.goForward(mutableUiState.value.pageStart)?.let {
        onPageChanged(it, it, false)
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
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    fun importAnalysisSidecar(content: String) {
        val state = mutableUiState.value
        val book = state.book ?: run {
            showError(IllegalStateException("Open a book before importing analysis."))
            return
        }
        stopPlayback(clearSelection = false)
        mutableUiState.update { it.copy(analysisMessage = "Validating analysis file…") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val result = portableAnalysisService.import(book, content)
                    val cached = portableAnalysisService.cached(book) {
                        SentenceSegmenter.segment(book.text)
                    }
                    result to cached
                }
            }.onSuccess { (result, cached) ->
                val overrides = storedBook?.voiceOverrides.orEmpty()
                val characters = applyVoiceOverrides(
                    cached.characters.ifEmpty { listOf(NarratorProfile) },
                    overrides,
                )
                mutableUiState.update {
                    it.copy(
                        phrases = cached.phrases,
                        characters = characters,
                        cachedSpeakerPhraseIndices = cached.cachedPhraseIndices,
                        phraseEmotions = cached.emotionsByPhraseIndex,
                        cachedAttributionProvider = cached.providerId,
                        cachedAttributionCoverage = cached.coverage,
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
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun reportAnalysisFileError(error: Throwable) {
        showError(error)
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

    private suspend fun showBook(
        document: BookDocument,
        stored: StoredBook,
    ) {
        storedBook = stored
        val restoredPosition = stored.readingPosition.coerceIn(0, document.text.length)
        mutableUiState.update { current ->
            current.copy(
                isLoading = false,
                book = document,
                phrases = emptyList(),
                characters = applyVoiceOverrides(listOf(NarratorProfile), stored.voiceOverrides),
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
                cachedSpeakerPhraseIndices = emptySet(),
                phraseEmotions = emptyMap(),
                cachedAttributionProvider = null,
                cachedAttributionCoverage = 0f,
                analysisMessage = "Preparing speech phrases in the background…",
                errorMessage = null,
            )
        }
        val cached = withContext(Dispatchers.Default) {
            portableAnalysisService.cached(document) { SentenceSegmenter.segment(document.text) }
        }
        if (mutableUiState.value.book?.id != document.id) return
        val characters = applyVoiceOverrides(
            cached.characters.ifEmpty { listOf(NarratorProfile) },
            stored.voiceOverrides,
        )
        mutableUiState.update {
            it.copy(
                phrases = cached.phrases,
                characters = characters,
                cachedSpeakerPhraseIndices = cached.cachedPhraseIndices,
                phraseEmotions = cached.emotionsByPhraseIndex,
                cachedAttributionProvider = cached.providerId,
                cachedAttributionCoverage = cached.coverage,
                analysisMessage = cached.providerId?.let { provider ->
                    "Using cached speaker analysis from $provider."
                },
            )
        }
    }

    private fun prepareAndPlay() {
        val state = mutableUiState.value
        state.book ?: return
        if (state.phrases.isEmpty()) {
            if (speechQueueJob?.isActive == true) return
            val bookId = state.book.id
            mutableUiState.update { it.copy(playbackState = PlaybackState.PREPARING) }
            speechQueueJob = viewModelScope.launch {
                uiState.first { it.book?.id != bookId || it.phrases.isNotEmpty() }
                speechQueueJob = null
                if (mutableUiState.value.book?.id == bookId) prepareAndPlay()
            }
            return
        }
        if (
            state.speechEngineMode == SpeechEngineMode.LOCAL_GENERATIVE &&
            piperVoicePackManager.installedPacks().isEmpty()
        ) {
            showError(IllegalStateException("Local AI voice is selected, but no compatible voice model is installed yet."))
            return
        }
        if (
            state.speechEngineMode == SpeechEngineMode.LOCAL_ADVANCED &&
            !advancedVoiceModelManager.installed()
        ) {
            showError(IllegalStateException("Advanced AI is selected, but KittenTTS Micro is not installed yet."))
            return
        }

        val requestedIndex = state.activePhraseIndex
            ?.coerceIn(state.phrases.indices)
            ?: firstVisiblePhraseIndex(state)
        val index = state.nextSpeakableIndex(requestedIndex, direction = 1, includeStart = true)
            ?: return
        preparePhraseAndPlay(index)
    }

    private fun pause() {
        resetSpeechPipeline()
        mutableUiState.update {
            it.copy(
                playbackState = if (it.speechControlsEnabled) {
                    PlaybackState.PAUSED
                } else {
                    PlaybackState.DISABLED
                },
            )
        }
        mutableUiState.value.book?.let { playbackNotificationController.update(it.title, false) }
    }

    private fun movePhrase(delta: Int) {
        val state = mutableUiState.value
        if (state.phrases.isEmpty()) return
        val wasPlaying = state.playbackState == PlaybackState.PLAYING ||
            state.playbackState == PlaybackState.PREPARING

        val base = state.activePhraseIndex ?: firstVisiblePhraseIndex(state)
        val target = state.nextSpeakableIndex(
            start = base,
            direction = delta,
            includeStart = false,
        ) ?: base
        val phrase = state.phrases[target]
        if (wasPlaying) {
            preparePhraseAndPlay(target)
        } else {
            resetSpeechPipeline()
            mutableUiState.update {
                it.copy(
                    activePhraseIndex = target,
                    highlightStart = phrase.start,
                    highlightEnd = phrase.end,
                    playbackState = PlaybackState.PAUSED,
                )
            }
        }
    }

    private fun preparePhraseAndPlay(index: Int) {
        val state = mutableUiState.value
        state.book ?: return
        if (index !in state.phrases.indices) return
        if (!SpeechText.isSpeakable(state.phrases[index].text)) {
            val next = state.nextSpeakableIndex(index, direction = 1, includeStart = false)
            if (next == null) {
                mutableUiState.update { it.copy(playbackState = PlaybackState.PAUSED) }
            } else {
                preparePhraseAndPlay(next)
            }
            return
        }

        resetSpeechPipeline()
        val session = playbackSession
        val phrase = state.phrases[index]
        mutableUiState.update {
            it.copy(
                activePhraseIndex = index,
                highlightStart = phrase.start,
                highlightEnd = phrase.end,
                playbackState = PlaybackState.PREPARING,
            )
        }
        playbackNotificationController.update(state.book.title, true)
        if (state.sleepTimerEnabled && sleepTimerJob?.isActive != true) restartSleepTimer()
        speechQueueJob = viewModelScope.launch {
            runCatching {
                activeSpeechEngine().awaitReady()
                fillSpeechBuffer(
                    session = session,
                    initialIndex = index,
                    flushQueue = true,
                )
            }.onFailure(::handlePreparationFailure)
        }
    }

    private suspend fun fillSpeechBuffer(
        session: Long,
        initialIndex: Int,
        flushQueue: Boolean,
    ) {
        mutableUiState.value.book ?: return
        var index: Int? = initialIndex
        var queueMode = if (flushQueue) {
            SpeechQueueMode.FLUSH
        } else {
            SpeechQueueMode.APPEND
        }
        while (
            session == playbackSession &&
            speechBuffer.hasCapacity &&
            index != null
        ) {
            val phraseIndex = index
            if (!speechBuffer.containsPhrase(phraseIndex)) {
                enqueuePhrase(
                    index = phraseIndex,
                    session = session,
                    queueMode = queueMode,
                )
                queueMode = SpeechQueueMode.APPEND
            }
            val latest = mutableUiState.value
            index = latest.nextSpeakableIndex(
                start = phraseIndex,
                direction = 1,
                includeStart = false,
            )
        }
    }

    private fun enqueuePhrase(
        index: Int,
        session: Long,
        queueMode: SpeechQueueMode,
    ) {
        val state = mutableUiState.value
        val phrase = state.phrases.getOrNull(index) ?: return
        // Without an imported character map, the book has no trusted speaker assignments.
        // Keep voice mode deterministic and use the configured narrator for every phrase.
        val profile = if (state.cachedAttributionProvider == null) {
            state.characters.firstOrNull { it.id == NarratorProfile.id } ?: NarratorProfile
        } else {
            state.characters.firstOrNull { it.id == phrase.speakerId } ?: NarratorProfile
        }
        val emotion = state.phraseEmotions[phrase.index]
        val delivery = EmotionDelivery.from(emotion, state.speechPostProcessing)
        val pitch = (profile.pitch * delivery.pitchMultiplier).coerceIn(0.5f, 1.6f)
        val speechRate = (
            profile.speechRate *
                state.playbackSpeed *
                delivery.speechRateMultiplier
            ).coerceIn(0.5f, 2f)
        val utteranceId = "${state.book?.id}:$session:${phrase.index}:${System.nanoTime()}"
        speechBuffer.add(
            BufferedPhrase(
                utteranceId = utteranceId,
                phraseIndex = index,
            ),
        )
        Log.d(
            TAG,
            "Queued phrase ${phrase.index} [${phrase.start},${phrase.end}) as " +
                "${profile.displayName} (${profile.voiceGroup}); " +
                "emotion=${emotion?.type ?: "NEUTRAL"}; " +
                "pitch=$pitch; rate=$speechRate; pause=${delivery.pauseAfterMillis}ms; " +
                "mode=$queueMode; buffered=${speechBuffer.size}",
        )
        activeSpeechEngine().enqueue(
            SpeechRequest(
                utteranceId = utteranceId,
                text = SpeechText.withoutSpokenQuotes(phrase.text),
                voiceSlot = profile.voiceSlot,
                voiceGroup = profile.voiceGroup,
                pitch = pitch,
                speechRate = speechRate,
                proceduralVoiceId = profile.proceduralVoiceId,
                localVoicePackId = profile.localVoicePackId,
                advancedVoiceId = profile.advancedVoiceId,
                postProcessingPitch = delivery.pitchMultiplier,
                loudness = delivery.loudnessMultiplier,
                tone = delivery.tone,
                pauseAfterMillis = delivery.pauseAfterMillis,
            ),
            queueMode = queueMode,
        )
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> Unit
            is SpeechEvent.Started -> {
                val buffered = speechBuffer[event.utteranceId] ?: return
                val phrase = mutableUiState.value.phrases.getOrNull(buffered.phraseIndex) ?: return
                currentUtteranceId = event.utteranceId
                mutableUiState.update {
                    it.copy(
                        activePhraseIndex = buffered.phraseIndex,
                        highlightStart = phrase.start,
                        highlightEnd = phrase.end,
                        playbackState = PlaybackState.PLAYING,
                    )
                }
            }

            is SpeechEvent.Range -> if (event.utteranceId == currentUtteranceId) {
                val buffered = speechBuffer[event.utteranceId] ?: return
                val phrase = mutableUiState.value.phrases.getOrNull(buffered.phraseIndex) ?: return
                mutableUiState.update {
                    it.copy(
                        highlightStart = (phrase.start + event.start).coerceIn(phrase.start, phrase.end),
                        highlightEnd = (phrase.start + event.end).coerceIn(phrase.start, phrase.end),
                    )
                }
            }

            is SpeechEvent.Completed -> {
                val completed = speechBuffer.remove(event.utteranceId) ?: return
                recentPhraseCache.addLast(completed.phraseIndex)
                while (recentPhraseCache.size > SPEECH_BEHIND_PHRASES) recentPhraseCache.removeFirst()
                if (currentUtteranceId == event.utteranceId) {
                    currentUtteranceId = null
                }
                val state = mutableUiState.value
                val nextIndex = state.nextSpeakableIndex(
                    start = speechBuffer.lastPhraseIndex ?: completed.phraseIndex,
                    direction = 1,
                    includeStart = false,
                )
                when {
                    !speechBuffer.isEmpty -> {
                        // The neural player may be waiting for the next generated PCM buffer.
                        // Started immediately changes this back to PLAYING when audio is already ready.
                        mutableUiState.update { it.copy(playbackState = PlaybackState.PREPARING) }
                        replenishSpeechBuffer()
                    }
                    nextIndex == null -> {
                        mutableUiState.update { it.copy(playbackState = PlaybackState.PAUSED) }
                    }

                    else -> {
                        mutableUiState.update { it.copy(playbackState = PlaybackState.PREPARING) }
                        replenishSpeechBuffer(nextIndex)
                    }
                }
            }

            is SpeechEvent.Stopped -> Unit
            is SpeechEvent.Error -> {
                if (
                    event.utteranceId == null ||
                    event.utteranceId == currentUtteranceId ||
                    speechBuffer[event.utteranceId] != null
                ) {
                    speechFailure(IllegalStateException(event.message))
                }
            }
        }
    }

    private fun replenishSpeechBuffer(initialIndex: Int? = null) {
        if (speechQueueJob?.isActive == true || !speechBuffer.hasCapacity) return
        val state = mutableUiState.value
        val start = initialIndex ?: run {
            val tail = speechBuffer.lastPhraseIndex ?: state.activePhraseIndex ?: return
            state.nextSpeakableIndex(
                start = tail,
                direction = 1,
                includeStart = false,
            ) ?: return
        }
        val session = playbackSession
        speechQueueJob = viewModelScope.launch {
            runCatching {
                fillSpeechBuffer(
                    session = session,
                    initialIndex = start,
                    flushQueue = false,
                )
            }.onFailure(::handlePreparationFailure)
        }
    }

    private fun firstVisiblePhraseIndex(state: ReaderUiState): Int {
        val index = state.phrases.indexOfFirst {
            it.end > state.pageStart && SpeechText.isSpeakable(it.text)
        }
        return if (index >= 0) index else state.phrases.lastIndex.coerceAtLeast(0)
    }

    private fun handlePreparationFailure(error: Throwable) {
        if (error is CancellationException) return
        speechFailure(error)
    }

    private fun speechFailure(error: Throwable) {
        Log.e(TAG, "Speech playback failed", error)
        resetSpeechPipeline()
        mutableUiState.update {
            it.copy(
                playbackState = PlaybackState.PAUSED,
                errorMessage = error.message ?: "Speech playback failed.",
            )
        }
    }

    private fun stopPlayback(clearSelection: Boolean) {
        resetSpeechPipeline()
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

    private fun restartSleepTimer() {
        sleepTimerJob?.cancel()
        mutableUiState.update { it.copy(sleepTimerWarning = false) }
        val state = mutableUiState.value
        if (!state.sleepTimerEnabled) return
        sleepTimerJob = viewModelScope.launch {
            delay(state.sleepCheckMinutes * 60_000L)
            if (!mutableUiState.value.sleepTimerEnabled) return@launch
            mutableUiState.update { it.copy(sleepTimerWarning = true) }
            delay(mutableUiState.value.sleepGraceSeconds * 1_000L)
            if (mutableUiState.value.sleepTimerEnabled && mutableUiState.value.sleepTimerWarning) {
                pause()
                mutableUiState.update { it.copy(sleepTimerEnabled = false, sleepTimerWarning = false) }
            }
        }
    }

    private fun resetSpeechPipeline() {
        playbackSession++
        speechQueueJob?.cancel()
        speechQueueJob = null
        currentUtteranceId = null
        speechBuffer.clear()
        speechEngine.stop()
        generativeSpeechEngine.stop()
        advancedSpeechEngine.stop()
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

    private fun ReaderUiState.nextSpeakableIndex(
        start: Int,
        direction: Int,
        includeStart: Boolean,
    ): Int? {
        require(direction == -1 || direction == 1)
        var index = if (includeStart) start else start + direction
        while (index in phrases.indices) {
            if (SpeechText.isSpeakable(phrases[index].text)) return index
            index += direction
        }
        return null
    }

    private fun activeSpeechEngine(): SpeechEngine = when (mutableUiState.value.speechEngineMode) {
        SpeechEngineMode.PROCEDURAL -> speechEngine
        SpeechEngineMode.LOCAL_GENERATIVE -> generativeSpeechEngine
        SpeechEngineMode.LOCAL_ADVANCED -> advancedSpeechEngine
    }

    private fun applyVoiceOverrides(
        profiles: List<CharacterProfile>,
        overrides: List<CharacterProfile>,
    ): List<CharacterProfile> {
        val overrideById = overrides.associateBy(CharacterProfile::id)
        val withNarrator = if (profiles.any { it.id == NarratorProfile.id }) {
            profiles
        } else {
            profiles + NarratorProfile
        }
        return withNarrator.map { profile ->
            val override = overrideById[profile.id] ?: return@map profile
            profile.copy(
                voiceGroup = override.voiceGroup,
                voiceSlot = override.voiceSlot,
                pitch = override.pitch,
                speechRate = override.speechRate,
                localVoicePackId = override.localVoicePackId,
                advancedVoiceId = override.advancedVoiceId,
            )
        }
    }

    private companion object {
        const val TAG = "VoxReader"
        const val SPEECH_LOOK_AHEAD_PHRASES = 5
        const val SPEECH_BEHIND_PHRASES = 3
        const val MAX_HISTORY_ENTRIES = 100
    }

    class Factory(
        private val importBook: ImportBookUseCase,
        private val loadLatestBook: LoadLatestBookUseCase,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
        private val speechEngine: SpeechEngine,
        private val proceduralVoiceCatalog: ProceduralVoiceCatalog,
        private val generativeSpeechEngine: SpeechEngine,
        private val piperVoicePackManager: PiperVoicePackManager,
        private val advancedSpeechEngine: SpeechEngine,
        private val advancedVoiceModelManager: AdvancedVoiceModelManager,
        private val portableAnalysisService: PortableAnalysisService,
        private val playbackNotificationController: PlaybackNotificationController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                importBook = importBook,
                loadLatestBook = loadLatestBook,
                libraryRepository = libraryRepository,
                settingsRepository = settingsRepository,
                speechEngine = speechEngine,
                proceduralVoiceCatalog = proceduralVoiceCatalog,
                generativeSpeechEngine = generativeSpeechEngine,
                piperVoicePackManager = piperVoicePackManager,
                advancedSpeechEngine = advancedSpeechEngine,
                advancedVoiceModelManager = advancedVoiceModelManager,
                portableAnalysisService = portableAnalysisService,
                playbackNotificationController = playbackNotificationController,
            ) as T
    }
}
