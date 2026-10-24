package com.krazylabs.miri.mvp

/**
 * D1 voice is the first item only, and only while parked.
 * Tolls use G1–G10. Zero tolls and at least one highway entry uses H1.
 * G0 is not a clip. Both counts at zero means no card and no voice.
 * H1 must not play while driving.
 */
object TodayRoad {
    fun voiceKey(tolls: Int, highways: Int, parked: Boolean): String? {
        if (!parked) return null
        if (tolls <= 0 && highways <= 0) return null
        if (tolls > 0) return tollKey(tolls)
        return VoiceClips.H1
    }

    fun tollKey(tolls: Int): String? = when {
        tolls in 1..9 -> "vo_toll_$tolls"
        tolls >= 10 -> "vo_toll_many"
        else -> null
    }
}
