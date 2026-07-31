package com.bifel.voxreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageNavigationTest {

    @Test
    fun `estimates progress without a complete page map`() {
        assertEquals(
            PageEstimate(currentPage = 1, totalPages = 5),
            PageNavigation.estimate(
                pageStart = 0,
                pageEnd = 100,
                textLength = 500,
                charactersPerPage = 100,
            ),
        )
        assertEquals(
            PageEstimate(currentPage = 3, totalPages = 5),
            PageNavigation.estimate(
                pageStart = 220,
                pageEnd = 320,
                textLength = 500,
                charactersPerPage = 100,
            ),
        )
        assertEquals(
            PageEstimate(currentPage = 5, totalPages = 5),
            PageNavigation.estimate(
                pageStart = 410,
                pageEnd = 500,
                textLength = 500,
                charactersPerPage = 100,
            ),
        )
    }

    @Test
    fun `maps slider positions back to estimated text offsets`() {
        assertEquals(0, PageNavigation.offsetForFraction(0f, 500, 100))
        assertEquals(200, PageNavigation.offsetForFraction(0.5f, 500, 100))
        assertEquals(499, PageNavigation.offsetForFraction(1f, 500, 100))
    }

    @Test
    fun `maps chapters to distinct interior estimated pages`() {
        assertEquals(
            listOf(0.25f, 0.5f, 0.75f),
            PageNavigation.chapterFractions(
                chapterStarts = listOf(0, 100, 150, 200, 300, 500),
                textLength = 500,
                charactersPerPage = 100,
            ),
        )
    }

}
