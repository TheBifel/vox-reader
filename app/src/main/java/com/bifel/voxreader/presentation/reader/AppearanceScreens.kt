package com.bifel.voxreader.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import kotlin.math.roundToInt

@Composable
internal fun AppearanceDialog(
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
internal fun ReaderColorsDialog(
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
internal fun DisplayDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (ReaderOrientation, Boolean) -> Unit) {
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
internal fun ImageAppearanceDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (ReaderImageSize, ReaderImageAlignment) -> Unit) {
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
internal fun SleepTimerDialog(state: ReaderUiState, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
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

