package com.bifel.voxreader.data.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun `parses title and spine ordered text from a basic epub`() {
        val file = Files.createTempFile("voxreader-test-", ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.entry(
                    "META-INF/container.xml",
                    """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """.trimIndent(),
                )
                zip.entry(
                    "OEBPS/content.opf",
                    """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                      </metadata>
                      <manifest>
                        <item id="chapter-one" href="chapter-one.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter-two" href="chapter-two.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter-one"/>
                        <itemref idref="chapter-two"/>
                      </spine>
                    </package>
                    """.trimIndent(),
                )
                zip.entry(
                    "OEBPS/chapter-one.xhtml",
                    """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Ignored title</title></head>
                      <body>
                        <h1>Chapter One</h1>
                        <p>First paragraph.</p>
                        <p>Second paragraph.</p>
                      </body>
                    </html>
                    """.trimIndent(),
                )
                zip.entry(
                    "OEBPS/chapter-two.xhtml",
                    """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body>
                        <h1>Chapter Two</h1>
                        <p>Another paragraph.</p>
                      </body>
                    </html>
                    """.trimIndent(),
                )
            }

            val parsed = EpubParser().parse(file)

            assertEquals("Test Book", parsed.title)
            assertTrue(parsed.text.startsWith("Chapter One"))
            assertTrue(parsed.text.contains("First paragraph.\n\nSecond paragraph."))
            assertEquals(
                listOf(0, parsed.text.indexOf("Chapter Two")),
                parsed.chapterStarts,
            )
        } finally {
            file.delete()
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
