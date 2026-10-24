package com.krazylabs.miri.domain

/**
 * Four seats this phase can name. Center and third-row areas are ignored.
 * Driver is ROW_1_LEFT: a left-hand-drive cabin, which is the Korea contest image.
 * A seat missing from the capability list stays unavailable. Rear seats are never assumed.
 */
enum class SeatId {
    DRIVER,
    PASSENGER,
    REAR_LEFT,
    REAR_RIGHT,
}

/**
 * Capability only. Occupancy and belt values are not subscribed, and no seat is moved.
 * [available] is true when at least one of those reads is supported.
 */
data class SeatSupport(
    val occupancyReadable: Boolean = false,
    val beltReadable: Boolean = false,
) {
    val available: Boolean get() = occupancyReadable || beltReadable
}

fun seatSupport(
    occupancyAreas: Set<SeatId>,
    beltAreas: Set<SeatId>,
    occupancyAccess: SignalAccess,
    beltAccess: SignalAccess,
): Map<SeatId, SeatSupport> {
    val occupancyOk = occupancyAccess.allowsRead()
    val beltOk = beltAccess.allowsRead()
    return SeatId.entries.associateWith { id ->
        SeatSupport(
            occupancyReadable = occupancyOk && id in occupancyAreas,
            beltReadable = beltOk && id in beltAreas,
        )
    }
}

fun unavailableSeats(): Map<SeatId, SeatSupport> =
    SeatId.entries.associateWith { SeatSupport() }
