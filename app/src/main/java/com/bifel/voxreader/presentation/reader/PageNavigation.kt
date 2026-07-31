package com.bifel.voxreader.presentation.reader

import kotlin.math.roundToInt

object PageNavigation {

    fun currentPageIndex(
        pages: List<PageLayout>,
        textOffset: Int,
    ): Int {
        if (pages.isEmpty()) return 0
        var low = 0
        var high = pages.lastIndex
        var result = 0
        while (low <= high) {
            val middle = low + (high - low) / 2
            if (pages[middle].start <= textOffset) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result.coerceIn(pages.indices)
    }

    fun percentage(
        pageIndex: Int,
        pageCount: Int,
    ): Int {
        if (pageCount <= 0) return 0
        if (pageCount == 1) return 100
        return ((pageIndex.coerceIn(0, pageCount - 1) * 100f) / (pageCount - 1))
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun chapterFractions(
        chapterStarts: List<Int>,
        pages: List<PageLayout>,
    ): List<Float> {
        if (pages.size <= 1) return emptyList()
        return chapterStarts
            .asSequence()
            .map { currentPageIndex(pages, it) }
            .filter { it in 1 until pages.lastIndex }
            .distinct()
            .map { it.toFloat() / pages.lastIndex }
            .toList()
    }
}
