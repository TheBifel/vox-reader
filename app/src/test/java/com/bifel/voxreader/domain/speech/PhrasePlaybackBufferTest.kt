package com.bifel.voxreader.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasePlaybackBufferTest {

    @Test
    fun `tracks current and look-ahead phrases in insertion order`() {
        val buffer = PhrasePlaybackBuffer(capacity = 3)

        buffer.add(BufferedPhrase("one", 10))
        buffer.add(BufferedPhrase("two", 12))
        buffer.add(BufferedPhrase("three", 13))

        assertEquals(3, buffer.size)
        assertFalse(buffer.hasCapacity)
        assertEquals(13, buffer.lastPhraseIndex)
        assertEquals(12, buffer["two"]?.phraseIndex)
    }

    @Test
    fun `completion frees one look-ahead slot`() {
        val buffer = PhrasePlaybackBuffer(capacity = 3)
        buffer.add(BufferedPhrase("one", 10))
        buffer.add(BufferedPhrase("two", 11))

        assertEquals(10, buffer.remove("one")?.phraseIndex)

        assertEquals(1, buffer.size)
        assertTrue(buffer.hasCapacity)
        assertEquals(11, buffer.lastPhraseIndex)
        assertNull(buffer["one"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `does not buffer the same phrase twice`() {
        val buffer = PhrasePlaybackBuffer(capacity = 3)
        buffer.add(BufferedPhrase("one", 10))

        buffer.add(BufferedPhrase("another-id", 10))
    }

    @Test
    fun `clear invalidates callbacks from the previous playback session`() {
        val buffer = PhrasePlaybackBuffer(capacity = 3)
        buffer.add(BufferedPhrase("old-session", 10))

        buffer.clear()

        assertTrue(buffer.isEmpty)
        assertNull(buffer["old-session"])
    }
}
