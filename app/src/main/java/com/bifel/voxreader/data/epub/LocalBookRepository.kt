package com.bifel.voxreader.data.epub

import android.content.Context
import androidx.core.net.toUri
import com.bifel.voxreader.data.fb2.Fb2Parser
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.BookTextRange
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class LocalBookRepository(
    private val context: Context,
    private val epubParser: EpubParser,
    private val fb2Parser: Fb2Parser,
) : BookRepository {

    override suspend fun importBook(sourceUri: String): BookDocument = withContext(Dispatchers.IO) {
        val booksDirectory = File(context.filesDir, BOOKS_DIRECTORY).apply {
            check(exists() || mkdirs()) { "Unable to create the private book library." }
        }
        val temporaryFile = File.createTempFile("book-import-", ".tmp", booksDirectory)

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val uri = sourceUri.toUri()
            val input = context.contentResolver.openInputStream(uri)
                ?: error("The selected book cannot be opened.")

            input.use { source ->
                temporaryFile.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        destination.write(buffer, 0, count)
                    }
                }
            }

            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val extension = if (isFb2(uri)) "fb2" else "epub"
            val localFile = File(booksDirectory, "$id.$extension")
            if (localFile.exists()) {
                temporaryFile.delete()
            } else if (!temporaryFile.renameTo(localFile)) {
                temporaryFile.copyTo(localFile, overwrite = false)
                temporaryFile.delete()
            }

            parseBook(localFile).toBookDocument(id, localFile)
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    override suspend fun loadBook(book: StoredBook): BookDocument = withContext(Dispatchers.IO) {
        val file = File(book.localPath)
        parseBook(file).toBookDocument(book.id, file)
    }

    private fun parseBook(file: File): ParsedBook = when (file.extension.lowercase()) {
        "fb2" -> fb2Parser.parse(file).let { ParsedBook(it.title, it.text, it.chapterStarts) }
        else -> epubParser.parse(file).let {
            ParsedBook(it.title, it.text, it.chapterStarts, it.links, it.headings, it.images)
        }
    }

    private fun ParsedBook.toBookDocument(id: String, file: File): BookDocument =
        BookDocument(
            id = id,
            title = title,
            localPath = file.absolutePath,
            text = text,
            chapterStarts = chapterStarts,
            links = links,
            headings = headings,
            images = images,
        )

    private fun isFb2(uri: android.net.Uri): Boolean =
        uri.toString().substringBefore('?').endsWith(".fb2", ignoreCase = true) ||
            context.contentResolver.getType(uri) in fb2MimeTypes

    private companion object {
        const val BOOKS_DIRECTORY = "books"
        val fb2MimeTypes = setOf("application/x-fictionbook+xml", "application/fb2+xml")
    }

    private data class ParsedBook(
        val title: String,
        val text: String,
        val chapterStarts: List<Int>,
        val links: List<BookLink> = emptyList(),
        val headings: List<BookTextRange> = emptyList(),
        val images: List<BookImage> = emptyList(),
    )
}
