package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.CarFacts
import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.ConnectorCodes
import com.krazylabs.miri.domain.ConnectorProbeText
import com.krazylabs.miri.domain.DestinationSnapshot
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
    fun vhalCcs1AndCcs2AreDcCombo() {
        val identity = CarFacts.from(
            intArrayOf(CarFacts.FUEL_ELECTRIC),
            null,
            CarFacts.PORT_REAR_RIGHT,
            intArrayOf(ConnectorCodes.IEC_TYPE_1_CCS_DC),
        )
        assertTrue(identity.evOnly)
        assertEquals(PortSide.REAR_RIGHT, identity.evPort)
        assertTrue(identity.connectorDcCombo)
        assertEquals(setOf(5, 6), ConnectorCodes.DC_COMBO)
        assertTrue(combo(ConnectorCodes.IEC_TYPE_2_CCS_DC))
        assertFalse(combo(ConnectorCodes.IEC_TYPE_4_DC))
        assertFalse(combo(ConnectorCodes.IEC_TYPE_1_AC))
        assertFalse(combo(ConnectorCodes.IEC_TYPE_2_AC))
        assertFalse(combo(ConnectorCodes.IEC_TYPE_3_AC))
        assertFalse(combo(ConnectorCodes.TESLA_ROADSTER))
        assertFalse(combo(ConnectorCodes.UNKNOWN))
        assertFalse(combo(ConnectorCodes.OTHER))
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
    fun rawFourOmitsTheConnectorSentenceAndRawFiveAndSixSayDcCombo() {
        val chademo = D2Rules.offer(evFromConnector(4), DestinationRole.CHARGING)
        val ccs1 = D2Rules.offer(evFromConnector(5), DestinationRole.CHARGING)
        val ccs2 = D2Rules.offer(evFromConnector(6), DestinationRole.CHARGING)
        assertFalse(saysDcCombo(chademo))
        assertFalse(chademo?.chips?.contains("DC 콤보") == true)
        assertTrue(saysDcCombo(ccs1))
        assertTrue(ccs1?.chips?.contains("DC 콤보") == true)
        assertTrue(saysDcCombo(ccs2))
        assertTrue(ccs2?.chips?.contains("DC 콤보") == true)
        assertFalse(combo(4))
        assertTrue(combo(5))
        assertTrue(combo(6))
        assertFalse(combo(7))
    }

    @Test
    fun probeLinePrintsPropertyInfoManagerAndPleosSideBySide() {
        val line = ConnectorProbeText.line(
            propertyId = 289472775,
            propertyValues = intArrayOf(5),
            infoManagerValues = intArrayOf(4),
            pleos = "[IEC_TYPE_1_CCS_DC]",
        )
        assertTrue(line.contains("propertyId=289472775"))
        assertTrue(line.contains("CarPropertyManager=[5]"))
        assertTrue(line.contains("vhal=[CCS1]"))
        assertTrue(line.contains("CarInfoManager.getEvConnectorTypes()=[4]"))
        assertTrue(line.contains("java=[CCS1]"))
        assertTrue(line.contains("Pleos=[IEC_TYPE_1_CCS_DC]"))
        val missing = ConnectorProbeText.line(289472775, intArrayOf(5), null, "unavailable")
        assertTrue(missing.contains("CarInfoManager.getEvConnectorTypes()=unavailable"))
        assertTrue(missing.contains("java=n/a"))
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
        assertEquals("앞 왼쪽 창문이 열려 있어요.", lines[0].text)
        assertEquals(CheckKind.WINDOW, lines[0].kind)
        assertTrue(lines.any { it.kind == CheckKind.LIGHT && it.text == "실내등이 켜져 있어요." })
        assertTrue(lines.any { it.kind == CheckKind.LIGHT && it.text == "뒤쪽 왼쪽 독서등이 켜져 있어요." })
        val stoodIn = CheckRules.items(
            doors = mapOf(DoorId.ROW_2_RIGHT to open),
            windows = emptyMap(),
            cabinOn = true,
            reading = emptyMap(),
            lightsUseDoorStandIn = true,
        )
        assertEquals(listOf(doorItem()), stoodIn)
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
    fun checkOutranksIntakeAndAnUnshownD1Waits() {
        val session = CardSession()
        val step = session.step(
            baseInput(GearPosition.PARK).copy(
                showIntake = true,
                checkItems = listOf(doorItem()),
                d1 = D1Face("route-1", listOf("톨게이트 1곳"), "vo_toll_1"),
            ),
        )
        assertEquals(MiriPane.CHECK, step.pane)
        session.step(baseInput(GearPosition.DRIVE))
        val next = session.step(baseInput(GearPosition.PARK, edgesOpen = true).copy(
            d1 = D1Face("route-1", listOf("톨게이트 1곳"), "vo_toll_1"),
        ))
        assertEquals(MiriPane.D1, next.pane)
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
        val hourAndMinute = F3Rules.lines(
            ev = true,
            level = null,
            target = null,
            returnAtMs = 1_700_000_000_000L,
            nowMs = 1_700_000_000_000L - (61 * 60_000L + 59_000L),
            etaSeconds = null,
        )
        assertTrue(hourAndMinute[0].contains("1시간 1분 남았어요."))
        assertFalse(hourAndMinute[0].contains("1시간 2분"))
        assertFalse(hourAndMinute[0].contains("2시간"))
        val almostHour = F3Rules.lines(
            ev = true,
            level = null,
            target = null,
            returnAtMs = 1_700_000_000_000L,
            nowMs = 1_700_000_000_000L - (59 * 60_000L + 59_000L),
            etaSeconds = null,
        )
        assertTrue(almostHour[0].contains("59분 남았어요."))
        assertFalse(almostHour[0].contains("1시간"))
        val underAMinute = F3Rules.lines(
            ev = true,
            level = null,
            target = null,
            returnAtMs = 1_700_000_000_000L,
            nowMs = 1_700_000_000_000L - 59_000L,
            etaSeconds = null,
        )
        assertEquals("1분도 안 남았어요", underAMinute[0])
        assertFalse(underAMinute[0].contains("곧"))
        assertFalse(underAMinute[0].contains("0분"))
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
    fun destinationKeyStaysPutWhenTheRouteChanges() {
        val place = destination("충전소", 37.5012, 127.1001)
        val arrived = RouteKey.of(place, "route-1")
        assertEquals(arrived, RouteKey.of(place, ""))
        assertEquals(arrived, RouteKey.of(place, "route-9"))
        assertEquals(arrived, RouteKey.of(destination("충전소", 37.50124, 127.10014), "route-2"))
        assertEquals("route-only", RouteKey.of(null, "route-only"))
        assertEquals("", RouteKey.of(null, " "))
        val session = CardSession()
        val face = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = arrived)!!
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 0)).pane)
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face))
        val rerouted = face.copy(routeKey = RouteKey.of(place, "route-2"))
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = rerouted, nowMs = 1_000)).pane)
        val engine = DriveCueEngine()
        val first = engine.step(arrivalCue(arrived, remainMeters = 80))
        assertEquals(CueKind.CLIPS, first.kind)
        val again = engine.step(arrivalCue(RouteKey.of(place, "route-new"), remainMeters = 80))
        assertEquals(CueKind.NONE, again.kind)
    }

    @Test
    fun emptyD2KeyShowsOncePerRental() {
        val session = CardSession()
        val face = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "")!!
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 0)).pane)
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face))
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 1_000)).pane)
        val keyed = face.copy(routeKey = "route-9")
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = keyed, nowMs = 2_000)).pane)
        session.resetRental()
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face))
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = face, nowMs = 3_000)).pane)
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
    fun choosingAKindOpensF2WithoutAGearChange() {
        val session = CardSession()
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
        val next = session.step(baseInput(GearPosition.PARK).copy(kind = "rental"))
        assertEquals(MiriPane.F2, next.pane)
    }

    @Test
    fun clearingTheKindWhileF2IsOpenReturnsToF1() {
        val session = CardSession()
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
        assertEquals(MiriPane.F2, session.step(baseInput(GearPosition.PARK).copy(kind = "rental")).pane)
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
    }

    @Test
    fun newRentalOnTheFinishedCheckReturnsToF1() {
        val session = CardSession()
        val door = baseInput(GearPosition.PARK).copy(
            kind = "rental",
            returnSaved = true,
            checkItems = listOf(doorItem()),
        )
        assertEquals(MiriPane.CHECK, session.step(door).pane)
        val done = session.step(door.copy(checkItems = emptyList()))
        assertEquals(listOf(CheckRules.DONE), done.card?.lines)
        assertEquals(MiriPane.CHECK, session.step(door.copy(checkItems = emptyList(), nowMs = 1_000)).pane)
        session.resetRental()
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
    }

    @Test
    fun destinationArrivingAfterResumeStillArmsD2() {
        val session = CardSession()
        val parked = baseInput(GearPosition.PARK).copy(kind = "rental", returnSaved = true)
        assertEquals(MiriPane.ICON, session.step(parked.copy(visible = false)).pane)
        session.onResume()
        val charging = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-late")!!
        assertEquals(MiriPane.D2, session.step(parked.copy(d2 = charging, nowMs = 1_000)).pane)
    }

    @Test
    fun lateDestinationInTheSameParkDoesNotShowD2Again() {
        val session = CardSession()
        val route = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "route-1")!!
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = route, nowMs = 0)).pane)
        val late = route.copy(routeKey = RouteKey.of(destination("충전소", 37.5, 127.1), "route-1"))
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = late, nowMs = 1_000)).pane)
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = late, nowMs = 6_000)).pane)
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = late, nowMs = 7_000)).pane)
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = late))
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = late, nowMs = 8_000)).pane)
    }

    /**
     * Demo script order. After D1 closes, the charging route is set in that
     * same Park. The stop is already spent, so D2 waits through the drive and
     * opens at the next Park.
     */
    @Test
    fun demoOrderReachesD1ThenD2ThenF3ThenCheck() {
        val session = CardSession()
        val rental = baseInput(GearPosition.PARK).copy(kind = "rental", returnSaved = true)
        assertEquals(MiriPane.F1, session.step(baseInput(GearPosition.PARK, showIntake = true)).pane)
        assertEquals(MiriPane.F2, session.step(baseInput(GearPosition.PARK).copy(kind = "rental")).pane)
        assertEquals(MiriPane.ICON, session.step(rental.copy(nowMs = 1_000)).pane)

        val road = D1Face("pretend", listOf("톨게이트 2곳"), "vo_toll_2")
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, d1 = road))
        assertEquals(MiriPane.D1, session.step(rental.copy(d1 = road, nowMs = 2_000)).pane)
        assertEquals(MiriPane.ICON, session.step(rental.copy(d1 = road, nowMs = 8_000)).pane)

        val charging = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-1")!!
        assertEquals(MiriPane.ICON, session.step(rental.copy(d2 = charging, nowMs = 9_000)).pane)
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, d2 = charging))
        assertEquals(MiriPane.D2, session.step(rental.copy(d2 = charging, nowMs = 11_000)).pane)
        assertEquals(MiriPane.D2, session.step(rental.copy(d2 = charging, nowMs = 12_000)).pane)

        val prepared = F3Face("rent-3h", listOf("반납까지 약 18% 더 충전해야 해요."))
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true))
        assertEquals(MiriPane.F3, session.step(rental.copy(f3 = prepared, nowMs = 20_000)).pane)
        assertEquals(MiriPane.F3, session.step(rental.copy(f3 = null, nowMs = 21_000)).pane)

        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true))
        val check = session.step(
            rental.copy(checkItems = listOf(doorItem()), nowMs = 30_000),
        )
        assertEquals(MiriPane.CHECK, check.pane)
    }

    @Test
    fun chargeLineWaitsUntilRoadCardsHaveShown() {
        val session = CardSession()
        val line = "충전 62% · 반납 기준 80%"
        val rental = baseInput(GearPosition.PARK).copy(kind = "rental", returnSaved = true, f4 = chargeItem(line))
        val road = D1Face("pretend", listOf("톨게이트 2곳"), "vo_toll_2")
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, f4 = chargeItem(line)))
        assertEquals(MiriPane.D1, session.step(rental.copy(d1 = road, nowMs = 2_000)).pane)
        assertEquals(MiriPane.ICON, session.step(rental.copy(d1 = road, nowMs = 8_000)).pane)

        val charging = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-1")!!
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, f4 = chargeItem(line)))
        assertEquals(MiriPane.D2, session.step(rental.copy(d2 = charging, nowMs = 11_000)).pane)

        val prepared = F3Face("rent-3h", listOf("반납까지 약 18% 더 충전해야 해요."))
        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, f4 = chargeItem(line)))
        assertEquals(MiriPane.F3, session.step(rental.copy(f3 = prepared, f4 = chargeItem(line), nowMs = 20_000)).pane)

        session.step(baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true, f4 = chargeItem(line)))
        val checklist = session.step(rental.copy(nowMs = 30_000))
        assertEquals(MiriPane.CHECK, checklist.pane)
        assertEquals(listOf(line), checklist.card?.lines)

        val openDoor = CardSession()
        assertEquals(
            MiriPane.CHECK,
            openDoor.step(
                rental.copy(checkItems = listOf(doorItem()), f3 = prepared),
            ).pane,
        )
    }

    @Test
    fun chargingRouteSetWhileParkedShowsAtTheNextPark() {
        val session = CardSession()
        val charging = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-1")!!
        val road = D1Face("pretend", listOf("톨게이트 2곳"), "vo_toll_2")
        assertEquals(MiriPane.D1, session.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 0)).pane)
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 6_000)).pane)
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = charging, nowMs = 7_000)).pane)
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = charging))
        assertEquals(MiriPane.D2, session.step(baseInput(GearPosition.PARK).copy(d2 = charging, nowMs = 8_000)).pane)
    }

    @Test
    fun destinationChangedWhileDrivingSkipsTheParkCard() {
        val session = CardSession()
        val parked = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-1")!!
        val changed = parked.copy(routeKey = "charge-2")
        val road = D1Face("pretend", listOf("톨게이트 2곳"), "vo_toll_2")
        session.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 0))
        session.step(baseInput(GearPosition.PARK).copy(d2 = parked, nowMs = 6_000))
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = parked))
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = changed))
        assertEquals(MiriPane.ICON, session.step(baseInput(GearPosition.PARK).copy(d2 = changed, nowMs = 8_000)).pane)
        val cue = DriveCueEngine().step(arrivalCue("charge-2", remainMeters = 80))
        assertEquals(CueKind.CLIPS, cue.kind)
        assertEquals(listOf("vo_arrive_ev_soon"), cue.clips)
    }

    @Test
    fun d1RouteIsRecordedOnlyWhenTheCardIsShown() {
        val session = CardSession()
        val road = D1Face("route-1", listOf("톨게이트 2곳"), "vo_toll_2")
        val prepared = F3Face("rent-3h", listOf("반납까지 약 18% 더 충전해야 해요."))
        assertEquals(MiriPane.F3, session.step(baseInput(GearPosition.PARK).copy(d1 = road, f3 = prepared)).pane)
        session.step(baseInput(GearPosition.DRIVE))
        assertEquals(MiriPane.D1, session.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 1_000)).pane)

        val hidden = CardSession()
        assertEquals(MiriPane.ICON, hidden.step(baseInput(GearPosition.PARK).copy(d1 = road, visible = false)).pane)
        assertEquals(MiriPane.D1, hidden.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 1_000)).pane)

        val blocked = CardSession()
        assertEquals(
            MiriPane.CHECK,
            blocked.step(
                baseInput(GearPosition.PARK).copy(
                    d1 = road,
                    checkItems = listOf(doorItem()),
                ),
            ).pane,
        )
        blocked.step(baseInput(GearPosition.DRIVE))
        assertEquals(MiriPane.D1, blocked.step(baseInput(GearPosition.PARK).copy(d1 = road, nowMs = 1_000)).pane)
    }

    @Test
    fun chargingDestinationChosenWhileDrivingSkipsTheNextPark() {
        val session = CardSession()
        val face = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "charge-1")!!
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = face, kind = "rental", returnSaved = true))
        assertEquals(
            MiriPane.ICON,
            session.step(baseInput(GearPosition.PARK).copy(d2 = face, kind = "rental", returnSaved = true)).pane,
        )
    }

    /**
     * An extra stop, then a gear change, opens a park with no F3. Applying
     * rent-3h in that park must not record the key. The next D→P shows F3,
     * and the following tick (the offer goes null once the card is held)
     * keeps the card.
     */
    @Test
    fun f3AppliedAfterAnExtraStopShowsOnTheNextPark() {
        val session = CardSession()
        val rental = baseInput(GearPosition.PARK).copy(kind = "rental", returnSaved = true)
        val driving = baseInput(GearPosition.DRIVE).copy(kind = "rental", returnSaved = true)
        val f3 = F3Face("rent-3h", listOf("반납까지 약 18% 더 충전해야 해요."))
        assertEquals(MiriPane.ICON, session.step(rental.copy(nowMs = 0)).pane)
        session.step(driving)
        assertEquals(MiriPane.ICON, session.step(rental.copy(nowMs = 1_000)).pane)
        assertEquals(MiriPane.ICON, session.step(rental.copy(f3 = f3, nowMs = 1_200)).pane)
        assertEquals(MiriPane.ICON, session.step(rental.copy(f3 = f3, nowMs = 1_500)).pane)
        assertFalse(session.f3Shown().contains("rent-3h"))
        session.step(driving.copy(f3 = f3))
        assertEquals(MiriPane.F3, session.step(rental.copy(f3 = f3, nowMs = 2_000)).pane)
        assertTrue(session.f3Shown().contains("rent-3h"))
        assertEquals(MiriPane.F3, session.step(rental.copy(f3 = null, nowMs = 2_300)).pane)
    }

    @Test
    fun aDoorCloseChimesOnceAcrossTwoTicks() {
        val session = CardSession()
        val open = baseInput(GearPosition.PARK).copy(
            checkItems = listOf(doorItem(), lightItem()),
        )
        assertEquals(MiriPane.CHECK, session.step(open.copy(nowMs = 0)).pane)
        assertEquals(
            VoicePlay.Chime,
            session.step(open.copy(checkItems = listOf(lightItem()), nowMs = 250)).voice,
        )
        val finished = session.step(open.copy(checkItems = emptyList(), nowMs = 500))
        assertEquals(MiriPane.CHECK, finished.pane)
        assertEquals(VoicePlay.None, finished.voice)

        val later = CardSession()
        later.step(open.copy(nowMs = 0))
        assertEquals(
            VoicePlay.Chime,
            later.step(open.copy(checkItems = listOf(lightItem()), nowMs = 250)).voice,
        )
        assertEquals(
            VoicePlay.Chime,
            later.step(open.copy(checkItems = emptyList(), nowMs = 1_200)).voice,
        )
    }

    @Test
    fun aDoorCloseDoesNotChimeAgainWhenTheCardChanges() {
        val session = CardSession()
        val open = baseInput(GearPosition.PARK).copy(
            checkItems = listOf(doorItem()),
            f4 = chargeItem("충전 60% · 반납 기준 80%"),
            reshowCheck = true,
        )
        assertEquals(VoicePlay.Chime, session.step(open.copy(nowMs = 0)).voice)
        assertEquals(
            VoicePlay.Chime,
            session.step(open.copy(checkItems = emptyList(), nowMs = 5_000)).voice,
        )
        val followUp = session.step(
            open.copy(
                checkItems = emptyList(),
                f4 = chargeItem("충전 59% · 반납 기준 80%"),
                nowMs = 5_280,
            ),
        )
        assertEquals(MiriPane.CHECK, followUp.pane)
        assertEquals(VoicePlay.None, followUp.voice)
        assertEquals(
            VoicePlay.None,
            session.step(
                open.copy(
                    checkItems = emptyList(),
                    f4 = chargeItem("충전 58% · 반납 기준 80%"),
                    nowMs = 9_000,
                ),
            ).voice,
        )
        session.step(
            open.copy(
                checkItems = listOf(lightItem()),
                f4 = chargeItem("충전 58% · 반납 기준 80%"),
                nowMs = 10_000,
            ),
        )
        assertEquals(
            VoicePlay.Chime,
            session.step(open.copy(checkItems = emptyList(), f4 = chargeItem("충전 58% · 반납 기준 80%"), nowMs = 11_000)).voice,
        )
    }

    @Test
    fun statusBadgeFollowsTheItemKind() {
        assertEquals("열림", CheckRules.statusBadge(CheckKind.DOOR))
        assertEquals("열림", CheckRules.statusBadge(CheckKind.WINDOW))
        assertEquals("켜짐", CheckRules.statusBadge(CheckKind.LIGHT))
        assertEquals("부족", CheckRules.statusBadge(CheckKind.CHARGE))
        assertEquals("부족", CheckRules.statusBadge(CheckKind.FUEL))
        assertNull(CheckRules.chargeLine(ev = true, level = 80, target = 80))
        assertNull(CheckRules.chargeLine(ev = true, level = null, target = 80))
        assertEquals(CheckKind.CHARGE, CheckRules.chargeLine(ev = true, level = 62, target = 80)?.kind)
        assertEquals(CheckKind.FUEL, CheckRules.chargeLine(ev = false, level = 10, target = 40)?.kind)
    }

    @Test
    fun aChargePercentChangeDoesNotReplayTheCheckVoice() {
        val session = CardSession()
        val open = baseInput(GearPosition.PARK).copy(
            checkItems = listOf(doorItem()),
            f4 = chargeItem("충전 60% · 반납 기준 80%"),
        )
        assertEquals(VoicePlay.Clips(listOf("vo_check_done")), session.step(open.copy(nowMs = 0)).voice)
        assertEquals(
            VoicePlay.None,
            session.step(open.copy(f4 = chargeItem("충전 59% · 반납 기준 80%"), nowMs = 250)).voice,
        )

        val reshown = CardSession()
        val again = open.copy(reshowCheck = true)
        assertEquals(VoicePlay.Chime, reshown.step(again.copy(nowMs = 0)).voice)
        assertEquals(
            VoicePlay.None,
            reshown.step(again.copy(f4 = chargeItem("충전 59% · 반납 기준 80%"), nowMs = 250)).voice,
        )
    }

    @Test
    fun aDestinationKeyForTheSameRouteDoesNotConsumeD2() {
        val session = CardSession()
        val road = D1Face("pretend", listOf("톨게이트 2곳"), "vo_toll_2")
        val byRoute = D2Rules.offer(evRearRight(true), DestinationRole.CHARGING, routeKey = "route-9")!!
            .copy(routeId = "route-9")
        val byPlace = byRoute.copy(routeKey = "강남 충전소@37.5000,127.0300")
        assertEquals(
            MiriPane.CHECK,
            session.step(
                baseInput(GearPosition.PARK).copy(
                    d1 = road,
                    d2 = byRoute,
                    checkItems = listOf(doorItem()),
                ),
            ).pane,
        )
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = byRoute))
        session.step(baseInput(GearPosition.DRIVE).copy(d2 = byPlace))
        assertEquals(
            MiriPane.D2,
            session.step(baseInput(GearPosition.PARK).copy(d2 = byPlace, nowMs = 1_000)).pane,
        )

        val changed = CardSession()
        val other = byRoute.copy(routeKey = "route-10", routeId = "route-10")
        changed.step(
            baseInput(GearPosition.PARK).copy(
                d1 = road,
                d2 = byRoute,
                checkItems = listOf(doorItem()),
            ),
        )
        changed.step(baseInput(GearPosition.DRIVE).copy(d2 = byRoute))
        changed.step(baseInput(GearPosition.DRIVE).copy(d2 = other))
        assertEquals(
            MiriPane.ICON,
            changed.step(baseInput(GearPosition.PARK).copy(d2 = other, nowMs = 1_000)).pane,
        )
    }

    @Test
    fun newRentalClearsDemoNumbersAndVoice() {
        val cleared = ScenarioOverrides(
            remainMeters = 400,
            remainSeconds = 90,
            returnInMinutes = 120,
            chargeTargetPercent = 80,
            pretendCharging = true,
            pretendTolls = 2,
            pretendHighways = 1,
            armNewRental = true,
            speechUsage = "navigation",
            gearBackend = "aaos",
            lightsUseDoorStandIn = true,
        ).clearedForNewRental()
        assertNull(cleared.remainMeters)
        assertNull(cleared.remainSeconds)
        assertNull(cleared.returnInMinutes)
        assertNull(cleared.chargeTargetPercent)
        assertFalse(cleared.pretendCharging)
        assertNull(cleared.pretendTolls)
        assertNull(cleared.pretendHighways)
        assertFalse(cleared.armNewRental)
        assertEquals("", cleared.speechUsage)
        assertEquals("aaos", cleared.gearBackend)
        assertTrue(cleared.lightsUseDoorStandIn)
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

    private fun destination(name: String, latitude: Double, longitude: Double) = DestinationSnapshot(
        name = name,
        address = "",
        latitude = latitude,
        longitude = longitude,
        remainDistance = 0,
        remainTime = 0,
        tollFare = 0,
    )

    private fun doorItem(text: String = "오른쪽 뒷문이 열려 있어요.") = CheckItem(CheckKind.DOOR, text)

    private fun lightItem(text: String = "실내등이 켜져 있어요.") = CheckItem(CheckKind.LIGHT, text)

    private fun chargeItem(text: String) = CheckItem(CheckKind.CHARGE, text)

    private fun arrivalCue(routeId: String, remainMeters: Int) = DriveCueInput(
        nowMs = 0,
        driveGear = true,
        d2bEligible = true,
        routeId = routeId,
        remainMeters = remainMeters,
        d2bClips = listOf("vo_arrive_ev_soon"),
    )

    private fun saysDcCombo(face: D2Face?): Boolean =
        face?.clips.orEmpty().any { VoiceClips.spoken(it)?.contains("디씨 콤보") == true }

    private fun combo(code: Int): Boolean =
        CarFacts.from(intArrayOf(CarFacts.FUEL_ELECTRIC), null, CarFacts.PORT_REAR_RIGHT, intArrayOf(code))
            .connectorDcCombo

    private fun evFromConnector(code: Int): CarIdentity = CarFacts.from(
        intArrayOf(CarFacts.FUEL_ELECTRIC),
        null,
        CarFacts.PORT_REAR_RIGHT,
        intArrayOf(code),
    )

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
