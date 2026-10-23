package io.hmg.appchallenge.foundation.ui

import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.perf.PerformanceBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IviButtonTest {
    @Test
    fun heightsMatchTheOfficialBasicButton() {
        assertEquals(100, IviButtonMetrics.LARGE_DP)
        assertEquals(80, IviButtonMetrics.MEDIUM_DP)
        assertEquals(64, IviButtonMetrics.SMALL_DP)
        assertEquals(100, IviButtonMetrics.heightDp(IviButtonSize.LARGE))
        assertEquals(80, IviButtonMetrics.heightDp(IviButtonSize.MEDIUM))
        assertEquals(64, IviButtonMetrics.heightDp(IviButtonSize.SMALL))
    }

    @Test
    fun smallIsPromotedWhileReduced() {
        assertEquals(IviButtonSize.MEDIUM, resolveButtonSize(IviButtonSize.SMALL, AppShell.REDUCED))
        assertEquals(IviButtonSize.SMALL, resolveButtonSize(IviButtonSize.SMALL, AppShell.FULL))
        assertEquals(IviButtonSize.LARGE, resolveButtonSize(IviButtonSize.LARGE, AppShell.REDUCED))
        assertEquals(IviButtonSize.MEDIUM, resolveButtonSize(IviButtonSize.MEDIUM, AppShell.REDUCED))
    }

    @Test
    fun pressScalesTo95WithinTheTouchBudget() {
        assertEquals(0.95f, pressScaleOf(pressed = true))
        assertEquals(1f, pressScaleOf(pressed = false))
        assertEquals(0.95f, PressFeedback.PRESSED_SCALE)
        assertTrue(PressFeedback.DURATION_MS in 1..PerformanceBudget.TOUCH_RESPONSE_MS)
    }
}
