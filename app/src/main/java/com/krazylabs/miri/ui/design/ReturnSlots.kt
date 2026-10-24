package com.krazylabs.miri.ui.design

import com.krazylabs.miri.mvp.F2Draft
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Wall clock for the return-time row. Production uses Seoul local time.
 * Previews and tests pass a fixed clock so past slots stay stable.
 */
fun interface WallClock {
    fun now(): LocalDateTime
}

object SystemWallClock : WallClock {
    override fun now(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
}

fun WallClock.today(): LocalDate = now().toLocalDate()

fun WallClock.minuteOfDay(): Int {
    val time = now().toLocalTime()
    return time.hour * 60 + time.minute
}

/**
 * The next 30-minute boundary strictly after [nowMinute].
 * 18:10 and 18:00 both start at 18:30. 18:30 starts at 19:00.
 * 24:00 means no slot remains today.
 */
fun nextSelectableMinute(nowMinute: Int): Int {
    val boundary = nowMinute - (nowMinute % 30)
    return boundary + 30
}

/**
 * Today starts at the next slot after now. Later days start at 00:00.
 * Slots before that start are omitted, so they cannot be selected.
 */
fun visibleReturnMinutes(dayOffset: Int, nowMinute: Int): List<Int> {
    val all = F2Draft.timeOptions()
    if (dayOffset != 0) return all
    val start = nextSelectableMinute(nowMinute)
    return all.filter { it >= start }
}

private val WEEK = arrayOf("월", "화", "수", "목", "금", "토", "일")

fun weekDay(date: LocalDate): String = WEEK[date.dayOfWeek.value - 1]

fun returnDate(today: LocalDate, dayOffset: Int, epochDay: Long?): LocalDate =
    epochDay?.let(LocalDate::ofEpochDay) ?: today.plusDays(dayOffset.toLong())

fun returnDayWord(today: LocalDate, dayOffset: Int, epochDay: Long?): String {
    val date = returnDate(today, dayOffset, epochDay)
    val delta = ChronoUnit.DAYS.between(today, date).toInt()
    return when (delta) {
        0 -> "오늘"
        1 -> "내일"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}

fun returnSummary(today: LocalDate, dayOffset: Int, epochDay: Long?, minuteOfDay: Int?): String {
    val day = returnDayWord(today, dayOffset, epochDay)
    val minute = minuteOfDay ?: return "$day · 시각을 골라 주세요"
    return "$day ${clockLabel(minute)}"
}

fun clockLabel(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun sheetDayCaption(today: LocalDate, offset: Int): String {
    val date = today.plusDays(offset.toLong())
    val name = weekDay(date)
    return when (offset) {
        0 -> "오늘 · $name"
        1 -> "내일 · $name"
        else -> name
    }
}

/** 0 today, 1 tomorrow, 2 the date sheet. */
fun segmentIndex(dayOffset: Int, epochDay: Long?, today: LocalDate): Int {
    val date = returnDate(today, dayOffset, epochDay)
    val delta = ChronoUnit.DAYS.between(today, date).toInt()
    return when (delta) {
        0 -> 0
        1 -> 1
        else -> 2
    }
}
