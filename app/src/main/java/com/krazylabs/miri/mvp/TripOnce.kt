package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GeoEstimate
import com.krazylabs.miri.domain.TripEstimate

/** One requestTrip per event key. A later read uses the cache and does not ask again. */
class TripOnce {
    private val asked = mutableSetOf<String>()
    private val cache = mutableMapOf<String, TripEstimate>()

    fun cached(key: String): TripEstimate? = cache[key]

    fun begin(key: String): Boolean {
        if (key in asked) return false
        asked += key
        return true
    }

    fun store(key: String, value: TripEstimate) {
        cache[key] = value
    }
}

fun straightLineTrip(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double,
): TripEstimate {
    val meters = GeoEstimate.haversineMeters(fromLat, fromLon, toLat, toLon)
    return TripEstimate(
        distance = meters,
        eta = GeoEstimate.etaSeconds(meters),
        estimatedSocAtDestPercent = -1,
        energyConsumption = 0.0,
    )
}
