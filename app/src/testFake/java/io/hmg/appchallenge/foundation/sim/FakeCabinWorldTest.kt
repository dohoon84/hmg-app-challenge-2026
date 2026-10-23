package io.hmg.appchallenge.foundation.sim

import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.KeyboardPolicy
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.ShellPolicy
import io.hmg.appchallenge.foundation.driving.FakeDrivingStateRepository
import io.hmg.appchallenge.foundation.navi.FakeNavigationRepository
import io.hmg.appchallenge.foundation.session.CabinSession
import io.hmg.appchallenge.foundation.speech.CabinAnnouncer
import io.hmg.appchallenge.foundation.vehicle.FakeVehicleRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCabinWorldTest {
    @Test
    fun givenPark_whenNoUxRestriction_thenFullShell() {
        val world = FakeCabinWorld()
        assertEquals(Motion.PARKED, world.driving.value.motion)
        assertEquals(
            AppShell.FULL,
            ShellPolicy.select(world.driving.value.motion, world.driving.value.uxAvailable, failClosed = false),
        )
    }

    @Test
    fun givenDriving_whenDistractionOptimization_thenReducedShellAndNoKeyboard() {
        val world = FakeCabinWorld()
        world.setParked(false)
        val driving = world.driving.value
        assertEquals(Motion.DRIVING, driving.motion)
        assertTrue(driving.requiresDistractionOptimization)
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(driving.motion, driving.uxAvailable, failClosed = false),
        )
        assertEquals(
            false,
            KeyboardPolicy.allowsKeyboard(driving.restrictionMask, driving.uxAvailable, failClosed = false),
        )
    }

    @Test
    fun givenClosedDriverDoor_whenToggled_thenOpen() {
        val world = FakeCabinWorld()
        world.setDriverDoorOpen(true)
        assertTrue(world.vehicle.value.doors.getValue(DoorId.ROW_1_LEFT).open)
    }

    @Test
    fun givenSoc_whenNudged_thenStaysInRange() {
        val world = FakeCabinWorld()
        world.nudgeSoc(-100)
        assertEquals(0, world.vehicle.value.socPercent)
        world.nudgeSoc(15)
        assertEquals(15, world.vehicle.value.socPercent)
    }

    @Test
    fun givenSamplePlace_whenTripThenArrival_thenDestinationClears() {
        val world = FakeCabinWorld()
        world.setSampleDestination()
        assertEquals("서울역", world.destination.value?.name)
        world.requestSampleTrip()
        assertTrue(world.trip.value!!.eta > 0)
        world.simulateArrival()
        assertEquals("서울역", world.arrival.value?.name)
        assertNull(world.destination.value)
    }
}

class FakeSessionReentryTest {
    @Test
    fun startStopStartBringsLinksBack() {
        val world = FakeCabinWorld()
        val vehicle = FakeVehicleRepository(world)
        val driving = FakeDrivingStateRepository(world)
        val navigation = FakeNavigationRepository(world)
        val session = CabinSession(vehicle, driving, navigation, object : CabinAnnouncer {
            override fun announce(text: String) = Unit
            override fun stop() = Unit
            override fun onSuspend() = Unit
            override fun onForeground() = Unit
        })
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = error("unused")
        }

        session.onStart(owner)
        assertEquals(LinkState.READY, vehicle.link.value)
        assertEquals(LinkState.READY, navigation.link.value)

        session.onStop(owner)
        assertEquals(LinkState.IDLE, vehicle.link.value)
        assertEquals(LinkState.IDLE, navigation.link.value)

        session.onStart(owner)
        assertEquals(LinkState.READY, vehicle.link.value)
        assertEquals(2, vehicle.startCount)
        assertEquals(1, vehicle.stopCount)
        assertEquals(2, driving.startCount)
        assertEquals(1, driving.stopCount)
        assertEquals(2, navigation.startCount)
        assertEquals(1, navigation.stopCount)
    }
}
