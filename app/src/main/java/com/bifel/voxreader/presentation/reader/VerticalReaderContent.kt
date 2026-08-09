package com.bifel.voxreader.presentation.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import android.graphics.BitmapFactory
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VerticalScrollableBook(
    book: BookDocument,
    phrases: List<SpeechPhrase>,
    requestedOffset: Int,
    highlightStart: Int?,
    highlightEnd: Int?,
    cachedSpeakerPhraseIndices: Set<Int>,
    speakerColorSlots: Map<String, Int>,
    darkTheme: Boolean,
    fontSizeSp: Float,
    lineHeightSp: Float,
    horizontalMarginDp: Float,
    topPaddingDp: Float,
    bottomPaddingDp: Float,
    fontFamily: ReaderFontFamily,
    textAlignment: ReaderTextAlignment,
    paragraphSpacingSp: Float,
    firstLineIndentSp: Float,
    textColor: Color,
    linkColor: Color,
    headingColor: Color,
    activeHighlightColor: Color,
    imageSize: ReaderImageSize,
    imageAlignment: ReaderImageAlignment,
    onToggleChrome: () -> Unit,
    onSpeakerTapped: (Int) -> Unit,
    onUserActivity: () -> Unit,
    onLinkTapped: (BookLink) -> Unit,
    onViewportChanged: (PageLayout) -> Unit,
    loadBookImage: suspend (BookDocument, BookImage) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val chunks = remember(book.id, book.text) { scrollChunks(book.text) }
    val listState = rememberLazyListState()
    val textLayouts = remember(book.id) { mutableStateMapOf<Int, RenderedTextLayout>() }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = textColor,
        fontFamily = fontFamily.toComposeFontFamily(),
        textAlign = textAlignment.toComposeTextAlign(),
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        letterSpacing = 0.15.sp,
    )

    fun chunkIndexAt(offset: Int): Int = chunks.indexOfLast { it.start <= offset }
        .coerceAtLeast(0)

    val activeChunkIndex = highlightStart?.let { chunkIndexAt(it.coerceIn(0, book.text.length)) }
    val activeTextLayout = activeChunkIndex?.let { textLayouts[chunks[it].start] }

    LaunchedEffect(book.id, requestedOffset) {
        val target = chunkIndexAt(requestedOffset.coerceIn(0, book.text.length))
        if (listState.firstVisibleItemIndex != target) listState.scrollToItem(target)
    }
    LaunchedEffect(book.id, highlightStart, activeTextLayout) {
        val target = highlightStart ?: return@LaunchedEffect
        val index = chunkIndexAt(target.coerceIn(0, book.text.length))
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (visibleItem == null) {
            // A new phrase outside the viewport begins at the top, then follows normally.
            listState.animateScrollToItem(index)
            return@LaunchedEffect
        }
        val renderedLayout = activeTextLayout ?: return@LaunchedEffect
        val layout = renderedLayout.layout
        val localOffset = (target - chunks[index].start)
            .let(renderedLayout.rendered::visualOffset)
            .coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        val phraseY = visibleItem.offset + layout.getBoundingBox(localOffset).top
        val viewportCenter = (
            listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
            ).toFloat() / 2f
        // Do not pull a new sentence upward. Once it reaches the midpoint, keep it there.
        if (phraseY > viewportCenter) listState.animateScrollBy(phraseY - viewportCenter)
    }
    fun firstVisibleBookOffset(): Int {
        val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            ?: return chunks.getOrNull(listState.firstVisibleItemIndex)?.start ?: 0
        val chunk = chunks.getOrNull(firstItem.index) ?: return 0
        val renderedLayout = textLayouts[chunk.start] ?: return chunk.start
        val localY = (listState.layoutInfo.viewportStartOffset - firstItem.offset).coerceAtLeast(0).toFloat()
        val visualOffset = renderedLayout.layout.getOffsetForPosition(Offset(0f, localY))
        return chunk.start + renderedLayout.rendered.bookOffset(visualOffset)
    }

    LaunchedEffect(listState, chunks, textLayouts.size) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, _) ->
                val chunk = chunks.getOrNull(index) ?: return@collect
                val firstVisible = firstVisibleBookOffset().coerceIn(chunk.start, chunk.end)
                onViewportChanged(PageLayout(firstVisible, chunk.end))
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) onUserActivity() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(top = topPaddingDp.dp, bottom = bottomPaddingDp.dp),
    ) {
        items(chunks.size, key = { chunks[it].start }) { index ->
            val chunk = chunks[index]
            val renderedChunk = remember(chunk.start, chunk.end, paragraphSpacingSp) {
                renderPageText(book.text, chunk, paragraphSpacingSp)
            }
            var textLayout by remember(chunk.start, chunk.end) {
                mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
            }
            Column {
            BookImagesForRange(book, chunk, imageSize, imageAlignment, loadBookImage)
            Text(
                text = highlightedPage(
                    text = book.text,
                    page = chunk,
                    renderedPage = renderedChunk,
                    phrases = phrases,
                    cachedSpeakerPhraseIndices = cachedSpeakerPhraseIndices,
                    speakerColorSlots = speakerColorSlots,
                    darkTheme = darkTheme,
                    highlightStart = highlightStart,
                    highlightEnd = highlightEnd,
                    links = book.links,
                    headings = book.headings,
                    linkColor = linkColor,
                    headingColor = headingColor,
                    activeHighlightColor = activeHighlightColor,
                    firstLineIndentSp = firstLineIndentSp,
                ),
                style = textStyle,
                onTextLayout = {
                    textLayout = it
                    textLayouts[chunk.start] = RenderedTextLayout(it, renderedChunk)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = horizontalMarginDp.dp, end = horizontalMarginDp.dp, top = 12.dp, bottom = 12.dp)
                    .pointerInput(chunk.start, chunk.end, phrases) {
                        detectTapGestures(
                            onTap = { position ->
                                onUserActivity()
                                val absoluteOffset = textLayout?.getOffsetForPosition(position)
                                    ?.let { chunk.start + renderedChunk.bookOffset(it) }
                                val link = absoluteOffset?.let { offset -> book.links.firstOrNull { offset in it.start until it.end } }
                                if (link != null) onLinkTapped(link) else onToggleChrome()
                            },
                            onLongPress = { position ->
                                textLayout?.getOffsetForPosition(position)
                                    ?.let { onSpeakerTapped(chunk.start + renderedChunk.bookOffset(it)) }
                            },
                        )
                    },
            )
            }
        }
    }
}

