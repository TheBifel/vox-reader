package com.bifel.voxreader.application.reader

import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.speech.BufferedPhrase
import com.bifel.voxreader.domain.speech.EmotionDelivery
import com.bifel.voxreader.domain.speech.PhrasePlaybackBuffer
import com.bifel.voxreader.domain.speech.PlaybackNotificationController
import com.bifel.voxreader.domain.speech.SpeechEngineRegistry
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.speech.SpeechText
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackStatus {
    DISABLED,
    PAUSED,
    PREPARING,
    PLAYING,
}

data class PlaybackContext(
    val bookId: String? = null,
    val bookTitle: String = "",
    val phrases: List<SpeechPhrase> = emptyList(),
    val characters: List<CharacterProfile> = listOf(NarratorProfile),
    val emotionsByPhraseIndex: Map<Int, PhraseEmotion> = emptyMap(),
    val hasSpeakerMap: Boolean = false,
    val firstVisibleOffset: Int = 0,
    val controlsEnabled: Boolean = false,
    val engineMode: SpeechEngineMode = SpeechEngineMode.PROCEDURAL,
    val playbackSpeed: Float = 1f,
    val postProcessing: SpeechPostProcessingSettings = SpeechPostProcessingSettings(),
    val sleepCheckMinutes: Int = 5,
    val sleepGraceSeconds: Int = 30,
)

data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.DISABLED,
    val activePhraseIndex: Int? = null,
    val highlightStart: Int? = null,
    val highlightEnd: Int? = null,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerWarning: Boolean = false,
    val errorMessage: String? = null,
)

