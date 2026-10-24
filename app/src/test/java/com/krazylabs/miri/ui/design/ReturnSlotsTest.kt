package com.krazylabs.miri.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReturnSlotsTest {
    @Test
    fun nextSlotIsTheFollowingHalfHour() {
        assertEquals(18 * 60 + 30, nextSelectableMinute(18 * 60))
        assertEquals(18 * 60 + 30, nextSelectableMinute(18 * 60 + 10))
        assertEquals(19 * 60, nextSelectableMinute(18 * 60 + 30))
        assertEquals(19 * 60, nextSelectableMinute(18 * 60 + 31))
        assertEquals(24 * 60, nextSelectableMinute(23 * 60 + 30))
    }

    @Test
    fun todayHidesPastSlotsAndLaterDaysStartAtMidnight() {
        val today = visibleReturnMinutes(dayOffset = 0, nowMinute = 17 * 60 + 10)
        assertEquals(17 * 60 + 30, today.first())
        assertTrue(17 * 60 !in today)
        assertEquals(20 * 60, today[5])

        val tomorrow = visibleReturnMinutes(dayOffset = 1, nowMinute = 17 * 60 + 10)
        assertEquals(0, tomorrow.first())
        assertEquals(48, tomorrow.size)
    }

    @Test
    fun summaryUsesTodayAndA24HourClock() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals("오늘 · 시각을 골라 주세요", returnSummary(today, 0, null, null))
        assertEquals("오늘 20:00", returnSummary(today, 0, null, 20 * 60))
        assertEquals("내일 09:30", returnSummary(today, 1, null, 9 * 60 + 30))
        assertEquals(0, segmentIndex(0, null, today))
        assertEquals(1, segmentIndex(1, null, today))
        assertEquals(2, segmentIndex(3, null, today))
        assertEquals("오늘 · 토", sheetDayCaption(today, 0))
        assertEquals("내일 · 일", sheetDayCaption(today, 1))
        assertEquals("월", sheetDayCaption(today, 2))
    }
}
