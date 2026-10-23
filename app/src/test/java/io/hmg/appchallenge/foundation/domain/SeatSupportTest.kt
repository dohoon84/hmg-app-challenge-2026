package io.hmg.appchallenge.foundation.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatSupportTest {
    @Test
    fun missingRearSeatsStayUnavailable() {
        val seats = seatSupport(
            occupancyAreas = setOf(SeatId.DRIVER, SeatId.PASSENGER),
            beltAreas = setOf(SeatId.DRIVER, SeatId.PASSENGER),
            occupancyAccess = SignalAccess.READ,
            beltAccess = SignalAccess.READ,
        )
        assertTrue(seats.getValue(SeatId.DRIVER).available)
        assertTrue(seats.getValue(SeatId.PASSENGER).available)
        assertFalse(seats.getValue(SeatId.REAR_LEFT).available)
        assertFalse(seats.getValue(SeatId.REAR_RIGHT).available)
    }

    @Test
    fun emptyOrUnreadableCapabilityDoesNotMarkASeatPresent() {
        val empty = seatSupport(
            occupancyAreas = emptySet(),
            beltAreas = emptySet(),
            occupancyAccess = SignalAccess.READ,
            beltAccess = SignalAccess.READ,
        )
        val denied = seatSupport(
            occupancyAreas = SeatId.entries.toSet(),
            beltAreas = SeatId.entries.toSet(),
            occupancyAccess = SignalAccess.NONE,
            beltAccess = SignalAccess.WRITE,
        )
        SeatId.entries.forEach { id ->
            assertFalse(empty.getValue(id).available)
            assertFalse(denied.getValue(id).available)
        }
        unavailableSeats().values.forEach { support ->
            assertFalse(support.available)
        }
    }

    @Test
    fun oneReadableSignalIsEnough() {
        val seats = seatSupport(
            occupancyAreas = emptySet(),
            beltAreas = setOf(SeatId.REAR_RIGHT),
            occupancyAccess = SignalAccess.NONE,
            beltAccess = SignalAccess.READ_WRITE,
        )
        assertFalse(seats.getValue(SeatId.REAR_LEFT).available)
        assertTrue(seats.getValue(SeatId.REAR_RIGHT).beltReadable)
        assertTrue(seats.getValue(SeatId.REAR_RIGHT).available)
    }
}
