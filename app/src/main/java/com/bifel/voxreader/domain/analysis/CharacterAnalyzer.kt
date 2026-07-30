package com.bifel.voxreader.domain.analysis

import com.bifel.voxreader.domain.model.CharacterAnalysis
import com.bifel.voxreader.domain.model.CharacterProfile

/**
 * Boundary for dialogue attribution. The POC uses deterministic rules, while a future
 * on-device language model can implement this interface without changing playback or UI code.
 */
interface CharacterAnalyzer {
    suspend fun analyze(
        text: String,
        knownCharacters: List<CharacterProfile>,
    ): CharacterAnalysis
}

