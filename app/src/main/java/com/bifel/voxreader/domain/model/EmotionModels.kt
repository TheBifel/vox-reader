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
