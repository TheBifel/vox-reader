package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.SpeechEngineMode

data class SpeechBackendAvailability(
    val available: Boolean,
    val unavailableMessage: String? = null,
)

/** Resolves speech implementations without exposing concrete engines to reader presentation. */
interface SpeechEngineRegistry {
    fun engine(mode: SpeechEngineMode): SpeechEngine
    fun availability(mode: SpeechEngineMode): SpeechBackendAvailability
    fun stopAll()
}

class DefaultSpeechEngineRegistry(
    private val engines: Map<SpeechEngineMode, SpeechEngine>,
    private val availability: (SpeechEngineMode) -> SpeechBackendAvailability,
) : SpeechEngineRegistry {

    override fun engine(mode: SpeechEngineMode): SpeechEngine =
        requireNotNull(engines[mode]) { "No speech engine is registered for $mode." }

    override fun availability(mode: SpeechEngineMode): SpeechBackendAvailability =
        availability.invoke(mode)

    override fun stopAll() {
        engines.values.distinct().forEach(SpeechEngine::stop)
    }
}
