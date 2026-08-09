package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.BookLink
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.UnknownProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.EmotionProcessingProfile
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.model.defaultEmotionProcessingProfiles
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.data.speech.PiperVoicePack
import com.bifel.voxreader.data.speech.AdvancedVoice
import com.bifel.voxreader.data.speech.AdvancedVoiceModel
import com.bifel.voxreader.data.speech.VoicePackDownloadState
import com.bifel.voxreader.data.speech.VoicePackLanguage
import com.bifel.voxreader.domain.speech.SpeechText
import com.bifel.voxreader.domain.speech.ProceduralVoice
import com.bifel.voxreader.domain.speech.ProceduralVoiceLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.ZipFile
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
    onImportAnalysis: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
    onSetSpeechPostProcessing: (SpeechPostProcessingSettings) -> Unit,
    onDownloadLocalVoice: (String) -> Unit,
    onRemoveLocalVoice: (String) -> Unit,
    onPreviewLocalVoice: (String) -> Unit,
    onDownloadAdvancedModel: () -> Unit,
    onRemoveAdvancedModel: () -> Unit,
    onPreviewAdvancedVoice: (String) -> Unit,
    onLoadProceduralVoices: () -> Unit,
    onPreviewProceduralVoice: (String) -> Unit,
    onUpdateCharacterVoice: (CharacterProfile) -> Unit,
    onPreviewCharacterVoice: (CharacterProfile) -> Unit,
    onSpeakerTapped: (Int) -> String?,
    onSetReadingAppearance: (Float, Float, Float, Float, Float) -> Unit,
    onSetTypography: (ReaderFontFamily, ReaderTextAlignment, Float, Float) -> Unit,
    onSetDisplayBehavior: (ReaderOrientation, Boolean) -> Unit,
    onSetImageAppearance: (ReaderImageSize, ReaderImageAlignment) -> Unit,
    onSetReaderColors: (Boolean, Int, Int, Int, Int, Int) -> Unit,
    onSetSleepTimerSettings: (Int, Int) -> Unit,
    onToggleSleepTimer: () -> Unit,
    onRegisterUserActivity: () -> Unit,
    onOpenInternalLink: (Int) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onSetReadingMode: (ReadingMode) -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
    onDismissError: () -> Unit,
    onOpenBook: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var editingCharacterId by remember { mutableStateOf<String?>(null) }
    var showAppearance by remember { mutableStateOf(false) }
    var showCharacters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVoiceModels by remember { mutableStateOf(false) }
    var showAdvancedVoiceModels by remember { mutableStateOf(false) }
    var showProceduralVoices by remember { mutableStateOf(false) }
    var showSpeechPostProcessing by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showImages by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
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
                onEnableSpeech = onEnableSpeech,
                onDisableSpeech = onDisableSpeech,
                onImportAnalysis = {
                    onImportAnalysis()
                    scope.launch { drawerState.close() }
                },
                onShowCharacters = {
                    showCharacters = true
                    scope.launch { drawerState.close() }
                },
                onShowSettings = {
                    showSettings = true
                    scope.launch { drawerState.close() }
                },
                onOpenBook = {
                    scope.launch { drawerState.close() }
                    onOpenBook()
                },
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
            showSettings && showVoiceModels -> VoiceModelsScreen(
                packs = state.localVoicePacks,
                onBack = { showVoiceModels = false },
                onDownload = onDownloadLocalVoice,
                onRemove = onRemoveLocalVoice,
                onPreview = onPreviewLocalVoice,
            )
            showSettings && showProceduralVoices -> ProceduralVoicesScreen(
                voices = state.proceduralVoices,
                onBack = { showProceduralVoices = false },
                onPreview = onPreviewProceduralVoice,
            )
            showSettings && showAdvancedVoiceModels -> AdvancedVoiceModelsScreen(
                model = state.advancedVoiceModel,
                voices = state.advancedVoices,
                onBack = { showAdvancedVoiceModels = false },
                onDownload = onDownloadAdvancedModel,
                onRemove = onRemoveAdvancedModel,
                onPreview = onPreviewAdvancedVoice,
            )
            showSettings && showSpeechPostProcessing -> SpeechPostProcessingScreen(
                settings = state.speechPostProcessing,
                onBack = { showSpeechPostProcessing = false },
                onSave = onSetSpeechPostProcessing,
            )
            showSettings -> SettingsScreen(
                state = state,
                onBack = { showSettings = false },
                onShowAppearance = { showAppearance = true },
                onShowVoiceModels = { showVoiceModels = true },
                onShowAdvancedVoiceModels = { showAdvancedVoiceModels = true },
                onShowProceduralVoices = {
                    onLoadProceduralVoices()
                    showProceduralVoices = true
                },
                onSetSpeechEngineMode = onSetSpeechEngineMode,
                onShowSpeechPostProcessing = { showSpeechPostProcessing = true },
                onShowColors = { showColors = true },
                onShowDisplay = { showDisplay = true },
                onShowImages = { showImages = true },
                onShowSleepTimer = { showSleepTimer = true },
            )
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
                darkTheme = state.darkTheme,
                onToggleTheme = onToggleTheme,
                onToggleSleepTimer = onToggleSleepTimer,
                onRegisterUserActivity = onRegisterUserActivity,
                onLinkTapped = { link ->
                    link.targetOffset?.let(onOpenInternalLink)
                        ?: link.externalUri?.let(onOpenExternalLink)
                },
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
                advancedVoices = state.advancedVoices,
                advancedVoiceAvailable = state.advancedVoiceAvailable,
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
            topPadding = state.topPaddingDp,
            bottomPadding = state.bottomPaddingDp,
            readingMode = state.readingMode,
            fontFamily = state.fontFamily,
            textAlignment = state.textAlignment,
            paragraphSpacing = state.paragraphSpacingSp,
            firstLineIndent = state.firstLineIndentSp,
            onDismiss = { showAppearance = false },
            onSave = { font, line, margin, top, bottom ->
                onSetReadingAppearance(font, line, margin, top, bottom)
                showAppearance = false
            },
            onSetReadingMode = onSetReadingMode,
            onSaveTypography = onSetTypography,
        )
    }

    if (showColors) ReaderColorsDialog(state, { showColors = false }) { background, text, link, heading, highlight ->
        onSetReaderColors(true, background, text, link, heading, highlight)
        showColors = false
    }
    if (showDisplay) DisplayDialog(state, { showDisplay = false }) { orientation, keepOn ->
        onSetDisplayBehavior(orientation, keepOn)
        showDisplay = false
    }
    if (showImages) ImageAppearanceDialog(state, { showImages = false }) { size, alignment ->
        onSetImageAppearance(size, alignment)
        showImages = false
    }
    if (showSleepTimer) SleepTimerDialog(state, { showSleepTimer = false }) { minutes, seconds ->
        onSetSleepTimerSettings(minutes, seconds)
        showSleepTimer = false
    }

    LaunchedEffect(state.sleepTimerWarning) {
        if (state.sleepTimerWarning) {
            Toast.makeText(context, "Tap the screen within ${state.sleepGraceSeconds} seconds or reading will stop.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun ReaderDrawer(
    state: ReaderUiState,
    onEnableSpeech: () -> Unit,
    onDisableSpeech: () -> Unit,
    onImportAnalysis: () -> Unit,
    onShowCharacters: () -> Unit,
    onShowSettings: () -> Unit,
    onOpenBook: () -> Unit,
    onGoToChapter: (Int) -> Unit,
) {
    var chaptersExpanded by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            ListItem(
                headlineContent = { Text("Voice mode") },
                supportingContent = {
                    Text(if (state.speechControlsEnabled) "Media controls and voice playback enabled" else "Voice playback disabled")
                },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = state.speechControlsEnabled,
                        onCheckedChange = { enabled -> if (enabled) onEnableSpeech() else onDisableSpeech() },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Characters and voices") },
                supportingContent = {
                    Text(
                        if (state.cachedAttributionProvider == null) {
                            "Narrator only until a character map is imported"
                        } else {
                            "${visibleVoiceProfiles(state).size} available profiles"
                        },
                    )
                },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onShowCharacters),
            )
            ListItem(
                headlineContent = { Text("Import character map") },
                supportingContent = { Text("Load speaker names, colours, and voice assignments from JSON") },
                leadingContent = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onImportAnalysis),
            )
            ListItem(
                headlineContent = { Text("Settings") },
                supportingContent = { Text("Appearance, speech, voices, and speaker map") },
                modifier = Modifier.clickable(onClick = onShowSettings),
            )
            ListItem(
                headlineContent = { Text("Open") },
                supportingContent = { Text("Open an EPUB or FB2 file") },
                leadingContent = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenBook),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onShowAppearance: () -> Unit,
    onShowVoiceModels: () -> Unit,
    onShowAdvancedVoiceModels: () -> Unit,
    onShowProceduralVoices: () -> Unit,
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
    onShowSpeechPostProcessing: () -> Unit,
    onShowColors: () -> Unit,
    onShowDisplay: () -> Unit,
    onShowImages: () -> Unit,
    onShowSleepTimer: () -> Unit,
) {
    var showEngineDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Back to reader")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Text(
            "Reading",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 22.dp),
        )
        ListItem(
            headlineContent = { Text("Text appearance") },
            supportingContent = { Text("Font, line spacing, margins, and page or scroll mode") },
            modifier = Modifier.clickable(onClick = onShowAppearance),
        )
        ListItem(headlineContent = { Text("Reader colors") }, supportingContent = { Text("Background, text, links, headings, and highlights") }, modifier = Modifier.clickable(onClick = onShowColors))
        ListItem(headlineContent = { Text("Display") }, supportingContent = { Text("Orientation lock and keep screen on") }, modifier = Modifier.clickable(onClick = onShowDisplay))
        ListItem(headlineContent = { Text("Images") }, supportingContent = { Text("Image size and alignment") }, modifier = Modifier.clickable(onClick = onShowImages))
        ListItem(headlineContent = { Text("Sleep timer") }, supportingContent = { Text("Inactivity warning and automatic stop") }, modifier = Modifier.clickable(onClick = onShowSleepTimer))
        Text(
            "Speech",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 22.dp),
        )
        ListItem(
            headlineContent = {
                Text(
                    when (state.speechEngineMode) {
                        SpeechEngineMode.PROCEDURAL -> "Voice engine: Procedural"
                        SpeechEngineMode.LOCAL_GENERATIVE -> "Voice engine: AI Simple"
                        SpeechEngineMode.LOCAL_ADVANCED -> "Voice engine: AI Advanced"
                    },
                )
            },
            supportingContent = { Text("Choose the engine used for character speech") },
            modifier = Modifier.clickable {
                showEngineDialog = true
            },
        )
        ListItem(
            headlineContent = { Text("Voice post-processing") },
            supportingContent = {
                Text(
                    if (state.speechPostProcessing.enabled) {
                        "Enabled for procedural, AI Simple, and AI Advanced"
                    } else {
                        "Configure emotion speed, pitch, loudness, tone, and pauses"
                    },
                )
            },
            modifier = Modifier.clickable(onClick = onShowSpeechPostProcessing),
        )
        when (state.speechEngineMode) {
            SpeechEngineMode.PROCEDURAL -> ListItem(
                headlineContent = { Text("Procedural voices") },
                supportingContent = { Text("Preview installed offline English and Ukrainian system voices") },
                modifier = Modifier.clickable(onClick = onShowProceduralVoices),
            )
            SpeechEngineMode.LOCAL_GENERATIVE -> ListItem(
                headlineContent = { Text("AI Simple models") },
                supportingContent = { Text("Download, preview, or remove fast Piper voice packs") },
                modifier = Modifier.clickable(onClick = onShowVoiceModels),
            )
            SpeechEngineMode.LOCAL_ADVANCED -> ListItem(
                headlineContent = { Text("AI Advanced model") },
                supportingContent = { Text("Configure KittenTTS Micro, download it, and preview voices") },
                modifier = Modifier.clickable(onClick = onShowAdvancedVoiceModels),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
    VoiceEngineDialogs(
        showEngineDialog = showEngineDialog,
        speechEngineMode = state.speechEngineMode,
        localGenerativeVoiceAvailable = state.localGenerativeVoiceAvailable,
        advancedVoiceAvailable = state.advancedVoiceAvailable,
        onShowEngineDialog = { showEngineDialog = it },
        onSetSpeechEngineMode = onSetSpeechEngineMode,
    )
}

@Composable
private fun SpeechPostProcessingScreen(
    settings: SpeechPostProcessingSettings,
    onBack: () -> Unit,
    onSave: (SpeechPostProcessingSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var expanded by remember { mutableStateOf<EmotionType?>(null) }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Back to settings")
            }
            Text("Voice post-processing", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Post processing") },
                    supportingContent = {
                        Text("Applies emotion profiles to every voice engine when the character map provides an emotion.")
                    },
                    trailingContent = {
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { enabled ->
                                draft = draft.copy(enabled = enabled)
                                onSave(draft)
                            },
                        )
                    },
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    TextButton(
                        onClick = {
                            draft = draft.copy(profiles = defaultEmotionProcessingProfiles())
                            onSave(draft)
                        },
                    ) { Text("Reset defaults") }
                }
                Text(
                    "Procedural voices apply speed, pitch, quieter volume, and pauses. AI voices also apply warm/bright tone, loudness boost, and duration-preserving pitch processing to generated PCM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            EmotionType.entries.forEach { type ->
                item(key = type.name) {
                    val profile = draft.profileFor(type)
                    ListItem(
                        headlineContent = { Text(emotionDisplayName(type)) },
                        supportingContent = { Text(emotionProfileSummary(profile)) },
                        trailingContent = {
                            Icon(
                                if (expanded == type) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            expanded = if (expanded == type) null else type
                        },
                    )
                    AnimatedVisibility(visible = expanded == type) {
                        EmotionProfileControls(
                            profile = profile,
                            enabled = draft.enabled,
                            onChange = { changed ->
                                draft = draft.copy(profiles = draft.profiles + (type to changed.normalized()))
                            },
                            onCommit = { onSave(draft) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun EmotionProfileControls(
    profile: EmotionProcessingProfile,
    enabled: Boolean,
    onChange: (EmotionProcessingProfile) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        EmotionSlider("Speed", profile.speed, "${formatDecimal(profile.speed)}x", 0.7f..1.3f, enabled, { onChange(profile.copy(speed = it)) }, onCommit)
        EmotionSlider("Pitch", profile.pitch, "${formatDecimal(profile.pitch)}x", 0.85f..1.15f, enabled, { onChange(profile.copy(pitch = it)) }, onCommit)
        EmotionSlider("Loudness", profile.loudness, "${formatDecimal(profile.loudness)}x", 0.6f..1.4f, enabled, { onChange(profile.copy(loudness = it)) }, onCommit)
        EmotionSlider(
            "Tone",
            profile.tone,
            when {
                profile.tone < -0.05f -> "Warm ${formatDecimal(-profile.tone)}"
                profile.tone > 0.05f -> "Bright ${formatDecimal(profile.tone)}"
                else -> "Neutral"
            },
            -1f..1f,
            enabled,
            { onChange(profile.copy(tone = it)) },
            onCommit,
        )
        EmotionSlider(
            "Pause after",
            profile.pauseAfterMillis.toFloat(),
            "${profile.pauseAfterMillis} ms",
            0f..600f,
            enabled,
            { onChange(profile.copy(pauseAfterMillis = (it / 25f).roundToInt() * 25L)) },
            onCommit,
        )
    }
}

@Composable
private fun EmotionSlider(
    label: String,
    value: Float,
    formattedValue: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formattedValue, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        enabled = enabled,
    )
}

private fun emotionDisplayName(type: EmotionType): String =
    type.name.lowercase().replaceFirstChar { it.titlecase() }

private fun emotionProfileSummary(profile: EmotionProcessingProfile): String {
    val tone = when {
        profile.tone < -0.05f -> "warm"
        profile.tone > 0.05f -> "bright"
        else -> "neutral tone"
    }
    return "${formatDecimal(profile.speed)}x speed, ${formatDecimal(profile.pitch)}x pitch, $tone, ${profile.pauseAfterMillis} ms pause"
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
    val speakers = (state.characters + NarratorProfile)
        .distinctBy(CharacterProfile::id)
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
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleSleepTimer: () -> Unit,
    onRegisterUserActivity: () -> Unit,
    onLinkTapped: (BookLink) -> Unit,
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
            BookImagesForRange(book, page, imageSize, imageAlignment)
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

@Composable
private fun VerticalScrollableBook(
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
            BookImagesForRange(book, chunk, imageSize, imageAlignment)
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
private fun BookImagesForRange(
    book: BookDocument,
    range: PageLayout,
    imageSize: ReaderImageSize,
    imageAlignment: ReaderImageAlignment,
) {
    val images = remember(book.images, range) {
        book.images.filter { it.offset in range.start until range.end }.take(MAX_IMAGES_PER_PAGE)
    }
    images.forEach { image ->
        val bitmap by produceState<android.graphics.Bitmap?>(null, book.localPath, image.archivePath) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ZipFile(book.localPath).use { archive ->
                        val entry = archive.getEntry(image.archivePath) ?: return@use null
                        archive.getInputStream(entry).use(BitmapFactory::decodeStream)
                    }
                }.getOrNull()
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

private fun highlightedPage(
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
private const val MAX_IMAGES_PER_PAGE = 3
private const val CHAPTER_DIVIDER = '\u2766'
private val BOOK_QUOTE_MARKS = setOf('"', '\u201c', '\u201d', '\u201e', '\u00ab', '\u00bb')
/** EPUBs commonly use a non-breaking space in an otherwise empty paragraph. */
private fun Char.isVisualSeparatorCharacter(): Boolean =
    this == CHAPTER_DIVIDER || this == '\u00a0' || isWhitespace()

private data class RenderedPageText(
    val value: String,
    private val visualOffsetForBook: IntArray,
    private val bookOffsetForVisual: IntArray,
) {
    fun visualOffset(bookOffsetInPage: Int): Int =
        visualOffsetForBook[bookOffsetInPage.coerceIn(0, visualOffsetForBook.lastIndex)]

    fun bookOffset(visualOffset: Int): Int =
        bookOffsetForVisual[visualOffset.coerceIn(0, bookOffsetForVisual.lastIndex)]
}

private data class RenderedTextLayout(
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
private fun renderPageText(text: String, page: PageLayout, paragraphSpacingSp: Float): RenderedPageText {
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

@Composable
private fun ReaderHeader(
    title: String,
    onOpenDrawer: () -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
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
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (darkTheme) "Use light theme" else "Use dark theme",
            )
        }
    }
}

@Composable
private fun AppearanceDialog(
    fontSize: Float,
    lineHeight: Float,
    margin: Float,
    topPadding: Float,
    bottomPadding: Float,
    readingMode: ReadingMode,
    fontFamily: ReaderFontFamily,
    textAlignment: ReaderTextAlignment,
    paragraphSpacing: Float,
    firstLineIndent: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float, Float, Float) -> Unit,
    onSetReadingMode: (ReadingMode) -> Unit,
    onSaveTypography: (ReaderFontFamily, ReaderTextAlignment, Float, Float) -> Unit,
) {
    var font by remember(fontSize) { mutableStateOf(fontSize) }
    var line by remember(lineHeight) { mutableStateOf(lineHeight) }
    var pageMargin by remember(margin) { mutableStateOf(margin) }
    var top by remember(topPadding) { mutableStateOf(topPadding) }
    var bottom by remember(bottomPadding) { mutableStateOf(bottomPadding) }
    var mode by remember(readingMode) { mutableStateOf(readingMode) }
    var family by remember(fontFamily) { mutableStateOf(fontFamily) }
    var alignment by remember(textAlignment) { mutableStateOf(textAlignment) }
    var paragraphSpace by remember(paragraphSpacing) { mutableStateOf(paragraphSpacing) }
    var indent by remember(firstLineIndent) { mutableStateOf(firstLineIndent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading appearance") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VoiceSliderLabel("Font size", "${font.roundToInt()} sp")
                Slider(value = font, onValueChange = { font = it }, valueRange = 14f..32f, steps = 17)
                VoiceSliderLabel("Line spacing", "${line.roundToInt()} sp")
                Slider(value = line, onValueChange = { line = it }, valueRange = 18f..48f, steps = 29)
                VoiceSliderLabel("Page margins", "${pageMargin.roundToInt()} dp")
                Slider(value = pageMargin, onValueChange = { pageMargin = it }, valueRange = 12f..56f, steps = 10)
                VoiceSliderLabel("Top padding", "${top.roundToInt()} dp")
                Slider(value = top, onValueChange = { top = it }, valueRange = 0f..80f, steps = 15)
                VoiceSliderLabel("Bottom padding", "${bottom.roundToInt()} dp")
                Slider(value = bottom, onValueChange = { bottom = it }, valueRange = 0f..80f, steps = 15)
                Text("Font", style = MaterialTheme.typography.labelLarge)
                ReaderFontFamily.entries.forEach { option ->
                    SettingRadioRow(option.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), family == option) { family = option }
                }
                Text("Text alignment", style = MaterialTheme.typography.labelLarge)
                ReaderTextAlignment.entries.forEach { option ->
                    SettingRadioRow(option.name.lowercase().replaceFirstChar(Char::uppercase), alignment == option) { alignment = option }
                }
                VoiceSliderLabel("Paragraph spacing", "${paragraphSpace.roundToInt()} sp")
                Slider(value = paragraphSpace, onValueChange = { paragraphSpace = it }, valueRange = 0f..32f, steps = 15)
                VoiceSliderLabel("First-line indentation", "${indent.roundToInt()} sp")
                Slider(value = indent, onValueChange = { indent = it }, valueRange = 0f..64f, steps = 15)
                Text("Reading mode", style = MaterialTheme.typography.labelLarge)
                ReadingMode.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = option },
                    ) {
                        RadioButton(
                            selected = mode == option,
                            onClick = { mode = option },
                            colors = themedRadioButtonColors(),
                        )
                        Text(if (option == ReadingMode.PAGINATED) "Horizontal pages" else "Vertical scroll")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(font, line, pageMargin, top, bottom)
                    onSetReadingMode(mode)
                    onSaveTypography(family, alignment, paragraphSpace, indent)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = themedRadioButtonColors())
        Text(label)
    }
}

@Composable
private fun ReaderColorsDialog(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int, Int) -> Unit,
) {
    var background by remember(state.readerBackgroundColor) { mutableStateOf(state.readerBackgroundColor) }
    var text by remember(state.readerTextColor) { mutableStateOf(state.readerTextColor) }
    var link by remember(state.readerLinkColor) { mutableStateOf(state.readerLinkColor) }
    var heading by remember(state.readerHeadingColor) { mutableStateOf(state.readerHeadingColor) }
    var highlight by remember(state.readerHighlightColor) { mutableStateOf(state.readerHighlightColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reader colors") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ColorPickerRow("Background", background) { background = it }
                ColorPickerRow("Text", text) { text = it }
                ColorPickerRow("Links", link) { link = it }
                ColorPickerRow("Headings", heading) { heading = it }
                ColorPickerRow("Playback highlight", highlight) { highlight = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(background, text, link, heading, highlight) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorPickerRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    var hue by remember(selected) { mutableStateOf(hueFromArgb(selected)) }
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(42.dp).padding(vertical = 8.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.horizontalGradient(HUE_GRADIENT),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
        Slider(
            value = hue,
            onValueChange = { value ->
                hue = value
                onSelect(hueToArgb(value, selected ushr 24))
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color(selected),
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (ReaderOrientation, Boolean) -> Unit) {
    var orientation by remember(state.orientation) { mutableStateOf(state.orientation) }
    var keepOn by remember(state.keepScreenOn) { mutableStateOf(state.keepScreenOn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display") },
        text = { Column {
            Text("Screen orientation", style = MaterialTheme.typography.labelLarge)
            ReaderOrientation.entries.forEach { SettingRadioRow(it.name.lowercase().replaceFirstChar(Char::uppercase), orientation == it) { orientation = it } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on while reading", modifier = Modifier.weight(1f))
                Switch(checked = keepOn, onCheckedChange = { keepOn = it })
            }
        } },
        confirmButton = { TextButton(onClick = { onSave(orientation, keepOn) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImageAppearanceDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (ReaderImageSize, ReaderImageAlignment) -> Unit) {
    var size by remember(state.imageSize) { mutableStateOf(state.imageSize) }
    var alignment by remember(state.imageAlignment) { mutableStateOf(state.imageAlignment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Images") },
        text = { Column {
            Text("Image size", style = MaterialTheme.typography.labelLarge)
            ReaderImageSize.entries.forEach { SettingRadioRow(it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), size == it) { size = it } }
            Text("Alignment", style = MaterialTheme.typography.labelLarge)
            ReaderImageAlignment.entries.forEach { SettingRadioRow(it.name.lowercase().replaceFirstChar(Char::uppercase), alignment == it) { alignment = it } }
        } },
        confirmButton = { TextButton(onClick = { onSave(size, alignment) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SleepTimerDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var minutes by remember(state.sleepCheckMinutes) { mutableStateOf(state.sleepCheckMinutes.toFloat()) }
    var seconds by remember(state.sleepGraceSeconds) { mutableStateOf(state.sleepGraceSeconds.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = { Column {
            Text("When enabled from the media controls, VoxReader asks for activity before stopping playback.")
            VoiceSliderLabel("Inactivity interval", "${minutes.roundToInt()} min")
            Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..30f, steps = 28)
            VoiceSliderLabel("Warning time", "${seconds.roundToInt()} sec")
            Slider(value = seconds, onValueChange = { seconds = it }, valueRange = 10f..120f, steps = 10)
        } },
        confirmButton = { TextButton(onClick = { onSave(minutes.roundToInt(), seconds.roundToInt()) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable private fun readerBackground(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerBackgroundColor) else MaterialTheme.colorScheme.background
@Composable private fun readerTextColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerTextColor) else MaterialTheme.colorScheme.onBackground
@Composable private fun readerLinkColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerLinkColor) else MaterialTheme.colorScheme.primary
@Composable private fun readerHeadingColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerHeadingColor) else MaterialTheme.colorScheme.tertiary
@Composable private fun readerActiveHighlightColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerHighlightColor) else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)

private fun ReaderFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReaderFontFamily.SERIF -> FontFamily.Serif
    ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
    ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
}
private fun ReaderTextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    ReaderTextAlignment.LEFT -> TextAlign.Left
    ReaderTextAlignment.CENTER -> TextAlign.Center
    ReaderTextAlignment.JUSTIFIED -> TextAlign.Justify
}

private val HUE_GRADIENT = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

private fun hueFromArgb(color: Int): Float = FloatArray(3).also {
    android.graphics.Color.colorToHSV(color, it)
}[0]

private fun hueToArgb(hue: Float, alpha: Int): Int =
    android.graphics.Color.HSVToColor(alpha.coerceIn(0, 255), floatArrayOf(hue, 0.72f, 0.95f))

@Composable
private fun SpeechControls(
    playbackState: PlaybackState,
    playbackSpeed: Float,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    sleepTimerEnabled: Boolean,
    onToggleSleepTimer: () -> Unit,
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
            IconButton(onClick = onToggleSleepTimer) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = if (sleepTimerEnabled) "Disable sleep timer" else "Enable sleep timer",
                    tint = if (sleepTimerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
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
    speechEngineMode: SpeechEngineMode,
    localGenerativeVoiceAvailable: Boolean,
    advancedVoiceAvailable: Boolean,
    onShowEngineDialog: (Boolean) -> Unit,
    onSetSpeechEngineMode: (SpeechEngineMode) -> Unit,
) {
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
                        title = "AI Simple",
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
                    EngineChoice(
                        title = "AI Advanced",
                        description = if (advancedVoiceAvailable) {
                            "Uses the faster KittenTTS Micro model with eight English voices."
                        } else {
                            "Select it, then download KittenTTS Micro from AI Advanced model settings."
                        },
                        selected = speechEngineMode == SpeechEngineMode.LOCAL_ADVANCED,
                    ) {
                        onSetSpeechEngineMode(SpeechEngineMode.LOCAL_ADVANCED)
                        onShowEngineDialog(false)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onShowEngineDialog(false) }) { Text("Done") } },
        )
    }

}

@Composable
private fun VoiceModelsScreen(
    packs: List<PiperVoicePack>,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var voicePendingRemoval by remember { mutableStateOf<PiperVoicePack?>(null) }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Back to settings")
            }
            Text("AI voice models", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Text(
            "Download, preview, or remove offline voice models. Installed models can be assigned to characters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            VoiceModelLanguageSection(
                title = "English",
                packs = packs.filter { it.language == VoicePackLanguage.ENGLISH },
                onDownload = onDownload,
                onRemove = onRemove,
                onPreview = onPreview,
                onPendingRemoval = { voicePendingRemoval = it },
            )
            VoiceModelLanguageSection(
                title = "Ukrainian",
                packs = packs.filter { it.language == VoicePackLanguage.UKRAINIAN },
                onDownload = onDownload,
                onRemove = onRemove,
                onPreview = onPreview,
                onPendingRemoval = { voicePendingRemoval = it },
            )
        }
    }
    voicePendingRemoval?.let { pack ->
        AlertDialog(
            onDismissRequest = { voicePendingRemoval = null },
            title = { Text("Remove ${pack.displayName}?") },
            text = { Text("This deletes the downloaded voice model from this device. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(pack.id)
                    voicePendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { voicePendingRemoval = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AdvancedVoiceModelsScreen(
    model: AdvancedVoiceModel?,
    voices: List<AdvancedVoice>,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onPreview: (String) -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf(false) }
    var voicesExpanded by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Back to settings")
            }
            Text("AI Advanced model", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            model?.let { current ->
                ListItem(
                    headlineContent = { Text(current.displayName) },
                    supportingContent = {
                        Column {
                            Text(current.description)
                            Text(
                                when (current.downloadState) {
                                    VoicePackDownloadState.INSTALLED ->
                                        "Installed - ${formatVoicePackSize(current.installedSizeBytes)}"
                                    VoicePackDownloadState.DOWNLOADING ->
                                        "Downloading ${((current.downloadProgress ?: 0f) * 100).roundToInt()}% of about ${formatVoicePackSize(current.downloadSizeBytes)}"
                                    VoicePackDownloadState.FAILED -> current.error ?: "Download failed"
                                    VoicePackDownloadState.NOT_INSTALLED ->
                                        "Not installed - about ${formatVoicePackSize(current.downloadSizeBytes)} download"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (current.downloadState == VoicePackDownloadState.DOWNLOADING) {
                                LinearProgressIndicator(
                                    progress = { current.downloadProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                    },
                    trailingContent = {
                        when {
                            current.installed -> TextButton(onClick = { confirmRemoval = true }) { Text("Remove") }
                            current.downloadState == VoicePackDownloadState.DOWNLOADING -> Unit
                            else -> TextButton(onClick = onDownload) { Text("Download") }
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text("English voices") },
                    supportingContent = { Text("${voices.size} English voices included in one model") },
                    trailingContent = {
                        Icon(
                            if (voicesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { voicesExpanded = !voicesExpanded },
                )
                AnimatedVisibility(voicesExpanded) {
                    Column {
                        voices.forEach { voice ->
                            ListItem(
                                headlineContent = { Text(voice.displayName) },
                                supportingContent = {
                                    Text(voice.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase))
                                },
                                trailingContent = {
                                    TextButton(
                                        enabled = current.installed,
                                        onClick = { onPreview(voice.id) },
                                    ) { Text("Play") }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove advanced model?") },
            text = { Text("This deletes KittenTTS Micro and all of its voices from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    confirmRemoval = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProceduralVoicesScreen(
    voices: List<ProceduralVoice>,
    onBack: () -> Unit,
    onPreview: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Back to settings")
            }
            Text("Procedural voices", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Text(
            "Offline voices installed in Android's text-to-speech engine. Install additional voices from Android system settings if a language is empty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ProceduralVoiceLanguageSection(
                title = "English",
                voices = voices.filter { it.language == ProceduralVoiceLanguage.ENGLISH },
                onPreview = onPreview,
            )
            ProceduralVoiceLanguageSection(
                title = "Ukrainian",
                voices = voices.filter { it.language == ProceduralVoiceLanguage.UKRAINIAN },
                onPreview = onPreview,
            )
        }
    }
}

@Composable
private fun ProceduralVoiceLanguageSection(
    title: String,
    voices: List<ProceduralVoice>,
    onPreview: (String) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(title == "English") }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text("${voices.size} installed offline voices") },
        trailingContent = {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        },
        modifier = Modifier.clickable { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column {
            if (voices.isEmpty()) {
                Text(
                    "No offline $title voice is installed on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                voices.forEach { voice ->
                    ListItem(
                        headlineContent = { Text(voice.displayName) },
                        supportingContent = { Text("Android offline text-to-speech voice") },
                        trailingContent = { TextButton(onClick = { onPreview(voice.id) }) { Text("Play") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceModelLanguageSection(
    title: String,
    packs: List<PiperVoicePack>,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
    onPendingRemoval: (PiperVoicePack) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(title == "English") }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text("${packs.size} available models") },
        trailingContent = {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        },
        modifier = Modifier.clickable { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column {
            packs.forEach { pack ->
                VoiceModelRow(
                    pack = pack,
                    onDownload = onDownload,
                    onRemove = onRemove,
                    onPreview = onPreview,
                    onPendingRemoval = onPendingRemoval,
                )
            }
        }
    }
}

@Composable
private fun VoiceModelRow(
    pack: PiperVoicePack,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
    onPendingRemoval: (PiperVoicePack) -> Unit,
) {
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
                    TextButton(onClick = { onPreview(pack.id) }) { Text("Play") }
                    TextButton(onClick = { onPendingRemoval(pack) }) { Text("Remove") }
                } else {
                    TextButton(
                        enabled = pack.downloadState != VoicePackDownloadState.DOWNLOADING,
                        onClick = { onDownload(pack.id) },
                    ) { Text("Download") }
                }
            }
        },
    )
}

private fun formatVoicePackSize(sizeBytes: Long?): String =
    sizeBytes?.let { String.format(Locale.ROOT, "%.1f MB", it / (1024f * 1024f)) }
        ?: "Size shown after download"

@Composable
private fun CharacterVoiceDialog(
    profile: CharacterProfile,
    speechEngineMode: SpeechEngineMode,
    localVoicePacks: List<PiperVoicePack>,
    advancedVoices: List<AdvancedVoice>,
    advancedVoiceAvailable: Boolean,
    onDismiss: () -> Unit,
    onPreview: (CharacterProfile) -> Unit,
    onSave: (CharacterProfile) -> Unit,
) {
    val installedAiVoices = localVoicePacks.filter { it.installed }
    val initialAiVoice = installedAiVoices.firstOrNull { it.id == profile.localVoicePackId }
        ?: installedAiVoices
        .filter { it.voiceGroup == profile.voiceGroup }
        .getOrNull(Math.floorMod(profile.voiceSlot, installedAiVoices.count { it.voiceGroup == profile.voiceGroup }.coerceAtLeast(1)))
        ?: installedAiVoices.firstOrNull()
    var selectedAiVoiceId by remember(profile.id, localVoicePacks) {
        mutableStateOf(initialAiVoice?.id)
    }
    val initialAdvancedVoice = advancedVoices.firstOrNull { it.id == profile.advancedVoiceId }
        ?: advancedVoices.firstOrNull { it.voiceGroup == profile.voiceGroup }
        ?: advancedVoices.firstOrNull()
    var selectedAdvancedVoiceId by remember(profile.id, advancedVoices) {
        mutableStateOf(initialAdvancedVoice?.id)
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
        val selectedAdvancedVoice = advancedVoices.firstOrNull { it.id == selectedAdvancedVoiceId }
        val aiVoiceSlot = selectedAiVoice?.let { selected ->
            installedAiVoices.filter { it.voiceGroup == selected.voiceGroup }.indexOf(selected)
        } ?: voiceSlot.roundToInt()
        return when (speechEngineMode) {
            SpeechEngineMode.PROCEDURAL -> profile.copy(
                voiceGroup = voiceGroup,
                voiceSlot = voiceSlot.roundToInt(),
                pitch = pitch,
                speechRate = speechRate,
            )
            SpeechEngineMode.LOCAL_GENERATIVE -> profile.copy(
                voiceGroup = selectedAiVoice?.voiceGroup ?: voiceGroup,
                voiceSlot = aiVoiceSlot,
                localVoicePackId = selectedAiVoice?.id,
                pitch = pitch,
                speechRate = speechRate,
            )
            SpeechEngineMode.LOCAL_ADVANCED -> profile.copy(
                voiceGroup = selectedAdvancedVoice?.voiceGroup ?: voiceGroup,
                voiceSlot = selectedAdvancedVoice?.speakerId ?: voiceSlot.roundToInt(),
                advancedVoiceId = selectedAdvancedVoice?.id,
                pitch = pitch,
                speechRate = speechRate,
            )
        }
    }

    fun previewProfileFor(pack: PiperVoicePack): CharacterProfile =
        profile.copy(
            voiceGroup = pack.voiceGroup,
            voiceSlot = installedAiVoices
                .filter { it.voiceGroup == pack.voiceGroup }
                .indexOf(pack),
            localVoicePackId = pack.id,
            pitch = pitch,
            speechRate = speechRate,
        )

    fun previewProfileFor(voice: AdvancedVoice): CharacterProfile =
        profile.copy(
            voiceGroup = voice.voiceGroup,
            voiceSlot = voice.speakerId,
            advancedVoiceId = voice.id,
            pitch = pitch,
            speechRate = speechRate,
        )

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
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RadioButton(
                                    selected = selectedAiVoiceId == pack.id,
                                    onClick = { selectedAiVoiceId = pack.id },
                                    colors = themedRadioButtonColors(),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.displayName)
                                    Text(
                                        pack.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        pack.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onPreview(previewProfileFor(pack)) }) {
                                    Text("Play")
                                }
                            }
                        }
                    }
                    Text(
                        "AI voice pitch is model-defined. Speaking rate is applied during generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (speechEngineMode == SpeechEngineMode.LOCAL_ADVANCED) {
                    Text("KittenTTS voice", style = MaterialTheme.typography.labelLarge)
                    if (!advancedVoiceAvailable) {
                        Text(
                            "The AI Advanced model is not installed. Download it from AI Advanced model settings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        advancedVoices.forEach { voice ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RadioButton(
                                    selected = selectedAdvancedVoiceId == voice.id,
                                    onClick = { selectedAdvancedVoiceId = voice.id },
                                    colors = themedRadioButtonColors(),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(voice.displayName)
                                    Text(
                                        voice.voiceGroup.name.lowercase().replaceFirstChar(Char::uppercase),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onPreview(previewProfileFor(voice)) }) {
                                    Text("Play")
                                }
                            }
                        }
                    }
                    Text(
                        "Voice character and pitch are model-defined. Speaking rate is applied during generation.",
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
                                colors = themedRadioButtonColors(),
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
                if (speechEngineMode == SpeechEngineMode.PROCEDURAL) {
                    TextButton(onClick = { onPreview(draftProfile()) }) {
                        Text("Preview")
                    }
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

@Composable
private fun themedRadioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = MaterialTheme.colorScheme.primary,
    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

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
                text = "Open a book to begin",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Choose an .epub or .fb2 file in Android's Files app and open it with VoxReader. " +
                    "The book is copied into the private offline library.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
