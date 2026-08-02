package com.bifel.voxreader.presentation.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.bifel.voxreader.domain.model.SpeechPhrase

data class PageLayout(
    val start: Int,
    val end: Int,
)

object PageCalculator {

    fun forward(
        text: String,
        anchor: Int,
        phrases: List<SpeechPhrase>,
        textMeasurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int,
        chapterStarts: List<Int> = emptyList(),
        paragraphSpacingSp: Float = 0f,
    ): PageLayout {
        if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) return PageLayout(0, 0)

        val safeAnchor = anchor.coerceIn(0, text.length)
        val currentChapterStart = chapterStarts.lastOrNull { it <= safeAnchor } ?: 0
        val start = maxOf(phraseStartAtOrBefore(safeAnchor, phrases), currentChapterStart)
        val nextChapterStart = chapterStarts.firstOrNull { it > start } ?: text.length
        val remaining = nextChapterStart - start
        if (remaining <= 0) return PageLayout(text.length, text.length)

        val maxFittingLength = largestFittingLength(
            maximum = remaining,
            fits = { length ->
                fits(
                    value = text.substring(start, start + length).withVisualParagraphSpacing(paragraphSpacingSp),
                    textMeasurer = textMeasurer,
                    style = style,
                    widthPx = widthPx,
                    heightPx = heightPx,
                )
            },
        )
        val rawEnd = (start + maxFittingLength.coerceAtLeast(1)).coerceAtMost(nextChapterStart)
        val phraseEnd = PhraseLookup.lastEndingAtOrBefore(phrases, rawEnd)
            ?.takeIf { it.end > start + MINIMUM_SNAPPED_PAGE }
            ?.end
        val end = phraseEnd ?: wordBoundaryBefore(text, rawEnd, start)
        return PageLayout(start, end.coerceAtLeast(start + 1).coerceAtMost(text.length))
    }

    fun backward(
        text: String,
        currentStart: Int,
        phrases: List<SpeechPhrase>,
        textMeasurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int,
        chapterStarts: List<Int> = emptyList(),
        paragraphSpacingSp: Float = 0f,
    ): PageLayout {
        if (text.isEmpty() || currentStart <= 0) {
            return forward(text, 0, phrases, textMeasurer, style, widthPx, heightPx, chapterStarts, paragraphSpacingSp)
        }

        val end = currentStart.coerceIn(0, text.length)
        val currentChapterStart = chapterStarts.lastOrNull { it < end } ?: 0
        val maxFittingLength = largestFittingLength(
            maximum = end - currentChapterStart,
            fits = { length ->
                fits(
                    value = text.substring(end - length, end).withVisualParagraphSpacing(paragraphSpacingSp),
                    textMeasurer = textMeasurer,
                    style = style,
                    widthPx = widthPx,
                    heightPx = heightPx,
                )
            },
        )
        val rawStart = (end - maxFittingLength.coerceAtLeast(1)).coerceAtLeast(0)
        val phraseStart = PhraseLookup.firstStartingAtOrAfter(phrases, rawStart)
            ?.takeIf { it.start < end }
            ?.start
        val start = phraseStart ?: wordBoundaryAfter(text, rawStart, end)
        return PageLayout(start.coerceAtMost(end - 1), end)
    }

    private fun largestFittingLength(
        maximum: Int,
        fits: (Int) -> Boolean,
    ): Int {
        if (maximum <= 1) return maximum

        var low = 1
        var high = minOf(INITIAL_PROBE, maximum)
        while (high < maximum && fits(high)) {
            low = high
            high = minOf(high * 2, maximum)
        }
        if (high == maximum && fits(high)) return high

        while (low + 1 < high) {
            val middle = low + (high - low) / 2
            if (fits(middle)) low = middle else high = middle
        }
        return low
    }

    private fun fits(
        value: String,
        textMeasurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        heightPx: Int,
    ): Boolean {
        val result = textMeasurer.measure(
            text = AnnotatedString(value),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            constraints = Constraints(
                maxWidth = widthPx,
                maxHeight = heightPx,
            ),
        )
        return !result.hasVisualOverflow
    }

    private fun phraseStartAtOrBefore(anchor: Int, phrases: List<SpeechPhrase>): Int {
        if (anchor <= 0) return 0
        return PhraseLookup.containing(phrases, anchor)?.start ?: anchor
    }

    private fun wordBoundaryBefore(text: String, rawEnd: Int, minimum: Int): Int {
        if (rawEnd >= text.length) return text.length
        for (index in rawEnd downTo (minimum + 1)) {
            if (text[index - 1].isWhitespace()) return index
        }
        return rawEnd
    }

    private fun wordBoundaryAfter(text: String, rawStart: Int, maximum: Int): Int {
        for (index in rawStart until maximum) {
            if (text[index].isWhitespace()) return (index + 1).coerceAtMost(maximum)
        }
        return rawStart
    }

    private const val INITIAL_PROBE = 512
    private const val MINIMUM_SNAPPED_PAGE = 80
}

internal fun String.withVisualParagraphSpacing(paragraphSpacingSp: Float): String {
    val extraBreaks = (paragraphSpacingSp / 8f + 0.5f).toInt().coerceIn(0, 4)
    val visualBoundary = "\n".repeat(extraBreaks + 1)
    return replace(Regex("""[\r\n]*\u2766[\r\n]*"""), visualBoundary)
        .replace(Regex("""[\r\n]+[\s\u00a0]+[\r\n]+"""), visualBoundary)
        .replace(Regex("""[\r\n]{2,}"""), visualBoundary)
}
