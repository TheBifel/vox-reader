package com.bifel.voxreader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.UnknownProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.SpeechText

@Composable
internal fun visibleVoiceProfiles(
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
internal fun CharacterListDialog(
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


