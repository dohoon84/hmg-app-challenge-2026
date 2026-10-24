package io.hmg.appchallenge.foundation.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeCadenceTest {
    @Test
    fun deltasArePerEventAndAcrossEvents() {
        val tracker = CadenceTracker()
        val first = tracker.mark("onDestinationInfo", 1_000)
        assertNull(first["deltaSameMs"])
        assertNull(first["deltaAnyMs"])

        val second = tracker.mark("onTBTInfo", 1_250)
        assertNull(second["deltaSameMs"])
        assertEquals(250L, second["deltaAnyMs"])

        val third = tracker.mark("onDestinationInfo", 1_800)
        assertEquals(800L, third["deltaSameMs"])
        assertEquals(550L, third["deltaAnyMs"])
    }

    @Test
    fun reverseToParkUsesAaosAndPleosNames() {
        assertTrue(isReverseToPark(previousRaw = 2, currentRaw = 4))
        assertFalse(isReverseToPark(previousRaw = 8, currentRaw = 4))
        assertFalse(isReverseToPark(previousRaw = null, currentRaw = 4))
        assertTrue(isPleosReverseToPark("GEAR_REVERSE", "GEAR_PARK"))
        assertFalse(isPleosReverseToPark("GEAR_DRIVE", "GEAR_PARK"))
    }
}
