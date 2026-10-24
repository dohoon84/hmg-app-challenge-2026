package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.DoorId
import com.krazylabs.miri.domain.PanelState
import com.krazylabs.miri.domain.SeatId
import com.krazylabs.miri.domain.WindowId

object CheckRules {
    const val FOOTER = "다 끝나면 알려 드릴게요."
    const val DONE = "점검 끝. 좋은 하루 보내세요."
    const val REAR_NOTE = "이번에 뒷문을 여닫은 적이 있어요. 뒷좌석에 두고 내린 물건이 없는지 한 번 봐 주세요."
    const val TITLE = "반납 전 점검"
    const val ARRIVED_TITLE = "반납 장소에 도착했어요"

    fun items(
        doors: Map<DoorId, PanelState>,
        windows: Map<WindowId, PanelState>,
        cabinOn: Boolean?,
        reading: Map<SeatId, Boolean>,
        lightsUseDoorStandIn: Boolean,
    ): List<String> {
        val lines = mutableListOf<String>()
        DoorId.entries.forEach { id ->
            if (doors[id]?.open == true) lines += doorLine(id)
        }
        listOf(
            WindowId.ROW_1_LEFT,
            WindowId.ROW_1_RIGHT,
            WindowId.ROW_2_LEFT,
            WindowId.ROW_2_RIGHT,
        ).forEach { id ->
            if (windows[id]?.open == true) lines += "${windowName(id)} 창문이 열려 있어요."
        }
        if (!lightsUseDoorStandIn) {
            if (cabinOn == true) lines += "실내등이 켜져 있어요."
            SeatId.entries.forEach { seat ->
                if (reading[seat] == true) lines += "${readingName(seat)} 독서등이 켜져 있어요."
            }
        }
        return lines
    }

    fun chargeLine(ev: Boolean, level: Int?, target: Int?): String? {
        if (level == null || target == null || level >= target) return null
        val word = if (ev) "충전" else "연료"
        return "$word $level% · 반납 기준 $target%"
    }

    fun badge(count: Int): String = "아직 ${count}개"

    fun doorLine(id: DoorId): String = when (id) {
        DoorId.REAR -> "트렁크가 열려 있어요."
        else -> "${doorName(id)}이 열려 있어요."
    }

    fun doorName(id: DoorId): String = when (id) {
        DoorId.ROW_1_LEFT -> "왼쪽 앞문"
        DoorId.ROW_1_RIGHT -> "오른쪽 앞문"
        DoorId.ROW_2_LEFT -> "왼쪽 뒷문"
        DoorId.ROW_2_RIGHT -> "오른쪽 뒷문"
        DoorId.ROW_3_LEFT -> "왼쪽 뒷문"
        DoorId.ROW_3_RIGHT -> "오른쪽 뒷문"
        DoorId.HOOD -> "보닛"
        DoorId.REAR -> "트렁크"
    }

    fun windowName(id: WindowId): String = when (id) {
        WindowId.ROW_1_LEFT -> "앞 왼쪽"
        WindowId.ROW_1_RIGHT -> "앞 오른쪽"
        WindowId.ROW_2_LEFT -> "뒤 왼쪽"
        WindowId.ROW_2_RIGHT -> "뒤 오른쪽"
        else -> ""
    }

    fun readingName(seat: SeatId): String = when (seat) {
        SeatId.DRIVER -> "앞쪽 왼쪽"
        SeatId.PASSENGER -> "앞쪽 오른쪽"
        SeatId.REAR_LEFT -> "뒤쪽 왼쪽"
        SeatId.REAR_RIGHT -> "뒤쪽 오른쪽"
    }
}

/** Remembers a rear-door open so the note can show after it closes. The note is not an item. */
class RearDoorNote {
    private var opened = false

    fun observe(doors: Map<DoorId, PanelState>): String? {
        val rear = listOf(DoorId.ROW_2_LEFT, DoorId.ROW_2_RIGHT, DoorId.REAR)
        if (rear.any { doors[it]?.open == true }) opened = true
        return if (opened) CheckRules.REAR_NOTE else null
    }
}
