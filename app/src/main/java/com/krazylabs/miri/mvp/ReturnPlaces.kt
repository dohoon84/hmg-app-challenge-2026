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
    val requestedAtMs: Long = 0L,
    val failed: Boolean = false,
) {
    val located: Boolean get() = !pending && !failed && latitude != null && longitude != null

    fun begin(currentSerial: Long, nowMs: Long = 0L): RentalOrigin = RentalOrigin(
        pending = true,
        seenSerial = currentSerial,
        requestedAtMs = nowMs,
    )

    /** A fix is stored only in Park, only for a newer serial, and never at 0,0. */
    fun arrive(
        serial: Long,
        latitude: Double,
        longitude: Double,
        address: String,
        parked: Boolean = true,
    ): RentalOrigin {
        if (!pending || !parked || serial <= seenSerial) return this
        if (!usableFix(latitude, longitude)) return this
        return copy(
            latitude = latitude,
            longitude = longitude,
            address = address,
            pending = false,
            failed = false,
            seenSerial = serial,
        )
    }

    /** Leave Park or wait out the request, then stop saying the location is on its way. */
    fun tick(nowMs: Long, parked: Boolean): RentalOrigin {
        if (!pending) return this
        val waited = nowMs - requestedAtMs
        if (!parked || waited >= ORIGIN_GIVE_UP_MS) {
            return copy(pending = false, failed = true, latitude = null, longitude = null, address = "")
        }
        return this
    }

    /** A process restart must not send the request again. */
    fun dropPending(): RentalOrigin = if (!pending) this else copy(pending = false, failed = false)
}

fun usableFix(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    return !(latitude == 0.0 && longitude == 0.0)
}

const val ORIGIN_GIVE_UP_MS = 30_000L
const val ORIGIN_PENDING = "위치 확인 중"
const val ORIGIN_MISSED = "위치를 못 찾았어요"
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

/** An unset home or work slot: no name, no address, and the 0,0 point. */
fun emptyBookmarkSlot(name: String?, address: String?, latitude: Double, longitude: Double): Boolean {
    if (name?.trim().orEmpty().isNotEmpty()) return false
    if (address?.trim().orEmpty().isNotEmpty()) return false
    return latitude == 0.0 && longitude == 0.0
}

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
            caption = if (origin.failed) ORIGIN_MISSED else ORIGIN_PENDING,
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
    if (destination != null && destination.id != origin.id) {
        destination.toReturn(hereLatitude, hereLongitude)?.let { row ->
            if (rows.none { it.id == row.id || it.name == row.name }) rows += row
        }
    }
    for (place in recents) {
        if (place.id == origin.id) continue
        val row = place.toReturn(hereLatitude, hereLongitude) ?: continue
        if (row.name == origin.name || row.id == origin.id) continue
        if (rows.any { it.id == row.id || it.name == row.name }) continue
        rows += row
    }
    if (rows.size == 1) {
        for (candidate in demo) {
            if (candidate.id == F2Draft.PLACE_PICKUP || candidate.name == F2Draft.PICKUP_LABEL) continue
            val shown = shownReturnRow(candidate.name, candidate.caption) ?: continue
            if (shown.first == origin.name) continue
            if (rows.any { it.id == candidate.id || it.name == shown.first }) continue
            rows += candidate.copy(name = shown.first, caption = shown.second)
        }
    }
    if (selected != null && selected.id != F2Draft.PLACE_PICKUP) {
        val picked = selected.toReturn(hereLatitude, hereLongitude)
        if (picked != null && rows.none { it.id == picked.id }) {
            rows.add(1, picked)
        }
    }
    return rows.take(limit.coerceAtLeast(1))
}

/**
 * The pickup label is the origin row only. A later row with that name shows
 * its address instead, and a caption that repeats the label is dropped.
 * No address and no other name means the row is skipped.
 */
fun shownReturnRow(name: String, caption: String): Pair<String, String>? {
    val label = name.trim()
    val note = caption.trim()
    if (label.isEmpty()) return null
    if (label == F2Draft.PICKUP_LABEL) {
        val address = note.takeIf {
            it.isNotEmpty() &&
                it != F2Draft.PICKUP_LABEL &&
                it != ORIGIN_PENDING &&
                it != ORIGIN_MISSED
        } ?: return null
        return address to ""
    }
    val distinct = if (note == label || note == F2Draft.PICKUP_LABEL) "" else note
    return label to distinct
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

private fun NearbyPlace.toReturn(hereLatitude: Double?, hereLongitude: Double?): ReturnPlace? {
    val shown = shownReturnRow(name, caption) ?: return null
    val distance = if (hereLatitude != null && hereLongitude != null) {
        straightLineLabel(hereLatitude, hereLongitude, latitude, longitude)
    } else {
        null
    }
    return ReturnPlace(id, shown.first, shown.second, latitude, longitude, distance)
}
