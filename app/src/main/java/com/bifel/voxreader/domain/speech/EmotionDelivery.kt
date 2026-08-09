package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import kotlin.math.roundToLong

data class EmotionDelivery(
    val speechRateMultiplier: Float = 1f,
    val pitchMultiplier: Float = 1f,
    val loudnessMultiplier: Float = 1f,
    val tone: Float = 0f,
    val pauseAfterMillis: Long = NEUTRAL_PAUSE_MILLIS,
) {
    companion object {
        const val NEUTRAL_PAUSE_MILLIS = 0L

        fun from(
            emotion: PhraseEmotion?,
            settings: SpeechPostProcessingSettings,
        ): EmotionDelivery {
            if (!settings.enabled || emotion == null || emotion.confidence < MIN_CONFIDENCE) {
                return EmotionDelivery()
            }
            val intensity = emotion.intensity.coerceIn(0f, 1f)
            val target = settings.profileFor(emotion.type)
            return EmotionDelivery(
                speechRateMultiplier = interpolate(1f, target.speed, intensity),
                pitchMultiplier = interpolate(1f, target.pitch, intensity),
                loudnessMultiplier = interpolate(1f, target.loudness, intensity),
                tone = interpolate(0f, target.tone, intensity),
                pauseAfterMillis = interpolate(
                    NEUTRAL_PAUSE_MILLIS.toFloat(),
                    target.pauseAfterMillis.toFloat(),
                    intensity,
                ).roundToLong(),
            )
        }

        private fun interpolate(start: Float, end: Float, amount: Float): Float =
            start + (end - start) * amount
        private const val MIN_CONFIDENCE = 0.85f
    }
}
