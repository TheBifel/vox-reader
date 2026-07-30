package com.bifel.voxreader.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFD8A35D),
    onPrimary = Color(0xFF24170A),
    secondary = Color(0xFFC8AB84),
    background = Color(0xFF090807),
    onBackground = Color(0xFFF4E9D6),
    surface = Color(0xFF15120F),
    onSurface = Color(0xFFF4E9D6),
    surfaceVariant = Color(0xFF28211A),
    onSurfaceVariant = Color(0xFFDCCDB7),
    error = Color(0xFFFFB4AB),
)

private val WarmLightColors = lightColorScheme(
    primary = Color(0xFF7A4D16),
    onPrimary = Color.White,
    secondary = Color(0xFF745A3A),
    background = Color(0xFFF7EEDC),
    onBackground = Color(0xFF17120D),
    surface = Color(0xFFFFF8EC),
    onSurface = Color(0xFF17120D),
    surfaceVariant = Color(0xFFEBDCC5),
    onSurfaceVariant = Color(0xFF4E4336),
    error = Color(0xFFBA1A1A),
)

val ReaderHighlight = Color(0x66D79A47)

@Composable
fun VoxReaderTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WarmDarkColors else WarmLightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

