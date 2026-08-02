package com.bifel.voxreader.data.epub

import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.BookTextRange
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Document
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEpub(
    val title: String,
    val text: String,
    val chapterStarts: List<Int>,
    val links: List<BookLink> = emptyList(),
    val headings: List<BookTextRange> = emptyList(),
    val images: List<BookImage> = emptyList(),
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
            val title = packageDocument.elements("title").firstOrNull()?.textContent
                ?.trim().orEmpty().ifBlank { file.nameWithoutExtension }

            val manifest = packageDocument.elements("item").mapNotNull { item ->
                val id = item.getAttribute("id")
                val href = item.getAttribute("href")
                if (id.isBlank() || href.isBlank()) null else id to ManifestItem(
                    href = href,
                    mediaType = item.getAttribute("media-type"),
                )
            }.toMap(LinkedHashMap())
            val spineIds = packageDocument.elements("itemref")
                .mapNotNull { it.getAttribute("idref").takeIf(String::isNotBlank) }
            val packageDirectory = packagePath.substringBeforeLast('/', "")
            val chapters = (spineIds.ifEmpty { manifest.keys.toList() })
                .mapNotNull(manifest::get)
                .filter(::isReadableDocument)
                .mapNotNull { item ->
                    val entryPath = resolveEntryPath(packageDirectory, item.href)
                    val entry = archive.getEntry(entryPath) ?: return@mapNotNull null
                    val markup = archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    // Keep the legacy plain-text stream byte-for-byte compatible with speaker
                    // sidecars: image-only spine items do not introduce separator characters.
                    extractChapter(entryPath, markup).takeIf { it.text.isNotBlank() }
                }

            val textBuilder = StringBuilder()
            val chapterStarts = mutableListOf<Int>()
            val headings = mutableListOf<BookTextRange>()
            val images = mutableListOf<BookImage>()
            val unresolvedLinks = mutableListOf<UnresolvedLink>()
            val anchors = mutableMapOf<String, Int>()
            chapters.forEachIndexed { index, chapter ->
                if (index > 0) textBuilder.append(CHAPTER_SEPARATOR)
                val base = textBuilder.length
                chapterStarts += base
                textBuilder.append(chapter.text)
                chapter.headings.forEach { headings += BookTextRange(base + it.start, base + it.end) }
                chapter.images.forEach { images += it.copy(offset = base + it.offset) }
                chapter.links.forEach { unresolvedLinks += it.copy(start = base + it.start, end = base + it.end) }
                anchors[chapter.entryPath] = base
                chapter.anchors.forEach { (key, offset) -> anchors[key] = base + offset }
            }
            val bookText = textBuilder.toString().trimEnd()
            require(bookText.isNotBlank()) {
                "No readable text was found. DRM-protected and image-only EPUB files are not supported."
            }

            val links = unresolvedLinks.mapNotNull { link ->
                if (link.end <= link.start) return@mapNotNull null
                if (link.externalUri != null) {
                    BookLink(link.start, link.end, externalUri = link.externalUri, isFootnote = link.isFootnote)
                } else {
                    val target = anchors[link.targetKey] ?: anchors[link.targetKey?.substringBefore('#')]
                    target?.let { BookLink(link.start, link.end, targetOffset = it, isFootnote = link.isFootnote) }
                }
            }
            return ParsedEpub(
                title = title,
                text = bookText,
                chapterStarts = chapterStarts.filter { it <= bookText.length }.distinct().ifEmpty { listOf(0) },
                links = links,
                headings = headings.filter { it.end <= bookText.length },
                images = images.filter { it.offset <= bookText.length },
            )
        }
    }

    private fun extractChapter(entryPath: String, markup: String): RichChapter {
        val document = Jsoup.parse(markup)
        document.select("script,style,nav").remove()
        val body = document.body()
        val blocks = body.select(BLOCK_SELECTOR).filterNot { it.isInsideSelectedBlock() }
        val text = StringBuilder()
        val headings = mutableListOf<BookTextRange>()
        val links = mutableListOf<UnresolvedLink>()
        val images = mutableListOf<BookImage>()
        val anchors = mutableMapOf<String, Int>()
        body.id().takeIf(String::isNotBlank)?.let { anchors[anchorKey(entryPath, it)] = 0 }

        val readableBlocks = if (blocks.isNotEmpty()) blocks else listOf(body)
        readableBlocks.forEach { block ->
            val blockText = block.text().normalizeWhitespace()
            val blockStart = text.length
            if (blockText.isNotBlank()) {
                if (text.isNotEmpty()) text.append("\n\n")
                val actualStart = text.length
                text.append(blockText)
                if (block.tagName() in HEADING_TAGS) headings += BookTextRange(actualStart, text.length)
                block.id().takeIf(String::isNotBlank)?.let { anchors[anchorKey(entryPath, it)] = actualStart }
                var searchFrom = 0
                block.select("a[href]").forEach { anchor ->
                    val label = anchor.text().normalizeWhitespace()
                    if (label.isBlank()) return@forEach
                    val local = blockText.indexOf(label, searchFrom).takeIf { it >= 0 }
                        ?: blockText.indexOf(label).takeIf { it >= 0 }
                        ?: return@forEach
                    searchFrom = local + label.length
                    val href = anchor.attr("href").trim()
                    val external = href.takeIf(::isExternalHref)
                    links += UnresolvedLink(
                        start = actualStart + local,
                        end = actualStart + local + label.length,
                        targetKey = external?.let { null } ?: resolveTargetKey(entryPath, href),
                        externalUri = external,
                        isFootnote = anchor.attr("epub:type").contains("noteref", true) ||
                            anchor.attr("role").contains("doc-noteref", true) ||
                            anchor.classNames().any { it.contains("footnote", true) || it.contains("noteref", true) },
                    )
                }
            }
            val imageOffset = if (blockText.isBlank()) blockStart else text.length
            block.select("img[src],image[href],image[xlink\\:href]").forEach { image ->
                val source = image.attr("src").ifBlank { image.attr("href") }.ifBlank { image.attr("xlink:href") }
                if (source.isBlank()) return@forEach
                images += BookImage(
                    offset = imageOffset,
                    archivePath = resolveEntryPath(entryPath.substringBeforeLast('/', ""), source),
                    description = image.attr("alt").ifBlank { "Image" },
                )
            }
        }
        return RichChapter(entryPath, text.toString(), headings, links, images, anchors)
    }

    private fun readPackagePath(input: InputStream): String = parseXml(input)
        .elements("rootfile").firstOrNull()?.getAttribute("full-path")
        ?.takeIf(String::isNotBlank) ?: error("This EPUB has no package document.")

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
        if (namespaced.length > 0) return (0 until namespaced.length).mapNotNull { namespaced.item(it) as? org.w3c.dom.Element }
        val plain = getElementsByTagName(localName)
        return (0 until plain.length).mapNotNull { plain.item(it) as? org.w3c.dom.Element }
    }

    private fun isReadableDocument(item: ManifestItem): Boolean =
        item.mediaType == "application/xhtml+xml" || item.mediaType == "text/html" ||
            item.href.endsWith(".xhtml", true) || item.href.endsWith(".html", true) || item.href.endsWith(".htm", true)

    private fun resolveTargetKey(entryPath: String, href: String): String {
        val fragment = href.substringAfter('#', "").decodeUrlComponent()
        val path = href.substringBefore('#').takeIf(String::isNotBlank)?.let {
            resolveEntryPath(entryPath.substringBeforeLast('/', ""), it)
        } ?: entryPath
        return anchorKey(path, fragment)
    }

    private fun anchorKey(path: String, fragment: String): String =
        if (fragment.isBlank()) path else "$path#$fragment"

    private fun resolveEntryPath(directory: String, href: String): String {
        val path = href.substringBefore('#').decodeUrlComponent()
        val combined = if (directory.isBlank()) path else "$directory/$path"
        return URI(null, null, combined, null).normalize().path.removePrefix("/")
    }

    private fun isExternalHref(href: String): Boolean = runCatching {
        URI(href).scheme?.lowercase() in setOf("http", "https", "mailto", "tel")
    }.getOrDefault(false)

    private fun String.decodeUrlComponent(): String = runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
    private fun Element.isInsideSelectedBlock(): Boolean = parents().any { it.tagName() in BLOCK_TAGS }
    // Some EPUBs use NBSP-only <p> nodes as visual spacers. They are not content and
    // must not become empty, full-height paragraphs in the reader.
    private fun String.normalizeWhitespace(): String = replace('\u00a0', ' ').replace(Regex("""\s+"""), " ").trim()

    private data class ManifestItem(val href: String, val mediaType: String)
    private data class RichChapter(
        val entryPath: String,
        val text: String,
        val headings: List<BookTextRange>,
        val links: List<UnresolvedLink>,
        val images: List<BookImage>,
        val anchors: Map<String, Int>,
    )
    private data class UnresolvedLink(
        val start: Int,
        val end: Int,
        val targetKey: String?,
        val externalUri: String?,
        val isFootnote: Boolean,
    )

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val CHAPTER_SEPARATOR = "\n\n\u2766\n\n"
        const val BLOCK_SELECTOR = "h1,h2,h3,h4,h5,h6,p,li,pre"
        val BLOCK_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "pre")
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    }
}
