package com.bifel.voxreader.domain.reader

/** Pure back/forward history for intentional non-linear reading jumps. */
class ReadingHistory(
    private val maxEntries: Int = 100,
) {
    private val back = ArrayDeque<Int>()
    private val forward = ArrayDeque<Int>()

    fun recordJump(from: Int, to: Int) {
        if (from == to) return
        back.addLast(from)
        if (back.size > maxEntries) back.removeFirst()
        forward.clear()
    }

    fun goBack(current: Int): Int? = move(back, forward, current)

    fun goForward(current: Int): Int? = move(forward, back, current)

    fun clear() {
        back.clear()
        forward.clear()
    }

    private fun move(from: ArrayDeque<Int>, to: ArrayDeque<Int>, current: Int): Int? {
        val target = from.removeLastOrNull() ?: return null
        to.addLast(current)
        return target
    }
}
