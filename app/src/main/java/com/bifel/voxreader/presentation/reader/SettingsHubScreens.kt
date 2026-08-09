package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.EmotionProcessingProfile
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.model.defaultEmotionProcessingProfiles
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
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
internal fun SpeechPostProcessingScreen(
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

