package com.bifel.voxreader.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookImage
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import kotlin.math.roundToInt

@Composable
internal fun ReaderContent(
    state: ReaderUiState,
    speakerColorSlots: Map<String, Int>,
    onToggleChrome: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousPhrase: () -> Unit,
    onNextPhrase: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onPageChanged: (Int, Int, Boolean) -> Unit,
    onSpeakerTapped: (Int) -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleSleepTimer: () -> Unit,
    onRegisterUserActivity: () -> Unit,
    onLinkTapped: (BookLink) -> Unit,
    loadBookImage: suspend (BookDocument, BookImage) -> ByteArray?,
) {
    var displayedPage by remember(state.book?.id) {
        mutableStateOf(PageLayout(state.pageStart, state.pageEnd))
    }
    var estimatedCharactersPerPage by remember(
        state.book?.id,
        state.speechControlsEnabled,
    ) { mutableStateOf(500) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBackground(state)),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.readingMode == ReadingMode.PAGINATED) {
                PaginatedBookPage(
                    book = requireNotNull(state.book),
                    phrases = state.phrases,
                    requestedPageStart = state.pageStart,
                    highlightStart = state.highlightStart,
                    highlightEnd = state.highlightEnd,
                    cachedSpeakerPhraseIndices = state.cachedSpeakerPhraseIndices,
                    speakerColorSlots = speakerColorSlots,
                    darkTheme = state.darkTheme,
                    fontSizeSp = state.fontSizeSp,
                    lineHeightSp = state.lineHeightSp,
                    horizontalMarginDp = state.horizontalMarginDp,
                    topPaddingDp = state.topPaddingDp,
                    bottomPaddingDp = state.bottomPaddingDp,
                    fontFamily = state.fontFamily,
                    textAlignment = state.textAlignment,
                    paragraphSpacingSp = state.paragraphSpacingSp,
                    firstLineIndentSp = state.firstLineIndentSp,
                    textColor = readerTextColor(state),
                    linkColor = readerLinkColor(state),
                    headingColor = readerHeadingColor(state),
                    activeHighlightColor = readerActiveHighlightColor(state),
                    imageSize = state.imageSize,
                    imageAlignment = state.imageAlignment,
                    onToggleChrome = onToggleChrome,
                    onSpeakerTapped = onSpeakerTapped,
                    onUserActivity = onRegisterUserActivity,
                    onLinkTapped = onLinkTapped,
                    onPageChanged = onPageChanged,
                    onPageDisplayed = { displayedPage = it },
                    onPageSizeEstimated = { estimatedCharactersPerPage = it },
                    loadBookImage = loadBookImage,
                    modifier = Modifier.weight(1f),
                )
            } else {
                VerticalScrollableBook(
                    book = requireNotNull(state.book),
                    phrases = state.phrases,
                    requestedOffset = state.pageStart,
                    highlightStart = state.highlightStart,
                    highlightEnd = state.highlightEnd,
                    cachedSpeakerPhraseIndices = state.cachedSpeakerPhraseIndices,
                    speakerColorSlots = speakerColorSlots,
                    darkTheme = state.darkTheme,
                    fontSizeSp = state.fontSizeSp,
                    lineHeightSp = state.lineHeightSp,
                    horizontalMarginDp = state.horizontalMarginDp,
                    topPaddingDp = state.topPaddingDp,
                    bottomPaddingDp = state.bottomPaddingDp,
                    fontFamily = state.fontFamily,
                    textAlignment = state.textAlignment,
                    paragraphSpacingSp = state.paragraphSpacingSp,
                    firstLineIndentSp = state.firstLineIndentSp,
                    textColor = readerTextColor(state),
                    linkColor = readerLinkColor(state),
                    headingColor = readerHeadingColor(state),
                    activeHighlightColor = readerActiveHighlightColor(state),
                    imageSize = state.imageSize,
                    imageAlignment = state.imageAlignment,
                    onToggleChrome = onToggleChrome,
                    onSpeakerTapped = onSpeakerTapped,
                    onUserActivity = onRegisterUserActivity,
                    onLinkTapped = onLinkTapped,
                    onViewportChanged = { page ->
                        displayedPage = page
                        estimatedCharactersPerPage = page.end - page.start
                        onPageChanged(page.start, page.end, false)
                    },
                    loadBookImage = loadBookImage,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.speechControlsEnabled) {
                SpeechControls(
                    playbackState = state.playbackState,
                    playbackSpeed = state.playbackSpeed,
                    onPrevious = onPreviousPhrase,
                    onPlayPause = onPlayPause,
                    onNext = onNextPhrase,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    sleepTimerEnabled = state.sleepTimerEnabled,
                    onToggleSleepTimer = onToggleSleepTimer,
                )
            }

            BookProgressNavigation(
                book = requireNotNull(state.book),
                displayedPage = displayedPage,
                charactersPerPage = estimatedCharactersPerPage,
                onSeekOffset = { offset ->
                    onPageChanged(offset, offset, true)
                },
            )
            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(2.dp),
            )
        }

        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderHeader(
                title = state.book?.title.orEmpty(),
                onOpenDrawer = onOpenDrawer,
                onHistoryBack = onHistoryBack,
                onHistoryForward = onHistoryForward,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
            )
        }
    }
}

