package com.bifel.voxreader.domain.repository

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage

/** Reads bounded binary assets owned by an imported book. */
interface BookAssetRepository {
    suspend fun readImage(book: BookDocument, image: BookImage): ByteArray?
}
