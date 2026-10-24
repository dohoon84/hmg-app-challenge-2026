package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.CarFacts
import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.DoorId
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.GearBackend
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.PanelState
import com.krazylabs.miri.domain.PortSide
import com.krazylabs.miri.domain.WindowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MvpPhaseTest {
    @Test
    fun gearBackendDefaultsToAaos() {
        assertEquals(GearBackend.AAOS, GearBackend.normalize(null))
        assertEquals(GearBackend.AAOS, GearBackend.normalize(""))
        assertEquals(GearBackend.AAOS, GearBackend.normalize("aaos"))
        assertEquals(GearBackend.PLEOS, GearBackend.normalize("pleos"))
    }

    @Test
    fun comboTwoAndIecAreDcCombo() {
        val identity = CarFacts.from(
            intArrayOf(CarFacts.FUEL_ELECTRIC),
            null,
            CarFacts.PORT_REAR_RIGHT,
            intArrayOf(CarFacts.CONNECTOR_COMBO_2),
        )
        assertTrue(identity.evOnly)
        assertEquals(PortSide.REAR_RIGHT, identity.evPort)
        assertTrue(identity.connectorDcCombo)
        assertTrue(CarFacts.from(intArrayOf(10), null, 3, intArrayOf(CarFacts.CONNECTOR_COMBO_1)).connectorDcCombo)
        assertFalse(CarFacts.from(intArrayOf(10), null, 3, intArrayOf(CarFacts.CONNECTOR_CHADEMO)).connectorDcCombo)
        assertFalse(
            CarFacts.from(intArrayOf(10), null, 3, intArrayOf(CarFacts.CONNECTOR_TESLA_ROADSTER)).connectorDcCombo,
        )
    }

    @Test
    fun emptyFuelOrPortDropsD2() {
        assertNull(D2Rules.offer(CarIdentity(), DestinationRole.CHARGING))
        assertNull(
            D2Rules.offer(
                CarIdentity(fuels = listOf(FuelKind.ELECTRIC), evPort = null),
                DestinationRole.CHARGING,
            ),
        )
    }

    @Test
    fun unknownConnectorOmitsOnlyThatSentence() {
        val face = D2Rules.offer(evRearRight(combo = false), DestinationRole.CHARGING)
        assertEquals("충전소에 가요", face?.title)
        assertEquals(listOf("충전구는 오른쪽 뒤에 있어요."), face?.lines)
        assertFalse(face?.chips?.contains("DC 콤보") == true)
        assertFalse(face?.clips?.contains("vo_connector_dc_combo") == true)
        assertTrue(face?.clips?.contains("vo_charge_port_rear_right") == true)
    }

    @Test
    fun fuelMismatchAndMultiFuelFollowTheCommonRules() {
        assertNull(D2Rules.offer(evRearRight(true), DestinationRole.GAS))
        assertNull(
            D2Rules.offer(
                CarIdentity(fuels = listOf(FuelKind.GASOLINE), fuelDoor = PortSide.FRONT_RIGHT),
                DestinationRole.CHARGING,
            ),
        )
        val multi = D2Rules.offer(
            CarIdentity(
                fuels = listOf(FuelKind.GASOLINE, FuelKind.LPG),
                fuelDoor = PortSide.REAR_LEFT,
            ),
            DestinationRole.GAS,
        )
        assertEquals("주유소에 가요", multi?.title)
        assertEquals(listOf("연료"), multi?.chips)
        assertEquals(emptyList<String>(), multi?.clips)
        assertEquals(emptyList<String>(), multi?.arrivalClips)
        assertNull(
            D2Rules.offer(
                CarIdentity(fuels = listOf(FuelKind.GASOLINE, FuelKind.ELECTRIC), evPort = PortSide.REAR_RIGHT),
                DestinationRole.CHARGING,
            ),
        )
    }

    @Test
    fun centerPortKeepsTheCardWithoutAPortClip() {
        val face = D2Rules.offer(
            CarIdentity(fuels = listOf(FuelKind.ELECTRIC), evPort = PortSide.REAR, connectorDcCombo = true),
            DestinationRole.CHARGING,
        )
        assertEquals("충전구는 뒤에 있어요.", face?.lines?.first())
        assertEquals(emptyList<String>(), face?.clips)
        assertEquals(emptyList<String>(), face?.arrivalClips)
    }

    @Test
    fun disagreeingSpecDropsD2() {
        assertNull(D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, specAgrees = false))
    }

    @Test
    fun windowCopyUsesTheSeatPhrase() {
        val open = PanelState(position = 1, readable = true)
        val lines = CheckRules.items(
            doors = emptyMap(),
            windows = mapOf(WindowId.ROW_1_LEFT to open),
            cabinOn = true,
            reading = mapOf(com.krazylabs.miri.domain.SeatId.REAR_LEFT to true),
            lightsUseDoorStandIn = false,
        )
        assertEquals("앞 왼쪽 창문이 열려 있어요.", lines[0])
        assertTrue(lines.contains("실내등이 켜져 있어요."))
        assertTrue(lines.contains("뒤쪽 왼쪽 독서등이 켜져 있어요."))
        val stoodIn = CheckRules.items(
            doors = mapOf(DoorId.ROW_2_RIGHT to open),
            windows = emptyMap(),
            cabinOn = true,
            reading = emptyMap(),
            lightsUseDoorStandIn = true,
        )
        assertEquals(listOf("오른쪽 뒷문이 열려 있어요."), stoodIn)
    }

    @Test
    fun reverseShowsNothingUntilResume() {
        val session = CardSession()
        val parked = baseInput(GearPosition.REVERSE)
        assertEquals(MiriPane.ICON, session.step(parked).pane)
        val back = session.step(baseInput(GearPosition.PARK, showIntake = true))
        assertEquals(MiriPane.ICON, back.pane)
        session.onResume()
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
    }

    @Test
    fun checkOutranksIntakeAndD1IsNotDeferred() {
        val session = CardSession()
        val step = session.step(
            baseInput(GearPosition.PARK).copy(
                showIntake = true,
                checkItems = listOf("오른쪽 뒷문이 열려 있어요."),
                d1 = D1Face("route-1", listOf("톨게이트 1곳"), "vo_toll_1"),
            ),
        )
        assertEquals(MiriPane.CHECK, step.pane)
        session.step(baseInput(GearPosition.DRIVE))
        val next = session.step(baseInput(GearPosition.PARK, edgesOpen = true).copy(
            d1 = D1Face("route-1", listOf("톨게이트 1곳"), "vo_toll_1"),
        ))
        assertEquals(MiriPane.ICON, next.pane)
    }

    @Test
    fun chargingDestinationPrefersD2OverF3() {
        val session = CardSession()
        val step = session.step(
            baseInput(GearPosition.PARK).copy(
                d2 = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING),
                f3 = F3Face("rent-3h", listOf("반납까지 약 10% 더 충전해야 해요.")),
            ),
        )
        assertEquals(MiriPane.D2, step.pane)
        assertTrue(step.voice is VoicePlay.Clips)
    }

    @Test
    fun f3EvCopyAndTripFallback() {
        val lines = F3Rules.lines(
            ev = true,
            level = 40,
            target = 80,
            returnAtMs = 1_700_000_000_000L,
            nowMs = 1_700_000_000_000L - 3_600_000L,
            etaSeconds = null,
        )
        assertEquals("반납까지 약 40% 더 충전해야 해요.", lines[0])
        assertTrue(lines[1].contains("1시간 남았어요."))
        val soon = F3Rules.lines(
            ev = true,
            level = 40,
            target = 80,
            returnAtMs = 1_700_000_000_000L,
            nowMs = 1_700_000_000_000L - 30 * 60_000L,
            etaSeconds = null,
        )
        assertTrue(soon[1].contains("30분 남았어요."))
        assertFalse(soon[1].contains("0시간"))
        val trip = straightLineTrip(37.5, 127.0, 37.5, 127.0)
        assertEquals(0, trip.distance)
        val asked = TripOnce()
        assertTrue(asked.begin("event"))
        assertFalse(asked.begin("event"))
    }

    @Test
    fun missingCatalogClipUsesDeviceTtsThenChime() {
        assertEquals(VoicePath.TTS, VoiceFallback.catalog("vo_highway_entry", 0))
        assertEquals(VoicePath.CLIP, VoiceFallback.catalog("vo_highway_entry", 12))
        assertEquals(VoicePath.DROP, VoiceFallback.catalog("vo_turn_signal_left", 12))
        assertEquals(VoicePath.CHIME, VoiceFallback.whenEngineUnavailable())
        assertEquals(29, VoiceClips.names.size)
        assertEquals(VoiceClips.names.toSet(), VoiceClips.names.mapNotNull { name ->
            VoiceClips.spoken(name)?.let { name }
        }.toSet())
        assertEquals(VoiceClips.H1_TEXT, VoiceClips.spoken(VoiceClips.H1))
    }

    @Test
    fun rentalArmNeedsTheUnlockAfterALongLock() {
        val arm = RentalArm()
        arm.observe(locked = true, occupied = true, parked = true, nowMs = 0, thresholdMs = 10_000, force = false)
        arm.observe(locked = false, occupied = true, parked = true, nowMs = 1_000, thresholdMs = 10_000, force = false)
        assertFalse(arm.primed())
        arm.observe(locked = true, occupied = true, parked = true, nowMs = 2_000, thresholdMs = 10_000, force = false)
        arm.observe(locked = true, occupied = true, parked = true, nowMs = 12_000, thresholdMs = 10_000, force = false)
        arm.observe(locked = false, occupied = null, parked = true, nowMs = 13_000, thresholdMs = 10_000, force = false)
        assertTrue(arm.takeEdge())
        assertFalse(arm.takeEdge())
        arm.reset()
        arm.observe(locked = true, occupied = true, parked = true, nowMs = 14_000, thresholdMs = 10_000, force = false)
        arm.observe(locked = true, occupied = true, parked = true, nowMs = 24_000, thresholdMs = 10_000, force = false)
        arm.observe(locked = false, occupied = true, parked = true, nowMs = 25_000, thresholdMs = 10_000, force = false)
        assertTrue(arm.takeEdge())
    }

    @Test
    fun settleWindowHidesEveryCardAndF1HasNoClip() {
        val session = CardSession()
        val waiting = session.step(baseInput(GearPosition.PARK, showIntake = true, edgesOpen = false))
        assertEquals(MiriPane.ICON, waiting.pane)
        val shown = session.step(baseInput(GearPosition.PARK, showIntake = true))
        assertEquals(MiriPane.F1, shown.pane)
        assertEquals(VoicePlay.None, shown.voice)
    }

    @Test
    fun d2ShowsOncePerRouteAndClosesWithTheCardTimer() {
        val session = CardSession()
        val face = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "route-9")
        val first = session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 0))
        assertEquals(MiriPane.D2, first.pane)
        assertTrue((first.voice as VoicePlay.Clips).keys.contains("vo_fuel_electric"))
        val closed = session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 6_000))
        assertEquals(MiriPane.ICON, closed.pane)
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face))
        val again = session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 8_000))
        assertEquals(MiriPane.ICON, again.pane)
    }

    @Test
    fun destinationChosenWhileDrivingSkipsTheParkCard() {
        val session = CardSession()
        val face = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "route-2")
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face))
        val parked = session.step(baseInput(GearPosition.PARK).copy(d2 = face))
        assertEquals(MiriPane.ICON, parked.pane)
    }

    @Test
    fun newRentalClearsTheD1Counter() {
        val session = CardSession()
        repeat(3) { index ->
            session.step(
                baseInput(GearPosition.PARK, edgesOpen = true).copy(
                    d1 = D1Face("route-$index", listOf("톨게이트 1곳"), "vo_toll_1"),
                    nowMs = index * 10_000L,
                ),
            )
            session.step(baseInput(GearPosition.DRIVE))
        }
        val blocked = session.step(
            baseInput(GearPosition.PARK, edgesOpen = true).copy(
                d1 = D1Face("route-3", listOf("톨게이트 1곳"), "vo_toll_1"),
            ),
        )
        assertEquals(MiriPane.ICON, blocked.pane)
        session.resetRental()
        val again = session.step(
            baseInput(GearPosition.PARK, edgesOpen = true).copy(
                d1 = D1Face("route-4", listOf("톨게이트 1곳"), "vo_toll_1"),
            ),
        )
        assertEquals(MiriPane.D1, again.pane)
    }

    @Test
    fun driverDoorOrDriveClearsTheRearNote() {
        val note = RearDoorNote()
        val open = PanelState(position = 1, readable = true)
        val closed = PanelState(position = 0, readable = true)
        assertEquals(CheckRules.REAR_NOTE, note.observe(mapOf(DoorId.ROW_2_LEFT to open)))
        assertNull(note.observe(mapOf(DoorId.ROW_2_LEFT to closed, DoorId.ROW_1_LEFT to open)))
        note.observe(mapOf(DoorId.ROW_2_RIGHT to open))
        assertNull(note.observe(mapOf(DoorId.ROW_2_RIGHT to closed), moving = true))
    }

    private fun evRearRight(combo: Boolean) = CarIdentity(
        fuels = listOf(FuelKind.ELECTRIC),
        evPort = PortSide.REAR_RIGHT,
        connectorDcCombo = combo,
    )

    private fun baseInput(
        gear: GearPosition,
        showIntake: Boolean = false,
        edgesOpen: Boolean = true,
    ) = DeckInput(
        gear = gear,
        visible = true,
        edgesOpen = edgesOpen,
        ownCar = false,
        kind = null,
        returnSaved = false,
        showIntake = showIntake,
        checkItems = emptyList(),
        checkNote = null,
        checkTitle = CheckRules.TITLE,
        reshowCheck = false,
        f4 = null,
        d2 = null,
        f3 = null,
        d1 = null,
        nowMs = 0L,
        cardMs = 6_000L,
        showGuide = false,
    )
}