@Composable
private fun PaginatedBookPage(
    book: BookDocument,
    phrases: List<SpeechPhrase>,
    requestedPageStart: Int,
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
    onPageChanged: (Int, Int, Boolean) -> Unit,
    onPageDisplayed: (PageLayout) -> Unit,
    onPageSizeEstimated: (Int) -> Unit,
    loadBookImage: suspend (BookDocument, BookImage) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 24)
    val pageStyle = MaterialTheme.typography.bodyLarge.copy(
        color = textColor,
        fontFamily = fontFamily.toComposeFontFamily(),
        textAlign = textAlignment.toComposeTextAlign(),
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        letterSpacing = 0.15.sp,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalMarginDp.dp, end = horizontalMarginDp.dp, top = topPaddingDp.dp, bottom = bottomPaddingDp.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx().roundToInt() }
        val heightPx = with(density) { maxHeight.toPx().roundToInt() }
        var page by remember(book.id, widthPx, heightPx) {
            mutableStateOf(PageLayout(requestedPageStart, requestedPageStart))
        }
        var previousPage by remember(book.id, widthPx, heightPx) {
            mutableStateOf<PageLayout?>(null)
        }
        var nextPage by remember(book.id, widthPx, heightPx) {
            mutableStateOf<PageLayout?>(null)
        }
        var pageSizeReported by remember(book.id, widthPx, heightPx) {
            mutableStateOf(false)
        }

        fun forward(anchor: Int): PageLayout = PageCalculator.forward(
            text = book.text,
            anchor = anchor,
            phrases = phrases,
            textMeasurer = textMeasurer,
            style = pageStyle,
            widthPx = widthPx,
            heightPx = heightPx,
            chapterStarts = book.chapterStarts,
            paragraphSpacingSp = paragraphSpacingSp,
        )

        fun showPage(layout: PageLayout, userInitiated: Boolean) {
            page = layout
            onPageDisplayed(layout)
            if (!pageSizeReported && layout.end > layout.start) {
                pageSizeReported = true
                onPageSizeEstimated(layout.end - layout.start)
            }
            onPageChanged(layout.start, layout.end, userInitiated)
        }

        LaunchedEffect(book.id, requestedPageStart, widthPx, heightPx, phrases.size) {
            val layout = if (book.text.isNotEmpty() && requestedPageStart >= book.text.lastIndex) {
                PageCalculator.backward(
                    text = book.text,
                    currentStart = book.text.length,
                    phrases = phrases,
                    textMeasurer = textMeasurer,
                    style = pageStyle,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    chapterStarts = book.chapterStarts,
                    paragraphSpacingSp = paragraphSpacingSp,
                )
            } else {
                forward(requestedPageStart)
            }
            showPage(layout, userInitiated = false)
        }

        LaunchedEffect(book.id, page.start, page.end, widthPx, heightPx, phrases.size) {
            withFrameNanos { }
            previousPage = if (page.start > 0) {
                PageCalculator.backward(
                    text = book.text,
                    currentStart = page.start,
                    phrases = phrases,
                    textMeasurer = textMeasurer,
                    style = pageStyle,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    chapterStarts = book.chapterStarts,
                    paragraphSpacingSp = paragraphSpacingSp,
                )
            } else {
                null
            }
            withFrameNanos { }
            nextPage = if (page.end in 1 until book.text.length) forward(page.end) else null
        }

        LaunchedEffect(highlightStart, highlightEnd, page.start, page.end) {
            val start = highlightStart ?: return@LaunchedEffect
            val end = highlightEnd ?: start
            if (start < page.start || start >= page.end || end > page.end) {
                showPage(forward(start), userInitiated = false)
            }
        }

        if (page.end > page.start && page.end <= book.text.length) {
            var textLayout by remember(page.start, page.end) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
            val renderedPage = remember(page.start, page.end, paragraphSpacingSp) {
                renderPageText(book.text, page, paragraphSpacingSp)
            }
            Column(Modifier.fillMaxSize()) {
            BookImagesForRange(book, page, imageSize, imageAlignment, loadBookImage)
            Text(
                text = highlightedPage(
                    text = book.text,
                    page = page,
                    renderedPage = renderedPage,
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
                style = pageStyle,
                overflow = TextOverflow.Clip,
                onTextLayout = { textLayout = it },
                modifier = Modifier
                    .fillMaxWidth().weight(1f)
                    .pointerInput(page.start, page.end, phrases) {
                        detectTapGestures(
                            onTap = { position ->
                                onUserActivity()
                                val absoluteOffset = textLayout?.getOffsetForPosition(position)
                                    ?.let { page.start + renderedPage.bookOffset(it) }
                                val link = absoluteOffset?.let { offset -> book.links.firstOrNull { offset in it.start until it.end } }
                                if (link != null) {
                                    onLinkTapped(link)
                                } else when {
                                    position.x < widthPx / 3f && page.start > 0 -> showPage(
                                        previousPage ?: PageCalculator.backward(book.text, page.start, phrases, textMeasurer, pageStyle, widthPx, heightPx, book.chapterStarts, paragraphSpacingSp),
                                        true,
                                    )
                                    position.x > widthPx * 2f / 3f && page.end < book.text.length -> showPage(nextPage ?: forward(page.end), true)
                                    else -> onToggleChrome()
                                }
                            },
                            onLongPress = { position ->
                                textLayout?.getOffsetForPosition(position)
                                    ?.let { onSpeakerTapped(page.start + renderedPage.bookOffset(it)) }
                            },
                        )
                    },
            )
            }
        }

    }
}
