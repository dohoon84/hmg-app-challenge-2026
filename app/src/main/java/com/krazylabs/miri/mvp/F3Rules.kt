package com.krazylabs.miri.mvp

import java.time.Instant
import java.time.ZoneId

data class F3Face(
    val key: String,
    val lines: List<String>,
)

object F3Rules {
    fun dueKey(kind: String?, minutesLeft: Long, shown: Set<String>): String? {
        if (minutesLeft < 0) return null
        if (kind == "share" && minutesLeft <= 60 && "share-1h" !in shown) return "share-1h"
        if (kind == "rental" && minutesLeft <= 180 && "rent-3h" !in shown) return "rent-3h"
        if (kind == "rental" && minutesLeft <= 24 * 60 && "rent-24h" !in shown) return "rent-24h"
        return null
    }

    fun coveredBy(key: String): Set<String> = when (key) {
        "rent-3h" -> setOf("rent-3h", "rent-24h")
        else -> setOf(key)
    }

    fun lines(
        ev: Boolean,
        level: Int?,
        target: Int?,
        returnAtMs: Long,
        nowMs: Long,
        etaSeconds: Int?,
        zone: ZoneId = ZoneId.of("Asia/Seoul"),
    ): List<String> {
        val lines = mutableListOf<String>()
        if (target != null && level != null) {
            val gap = target - level
            lines += if (gap > 0) {
                if (ev) "반납까지 약 $gap% 더 충전해야 해요." else "반납까지 연료를 약 $gap% 더 채워야 해요."
            } else {
                if (ev) "반납 기준만큼 충전됐어요." else "반납 기준만큼 채워졌어요."
            }
        }
        if (etaSeconds != null) {
            val depart = returnAtMs - etaSeconds * 1000L - 15 * 60_000L
            lines += "${clock(depart, zone)}쯤 출발하면 여유 있게 도착해요."
        } else {
            val remainMs = (returnAtMs - nowMs).coerceAtLeast(0)
            val totalMinutes = (remainMs / 60_000L).toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val left = when {
                totalMinutes < 1 -> "곧"
                hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
                hours > 0 -> "${hours}시간"
                else -> "${minutes}분"
            }
            lines += "반납 ${clock(returnAtMs, zone)}까지 ${left} 남았어요."
        }
        return lines
    }

    fun clock(epochMs: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }
}
