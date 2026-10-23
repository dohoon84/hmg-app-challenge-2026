package io.hmg.appchallenge.foundation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun FoundationTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) IviDarkColors else IviLightColors
    ProvideIviColors(colors) {
        MaterialTheme(
            colorScheme = iviMaterialColors(colors, dark),
            typography = iviTypography(),
            content = content,
        )
    }
}
