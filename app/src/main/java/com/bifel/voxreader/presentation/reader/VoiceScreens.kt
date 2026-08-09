package com.bifel.voxreader.presentation.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.VoiceInstallState
import com.bifel.voxreader.domain.speech.VoiceLanguage
import com.bifel.voxreader.domain.speech.VoiceModel
import com.bifel.voxreader.domain.speech.VoiceOption
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun VoiceModelsScreen(
    packs: List<VoiceModel>,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var voicePendingRemoval by remember { mutableStateOf<VoiceModel?>(null) }
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
                packs = packs.filter { it.language == VoiceLanguage.ENGLISH },
                onDownload = onDownload,
                onRemove = onRemove,
                onPreview = onPreview,
                onPendingRemoval = { voicePendingRemoval = it },
            )
            VoiceModelLanguageSection(
                title = "Ukrainian",
                packs = packs.filter { it.language == VoiceLanguage.UKRAINIAN },
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
internal fun AdvancedVoiceModelsScreen(
    model: VoiceModel?,
    voices: List<VoiceOption>,
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
                                    VoiceInstallState.INSTALLED ->
                                        "Installed - ${formatVoicePackSize(current.installedSizeBytes)}"
                                    VoiceInstallState.DOWNLOADING ->
                                        "Downloading ${((current.downloadProgress ?: 0f) * 100).roundToInt()}% of about ${formatVoicePackSize(current.downloadSizeBytes)}"
                                    VoiceInstallState.FAILED -> current.error ?: "Download failed"
                                    VoiceInstallState.NOT_INSTALLED ->
                                        "Not installed - about ${formatVoicePackSize(current.downloadSizeBytes)} download"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (current.downloadState == VoiceInstallState.DOWNLOADING) {
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
                            current.downloadState == VoiceInstallState.DOWNLOADING -> Unit
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
internal fun ProceduralVoicesScreen(
    voices: List<VoiceOption>,
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
                voices = voices.filter { it.language == VoiceLanguage.ENGLISH },
                onPreview = onPreview,
            )
            ProceduralVoiceLanguageSection(
                title = "Ukrainian",
                voices = voices.filter { it.language == VoiceLanguage.UKRAINIAN },
                onPreview = onPreview,
            )
        }
    }
}

@Composable
private fun ProceduralVoiceLanguageSection(
    title: String,
    voices: List<VoiceOption>,
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
    packs: List<VoiceModel>,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
    onPendingRemoval: (VoiceModel) -> Unit,
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
    pack: VoiceModel,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
    onPendingRemoval: (VoiceModel) -> Unit,
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
                            VoiceInstallState.INSTALLED -> "Installed"
                            VoiceInstallState.DOWNLOADING -> "Downloading..."
                            VoiceInstallState.FAILED -> pack.error ?: "Download failed"
                            VoiceInstallState.NOT_INSTALLED -> "Not downloaded"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pack.downloadState == VoiceInstallState.DOWNLOADING) {
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
                        enabled = pack.downloadState != VoiceInstallState.DOWNLOADING,
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
internal fun CharacterVoiceDialog(
    profile: CharacterProfile,
    speechEngineMode: SpeechEngineMode,
    localVoicePacks: List<VoiceModel>,
    advancedVoices: List<VoiceOption>,
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

    fun previewProfileFor(pack: VoiceModel): CharacterProfile =
        profile.copy(
            voiceGroup = pack.voiceGroup,
            voiceSlot = installedAiVoices
                .filter { it.voiceGroup == pack.voiceGroup }
                .indexOf(pack),
            localVoicePackId = pack.id,
            pitch = pitch,
            speechRate = speechRate,
        )

    fun previewProfileFor(voice: VoiceOption): CharacterProfile =
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
