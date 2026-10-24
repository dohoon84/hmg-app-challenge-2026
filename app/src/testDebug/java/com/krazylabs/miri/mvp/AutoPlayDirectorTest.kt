package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.DoorId
import kotlin.math.roundToInt
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.vehicle.ScriptedCabinFeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayDirectorTest {
    @Test
    fun theScriptOrderLastsNinetySeconds() {
        assertEquals(
            listOf("F1", "F2", "D1", "D2", "F3", "C1", "C3", "C2"),
            AutoPlayScript.beats(),
        )
        assertEquals(90_000L, AutoPlayScript.totalMs())
        val roads = AutoPlayScript.steps.filterIsInstance<AutoStep.Road>()
        assertEquals(2, roads.first().tolls)
        assertEquals(1, roads.first().highways)
        assertFalse(roads.first().charging)
        assertTrue(roads.last().charging)
        assertEquals(null, roads.last().tolls)
        assertTrue(AutoPlayScript.steps.any { it is AutoStep.ReturnWindow })
        assertTrue(AutoPlayScript.steps.any { it is AutoStep.Rewind })
        assertEquals(3_000L, AutoPlayScript.steps.last { AutoPlayScript.beatOf(it) == "C2" }.ms)
        assertEquals(7_000L, AutoPlayScript.steps.last { AutoPlayScript.beatOf(it) == "C3" }.ms)
    }

    @Test
    fun theReturnWindowFillsTheDepartValues() = runTest {
        val host = RecordingHost()
        val director = AutoPlayDirector.forTest(ScriptedCabinFeed()) {}
        director.play(host, this, GearPosition.PARK)
        advanceUntilIdle()
        val window = host.applied.last { it.returnInMinutes == 120 }
        assertEquals(F3Rules.DEMO_ETA_SECONDS, window.remainSeconds)
        assertEquals(80, window.chargeTargetPercent)
        assertTrue(host.rewinds >= 2)
    }

    @Test
    fun theStandInReplacesGearDoorAndSocThenGivesThemBack() {
        val cabin = ScriptedCabinFeed()
        val live = VehicleSnapshot(
            socPercent = 80,
            socReadable = false,
            identity = CarIdentity(fuels = listOf(FuelKind.ELECTRIC)),
        )
        assertEquals(live, cabin.overlay(live))
        assertEquals(GearPosition.DRIVE, cabin.gear(GearPosition.DRIVE))
        cabin.engage()
        cabin.gear = GearPosition.DRIVE
        cabin.rearDoorOpen = true
        cabin.soc = 60
        assertEquals(GearPosition.DRIVE, cabin.gear(GearPosition.PARK))
        val over = cabin.overlay(live)
        assertEquals(60, over.socPercent)
        assertTrue(over.socReadable)
        assertEquals(ScriptedCabinFeed.START_KM, over.odometerKm)
        assertTrue(over.odometerReadable)
        assertTrue(over.doors.getValue(DoorId.ROW_2_RIGHT).open)
        assertFalse(over.doors.getValue(DoorId.ROW_1_LEFT).open)
        assertEquals(live.identity, over.identity)
        cabin.release()
        assertFalse(cabin.engaged)
        assertEquals(live, cabin.overlay(live))
        assertEquals(GearPosition.PARK, cabin.gear(GearPosition.PARK))
    }

    @Test
    fun stopAndASecondPlayRestoreThenEngageAgain() = runTest {
        val cabin = ScriptedCabinFeed()
        val host = RecordingHost()
        lateinit var director: AutoPlayDirector
        var seen = 0
        var stopAt = 2
        director = AutoPlayDirector.forTest(cabin) {
            seen += 1
            yield()
            if (seen == stopAt) {
                director.stop()
                yield()
            }
        }
        director.play(host, this, GearPosition.PARK)
        advanceUntilIdle()
        assertFalse(cabin.engaged)
        assertFalse(director.running.value)
        assertTrue(host.clears >= 1)
        assertTrue(host.rewinds >= 1)

        val clearsAfterStop = host.clears
        stopAt = seen + 12
        director.play(host, this, GearPosition.PARK)
        advanceUntilIdle()
        assertTrue(host.rewinds >= 2)
        assertFalse(cabin.engaged)
        assertTrue(host.clears > clearsAfterStop)
        assertTrue(host.applied.all { !it.armNewRental })
    }

    @Test
    fun autoPlayStartsOnlyInRealParkAndStopsWhenItLeaves() = runTest {
        val cabin = ScriptedCabinFeed()
        val host = RecordingHost()
        val director = AutoPlayDirector.forTest(cabin) { yield() }
        director.play(host, this, GearPosition.DRIVE)
        advanceUntilIdle()
        assertFalse(cabin.engaged)
        assertFalse(director.running.value)
        assertEquals(0, host.rewinds)
        assertEquals(0, host.clears)

        director.play(host, this, GearPosition.PARK)
        yield()
        assertTrue(cabin.engaged)
        assertTrue(director.running.value)
        director.onRealGear(GearPosition.REVERSE)
        advanceUntilIdle()
        assertFalse(cabin.engaged)
        assertFalse(director.running.value)
        assertTrue(host.clears >= 1)
    }

    @Test
    fun playFromABeatScalesTheHoldAndLeavesTheFullScriptAlone() = runTest {
        val paced = AutoPlayScript.paced("F3", 2f)
        val f3 = paced.indexOfFirst { it is AutoStep.Gear && it.beat == "F3" }
        assertTrue(f3 > 0)
        assertTrue(paced.take(f3).all { it.ms == 0L })
        assertEquals(16_000L, paced[f3].ms)
        val c2 = paced.last { AutoPlayScript.beatOf(it) == "C2" }
        assertEquals(6_000L, c2.ms)
        assertTrue(paced.last() is AutoStep.Rewind)
        assertEquals(90_000L, AutoPlayScript.totalMs())
        assertEquals(7_000L, AutoPlayScript.steps.first().ms)
        assertTrue(AutoPlayScript.paced("NOPE", 2f).isEmpty())

        val waits = mutableListOf<Long>()
        val cabin = ScriptedCabinFeed()
        val director = AutoPlayDirector.forTest(cabin) { waits += it }
        director.playFrom(RecordingHost(), this, GearPosition.PARK, "F3", 2f)
        advanceUntilIdle()
        assertEquals(paced.mapNotNull { step ->
            when (step) {
                is AutoStep.Show, is AutoStep.Highlight, is AutoStep.Gear, is AutoStep.Body ->
                    step.ms.takeIf { it > 0L }
                else -> null
            }
        }, waits)
        assertFalse(cabin.engaged)
    }

    @Test
    fun playFromOutsideParkDoesNotEngageOrStopARunningScript() = runTest {
        val cabin = ScriptedCabinFeed()
        val host = RecordingHost()
        val director = AutoPlayDirector.forTest(cabin) { yield() }
        director.playFrom(host, this, GearPosition.DRIVE, "C1", 2f)
        advanceUntilIdle()
        assertFalse(cabin.engaged)
        assertEquals(0, host.rewinds)

        director.play(host, this, GearPosition.PARK)
        yield()
        assertTrue(director.running.value)
        director.playFrom(host, this, GearPosition.PARK, "NOPE", 2f)
        assertTrue(director.running.value)
        assertTrue(cabin.engaged)
        director.stop()
        advanceUntilIdle()
        assertFalse(cabin.engaged)
    }

    @Test
    fun storyboardV1HoldsMatchTheCutTable() {
        assertEquals(FilmingCommands.storyboardSteps, StoryboardScript.beats())
        assertEquals(5_000L, StoryboardScript.holdMs("F1"))
        assertEquals(7_000L, StoryboardScript.holdMs("F2"))
        assertEquals(6_000L, StoryboardScript.holdMs("D1"))
        assertEquals(6_000L, StoryboardScript.holdMs("DRIVE1"))
        assertEquals(7_000L, StoryboardScript.holdMs("D2"))
        assertEquals(3_000L, StoryboardScript.holdMs("DRIVE2"))
        assertEquals(9_000L, StoryboardScript.holdMs("F3"))
        assertEquals(7_000L, StoryboardScript.holdMs("C1"))
        assertEquals(4_000L, StoryboardScript.holdMs("C2"))
        assertEquals(54_000L, StoryboardScript.steps.sumOf { it.ms })
        assertFalse(StoryboardScript.beats().contains("C3"))
        assertTrue(StoryboardScript.steps.none { it is AutoStep.Epilogue })
        assertTrue(StoryboardScript.steps.filterIsInstance<AutoStep.Show>().all { it.caption.isEmpty() })
        val roads = StoryboardScript.steps.filterIsInstance<AutoStep.Road>()
        assertEquals(2, roads.first().tolls)
        assertEquals(1, roads.first().highways)
        assertFalse(roads.first().charging)
        assertTrue(roads.last().charging)
        val open = StoryboardScript.steps.filterIsInstance<AutoStep.Gear>().first { it.beat == "C1" }
        val close = StoryboardScript.steps.filterIsInstance<AutoStep.Body>().last()
        assertEquals(6_000L, open.ms)
        assertTrue(StoryboardScript.steps.filterIsInstance<AutoStep.Body>().any { it.rearDoorOpen })
        assertEquals(1_000L, close.ms)
        assertFalse(close.rearDoorOpen)
        assertEquals(StoryboardScript.SOC_RETURN, close.soc)
        val paced = StoryboardScript.paced("D1", 1f, null)
        val d1 = paced.indexOfFirst { it is AutoStep.Gear && it.beat == "D1" }
        assertTrue(paced.take(d1).all { it.ms == 0L })
        assertEquals(6_000L, paced[d1].ms)
        assertEquals(6_000L, paced.first { it is AutoStep.Gear && it.beat == "DRIVE1" }.ms)
        val stretched = StoryboardScript.paced("C1", 1f, 14)
        assertEquals(14_000L, cutHold(stretched, "C1"))
        assertEquals(4_000L, cutHold(stretched, "C2"))
        assertTrue(StoryboardScript.paced("O1", 1f, null).isEmpty())
        assertTrue(StoryboardScript.paced("C3", 1f, null).isEmpty())
    }

    @Test
    fun storyboardKeepsSocAndCountsDistanceFromTheOdometer() = runTest {
        val cabin = ScriptedCabinFeed()
        data class Sample(val soc: Int, val door: Boolean, val gear: GearPosition, val ms: Long)
        val samples = mutableListOf<Sample>()
        val host = RecordingHost()
        val director = AutoPlayDirector.forTest(cabin) { ms ->
            samples += Sample(cabin.soc, cabin.rearDoorOpen, cabin.gear, ms)
        }
        director.playCut(
            host,
            this,
            GearPosition.PARK,
            null,
            1f,
            FilmingCommands.PROFILE_STORYBOARD_V1,
            null,
        )
        advanceUntilIdle()
        assertEquals(StoryboardScript.SOC_OPEN, samples.first().soc)
        assertEquals(60, samples.first { it.ms == 7_000L && it.gear == GearPosition.PARK }.soc)
        assertTrue(samples.any { it.ms == 6_000L && it.gear == GearPosition.DRIVE && it.soc == 60 && !it.door })
        assertTrue(samples.any { it.ms == 9_000L && it.soc == 85 && !it.door })
        assertTrue(samples.any { it.ms == 6_000L && it.soc == 85 && it.door && it.gear == GearPosition.PARK })
        assertTrue(samples.any { it.ms == 1_000L && it.soc == 85 && !it.door })
        val drives = StoryboardScript.steps.filterIsInstance<AutoStep.Gear>().count { it.gear == GearPosition.DRIVE }
        assertEquals(4, drives)
        val km = (cabin.odometerKm - ScriptedCabinFeed.START_KM).roundToInt()
        assertEquals(drives * ScriptedCabinFeed.DRIVE_STEP_KM.roundToInt(), km)
        assertEquals(
            RentalDistance.line(ScriptedCabinFeed.START_KM, cabin.odometerKm, true),
            "이번 대여에서 ${km}km를 달렸어요. 수고하셨어요.",
        )
        assertFalse(cabin.engaged)
        val window = host.applied.last { it.returnInMinutes == 120 }
        assertEquals(80, window.chargeTargetPercent)
        assertEquals(F3Rules.DEMO_ETA_SECONDS, window.remainSeconds)
        assertEquals(2, host.applied.first { it.pretendTolls != null }.pretendTolls)
    }

    @Test
    fun storyboardRejectsAnUnknownCutWithoutStopping() = runTest {
        val cabin = ScriptedCabinFeed()
        val host = RecordingHost()
        val director = AutoPlayDirector.forTest(cabin) { yield() }
        director.play(host, this, GearPosition.PARK)
        yield()
        assertTrue(director.running.value)
        director.playCut(host, this, GearPosition.PARK, "O1", 1f, FilmingCommands.PROFILE_STORYBOARD_V1, null)
        director.playCut(host, this, GearPosition.PARK, "C3", 1f, FilmingCommands.PROFILE_STORYBOARD_V1, null)
        director.playCut(host, this, GearPosition.PARK, "D1", 1f, "other-profile", null)
        director.playCut(host, this, GearPosition.DRIVE, "F1", 1f, FilmingCommands.PROFILE_STORYBOARD_V1, null)
        assertTrue(director.running.value)
        assertTrue(cabin.engaged)
        director.stop()
        advanceUntilIdle()
        assertFalse(cabin.engaged)
    }

    @Test
    fun drivingRaisesTheStandInOdometer() = runTest {
        val cabin = ScriptedCabinFeed()
        val director = AutoPlayDirector.forTest(cabin) {}
        director.play(RecordingHost(), this, GearPosition.PARK)
        advanceUntilIdle()
        val drives = AutoPlayScript.steps.filterIsInstance<AutoStep.Gear>().count { it.gear == GearPosition.DRIVE }
        assertEquals(4, drives)
        assertEquals(
            ScriptedCabinFeed.START_KM + drives * ScriptedCabinFeed.DRIVE_STEP_KM,
            cabin.odometerKm,
            0.01f,
        )
        assertFalse(cabin.engaged)
    }

    private class RecordingHost : DemoPlaybackHost {
        var clears = 0
        var rewinds = 0
        val applied = mutableListOf<ScenarioOverrides>()
        private var scenario = ScenarioOverrides()

        override fun refresh() = Unit
        override fun rewindToIntake() {
            rewinds += 1
        }
        override fun chooseRental() = Unit
        override fun defaultReturnMinute(): Int = 20 * 60
        override fun selectReturnMinute(minuteOfDay: Int) = Unit
        override fun selectReturnDay(offset: Int) = Unit
        override fun selectReturnPlace(placeId: String) = Unit
        override fun confirmReturn() = Unit
        override fun scenario(): ScenarioOverrides = scenario
        override fun applyScenario(next: ScenarioOverrides) {
            scenario = next
            applied += next
        }
        override fun clearDemo() {
            clears += 1
        }
    }
}
