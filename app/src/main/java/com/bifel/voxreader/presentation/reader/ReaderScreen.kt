package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterAnalysisSource
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.presentation.theme.ReaderHighlight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onToggleTheme: () -> Unit,
    onEnableSpeech: () -> Unit,
    onDisableSpeech: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousPhrase: () -> Unit,
    onNextPhrase: () -> Unit,
    onPageChanged: (Int, Int, Boolean) -> Unit,
    onDismissError: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ReaderDrawer(
                state = state,
                onToggleTheme = onToggleTheme,
                onEnableSpeech = {
                    onEnableSpeech()
                    scope.launch { drawerState.close() }
                },
                onDisableSpeech = {
                    onDisableSpeech()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.book == null -> EmptyLibraryScreen()
            else -> ReaderContent(
                state = state,
                onToggleChrome = onToggleChrome,
                onOpenDrawer = {
                    onHideChrome()
                    scope.launch { drawerState.open() }
                },
                onPlayPause = onPlayPause,
                onPreviousPhrase = onPreviousPhrase,
                onNextPhrase = onNextPhrase,
                onPageChanged = onPageChanged,
            )
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("OK")
                }
            },
            title = { Text("VoxReader") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun ReaderDrawer(
    state: ReaderUiState,
    onToggleTheme: () -> Unit,
    onEnableSpeech: () -> Unit,
    onDisableSpeech: () -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 28.dp),
        ) {
            Text(
                text = "VoxReader",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            )
            state.book?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            ListItem(
                headlineContent = { Text("Dark reading theme") },
                supportingContent = { Text("Black background with warm white text") },
                leadingContent = {
                    Icon(
                        imageVector = if (state.darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = { onToggleTheme() },
                    )
                },
                modifier = Modifier.clickable(role = Role.Switch) { onToggleTheme() },
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = {
                    Text(
                        if (state.speechControlsEnabled) {
                            "Hide speech player"
                        } else {
                            "Start text to speech"
                        },
                    )
                },
                supportingContent = {
                    Text("Uses a downloaded Android voice and stays offline")
                },
                leadingContent = {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    if (state.speechControlsEnabled) onDisableSpeech() else onEnableSpeech()
                },
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("TinyBERT dialogue classifier") },
                supportingContent = {
                    val source = if (
                        state.lastAnalysisSource == CharacterAnalysisSource.TINY_BERT
                    ) {
                        "TinyBERT"
                    } else {
                        "dialogue rules"
                    }
                    Text(
                        "Bundled and fully offline. Last speaker decision used $source.",
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Psychology, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    onToggleChrome: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousPhrase: () -> Unit,
    onNextPhrase: () -> Unit,
    onPageChanged: (Int, Int, Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            PaginatedBookPage(
                book = requireNotNull(state.book),
                phrases = state.phrases,
                requestedPageStart = state.pageStart,
                highlightStart = state.highlightStart,
                highlightEnd = state.highlightEnd,
                onToggleChrome = onToggleChrome,
                onPageChanged = onPageChanged,
                modifier = Modifier.weight(1f),
            )

            if (state.speechControlsEnabled) {
                SpeechControls(
                    playbackState = state.playbackState,
                    onPrevious = onPreviousPhrase,
                    onPlayPause = onPlayPause,
                    onNext = onNextPhrase,
                )
            }

            LinearProgressIndicator(
                progress = { state.readingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
    onToggleChrome: () -> Unit,
    onPageChanged: (Int, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 24)
    val pageStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.15.sp,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 27.dp, vertical = 22.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx().roundToInt() }
        val heightPx = with(density) { maxHeight.toPx().roundToInt() }
        var page by remember(book.id, widthPx, heightPx) {
            mutableStateOf(PageLayout(requestedPageStart, requestedPageStart))
        }

        fun forward(anchor: Int): PageLayout = PageCalculator.forward(
            text = book.text,
            anchor = anchor,
            phrases = phrases,
            textMeasurer = textMeasurer,
            style = pageStyle,
            widthPx = widthPx,
            heightPx = heightPx,
        )

        fun showPage(layout: PageLayout, userInitiated: Boolean) {
            page = layout
            onPageChanged(layout.start, layout.end, userInitiated)
        }

        LaunchedEffect(book.id, requestedPageStart, widthPx, heightPx, phrases.size) {
            showPage(forward(requestedPageStart), userInitiated = false)
        }

        LaunchedEffect(highlightStart, highlightEnd, page.start, page.end) {
            val start = highlightStart ?: return@LaunchedEffect
            val end = highlightEnd ?: start
            if (start < page.start || start >= page.end || end > page.end) {
                showPage(forward(start), userInitiated = false)
            }
        }

        if (page.end > page.start && page.end <= book.text.length) {
            Text(
                text = highlightedPage(
                    text = book.text,
                    page = page,
                    highlightStart = highlightStart,
                    highlightEnd = highlightEnd,
                ),
                style = pageStyle,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(Modifier.fillMaxSize()) {
            InvisibleTapZone(
                label = "Previous page",
                onClick = {
                    if (page.start > 0) {
                        showPage(
                            PageCalculator.backward(
                                text = book.text,
                                currentStart = page.start,
                                phrases = phrases,
                                textMeasurer = textMeasurer,
                                style = pageStyle,
                                widthPx = widthPx,
                                heightPx = heightPx,
                            ),
                            userInitiated = true,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            InvisibleTapZone(
                label = "Show or hide reader menu",
                onClick = onToggleChrome,
                modifier = Modifier.weight(1f),
            )
            InvisibleTapZone(
                label = "Next page",
                onClick = {
                    if (page.end < book.text.length) {
                        showPage(forward(page.end), userInitiated = true)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InvisibleTapZone(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
    )
}

private fun highlightedPage(
    text: String,
    page: PageLayout,
    highlightStart: Int?,
    highlightEnd: Int?,
): AnnotatedString = buildAnnotatedString {
    val pageText = text.substring(page.start, page.end)
    val start = highlightStart?.coerceIn(page.start, page.end)?.minus(page.start)
    val end = highlightEnd?.coerceIn(page.start, page.end)?.minus(page.start)

    if (start == null || end == null || end <= start) {
        append(pageText)
        return@buildAnnotatedString
    }

    append(pageText.substring(0, start))
    withStyle(
        SpanStyle(
            background = ReaderHighlight,
            fontWeight = FontWeight.SemiBold,
        ),
    ) {
        append(pageText.substring(start, end))
    }
    append(pageText.substring(end))
}

@Composable
private fun ReaderHeader(
    title: String,
    onOpenDrawer: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Open reader menu")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SpeechControls(
    playbackState: PlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence")
        }
        Spacer(Modifier.size(22.dp))
        when (playbackState) {
            PlaybackState.PREPARING -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            PlaybackState.PLAYING -> {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            else -> {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(22.dp))
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next sentence")
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyLibraryScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(36.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Open an EPUB to begin",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Choose an .epub file in Android's Files app and open it with VoxReader. " +
                    "The book is copied into the private offline library.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
