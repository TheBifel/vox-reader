package com.bifel.voxreader.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bifel.voxreader.domain.model.BookDocument

@Composable
internal fun ReaderDrawer(
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


private fun chapterLabel(book: BookDocument, index: Int, start: Int): String {
    val preview = book.text.substring(start, minOf(book.text.length, start + 140))
        .lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return preview.takeIf { it.length in 2..80 } ?: "Chapter ${index + 1}"
}

@Composable
internal fun ReaderHeader(
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


@Composable internal fun readerBackground(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerBackgroundColor) else MaterialTheme.colorScheme.background
@Composable internal fun readerTextColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerTextColor) else MaterialTheme.colorScheme.onBackground
@Composable internal fun readerLinkColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerLinkColor) else MaterialTheme.colorScheme.primary
@Composable internal fun readerHeadingColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerHeadingColor) else MaterialTheme.colorScheme.tertiary
@Composable internal fun readerActiveHighlightColor(state: ReaderUiState): Color =
    if (state.customReaderColors) Color(state.readerHighlightColor) else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)


