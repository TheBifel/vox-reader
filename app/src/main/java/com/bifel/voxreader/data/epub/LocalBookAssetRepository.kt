package com.bifel.voxreader.data.epub

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.repository.BookAssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile

class LocalBookAssetRepository : BookAssetRepository {
    override suspend fun readImage(book: BookDocument, image: BookImage): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                ZipFile(book.localPath).use { archive ->
                    val entry = archive.getEntry(image.archivePath) ?: return@use null
                    if (entry.isDirectory || entry.size > MAX_IMAGE_BYTES) return@use null
                    archive.getInputStream(entry).use { input ->
                        val output = ByteArrayOutputStream(
                            entry.size.coerceAtLeast(0L).coerceAtMost(MAX_IMAGE_BYTES.toLong()).toInt(),
                        )
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_IMAGE_BYTES) return@use null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            }.getOrNull()
        }

    private companion object {
        const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    }
}
