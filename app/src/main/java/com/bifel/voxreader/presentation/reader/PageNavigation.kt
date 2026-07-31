package com.bifel.voxreader.presentation.reader

import kotlin.math.ceil
import kotlin.math.roundToInt

data class PageEstimate(
    val currentPage: Int,
    val totalPages: Int,
) {
    val progressFraction: Float
        get() = when {
            totalPages <= 1 -> 1f
            else -> (currentPage - 1).toFloat() / (totalPages - 1)
        }

    val percentage: Int
        get() = (progressFraction * 100f).roundToInt().coerceIn(0, 100)
}

/**
 * Maps reader progress without laying out the complete book. Page counts are deliberately
 * estimates, as in mature EPUB readers: only the current viewport determines page capacity.
 */
object PageNavigation {

    fun estimate(
        pageStart: Int,
        pageEnd: Int,
        textLength: Int,
        charactersPerPage: Int,
    ): PageEstimate {
        if (textLength <= 0) return PageEstimate(currentPage = 0, totalPages = 0)

        val pageCapacity = charactersPerPage.coerceAtLeast(1)
        val total = ceil(textLength.toDouble() / pageCapacity).toInt().coerceAtLeast(1)
        val current = if (pageEnd >= textLength) {
            total
        } else {
            (pageStart.coerceIn(0, textLength - 1) / pageCapacity) + 1
        }
        return PageEstimate(
            currentPage = current.coerceIn(1, total),
            totalPages = total,
        )
    }

    fun offsetForFraction(
        fraction: Float,
        textLength: Int,
        charactersPerPage: Int,
    ): Int {
        if (textLength <= 0) return 0
        val total = estimate(0, 0, textLength, charactersPerPage).totalPages
        if (total <= 1) return 0

        val pageIndex = (fraction.coerceIn(0f, 1f) * (total - 1)).roundToInt()
        return if (pageIndex == total - 1) {
            textLength - 1
        } else {
            (pageIndex * charactersPerPage.coerceAtLeast(1)).coerceIn(0, textLength - 1)
        }
    }

    fun chapterFractions(
        chapterStarts: List<Int>,
        textLength: Int,
        charactersPerPage: Int,
    ): List<Float> {
        if (textLength <= 0) return emptyList()
        val total = estimate(0, 0, textLength, charactersPerPage).totalPages
        if (total <= 1) return emptyList()

        return chapterStarts
            .asSequence()
            .map { start ->
                (start.coerceIn(0, textLength - 1) / charactersPerPage.coerceAtLeast(1))
                    .coerceIn(0, total - 1)
            }
            .filter { it in 1 until total - 1 }
            .distinct()
            .map { it.toFloat() / (total - 1) }
            .toList()
    }

}
