package com.bifel.voxreader.data.fb2

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedFb2(
    val title: String,
    val text: String,
    val chapterStarts: List<Int>,
)

/** Minimal FictionBook 2.0 text parser. Images, styles, notes, and DRM are intentionally ignored. */
class Fb2Parser {
    fun parse(file: File): ParsedFb2 {
        require(file.isFile) { "The imported FB2 is no longer available." }
        val document = file.inputStream().use(::parseXml)
        val title = document.elements("book-title").firstOrNull()?.textContent?.trim()
            .orEmpty()
            .ifBlank { file.nameWithoutExtension }
        val body = document.elements("body").firstOrNull()
            ?: error("This FB2 file has no readable body.")
        val chapters = directChildren(body, "section")
            .map(::sectionText)
            .filter(String::isNotBlank)
            .ifEmpty { listOf(sectionText(body)) }
        val text = chapters.joinToString(CHAPTER_SEPARATOR).normalizeBookText()
        require(text.isNotBlank()) { "No readable text was found in this FB2 file." }

        var searchStart = 0
        val starts = chapters.mapNotNull { chapter ->
            val normalized = chapter.normalizeBookText()
            val start = text.indexOf(normalized, searchStart)
            if (start < 0) null else {
                searchStart = start + normalized.length
                start
            }
        }.distinct().ifEmpty { listOf(0) }
        return ParsedFb2(title, text, starts)
    }

    private fun sectionText(element: Element): String = buildList {
        directChildren(element, "title").firstOrNull()?.let { add(it.textContent.normalizeWhitespace()) }
        element.paragraphs().forEach { paragraph ->
            paragraph.textContent.normalizeWhitespace().takeIf(String::isNotBlank)?.let(::add)
        }
    }.joinToString("\n\n")

    private fun Element.paragraphs(): List<Element> {
        val namespaced = getElementsByTagNameNS("*", "p")
        if (namespaced.length > 0) return (0 until namespaced.length).mapNotNull { namespaced.item(it) as? Element }
        val plain = getElementsByTagName("p")
        return (0 until plain.length).mapNotNull { plain.item(it) as? Element }
    }

    private fun directChildren(parent: Element, name: String): List<Element> = buildList {
        for (index in 0 until parent.childNodes.length) {
            val node = parent.childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                if (element.localName == name || element.tagName.substringAfter(':') == name) add(element)
            }
        }
    }

    private fun Document.elements(localName: String): List<Element> {
        val namespaced = getElementsByTagNameNS("*", localName)
        if (namespaced.length > 0) return (0 until namespaced.length).mapNotNull { namespaced.item(it) as? Element }
        val plain = getElementsByTagName(localName)
        return (0 until plain.length).mapNotNull { plain.item(it) as? Element }
    }

    private fun parseXml(input: java.io.InputStream): Document {
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()
    private fun String.normalizeBookText(): String =
        replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim()

    private companion object {
        const val CHAPTER_SEPARATOR = "\n\n❦\n\n"
    }
}
