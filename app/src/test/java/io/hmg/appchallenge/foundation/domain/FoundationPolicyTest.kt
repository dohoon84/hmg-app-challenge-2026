package io.hmg.appchallenge.foundation.domain

import android.car.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GearSelectionCodesTest {
    @Test
    fun matchesAndroidCarVehicleGear() {
        assertEquals(VehicleGear.GEAR_UNKNOWN, GearSelectionCodes.UNKNOWN)
        assertEquals(VehicleGear.GEAR_NEUTRAL, GearSelectionCodes.NEUTRAL)
        assertEquals(VehicleGear.GEAR_REVERSE, GearSelectionCodes.REVERSE)
        assertEquals(VehicleGear.GEAR_PARK, GearSelectionCodes.PARK)
        assertEquals(VehicleGear.GEAR_DRIVE, GearSelectionCodes.DRIVE)
        assertEquals(VehicleGear.GEAR_FIRST, GearSelectionCodes.FIRST)
        assertEquals(VehicleGear.GEAR_NINTH, GearSelectionCodes.NINTH)
    }

    @Test
    fun mapsParkDriveAndManualGears() {
        assertEquals(GearPosition.PARK, GearSelectionCodes.toGear(GearSelectionCodes.PARK))
        assertEquals(GearPosition.NEUTRAL, GearSelectionCodes.toGear(GearSelectionCodes.NEUTRAL))
        assertEquals(GearPosition.REVERSE, GearSelectionCodes.toGear(GearSelectionCodes.REVERSE))
        assertEquals(GearPosition.DRIVE, GearSelectionCodes.toGear(GearSelectionCodes.DRIVE))
        assertEquals(GearPosition.DRIVE, GearSelectionCodes.toGear(GearSelectionCodes.FIRST))
        assertEquals(GearPosition.DRIVE, GearSelectionCodes.toGear(GearSelectionCodes.NINTH))
        assertEquals(GearPosition.UNKNOWN, GearSelectionCodes.toGear(null))
        assertEquals(GearPosition.OTHER, GearSelectionCodes.toGear(999))
    }
}

class MotionResolverTest {
    @Test
    fun parkWithoutUxRestrictionIsParked() {
        assertEquals(Motion.PARKED, MotionResolver.resolve(GearPosition.PARK, false))
        assertEquals(AppShell.FULL, ShellPolicy.select(Motion.PARKED))
    }

    @Test
    fun uxRestrictionForcesDrivingEvenInPark() {
        assertEquals(Motion.DRIVING, MotionResolver.resolve(GearPosition.PARK, true))
        assertEquals(AppShell.REDUCED, ShellPolicy.select(Motion.DRIVING))
    }

    @Test
    fun knownNonParkGearsAreDriving() {
        listOf(GearPosition.NEUTRAL, GearPosition.REVERSE, GearPosition.DRIVE, GearPosition.OTHER).forEach { gear ->
            assertEquals(Motion.DRIVING, MotionResolver.resolve(gear, false))
        }
    }

    @Test
    fun unknownGearWithoutRestrictionStaysUnknown() {
        assertEquals(Motion.UNKNOWN, MotionResolver.resolve(GearPosition.UNKNOWN, false))
        assertEquals(AppShell.FULL, ShellPolicy.select(Motion.UNKNOWN))
    }
}

class CapabilityAndLayoutTest {
    @Test
    fun onlyReadAccessAllowsAGet() {
        assertTrue(SignalAccess.READ.allowsRead())
        assertTrue(SignalAccess.READ_WRITE.allowsRead())
        assertFalse(SignalAccess.NONE.allowsRead())
        assertFalse(SignalAccess.WRITE.allowsRead())
    }

    @Test
    fun zeroPositionIsClosed() {
        assertFalse(isOpenPosition(0))
        assertFalse(isOpenPosition(null))
        assertTrue(isOpenPosition(1))
    }

    @Test
    fun landscapeOrWideWidthUsesTwoPanes() {
        assertTrue(PanePolicy.useTwoPanes(landscape = true, widthDp = 400))
        assertFalse(PanePolicy.useTwoPanes(landscape = false, widthDp = 400))
        assertTrue(PanePolicy.useTwoPanes(landscape = false, widthDp = 800))
    }

    @Test
    fun placeParserRejectsOutOfRange() {
        assertNull(PlaceParser.parse("91", "0", "x"))
        assertNull(PlaceParser.parse("lat", "127", "x"))
        val parsed = PlaceParser.parse("37.5", "127.0", "  ")
        assertEquals("좌표 목적지", parsed?.name)
        assertEquals(37.5, parsed?.latitude)
    }

    @Test
    fun etaUsesAssumedSpeed() {
        assertEquals(900, GeoEstimate.etaSeconds(10_000, speedKmh = 40.0))
        val meters = GeoEstimate.haversineMeters(37.4979, 127.0276, 37.554722, 126.970833)
        assertTrue(meters in 7_000..12_000)
    }

    @Test
    fun restrictionShortensOnlyWhenActive() {
        assertEquals("서울역", shortenForRestriction("서울역", 2, restricted = false))
        assertEquals("서울", shortenForRestriction("서울역", 2, restricted = true))
        assertEquals("서울역", shortenForRestriction("서울역", 0, restricted = true))
    }
}
