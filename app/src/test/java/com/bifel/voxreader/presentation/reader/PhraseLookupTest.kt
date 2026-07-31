package com.bifel.voxreader.presentation.reader

import com.bifel.voxreader.domain.model.SpeechPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhraseLookupTest {

    private val phrases = listOf(
        phrase(0, 0, 10),
        phrase(1, 12, 20),
        phrase(2, 25, 40),
        phrase(3, 45, 60),
    )

    @Test
    fun `finds a phrase containing an offset`() {
        assertEquals(phrases[1], PhraseLookup.containing(phrases, 12))
        assertEquals(phrases[1], PhraseLookup.containing(phrases, 19))
        assertNull(PhraseLookup.containing(phrases, 22))
    }

    @Test
    fun `finds page snapping boundaries with binary search`() {
        assertEquals(phrases[1], PhraseLookup.lastEndingAtOrBefore(phrases, 24))
        assertEquals(phrases[2], PhraseLookup.firstStartingAtOrAfter(phrases, 21))
    }

    @Test
    fun `returns only phrases overlapping the visible page`() {
        assertEquals(1..2, PhraseLookup.overlappingRange(phrases, start = 15, end = 45))
        assertNull(PhraseLookup.overlappingRange(phrases, start = 20, end = 25))
    }

    private fun phrase(index: Int, start: Int, end: Int) = SpeechPhrase(
        index = index,
        start = start,
        end = end,
        text = "x",
    )
}
