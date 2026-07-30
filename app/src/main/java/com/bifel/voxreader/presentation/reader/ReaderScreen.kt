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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterAnalysisSource
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.SpeechText
import com.bifel.voxreader.presentation.theme.ReaderHighlight
import kotlinx.coroutines.launch
import java.util.Locale
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
    onExportAnalysis: () -> Unit,
    onImportAnalysis: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onUpdateCharacterVoice: (CharacterProfile) -> Unit,
    onDismissError: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var editingCharacterId by remember { mutableStateOf<String?>(null) }
    val speakerColorSlots = remember(state.characters) {
        state.characters
            .asSequence()
            .filter { it.id != NARRATOR_ID && it.id != UNKNOWN_SPEAKER_ID }
            .map { it.id }
            .distinct()
            .sorted()
            .mapIndexed { index, speakerId -> speakerId to index }
            .toMap()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ReaderDrawer(
                state = state,
                speakerColorSlots = speakerColorSlots,
                onToggleTheme = onToggleTheme,
                onEnableSpeech = {
                    onEnableSpeech()
                    scope.launch { drawerState.close() }
                },
                onDisableSpeech = {
                    onDisableSpeech()
                    scope.launch { drawerState.close() }
                },
                onExportAnalysis = {
                    onExportAnalysis()
                    scope.launch { drawerState.close() }
                },
                onImportAnalysis = {
                    onImportAnalysis()
                    scope.launch { drawerState.close() }
                },
                onEditCharacter = { editingCharacterId = it.id },
            )
        },
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.book == null -> EmptyLibraryScreen()
            else -> ReaderContent(
                state = state,
                speakerColorSlots = speakerColorSlots,
                onToggleChrome = onToggleChrome,
                onOpenDrawer = {
                    onHideChrome()
                    scope.launch { drawerState.open() }
                },
                onPlayPause = onPlayPause,
                onPreviousPhrase = onPreviousPhrase,
                onNextPhrase = onNextPhrase,
                onSetPlaybackSpeed = onSetPlaybackSpeed,
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

    editingCharacterId
        ?.let { id -> state.characters.firstOrNull { it.id == id } }
        ?.let { profile ->
            CharacterVoiceDialog(
                profile = profile,
                onDismiss = { editingCharacterId = null },
                onSave = {
                    onUpdateCharacterVoice(it)
                    editingCharacterId = null
                },
            )
        }
}

