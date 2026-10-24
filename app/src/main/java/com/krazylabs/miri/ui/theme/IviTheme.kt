@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.krazylabs.miri.ui.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.krazylabs.miri.R

/**
 * Asta Sans variable font, OFL 1.1, subset to Latin, punctuation, and Hangul syllables.
 * The notice is in assets/asta-sans/OFL.txt. Weights 400, 700, and 800 are the wght axis.
 * The three previous static files were really those weights (OS/2 400/700/800) and almost
 * the same size because each carried the full Hangul repertoire.
 * The file's default wght is 300, so an XML `@font/asta_sans` reference without
 * `fontVariationSettings` renders Light. Compose sets 400, 700, and 800 below.
 * A View that uses this file needs a font-family XML with `fontVariationSettings`.
 *
 * TODO(after 2026-10-27): do not export the 42 official icons yet. See Figma "Official Icons (Pleos)".
 */
val AstaSans = FontFamily(
    Font(
        R.font.asta_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.asta_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.asta_sans,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

/**
 * Named like the design-token code syntax: ivi_ + snake_case.
 * Hex values live only in `res/values/colors.xml` and `res/values-night/colors.xml`.
 * [iviColors] reads that resource set. Text secondary, tertiary, disabled, and neutral
 * are [textPrimary] at 84% / 64% / 32% / 64%. That alpha rule is not a second hex.
 * Official ivi_basic steps are not in a library this project can import.
 */
@Immutable
data class IviColors(
    val surfacePrimary: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val buttonBasic: Color,
    val buttonOnBasic: Color,
    val statusSuccess: Color,
    /** Text and icons. Light stays the darker caution. Dark is the driving orange. */
    val statusCaution: Color,
    val statusCautionContainer: Color,
    /**
     * Icon only. Never text. Light is the app-adjusted orange that stays at icon
     * contrast. Dark matches [statusCaution].
     */
    val statusCautionIcon: Color,
    /**
     * Same value as [informativeNegative]. Light is the app-adjusted red so text
     * and icons clear 4.5:1. Dark stays the driving-alert red.
     */
    val statusError: Color,
    /** ivi_driving_alert. Official Pleos driving alert, #FF8A00 in light and dark. */
    val drivingAlert: Color,
    /**
     * Fill behind the door-open warning. Light is [drivingAlert]. Dark matches
     * the surface so the screen color does not change.
     */
    val drivingAlertContainer: Color,
    /**
     * Text on [drivingAlertContainer]. Light is #121417. Dark is [drivingAlert],
     * so the warning stays orange text.
     */
    val onDrivingAlert: Color,
    /**
     * ivi_informative_negative. App-defined, not an official Pleos token.
     * The official guide has no red informative color. Light and dark differ.
     * This is the error red. The legacy team status/negative token was removed.
     */
    val informativeNegative: Color,
    val statusNeutral: Color,
)

private const val TEXT_SECONDARY = 0.84f
private const val TEXT_TERTIARY = 0.64f
private const val TEXT_DISABLED = 0.32f

/** Reads the night mode already selected on [context]. Does not look at the system theme itself. */
fun iviColors(context: Context): IviColors {
    fun color(@ColorRes id: Int) = Color(context.getColor(id))
    val text = color(R.color.ivi_text_primary)
    return IviColors(
        surfacePrimary = color(R.color.ivi_surface_primary),
        surfaceHigh = color(R.color.ivi_surface_high),
        textPrimary = text,
        textSecondary = text.copy(alpha = TEXT_SECONDARY),
        textTertiary = text.copy(alpha = TEXT_TERTIARY),
        textDisabled = text.copy(alpha = TEXT_DISABLED),
        buttonBasic = color(R.color.ivi_button_basic),
        buttonOnBasic = color(R.color.ivi_button_on_basic),
        statusSuccess = color(R.color.ivi_status_success),
        statusCaution = color(R.color.ivi_status_caution),
        statusCautionContainer = color(R.color.ivi_status_caution_container),
        statusCautionIcon = color(R.color.ivi_status_caution_icon),
        statusError = color(R.color.ivi_status_error),
        drivingAlert = color(R.color.ivi_driving_alert),
        drivingAlertContainer = color(R.color.ivi_driving_alert_container),
        onDrivingAlert = color(R.color.ivi_on_driving_alert),
        informativeNegative = color(R.color.ivi_informative_negative),
        statusNeutral = text.copy(alpha = TEXT_TERTIARY),
    )
}

/**
 * ivi_headline_small, ivi_title_large, ivi_title_medium, ivi_body_large, ivi_label_medium,
 * ivi_label_small, ivi_minimum. Body line height is at least 1.5. Nothing is below 12sp.
 * ivi_driving_main is the single driving headline size (36/44).
 */
object IviType {
    val headlineSmall = style(48, 64, FontWeight.ExtraBold)
    val titleLarge = style(40, 60, FontWeight.Bold)
    val titleMedium = style(36, 54, FontWeight.Bold)
    val drivingMain = style(36, 44, FontWeight.Bold)
    val titleSmall = style(32, 48, FontWeight.Bold)
    val bodyLarge = style(24, 36, FontWeight.Normal)
    val labelMedium = style(24, 28, FontWeight.Normal)
    val labelSmall = style(20, 24, FontWeight.Normal)
    val minimum = style(12, 18, FontWeight.Normal)

    private fun style(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
        fontFamily = AstaSans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )
}

fun iviTypography(): Typography = Typography(
    headlineLarge = IviType.headlineSmall,
    headlineMedium = IviType.headlineSmall,
    headlineSmall = IviType.headlineSmall,
    titleLarge = IviType.titleLarge,
    titleMedium = IviType.titleMedium,
    titleSmall = IviType.titleSmall,
    bodyLarge = IviType.bodyLarge,
    bodyMedium = IviType.bodyLarge,
    bodySmall = IviType.minimum,
    labelLarge = IviType.labelMedium,
    labelMedium = IviType.labelSmall,
    labelSmall = IviType.minimum,
)

private val LocalIviColors = staticCompositionLocalOf<IviColors> {
    error("IviColors is provided by FoundationTheme")
}

object IviTheme {
    val colors: IviColors
        @Composable get() = LocalIviColors.current
}

fun iviMaterialColors(colors: IviColors, dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = colors.buttonBasic,
            onPrimary = colors.buttonOnBasic,
            secondary = colors.statusCaution,
            onSecondary = colors.buttonOnBasic,
            background = colors.surfacePrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            error = colors.informativeNegative,
            onError = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.buttonBasic,
            onPrimary = colors.buttonOnBasic,
            secondary = colors.statusCaution,
            onSecondary = colors.buttonOnBasic,
            background = colors.surfacePrimary,
            onBackground = colors.textPrimary,
            surface = colors.surfacePrimary,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            error = colors.informativeNegative,
            onError = colors.textPrimary,
        )
    }

@Composable
fun ProvideIviColors(colors: IviColors, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalIviColors provides colors, content = content)
}
