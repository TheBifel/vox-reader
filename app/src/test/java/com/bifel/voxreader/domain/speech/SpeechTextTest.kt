package com.bifel.voxreader.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    @Test
    fun `quote removal preserves offsets`() {
        val source = "\u201cHello,\u201d she said. \"Again.\""
        val sanitized = SpeechText.withoutSpokenQuotes(source)

        assertEquals(source.length, sanitized.length)
        assertFalse(sanitized.any { it in setOf('"', '\u201c', '\u201d') })
        assertTrue("Hello" in sanitized)
    }

    @Test
    fun `decorative and quote-only phrases are not speakable`() {
        assertFalse(SpeechText.isSpeakable("***"))
        assertFalse(SpeechText.isSpeakable("\u201c\u201d"))
        assertTrue(SpeechText.isSpeakable("\u201cListen.\u201d"))
    }
}
