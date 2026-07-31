package com.bifel.voxreader.data.epub

import android.content.Context
import android.net.Uri
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class LocalBookRepository(
    private val context: Context,
    private val parser: EpubParser,
) : BookRepository {

    override suspend fun importBook(sourceUri: String): BookDocument = withContext(Dispatchers.IO) {
        val booksDirectory = File(context.filesDir, BOOKS_DIRECTORY).apply {
            check(exists() || mkdirs()) { "Unable to create the private book library." }
        }
        val temporaryFile = File.createTempFile("epub-import-", ".tmp", booksDirectory)

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val uri = Uri.parse(sourceUri)
            val input = context.contentResolver.openInputStream(uri)
                ?: error("The selected EPUB cannot be opened.")

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
            val localFile = File(booksDirectory, "$id.epub")
            if (localFile.exists()) {
                temporaryFile.delete()
            } else if (!temporaryFile.renameTo(localFile)) {
                temporaryFile.copyTo(localFile, overwrite = false)
                temporaryFile.delete()
            }

            parser.parse(localFile).toBookDocument(id, localFile)
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    override suspend fun loadBook(book: StoredBook): BookDocument = withContext(Dispatchers.IO) {
        val file = File(book.localPath)
        parser.parse(file).toBookDocument(book.id, file)
    }

    private fun ParsedEpub.toBookDocument(id: String, file: File): BookDocument =
        BookDocument(
            id = id,
            title = title,
            localPath = file.absolutePath,
            text = text,
            chapterStarts = chapterStarts,
        )

    private companion object {
        const val BOOKS_DIRECTORY = "books"
    }
}