@Composable
private fun ReaderDrawer(
    state: ReaderUiState,
    speakerColorSlots: Map<String, Int>,
    onToggleTheme: () -> Unit,
    onEnableSpeech: () -> Unit,
    onDisableSpeech: () -> Unit,
    onExportAnalysis: () -> Unit,
    onImportAnalysis: () -> Unit,
    onEditCharacter: (CharacterProfile) -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
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
                headlineContent = {
                    Text(
                        if (state.cachedAttributionCoverage >= COMPLETE_CACHE_THRESHOLD) {
                            "Cached JSON speaker map"
                        } else {
                            "TinyBERT dialogue classifier"
                        },
                    )
                },
                supportingContent = {
                    if (state.cachedAttributionCoverage >= COMPLETE_CACHE_THRESHOLD) {
                        Text("Authoritative for this book. Live speaker recognition is skipped.")
                    } else {
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
                    }
                },
                leadingContent = {
                    Icon(Icons.Default.Psychology, contentDescription = null)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            ListItem(
                headlineContent = { Text("Export speaker analysis JSON") },
                supportingContent = {
                    Text("Creates the portable whole-book file for cloud, desktop, or manual editing.")
                },
                leadingContent = {
                    Icon(Icons.Default.SaveAlt, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onExportAnalysis),
            )
            ListItem(
                headlineContent = { Text("Import speaker analysis JSON") },
                supportingContent = {
                    Text("Validates and caches assignments for the currently open book.")
                },
                leadingContent = {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onImportAnalysis),
            )
            state.cachedAttributionProvider?.let { provider ->
                Text(
                    text = "Cached analysis: $provider · " +
                        formatCoverage(state.cachedAttributionCoverage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
                VoiceProfileList(
                    state = state,
                    speakerColorSlots = speakerColorSlots,
                    onEditCharacter = onEditCharacter,
                )
            }
            state.analysisMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceProfileList(
    state: ReaderUiState,
    speakerColorSlots: Map<String, Int>,
    onEditCharacter: (CharacterProfile) -> Unit,
) {
    val cachedSpeakerIds = state.phrases
        .asSequence()
        .filter { SpeechText.isSpeakable(it.text) }
        .filter { it.index in state.cachedSpeakerPhraseIndices }
        .map(SpeechPhrase::speakerId)
        .filter { it != NARRATOR_ID && it != UNKNOWN_SPEAKER_ID }
        .toSet()
    val speakers = state.characters
        .filter {
            it.id == NARRATOR_ID ||
                (it.id in cachedSpeakerIds && it.id != UNKNOWN_SPEAKER_ID)
        }
        .sortedWith(compareBy({ it.id != NARRATOR_ID }, { it.displayName }))
    if (speakers.isEmpty()) return

    Text(
        text = "Voices and speaker colors",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
    speakers.forEach { character ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditCharacter(character) }
                .padding(horizontal = 24.dp, vertical = 9.dp),
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(
                        if (character.id == NARRATOR_ID) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        } else {
                            speakerHighlightColor(
                                colorSlot = speakerColorSlots.getValue(character.id),
                                darkTheme = state.darkTheme,
                            )
                        },
                    ),
            )
            Text(
                text = character.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = "${character.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase)} " +
                    "· ${formatMultiplier(character.speechRate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit ${character.displayName} voice",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    speakerColorSlots: Map<String, Int>,
    onToggleChrome: () -> Unit,
    onOpenDrawer: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousPhrase: () -> Unit,
    onNextPhrase: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
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
                cachedSpeakerPhraseIndices = state.cachedSpeakerPhraseIndices,
                speakerColorSlots = speakerColorSlots,
                darkTheme = state.darkTheme,
                onToggleChrome = onToggleChrome,
                onPageChanged = onPageChanged,
                modifier = Modifier.weight(1f),
            )

            if (state.speechControlsEnabled) {
                SpeechControls(
                    playbackState = state.playbackState,
                    playbackSpeed = state.playbackSpeed,
                    onPrevious = onPreviousPhrase,
                    onPlayPause = onPlayPause,
                    onNext = onNextPhrase,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
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
    cachedSpeakerPhraseIndices: Set<Int>,
    speakerColorSlots: Map<String, Int>,
    darkTheme: Boolean,
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
                    phrases = phrases,
                    cachedSpeakerPhraseIndices = cachedSpeakerPhraseIndices,
                    speakerColorSlots = speakerColorSlots,
                    darkTheme = darkTheme,
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
    phrases: List<SpeechPhrase>,
    cachedSpeakerPhraseIndices: Set<Int>,
    speakerColorSlots: Map<String, Int>,
    darkTheme: Boolean,
    highlightStart: Int?,
    highlightEnd: Int?,
): AnnotatedString = buildAnnotatedString {
    val pageText = text.substring(page.start, page.end)
    append(pageText)

    val phrasesByIndex = phrases.associateBy(SpeechPhrase::index)
    phrases.asSequence()
        .filter { phrase ->
            SpeechText.isSpeakable(phrase.text) &&
                phrase.index in cachedSpeakerPhraseIndices &&
                phrase.speakerId != NARRATOR_ID &&
                phrase.speakerId != UNKNOWN_SPEAKER_ID &&
                phrase.end > page.start &&
                phrase.start < page.end
        }
        .forEach { phrase ->
            val phraseStart = includeAdjacentOpeningQuotes(text, phrase.start)
                .coerceIn(page.start, page.end) - page.start
            val nextPhrase = phrasesByIndex[phrase.index + 1]
            val absoluteEnd = if (
                nextPhrase != null &&
                nextPhrase.index in cachedSpeakerPhraseIndices &&
                nextPhrase.speakerId == phrase.speakerId
            ) {
                nextPhrase.start
            } else {
                phrase.end
            }
            val phraseEnd = includeAdjacentClosingQuotes(text, absoluteEnd)
                .coerceIn(page.start, page.end) - page.start
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

    val start = highlightStart?.coerceIn(page.start, page.end)?.minus(page.start)
    val end = highlightEnd?.coerceIn(page.start, page.end)?.minus(page.start)
    if (start != null && end != null && end > start) {
        addStyle(
            style = SpanStyle(
                background = ReaderHighlight,
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

private const val COMPLETE_CACHE_THRESHOLD = 0.999f
private const val GOLDEN_ANGLE_DEGREES = 137.50776f
private val BOOK_QUOTE_MARKS = setOf('"', '\u201c', '\u201d', '\u201e', '\u00ab', '\u00bb')

private fun formatCoverage(coverage: Float): String {
    val percentage = coverage.coerceIn(0f, 1f) * 100f
    return when {
        percentage == 0f -> "0%"
        percentage < 0.1f -> "<0.1%"
        percentage < 10f -> "${(percentage * 10).roundToInt() / 10f}%"
        else -> "${percentage.roundToInt()}%"
    }
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
    playbackSpeed: Float,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
) {
    var showSpeedDialog by remember { mutableStateOf(false) }
    var draftSpeed by remember(playbackSpeed) { mutableStateOf(playbackSpeed) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TextButton(
            onClick = {
                draftSpeed = playbackSpeed
                showSpeedDialog = true
            },
        ) {
            Text("Playback speed  ${formatMultiplier(playbackSpeed)}")
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
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

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatMultiplier(draftSpeed),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Slider(
                        value = draftSpeed,
                        onValueChange = { draftSpeed = snapToTenth(it) },
                        valueRange = 0.5f..2f,
                        steps = 14,
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("0.5×")
                        Text("2.0×")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetPlaybackSpeed(draftSpeed)
                        showSpeedDialog = false
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CharacterVoiceDialog(
    profile: CharacterProfile,
    onDismiss: () -> Unit,
    onSave: (CharacterProfile) -> Unit,
) {
    var voiceGroup by remember(profile.id, profile.voiceGroup) {
        mutableStateOf(profile.voiceGroup)
    }
    var voiceSlot by remember(profile.id, profile.voiceSlot) {
        mutableStateOf(profile.voiceSlot.toFloat())
    }
    var pitch by remember(profile.id, profile.pitch) {
        mutableStateOf(profile.pitch)
    }
    var speechRate by remember(profile.id, profile.speechRate) {
        mutableStateOf(profile.speechRate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${profile.displayName} voice") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Voice family", style = MaterialTheme.typography.labelLarge)
                VoiceGroup.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { voiceGroup = option },
                    ) {
                        RadioButton(
                            selected = voiceGroup == option,
                            onClick = { voiceGroup = option },
                        )
                        Text(option.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
                Spacer(Modifier.height(10.dp))
                VoiceSliderLabel("Voice variation", voiceSlot.roundToInt().toString())
                Slider(
                    value = voiceSlot,
                    onValueChange = { voiceSlot = it.roundToInt().toFloat() },
                    valueRange = 0f..7f,
                    steps = 6,
                )
                VoiceSliderLabel("Pitch", formatDecimal(pitch))
                Slider(
                    value = pitch,
                    onValueChange = { pitch = snapToTenth(it) },
                    valueRange = 0.5f..1.6f,
                    steps = 10,
                )
                VoiceSliderLabel("Base speaking rate", formatMultiplier(speechRate))
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = snapToTenth(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                )
                Text(
                    text = "Book playback speed and phrase emotion are applied on top of these values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(
                            voiceGroup = voiceGroup,
                            voiceSlot = voiceSlot.roundToInt(),
                            pitch = pitch,
                            speechRate = speechRate,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun VoiceSliderLabel(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

private fun formatMultiplier(value: Float): String =
    "${formatDecimal(value)}×"

private fun formatDecimal(value: Float): String =
    String.format(Locale.ROOT, "%.1f", value)

private fun snapToTenth(value: Float): Float =
    (value * 10f).roundToInt() / 10f

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
