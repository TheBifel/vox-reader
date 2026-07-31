package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.domain.model.SpeechPhrase

/** Binary-search helpers for the start/end ordered phrase manifest. */
internal object PhraseLookup {

    fun containing(phrases: List<SpeechPhrase>, offset: Int): SpeechPhrase? {
        val index = lastIndexWithStartAtOrBefore(phrases, offset)
        return phrases.getOrNull(index)?.takeIf { it.end > offset }
    }

    fun lastEndingAtOrBefore(phrases: List<SpeechPhrase>, offset: Int): SpeechPhrase? {
        var low = 0
        var high = phrases.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (phrases[middle].end <= offset) low = middle + 1 else high = middle
        }
        return phrases.getOrNull(low - 1)
    }

    fun firstStartingAtOrAfter(phrases: List<SpeechPhrase>, offset: Int): SpeechPhrase? =
        phrases.getOrNull(firstIndexWithStartAtOrAfter(phrases, offset))

    fun overlappingRange(
        phrases: List<SpeechPhrase>,
        start: Int,
        end: Int,
    ): IntRange? {
        if (phrases.isEmpty() || end <= start) return null

        var low = 0
        var high = phrases.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (phrases[middle].end <= start) low = middle + 1 else high = middle
        }
        val first = low
        val endExclusive = firstIndexWithStartAtOrAfter(phrases, end)
        return if (first < endExclusive) first until endExclusive else null
    }

    private fun lastIndexWithStartAtOrBefore(
        phrases: List<SpeechPhrase>,
        offset: Int,
    ): Int {
        var low = 0
        var high = phrases.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (phrases[middle].start <= offset) low = middle + 1 else high = middle
        }
        return low - 1
    }

    private fun firstIndexWithStartAtOrAfter(
        phrases: List<SpeechPhrase>,
        offset: Int,
    ): Int {
        var low = 0
        var high = phrases.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (phrases[middle].start < offset) low = middle + 1 else high = middle
        }
        return low
    }
}
