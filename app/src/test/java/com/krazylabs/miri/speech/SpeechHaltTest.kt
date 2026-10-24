package com.krazylabs.miri.speech

import com.krazylabs.miri.domain.GearPosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechHaltTest {
    @Test
    fun shiftingIntoDriveOrReverseStopsAndASteadyGearDoesNot() {
        val halt = SpeechHalt()
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
    }

    @Test
    fun navGuidanceStartingStops() {
        val halt = SpeechHalt()
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
    }

    @Test
    fun aHiddenScreenStops() {
        val halt = SpeechHalt()
        assertTrue(halt.shouldStop(visible = false, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = false, gear = GearPosition.DRIVE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
    }
}
