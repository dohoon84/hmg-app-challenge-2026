package com.krazylabs.miri.ui.design

import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.PortSide
import com.krazylabs.miri.mvp.D2Face
import com.krazylabs.miri.mvp.D2Rules
import com.krazylabs.miri.mvp.DestinationRole
import com.krazylabs.miri.ui.ShownCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fuel D2 copy. The remain pill and the charge labels stay on the EV card only. */
class D2FuelCopyTest {
    @Test
    fun gasolineDropsChargeLabelsAndTheRemainPill() {
        val face = fuelFace(FuelKind.GASOLINE, PortSide.FRONT_LEFT)
        assertEquals("주유소에 가요", face.title)
        assertEquals(listOf("휘발유"), face.chips)
        assertEquals(listOf("주유구는 왼쪽에 있어요."), face.lines)
        val labels = d2Labels(shown(face, chargePercent = 15))
        assertFuel(labels, badge = "휘발유")
    }

    @Test
    fun dieselUsesTheMappedBadgeWithoutARemainPill() {
        val face = fuelFace(FuelKind.DIESEL, PortSide.REAR_RIGHT)
        assertEquals(listOf("경유"), face.chips)
        assertEquals(listOf("주유구는 오른쪽에 있어요."), face.lines)
        assertFuel(d2Labels(shown(face, chargePercent = 40)), badge = "경유")
    }

    @Test
    fun lpgHybridAndMultiFuelUseTheSameMapping() {
        assertEquals("LPG", D2Rules.fuelBadge(fuelFace(FuelKind.LPG, PortSide.FRONT_RIGHT).chips))
        assertEquals("하이브리드", D2Rules.fuelBadge(fuelFace(FuelKind.HYBRID, PortSide.REAR_LEFT).chips))
        val multi = D2Rules.offer(
            CarIdentity(fuels = listOf(FuelKind.GASOLINE, FuelKind.LPG), fuelDoor = PortSide.REAR_LEFT),
            DestinationRole.GAS,
        )
        assertEquals(listOf("연료"), multi?.chips)
        assertFuel(d2Labels(shown(checkNotNull(multi), chargePercent = 15)), badge = "연료")
    }

    @Test
    fun aFuelLineWithoutTheGasTitleIsStillAFuelStop() {
        val labels = d2Labels(
            ShownCard(
                title = "오늘 길",
                lines = listOf("주유구는 왼쪽에 있어요."),
                chips = listOf("경유"),
                chargePercent = 8,
            ),
        )
        assertFuel(labels, badge = "경유")
    }

    @Test
    fun evCardKeepsTheChargeCopy() {
        val labels = d2Labels(
            ShownCard(
                title = "충전소에 가요",
                lines = listOf("충전구는 오른쪽 뒤에 있어요.", "커넥터: DC 콤보"),
                chips = listOf("전기", "DC 콤보"),
                portName = "REAR_RIGHT",
                chargePercent = 15,
            ),
        )
        assertEquals("충전", labels.energy)
        assertEquals("15% 남음", labels.badge)
        assertEquals("충전구", labels.portCaption)
        assertEquals("DC 콤보", labels.connector)
    }

    @Test
    fun aMissingCardStaysOnTheEvCopy() {
        val labels = d2Labels(null)
        assertEquals("충전", labels.energy)
        assertNull(labels.badge)
        assertEquals("충전구", labels.portCaption)
        assertNull(labels.connector)
    }

    private fun assertFuel(labels: D2Labels, badge: String) {
        assertEquals("연료", labels.energy)
        assertEquals(badge, labels.badge)
        assertEquals("주유구", labels.portCaption)
        assertNull(labels.connector)
        val copy = listOf(labels.energy, labels.badge, labels.portCaption, labels.connector)
        assertTrue(copy.none { it?.contains("충전") == true })
        assertTrue(copy.none { it?.contains("충전구") == true })
        assertFalse(labels.badge.orEmpty().contains('%'))
        assertFalse(labels.badge.orEmpty().contains("남음"))
    }

    private fun fuelFace(kind: FuelKind, door: PortSide): D2Face =
        checkNotNull(
            D2Rules.offer(
                CarIdentity(fuels = listOf(kind), fuelDoor = door),
                DestinationRole.GAS,
            ),
        )

    private fun shown(face: D2Face, chargePercent: Int): ShownCard = ShownCard(
        title = face.title,
        lines = face.lines,
        chips = face.chips,
        portName = face.port?.name,
        chargePercent = chargePercent,
    )
}
