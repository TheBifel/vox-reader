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

    @Test
    fun `splits quoted speech from narration in the same sentence`() {
        val text = "\u201cWait here,\u201d Alice said, replacing the bandage."

        val phrases = SentenceSegmenter.segment(text, Locale.ENGLISH)

        assertEquals(
            listOf("\u201cWait here,\u201d", "Alice said, replacing the bandage."),
            phrases.map { it.text },
        )
        phrases.forEach { phrase ->
            assertEquals(phrase.text, text.substring(phrase.start, phrase.end))
        }
    }

    @Test
    fun `splits an interrupted quote into alternating dialogue and narration spans`() {
        val text = "\u201cFirst part,\u201d she said, \u201csecond part.\u201d"

        val phrases = SentenceSegmenter.segment(text, Locale.ENGLISH)

        assertEquals(
            listOf("\u201cFirst part,\u201d", "she said,", "\u201csecond part.\u201d"),
            phrases.map { it.text },
        )
    }

    @Test
    fun `keeps inline scare quotes inside narrator text`() {
        val text = "Their \u201ccampsite\u201d was only a fire and two blankets."

        val phrases = SentenceSegmenter.segment(text, Locale.ENGLISH)

        assertEquals(listOf(text), phrases.map { it.text })
    }
}
