package com.bifel.voxreader.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingHistoryTest {
    @Test
    fun `back and forward retain non-linear jump positions`() {
        val history = ReadingHistory()
        history.recordJump(from = 10, to = 100)
        history.recordJump(from = 100, to = 500)

        assertEquals(100, history.goBack(current = 500))
        assertEquals(500, history.goForward(current = 100))
    }

    @Test
    fun `a new jump clears forward history`() {
        val history = ReadingHistory()
        history.recordJump(from = 10, to = 100)
        history.recordJump(from = 100, to = 500)
        history.goBack(current = 500)

        history.recordJump(from = 100, to = 250)

        assertNull(history.goForward(current = 250))
    }
}
