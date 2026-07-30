package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.data.epub.EpubParser
import com.bifel.voxreader.domain.text.SentenceSegmenter
import java.io.File
import org.junit.Test

/**
 * Opt-in local diagnostic. Set VOXREADER_DIAGNOSTIC_EPUB and
 * VOXREADER_DIAGNOSTIC_OUTPUT to export the exact phrases used by the app.
 * No book text is written into the repository or ordinary test reports.
 */
class BookPhraseDiagnosticTest {

    @Test
    fun `export exact segmented phrases when explicitly requested`() {
        val epubPath = System.getenv("VOXREADER_DIAGNOSTIC_EPUB") ?: return
        val outputPath = System.getenv("VOXREADER_DIAGNOSTIC_OUTPUT") ?: return
        val parsed = EpubParser().parse(File(epubPath))
        val phrases = SentenceSegmenter.segment(parsed.text)

        File(outputPath).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("index\tstart\tend\ttext")
            phrases.forEach { phrase ->
                writer.append(phrase.index.toString())
                writer.append('\t')
                writer.append(phrase.start.toString())
                writer.append('\t')
                writer.append(phrase.end.toString())
                writer.append('\t')
                writer.appendLine(
                    phrase.text
                        .replace('\t', ' ')
                        .replace("\r", "\\r")
                        .replace("\n", "\\n"),
                )
            }
        }
    }
}
