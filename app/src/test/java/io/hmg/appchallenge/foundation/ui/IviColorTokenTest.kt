package io.hmg.appchallenge.foundation.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import io.hmg.appchallenge.foundation.R
import io.hmg.appchallenge.foundation.ui.theme.iviColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IviColorTokenTest {
    @Test
    fun lightTextTokensClearContrastOnLightSurfaces() {
        val context = RuntimeEnvironment.getApplication()
        val colors = iviColors(context)
        assertEquals(0xFFBE250A.toInt(), context.getColor(R.color.ivi_informative_negative))
        assertEquals(0xFFBE250A.toInt(), context.getColor(R.color.ivi_status_error))
        assertEquals(0xFF9A4A00.toInt(), context.getColor(R.color.ivi_status_caution))
        assertEquals(0xFFBD6600.toInt(), context.getColor(R.color.ivi_status_caution_icon))
        assertEquals(0xFFFF8A00.toInt(), context.getColor(R.color.ivi_driving_alert))
        assertEquals(context.getColor(R.color.ivi_surface_primary), colors.surfacePrimary.toArgb())
        assertEquals(context.getColor(R.color.ivi_text_primary), colors.textPrimary.toArgb())
        assertEquals(context.getColor(R.color.ivi_status_caution), colors.statusCaution.toArgb())
        assertEquals(context.getColor(R.color.ivi_status_error), colors.statusError.toArgb())
        assertEquals(context.getColor(R.color.ivi_informative_negative), colors.informativeNegative.toArgb())
        assertEquals(colors.statusError.toArgb(), colors.informativeNegative.toArgb())
        assertEquals(0.84f, colors.textSecondary.alpha, 0.001f)

        val bannedOnLight = setOf(0xFFFF8A00.toInt(), 0xFFFF4C28.toInt())
        val textTokens = listOf(
            colors.textPrimary,
            colors.statusCaution,
            colors.statusError,
            colors.informativeNegative,
        )
        val surfaces = listOf(colors.surfacePrimary, colors.surfaceHigh)
        textTokens.forEach { text ->
            assertFalse(bannedOnLight.contains(text.toArgb()))
            surfaces.forEach { surface ->
                val ratio = contrastRatio(text.toArgb(), surface.toArgb())
                assertTrue("$text on $surface is $ratio", ratio >= 4.5)
            }
        }
        assertFalse(bannedOnLight.contains(colors.statusCautionIcon.toArgb()))
        surfaces.forEach { surface ->
            val ratio = contrastRatio(colors.statusCautionIcon.toArgb(), surface.toArgb())
            assertTrue(ratio >= 3.0)
            assertTrue(ratio < 4.5)
        }
        assertEquals(0xFFFF8A00.toInt(), colors.drivingAlert.toArgb())
    }

    @Test
    @Config(sdk = [34], qualifiers = "night")
    fun nightKeepsDarkStatusAndDrivingAlert() {
        val night = RuntimeEnvironment.getApplication()
        val colors = iviColors(night)
        assertEquals(0xFFFF4C28.toInt(), night.getColor(R.color.ivi_informative_negative))
        assertEquals(0xFFFF4C28.toInt(), colors.informativeNegative.toArgb())
        assertEquals(0xFFFF4C28.toInt(), colors.statusError.toArgb())
        assertEquals(0xFFFF8A00.toInt(), colors.statusCaution.toArgb())
        assertEquals(0xFFFF8A00.toInt(), colors.statusCautionIcon.toArgb())
        assertEquals(0xFFFF8A00.toInt(), colors.drivingAlert.toArgb())
        assertEquals(0xFF121417.toInt(), colors.surfacePrimary.toArgb())
    }

    private fun contrastRatio(foreground: Int, background: Int): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(component: Int): Double {
            val value = component / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(AndroidColor.red(color)) +
            0.7152 * channel(AndroidColor.green(color)) +
            0.0722 * channel(AndroidColor.blue(color))
    }
}
