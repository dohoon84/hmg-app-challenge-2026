package com.krazylabs.miri.mvp

import kotlin.math.roundToInt

/**
 * Distance driven since the rental started.
 * The start reading is kept only when the odometer could be read.
 * The return line is omitted when that start is missing, the current
 * reading cannot be read, or the difference is negative or rounds to 0.
 */
object RentalDistance {
    fun startToKeep(odometerKm: Float?, readable: Boolean): Float? =
        if (readable && odometerKm != null) odometerKm else null

    fun line(startKm: Float?, currentKm: Float?, readable: Boolean): String? {
        if (!readable || startKm == null || currentKm == null) return null
        val km = (currentKm - startKm).roundToInt()
        if (km <= 0) return null
        return "이번 대여에서 ${km}km를 달렸어요. 수고하셨어요."
    }
}
