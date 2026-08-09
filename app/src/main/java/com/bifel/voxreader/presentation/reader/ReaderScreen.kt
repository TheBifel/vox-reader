package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    actions: ReaderActions,
) {
    val onToggleChrome = actions.chrome.toggle
    val onHideChrome = actions.chrome.hide
    val onDismissError = actions.chrome.dismissError
    val onToggleTheme = actions.settings.toggleTheme
    val onEnableSpeech = actions.playback.enable
    val onDisableSpeech = actions.playback.disable
    val onPlayPause = actions.playback.playPause
    val onPreviousPhrase = actions.playback.previousPhrase
    val onNextPhrase = actions.playback.nextPhrase
    val onSetPlaybackSpeed = actions.playback.setSpeed
    val onToggleSleepTimer = actions.playback.toggleSleepTimer
    val onPageChanged = actions.reading.pageChanged
    val onSetReadingMode = actions.reading.setMode
    val onHistoryBack = actions.reading.historyBack
    val onHistoryForward = actions.reading.historyForward
    val onOpenInternalLink = actions.reading.openInternalLink
    val onRegisterUserActivity = actions.reading.registerUserActivity
    val onImportAnalysis = actions.analysis.import
    val onSpeakerTapped = actions.analysis.speakerAt
    val onSetSpeechEngineMode = actions.voices.setEngineMode
    val onSetSpeechPostProcessing = actions.voices.setPostProcessing
    val onDownloadLocalVoice = actions.voices.downloadLocal
    val onRemoveLocalVoice = actions.voices.removeLocal
    val onPreviewLocalVoice = actions.voices.previewLocal
    val onDownloadAdvancedModel = actions.voices.downloadAdvanced
    val onRemoveAdvancedModel = actions.voices.removeAdvanced
    val onPreviewAdvancedVoice = actions.voices.previewAdvanced
    val onLoadProceduralVoices = actions.voices.loadProcedural
    val onPreviewProceduralVoice = actions.voices.previewProcedural
    val onUpdateCharacterVoice = actions.voices.updateCharacter
    val onPreviewCharacterVoice = actions.voices.previewCharacter
    val onSetReadingAppearance = actions.settings.setReadingAppearance
    val onSetTypography = actions.settings.setTypography
    val onSetDisplayBehavior = actions.settings.setDisplayBehavior
    val onSetImageAppearance = actions.settings.setImageAppearance
    val onSetReaderColors = actions.settings.setColors
    val onSetSleepTimerSettings = actions.settings.setSleepTimer
    val onOpenExternalLink = actions.platform.openExternalLink
    val onOpenBook = actions.platform.openBook
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
                loadBookImage = actions.reading.loadImage,
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

internal fun ReaderFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReaderFontFamily.SERIF -> FontFamily.Serif
    ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
    ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
}
internal fun ReaderTextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    ReaderTextAlignment.LEFT -> TextAlign.Left
    ReaderTextAlignment.CENTER -> TextAlign.Center
    ReaderTextAlignment.JUSTIFIED -> TextAlign.Justify
}

internal val HUE_GRADIENT = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

internal fun hueFromArgb(color: Int): Float = FloatArray(3).also {
    android.graphics.Color.colorToHSV(color, it)
}[0]

internal fun hueToArgb(hue: Float, alpha: Int): Int =
    android.graphics.Color.HSVToColor(alpha.coerceIn(0, 255), floatArrayOf(hue, 0.72f, 0.95f))

@Composable
internal fun EngineChoice(
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
internal fun VoiceEngineDialogs(
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
internal fun VoiceSliderLabel(
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

internal fun formatMultiplier(value: Float): String =
    "${formatDecimal(value)}×"

@Composable
internal fun themedRadioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = MaterialTheme.colorScheme.primary,
    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

internal fun formatDecimal(value: Float): String =
    String.format(Locale.ROOT, "%.1f", value)

internal fun snapToTenth(value: Float): Float =
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
