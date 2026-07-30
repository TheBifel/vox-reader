package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.PhraseEmotion
import kotlin.math.roundToLong

data class EmotionDelivery(
    val speechRateMultiplier: Float = 1f,
    val pitchMultiplier: Float = 1f,
    val pauseAfterMillis: Long = NEUTRAL_PAUSE_MILLIS,
) {
    companion object {
        const val NEUTRAL_PAUSE_MILLIS = 0L

        fun from(emotion: PhraseEmotion?): EmotionDelivery {
            emotion ?: return EmotionDelivery()
            val intensity = emotion.intensity.coerceIn(0f, 1f)
            val target = when (emotion.type) {
                EmotionType.ANGER -> Target(rate = 1.18f, pitch = 0.94f, pauseMillis = 0L)
                EmotionType.SADNESS -> Target(rate = 0.82f, pitch = 0.92f, pauseMillis = 320L)
                EmotionType.EXCITEMENT -> Target(rate = 1.16f, pitch = 1.07f, pauseMillis = 0L)
                EmotionType.FEAR -> Target(rate = 1.12f, pitch = 1.10f, pauseMillis = 0L)
                else -> return EmotionDelivery()
            }
            return EmotionDelivery(
                speechRateMultiplier = interpolate(1f, target.rate, intensity),
                pitchMultiplier = interpolate(1f, target.pitch, intensity),
                pauseAfterMillis = interpolate(
                    NEUTRAL_PAUSE_MILLIS.toFloat(),
                    target.pauseMillis.toFloat(),
                    intensity,
                ).roundToLong(),
            )
        }

        private fun interpolate(start: Float, end: Float, amount: Float): Float =
            start + (end - start) * amount
    }

    private data class Target(
        val rate: Float,
        val pitch: Float,
        val pauseMillis: Long,
    )
}
