package com.bifel.voxreader.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.bifel.voxreader.BuildConfig
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechQueueMode
import com.bifel.voxreader.domain.speech.SpeechRequest
import com.bifel.voxreader.domain.speech.ProceduralVoice
import com.bifel.voxreader.domain.speech.ProceduralVoiceCatalog
import com.bifel.voxreader.domain.speech.ProceduralVoiceLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

class AndroidSpeechEngine(
    context: Context,
) : SpeechEngine, ProceduralVoiceCatalog {

    private val applicationContext = context.applicationContext
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(
        extraBufferCapacity = 64,
    )
    override val events: SharedFlow<SpeechEvent> = mutableEvents.asSharedFlow()

    private val initialization = MutableStateFlow<Initialization>(Initialization.NotStarted)
    private val mutableProceduralVoices = MutableStateFlow<List<ProceduralVoice>>(emptyList())
    override val proceduralVoices: StateFlow<List<ProceduralVoice>> = mutableProceduralVoices.asStateFlow()
    private var offlineVoices: List<Voice> = emptyList()
    private var maleVoices: List<Voice> = emptyList()
    private var femaleVoices: List<Voice> = emptyList()
    private var configuredVoiceName: String? = null
    private var configuredPitch: Float? = null
    private var configuredSpeechRate: Float? = null
    private lateinit var textToSpeech: TextToSpeech

    private fun ensureStarted() {
        if (initialization.value != Initialization.NotStarted) return
        initialization.value = Initialization.Starting
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureEngine()
                initialization.value = Initialization.Ready
                mutableEvents.tryEmit(SpeechEvent.Ready)
            } else {
                val message = "Android's text-to-speech engine could not be initialized."
                initialization.value = Initialization.Failed(message)
                mutableEvents.tryEmit(SpeechEvent.Error(null, message))
            }
        }
    }

    override suspend fun awaitReady() {
        ensureStarted()
        when (
            val state = initialization.first {
                it !is Initialization.Starting && it !is Initialization.NotStarted
            }
        ) {
            Initialization.Ready -> Unit
            is Initialization.Failed -> error(state.message)
            Initialization.Starting,
            Initialization.NotStarted,
            -> error("Unexpected speech initialization state.")
        }
    }

    override suspend fun loadVoiceCatalog() = awaitReady()

    override fun enqueue(
        request: SpeechRequest,
        queueMode: SpeechQueueMode,
    ) {
        if (initialization.value != Initialization.Ready) {
            mutableEvents.tryEmit(
                SpeechEvent.Error(
                    request.utteranceId,
                    "The speech engine is not ready yet.",
                ),
            )
            return
        }

        val requestedVoice = request.proceduralVoiceId
            ?.let { id -> textToSpeech.voices.orEmpty().firstOrNull { it.name == id } }
        val voicePool = when (request.voiceGroup) {
            VoiceGroup.MALE -> maleVoices
            VoiceGroup.FEMALE -> femaleVoices
            VoiceGroup.NARRATOR,
            VoiceGroup.NEUTRAL,
            -> offlineVoices
        }.ifEmpty { offlineVoices }
        var selectedVoiceName: String? = null
        if (requestedVoice != null || voicePool.isNotEmpty()) {
            val selectedVoice = requestedVoice ?: voicePool[Math.floorMod(request.voiceSlot, voicePool.size)]
            selectedVoiceName = selectedVoice.name
            if (configuredVoiceName != selectedVoice.name) {
                textToSpeech.voice = selectedVoice
                configuredVoiceName = selectedVoice.name
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Queued ${request.voiceGroup} voice ${selectedVoice.name}; " +
                        "pitch=${request.pitch}; rate=${request.speechRate}; mode=$queueMode",
                )
            }
        }
        val pitch = request.pitch.coerceIn(0.5f, 1.6f)
        val speechRate = request.speechRate.coerceIn(0.5f, 2f)
        if (configuredPitch != pitch) {
            textToSpeech.setPitch(pitch)
            configuredPitch = pitch
        }
        if (configuredSpeechRate != speechRate) {
            textToSpeech.setSpeechRate(speechRate)
            configuredSpeechRate = speechRate
        }

        val speechParameters = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.loudness.coerceIn(0f, 1f))
        }
        val result = textToSpeech.speak(
            request.text,
            when (queueMode) {
                SpeechQueueMode.FLUSH -> TextToSpeech.QUEUE_FLUSH
                SpeechQueueMode.APPEND -> TextToSpeech.QUEUE_ADD
            },
            speechParameters,
            request.utteranceId,
        )
        if (result == TextToSpeech.ERROR) {
            Log.e(
                TAG,
                "Android rejected queued utterance ${request.utteranceId} " +
                    "for voice $selectedVoiceName.",
            )
            mutableEvents.tryEmit(
                SpeechEvent.Error(
                    request.utteranceId,
                    "Android rejected the speech request.",
                ),
            )
        } else if (request.pauseAfterMillis > 0L) {
            textToSpeech.playSilentUtterance(
                request.pauseAfterMillis,
                TextToSpeech.QUEUE_ADD,
                "${request.utteranceId}:emotion-pause",
            )
        }
    }

    override fun stop() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }
    }

    private fun configureEngine() {
        textToSpeech.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        textToSpeech.setOnUtteranceProgressListener(progressListener)

        val available = textToSpeech.voices
            .orEmpty()
            .filterNot(Voice::isNetworkConnectionRequired)
            .filterNot { "notInstalled" in it.features }
            .filter { it.locale.language in supportedLanguages }
            .filterNot { it.name.endsWith("-language") }
        mutableProceduralVoices.value = available.mapNotNull { voice ->
            val language = when (voice.locale.language) {
                Locale.ENGLISH.language -> ProceduralVoiceLanguage.ENGLISH
                UKRAINIAN_LANGUAGE -> ProceduralVoiceLanguage.UKRAINIAN
                else -> null
            } ?: return@mapNotNull null
            ProceduralVoice(
                id = voice.name,
                displayName = "${voice.name} (${voice.locale.getDisplayLanguage(Locale.ENGLISH)})",
                language = language,
            )
        }.sortedBy { it.displayName }
        val englishAvailable = available.filter { it.locale.language == Locale.ENGLISH.language }
        val preferredVoicePack = englishAvailable
            .groupBy { it.locale }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<Locale, List<Voice>>> {
                    voiceGroupCoverage(it.value)
                }
                    .thenByDescending { it.key.country == Locale.getDefault().country }
                    .thenByDescending { it.key.country == Locale.US.country }
                    .thenByDescending { it.value.size },
            )
            .firstOrNull()
            ?.value
            .orEmpty()
            .ifEmpty { englishAvailable }
        offlineVoices = preferredVoicePack
            .sortedWith(
                compareBy<Voice>(
                    { -it.quality },
                    { it.name },
                ),
            )
        maleVoices = offlineVoices.filter { voice ->
            maleVoiceMarkers.any(voice.name::contains)
        }
        femaleVoices = offlineVoices.filter { voice ->
            femaleVoiceMarkers.any(voice.name::contains)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                offlineVoices.mapIndexed { index, voice ->
                    "$index: ${voice.name}; locale=${voice.locale}"
                }.joinToString(
                    prefix = "Preferred offline voice pack " +
                        "${offlineVoices.firstOrNull()?.locale ?: "unavailable"} " +
                        "(${maleVoices.size} male, " +
                        "${femaleVoices.size} female):\n",
                    separator = "\n",
                ),
            )
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let {
                if (BuildConfig.DEBUG) Log.d(TAG, "Started $it")
                mutableEvents.tryEmit(SpeechEvent.Started(it))
            }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let {
                if (BuildConfig.DEBUG) Log.d(TAG, "Completed $it")
                mutableEvents.tryEmit(SpeechEvent.Completed(it))
            }
        }

        @Deprecated("Required by the Android TTS listener API")
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "Synthesis failed for $utteranceId")
            mutableEvents.tryEmit(
                SpeechEvent.Error(utteranceId, "Speech synthesis failed."),
            )
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "Synthesis failed for $utteranceId with error $errorCode")
            mutableEvents.tryEmit(
                SpeechEvent.Error(
                    utteranceId,
                    "Speech synthesis failed with error $errorCode.",
                ),
            )
        }

        override fun onRangeStart(
            utteranceId: String?,
            start: Int,
            end: Int,
            frame: Int,
        ) {
            utteranceId?.let {
                mutableEvents.tryEmit(
                    SpeechEvent.Range(
                        utteranceId = it,
                        start = start,
                        end = end,
                    ),
                )
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let {
                mutableEvents.tryEmit(SpeechEvent.Stopped(it, interrupted))
            }
        }
    }

    private sealed interface Initialization {
        data object NotStarted : Initialization
        data object Starting : Initialization
        data object Ready : Initialization
        data class Failed(val message: String) : Initialization
    }

    private companion object {
        const val TAG = "VoxReaderTTS"
        const val UKRAINIAN_LANGUAGE = "uk"
        val supportedLanguages = setOf(Locale.ENGLISH.language, UKRAINIAN_LANGUAGE)
        val maleVoiceMarkers = listOf(
            "x-aub-",
            "x-aud-",
            "x-gbb-",
            "x-gbd-",
            "x-enc-",
            "x-ene-",
            "x-iob-",
            "x-iol-",
            "x-iom-",
        )
        val femaleVoiceMarkers = listOf(
            "x-aua-",
            "x-auc-",
            "x-gba-",
            "x-gbc-",
            "x-ena-",
            "x-end-",
            "x-iog-",
            "x-sfg-",
            "x-tpc-",
            "x-tpf-",
        )

        fun voiceGroupCoverage(voices: List<Voice>): Int =
            listOf(
                maleVoiceMarkers.any { marker -> voices.any { marker in it.name } },
                femaleVoiceMarkers.any { marker -> voices.any { marker in it.name } },
            ).count { it }
    }
}
