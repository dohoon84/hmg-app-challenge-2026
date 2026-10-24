package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GeoEstimate
import com.krazylabs.miri.domain.PlaceRequest
import java.util.Locale

/** One row of the F2 return-place list. [distance] is already formatted. */
data class ReturnPlace(
    val id: String,
    val name: String,
    val caption: String,
    val latitude: Double,
    val longitude: Double,
    val distance: String?,
)

/** A named origin or a saved / branch place. Blank names are not rows. */
data class NearbyPlace(
    val id: String,
    val name: String,
    val caption: String,
    val latitude: Double,
    val longitude: Double,
)

fun kmDistance(meters: Int): String =
    String.format(Locale.US, "%.1f km", meters / 1000.0)

/**
 * Up to three return places. A full live list wins. An empty live list uses
 * [demo]. A short live list is filled from [demo] without repeating an id or
 * a name. With neither, the pickup row stays so release still has a choice.
 */
fun assembleReturnPlaces(
    origin: NearbyPlace?,
    nearby: List<NearbyPlace>,
    hereLatitude: Double?,
    hereLongitude: Double?,
    demo: List<ReturnPlace>,
): List<ReturnPlace> {
    val live = buildList {
        if (origin != null && origin.name.isNotBlank()) {
            add(origin.toReturn(hereLatitude, hereLongitude))
        }
        for (place in nearby) {
            if (place.name.isBlank()) continue
            if (any { it.id == place.id || it.name == place.name }) continue
            add(place.toReturn(hereLatitude, hereLongitude))
        }
    }
    if (live.size >= 3) return live.take(3)
    val filler = demo.filter { candidate ->
        live.none { it.id == candidate.id || it.name == candidate.name }
    }
    val merged = if (live.isEmpty()) filler else live + filler
    if (merged.isNotEmpty()) return merged.take(3)
    return listOf(
        ReturnPlace(
            id = F2Draft.PLACE_PICKUP,
            name = F2Draft.PICKUP_LABEL,
            caption = F2Draft.PICKUP_LABEL,
            latitude = 0.0,
            longitude = 0.0,
            distance = null,
        ),
    )
}

fun ReturnPlace.toPlaceRequest(): PlaceRequest = PlaceRequest(
    latitude = latitude,
    longitude = longitude,
    name = name,
    address = caption,
    poiId = id,
)

/**
 * F1 opens when the lock is still unknown, when a long lock has primed a
 * rental, or when the debug panel just started a new rental. An own car
 * never opens F1. The forced flag lasts until a kind or an own car is chosen,
 * including when the door lock is already known.
 */
fun intakeVisible(
    doorLocked: Boolean?,
    kind: String?,
    ownCar: Boolean,
    primed: Boolean,
    forcedNewRental: Boolean,
): Boolean {
    if (ownCar) return false
    if (forcedNewRental) return kind == null
    return if (doorLocked == null) kind == null else primed
}

private fun NearbyPlace.toReturn(hereLatitude: Double?, hereLongitude: Double?): ReturnPlace {
    val distance = if (hereLatitude != null && hereLongitude != null) {
        kmDistance(GeoEstimate.haversineMeters(hereLatitude, hereLongitude, latitude, longitude))
    } else {
        null
    }
    return ReturnPlace(id, name, caption, latitude, longitude, distance)
}
