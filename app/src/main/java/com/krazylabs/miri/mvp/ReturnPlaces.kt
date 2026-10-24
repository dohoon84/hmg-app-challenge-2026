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

/** A named destination, bookmark, or recent place. Blank names are not rows. */
data class NearbyPlace(
    val id: String,
    val name: String,
    val caption: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * The rental's start fix. [begin] drops the previous coordinates.
 * [arrive] accepts only a location newer than the one already seen, so a
 * cached fix cannot stand in for the request.
 */
data class RentalOrigin(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val pending: Boolean = false,
    val seenSerial: Long = 0L,
) {
    val located: Boolean get() = !pending && latitude != null && longitude != null

    fun begin(currentSerial: Long): RentalOrigin = RentalOrigin(
        pending = true,
        seenSerial = currentSerial,
    )

    fun arrive(serial: Long, latitude: Double, longitude: Double, address: String): RentalOrigin {
        if (!pending || serial <= seenSerial) return this
        return copy(
            latitude = latitude,
            longitude = longitude,
            address = address,
            pending = false,
            seenSerial = serial,
        )
    }
}

const val ORIGIN_PENDING = "위치 확인 중"
const val PLACE_SEARCH = "다른 곳 찾기"
const val PLACE_SEARCH_EMPTY = "내비에서 목적지를 정하면 여기 나타나요"
const val PLACE_DEST_ID = "dest"

fun kmDistance(meters: Int): String =
    String.format(Locale.US, "%.1f km", meters / 1000.0)

fun straightLineLabel(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): String = kmDistance(GeoEstimate.haversineMeters(fromLatitude, fromLongitude, toLatitude, toLongitude))

/** Name, then address, then coordinates. A bookmark row is never blank. */
fun bookmarkRowLabel(name: String?, address: String?, latitude: Double, longitude: Double): String {
    val named = name?.trim().orEmpty()
    if (named.isNotEmpty()) return named
    val addressed = address?.trim().orEmpty()
    if (addressed.isNotEmpty()) return addressed
    return "$latitude,$longitude"
}

fun navPlaceId(prefix: String, poiId: String, name: String, latitude: Double, longitude: Double): String {
    val key = poiId.ifBlank { "$name:$latitude:$longitude" }
    return "$prefix:$key"
}

/** Row 1 is always the rental origin. A recent place never fills this row. */
fun originRow(
    origin: RentalOrigin,
    hereLatitude: Double?,
    hereLongitude: Double?,
): ReturnPlace {
    if (!origin.located) {
        return ReturnPlace(
            id = F2Draft.PLACE_PICKUP,
            name = F2Draft.PICKUP_LABEL,
            caption = ORIGIN_PENDING,
            latitude = 0.0,
            longitude = 0.0,
            distance = null,
        )
    }
    val distance = if (hereLatitude != null && hereLongitude != null) {
        straightLineLabel(hereLatitude, hereLongitude, origin.latitude!!, origin.longitude!!)
    } else {
        null
    }
    return ReturnPlace(
        id = F2Draft.PLACE_PICKUP,
        name = F2Draft.PICKUP_LABEL,
        caption = origin.address,
        latitude = origin.latitude!!,
        longitude = origin.longitude!!,
        distance = distance,
    )
}

/**
 * 대여한 곳, then the current destination when one exists, then recents.
 * Demo rows are appended only when nothing real follows the origin, and they
 * never take the origin's place. [limit] keeps the column short enough for
 * the search row under it.
 */
fun assembleReturnPlaces(
    origin: ReturnPlace,
    destination: NearbyPlace?,
    recents: List<NearbyPlace>,
    selected: NearbyPlace?,
    hereLatitude: Double?,
    hereLongitude: Double?,
    demo: List<ReturnPlace>,
    limit: Int = 3,
): List<ReturnPlace> {
    val rows = mutableListOf(origin)
    if (destination != null && destination.name.isNotBlank() && destination.id != origin.id) {
        rows += destination.toReturn(hereLatitude, hereLongitude)
    }
    for (place in recents) {
        if (place.name.isBlank()) continue
        if (place.id == origin.id || place.name == origin.name) continue
        if (rows.any { it.id == place.id || it.name == place.name }) continue
        rows += place.toReturn(hereLatitude, hereLongitude)
    }
    if (rows.size == 1) {
        for (candidate in demo) {
            if (candidate.id == F2Draft.PLACE_PICKUP || candidate.name == F2Draft.PICKUP_LABEL) continue
            if (rows.any { it.id == candidate.id || it.name == candidate.name }) continue
            rows += candidate
        }
    }
    if (
        selected != null &&
        selected.id != F2Draft.PLACE_PICKUP &&
        selected.name.isNotBlank() &&
        rows.none { it.id == selected.id }
    ) {
        val picked = selected.toReturn(hereLatitude, hereLongitude)
        rows.add(1, picked)
    }
    return rows.take(limit.coerceAtLeast(1))
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
        straightLineLabel(hereLatitude, hereLongitude, latitude, longitude)
    } else {
        null
    }
    return ReturnPlace(id, name, caption, latitude, longitude, distance)
}
