package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.HazardDetector
import com.krazylabs.miri.domain.TurnLamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DriveCueTest {
    private val holdMs = 8_000L

    @Test
    fun hazardDoesNotPlayTheTurnChime() {
        val engine = DriveCueEngine()
        val hazard = TurnLamps(left = true, right = true)
        val cue = engine.step(drive(now = 30_000, lamps = hazard))

        assertEquals(CueKind.NONE, cue.kind)
        assertEquals(0, cue.turnSpeeches)
        assertEquals(0, engine.turnSpeeches)
    }

    @Test
    fun hazardResetsTheSingleSideTimer() {
        val engine = DriveCueEngine()
        engine.step(drive(now = 0, lamps = TurnLamps(left = true)))
        engine.step(drive(now = 7_000, lamps = TurnLamps(left = true, right = true)))
        val cue = engine.step(drive(now = 8_000, lamps = TurnLamps(left = true)))

        assertEquals(CueKind.NONE, cue.kind)
        assertEquals(0, engine.turnSpeeches)
    }

    @Test
    fun leftBlinkPastTheDemoHoldPlaysAChime() {
        val engine = DriveCueEngine()
        val left = TurnLamps(left = true)
        engine.step(drive(now = 0, lamps = left))
        val cue = engine.step(drive(now = holdMs, lamps = left))

        assertEquals(CueKind.CHIME, cue.kind)
        assertTrue(cue.clips.isEmpty())
        assertEquals(1, cue.turnSpeeches)
    }

    @Test
    fun navVoiceSkipsTheChimeWithoutCountingIt() {
        val engine = DriveCueEngine()
        val left = TurnLamps(left = true)
        engine.step(drive(now = 0, lamps = left))
        val skipped = engine.step(drive(now = holdMs, lamps = left, navVoice = true))
        val later = engine.step(drive(now = holdMs + 1_000, lamps = left, navVoice = false))

        assertEquals(CueKind.NONE, skipped.kind)
        assertEquals(CueKind.NONE, later.kind)
        assertEquals(0, engine.turnSpeeches)
    }

    @Test
    fun thirdHoldDoesNotChime() {
        val engine = DriveCueEngine()
        chime(engine, start = 0)
        chime(engine, start = 20_000)
        engine.step(drive(now = 40_000, lamps = TurnLamps(left = true)))
        val third = engine.step(drive(now = 48_000, lamps = TurnLamps(left = true)))

        assertEquals(CueKind.NONE, third.kind)
        assertEquals(2, engine.turnSpeeches)
    }

    @Test
    fun overlappingTurnChimeAndArrivalPlaysOnlyArrival() {
        val engine = DriveCueEngine()
        val left = TurnLamps(left = true)
        val clips = listOf("vo_arrive_ev_soon", "vo_charge_port_rear_right")
        engine.step(drive(now = 0, lamps = left))
        val cue = engine.step(
            drive(
                now = holdMs,
                lamps = left,
                routeId = "route-1",
                d2bEligible = true,
                remainMeters = 400,
                d2bClips = clips,
            ),
        )
        val after = engine.step(
            drive(
                now = holdMs + 500,
                lamps = left,
                routeId = "route-1",
                d2bEligible = true,
                remainMeters = 380,
                d2bClips = clips,
            ),
        )

        assertEquals(CueKind.CLIPS, cue.kind)
        assertEquals(clips, cue.clips)
        assertEquals(0, cue.turnSpeeches)
        assertEquals(CueKind.NONE, after.kind)
        assertEquals(0, engine.turnSpeeches)
    }

    @Test
    fun navStillSpeakingAfterFiveSecondsOmitsArrival() {
        val engine = DriveCueEngine()
        val first = engine.step(arrival(now = 0, navVoice = true, remainMeters = 400))
        val later = engine.step(arrival(now = 5_000, navVoice = true, remainMeters = 300))
        val after = engine.step(arrival(now = 6_000, navVoice = false, remainMeters = 280))

        assertEquals(CueKind.NONE, first.kind)
        assertEquals(CueKind.NONE, later.kind)
        assertEquals(CueKind.NONE, after.kind)
    }

    @Test
    fun navEndingEarlyStillRequiresOneHundredFiftyMeters() {
        val engine = DriveCueEngine()
        engine.step(arrival(now = 0, navVoice = true, remainMeters = 400))
        val spoken = engine.step(arrival(now = 1_000, navVoice = false, remainMeters = 200))
        assertEquals(CueKind.CLIPS, spoken.kind)

        val quiet = DriveCueEngine()
        quiet.step(arrival(now = 0, navVoice = true, remainMeters = 180))
        val omitted = quiet.step(arrival(now = 1_000, navVoice = false, remainMeters = 100))
        assertEquals(CueKind.NONE, omitted.kind)
    }

    @Test
    fun arrivalWithoutNavVoiceDoesNotApplyTheFloor() {
        val engine = DriveCueEngine()
        val cue = engine.step(arrival(now = 0, navVoice = false, remainMeters = 80))
        assertEquals(CueKind.CLIPS, cue.kind)
    }

    @Test
    fun alternatingSidesInsideTheBlinkWindowIsHazard() {
        val detector = HazardDetector()
        detector.observe("LEFT", 0)
        val lamps = detector.observe("RIGHT", 400)

        assertTrue(lamps.hazard)
        val blinked = HazardDetector()
        blinked.observe("LEFT", 0)
        assertFalse(blinked.observe("OFF", 100).hazard)
        assertTrue(blinked.observe("RIGHT", 500).hazard)
        val slow = HazardDetector()
        slow.observe("LEFT", 0)
        slow.observe("OFF", 100)
        assertFalse(slow.observe("RIGHT", 2_000).hazard)
        val engine = DriveCueEngine()
        engine.step(drive(now = 0, lamps = lamps))
        val cue = engine.step(drive(now = holdMs, lamps = lamps))
        assertEquals(CueKind.NONE, cue.kind)
    }

    private fun chime(engine: DriveCueEngine, start: Long) {
        val left = TurnLamps(left = true)
        engine.step(drive(now = start, lamps = TurnLamps()))
        engine.step(drive(now = start + 1, lamps = left))
        val cue = engine.step(drive(now = start + 1 + holdMs, lamps = left))
        assertEquals(CueKind.CHIME, cue.kind)
    }

    private fun arrival(now: Long, navVoice: Boolean, remainMeters: Int) = drive(
        now = now,
        lamps = TurnLamps(),
        navVoice = navVoice,
        routeId = "route-1",
        d2bEligible = true,
        remainMeters = remainMeters,
        d2bClips = listOf("vo_arrive_ev_soon", "vo_charge_port_rear_right"),
    )

    private fun drive(
        now: Long,
        lamps: TurnLamps,
        navVoice: Boolean = false,
        routeId: String = "",
        d2bEligible: Boolean = false,
        remainMeters: Int? = null,
        d2bClips: List<String> = emptyList(),
    ) = DriveCueInput(
        nowMs = now,
        driveGear = true,
        lamps = lamps,
        navVoice = navVoice,
        holdMs = holdMs,
        routeId = routeId,
        d2bEligible = d2bEligible,
        remainMeters = remainMeters,
        d2bClips = d2bClips,
    )
}

