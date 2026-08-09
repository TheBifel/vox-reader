package com.bifel.voxreader.application.reader

import com.bifel.voxreader.domain.reader.ReadingHistory

data class ReaderPosition(
    val start: Int,
    val end: Int,
)

/** Owns canonical reader offsets and non-linear jump history. */
class ReaderNavigationController(
    maxHistoryEntries: Int = 100,
) {
    private val history = ReadingHistory(maxHistoryEntries)
    private var textLength: Int = 0
    var position: ReaderPosition = ReaderPosition(0, 0)
        private set

    fun reset(textLength: Int, start: Int, end: Int): ReaderPosition {
        this.textLength = textLength.coerceAtLeast(0)
        history.clear()
        position = normalized(start, end)
        return position
    }

    fun move(start: Int, end: Int, recordJump: Boolean): ReaderPosition {
        val next = normalized(start, end)
        if (recordJump && next.start != position.start) history.recordJump(position.start, next.start)
        position = next
        return position
    }

    fun back(): ReaderPosition? = history.goBack(position.start)?.let { move(it, it, false) }

    fun forward(): ReaderPosition? = history.goForward(position.start)?.let { move(it, it, false) }

    private fun normalized(start: Int, end: Int): ReaderPosition {
        val safeStart = start.coerceIn(0, textLength)
        return ReaderPosition(safeStart, end.coerceIn(safeStart, textLength))
    }
}
