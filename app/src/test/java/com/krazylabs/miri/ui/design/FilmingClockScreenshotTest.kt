package com.krazylabs.miri.ui.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.mvp.F3Rules
import com.krazylabs.miri.ui.PlaceChip
import com.krazylabs.miri.ui.theme.FoundationTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Filming F2 and F3 share the saved return chip. Each test calls setContent
 * once and draws the view into a software bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi")
class FilmingClockScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = WallClock { LocalDateTime.of(2026, 10, 24, 20, 45) }
    private val draft = F2Draft(dayOffset = 2, minuteOfDay = 12 * 60, placeId = "pickup")

    @Test
    fun filmingF2LightShowsTheSavedChip() = saveF2("F2_filming_light.png", dark = false)

    @Test
    fun filmingF2DarkShowsTheSavedChip() = saveF2("F2_filming_dark.png", dark = true)

    @Test
    fun filmingF3LightMatchesThatChip() = saveF3("F3_filming_light.png", dark = false)

    @Test
    fun filmingF3DarkMatchesThatChip() = saveF3("F3_filming_dark.png", dark = true)

    private fun saveF2(name: String, dark: Boolean) {
        assertEquals("10/26 12:00", returnSummary(today, draft.dayOffset, draft.epochDay, draft.minuteOfDay))
        val rendered = render(dark) {
            F2ReturnScreen(
                draft = draft,
                places = listOf(PlaceChip("pickup", "대치동 공영주차장", caption = "대여한 곳")),
                clock = clock,
                onOpenDates = {},
                onDate = {},
                onTime = {},
                onPlace = {},
                onDone = {},
            )
        }
        rule.onNodeWithText("10/26 12:00", substring = true).assertExists()
        write(name, rendered)
    }

    private fun saveF3(name: String, dark: Boolean) {
        val at = F3Rules.savedReturnAtMs(today.plusDays(2).toEpochDay(), 12 * 60, zone)
        val model = f3Model(
            F3Rules.lines(
                ev = true,
                level = 80,
                target = 80,
                returnAtMs = at,
                nowMs = clock.now().atZone(zone).toInstant().toEpochMilli(),
                etaSeconds = F3Rules.DEMO_ETA_SECONDS,
                zone = zone,
            ),
        )
        assertEquals("11:03", model.hero)
        assertEquals("12:00", model.leftValue)
        assertEquals("약 42분", model.rightValue)
        val rendered = render(dark) {
            F3ReturnScreen(
                card = null,
                dark = dark,
                model = model,
                showReturnGuide = false,
            )
        }
        rule.onNodeWithText("11:03").assertExists()
        rule.onNodeWithText("12:00").assertExists()
        rule.onNodeWithText("약 42분").assertExists()
        rule.onAllNodesWithText("00:47").assertCountEquals(0)
        write(name, rendered)
    }

    private fun write(name: String, rendered: Bitmap) {
        assertTrue("$name is blank", varied(rendered))
        val out = File("/opt/cursor/artifacts", name)
        out.parentFile?.mkdirs()
        out.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name missing", out.isFile && out.length() > 0L)
    }

    private fun render(dark: Boolean, content: @Composable () -> Unit): Bitmap {
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            FoundationTheme(dark = dark) {
                Box(Modifier.requiredSize(1682.dp, 1210.dp).fillMaxSize()) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1682),
            view.height.coerceAtLeast(1210),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        return if (bitmap.width == 1682 && bitmap.height == 1210) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, minOf(1682, bitmap.width), minOf(1210, bitmap.height))
        }
    }

    private fun varied(bitmap: Bitmap): Boolean {
        if (bitmap.width < 100 || bitmap.height < 100) return false
        val origin = bitmap.getPixel(40, 40)
        return listOf(200 to 300, 900 to 360, 400 to 700).any { (x, y) ->
            x < bitmap.width && y < bitmap.height && bitmap.getPixel(x, y) != origin
        }
    }
}
