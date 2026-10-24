package com.krazylabs.miri.domain

import android.car.VehicleGear
import android.car.drivingstate.CarUxRestrictions
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
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_BASELINE, UxRestrictionBits.BASELINE)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD, UxRestrictionBits.NO_DIALPAD)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING, UxRestrictionBits.NO_FILTERING)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH, UxRestrictionBits.LIMIT_STRING_LENGTH)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD, UxRestrictionBits.NO_KEYBOARD)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO, UxRestrictionBits.NO_VIDEO)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT, UxRestrictionBits.LIMIT_CONTENT)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, UxRestrictionBits.NO_SETUP)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE, UxRestrictionBits.NO_TEXT_MESSAGE)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION, UxRestrictionBits.NO_VOICE_TRANSCRIPTION)
        assertEquals(CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED, UxRestrictionBits.FULLY_RESTRICTED)
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
        assertEquals(
            AppShell.FULL,
            ShellPolicy.select(Motion.PARKED, uxAvailable = true, failClosed = true),
        )
    }

    @Test
    fun uxRestrictionForcesDrivingEvenInPark() {
        assertEquals(Motion.DRIVING, MotionResolver.resolve(GearPosition.PARK, true))
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(Motion.DRIVING, uxAvailable = true, failClosed = true),
        )
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
    }
}

class FailClosedShellTest {
    @Test
    fun pleosUnknownOrMissingUxIsReduced() {
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(Motion.UNKNOWN, uxAvailable = true, failClosed = true),
        )
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(Motion.PARKED, uxAvailable = false, failClosed = true),
        )
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(Motion.UNKNOWN, uxAvailable = false, failClosed = true),
        )
    }

    @Test
    fun fakeUnknownCanStayFull() {
        assertEquals(
            AppShell.FULL,
            ShellPolicy.select(Motion.UNKNOWN, uxAvailable = false, failClosed = false),
        )
    }

    @Test
    fun lostCarServiceRequiresOptimization() {
        val lost = SignalLoss.drivingSnapshot()
        assertFalse(lost.uxAvailable)
        assertEquals(Motion.DRIVING, lost.motion)
        assertEquals(
            AppShell.REDUCED,
            ShellPolicy.select(lost.motion, lost.uxAvailable, failClosed = true),
        )
    }

    @Test
    fun keyboardFollowsNoKeyboardBit() {
        val mask = UxRestrictionBits.NO_KEYBOARD
        assertFalse(keyboardAllowed(mask, uxAvailable = true, failClosed = true))
        assertTrue(keyboardAllowed(0, uxAvailable = true, failClosed = true))
        assertFalse(keyboardAllowed(0, uxAvailable = false, failClosed = true))
        assertTrue(keyboardAllowed(0, uxAvailable = false, failClosed = false))
        assertFalse(keyboardAllowed(mask, uxAvailable = true, failClosed = false))
    }

    private fun keyboardAllowed(mask: Int, uxAvailable: Boolean, failClosed: Boolean): Boolean =
        UxRestrictionState.from(
            uxAvailable = uxAvailable,
            mask = mask,
            maxRestrictedStringLength = 0,
            failClosed = failClosed,
        ).keyboardAllowed

    @Test
    fun vinMaskKeepsLastFour() {
        assertNull(maskVin(null))
        assertEquals("", maskVin(""))
        assertEquals("***", maskVin("ABC"))
        assertEquals("**********-VIN", maskVin("FAKE-NOT-A-VIN"))
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
}
