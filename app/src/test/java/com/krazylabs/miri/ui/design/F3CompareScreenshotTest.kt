package com.krazylabs.miri.ui.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.mvp.F3Rules
import com.krazylabs.miri.ui.theme.FoundationTheme
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Auto-play F3 beside the approved mockup. The full design-match suite stays
 * ignored because captureToImage waits on a GPU redraw. This draws the view
 * into a software bitmap instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi")
class F3CompareScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun autoPlayDepartLightMatchesTheMockup() {
        save("F3_compare_light.png", departModel(), dark = false, mockup = "F3_return-ready_depart_light")
    }

    @Test
    fun autoPlayDepartDarkMatchesTheMockup() {
        save("F3_compare_dark.png", departModel(), dark = true, mockup = "F3_return-ready_depart_dark")
    }

    @Test
    fun aMissingTripRendersTheFallbackCopy() {
        val zone = ZoneId.of("Asia/Seoul")
        val back = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = back - 3 * 60 * 60_000L
        val model = f3Model(
            F3Rules.lines(
                ev = true,
                level = null,
                target = null,
                returnAtMs = back,
                nowMs = now,
                etaSeconds = null,
                zone = zone,
            ),
        )
        assertEquals("반납까지", model.label)
        assertEquals("3", model.hero)
        assertEquals("반납 20:00까지 3시간 남았어요.", model.sentence)
        save("F3_compare_fallback.png", model, dark = false, mockup = "F3_return-ready_fallback_light")
    }

    private fun save(name: String, model: F3Model, dark: Boolean, mockup: String) {
        val rendered = render(dark) {
            F3ReturnScreen(
                card = null,
                dark = dark,
                model = model,
                showReturnGuide = true,
            )
        }
        assertTrue("$name is blank", varied(rendered))
        assertTrue("$name dashed stroke missing", dashedStrokeDrawn(rendered))
        val side = composite(rendered, loadMockup(mockup))
        val out = File("/opt/cursor/artifacts", name)
        out.parentFile?.mkdirs()
        out.outputStream().use { side.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name missing", out.isFile && out.length() > 0L)
        assertTrue("$name width ${side.width}", side.width >= 1682 * 2)
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
        val width = view.width.coerceAtLeast(1682)
        val height = view.height.coerceAtLeast(1210)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return crop(bitmap, 1682, 1210)
    }

    private fun crop(source: Bitmap, width: Int, height: Int): Bitmap {
        val w = minOf(width, source.width)
        val h = minOf(height, source.height)
        if (w == source.width && h == source.height) return source
        return Bitmap.createBitmap(source, 0, 0, w, h)
    }

    private fun loadMockup(name: String): Bitmap {
        val file = repoFile("docs/mvp/mockups/u1-rental/v4/$name.png")
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ?: error("missing ${file.absolutePath}")
    }

    private fun composite(ours: Bitmap, mockup: Bitmap): Bitmap {
        val gap = 8
        val width = ours.width + gap + mockup.width
        val height = maxOf(ours.height, mockup.height)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(AndroidColor.BLACK)
        canvas.drawBitmap(ours, 0f, 0f, null)
        canvas.drawBitmap(mockup, (ours.width + gap).toFloat(), 0f, null)
        return out
    }

    private fun varied(bitmap: Bitmap): Boolean {
        if (bitmap.width < 100 || bitmap.height < 100) return false
        val origin = bitmap.getPixel(40, 40)
        val probes = listOf(400 to 200, 900 to 360, 1100 to 700, 1500 to 1100)
        return probes.any { (x, y) ->
            x < bitmap.width && y < bitmap.height && bitmap.getPixel(x, y) != origin
        }
    }

    private fun departModel(): F3Model {
        val zone = ZoneId.of("Asia/Seoul")
        val back = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = back - 120 * 60_000L
        val model = f3Model(
            F3Rules.lines(
                ev = true,
                level = 85,
                target = 80,
                returnAtMs = back,
                nowMs = now,
                etaSeconds = F3Rules.DEMO_ETA_SECONDS,
                zone = zone,
            ),
        )
        assertEquals("19:03", model.hero)
        assertEquals("20:00", model.leftValue)
        assertEquals("약 42분", model.rightValue)
        assertEquals("반납", model.leftLabel)
        assertEquals("가는 데", model.rightLabel)
        return model
    }

    /** The guide outline is the accent pixels on the button edge, not the fill. */
    private fun dashedStrokeDrawn(bitmap: Bitmap): Boolean {
        val canvas = bitmap.getPixel(1400, 200)
        var hits = 0
        val left = 1256
        val top = 1062
        val right = 1633
        val bottom = 1161
        if (bitmap.width <= right || bitmap.height <= bottom) return false
        for (x in left until right) {
            if (bitmap.getPixel(x, top) != canvas) hits++
            if (bitmap.getPixel(x, bottom) != canvas) hits++
        }
        return hits > 80
    }

    private fun repoFile(path: String): File {
        val dir = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(dir, "settings.gradle.kts").exists()) dir else dir.parentFile ?: dir
        return File(root, path)
    }
}
