package io.hmg.appchallenge.foundation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingActionLayoutTest {
    @Test
    fun singleActionStaysOneColumn() {
        assertEquals(1, drivingActionColumns(1, availableHeightDp = 200, availableWidthDp = 200))
    }

    @Test
    fun tallPaneKeepsFullWidthButtons() {
        assertEquals(1, drivingActionColumns(8, availableHeightDp = 800, availableWidthDp = 400))
    }

    @Test
    fun landscapePhoneFitsEverySimulatorButton() {
        // Half of a short landscape width, eight 88dp buttons. Three columns stay on screen.
        assertEquals(3, drivingActionColumns(8, availableHeightDp = 360, availableWidthDp = 320))
    }

    @Test
    fun mediumHeightUsesTwoColumns() {
        assertEquals(2, drivingActionColumns(8, availableHeightDp = 500, availableWidthDp = 400))
    }
}
