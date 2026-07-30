package com.bifel.voxreader.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SentenceSegmenterTest {

    @Test
    fun `segments sentences while retaining global source offsets`() {
        val text = "  First sentence.  Second sentence!\nThird?"

        val phrases = SentenceSegmenter.segment(text, Locale.ENGLISH)

        assertEquals(3, phrases.size)
        phrases.forEach { phrase ->
            assertEquals(phrase.text, text.substring(phrase.start, phrase.end))
            assertTrue(phrase.text.first().isLetter())
        }
        assertEquals("First sentence.", phrases[0].text)
        assertEquals("Second sentence!", phrases[1].text)
        assertEquals("Third?", phrases[2].text)
    }
}

