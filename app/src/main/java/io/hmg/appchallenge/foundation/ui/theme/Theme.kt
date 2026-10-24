package io.hmg.appchallenge.foundation.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun FoundationTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = LocalContext.current
    val themed = remember(base, dark) {
        val config = Configuration(base.resources.configuration)
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        base.createConfigurationContext(config)
    }
    val colors = iviColors(themed)
    ProvideIviColors(colors) {
        MaterialTheme(
            colorScheme = iviMaterialColors(colors, dark),
            typography = iviTypography(),
            content = content,
        )
    }
}
