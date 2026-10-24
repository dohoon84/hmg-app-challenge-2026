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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.mvp.CheckItem
import com.krazylabs.miri.mvp.CheckKind
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.FoundationTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settled C1 and F4 rows. Each test calls setContent once and draws a software bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi")
class SettledCheckScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val closedDoor = CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요.", settled = true)

    @Test
    fun settledC1Light() = saveC1("C1_settled_light.png", dark = false)

    @Test
    fun settledC1Dark() = saveC1("C1_settled_dark.png", dark = true)

    @Test
    fun settledF4Light() = saveF4("F4_settled_light.png", dark = false)

    @Test
    fun settledF4Dark() = saveF4("F4_settled_dark.png", dark = true)

    private fun saveC1(name: String, dark: Boolean) {
        val rendered = render(dark) {
            C1CheckScreen(
                card = ShownCard(
                    title = "반납 전 점검",
                    lines = listOf(closedDoor.text),
                    checks = listOf(closedDoor),
                ),
                dark = dark,
            )
        }
        rule.onNodeWithText("닫힘").assertExists()
        rule.onNodeWithText("오른쪽 뒷문", substring = true).assertExists()
        rule.onAllNodesWithText("열림").assertCountEquals(0)
        rule.onAllNodesWithTag(CheckRowStyle.CAUTION).assertCountEquals(0)
        rule.onAllNodesWithTag(CheckRowStyle.SETTLED).assertCountEquals(1)
        rule.onAllNodesWithTag(CheckRowStyle.CHECK).assertCountEquals(1)
        write(name, rendered)
    }

    private fun saveF4(name: String, dark: Boolean) {
        val rendered = render(dark) {
            F4ChecklistScreen(
                card = ShownCard(
                    title = "반납 장소에 도착했어요",
                    lines = listOf(closedDoor.text, "충전 80% · 반납 기준 80%"),
                    checks = listOf(closedDoor),
                ),
                dark = dark,
            )
        }
        rule.onNodeWithText("닫힘").assertExists()
        rule.onNodeWithText("꺼졌어요").assertExists()
        rule.onAllNodesWithText("열림").assertCountEquals(0)
        rule.onAllNodesWithTag(CheckRowStyle.CAUTION).assertCountEquals(0)
        rule.onAllNodesWithTag(CheckRowStyle.SETTLED).assertCountEquals(2)
        rule.onAllNodesWithTag(CheckRowStyle.CHECK).assertCountEquals(2)
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
