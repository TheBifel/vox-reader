package com.bifel.voxreader.data.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Document
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEpub(
    val title: String,
    val text: String,
    val chapterStarts: List<Int>,
)

class EpubParser {

    fun parse(file: File): ParsedEpub {
        require(file.isFile) { "The imported EPUB is no longer available." }

        ZipFile(file).use { archive ->
            val containerEntry = archive.getEntry(CONTAINER_PATH)
                ?: error("This file is not a valid EPUB: container.xml is missing.")
            val packagePath = archive.getInputStream(containerEntry).use(::readPackagePath)
            val packageEntry = archive.getEntry(packagePath)
                ?: error("This EPUB references a missing package document.")
            val packageDocument = archive.getInputStream(packageEntry).use(::parseXml)

            val title = packageDocument
                .elements("title")
                .firstOrNull()
                ?.textContent
                ?.trim()
                .orEmpty()
                .ifBlank { file.nameWithoutExtension }

            val manifest = packageDocument
                .elements("item")
                .mapNotNull { item ->
                    val id = item.getAttribute("id")
                    val href = item.getAttribute("href")
                    val mediaType = item.getAttribute("media-type")
                    if (id.isBlank() || href.isBlank()) null
                    else id to ManifestItem(href, mediaType)
                }
                .toMap(LinkedHashMap())

            val spineIds = packageDocument
                .elements("itemref")
                .mapNotNull { it.getAttribute("idref").takeIf(String::isNotBlank) }

            val orderedItems = (spineIds.ifEmpty { manifest.keys.toList() })
                .mapNotNull(manifest::get)
                .filter { item ->
                    item.mediaType == "application/xhtml+xml" ||
                        item.mediaType == "text/html" ||
                        item.href.endsWith(".xhtml", ignoreCase = true) ||
                        item.href.endsWith(".html", ignoreCase = true) ||
                        item.href.endsWith(".htm", ignoreCase = true)
                }

            val packageDirectory = packagePath.substringBeforeLast('/', "")
            val chapters = orderedItems.mapNotNull { item ->
                val entryPath = resolveEntryPath(packageDirectory, item.href)
                val entry = archive.getEntry(entryPath) ?: return@mapNotNull null
                archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                    extractReadableText(reader.readText())
                }.takeIf(String::isNotBlank)
            }

            val bookText = chapters
                .joinToString(CHAPTER_SEPARATOR)
                .replace(Regex("""[ \t]+\n"""), "\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
            var chapterSearchStart = 0
            val chapterStarts = chapters.mapNotNull { chapter ->
                val normalizedChapter = chapter
                    .replace(Regex("""[ \t]+\n"""), "\n")
                    .replace(Regex("""\n{3,}"""), "\n\n")
                    .trim()
                val start = bookText.indexOf(normalizedChapter, startIndex = chapterSearchStart)
                if (start < 0) {
                    null
                } else {
                    chapterSearchStart = start + normalizedChapter.length
                    start
                }
            }.distinct()

            require(bookText.isNotBlank()) {
                "No readable text was found. DRM-protected and image-only EPUB files are not supported."
            }

            return ParsedEpub(
                title = title,
                text = bookText,
                chapterStarts = chapterStarts.ifEmpty { listOf(0) },
            )
        }
    }

    private fun readPackagePath(input: InputStream): String {
        val document = parseXml(input)
        return document
            .elements("rootfile")
            .firstOrNull()
            ?.getAttribute("full-path")
            ?.takeIf(String::isNotBlank)
            ?: error("This EPUB has no package document.")
    }

    private fun parseXml(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(input)
    }

    private fun Document.elements(localName: String): List<org.w3c.dom.Element> {
        val namespaced = getElementsByTagNameNS("*", localName)
        if (namespaced.length > 0) {
            return (0 until namespaced.length).mapNotNull { namespaced.item(it) as? org.w3c.dom.Element }
        }
        val plain = getElementsByTagName(localName)
        return (0 until plain.length).mapNotNull { plain.item(it) as? org.w3c.dom.Element }
    }

    private fun resolveEntryPath(packageDirectory: String, href: String): String {
        val withoutFragment = href.substringBefore('#')
        val combined = if (packageDirectory.isBlank()) {
            withoutFragment
        } else {
            "$packageDirectory/$withoutFragment"
        }
        return URI(null, null, combined, null)
            .normalize()
            .path
            .removePrefix("/")
    }

    private fun extractReadableText(markup: String): String {
        val document = Jsoup.parse(markup)
        document.select("script,style,nav,svg").remove()
        val body = document.body()
        val blocks = body
            .select("h1,h2,h3,h4,h5,h6,p,li,pre")
            .filterNot { it.isInsideSelectedBlock() }
            .map { it.text().normalizeWhitespace() }
            .filter(String::isNotBlank)

        return if (blocks.isNotEmpty()) {
            blocks.joinToString("\n\n")
        } else {
            body.text().normalizeWhitespace()
        }
    }

    private fun Element.isInsideSelectedBlock(): Boolean =
        parents().any { it.tagName() in blockTags }

    private fun String.normalizeWhitespace(): String =
        replace(Regex("""\s+"""), " ").trim()

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
    )

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val CHAPTER_SEPARATOR = "\n\n\u2766\n\n"
        val blockTags = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "pre")
    }
}
