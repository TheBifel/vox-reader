package com.bifel.voxreader.data.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.bifel.voxreader.domain.preanalysis.sidecar.AnalysisSidecarJson
import java.nio.file.Files
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun `Cradle 2 table of contents links resolve to chapter starts`() {
        val file = sequenceOf(
            File("test-data/2 Soulsmith_-_Will_Wight.epub"),
            File("../test-data/2 Soulsmith_-_Will_Wight.epub"),
        ).firstOrNull(File::isFile) ?: error("Cradle 2 test book is missing")
        val parsed = EpubParser().parse(file)
        assertTrue(parsed.links.size >= 20)
        assertTrue(parsed.links.filter { it.targetOffset != null }.all { it.targetOffset in parsed.chapterStarts })
        assertTrue(parsed.images.isNotEmpty())
    }

    @Test
    fun `rich parsing remains compatible with the Cradle 2 speaker sidecar`() {
        val book = testDataFile("2 Soulsmith_-_Will_Wight.epub")
        val sidecar = testDataFile("Soulsmith.speakers.json")
        val parsed = EpubParser().parse(book)
        val bookId = MessageDigest.getInstance("SHA-256").digest(book.readBytes())
            .joinToString("") { "%02x".format(it) }
        val errors = AnalysisSidecarJson.decode(sidecar.readText()).validateAgainst(bookId, parsed.text)
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    @Test
    fun `resolves internal and external hyperlinks while preserving plain text`() {
        val file = Files.createTempFile("voxreader-links-", ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
                zip.entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Links</dc:title></metadata><manifest><item id="toc" href="toc.xhtml" media-type="application/xhtml+xml"/><item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="toc"/><itemref idref="one"/></spine></package>""")
                zip.entry("OPS/toc.xhtml", """<html><body><p><a href="text/one.xhtml#target">Chapter one</a></p><p><a href="https://example.com">Website</a></p></body></html>""")
                zip.entry("OPS/text/one.xhtml", """<html><body id="target"><h1>Chapter One</h1><p>Body text.</p></body></html>""")
            }
            val parsed = EpubParser().parse(file)
            assertEquals("Chapter one\n\nWebsite\n\n❦\n\nChapter One\n\nBody text.", parsed.text)
            assertEquals(parsed.text.indexOf("Chapter One"), parsed.links.first().targetOffset)
            assertEquals("https://example.com", parsed.links.last().externalUri)
            assertEquals(parsed.text.indexOf("Chapter One"), parsed.headings.single().start)
        } finally {
            file.delete()
        }
    }

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

    private fun testDataFile(name: String): File = sequenceOf(File("test-data/$name"), File("../test-data/$name"))
        .firstOrNull(File::isFile) ?: error("Missing test-data/$name")
}
