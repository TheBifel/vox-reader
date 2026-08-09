package com.bifel.voxreader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@Composable
internal fun SpeechControls(
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
