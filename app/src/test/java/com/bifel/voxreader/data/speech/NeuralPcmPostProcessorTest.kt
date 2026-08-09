package com.bifel.voxreader.data.speech

import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.SpeechRequest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralPcmPostProcessorTest {

    @Test
    fun `neutral request leaves generated PCM untouched`() {
        val audio = PcmAudio(floatArrayOf(-0.5f, 0f, 0.5f), 24_000)

        assertSame(audio, NeuralPcmPostProcessor.process(audio, request()))
    }

    @Test
    fun `quiet profile reduces waveform energy`() {
        val input = alternatingWave()
        val output = NeuralPcmPostProcessor.applyToneAndLoudness(input, loudness = 0.7f, tone = 0f)

        assertTrue(rms(output) < rms(input))
        assertTrue(output.all { it in -1f..1f })
    }

    @Test
    fun `warm tone attenuates rapid high frequency changes`() {
        val input = alternatingWave()
        val warm = NeuralPcmPostProcessor.applyToneAndLoudness(input, loudness = 1f, tone = -1f)
        val bright = NeuralPcmPostProcessor.applyToneAndLoudness(input, loudness = 1f, tone = 1f)

        assertTrue(averageDifference(warm) < averageDifference(input))
        assertTrue(averageDifference(bright) > averageDifference(warm))
    }

    @Test
    fun `pitch shifter produces finite duration preserving PCM`() {
        val sampleRate = 24_000
        val input = FloatArray(sampleRate) { index ->
            (sin(2.0 * PI * 220.0 * index / sampleRate) * 0.4).toFloat()
        }
        val shifted = NeuralPcmPostProcessor.shiftPitch(input, sampleRate, pitch = 1.1f)

        assertTrue(shifted.isNotEmpty())
        assertEquals(input.size.toDouble(), shifted.size.toDouble(), input.size * 0.08)
        assertTrue(shifted.all(Float::isFinite))
    }

    private fun request() = SpeechRequest(
        utteranceId = "mock",
        text = "Synthetic speech",
        voiceSlot = 0,
        voiceGroup = VoiceGroup.NARRATOR,
        pitch = 1f,
        speechRate = 1f,
    )

    private fun alternatingWave() = FloatArray(2_000) { if (it % 2 == 0) 0.6f else -0.6f }

    private fun rms(samples: FloatArray): Float =
        kotlin.math.sqrt(samples.map { it * it }.average()).toFloat()

    private fun averageDifference(samples: FloatArray): Float =
        samples.asSequence().zipWithNext { first, second -> abs(second - first) }.average().toFloat()
}
