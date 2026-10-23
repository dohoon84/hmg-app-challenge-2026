package io.hmg.appchallenge.foundation.sim

import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.SamplePlaces
import io.hmg.appchallenge.foundation.domain.ShellPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCabinWorldTest {
    @Test
    fun togglesParkedDrivingDoorSocAndArrival() {
        val world = FakeCabinWorld()
        assertEquals(Motion.PARKED, world.driving.value.motion)
        assertEquals(AppShell.FULL, ShellPolicy.select(world.driving.value.motion))

        world.setParked(false)
        assertEquals(Motion.DRIVING, world.driving.value.motion)
        assertTrue(world.driving.value.requiresDistractionOptimization)
        assertEquals(AppShell.REDUCED, ShellPolicy.select(world.driving.value.motion))

        world.setDriverDoorOpen(true)
        assertTrue(world.vehicle.value.doors.getValue(DoorId.ROW_1_LEFT).open)

        world.nudgeSoc(-100)
        assertEquals(0, world.vehicle.value.socPercent)
        world.nudgeSoc(15)
        assertEquals(15, world.vehicle.value.socPercent)

        world.setDestination(SamplePlaces.seoulStation)
        assertEquals("서울역", world.destination.value?.name)
        assertNotNull(world.trip.value.let { world.requestTrip(SamplePlaces.seoulStation); world.trip.value })
        assertTrue(world.trip.value!!.eta > 0)

        world.simulateArrival()
        assertEquals("서울역", world.arrival.value?.name)
        assertNull(world.destination.value)

        world.setParked(true)
        assertEquals(Motion.PARKED, world.driving.value.motion)
    }
}