/** Keeps continuous mode lazy: only nearby paragraph-sized blocks are composed. */
private fun scrollChunks(text: String, maxCharacters: Int = 4_000): List<PageLayout> {
    if (text.isEmpty()) return listOf(PageLayout(0, 0))
    val chunks = mutableListOf<PageLayout>()
    var start = 0
    while (start < text.length) {
        var end = (start + maxCharacters).coerceAtMost(text.length)
        if (end < text.length) {
            val paragraphEnd = text.lastIndexOf('\n', startIndex = end - 1)
            if (paragraphEnd > start + maxCharacters / 2) end = paragraphEnd + 1
        }
        chunks += PageLayout(start, end)
        start = end
    }
    return chunks
}

@Composable
internal fun BookImagesForRange(
    book: BookDocument,
    range: PageLayout,
    imageSize: ReaderImageSize,
    imageAlignment: ReaderImageAlignment,
    loadBookImage: suspend (BookDocument, BookImage) -> ByteArray?,
) {
    val images = remember(book.images, range) {
        book.images.filter { it.offset in range.start until range.end }.take(MAX_IMAGES_PER_PAGE)
    }
    images.forEach { image ->
        val bitmap by produceState<android.graphics.Bitmap?>(null, book.localPath, image.archivePath) {
            value = withContext(Dispatchers.IO) {
                loadBookImage(book, image)?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        }
        bitmap?.let { loaded ->
            Box(
                contentAlignment = when (imageAlignment) {
                    ReaderImageAlignment.LEFT -> Alignment.CenterStart
                    ReaderImageAlignment.CENTER -> Alignment.Center
                    ReaderImageAlignment.RIGHT -> Alignment.CenterEnd
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = image.description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(
                            when (imageSize) {
                                ReaderImageSize.SMALL -> 0.35f
                                ReaderImageSize.MEDIUM -> 0.65f
                                ReaderImageSize.ORIGINAL -> 0.85f
                                ReaderImageSize.FIT_WIDTH -> 1f
                            },
                        )
                        .heightIn(max = 320.dp),
                )
            }
        }
    }
}

