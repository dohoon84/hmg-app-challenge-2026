package io.hmg.appchallenge.foundation.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.hmg.appchallenge.foundation.R

/**
 * Asta Sans, OFL 1.1. Regular, Bold, and ExtraBold.
 * The notice is in assets/asta-sans/OFL.txt.
 */
val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_bold, FontWeight.Bold),
    Font(R.font.asta_sans_extrabold, FontWeight.ExtraBold),
)

/**
 * Named like the design-token code syntax: ivi_ + snake_case.
 * Text on a surface uses [IviColors.textPrimary]. Status colors are for icons and containers.
 * Official ivi_basic steps are not in a library this project can import, so the neutral
 * surfaces follow the published white 100/84/64/32% text rule.
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
    val statusCaution: Color,
    val statusCautionContainer: Color,
    val statusError: Color,
    val statusNeutral: Color,
)

val IviDarkColors = IviColors(
    surfacePrimary = Color(0xFF121417),
    surfaceHigh = Color(0xFF1C2128),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.84f),
    textTertiary = Color.White.copy(alpha = 0.64f),
    textDisabled = Color.White.copy(alpha = 0.32f),
    buttonBasic = Color(0xFFF2F3F5),
    buttonOnBasic = Color(0xFF121417),
    statusSuccess = Color(0xFF00E074),
    statusCaution = Color(0xFFFF8A00),
    statusCautionContainer = Color(0xFF3A2200),
    statusError = Color(0xFFFF4C28),
    statusNeutral = Color.White.copy(alpha = 0.64f),
)

val IviLightColors = IviColors(
    surfacePrimary = Color.White,
    surfaceHigh = Color(0xFFF2F3F5),
    textPrimary = Color(0xFF121417),
    textSecondary = Color(0xFF121417).copy(alpha = 0.84f),
    textTertiary = Color(0xFF121417).copy(alpha = 0.64f),
    textDisabled = Color(0xFF121417).copy(alpha = 0.32f),
    buttonBasic = Color(0xFF121417),
    buttonOnBasic = Color.White,
    statusSuccess = Color(0xFF02C265),
    statusCaution = Color(0xFF9A4A00),
    statusCautionContainer = Color(0xFFFFF0DE),
    statusError = Color(0xFFFE3D16),
    statusNeutral = Color(0xFF121417).copy(alpha = 0.64f),
)

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

private val LocalIviColors = staticCompositionLocalOf { IviDarkColors }

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
            error = colors.statusError,
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
            error = colors.statusError,
            onError = colors.textPrimary,
        )
    }

@Composable
fun ProvideIviColors(colors: IviColors, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalIviColors provides colors, content = content)
}
