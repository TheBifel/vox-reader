package com.bifel.voxreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageNavigationTest {

    private val pages = listOf(
        PageLayout(0, 100),
        PageLayout(100, 220),
        PageLayout(220, 350),
        PageLayout(350, 500),
        PageLayout(500, 650),
    )

    @Test
    fun `finds the page containing or preceding an offset`() {
        assertEquals(0, PageNavigation.currentPageIndex(pages, 0))
        assertEquals(1, PageNavigation.currentPageIndex(pages, 150))
        assertEquals(3, PageNavigation.currentPageIndex(pages, 499))
        assertEquals(4, PageNavigation.currentPageIndex(pages, 999))
    }

    @Test
    fun `reports progress from first to last page`() {
        assertEquals(0, PageNavigation.percentage(pageIndex = 0, pageCount = 5))
        assertEquals(50, PageNavigation.percentage(pageIndex = 2, pageCount = 5))
        assertEquals(100, PageNavigation.percentage(pageIndex = 4, pageCount = 5))
    }

    @Test
    fun `maps chapter starts to distinct interior page ticks`() {
        assertEquals(
            listOf(0.25f, 0.5f, 0.75f),
            PageNavigation.chapterFractions(
                chapterStarts = listOf(0, 100, 150, 220, 350, 650),
                pages = pages,
            ),
        )
    }
}
