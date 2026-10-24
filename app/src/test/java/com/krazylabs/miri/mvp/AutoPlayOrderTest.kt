package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.PortSide
import com.krazylabs.miri.ui.design.f3Model
import com.krazylabs.miri.ui.design.visibleReturnMinutes
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlayOrderTest {
    private val clocks = listOf(8 * 60, 18 * 60, 23 * 60 + 30)

    @Test
    fun theWallClockChipIsSoonEnoughToOpenF3() {
        clocks.forEach { now ->
            val today = visibleReturnMinutes(0, now)
            val ahead = today.firstOrNull()?.minus(now) ?: (24 * 60 - now)
            assertTrue("$now is $ahead minutes out", ahead in 1..180)
        }
    }

    @Test
    fun autoPlayOrderIgnoresTheClockAndARealCharger() {
        val expected = listOf("F1", "F2", "D1", "D2", "F3", "C1", "C3", "C2")
        clocks.forEach { now ->
            assertEquals(now.toString(), expected, walk(now).beats)
        }
    }

    @Test
    fun aRealChargerTakesTheFirstParkWhenTheDemoFeedIsOff() {
        val session = CardSession()
        session.step(deck(GearPosition.PARK, kind = null, saved = false, d1 = null, d2 = null, f3 = null))
        val feed = AutoPlayFeed.resolve(
            playing = false,
            pretendCharging = false,
            returnInMinutes = null,
            scenarioRemainMeters = null,
            scenarioRemainSeconds = null,
            realCharging = true,
            realRouteId = "charger-route",
            realRouteKey = "강남 충전소@37.5056,127.0539",
            realTolls = 0,
            realHighways = 0,
            realRouting = true,
            realRemainMeters = 80,
            realRemainSeconds = 20,
            formMinutesLeft = 20L,
        )
        assertTrue(feed.charging)
        assertEquals(20L, feed.f3MinutesLeft)
        assertEquals(80, feed.remainMeters)
        val identity = ev()
        val d2 = D2Rules.offer(identity, DestinationRole.CHARGING, routeKey = feed.routeKey)
            ?.copy(routeId = feed.routeId)
        val parked = session.step(
            deck(
                gear = GearPosition.PARK,
                kind = "rental",
                saved = true,
                d1 = TodayRoad.face("charger-route", true, 0, 0, 2, 1),
                d2 = d2,
                f3 = F3Face("rent-3h", listOf("곧 반납")),
            ),
        )
        assertEquals(MiriPane.D2, parked.pane)
    }

    @Test
    fun departValuesComeFromTheDemoEta() {
        val now = 1_000_000L
        val back = now + 120 * 60_000L
        val lines = F3Rules.lines(
            ev = true,
            level = 90,
            target = 80,
            returnAtMs = back,
            nowMs = now,
            etaSeconds = F3Rules.DEMO_ETA_SECONDS,
        )
        val model = f3Model(lines)
        assertEquals("반납", model.leftLabel)
        assertEquals("가는 데", model.rightLabel)
        assertFalse(model.leftValue.isNullOrBlank())
        assertEquals("약 42분", model.rightValue)
        assertTrue(model.sentence.contains("출발하면"))
        val depart = F3Rules.departAtMs(back, F3Rules.DEMO_ETA_SECONDS)
        assertEquals(back - F3Rules.DEMO_ETA_SECONDS * 1000L - F3Rules.DEPART_BUFFER_MS, depart)
    }

    @Test
    fun demoDepartureIsNotTheWallClock() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 10, 24, 20, 45, 0, 0, zone).toInstant().toEpochMilli()
        val back = now + 120 * 60_000L
        val lines = F3Rules.lines(
            ev = true,
            level = 85,
            target = 80,
            returnAtMs = back,
            nowMs = now,
            etaSeconds = F3Rules.DEMO_ETA_SECONDS,
            zone = zone,
        )
        val model = f3Model(lines)
        assertEquals("22:45", model.leftValue)
        assertEquals("약 42분", model.rightValue)
        assertEquals("21:48", model.hero)
        assertFalse(model.hero == "20:45")
        assertEquals("반납", model.leftLabel)
        assertEquals("가는 데", model.rightLabel)
    }

    @Test
    fun aDepartLineWithoutSideValuesLeavesNoEmptyLabels() {
        val model = f3Model(listOf("20:45쯤 출발하면 여유 있게 도착해요."))
        assertEquals("20:45", model.hero)
        assertNull(model.leftLabel)
        assertNull(model.rightLabel)
        assertTrue(model.leftValue.isNullOrBlank())
        assertTrue(model.rightValue.isNullOrBlank())
    }

    @Test
    fun filmingF3UsesTheSameReturnChipAsF2() {
        val zone = ZoneId.of("Asia/Seoul")
        val today = java.time.LocalDate.of(2026, 10, 24)
        val summary = com.krazylabs.miri.ui.design.returnSummary(today, 2, null, 12 * 60)
        assertEquals("10/26 12:00", summary)
        val at = F3Rules.savedReturnAtMs(today.plusDays(2).toEpochDay(), 12 * 60, zone)
        val now = java.time.ZonedDateTime.of(2026, 10, 24, 22, 47, 0, 0, zone).toInstant().toEpochMilli()
        val lines = F3Rules.lines(
            ev = true,
            level = 80,
            target = 80,
            returnAtMs = at,
            nowMs = now,
            etaSeconds = F3Rules.DEMO_ETA_SECONDS,
            zone = zone,
        )
        val model = f3Model(lines)
        assertEquals("12:00", model.leftValue)
        assertEquals("11:03", model.hero)
        assertEquals("약 42분", model.rightValue)
        assertEquals("반납", model.leftLabel)
        assertFalse(model.hero == "22:47")
        assertFalse(model.leftValue == "00:47")
        val depart = F3Rules.departAtMs(at, F3Rules.DEMO_ETA_SECONDS)
        assertEquals(at - 42 * 60_000L - F3Rules.DEPART_BUFFER_MS, depart)
    }

    @Test
    fun aMissingTripUsesTheFallbackCopy() {
        val zone = ZoneId.of("Asia/Seoul")
        val back = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 10, 24, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        val lines = F3Rules.lines(
            ev = true,
            level = null,
            target = null,
            returnAtMs = back,
            nowMs = now,
            etaSeconds = null,
            zone = zone,
        )
        val model = f3Model(lines)
        assertEquals("반납까지", model.label)
        assertEquals("3", model.hero)
        assertEquals("시간", model.heroRest)
        assertEquals("반납 20:00까지 3시간 남았어요.", model.sentence)
        assertNull(model.leftLabel)
        assertNull(model.rightLabel)
    }

    private fun walk(nowMinute: Int): WalkResult {
        val session = CardSession()
        val cue = DriveCueEngine()
        val identity = ev()
        var now = 10_000L + nowMinute
        var gear = GearPosition.PARK
        var kind: String? = null
        var saved = false
        var tolls: Int? = null
        var highways: Int? = null
        var charging = false
        var returnInMinutes: Int? = null
        var remainSeconds: Int? = null
        val target = 80
        var level = 90
        var door = false
        var drove = false
        var epilogue: String? = null
        val beats = mutableListOf<String>()
        var d2Voices = 0
        var arrivalCues = 0
        val trip = "이번 대여에서 120km를 달렸어요. 수고하셨어요."
        var c3Line: String? = null

        fun tick() {
            if (gear == GearPosition.DRIVE) drove = true
            val feed = AutoPlayFeed.resolve(
                playing = true,
                pretendCharging = charging,
                returnInMinutes = returnInMinutes,
                scenarioRemainMeters = null,
                scenarioRemainSeconds = remainSeconds,
                realCharging = true,
                realRouteId = "charger-route",
                realRouteKey = "강남 충전소@37.5056,127.0539",
                realTolls = 4,
                realHighways = 4,
                realRouting = true,
                realRemainMeters = 80,
                realRemainSeconds = 20,
                formMinutesLeft = 20L,
            )
            val role = if (feed.charging) DestinationRole.CHARGING else DestinationRole.OTHER
            val offered = if (role == DestinationRole.CHARGING) {
                D2Rules.offer(identity, role, routeKey = feed.routeKey)?.copy(routeId = feed.routeId)
            } else {
                null
            }
            val d2 = AutoPlayFeed.cardD2(playing = true, gear = gear, face = offered)
            val heard = cue.step(
                DriveCueInput(
                    nowMs = now,
                    driveGear = gear == GearPosition.DRIVE,
                    routeId = feed.routeKey,
                    d2bEligible = d2 != null && d2.arrivalClips.isNotEmpty(),
                    remainMeters = feed.remainMeters,
                    remainSeconds = feed.remainSeconds,
                    d2bClips = d2?.arrivalClips.orEmpty(),
                ),
            )
            if (heard.kind != CueKind.NONE) arrivalCues += 1
            val d1 = if (role == DestinationRole.OTHER) {
                TodayRoad.face("", false, 0, 0, tolls, highways)
            } else {
                null
            }
            val minutes = feed.f3MinutesLeft
            val f3 = if (kind != null && saved && minutes != null) {
                val at = now + minutes * 60_000L
                F3Rules.dueKey(kind, minutes, session.f3Shown())?.let { key ->
                    F3Face(key, F3Rules.lines(true, level, target, at, now, feed.remainSeconds))
                }
            } else {
                null
            }
            val moving = gear == GearPosition.DRIVE
            val items = if (drove && !moving && door) {
                listOf(CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요."))
            } else {
                emptyList()
            }
            val step = session.step(
                deck(
                    gear = gear,
                    kind = kind,
                    saved = saved,
                    d1 = d1,
                    d2 = d2,
                    f3 = f3,
                    nowMs = now,
                    checkItems = items,
                    f4 = if (drove && !moving) CheckRules.chargeLine(true, level, target) else null,
                    tripLine = trip,
                    epilogue = epilogue,
                    showIntake = kind == null,
                ),
            )
            val beat = beatOf(step)
            if (beat != null && beats.lastOrNull() != beat) beats += beat
            if (beat == "C3") c3Line = step.card?.lines?.firstOrNull { it.contains("km") }
            if (step.pane == MiriPane.D2 && step.voice is VoicePlay.Clips) d2Voices += 1
            now += 1_000L
        }

        tick()
        kind = "rental"
        tick()
        saved = true
        tick()
        tolls = 2
        highways = 1
        gear = GearPosition.DRIVE
        tick()
        gear = GearPosition.PARK
        tick()
        tolls = null
        highways = null
        charging = true
        gear = GearPosition.DRIVE
        tick()
        gear = GearPosition.PARK
        tick()
        charging = false
        gear = GearPosition.DRIVE
        tick()
        returnInMinutes = 120
        remainSeconds = F3Rules.DEMO_ETA_SECONDS
        tick()
        gear = GearPosition.PARK
        tick()
        gear = GearPosition.DRIVE
        tick()
        door = true
        level = 60
        gear = GearPosition.PARK
        tick()
        door = false
        tick()
        epilogue = "C3"
        level = 90
        tick()
        epilogue = "C2"
        tick()

        assertEquals(0, arrivalCues)
        assertEquals(1, d2Voices)
        assertEquals(trip, c3Line)
        return WalkResult(beats)
    }

    private fun beatOf(step: DeckStep): String? {
        val card = step.card ?: return null
        return when (step.pane) {
            MiriPane.F1 -> "F1"
            MiriPane.F2 -> "F2"
            MiriPane.D1 -> "D1"
            MiriPane.D2 -> "D2"
            MiriPane.F3 -> "F3"
            MiriPane.CHECK -> when {
                card.title.contains("다녀왔어요") -> "C3"
                card.lines.any { it.contains("점검 끝") } -> "C2"
                else -> "C1"
            }
            MiriPane.ICON -> null
        }
    }

    private fun ev() = CarIdentity(
        fuels = listOf(FuelKind.ELECTRIC),
        evPort = PortSide.REAR_RIGHT,
    )

    private fun deck(
        gear: GearPosition,
        kind: String?,
        saved: Boolean,
        d1: D1Face?,
        d2: D2Face?,
        f3: F3Face?,
        nowMs: Long = 0L,
        checkItems: List<CheckItem> = emptyList(),
        f4: CheckItem? = null,
        tripLine: String? = null,
        epilogue: String? = null,
        showIntake: Boolean = kind == null,
    ) = DeckInput(
        gear = gear,
        visible = true,
        edgesOpen = true,
        ownCar = false,
        kind = kind,
        returnSaved = saved,
        showIntake = showIntake,
        checkItems = checkItems,
        checkNote = null,
        checkTitle = CheckRules.TITLE,
        reshowCheck = false,
        f4 = f4,
        d2 = d2,
        f3 = f3,
        d1 = d1,
        nowMs = nowMs,
        cardMs = 20_000L,
        showGuide = false,
        tripLine = tripLine,
        epilogue = epilogue,
    )

    private data class WalkResult(val beats: List<String>)
}
