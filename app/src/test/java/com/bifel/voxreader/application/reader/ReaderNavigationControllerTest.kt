package com.bifel.voxreader.application.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderNavigationControllerTest {

    @Test
    fun `normalizes offsets and records only intentional jumps`() {
        val controller = ReaderNavigationController()
        assertEquals(ReaderPosition(20, 30), controller.reset(100, 20, 30))

        assertEquals(ReaderPosition(80, 100), controller.move(80, 150, recordJump = true))
        assertEquals(ReaderPosition(20, 20), controller.back())
        assertEquals(ReaderPosition(80, 80), controller.forward())
    }

    @Test
    fun `reset clears history for a new book`() {
        val controller = ReaderNavigationController()
        controller.reset(100, 10, 20)
        controller.move(70, 80, recordJump = true)

        controller.reset(50, 5, 10)

        assertNull(controller.back())
    }
}