class TodayRoadTest {
    @Test
    fun zeroTollsUsesHighwayClipAndNotG0() {
        assertEquals(VoiceClips.H1, TodayRoad.voiceKey(tolls = 0, highways = 2, parked = true))
        assertNull(TodayRoad.tollKey(0))
        assertFalse(VoiceClips.names.contains("vo_toll_0"))
        assertEquals(
            "오늘 길엔 고속도로 진입이 있어요. 출발 전에 운전 보조 버튼 위치를 봐 두세요.",
            VoiceClips.H1_TEXT,
        )
    }

    @Test
    fun highwayClipDoesNotPlayWhileDriving() {
        assertNull(TodayRoad.voiceKey(tolls = 0, highways = 3, parked = false))
    }

    @Test
    fun bothZeroHasNoVoice() {
        assertNull(TodayRoad.voiceKey(tolls = 0, highways = 0, parked = true))
    }

    @Test
    fun tollCountUsesG1ThroughMany() {
        assertEquals("vo_toll_1", TodayRoad.voiceKey(1, 4, parked = true))
        assertEquals("vo_toll_many", TodayRoad.tollKey(12))
    }
}

class VoiceClipCatalogTest {
    @Test
    fun catalogStaysAtTwentyNineWithoutTurnSignalSlots() {
        assertEquals(29, VoiceClips.names.size)
        assertEquals(29, VoiceClips.names.toSet().size)
        assertTrue(VoiceClips.names.none { it.contains("turn_signal") })
        assertTrue(VoiceClips.contains(VoiceClips.H1))
    }
}

