package com.bifel.voxreader.domain.model

enum class EmotionType {
    JOY,
    SADNESS,
    ANGER,
    FEAR,
    SURPRISE,
    DISGUST,
    TENSION,
    EXCITEMENT,
    CURIOSITY,
    CONFIDENCE,
    AFFECTION,
    SHAME,
    GRIEF,
    AMUSEMENT,
    FRUSTRATION,
    RELIEF,
    DETERMINATION,
}

data class PhraseEmotion(
    val type: EmotionType,
    val intensity: Float,
    val confidence: Float,
)

data class EmotionProcessingProfile(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val loudness: Float = 1f,
    /** -1 is warm/dark, 0 is neutral, and +1 is bright. */
    val tone: Float = 0f,
    val pauseAfterMillis: Long = 0L,
) {
    fun normalized() = copy(
        speed = speed.coerceIn(0.7f, 1.3f),
        pitch = pitch.coerceIn(0.85f, 1.15f),
        loudness = loudness.coerceIn(0.6f, 1.4f),
        tone = tone.coerceIn(-1f, 1f),
        pauseAfterMillis = pauseAfterMillis.coerceIn(0L, 600L),
    )
}

data class SpeechPostProcessingSettings(
    val enabled: Boolean = false,
    val profiles: Map<EmotionType, EmotionProcessingProfile> = defaultEmotionProcessingProfiles(),
) {
    fun profileFor(type: EmotionType): EmotionProcessingProfile =
        (profiles[type] ?: DEFAULT_EMOTION_PROCESSING_PROFILES.getValue(type)).normalized()
}

private val DEFAULT_EMOTION_PROCESSING_PROFILES = mapOf(
    EmotionType.JOY to EmotionProcessingProfile(1.10f, 1.05f, 1.08f, 0.25f, 20L),
    EmotionType.EXCITEMENT to EmotionProcessingProfile(1.16f, 1.07f, 1.12f, 0.30f, 0L),
    EmotionType.SURPRISE to EmotionProcessingProfile(1.10f, 1.08f, 1.08f, 0.30f, 60L),
    EmotionType.ANGER to EmotionProcessingProfile(1.18f, 0.94f, 1.18f, -0.08f, 0L),
    EmotionType.FRUSTRATION to EmotionProcessingProfile(1.12f, 0.96f, 1.12f, -0.05f, 30L),
    EmotionType.DETERMINATION to EmotionProcessingProfile(1.08f, 0.96f, 1.10f, -0.10f, 40L),
    EmotionType.FEAR to EmotionProcessingProfile(1.12f, 1.10f, 1.02f, 0.20f, 0L),
    EmotionType.TENSION to EmotionProcessingProfile(1.06f, 1.05f, 1.00f, 0.15f, 40L),
    EmotionType.SADNESS to EmotionProcessingProfile(0.82f, 0.92f, 0.78f, -0.30f, 320L),
    EmotionType.GRIEF to EmotionProcessingProfile(0.78f, 0.90f, 0.72f, -0.38f, 420L),
    EmotionType.SHAME to EmotionProcessingProfile(0.88f, 0.94f, 0.78f, -0.25f, 260L),
    EmotionType.AFFECTION to EmotionProcessingProfile(0.94f, 1.02f, 0.88f, -0.25f, 140L),
    EmotionType.RELIEF to EmotionProcessingProfile(0.92f, 0.98f, 0.88f, -0.20f, 200L),
    EmotionType.AMUSEMENT to EmotionProcessingProfile(1.08f, 1.04f, 1.03f, 0.15f, 60L),
    EmotionType.CURIOSITY to EmotionProcessingProfile(1.02f, 1.06f, 1.00f, 0.10f, 100L),
    EmotionType.CONFIDENCE to EmotionProcessingProfile(0.98f, 0.96f, 1.10f, -0.12f, 80L),
    EmotionType.DISGUST to EmotionProcessingProfile(0.93f, 0.94f, 0.92f, -0.20f, 150L),
)

fun defaultEmotionProcessingProfiles(): Map<EmotionType, EmotionProcessingProfile> =
    DEFAULT_EMOTION_PROCESSING_PROFILES.toMap()
