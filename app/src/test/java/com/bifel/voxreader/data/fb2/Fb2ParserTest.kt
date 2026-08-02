package com.bifel.voxreader.data.fb2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Fb2ParserTest {
    @Test
    fun `parses title chapters and paragraphs from fb2`() {
        val file = File.createTempFile("voxreader-fb2-", ".fb2")
        try {
            file.writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><book-title>Test FB2</book-title></title-info></description>
                  <body>
                    <section><title><p>One</p></title><p>First paragraph.</p></section>
                    <section><title><p>Two</p></title><p>Second paragraph.</p></section>
                  </body>
                </FictionBook>
                """.trimIndent(),
            )

            val parsed = Fb2Parser().parse(file)

            assertEquals("Test FB2", parsed.title)
            assertTrue(parsed.text.contains("First paragraph."))
            assertTrue(parsed.text.contains("Second paragraph."))
            assertEquals(2, parsed.chapterStarts.size)
        } finally {
            file.delete()
        }
    }
}
