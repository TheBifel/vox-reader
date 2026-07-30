package com.bifel.voxreader.presentation.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bifel.voxreader.domain.analysis.CharacterAnalysisRequest
import com.bifel.voxreader.domain.analysis.CharacterAnalyzer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SettingsRepository
import com.bifel.voxreader.domain.speech.EmotionDelivery
import com.bifel.voxreader.domain.speech.BufferedPhrase
import com.bifel.voxreader.domain.speech.PhrasePlaybackBuffer
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.speech.SpeechText
import com.bifel.voxreader.domain.text.SentenceSegmenter
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import com.bifel.voxreader.data.preanalysis.PortableAnalysisService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val importBook: ImportBookUseCase,
    private val loadLatestBook: LoadLatestBookUseCase,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val characterAnalyzer: CharacterAnalyzer,
    private val speechEngine: SpeechEngine,
    private val portableAnalysisService: PortableAnalysisService,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var initialized = false
    private var storedBook: StoredBook? = null
    private val analyzedPhraseRanges = mutableListOf<IntRange>()
    private var currentUtteranceId: String? = null
    private var speechQueueJob: Job? = null
    private var analysisLookAheadJob: Job? = null
    private val speechBuffer = PhrasePlaybackBuffer(SPEECH_LOOK_AHEAD_PHRASES)
    private var playbackSession = 0L
    private val analysisMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(
                        darkTheme = settings.darkTheme,
                        speechControlsEnabled = settings.speechControlsEnabled,
                        playbackSpeed = settings.playbackSpeed,
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

    fun updateCharacterVoice(profile: CharacterProfile) {
        val state = mutableUiState.value
        val book = state.book ?: return
        val updatedCharacters = (state.characters.filterNot { it.id == profile.id } + profile)
            .sortedWith(
                compareBy<CharacterProfile>({ it.id != NarratorProfile.id }, { it.displayName }),
            )
        storedBook = storedBook?.let { stored ->
            stored.copy(
                characters = updatedCharacters,
                voiceOverrides = stored.voiceOverrides.filterNot { it.id == profile.id } + profile,
            )
        }
        mutableUiState.update { it.copy(characters = updatedCharacters) }
        viewModelScope.launch {
            libraryRepository.updateVoiceOverride(book.id, profile)
            portableAnalysisService.updateVoiceProfile(book.id, profile)
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

    fun dismissError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    suspend fun createAnalysisSidecar(): String {
        val state = mutableUiState.value
        val book = requireNotNull(state.book) { "Open a book before exporting analysis." }
        return withContext(Dispatchers.Default) {
            portableAnalysisService.export(book, state.phrases)
        }
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
                    storedBook?.voiceOverrides.orEmpty().forEach { override ->
                        portableAnalysisService.updateVoiceProfile(book.id, override)
                    }
                    val cached = portableAnalysisService.cached(
                        book = book,
                        phrases = SentenceSegmenter.segment(book.text),
                    )
                    result to cached
                }
            }.onSuccess { (result, cached) ->
                analyzedPhraseRanges.clear()
                val overrides = storedBook?.voiceOverrides.orEmpty()
                val characters = applyVoiceOverrides(
                    cached.characters.ifEmpty { listOf(NarratorProfile) },
                    overrides,
                )
                storedBook = storedBook?.copy(characters = characters)
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

    fun analysisFileExported() {
        mutableUiState.update {
            it.copy(analysisMessage = "Analysis JSON exported. Process it externally, then import it.")
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
        analyzedPhraseRanges.clear()
        val segmentedPhrases = SentenceSegmenter.segment(document.text)
        val cached = withContext(Dispatchers.Default) {
            portableAnalysisService.cached(document, segmentedPhrases)
        }
        val phrases = cached.phrases
        val baseCharacters = if (cached.providerId != null) {
            cached.characters.ifEmpty { listOf(NarratorProfile) }
        } else {
            stored.characters.ifEmpty { listOf(NarratorProfile) }
        }
        val characters = applyVoiceOverrides(baseCharacters, stored.voiceOverrides)
        storedBook = stored.copy(characters = characters)
        val restoredPosition = stored.readingPosition.coerceIn(0, document.text.length)
        mutableUiState.update { current ->
            current.copy(
                isLoading = false,
                book = document,
                phrases = phrases,
                characters = characters,
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
                cachedSpeakerPhraseIndices = cached.cachedPhraseIndices,
                phraseEmotions = cached.emotionsByPhraseIndex,
                cachedAttributionProvider = cached.providerId,
                cachedAttributionCoverage = cached.coverage,
                analysisMessage = cached.providerId?.let {
                    "Using cached speaker analysis from $it."
                },
                errorMessage = null,
            )
        }
    }

    private fun prepareAndPlay() {
        val state = mutableUiState.value
        state.book ?: return
        if (state.phrases.isEmpty()) return

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
        speechQueueJob = viewModelScope.launch {
            runCatching {
                speechEngine.awaitReady()
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
        val book = mutableUiState.value.book ?: return
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
            ensurePhraseAnalyzed(book, phraseIndex)
            if (session != playbackSession) return
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
        val profile = state.characters.firstOrNull { it.id == phrase.speakerId }
            ?: NarratorProfile
        val emotion = state.phraseEmotions[phrase.index]
        val delivery = EmotionDelivery.from(emotion)
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
        speechEngine.enqueue(
            SpeechRequest(
                utteranceId = utteranceId,
                text = SpeechText.withoutSpokenQuotes(phrase.text),
                voiceSlot = profile.voiceSlot,
                voiceGroup = profile.voiceGroup,
                pitch = pitch,
                speechRate = speechRate,
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
                startAnalysisLookAhead(buffered.phraseIndex + 1)
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
                    !speechBuffer.isEmpty -> replenishSpeechBuffer()
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

    private fun isPhraseAnalyzed(index: Int): Boolean =
        index in mutableUiState.value.cachedSpeakerPhraseIndices ||
            analyzedPhraseRanges.any { index in it }

    private suspend fun ensurePhraseAnalyzed(
        book: BookDocument,
        index: Int,
    ) {
        if (isPhraseAnalyzed(index)) return
        analysisMutex.withLock {
            if (isPhraseAnalyzed(index)) return@withLock
            val latest = mutableUiState.value
            val phrase = latest.phrases.getOrNull(index)
                ?: error("The requested sentence is no longer available.")
            val analysis = characterAnalyzer.analyze(
                CharacterAnalysisRequest(
                    text = book.text,
                    knownCharacters = latest.characters,
                    existingPhrases = latest.phrases,
                    focusPosition = phrase.start,
                    preservedPhraseRanges = analyzedPhraseRanges.toList() +
                        cachedPhraseRanges(latest.cachedSpeakerPhraseIndices),
                ),
            )
            val mergedPhrases = analysis.phrases.mapIndexed { phraseIndex, analyzed ->
                if (phraseIndex in latest.cachedSpeakerPhraseIndices) {
                    latest.phrases.getOrNull(phraseIndex) ?: analyzed
                } else {
                    analyzed
                }
            }
            val mergedCharacters = (latest.characters + analysis.characters)
                .distinctBy(CharacterProfile::id)
            val resolvedPhrase = mergedPhrases.getOrNull(index)
            val resolvedProfile = mergedCharacters.firstOrNull {
                it.id == resolvedPhrase?.speakerId
            } ?: NarratorProfile
            Log.d(
                TAG,
                "Attributed phrase $index [${resolvedPhrase?.start},${resolvedPhrase?.end}) as " +
                    "${resolvedProfile.displayName} " +
                    "(${resolvedProfile.voiceGroup}) using ${analysis.source}",
            )
            analysis.analyzedPhraseRange?.let(::rememberAnalyzedRange)
            storedBook = storedBook?.copy(characters = mergedCharacters)
            mutableUiState.update {
                it.copy(
                    phrases = mergedPhrases,
                    characters = mergedCharacters,
                    lastAnalysisSource = analysis.source,
                )
            }
            libraryRepository.updateCharacters(book.id, mergedCharacters)
        }
    }

    private fun cachedPhraseRanges(indices: Set<Int>): List<IntRange> {
        if (indices.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        indices.sorted().forEach { index ->
            val previous = ranges.lastOrNull()
            if (previous != null && index == previous.last + 1) {
                ranges[ranges.lastIndex] = previous.first..index
            } else {
                ranges += index..index
            }
        }
        return ranges
    }

    /**
     * Character attribution is intentionally pipelined with speech. While Android TTS reads
     * the current sentence, TinyBERT resolves a short window ahead in the background.
     */
    private fun startAnalysisLookAhead(fromIndex: Int) {
        if (analysisLookAheadJob?.isActive == true) return
        val book = mutableUiState.value.book ?: return
        val lastIndex = mutableUiState.value.phrases.lastIndex
        if (fromIndex !in 0..lastIndex) return
        val windowEnd = (fromIndex + ANALYSIS_LOOK_AHEAD_PHRASES - 1)
            .coerceAtMost(lastIndex)

        analysisLookAheadJob = viewModelScope.launch {
            runCatching {
                while (mutableUiState.value.playbackState == PlaybackState.PLAYING ||
                    mutableUiState.value.playbackState == PlaybackState.PREPARING
                ) {
                    val nextUnresolved = (fromIndex..windowEnd)
                        .firstOrNull {
                            !isPhraseAnalyzed(it) &&
                                SpeechText.isSpeakable(mutableUiState.value.phrases[it].text)
                        }
                        ?: break
                    ensurePhraseAnalyzed(book, nextUnresolved)
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    Log.w(TAG, "Character look-ahead stopped; playback will retry on demand", error)
                }
            }
        }
    }

    private fun rememberAnalyzedRange(newRange: IntRange) {
        if (newRange.isEmpty()) return
        val allRanges = buildList {
            addAll(analyzedPhraseRanges)
            add(newRange)
        }.sortedBy { it.first }
        analyzedPhraseRanges.clear()
        allRanges.forEach { range ->
            val previous = analyzedPhraseRanges.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                analyzedPhraseRanges[analyzedPhraseRanges.lastIndex] =
                    previous.first..maxOf(previous.last, range.last)
            } else {
                analyzedPhraseRanges.add(range)
            }
        }
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

    private fun resetSpeechPipeline() {
        playbackSession++
        speechQueueJob?.cancel()
        speechQueueJob = null
        analysisLookAheadJob?.cancel()
        analysisLookAheadJob = null
        currentUtteranceId = null
        speechBuffer.clear()
        speechEngine.stop()
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
            )
        }
    }

    private companion object {
        const val TAG = "VoxReader"
        const val ANALYSIS_LOOK_AHEAD_PHRASES = 6
        const val SPEECH_LOOK_AHEAD_PHRASES = 3
    }

    class Factory(
        private val importBook: ImportBookUseCase,
        private val loadLatestBook: LoadLatestBookUseCase,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
        private val characterAnalyzer: CharacterAnalyzer,
        private val speechEngine: SpeechEngine,
        private val portableAnalysisService: PortableAnalysisService,
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
                portableAnalysisService = portableAnalysisService,
            ) as T
    }
}
