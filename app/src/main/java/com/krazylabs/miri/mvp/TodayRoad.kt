package com.krazylabs.miri.mvp

/**
 * D1 voice is the first item only, and only while parked.
 * Tolls use G1–G10. Zero tolls and at least one highway entry uses H1.
 * G0 is not a clip. Both counts at zero means no card and no voice.
 * H1 must not play while driving.
 */
object TodayRoad {
    /**
     * D1 for one park. A real [routeId] wins. The pretend id is only a demo
     * stand-in when no route id has arrived. An empty id is not a route.
     */
    fun face(
        routeId: String,
        routing: Boolean,
        tolls: Int,
        highways: Int,
        pretendTolls: Int?,
        pretendHighways: Int?,
    ): D1Face? {
        val shownTolls = pretendTolls ?: tolls
        val shownHighways = pretendHighways ?: highways
        val pretendRoad = pretendTolls != null || pretendHighways != null
        val id = when {
            routeId.isNotEmpty() -> routeId
            pretendRoad -> "pretend"
            else -> ""
        }
        if (!(routing || pretendRoad) || id.isEmpty()) return null
        val clip = voiceKey(shownTolls, shownHighways, parked = true) ?: return null
        return D1Face(id, lines(shownTolls, shownHighways), clip)
    }

    fun voiceKey(tolls: Int, highways: Int, parked: Boolean): String? {
        if (!parked) return null
        if (tolls <= 0 && highways <= 0) return null
        if (tolls > 0) return tollKey(tolls)
        return VoiceClips.H1
    }

    private fun lines(tolls: Int, highways: Int): List<String> {
        val lines = mutableListOf<String>()
        if (tolls > 0) lines += "톨게이트 ${tolls}곳"
        if (highways > 0) lines += "고속도로 진입 ${highways}번"
        return lines
    }

    fun tollKey(tolls: Int): String? = when {
        tolls in 1..9 -> "vo_toll_$tolls"
        tolls >= 10 -> "vo_toll_many"
        else -> null
    }
}
