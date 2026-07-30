package com.bifel.voxreader.domain.analysis

import com.bifel.voxreader.domain.model.CharacterAnalysis
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.SpeechPhrase

/**
 * Boundary for dialogue attribution. Implementations can refine a local scene while preserving
 * previously analyzed phrase ranges, allowing playback to start without processing a whole book.
 */
interface CharacterAnalyzer {
    suspend fun analyze(request: CharacterAnalysisRequest): CharacterAnalysis
}

data class CharacterAnalysisRequest(
    val text: String,
    val knownCharacters: List<CharacterProfile>,
    val existingPhrases: List<SpeechPhrase> = emptyList(),
    val focusPosition: Int = 0,
    val preservedPhraseRanges: List<IntRange> = emptyList(),
)

/**
 * Scores dynamic character candidates without putting book-specific names in the model labels.
 * The returned values use the same ordering as [candidates] and are probabilities in 0..1.
 */
interface SpeakerScorer {
    suspend fun scoreCandidates(
        scene: String,
        candidates: List<String>,
    ): List<Float>
}
