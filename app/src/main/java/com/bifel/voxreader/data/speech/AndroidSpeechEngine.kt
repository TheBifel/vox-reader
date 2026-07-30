package com.bifel.voxreader.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.bifel.voxreader.domain.speech.SpeechEngine
import com.bifel.voxreader.domain.speech.SpeechEvent
import com.bifel.voxreader.domain.speech.SpeechRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

class AndroidSpeechEngine(
    context: Context,
) : SpeechEngine {

    private val mutableEvents = MutableSharedFlow<SpeechEvent>(
        extraBufferCapacity = 64,
    )
    override val events: SharedFlow<SpeechEvent> = mutableEvents.asSharedFlow()

    private val initialization = MutableStateFlow<Initialization>(Initialization.Starting)
    private var offlineVoices: List<Voice> = emptyList()
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
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
        when (val state = initialization.first { it !is Initialization.Starting }) {
            Initialization.Ready -> Unit
            is Initialization.Failed -> error(state.message)
            Initialization.Starting -> error("Unexpected speech initialization state.")
        }
    }

    override fun speak(request: SpeechRequest) {
        if (initialization.value != Initialization.Ready) {
            mutableEvents.tryEmit(
                SpeechEvent.Error(
                    request.utteranceId,
                    "The speech engine is not ready yet.",
                ),
            )
            return
        }

        if (offlineVoices.isNotEmpty()) {
            textToSpeech.voice = offlineVoices[
                Math.floorMod(request.voiceSlot, offlineVoices.size)
            ]
        }
        textToSpeech.setPitch(request.pitch.coerceIn(0.5f, 1.6f))
        textToSpeech.setSpeechRate(request.speechRate.coerceIn(0.6f, 1.5f))

        val result = textToSpeech.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            request.utteranceId,
        )
        if (result == TextToSpeech.ERROR) {
            mutableEvents.tryEmit(
                SpeechEvent.Error(
                    request.utteranceId,
                    "Android rejected the speech request.",
                ),
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
        val deviceLanguage = Locale.getDefault().language
        offlineVoices = available
            .sortedWith(
                compareBy<Voice>(
                    { it.locale.language != deviceLanguage },
                    { it.locale.language != Locale.ENGLISH.language },
                    { -it.quality },
                    { it.name },
                ),
            )
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let { mutableEvents.tryEmit(SpeechEvent.Started(it)) }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { mutableEvents.tryEmit(SpeechEvent.Completed(it)) }
        }

        @Deprecated("Required by the Android TTS listener API")
        override fun onError(utteranceId: String?) {
            mutableEvents.tryEmit(
                SpeechEvent.Error(utteranceId, "Speech synthesis failed."),
            )
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
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
        data object Starting : Initialization
        data object Ready : Initialization
        data class Failed(val message: String) : Initialization
    }
}