class ReturnFormTest {
    @Test
    fun defaultsAreTodayPickupAndEmptyTime() {
        val draft = F2Draft()

        assertEquals(0, draft.dayOffset)
        assertEquals(F2Draft.PLACE_PICKUP, draft.placeId)
        assertEquals(F2Draft.PICKUP_LABEL, "대여한 곳")
        assertNull(draft.minuteOfDay)
        assertFalse(draft.doneEnabled)
        assertEquals("다 됐어요", F2Draft.DONE)
        assertEquals("시각을 고르면 누를 수 있어요.", F2Draft.DONE_HINT)
    }

    @Test
    fun doneStaysDisabledUntilATimeIsChosen() {
        val form = ReturnForm()
        assertFalse(form.confirm())
        assertNull(form.saved)

        form.selectTime(9 * 60 + 30)

        assertTrue(form.draft.doneEnabled)
        assertTrue(form.confirm())
        assertEquals(9 * 60 + 30, form.saved?.minuteOfDay)
        assertEquals(F2Draft.PLACE_PICKUP, form.saved?.placeId)
    }

    @Test
    fun timeChipsAreThirtyMinutes() {
        val options = F2Draft.timeOptions()
        assertEquals(48, options.size)
        assertEquals(0, options.first())
        assertEquals(23 * 60 + 30, options.last())
        assertTrue(options.zipWithNext().all { (a, b) -> b - a == 30 })
    }

    @Test
    fun dateSheetListsSevenDaysIncludingToday() {
        val today = LocalDate.of(2026, 10, 24)
        val offsets = F2Draft.dateOffsets().toList()
        assertEquals(7, offsets.size)
        assertEquals("10/24 (토)", dateLabel(today, 0))
        assertEquals("10/30 (금)", dateLabel(today, 6))

        val form = ReturnForm()
        form.openDateSheet()
        assertTrue(form.draft.dateSheetOpen)
        form.selectDate(2)
        assertFalse(form.draft.dateSheetOpen)
        assertEquals(2, form.draft.dayOffset)
    }

    @Test
    fun savedDateStaysOnTheChosenDay() {
        val today = LocalDate.of(2026, 10, 24)
        val form = ReturnForm()
        form.selectDate(1)
        form.selectTime(10 * 60)
        assertTrue(form.confirm(today))
        val saved = form.saved
        assertEquals(LocalDate.of(2026, 10, 25).toEpochDay(), saved?.epochDay)
        form.restore(saved!!.epochDay, saved.minuteOfDay, saved.placeId, today.plusDays(1))
        assertEquals(LocalDate.of(2026, 10, 25).toEpochDay(), form.saved?.epochDay)
        assertEquals(0, form.draft.dayOffset)
    }

    @Test
    fun closingBeforeConfirmDoesNotSave() {
        val form = ReturnForm()
        form.selectTime(18 * 60)
        form.closedWithoutConfirm()

        assertNull(form.saved)
        assertNull(form.draft.minuteOfDay)
        assertEquals(0, form.draft.dayOffset)
    }
}

class MiriPaneTest {
    @Test
    fun parkedRentalWithoutATimeShowsF2() {
        assertEquals(
            MiriPane.F2,
            miriPane(GearPosition.PARK, ownCar = false, kindChosen = true, returnSaved = false),
        )
        assertEquals(
            MiriPane.ICON,
            miriPane(GearPosition.DRIVE, ownCar = false, kindChosen = true, returnSaved = false),
        )
    }
}

class TbtGuardTest {
    @Test
    fun threeEmptyListsKeepThePreviousSteps() {
        val guard = TbtGuard()
        guard.accept(listOf("TURN_TOLL", "TURN_ENTER_IC_R"), routing = true)
        repeat(3) {
            assertEquals(listOf("TURN_TOLL", "TURN_ENTER_IC_R"), guard.accept(emptyList(), routing = true))
        }
        assertEquals(listOf("TURN_TOLL", "TURN_ENTER_IC_R"), guard.accept(emptyList(), routing = true))
    }

    @Test
    fun stepsArrivingBeforeRouteStateAreKept() {
        val guard = TbtGuard()
        assertEquals(listOf("TURN_TOLL"), guard.accept(listOf("TURN_TOLL"), routing = false))
        assertEquals(listOf("TURN_TOLL"), guard.accept(emptyList(), routing = true))
    }

    @Test
    fun routingFalseClearsTheList() {
        val guard = TbtGuard()
        guard.accept(listOf("TURN_TOLL"), routing = true)
        assertEquals(emptyList<String>(), guard.accept(emptyList(), routing = false))
    }
}
