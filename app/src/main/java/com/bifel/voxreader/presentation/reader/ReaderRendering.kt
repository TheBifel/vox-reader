package com.bifel.voxreader.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.speech.SpeechText
import kotlin.math.roundToInt

@Composable
internal fun BookProgressNavigation(
    book: BookDocument,
    displayedPage: PageLayout,
    charactersPerPage: Int,
    onSeekOffset: (Int) -> Unit,
) {
    var isSeeking by remember(book.id) { mutableStateOf(false) }
    var draftFraction by remember(book.id) { mutableStateOf(0f) }
    val pageEstimate = PageNavigation.estimate(
        pageStart = displayedPage.start,
        pageEnd = displayedPage.end,
        textLength = book.text.length,
        charactersPerPage = charactersPerPage,
    )
    LaunchedEffect(pageEstimate, charactersPerPage) {
        if (!isSeeking) draftFraction = pageEstimate.progressFraction
    }

    val displayEstimate = if (isSeeking && pageEstimate.totalPages > 0) {
        val page = (draftFraction.coerceIn(0f, 1f) * (pageEstimate.totalPages - 1))
            .roundToInt() + 1
        PageEstimate(currentPage = page, totalPages = pageEstimate.totalPages)
    } else {
        pageEstimate
    }
    val chapterFractions = remember(book.chapterStarts, book.text.length, charactersPerPage) {
        PageNavigation.chapterFractions(
            chapterStarts = book.chapterStarts,
            textLength = book.text.length,
            charactersPerPage = charactersPerPage,
        )
    }
    val chapterTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "${displayEstimate.percentage}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(38.dp),
        ) {
            Slider(
                value = draftFraction.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    draftFraction = value
                },
                onValueChangeFinished = {
                    onSeekOffset(
                        PageNavigation.offsetForFraction(
                            fraction = draftFraction,
                            textLength = book.text.length,
                            charactersPerPage = charactersPerPage,
                        ),
                    )
                    isSeeking = false
                },
                valueRange = 0f..1f,
                enabled = pageEstimate.totalPages > 1,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val halfTick = 5.dp.toPx()
                val tickWidth = 1.dp.toPx()
                val trackInset = 10.dp.toPx()
                val trackWidth = (size.width - (trackInset * 2f)).coerceAtLeast(0f)
                chapterFractions.forEach { fraction ->
                    val x = trackInset + (fraction.coerceIn(0f, 1f) * trackWidth)
                    drawLine(
                        color = chapterTickColor,
                        start = androidx.compose.ui.geometry.Offset(x, centerY - halfTick),
                        end = androidx.compose.ui.geometry.Offset(x, centerY + halfTick),
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Butt,
                    )
                }
            }
        }
        Text(
            text = if (displayEstimate.totalPages == 0) {
                "…/…"
            } else {
                "${displayEstimate.currentPage}/${displayEstimate.totalPages}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

internal fun highlightedPage(
    text: String,
    page: PageLayout,
    renderedPage: RenderedPageText,
    phrases: List<SpeechPhrase>,
    cachedSpeakerPhraseIndices: Set<Int>,
    speakerColorSlots: Map<String, Int>,
    darkTheme: Boolean,
    highlightStart: Int?,
    highlightEnd: Int?,
    links: List<BookLink>,
    headings: List<com.bifel.voxreader.domain.model.BookTextRange>,
    linkColor: Color,
    headingColor: Color,
    activeHighlightColor: Color,
    firstLineIndentSp: Float,
): AnnotatedString = buildAnnotatedString {
    val pageText = renderedPage.value
    append(pageText)

    fun visualOffset(absoluteBookOffset: Int): Int =
        renderedPage.visualOffset(absoluteBookOffset - page.start)

    headings.filter { it.end > page.start && it.start < page.end }.forEach { heading ->
        addStyle(
            SpanStyle(color = headingColor, fontWeight = FontWeight.Bold),
            visualOffset(heading.start.coerceAtLeast(page.start)),
            visualOffset(heading.end.coerceAtMost(page.end)),
        )
    }
    links.filter { it.end > page.start && it.start < page.end }.forEach { link ->
        addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            visualOffset(link.start.coerceAtLeast(page.start)),
            visualOffset(link.end.coerceAtMost(page.end)),
        )
    }
    if (firstLineIndentSp > 0f && pageText.isNotEmpty()) {
        addStyle(
            ParagraphStyle(textIndent = TextIndent(firstLine = firstLineIndentSp.sp)),
            0,
            pageText.length,
        )
    }

    PhraseLookup.overlappingRange(phrases, page.start, page.end)
        ?.forEach { position ->
            val phrase = phrases[position]
            if (
                SpeechText.isSpeakable(phrase.text) &&
                phrase.index in cachedSpeakerPhraseIndices &&
                phrase.speakerId != NARRATOR_ID &&
                phrase.end > page.start &&
                phrase.start < page.end
            ) {
                val phraseStart = includeAdjacentOpeningQuotes(text, phrase.start)
                    .coerceIn(page.start, page.end)
                    .let(::visualOffset)
                val nextPhrase = phrases.getOrNull(position + 1)
                val absoluteEnd = if (
                    nextPhrase != null &&
                    nextPhrase.index == phrase.index + 1 &&
                    nextPhrase.index in cachedSpeakerPhraseIndices &&
                    nextPhrase.speakerId == phrase.speakerId
                ) {
                    nextPhrase.start
                } else {
                    phrase.end
                }
                val phraseEnd = includeAdjacentClosingQuotes(text, absoluteEnd)
                    .coerceIn(page.start, page.end)
                    .let(::visualOffset)
                if (phraseEnd > phraseStart) {
                    addStyle(
                        style = SpanStyle(
                            background = speakerHighlightColor(
                                colorSlot = speakerColorSlots[phrase.speakerId]
                                    ?: stableColorSlot(phrase.speakerId),
                                darkTheme = darkTheme,
                            ),
                        ),
                        start = phraseStart,
                        end = phraseEnd,
                    )
                }
            }
        }

    val start = highlightStart?.coerceIn(page.start, page.end)?.let(::visualOffset)
    val end = highlightEnd?.coerceIn(page.start, page.end)?.let(::visualOffset)
    if (start != null && end != null && end > start) {
        addStyle(
            style = SpanStyle(
                background = activeHighlightColor,
                fontWeight = FontWeight.SemiBold,
            ),
            start = start,
            end = end,
        )
    }
}

internal fun speakerHighlightColor(
    colorSlot: Int,
    darkTheme: Boolean,
): Color {
    val hue = (colorSlot * GOLDEN_ANGLE_DEGREES) % 360f
    return Color
        .hsl(hue = hue, saturation = 0.68f, lightness = 0.58f)
        .copy(alpha = if (darkTheme) 0.42f else 0.28f)
}

private fun stableColorSlot(speakerId: String): Int =
    speakerId.hashCode() and Int.MAX_VALUE

private fun includeAdjacentOpeningQuotes(
    text: String,
    start: Int,
): Int {
    var result = start.coerceIn(0, text.length)
    while (result > 0 && text[result - 1] in BOOK_QUOTE_MARKS) result--
    return result
}

private fun includeAdjacentClosingQuotes(
    text: String,
    end: Int,
): Int {
    var result = end.coerceIn(0, text.length)
    while (result < text.length && text[result] in BOOK_QUOTE_MARKS) result++
    return result
}

private const val GOLDEN_ANGLE_DEGREES = 137.50776f
internal const val MAX_IMAGES_PER_PAGE = 3
private const val CHAPTER_DIVIDER = '\u2766'
private val BOOK_QUOTE_MARKS = setOf('"', '\u201c', '\u201d', '\u201e', '\u00ab', '\u00bb')
/** EPUBs commonly use a non-breaking space in an otherwise empty paragraph. */
private fun Char.isVisualSeparatorCharacter(): Boolean =
    this == CHAPTER_DIVIDER || this == '\u00a0' || isWhitespace()

internal data class RenderedPageText(
    val value: String,
    private val visualOffsetForBook: IntArray,
    private val bookOffsetForVisual: IntArray,
) {
    fun visualOffset(bookOffsetInPage: Int): Int =
        visualOffsetForBook[bookOffsetInPage.coerceIn(0, visualOffsetForBook.lastIndex)]

    fun bookOffset(visualOffset: Int): Int =
        bookOffsetForVisual[visualOffset.coerceIn(0, bookOffsetForVisual.lastIndex)]
}

internal data class RenderedTextLayout(
    val layout: androidx.compose.ui.text.TextLayoutResult,
    val rendered: RenderedPageText,
)

/**
 * Produces the text shown by the reader without changing the book-coordinate stream.
 *
 * EPUB paragraphs and our internal chapter divider are both stored as multi-character
 * separators. A zero paragraph-space setting must render either of them as precisely one
 * normal line break, never as a blank line (or a stack of blank lines).
 */
internal fun renderPageText(text: String, page: PageLayout, paragraphSpacingSp: Float): RenderedPageText {
    val raw = text.substring(page.start, page.end)
    val extraBreaks = (paragraphSpacingSp / 8f).roundToInt().coerceIn(0, 4)
    if (!raw.contains("\n\n") && !raw.contains(CHAPTER_DIVIDER) && !raw.contains('\u00a0')) {
        return RenderedPageText(raw, IntArray(raw.length + 1) { it }, IntArray(raw.length + 1) { it })
    }
    val output = StringBuilder(raw.length + raw.length / 24)
    val bookToVisual = IntArray(raw.length + 1)
    val visualToBook = ArrayList<Int>(raw.length + raw.length / 24)
    var index = 0
    while (index < raw.length) {
        val character = raw[index]
        bookToVisual[index] = output.length
        if (character == '\n' || character == '\r' || character == CHAPTER_DIVIDER) {
            var runEnd = index
            var lineBreakCount = 0
            var containsDivider = false
            while (runEnd < raw.length && raw[runEnd].isVisualSeparatorCharacter()) {
                when (raw[runEnd]) {
                    '\n' -> lineBreakCount++
                    CHAPTER_DIVIDER -> containsDivider = true
                }
                runEnd++
            }
            // Keep a genuine one-line break intact. A run of two or more line breaks,
            // or the internal chapter divider, is one paragraph boundary in the view.
            if (lineBreakCount < 2 && !containsDivider) {
                output.append(character)
                visualToBook += index
                index++
                continue
            }
            output.append('\n')
            visualToBook += index
            for (skipped in (index + 1) until runEnd) bookToVisual[skipped] = output.length
            repeat(extraBreaks) {
                output.append('\n')
                visualToBook += runEnd - 1
            }
            index = runEnd
            continue
        }
        output.append(character)
        visualToBook += index
        index++
    }
    bookToVisual[raw.length] = output.length
    visualToBook += raw.length
    return RenderedPageText(output.toString(), bookToVisual, visualToBook.toIntArray())
}
