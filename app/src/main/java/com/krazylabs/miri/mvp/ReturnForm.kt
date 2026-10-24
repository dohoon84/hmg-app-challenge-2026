package com.krazylabs.miri.mvp

import java.time.LocalDate
import java.time.LocalTime

/**
 * F2 (v1.21). Defaults: today, the pickup place, time empty.
 * '다 됐어요' stays disabled until a time is chosen.
 * The date sheet is the next 7 days including today, not a calendar.
 * Closing the screen before confirm does not save a return.
 */
data class SavedReturn(
    val dayOffset: Int,
    val minuteOfDay: Int,
    val placeId: String,
)

data class F2Draft(
    val dayOffset: Int = 0,
    val minuteOfDay: Int? = null,
    val placeId: String = PLACE_PICKUP,
    val dateSheetOpen: Boolean = false,
) {
    val doneEnabled: Boolean get() = minuteOfDay != null

    companion object {
        const val PLACE_PICKUP = "pickup"
        const val PICKUP_LABEL = "대여한 곳"
        const val TITLE = "언제, 어디로 반납하세요?"
        const val DONE = "다 됐어요"
        const val DONE_HINT = "시각을 고르면 누를 수 있어요."
        const val TIME_EMPTY = "시각"

        fun timeOptions(): List<Int> = (0 until 24 * 60 step 30).toList()

        fun dateOffsets(): IntRange = 0..6
    }
}

fun dateLabel(today: LocalDate, offset: Int): String {
    val date = today.plusDays(offset.toLong())
    val week = arrayOf("월", "화", "수", "목", "금", "토", "일")
    val day = week[date.dayOfWeek.value - 1]
    return "${date.monthValue}/${date.dayOfMonth} ($day)"
}

fun timeLabel(minuteOfDay: Int): String {
    val clock = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val marker = if (clock.hour < 12) "오전" else "오후"
    val hour = when (val twelve = clock.hour % 12) {
        0 -> 12
        else -> twelve
    }
    return "%s %d:%02d".format(marker, hour, clock.minute)
}

class ReturnForm {
    var draft: F2Draft = F2Draft()
        private set

    var saved: SavedReturn? = null
        private set

    fun selectTime(minuteOfDay: Int) {
        require(minuteOfDay in F2Draft.timeOptions())
        draft = draft.copy(minuteOfDay = minuteOfDay)
    }

    fun openDateSheet() {
        draft = draft.copy(dateSheetOpen = true)
    }

    fun selectDate(offset: Int) {
        require(offset in F2Draft.dateOffsets())
        draft = draft.copy(dayOffset = offset, dateSheetOpen = false)
    }

    fun selectPlace(placeId: String) {
        draft = draft.copy(placeId = placeId)
    }

    fun confirm(): Boolean {
        val minute = draft.minuteOfDay ?: return false
        saved = SavedReturn(draft.dayOffset, minute, draft.placeId)
        return true
    }

    /** D or R before confirm. An unsaved time selection is discarded. */
    fun closedWithoutConfirm() {
        if (saved != null) return
        draft = F2Draft()
    }
}
