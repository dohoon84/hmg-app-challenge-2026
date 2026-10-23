package io.hmg.appchallenge.foundation.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import io.hmg.appchallenge.foundation.R
import io.hmg.appchallenge.foundation.ui.theme.IviDarkColors
import io.hmg.appchallenge.foundation.ui.theme.IviLightColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IviColorTokenTest {
    @Test
    fun informativeNegativeIsAppDefinedAndShared() {
        val light = RuntimeEnvironment.getApplication()
        assertEquals(0xFFFF4C28.toInt(), light.getColor(R.color.ivi_informative_negative))
        assertEquals(0xFFFE3D16.toInt(), light.getColor(R.color.ivi_team_status_negative_legacy))
        assertEquals(0xFFFF8A00.toInt(), light.getColor(R.color.ivi_driving_alert))
        assertEquals(AndroidColor.parseColor("#FF4C28"), IviLightColors.informativeNegative.toArgb())
        assertEquals(AndroidColor.parseColor("#FF4C28"), IviDarkColors.informativeNegative.toArgb())
        assertEquals(AndroidColor.parseColor("#FE3D16"), IviLightColors.teamStatusNegativeLegacy.toArgb())
        assertEquals(AndroidColor.parseColor("#FF4C28"), IviDarkColors.teamStatusNegativeLegacy.toArgb())
        assertEquals(AndroidColor.parseColor("#FF8A00"), IviLightColors.drivingAlert.toArgb())
        assertEquals(AndroidColor.parseColor("#FF8A00"), IviDarkColors.drivingAlert.toArgb())
        assertEquals(AndroidColor.parseColor("#9A4A00"), IviLightColors.statusCaution.toArgb())
    }

    @Test
    @Config(sdk = [34], qualifiers = "night")
    fun nightKeepsInformativeNegativeAndLegacyDarkValue() {
        val night = RuntimeEnvironment.getApplication()
        assertEquals(0xFFFF4C28.toInt(), night.getColor(R.color.ivi_informative_negative))
        assertEquals(0xFFFF4C28.toInt(), night.getColor(R.color.ivi_team_status_negative_legacy))
        assertEquals(0xFFFF8A00.toInt(), night.getColor(R.color.ivi_driving_alert))
    }
}
