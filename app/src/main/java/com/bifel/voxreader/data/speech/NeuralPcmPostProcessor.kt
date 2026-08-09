package com.bifel.voxreader.data.speech

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.bifel.voxreader.domain.speech.SpeechRequest
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Applies engine-independent emotion prosody to generated neural PCM. */
internal object NeuralPcmPostProcessor {
    fun process(audio: PcmAudio, request: SpeechRequest): PcmAudio {
        if (
            abs(request.postProcessingPitch - 1f) < EPSILON &&
            abs(request.loudness - 1f) < EPSILON &&
            abs(request.tone) < EPSILON
        ) return audio

        val pitched = shiftPitch(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            pitch = request.postProcessingPitch.coerceIn(0.85f, 1.15f),
        )
        return PcmAudio(
            samples = applyToneAndLoudness(
                samples = pitched,
                loudness = request.loudness.coerceIn(0.6f, 1.4f),
                tone = request.tone.coerceIn(-1f, 1f),
            ),
            sampleRate = audio.sampleRate,
        )
    }

    internal fun applyToneAndLoudness(
        samples: FloatArray,
        loudness: Float,
        tone: Float,
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val output = FloatArray(samples.size)
        var lowPass = samples.first()
        val warmMix = (-tone).coerceIn(0f, 1f) * 0.65f
        val brightMix = tone.coerceIn(0f, 1f) * 0.35f
        samples.forEachIndexed { index, sample ->
            lowPass += LOW_PASS_ALPHA * (sample - lowPass)
            val toned = when {
                warmMix > 0f -> sample + (lowPass - sample) * warmMix
                brightMix > 0f -> sample + (sample - lowPass) * brightMix
                else -> sample
            }
            output[index] = (toned * loudness).coerceIn(-1f, 1f)
        }
        return output
    }

    @OptIn(UnstableApi::class)
    internal fun shiftPitch(samples: FloatArray, sampleRate: Int, pitch: Float): FloatArray {
        if (samples.isEmpty() || abs(pitch - 1f) < EPSILON) return samples
        val processor = SonicAudioProcessor().apply {
            setSpeed(1f)
            setPitch(pitch)
            configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
            flush(AudioProcessor.StreamMetadata.DEFAULT)
        }
        return try {
            val input = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            samples.forEach { sample ->
                input.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            }
            input.flip()
            val output = ByteArrayOutputStream(samples.size * Short.SIZE_BYTES)
            while (input.hasRemaining()) {
                processor.queueInput(input)
                drain(processor, output)
            }
            processor.queueEndOfStream()
            while (!processor.isEnded) drain(processor, output)
            val bytes = output.toByteArray()
            ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).run {
                FloatArray(bytes.size / Short.SIZE_BYTES) {
                    short.toFloat() / Short.MAX_VALUE
                }
            }
        } finally {
            processor.reset()
        }
    }

    @OptIn(UnstableApi::class)
    private fun drain(processor: SonicAudioProcessor, destination: ByteArrayOutputStream) {
        val output = processor.output
        if (!output.hasRemaining()) return
        val bytes = ByteArray(output.remaining())
        output.get(bytes)
        destination.write(bytes)
    }

    private const val LOW_PASS_ALPHA = 0.18f
    private const val EPSILON = 0.005f
}
