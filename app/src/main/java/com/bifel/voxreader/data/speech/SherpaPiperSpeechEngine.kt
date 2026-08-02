package com.bifel.voxreader.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.io.File

/** Offline Piper/VITS synthesis from voice packs downloaded into app-private storage. */
class SherpaPiperSpeechEngine(
    context: Context,
    private val voicePacks: PiperVoicePackManager,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventsFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SpeechEvent> = eventsFlow.asSharedFlow()
    private val ready = MutableStateFlow(false)
    private val initLock = Mutex()
    private val queue = Channel<QueuedRequest>(Channel.UNLIMITED)
    private val generation = AtomicLong(0)
    private val stopped = AtomicBoolean(false)
    private val engines = mutableMapOf<String, OfflineTts>()
    private var track: AudioTrack? = null
    @Volatile private var activeRequest: QueuedRequest? = null
    @Volatile private var activeStarted = false

    init {
        scope.launch {
            for (queued in queue) play(queued)
        }
    }

    override suspend fun awaitReady() {
        if (!ready.value) initialize()
        ready.first { it }
    }

    override fun enqueue(request: SpeechRequest, queueMode: SpeechQueueMode) {
        if (!ready.value) {
            eventsFlow.tryEmit(SpeechEvent.Error(request.utteranceId, "Local AI voice is still loading."))
            return
        }
        val generationId = if (queueMode == SpeechQueueMode.FLUSH) {
            stopped.set(true)
            generation.incrementAndGet().also { clearQueue() }
        } else {
            generation.get()
        }
        stopped.set(false)
        queue.trySend(QueuedRequest(request, generationId))
    }

    override fun stop() {
        stopped.set(true)
        generation.incrementAndGet()
        clearQueue()
        track?.let {
            runCatching { it.pause(); it.flush() }
        }
    }

    private suspend fun initialize() {
        initLock.withLock {
            if (ready.value) return@withLock
        runCatching {
            val installed = voicePacks.installedPacks()
            check(installed.isNotEmpty()) { "Download at least one Local AI voice first." }
            val sampleRate = engineFor(installed.first()).sampleRate()
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(sampleRate / 4)
            track = AudioTrack(
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
            )
            ready.value = true
            eventsFlow.tryEmit(SpeechEvent.Ready)
            }.onFailure { error ->
                eventsFlow.tryEmit(SpeechEvent.Error(null, "Could not load Local AI voice: ${error.message}"))
                throw error
            }
        }
    }

    private fun play(queued: QueuedRequest) {
        if (queued.generationId != generation.get() || stopped.get()) return
        val request = queued.request
        activeRequest = queued
        activeStarted = false
        val audio = runCatching {
            voiceFor(request).let { pack -> engineFor(pack).generateWithConfigAndCallback(
                text = request.text,
                config = GenerationConfig(
                    speed = request.speechRate.coerceIn(0.5f, 2f),
                    sid = pack.speakerId,
                ),
                callback = this::onGeneratedSamples,
            ) }
        }.getOrElse { error ->
            eventsFlow.tryEmit(SpeechEvent.Error(request.utteranceId, error.message ?: "AI speech generation failed."))
            return
        }
        if (activeStarted && queued.generationId == generation.get() && !stopped.get()) {
            // The final AudioTrack buffer may still be draining when generation returns.
            val drainMillis = (audio.samples.size * 1_000L / audio.sampleRate).coerceIn(30L, 250L)
            scope.launch {
                delay(drainMillis)
                if (queued.generationId == generation.get() && !stopped.get()) {
                    eventsFlow.emit(SpeechEvent.Completed(request.utteranceId))
                }
            }
        }
        activeRequest = null
    }

    /** A method reference is required: Sherpa's JNI code resolves this exact typed callback. */
    private fun onGeneratedSamples(samples: FloatArray): Int {
        val queued = activeRequest ?: return 0
        if (queued.generationId != generation.get() || stopped.get()) return 0
        if (!activeStarted) {
            activeStarted = true
            eventsFlow.tryEmit(SpeechEvent.Started(queued.request.utteranceId))
            track?.play()
        }
        track?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        return 1
    }

    private fun clearQueue() {
        while (queue.tryReceive().isSuccess) Unit
    }

    private fun copyEspeakData(): File {
        val destination = File(appContext.filesDir, "piper-runtime/espeak-ng-data")
        if (File(destination, "en_dict").isFile) return destination
        copyAssetTree("$RUNTIME_ASSET_ROOT/espeak-ng-data", destination)
        check(File(destination, "en_dict").isFile) { "AI voice pronunciation data is incomplete." }
        return destination
    }

    private fun engineFor(pack: PiperVoicePack): OfflineTts = engines.getOrPut(pack.storageId) {
        val espeakDataDirectory = copyEspeakData()
        val tokens = copyTokens()
        OfflineTts(
        null,
        OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = voicePacks.modelPath(pack),
                    tokens = tokens.absolutePath,
                    dataDir = espeakDataDirectory.absolutePath,
                ),
                numThreads = 2,
                debug = false,
            ),
            maxNumSentences = 1,
        ),
        )
    }

    private fun copyTokens(): File {
        val destination = File(appContext.filesDir, "piper-runtime/tokens.txt")
        if (!destination.isFile) copyAssetTree("$RUNTIME_ASSET_ROOT/tokens.txt", destination)
        return destination
    }

    private fun voiceFor(request: SpeechRequest): PiperVoicePack {
        val installed = voicePacks.installedPacks()
        check(installed.isNotEmpty()) { "Download at least one Local AI voice first." }
        request.localVoicePackId?.let { id ->
            installed.firstOrNull { it.id == id }?.let { return it }
        }
        val matchingGroup = installed.filter { it.voiceGroup == request.voiceGroup }
        val candidates = matchingGroup.ifEmpty {
            if (request.voiceGroup.name == "NARRATOR") {
                installed.filter { it.voiceGroup.name == "NARRATOR" }.ifEmpty { installed }
            } else installed
        }
        return candidates[Math.floorMod(request.voiceSlot, candidates.size)]
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
        } else {
            destination.mkdirs()
            children.forEach { child -> copyAssetTree("$assetPath/$child", File(destination, child)) }
        }
    }

    private data class QueuedRequest(val request: SpeechRequest, val generationId: Long)

    private companion object {
        const val RUNTIME_ASSET_ROOT = "vits-piper-en_US-amy-low"
    }
}
