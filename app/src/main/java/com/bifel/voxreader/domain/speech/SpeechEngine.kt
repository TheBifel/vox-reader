package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.VoiceGroup
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ProceduralVoice(
    val id: String,
    val displayName: String,
    val language: ProceduralVoiceLanguage,
)

enum class ProceduralVoiceLanguage { ENGLISH, UKRAINIAN }

interface ProceduralVoiceCatalog {
    val proceduralVoices: StateFlow<List<ProceduralVoice>>

    suspend fun loadVoiceCatalog()
}

data class SpeechRequest(
    val utteranceId: String,
    val text: String,
    val voiceSlot: Int,
    val voiceGroup: VoiceGroup,
    val pitch: Float,
    val speechRate: Float,
    val proceduralVoiceId: String? = null,
    val localVoicePackId: String? = null,
    val pauseAfterMillis: Long = 0L,
)

enum class SpeechQueueMode {
    FLUSH,
    APPEND,
}

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Started(val utteranceId: String) : SpeechEvent
    data class Range(
        val utteranceId: String,
        val start: Int,
        val end: Int,
    ) : SpeechEvent

    data class Completed(val utteranceId: String) : SpeechEvent
    data class Stopped(val utteranceId: String, val interrupted: Boolean) : SpeechEvent
    data class Error(val utteranceId: String?, val message: String) : SpeechEvent
}

interface SpeechEngine {
    val events: SharedFlow<SpeechEvent>

    suspend fun awaitReady()
    fun enqueue(
        request: SpeechRequest,
        queueMode: SpeechQueueMode,
    )
    fun stop()
}
