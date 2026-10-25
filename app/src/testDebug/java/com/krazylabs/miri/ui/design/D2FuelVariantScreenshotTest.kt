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
import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.PortSide
import com.krazylabs.miri.mvp.D2Rules
import com.krazylabs.miri.mvp.DestinationRole
import com.krazylabs.miri.ui.ShownCard
import com.krazylabs.miri.ui.theme.FoundationTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D2 fuel stops. Gasoline is the default door screen (left, no remain pill).
 * Diesel is the second fuel. Each test draws one software bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-mdpi")
class D2FuelVariantScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun gasolineLightShowsTheFuelDoor() {
        val card = fuelCard(FuelKind.GASOLINE, PortSide.FRONT_LEFT)
        val rendered = render(dark = false) { D2ChargeScreen(card, dark = false) }
        assertFuelNodes(badge = "휘발유", side = "왼쪽")
        write("D2_fuel_gasoline_light.png", rendered)
    }

    @Test
    fun gasolineDarkShowsTheFuelDoor() {
        val card = fuelCard(FuelKind.GASOLINE, PortSide.FRONT_LEFT)
        val rendered = render(dark = true) { D2ChargeScreen(card, dark = true) }
        assertFuelNodes(badge = "휘발유", side = "왼쪽")
        write("D2_fuel_gasoline_dark.png", rendered)
    }

    @Test
    fun dieselLightShowsTheFuelDoor() {
        val card = fuelCard(FuelKind.DIESEL, PortSide.REAR_RIGHT)
        val rendered = render(dark = false) { D2ChargeScreen(card, dark = false) }
        assertFuelNodes(badge = "경유", side = "오른쪽")
        write("D2_fuel_diesel_light.png", rendered)
    }

    @Test
    fun evChargeCopyStaysOnTheChargeScreen() {
        render(dark = false) {
            D2ChargeScreen(
                card = ShownCard(
                    title = "충전소에 가요",
                    lines = listOf("충전구는 오른쪽 뒤에 있어요.", "커넥터: DC 콤보"),
                    chips = listOf("전기", "DC 콤보"),
                    portName = "REAR_RIGHT",
                    chargePercent = 15,
                ),
                dark = false,
            )
        }
        rule.onNodeWithText("충전소에 가요").assertExists()
        rule.onNodeWithText("충전").assertExists()
        rule.onNodeWithText("15% 남음").assertExists()
        rule.onNodeWithText("충전구").assertExists()
        rule.onNodeWithText("오른쪽 뒤").assertExists()
        rule.onNodeWithText("커넥터").assertExists()
        rule.onNodeWithText("DC 콤보").assertExists()
        absent("연료")
        absent("주유구")
        absent("주유소에 가요")
    }

    @Test
    fun aRenamedFuelCardStillShowsTheFuelPill() {
        val card = fuelCard(FuelKind.GASOLINE, PortSide.FRONT_LEFT).copy(
            title = "기름 넣으러 가요",
            lines = listOf("문은 왼쪽에 있어요."),
        )
        render(dark = false) { D2ChargeScreen(card, dark = false) }
        rule.onNodeWithText("기름 넣으러 가요").assertExists()
        rule.onNodeWithText("연료").assertExists()
        rule.onNodeWithText("휘발유").assertExists()
        rule.onNodeWithText("주유구").assertExists()
        absent("충전")
        absent("충전구")
        absent("15% 남음")
        rule.onAllNodesWithText("% 남음", substring = true).assertCountEquals(0)
    }

    private fun assertFuelNodes(badge: String, side: String) {
        rule.onNodeWithText("주유소에 가요").assertExists()
        rule.onNodeWithText("정차 중 · 출발 전").assertExists()
        rule.onNodeWithText("연료").assertExists()
        rule.onNodeWithText(badge).assertExists()
        rule.onNodeWithText("주유구").assertExists()
        rule.onNodeWithText(side).assertExists()
        rule.onNodeWithText("다 봤어요").assertExists()
        absent("충전")
        absent("충전구")
        absent("충전소에 가요")
        absent("커넥터")
        absent("15% 남음")
        rule.onAllNodesWithText("% 남음", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("남음", substring = true).assertCountEquals(0)
    }

    private fun absent(text: String) {
        rule.onAllNodesWithText(text).assertCountEquals(0)
    }

    private fun fuelCard(kind: FuelKind, door: PortSide): ShownCard {
        val face = checkNotNull(
            D2Rules.offer(
                CarIdentity(fuels = listOf(kind), fuelDoor = door),
                DestinationRole.GAS,
            ),
        )
        assertEquals(listOf(if (kind == FuelKind.DIESEL) "경유" else "휘발유"), face.chips)
        return ShownCard(
            title = face.title,
            lines = face.lines,
            chips = face.chips,
            portName = face.port?.name,
            chargePercent = 15,
            fuelBadge = face.fuelBadge,
        )
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
