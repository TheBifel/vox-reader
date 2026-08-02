package com.bifel.voxreader.data.speech

import android.content.Context
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Offline Piper/VITS synthesis from voice packs downloaded into app-private storage. */
class SherpaPiperSpeechEngine(
    context: Context,
    private val voicePacks: PiperVoicePackManager,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val eventsFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SpeechEvent> = eventsFlow.asSharedFlow()
    private val ready = MutableStateFlow(false)
    private val initLock = Mutex()
    private val engines = mutableMapOf<String, OfflineTts>()
    @Volatile private var activeGenerationCheck: (() -> Boolean)? = null
    private val pipeline = BufferedPcmSpeechPipeline(
        events = eventsFlow,
        engineName = "AI Simple",
        synthesize = ::synthesize,
    )

    override suspend fun awaitReady() {
        if (!ready.value) withContext(Dispatchers.Default) { initialize() }
        ready.first { it }
    }

    override fun enqueue(request: SpeechRequest, queueMode: SpeechQueueMode) {
        if (!ready.value) {
            eventsFlow.tryEmit(SpeechEvent.Error(request.utteranceId, "Local AI voice is still loading."))
            return
        }
        pipeline.enqueue(request, queueMode)
    }

    override fun stop() = pipeline.stop()

    private suspend fun initialize() {
        initLock.withLock {
            if (ready.value) return@withLock
        runCatching {
            val installed = voicePacks.installedPacks()
            check(installed.isNotEmpty()) { "Download at least one Local AI voice first." }
            engineFor(installed.first())
            ready.value = true
            eventsFlow.tryEmit(SpeechEvent.Ready)
            }.onFailure { error ->
                eventsFlow.tryEmit(SpeechEvent.Error(null, "Could not load Local AI voice: ${error.message}"))
                throw error
            }
        }
    }

    private fun synthesize(request: SpeechRequest, isCurrent: () -> Boolean): PcmAudio {
        activeGenerationCheck = isCurrent
        return try {
            val audio = voiceFor(request).let { pack -> engineFor(pack).generateWithConfigAndCallback(
                    text = request.text,
                    config = GenerationConfig(
                        speed = request.speechRate.coerceIn(0.5f, 2f),
                        sid = pack.speakerId,
                    ),
                    callback = this::onGeneratedSamples,
                ) }
            PcmAudio(audio.samples, audio.sampleRate)
        } finally {
            activeGenerationCheck = null
        }
    }

    /** Sherpa JNI resolves this exact typed method reference at runtime. */
    private fun onGeneratedSamples(@Suppress("UNUSED_PARAMETER") samples: FloatArray): Int =
        if (activeGenerationCheck?.invoke() == true) 1 else 0

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

    private companion object {
        const val RUNTIME_ASSET_ROOT = "vits-piper-en_US-amy-low"
    }
}
