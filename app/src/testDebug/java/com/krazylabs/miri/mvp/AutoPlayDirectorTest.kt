package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.DoorId
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
            listOf("F1", "F2", "F2_DONE", "D1", "D2", "F3", "C1", "C2"),
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
