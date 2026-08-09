package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.EmotionProcessingProfile
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionDeliveryTest {

    @Test
    fun `disabled post processing always returns neutral delivery`() {
        val delivery = delivery(EmotionType.ANGER, enabled = false)

        assertEquals(EmotionDelivery(), delivery)
    }

    @Test
    fun `emotion below confidence gate returns neutral delivery`() {
        val delivery = EmotionDelivery.from(
            PhraseEmotion(EmotionType.SADNESS, intensity = 1f, confidence = 0.84f),
            SpeechPostProcessingSettings(enabled = true),
        )

        assertEquals(EmotionDelivery(), delivery)
    }

    @Test
    fun `all supported emotions have an audible default profile`() {
        EmotionType.entries.forEach { emotion ->
            val delivery = delivery(emotion)
            assertTrue("$emotion should differ from neutral", delivery != EmotionDelivery())
        }
    }

    @Test
    fun `emotion families follow their intended direction`() {
        listOf(EmotionType.JOY, EmotionType.EXCITEMENT, EmotionType.SURPRISE).forEach {
            val delivery = delivery(it)
            assertTrue(delivery.speechRateMultiplier > 1f)
            assertTrue(delivery.pitchMultiplier > 1f)
            assertTrue(delivery.loudnessMultiplier > 1f)
        }
        listOf(EmotionType.SADNESS, EmotionType.GRIEF, EmotionType.SHAME).forEach {
            val delivery = delivery(it)
            assertTrue(delivery.speechRateMultiplier < 1f)
            assertTrue(delivery.pitchMultiplier < 1f)
            assertTrue(delivery.loudnessMultiplier < 1f)
            assertTrue(delivery.pauseAfterMillis > 0L)
        }
    }

    @Test
    fun `intensity interpolates every control from neutral`() {
        val settings = SpeechPostProcessingSettings(
            enabled = true,
            profiles = mapOf(
                EmotionType.ANGER to EmotionProcessingProfile(
                    speed = 1.2f,
                    pitch = 0.9f,
                    loudness = 1.4f,
                    tone = -0.4f,
                    pauseAfterMillis = 400L,
                ),
            ),
        )
        val delivery = EmotionDelivery.from(
            PhraseEmotion(EmotionType.ANGER, intensity = 0.5f, confidence = 0.95f),
            settings,
        )

        assertEquals(1.1f, delivery.speechRateMultiplier, 0.001f)
        assertEquals(0.95f, delivery.pitchMultiplier, 0.001f)
        assertEquals(1.2f, delivery.loudnessMultiplier, 0.001f)
        assertEquals(-0.2f, delivery.tone, 0.001f)
        assertEquals(200L, delivery.pauseAfterMillis)
    }

    private fun delivery(type: EmotionType, enabled: Boolean = true): EmotionDelivery =
        EmotionDelivery.from(
            PhraseEmotion(type = type, intensity = 1f, confidence = 0.95f),
            SpeechPostProcessingSettings(enabled = enabled),
        )
}