/** Owns the complete phrase playback state machine independently of Android lifecycle classes. */
class ReaderPlaybackController(
    private val engines: SpeechEngineRegistry,
    private val notification: PlaybackNotificationController,
    private val scope: CoroutineScope,
) : Closeable {

    private val mutableState = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = mutableState.asStateFlow()

    private var context = PlaybackContext()
    private var currentUtteranceId: String? = null
    private var queueJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val buffer = PhrasePlaybackBuffer(LOOK_AHEAD_PHRASES)
    private val recentPhraseIndices = ArrayDeque<Int>()
    private var session = 0L
    private var waitingForPhrases = false

    init {
        SpeechEngineMode.entries.forEach { mode ->
            scope.launch {
                engines.engine(mode).events.collect { event -> handleEvent(mode, event) }
            }
        }
        scope.launch {
            notification.commands.collect { command ->
                when (command) {
                    com.bifel.voxreader.domain.speech.PlaybackCommand.PLAY -> play()
                    com.bifel.voxreader.domain.speech.PlaybackCommand.PAUSE -> pause()
                    com.bifel.voxreader.domain.speech.PlaybackCommand.NEXT -> movePhrase(1)
                    com.bifel.voxreader.domain.speech.PlaybackCommand.PREVIOUS -> movePhrase(-1)
                }
            }
        }
    }

    fun updateContext(updated: PlaybackContext) {
        val previous = context
        context = updated
        when {
            previous.bookId != updated.bookId -> stop(clearSelection = true)
            previous.engineMode != updated.engineMode -> pause()
            !updated.controlsEnabled -> disable()
            previous.controlsEnabled != updated.controlsEnabled && updated.controlsEnabled -> {
                mutableState.update {
                    it.copy(status = PlaybackStatus.PAUSED, errorMessage = null)
                }
            }
        }
        if (
            waitingForPhrases &&
            previous.bookId == updated.bookId &&
            updated.phrases.isNotEmpty()
        ) {
            waitingForPhrases = false
            play()
        }
    }

    fun playOrPause() {
        when (state.value.status) {
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
            -> pause()
            PlaybackStatus.PAUSED -> play()
            PlaybackStatus.DISABLED -> Unit
        }
    }

    fun play() {
        if (!context.controlsEnabled || context.bookId == null) return
        if (context.phrases.isEmpty()) {
            waitingForPhrases = true
            mutableState.update { it.copy(status = PlaybackStatus.PREPARING) }
            return
        }
        val availability = engines.availability(context.engineMode)
        if (!availability.available) {
            fail(IllegalStateException(availability.unavailableMessage ?: "The selected speech engine is unavailable."))
            return
        }
        val requested = state.value.activePhraseIndex
            ?.coerceIn(context.phrases.indices)
            ?: firstVisiblePhraseIndex()
        val index = nextSpeakableIndex(requested, direction = 1, includeStart = true) ?: return
        preparePhraseAndPlay(index)
    }

    fun pause() {
        resetPipeline()
        mutableState.update {
            it.copy(
                status = if (context.controlsEnabled) PlaybackStatus.PAUSED else PlaybackStatus.DISABLED,
            )
        }
        if (context.bookId != null) notification.update(context.bookTitle, false)
    }

    fun disable() {
        stop(clearSelection = false)
        notification.stop()
    }

    fun stop(clearSelection: Boolean) {
        resetPipeline()
        mutableState.update {
            it.copy(
                status = if (context.controlsEnabled) PlaybackStatus.PAUSED else PlaybackStatus.DISABLED,
                activePhraseIndex = if (clearSelection) null else it.activePhraseIndex,
                highlightStart = if (clearSelection) null else it.highlightStart,
                highlightEnd = if (clearSelection) null else it.highlightEnd,
            )
        }
    }

    fun restartActivePhrase() {
        state.value.activePhraseIndex
            ?.takeIf { state.value.status == PlaybackStatus.PLAYING || state.value.status == PlaybackStatus.PREPARING }
            ?.let(::preparePhraseAndPlay)
    }

    fun movePhrase(delta: Int) {
        if (context.phrases.isEmpty()) return
        val wasPlaying = state.value.status == PlaybackStatus.PLAYING ||
            state.value.status == PlaybackStatus.PREPARING
        val base = state.value.activePhraseIndex ?: firstVisiblePhraseIndex()
        val target = nextSpeakableIndex(base, direction = delta, includeStart = false) ?: base
        val phrase = context.phrases[target]
        if (wasPlaying) {
            preparePhraseAndPlay(target)
        } else {
            resetPipeline()
            mutableState.update {
                it.copy(
                    activePhraseIndex = target,
                    highlightStart = phrase.start,
                    highlightEnd = phrase.end,
                    status = PlaybackStatus.PAUSED,
                )
            }
        }
    }

    fun onUserNavigation() {
        if (state.value.status == PlaybackStatus.PLAYING || state.value.status == PlaybackStatus.PREPARING) {
            pause()
        }
        mutableState.update {
            it.copy(activePhraseIndex = null, highlightStart = null, highlightEnd = null)
        }
    }

    fun preview(mode: SpeechEngineMode, request: SpeechRequest) {
        resetPipeline()
        mutableState.update {
            it.copy(status = if (context.controlsEnabled) PlaybackStatus.PAUSED else PlaybackStatus.DISABLED)
        }
        scope.launch {
            runCatching {
                val availability = engines.availability(mode)
                check(availability.available) {
                    availability.unavailableMessage ?: "The selected speech engine is unavailable."
                }
                engines.engine(mode).awaitReady()
                engines.engine(mode).enqueue(request, SpeechQueueMode.FLUSH)
            }.onFailure(::handlePreparationFailure)
        }
    }

    fun toggleSleepTimer() {
        val enabled = !state.value.sleepTimerEnabled
        mutableState.update { it.copy(sleepTimerEnabled = enabled, sleepTimerWarning = false) }
        if (enabled) restartSleepTimer() else sleepTimerJob?.cancel()
    }

    fun registerUserActivity() {
        if (state.value.sleepTimerEnabled) restartSleepTimer()
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun preparePhraseAndPlay(index: Int) {
        if (index !in context.phrases.indices) return
        if (!SpeechText.isSpeakable(context.phrases[index].text)) {
            val next = nextSpeakableIndex(index, direction = 1, includeStart = false)
            if (next == null) mutableState.update { it.copy(status = PlaybackStatus.PAUSED) }
            else preparePhraseAndPlay(next)
            return
        }
        resetPipeline()
        val currentSession = session
        val phrase = context.phrases[index]
        mutableState.update {
            it.copy(
                activePhraseIndex = index,
                highlightStart = phrase.start,
                highlightEnd = phrase.end,
                status = PlaybackStatus.PREPARING,
                errorMessage = null,
            )
        }
        notification.update(context.bookTitle, true)
        if (state.value.sleepTimerEnabled && sleepTimerJob?.isActive != true) restartSleepTimer()
        queueJob = scope.launch {
            runCatching {
                engines.engine(context.engineMode).awaitReady()
                fillBuffer(currentSession, index, flushQueue = true)
            }.onFailure(::handlePreparationFailure)
        }
    }

    private suspend fun fillBuffer(currentSession: Long, initialIndex: Int, flushQueue: Boolean) {
        var index: Int? = initialIndex
        var queueMode = if (flushQueue) SpeechQueueMode.FLUSH else SpeechQueueMode.APPEND
        while (currentSession == session && buffer.hasCapacity && index != null) {
            val phraseIndex = index
            if (!buffer.containsPhrase(phraseIndex)) {
                enqueuePhrase(phraseIndex, currentSession, queueMode)
                queueMode = SpeechQueueMode.APPEND
            }
            index = nextSpeakableIndex(phraseIndex, direction = 1, includeStart = false)
        }
    }

    private fun enqueuePhrase(index: Int, currentSession: Long, queueMode: SpeechQueueMode) {
        val phrase = context.phrases.getOrNull(index) ?: return
        val profile = if (!context.hasSpeakerMap) {
            context.characters.firstOrNull { it.id == NarratorProfile.id } ?: NarratorProfile
        } else {
            context.characters.firstOrNull { it.id == phrase.speakerId } ?: NarratorProfile
        }
        val delivery = EmotionDelivery.from(
            context.emotionsByPhraseIndex[phrase.index],
            context.postProcessing,
        )
        val utteranceId = "${context.bookId}:$currentSession:${phrase.index}:${System.nanoTime()}"
        buffer.add(BufferedPhrase(utteranceId, index))
        engines.engine(context.engineMode).enqueue(
            SpeechRequest(
                utteranceId = utteranceId,
                text = SpeechText.withoutSpokenQuotes(phrase.text),
                voiceSlot = profile.voiceSlot,
                voiceGroup = profile.voiceGroup,
                pitch = (profile.pitch * delivery.pitchMultiplier).coerceIn(0.5f, 1.6f),
                speechRate = (
                    profile.speechRate * context.playbackSpeed * delivery.speechRateMultiplier
                    ).coerceIn(0.5f, 2f),
                proceduralVoiceId = profile.proceduralVoiceId,
                localVoicePackId = profile.localVoicePackId,
                advancedVoiceId = profile.advancedVoiceId,
                postProcessingPitch = delivery.pitchMultiplier,
                loudness = delivery.loudnessMultiplier,
                tone = delivery.tone,
                pauseAfterMillis = delivery.pauseAfterMillis,
            ),
            queueMode,
        )
    }

    private fun handleEvent(mode: SpeechEngineMode, event: SpeechEvent) {
        if (mode != context.engineMode) return
        when (event) {
            SpeechEvent.Ready -> Unit
            is SpeechEvent.Started -> {
                val buffered = buffer[event.utteranceId] ?: return
                val phrase = context.phrases.getOrNull(buffered.phraseIndex) ?: return
                currentUtteranceId = event.utteranceId
                mutableState.update {
                    it.copy(
                        activePhraseIndex = buffered.phraseIndex,
                        highlightStart = phrase.start,
                        highlightEnd = phrase.end,
                        status = PlaybackStatus.PLAYING,
                    )
                }
            }
            is SpeechEvent.Range -> if (event.utteranceId == currentUtteranceId) {
                val buffered = buffer[event.utteranceId] ?: return
                val phrase = context.phrases.getOrNull(buffered.phraseIndex) ?: return
                mutableState.update {
                    it.copy(
                        highlightStart = (phrase.start + event.start).coerceIn(phrase.start, phrase.end),
                        highlightEnd = (phrase.start + event.end).coerceIn(phrase.start, phrase.end),
                    )
                }
            }
            is SpeechEvent.Completed -> complete(event.utteranceId)
            is SpeechEvent.Stopped -> Unit
            is SpeechEvent.Error -> if (
                event.utteranceId == null ||
                event.utteranceId == currentUtteranceId ||
                buffer[event.utteranceId] != null
            ) {
                fail(IllegalStateException(event.message))
            }
        }
    }

    private fun complete(utteranceId: String) {
        val completed = buffer.remove(utteranceId) ?: return
        recentPhraseIndices.addLast(completed.phraseIndex)
        while (recentPhraseIndices.size > BEHIND_PHRASES) recentPhraseIndices.removeFirst()
        if (currentUtteranceId == utteranceId) currentUtteranceId = null
        val next = nextSpeakableIndex(
            buffer.lastPhraseIndex ?: completed.phraseIndex,
            direction = 1,
            includeStart = false,
        )
        when {
            !buffer.isEmpty -> {
                mutableState.update { it.copy(status = PlaybackStatus.PREPARING) }
                replenishBuffer()
            }
            next == null -> mutableState.update { it.copy(status = PlaybackStatus.PAUSED) }
            else -> {
                mutableState.update { it.copy(status = PlaybackStatus.PREPARING) }
                replenishBuffer(next)
            }
        }
    }

    private fun replenishBuffer(initialIndex: Int? = null) {
        if (queueJob?.isActive == true || !buffer.hasCapacity) return
        val start = initialIndex ?: run {
            val tail = buffer.lastPhraseIndex ?: state.value.activePhraseIndex ?: return
            nextSpeakableIndex(tail, direction = 1, includeStart = false) ?: return
        }
        val currentSession = session
        queueJob = scope.launch {
            runCatching { fillBuffer(currentSession, start, flushQueue = false) }
                .onFailure(::handlePreparationFailure)
        }
    }

    private fun firstVisiblePhraseIndex(): Int {
        val index = context.phrases.indexOfFirst {
            it.end > context.firstVisibleOffset && SpeechText.isSpeakable(it.text)
        }
        return if (index >= 0) index else context.phrases.lastIndex.coerceAtLeast(0)
    }

    private fun nextSpeakableIndex(start: Int, direction: Int, includeStart: Boolean): Int? {
        require(direction == -1 || direction == 1)
        var index = if (includeStart) start else start + direction
        while (index in context.phrases.indices) {
            if (SpeechText.isSpeakable(context.phrases[index].text)) return index
            index += direction
        }
        return null
    }

    private fun restartSleepTimer() {
        sleepTimerJob?.cancel()
        mutableState.update { it.copy(sleepTimerWarning = false) }
        if (!state.value.sleepTimerEnabled) return
        sleepTimerJob = scope.launch {
            delay(context.sleepCheckMinutes * 60_000L)
            if (!state.value.sleepTimerEnabled) return@launch
            mutableState.update { it.copy(sleepTimerWarning = true) }
            delay(context.sleepGraceSeconds * 1_000L)
            if (state.value.sleepTimerEnabled && state.value.sleepTimerWarning) {
                pause()
                mutableState.update { it.copy(sleepTimerEnabled = false, sleepTimerWarning = false) }
            }
        }
    }

    private fun handlePreparationFailure(error: Throwable) {
        if (error !is CancellationException) fail(error)
    }

    private fun fail(error: Throwable) {
        resetPipeline()
        mutableState.update {
            it.copy(
                status = if (context.controlsEnabled) PlaybackStatus.PAUSED else PlaybackStatus.DISABLED,
                errorMessage = error.message ?: "Speech playback failed.",
            )
        }
    }

    private fun resetPipeline() {
        session++
        queueJob?.cancel()
        queueJob = null
        currentUtteranceId = null
        waitingForPhrases = false
        buffer.clear()
        engines.stopAll()
    }

    override fun close() {
        sleepTimerJob?.cancel()
        resetPipeline()
        notification.stop()
    }

    companion object {
        const val LOOK_AHEAD_PHRASES = 5
        const val BEHIND_PHRASES = 3
    }
}

class ReaderPlaybackControllerFactory(
    private val engines: SpeechEngineRegistry,
    private val notification: PlaybackNotificationController,
) {
    fun create(scope: CoroutineScope): ReaderPlaybackController =
        ReaderPlaybackController(engines, notification, scope)
}
