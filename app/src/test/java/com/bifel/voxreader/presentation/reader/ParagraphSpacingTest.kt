package com.bifel.voxreader.presentation.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParagraphSpacingTest {

    @Test
    fun `zero spacing renders one ordinary line break`() {
        val rendered = "First\n\nSecond\n\n\u2766\n\nThird"
            .withVisualParagraphSpacing(0f)

        assertEquals("First\nSecond\nThird", rendered)
        assertFalse(rendered.contains('\u2028'))
        assertFalse(rendered.contains('\u2766'))
    }

    @Test
    fun `spacing adds only requested blank lines`() {
        assertEquals("First\n\nSecond", "First\n\nSecond".withVisualParagraphSpacing(8f))
        assertEquals("First\n\n\nSecond", "First\n\nSecond".withVisualParagraphSpacing(16f))
    }
}
