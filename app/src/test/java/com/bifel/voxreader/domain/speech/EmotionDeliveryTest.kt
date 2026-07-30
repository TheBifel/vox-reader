package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.PhraseEmotion
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionDeliveryTest {

    @Test
    fun `anger is faster lower and more immediate`() {
        val delivery = delivery(EmotionType.ANGER)

        assertTrue(delivery.speechRateMultiplier > 1f)
        assertTrue(delivery.pitchMultiplier < 1f)
        assertTrue(delivery.pauseAfterMillis < EmotionDelivery.NEUTRAL_PAUSE_MILLIS)
    }

    @Test
    fun `sadness is slower lower and has a longer pause`() {
        val delivery = delivery(EmotionType.SADNESS)

        assertTrue(delivery.speechRateMultiplier < 1f)
        assertTrue(delivery.pitchMultiplier < 1f)
        assertTrue(delivery.pauseAfterMillis > EmotionDelivery.NEUTRAL_PAUSE_MILLIS)
    }

    @Test
    fun `excitement and fear are faster and higher`() {
        listOf(EmotionType.EXCITEMENT, EmotionType.FEAR).forEach { emotion ->
            val delivery = delivery(emotion)
            assertTrue(delivery.speechRateMultiplier > 1f)
            assertTrue(delivery.pitchMultiplier > 1f)
            assertTrue(delivery.pauseAfterMillis < EmotionDelivery.NEUTRAL_PAUSE_MILLIS)
        }
    }

    @Test
    fun `other schema emotions keep neutral Android TTS delivery`() {
        val delivery = delivery(EmotionType.CURIOSITY)

        assertTrue(delivery.speechRateMultiplier == 1f)
        assertTrue(delivery.pitchMultiplier == 1f)
        assertTrue(delivery.pauseAfterMillis == EmotionDelivery.NEUTRAL_PAUSE_MILLIS)
    }

    private fun delivery(type: EmotionType): EmotionDelivery =
        EmotionDelivery.from(
            PhraseEmotion(
                type = type,
                intensity = 1f,
                confidence = 0.95f,
            ),
        )
}
