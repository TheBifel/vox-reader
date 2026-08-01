package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.widget.Toast
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
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.UnknownProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.data.speech.PiperVoicePack
import com.bifel.voxreader.data.speech.VoicePackDownloadState
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
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
    onDownloadLocalVoice: (String) -> Unit,
    onRemoveLocalVoice: (String) -> Unit,
    onPreviewLocalVoice: (String) -> Unit,
    onUpdateCharacterVoice: (CharacterProfile) -> Unit,
    onPreviewCharacterVoice: (CharacterProfile) -> Unit,
    onSpeakerTapped: (Int) -> String?,
    onSetReadingAppearance: (Float, Float, Float) -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
    onDismissError: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var editingCharacterId by remember { mutableStateOf<String?>(null) }
    var showAppearance by remember { mutableStateOf(false) }
    var showCharacters by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speakerColorSlots = remember(state.characters) {
        state.characters
            .asSequence()
            .filter { it.id != NARRATOR_ID }
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
                onSetSpeechEngineMode = onSetSpeechEngineMode,
                onDownloadLocalVoice = onDownloadLocalVoice,
                onRemoveLocalVoice = onRemoveLocalVoice,
                onPreviewLocalVoice = onPreviewLocalVoice,
                onEditCharacter = { editingCharacterId = it.id },
                onShowCharacters = {
                    showCharacters = true
                    scope.launch { drawerState.close() }
                },
                onShowAppearance = { showAppearance = true },
                onGoToChapter = { offset ->
                    onPageChanged(offset, offset, true)
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
                onSpeakerTapped = { offset ->
                    onSpeakerTapped(offset)?.let { Toast.makeText(context, "$it’s voice", Toast.LENGTH_SHORT).show() }
                },
                onHistoryBack = onHistoryBack,
                onHistoryForward = onHistoryForward,
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

    if (showCharacters) {
        CharacterListDialog(
            state = state,
            speakerColorSlots = speakerColorSlots,
            onDismiss = { showCharacters = false },
            onEditCharacter = {
                showCharacters = false
                editingCharacterId = it.id
            },
        )
    }

    editingCharacterId
        ?.let { id -> state.characters.firstOrNull { it.id == id } }
        ?.let { profile ->
            CharacterVoiceDialog(
                profile = profile,
                speechEngineMode = state.speechEngineMode,
                localVoicePacks = state.localVoicePacks,
                onDismiss = { editingCharacterId = null },
                onPreview = onPreviewCharacterVoice,
                onSave = {
                    onUpdateCharacterVoice(it)
                    editingCharacterId = null
                },
            )
        }

    if (showAppearance) {
        AppearanceDialog(
            fontSize = state.fontSizeSp,
            lineHeight = state.lineHeightSp,
            margin = state.horizontalMarginDp,
            onDismiss = { showAppearance = false },
            onSave = { font, line, margin ->
                onSetReadingAppearance(font, line, margin)
                showAppearance = false
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
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
    onDownloadLocalVoice: (String) -> Unit,
    onRemoveLocalVoice: (String) -> Unit,
    onPreviewLocalVoice: (String) -> Unit,
    onEditCharacter: (CharacterProfile) -> Unit,
    onShowCharacters: () -> Unit,
    onShowAppearance: () -> Unit,
    onGoToChapter: (Int) -> Unit,
) {
    var showEngineDialog by remember { mutableStateOf(false) }
    var showVoicePacks by remember { mutableStateOf(false) }
    var chaptersExpanded by remember { mutableStateOf(false) }
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
            state.book?.let { book ->
                val currentChapter = book.chapterStarts.indexOfLast { it <= state.pageStart }.coerceAtLeast(0)
                ListItem(
                    headlineContent = { Text("Table of contents") },
                    supportingContent = { Text("Chapter ${currentChapter + 1} of ${book.chapterStarts.size}") },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { chaptersExpanded = !chaptersExpanded },
                )
                AnimatedVisibility(visible = chaptersExpanded) {
                    Column {
                        book.chapterStarts.forEachIndexed { index, start ->
                            ListItem(
                                headlineContent = { Text(chapterLabel(book, index, start)) },
                                supportingContent = {
                                    if (index == currentChapter) Text("Current chapter", color = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable {
                                    chaptersExpanded = false
                                    onGoToChapter(start)
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            ListItem(
                headlineContent = { Text("Reading appearance") },
                supportingContent = { Text("Font size, line spacing, and page margins") },
                modifier = Modifier.clickable(onClick = onShowAppearance),
            )
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
            ListItem(
                headlineContent = {
                    Text(
                        if (state.speechEngineMode == SpeechEngineMode.LOCAL_GENERATIVE) {
                            "Voice engine: Local AI"
                        } else {
                            "Voice engine: Procedural"
                        },
                    )
                },
                supportingContent = {
                    Text(
                        if (state.speechEngineMode == SpeechEngineMode.LOCAL_GENERATIVE) {
                            "Choose downloaded AI voices for each character."
                        } else {
                            "Fast Android offline voices with pitch and rate controls."
                        },
                    )
                },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                modifier = Modifier.clickable { showEngineDialog = true },
            )
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = {
                    Text(
                        if (state.cachedAttributionProvider != null) {
                            "Cached JSON speaker map"
                        } else {
                            "Narrator-only mode"
                        },
                    )
                },
                supportingContent = {
                    if (state.cachedAttributionProvider != null) {
                        Text("Authoritative character voices and colors for this book.")
                    } else {
                        Text("Import a speaker-analysis JSON to add character voices and colors.")
                    }
                },
                leadingContent = {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
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
                ListItem(
                    headlineContent = { Text("Characters and voices") },
                    supportingContent = { Text("${visibleVoiceProfiles(state).size} available profiles") },
                    leadingContent = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onShowCharacters),
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

    VoiceEngineDialogs(
        showEngineDialog = showEngineDialog,
        showVoicePacks = showVoicePacks,
        speechEngineMode = state.speechEngineMode,
        localGenerativeVoiceAvailable = state.localGenerativeVoiceAvailable,
        localVoicePacks = state.localVoicePacks,
        onShowEngineDialog = { showEngineDialog = it },
        onShowVoicePacks = { showVoicePacks = it },
        onSetSpeechEngineMode = onSetSpeechEngineMode,
        onDownloadLocalVoice = onDownloadLocalVoice,
        onRemoveLocalVoice = onRemoveLocalVoice,
        onPreviewLocalVoice = onPreviewLocalVoice,
    )
}

private fun chapterLabel(book: BookDocument, index: Int, start: Int): String {
    val preview = book.text.substring(start, minOf(book.text.length, start + 140))
        .lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return preview.takeIf { it.length in 2..80 } ?: "Chapter ${index + 1}"
}

@Composable
private fun visibleVoiceProfiles(
    state: ReaderUiState,
): List<CharacterProfile> {
    val cachedSpeakerIds = state.phrases
        .asSequence()
        .filter { SpeechText.isSpeakable(it.text) }
        .filter { it.index in state.cachedSpeakerPhraseIndices }
        .map(SpeechPhrase::speakerId)
        .filter { it != NARRATOR_ID }
        .toSet()
    val speakers = state.characters
        .filter {
            it.id == NARRATOR_ID ||
                it.id in cachedSpeakerIds
        }
        .let { profiles ->
            if (UNKNOWN_SPEAKER_ID in cachedSpeakerIds && profiles.none { it.id == UNKNOWN_SPEAKER_ID }) {
                profiles + UnknownProfile
            } else {
                profiles
            }
        }
        .sortedWith(
            compareBy<CharacterProfile>(
                { when (it.id) { NARRATOR_ID -> 0; UNKNOWN_SPEAKER_ID -> 2; else -> 1 } },
                { it.displayName },
            ),
        )
    return speakers
}

@Composable
private fun CharacterListDialog(
    state: ReaderUiState,
    speakerColorSlots: Map<String, Int>,
    onDismiss: () -> Unit,
    onEditCharacter: (CharacterProfile) -> Unit,
) {
    val speakers = visibleVoiceProfiles(state)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Characters and voices") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tap a character to choose and tune its voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
    speakers.forEach { character ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditCharacter(character) }
                .padding(vertical = 9.dp),
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
    onSpeakerTapped: (Int) -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
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
                fontSizeSp = state.fontSizeSp,
                lineHeightSp = state.lineHeightSp,
                horizontalMarginDp = state.horizontalMarginDp,
                onToggleChrome = onToggleChrome,
                onSpeakerTapped = onSpeakerTapped,
                onPageChanged = onPageChanged,
                onPageDisplayed = { displayedPage = it },
                onPageSizeEstimated = { estimatedCharactersPerPage = it },
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
    onToggleChrome: () -> Unit,
    onSpeakerTapped: (Int) -> Unit,
    onPageChanged: (Int, Int, Boolean) -> Unit,
    onPageDisplayed: (PageLayout) -> Unit,
    onPageSizeEstimated: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 24)
    val pageStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = FontFamily.Serif,
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        letterSpacing = 0.15.sp,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMarginDp.dp, vertical = 22.dp),
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
                onTextLayout = { textLayout = it },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page.start, page.end, phrases) {
                        detectTapGestures(
                            onTap = { onToggleChrome() },
                            onLongPress = { position ->
                                textLayout?.getOffsetForPosition(position)
                                    ?.let { onSpeakerTapped(page.start + it) }
                            },
                        )
                    },
            )
        }

        Row(Modifier.fillMaxSize()) {
            InvisibleTapZone(
                label = "Previous page",
                onClick = {
                    if (page.start > 0) {
                        showPage(
                            previousPage?.takeIf { it.end == page.start }
                                ?: PageCalculator.backward(
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
            Box(Modifier.weight(1f))
            InvisibleTapZone(
                label = "Next page",
                onClick = {
                    if (page.end < book.text.length) {
                        showPage(
                            nextPage?.takeIf { it.start == page.end } ?: forward(page.end),
                            userInitiated = true,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BookProgressNavigation(
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
                    .coerceIn(page.start, page.end) - page.start
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
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
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
        IconButton(onClick = onHistoryBack) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Back in reading history")
        }
        IconButton(onClick = onHistoryForward) {
            Icon(Icons.Default.SkipNext, contentDescription = "Forward in reading history")
        }
    }
}

@Composable
private fun AppearanceDialog(
    fontSize: Float,
    lineHeight: Float,
    margin: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float) -> Unit,
) {
    var font by remember(fontSize) { mutableStateOf(fontSize) }
    var line by remember(lineHeight) { mutableStateOf(lineHeight) }
    var pageMargin by remember(margin) { mutableStateOf(margin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading appearance") },
        text = {
            Column {
                VoiceSliderLabel("Font size", "${font.roundToInt()} sp")
                Slider(value = font, onValueChange = { font = it }, valueRange = 14f..32f, steps = 17)
                VoiceSliderLabel("Line spacing", "${line.roundToInt()} sp")
                Slider(value = line, onValueChange = { line = it }, valueRange = 18f..48f, steps = 29)
                VoiceSliderLabel("Page margins", "${pageMargin.roundToInt()} dp")
                Slider(value = pageMargin, onValueChange = { pageMargin = it }, valueRange = 12f..56f, steps = 10)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(font, line, pageMargin) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun EngineChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VoiceEngineDialogs(
    showEngineDialog: Boolean,
    showVoicePacks: Boolean,
    speechEngineMode: SpeechEngineMode,
    localGenerativeVoiceAvailable: Boolean,
    localVoicePacks: List<PiperVoicePack>,
    onShowEngineDialog: (Boolean) -> Unit,
    onShowVoicePacks: (Boolean) -> Unit,
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
    onDownloadLocalVoice: (String) -> Unit,
    onRemoveLocalVoice: (String) -> Unit,
    onPreviewLocalVoice: (String) -> Unit,
) {
    var voicePendingRemoval by remember { mutableStateOf<PiperVoicePack?>(null) }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { onShowEngineDialog(false) },
            title = { Text("Voice engine") },
            text = {
                Column {
                    EngineChoice(
                        title = "Procedural voice",
                        description = "Fast Android offline voice with pitch and rate controls.",
                        selected = speechEngineMode == SpeechEngineMode.PROCEDURAL,
                    ) {
                        onSetSpeechEngineMode(SpeechEngineMode.PROCEDURAL)
                        onShowEngineDialog(false)
                    }
                    EngineChoice(
                        title = "Local AI voice",
                        description = if (localGenerativeVoiceAvailable) {
                            "Uses downloaded AI voice packs and buffers speech on this device."
                        } else {
                            "Download at least one compatible AI voice pack first."
                        },
                        selected = speechEngineMode == SpeechEngineMode.LOCAL_GENERATIVE,
                    ) {
                        onSetSpeechEngineMode(SpeechEngineMode.LOCAL_GENERATIVE)
                        onShowEngineDialog(false)
                    }
                    TextButton(onClick = { onShowVoicePacks(true) }) { Text("Add or manage AI voices") }
                }
            },
            confirmButton = { TextButton(onClick = { onShowEngineDialog(false) }) { Text("Done") } },
        )
    }

    if (showVoicePacks) {
        AlertDialog(
            onDismissRequest = { onShowVoicePacks(false) },
            title = { Text("Local AI voices") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Use Play to preview a downloaded voice. Downloaded voices can be assigned to characters.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    localVoicePacks.forEach { pack ->
                        ListItem(
                            headlineContent = { Text(pack.displayName) },
                            supportingContent = {
                                Column {
                                    Text(pack.description)
                                    Text(
                                        "${pack.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase)} - " +
                                            formatVoicePackSize(pack.installedSizeBytes) + " - " +
                                            when (pack.downloadState) {
                                                VoicePackDownloadState.INSTALLED -> "Installed"
                                                VoicePackDownloadState.DOWNLOADING -> "Downloading..."
                                                VoicePackDownloadState.FAILED -> pack.error ?: "Download failed"
                                                VoicePackDownloadState.NOT_INSTALLED -> "Not downloaded"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (pack.downloadState == VoicePackDownloadState.DOWNLOADING) {
                                        LinearProgressIndicator(
                                            progress = { pack.downloadProgress ?: 0f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    if (pack.installed) {
                                        TextButton(onClick = { onPreviewLocalVoice(pack.id) }) { Text("Play") }
                                        TextButton(onClick = { voicePendingRemoval = pack }) { Text("Remove") }
                                    } else {
                                        TextButton(
                                            enabled = pack.downloadState != VoicePackDownloadState.DOWNLOADING,
                                            onClick = { onDownloadLocalVoice(pack.id) },
                                        ) { Text("Download") }
                                    }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onShowVoicePacks(false) }) { Text("Done") } },
        )
    }

    voicePendingRemoval?.let { pack ->
        AlertDialog(
            onDismissRequest = { voicePendingRemoval = null },
            title = { Text("Remove ${pack.displayName}?") },
            text = { Text("This deletes the downloaded voice model from this device. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveLocalVoice(pack.id)
                    voicePendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { voicePendingRemoval = null }) { Text("Cancel") } },
        )
    }
}

private fun formatVoicePackSize(sizeBytes: Long?): String =
    sizeBytes?.let { String.format(Locale.ROOT, "%.1f MB", it / (1024f * 1024f)) }
        ?: "Size shown after download"

@Composable
private fun CharacterVoiceDialog(
    profile: CharacterProfile,
    speechEngineMode: SpeechEngineMode,
    localVoicePacks: List<PiperVoicePack>,
    onDismiss: () -> Unit,
    onPreview: (CharacterProfile) -> Unit,
    onSave: (CharacterProfile) -> Unit,
) {
    val installedAiVoices = localVoicePacks.filter { it.installed }
    val initialAiVoice = installedAiVoices
        .filter { it.voiceGroup == profile.voiceGroup }
        .getOrNull(Math.floorMod(profile.voiceSlot, installedAiVoices.count { it.voiceGroup == profile.voiceGroup }.coerceAtLeast(1)))
        ?: installedAiVoices.firstOrNull()
    var selectedAiVoiceId by remember(profile.id, localVoicePacks) {
        mutableStateOf(initialAiVoice?.id)
    }
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
    fun draftProfile(): CharacterProfile {
        val selectedAiVoice = installedAiVoices.firstOrNull { it.id == selectedAiVoiceId }
        val aiVoiceSlot = selectedAiVoice?.let { selected ->
            installedAiVoices.filter { it.voiceGroup == selected.voiceGroup }.indexOf(selected)
        } ?: voiceSlot.roundToInt()
        return profile.copy(
            voiceGroup = selectedAiVoice?.voiceGroup ?: voiceGroup,
            voiceSlot = aiVoiceSlot,
            pitch = pitch,
            speechRate = speechRate,
        )
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
                if (speechEngineMode == SpeechEngineMode.LOCAL_GENERATIVE) {
                    Text("AI voice pack", style = MaterialTheme.typography.labelLarge)
                    if (installedAiVoices.isEmpty()) {
                        Text(
                            "No AI voices are installed. Add them from Voice engine in the reader menu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        installedAiVoices.forEach { pack ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAiVoiceId = pack.id },
                            ) {
                                RadioButton(
                                    selected = selectedAiVoiceId == pack.id,
                                    onClick = { selectedAiVoiceId = pack.id },
                                )
                                Column {
                                    Text(pack.displayName)
                                    Text(
                                        pack.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "AI voice pitch is model-defined. Speaking rate is applied during generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
                    VoiceSliderLabel("Pitch / bass", formatDecimal(pitch))
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = snapToTenth(it) },
                        valueRange = 0.5f..1.6f,
                        steps = 10,
                    )
                }
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
            Row {
                TextButton(
                    enabled = speechEngineMode != SpeechEngineMode.LOCAL_GENERATIVE || installedAiVoices.isNotEmpty(),
                    onClick = { onPreview(draftProfile()) },
                ) {
                    Text("Preview")
                }
                TextButton(onClick = { onSave(draftProfile()) }) {
                    Text("Save")
                }
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
