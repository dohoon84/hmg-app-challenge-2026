package io.hmg.appchallenge.foundation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF3DDCFF),
    onPrimary = Color(0xFF003544),
    secondary = Color(0xFFFFB020),
    onSecondary = Color(0xFF2A1C00),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF1A1F27),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF262C36),
    onSurfaceVariant = Color(0xFFC5CDD6),
    error = Color(0xFFFF5A5A),
    onError = Color(0xFF2A0000),
)

@Composable
fun FoundationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
