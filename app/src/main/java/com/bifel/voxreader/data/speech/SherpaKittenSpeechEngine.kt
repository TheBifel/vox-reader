package com.bifel.voxreader.data.speech

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** KittenTTS Micro inference using the same buffered PCM pipeline as Piper. */
class SherpaKittenSpeechEngine(
    context: Context,
    private val models: AdvancedVoiceModelManager,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val eventsFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SpeechEvent> = eventsFlow.asSharedFlow()
    private val ready = MutableStateFlow(false)
    private val initLock = Mutex()
    private var engine: OfflineTts? = null
    @Volatile private var activeGenerationCheck: (() -> Boolean)? = null
    private val pipeline = BufferedPcmSpeechPipeline(
        events = eventsFlow,
        engineName = "AI Advanced",
        synthesize = ::synthesize,
    )

    override suspend fun awaitReady() {
        if (!ready.value) withContext(Dispatchers.Default) { initialize() }
        ready.first { it }
    }

    override fun enqueue(request: SpeechRequest, queueMode: SpeechQueueMode) {
        if (!ready.value) {
            eventsFlow.tryEmit(SpeechEvent.Error(request.utteranceId, "AI Advanced is still loading."))
            return
        }
        pipeline.enqueue(request, queueMode)
    }

    override fun stop() = pipeline.stop()

    private suspend fun initialize() {
        initLock.withLock {
            if (ready.value) return@withLock
            runCatching {
                check(models.installed()) { "Download KittenTTS Micro first." }
                val startedAt = SystemClock.elapsedRealtime()
                val created = OfflineTts(
                    null,
                    OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kitten = OfflineTtsKittenModelConfig(
                                model = models.modelPath(),
                                voices = models.voicesPath(),
                                tokens = models.tokensPath(),
                                dataDir = copyEspeakData().absolutePath,
                            ),
                            numThreads = KITTEN_THREADS,
                            debug = false,
                        ),
                        maxNumSentences = 1,
                    ),
                )
                check(created.numSpeakers() >= models.voices.size) {
                    "The downloaded KittenTTS voice data is incompatible."
                }
                engine = created
                ready.value = true
                Log.i(TAG, "KittenTTS initialized in ${SystemClock.elapsedRealtime() - startedAt} ms")
                eventsFlow.tryEmit(SpeechEvent.Ready)
            }.onFailure { error ->
                eventsFlow.tryEmit(SpeechEvent.Error(null, "Could not load AI Advanced: ${error.message}"))
                throw error
            }
        }
    }

    private fun synthesize(request: SpeechRequest, isCurrent: () -> Boolean): PcmAudio {
        val voice = voiceFor(request)
        activeGenerationCheck = isCurrent
        return try {
            val audio = requireNotNull(engine).generateWithConfigAndCallback(
                text = request.text,
                config = GenerationConfig(
                    speed = request.speechRate.coerceIn(0.5f, 2f),
                    sid = voice.speakerId,
                    silenceScale = 0.2f,
                ),
                callback = this::onGeneratedSamples,
            )
            PcmAudio(audio.samples, audio.sampleRate)
        } finally {
            activeGenerationCheck = null
        }
    }

    /** Sherpa JNI resolves this exact typed method reference at runtime. */
    private fun onGeneratedSamples(@Suppress("UNUSED_PARAMETER") samples: FloatArray): Int =
        if (activeGenerationCheck?.invoke() == true) 1 else 0

    private fun voiceFor(request: SpeechRequest): AdvancedVoice {
        request.advancedVoiceId?.let { id ->
            models.voices.firstOrNull { it.id == id }?.let { return it }
        }
        val matchingGroup = models.voices.filter { it.voiceGroup == request.voiceGroup }
        val candidates = matchingGroup.ifEmpty { models.voices }
        return candidates[Math.floorMod(request.voiceSlot, candidates.size)]
    }

    private fun copyEspeakData(): File {
        val destination = File(appContext.filesDir, "piper-runtime/espeak-ng-data")
        if (File(destination, "en_dict").isFile) return destination
        copyAssetTree("$RUNTIME_ASSET_ROOT/espeak-ng-data", destination)
        check(File(destination, "en_dict").isFile) { "AI voice pronunciation data is incomplete." }
        return destination
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input -> destination.outputStream().use(input::copyTo) }
        } else {
            destination.mkdirs()
            children.forEach { child -> copyAssetTree("$assetPath/$child", File(destination, child)) }
        }
    }

    private companion object {
        const val TAG = "VoxKitten"
        const val KITTEN_THREADS = 2
        const val RUNTIME_ASSET_ROOT = "vits-piper-en_US-amy-low"
    }
}
