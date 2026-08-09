package com.bifel.voxreader.data.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Generates neural speech ahead of playback and retains a small LRU of PCM results.
 *
 * A single generator preserves phrase order and avoids invoking one native model concurrently.
 * Playback runs independently, so phrase N+1 is synthesized while phrase N is audible.
 */
internal class BufferedPcmSpeechPipeline(
    private val events: MutableSharedFlow<SpeechEvent>,
    private val engineName: String,
    private val synthesize: (SpeechRequest, isCurrent: () -> Boolean) -> PcmAudio,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<QueuedRequest>(Channel.UNLIMITED)
    private val generated = Channel<GeneratedRequest>(Channel.UNLIMITED)
    private val generation = AtomicLong(0)
    private val stopped = AtomicBoolean(false)
    private val cache = object : LinkedHashMap<PcmCacheKey, PcmAudio>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PcmCacheKey, PcmAudio>?): Boolean =
            size > PCM_CACHE_ENTRIES
    }
    private val trackLock = Any()
    private var track: AudioTrack? = null
    private var trackSampleRate = 0

    init {
        scope.launch { generateLoop() }
        scope.launch { playbackLoop() }
    }

    fun enqueue(request: SpeechRequest, queueMode: SpeechQueueMode) {
        val generationId = if (queueMode == SpeechQueueMode.FLUSH) {
            stopped.set(true)
            generation.incrementAndGet().also {
                clear(requests)
                clear(generated)
                flushTrack()
            }
        } else {
            generation.get()
        }
        stopped.set(false)
        requests.trySend(QueuedRequest(request, generationId))
    }

    fun stop() {
        stopped.set(true)
        generation.incrementAndGet()
        clear(requests)
        clear(generated)
        flushTrack()
    }

    private suspend fun generateLoop() {
        for (queued in requests) {
            if (!queued.isCurrent()) continue
            val key = PcmCacheKey.from(queued.request)
            val startedAt = SystemClock.elapsedRealtime()
            val cached = synchronized(cache) { cache[key] }
            val audio = cached ?: runCatching {
                NeuralPcmPostProcessor.process(
                    synthesize(queued.request, queued::isCurrent),
                    queued.request,
                )
            }.getOrElse { error ->
                if (queued.isCurrent()) {
                    events.emit(
                        SpeechEvent.Error(
                            queued.request.utteranceId,
                            error.message ?: "$engineName speech generation failed.",
                        ),
                    )
                }
                continue
            }
            if (!queued.isCurrent()) continue
            if (cached == null) synchronized(cache) { cache[key] = audio }
            val audioMillis = audio.samples.size * 1_000L / audio.sampleRate
            Log.i(
                TAG,
                "$engineName ${if (cached == null) "generated" else "cache hit"}: " +
                    "${audioMillis}ms audio in ${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            generated.send(GeneratedRequest(queued, audio))
        }
    }

    private suspend fun playbackLoop() {
        for (item in generated) {
            val queued = item.queued
            if (!queued.isCurrent()) continue
            val audioTrack = trackFor(item.audio.sampleRate)
            events.emit(SpeechEvent.Started(queued.request.utteranceId))
            val playbackError = runCatching {
                if (queued.isCurrent()) {
                    audioTrack.play()
                    val written = audioTrack.write(
                        item.audio.samples,
                        0,
                        item.audio.samples.size,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    check(written >= 0) { "Audio playback failed ($written)." }
                }
            }.exceptionOrNull()
            if (playbackError != null) {
                if (queued.isCurrent()) {
                    events.emit(
                        SpeechEvent.Error(
                            queued.request.utteranceId,
                            playbackError.message ?: "Audio playback failed.",
                        ),
                    )
                }
                continue
            }
            if (!queued.isCurrent()) continue
            // write() returns when the final buffer was accepted, slightly before it is audible.
            delay(FINAL_AUDIO_DRAIN_MILLIS + queued.request.pauseAfterMillis)
            if (queued.isCurrent()) events.emit(SpeechEvent.Completed(queued.request.utteranceId))
        }
    }

    private fun trackFor(sampleRate: Int): AudioTrack = synchronized(trackLock) {
        track?.takeIf { trackSampleRate == sampleRate } ?: run {
            track?.release()
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(sampleRate / 4)
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
                minBuffer,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ).also {
                track = it
                trackSampleRate = sampleRate
            }
        }
    }

    private fun flushTrack() = synchronized(trackLock) {
        track?.let { current ->
            runCatching {
                current.pause()
                current.flush()
            }
        }
    }

    private fun <T> clear(channel: Channel<T>) {
        while (channel.tryReceive().isSuccess) Unit
    }

    private inner class QueuedRequest(
        val request: SpeechRequest,
        val generationId: Long,
    ) {
        fun isCurrent(): Boolean = generationId == generation.get() && !stopped.get()
    }

    private data class GeneratedRequest(
        val queued: BufferedPcmSpeechPipeline.QueuedRequest,
        val audio: PcmAudio,
    )

    private data class PcmCacheKey(
        val text: String,
        val voiceSlot: Int,
        val voiceGroup: String,
        val pitch: Float,
        val speechRate: Float,
        val proceduralVoiceId: String?,
        val localVoicePackId: String?,
        val advancedVoiceId: String?,
        val postProcessingPitch: Float,
        val loudness: Float,
        val tone: Float,
    ) {
        companion object {
            fun from(request: SpeechRequest) = PcmCacheKey(
                text = request.text,
                voiceSlot = request.voiceSlot,
                voiceGroup = request.voiceGroup.name,
                pitch = request.pitch,
                speechRate = request.speechRate,
                proceduralVoiceId = request.proceduralVoiceId,
                localVoicePackId = request.localVoicePackId,
                advancedVoiceId = request.advancedVoiceId,
                postProcessingPitch = request.postProcessingPitch,
                loudness = request.loudness,
                tone = request.tone,
            )
        }
    }

    private companion object {
        const val TAG = "VoxSpeechBuffer"
        const val PCM_CACHE_ENTRIES = 8
        const val FINAL_AUDIO_DRAIN_MILLIS = 70L
    }
}

internal data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
)
