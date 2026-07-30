package com.bifel.voxreader.data.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubParserDeviceTest {

    @Test
    fun parsesEpubWithAndroidXmlParser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("android-parser-", ".epub", context.cacheDir)

        try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.entry(
                    "META-INF/container.xml",
                    """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """.trimIndent(),
                )
                zip.entry(
                    "content.opf",
                    """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Android Parser Test</dc:title>
                      </metadata>
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="chapter"/></spine>
                    </package>
                    """.trimIndent(),
                )
                zip.entry(
                    "chapter.xhtml",
                    """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><p>This EPUB was parsed on Android.</p></body>
                    </html>
                    """.trimIndent(),
                )
            }

            val parsed = EpubParser().parse(file)

            assertEquals("Android Parser Test", parsed.title)
            assertTrue(parsed.text.contains("parsed on Android"))
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
