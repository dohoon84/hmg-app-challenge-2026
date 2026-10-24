package com.krazylabs.miri.ui.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredSize
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.ui.PlaceChip
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.FoundationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Headless 1682×1210 renders. The comparison image is our frame on the left
 * and the approved mockup on the right.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi")
class DesignMatchScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val previewClock = WallClock {
        LocalDateTime.of(2026, 10, 24, 17, 10)
    }

    @Test
    fun lightFrames() = renderSet(dark = false)

    @Test
    @Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi-night")
    fun darkFrames() = renderSet(dark = true)

    private fun renderSet(dark: Boolean) {
        val suffix = if (dark) "dark" else "light"
        capture("F1_vehicle-type_$suffix") {
            F1KindScreen(
                highlighted = VehicleKind.RENTAL,
                onOwn = {},
                onRental = {},
                onShare = {},
                dark = dark,
            )
        }
        capture("F2_return-info_$suffix") {
            F2ReturnScreen(
                draft = F2Draft(),
                places = samplePlaces,
                clock = previewClock,
                onOpenDates = {},
                onDate = {},
                onTime = {},
                onPlace = {},
                onDone = {},
            )
        }
        capture("F2_return-info_done_$suffix") {
            F2ReturnScreen(
                draft = F2Draft(minuteOfDay = 20 * 60),
                places = samplePlaces,
                clock = previewClock,
                onOpenDates = {},
                onDate = {},
                onTime = {},
                onPlace = {},
                onDone = {},
            )
        }
        capture("F2s_date-sheet_$suffix") {
            F2ReturnScreen(
                draft = F2Draft(dateSheetOpen = true),
                places = samplePlaces,
                clock = previewClock,
                onOpenDates = {},
                onDate = {},
                onTime = {},
                onPlace = {},
                onDone = {},
            )
        }
        capture("D1_route_standalone_$suffix") {
            D1RoadScreen(
                card = ShownCard(
                    title = "오늘 길",
                    lines = listOf("톨게이트 1곳", "고속도로 진입 1번"),
                ),
                dark = dark,
            )
        }
        capture("D2ev_charge-port_main_$suffix") {
            D2ChargeScreen(
                card = ShownCard(
                    title = "충전소에 가요",
                    lines = listOf("충전구는 오른쪽 뒤에 있어요.", "커넥터: DC 콤보"),
                    chips = listOf("전기", "DC 콤보"),
                    portName = "REAR_RIGHT",
                    chargePercent = 15,
                ),
                dark = dark,
            )
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            FoundationTheme {
                Box(Modifier.requiredSize(1682.dp, 1210.dp).fillMaxSize()) {
                    content()
                }
            }
        }
        rule.waitForIdle()
        val rendered = rule.onRoot().captureToImage().asAndroidBitmap()
        val mockup = loadMockup(name)
        val side = composite(rendered, mockup)
        val out = comparisonFile(name)
        val update = System.getProperty("miri.designMatch.update") == "true" || !out.exists()
        if (update) {
            out.parentFile.mkdirs()
            out.outputStream().use { side.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val artifacts = File("/opt/cursor/artifacts/design-match")
            artifacts.mkdirs()
            File(artifacts, out.name).outputStream().use { side.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val golden = Bitmap.createBitmap(side.width, side.height, Bitmap.Config.ARGB_8888)
            // The committed file is the comparison itself. Re-render must stay close.
            val stored = android.graphics.BitmapFactory.decodeFile(out.absolutePath)
            assertTrue("$name golden missing", stored != null)
            val diff = meanDelta(side, stored)
            assertTrue("$name drifted by $diff", diff < 12.0)
        }
        assertTrue("$name width ${side.width}", side.width >= 1682 * 2)
    }

    private fun loadMockup(name: String): Bitmap {
        val stream = javaClass.classLoader.getResourceAsStream("mockups/$name.png")
            ?: error("missing mockup mockups/$name.png")
        return stream.use { android.graphics.BitmapFactory.decodeStream(it) }
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

    private fun meanDelta(left: Bitmap, right: Bitmap): Double {
        val w = minOf(left.width, right.width)
        val h = minOf(left.height, right.height)
        var sum = 0.0
        var n = 0
        val step = 8
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val a = left.getPixel(x, y)
                val b = right.getPixel(x, y)
                sum += abs(AndroidColor.red(a) - AndroidColor.red(b))
                sum += abs(AndroidColor.green(a) - AndroidColor.green(b))
                sum += abs(AndroidColor.blue(a) - AndroidColor.blue(b))
                n += 3
                x += step
            }
            y += step
        }
        return if (n == 0) 0.0 else sum / n
    }

    private fun comparisonFile(name: String): File {
        val dir = System.getProperty("user.dir")
        val module = File(dir)
        val root = if (File(module, "settings.gradle.kts").exists()) module else module.parentFile
        return File(root, "docs/design-match/$name.png")
    }

    private companion object {
        val samplePlaces = listOf(
            PlaceChip("pickup", "대치동 공영주차장", caption = "대여한 곳", distance = "2.4 km"),
            PlaceChip("yeoksam", "역삼 영업소", caption = "영업소", distance = "3.1 km"),
            PlaceChip("pangyo", "판교역 환승주차장", caption = "최근 목적지", distance = "12.8 km"),
        )
    }
}
