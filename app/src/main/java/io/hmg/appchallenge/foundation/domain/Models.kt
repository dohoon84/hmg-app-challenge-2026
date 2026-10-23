package io.hmg.appchallenge.foundation.domain

/**
 * Parked when gear is Park and CarUxRestrictions does not require distraction optimization.
 * Any other known gear, or a UX restriction that requires optimization, is driving.
 * Unknown means the gear property has not been read and UX is quiet, so the full UI stays up.
 */
enum class Motion {
    UNKNOWN,
    PARKED,
    DRIVING,
}

enum class GearPosition {
    UNKNOWN,
    PARK,
    NEUTRAL,
    REVERSE,
    DRIVE,
    OTHER,
}

enum class AppShell {
    FULL,
    REDUCED,
}

enum class LinkState {
    IDLE,
    CONNECTING,
    READY,
    FAILED,
}

enum class SignalAccess {
    NONE,
    READ,
    WRITE,
    READ_WRITE,
}

fun SignalAccess.allowsRead(): Boolean = this == SignalAccess.READ || this == SignalAccess.READ_WRITE

enum class DoorId {
    ROW_1_LEFT,
    ROW_1_RIGHT,
    ROW_2_LEFT,
    ROW_2_RIGHT,
    ROW_3_LEFT,
    ROW_3_RIGHT,
    HOOD,
    REAR,
}

enum class WindowId {
    ROW_1_LEFT,
    ROW_1_RIGHT,
    ROW_2_LEFT,
    ROW_2_RIGHT,
    FRONT_WINDSHIELD,
    REAR_WINDSHIELD,
    ROOF_TOP_1,
    ROOF_TOP_2,
}

/**
 * Raw integer from Door.getCurrentDoorPosition / Window.getCurrentWindowPosition.
 * The reference pages describe the value only as an Int. This project treats 0 as closed
 * and any other non-null value as open, matching the SDK sample that writes `1`.
 */
data class PanelState(
    val position: Int?,
    val readable: Boolean,
) {
    val open: Boolean get() = readable && isOpenPosition(position)
}

fun isOpenPosition(position: Int?): Boolean = position != null && position != 0

enum class RangeSource {
    /** Vehicle SDK 2.3.2 has no remaining-range getter. setRangeRemainingDistance is deprecated. */
    UNAVAILABLE,

    /** AAOS VehiclePropertyIds.RANGE_REMAINING. Exposure on Pleos is unverified. */
    AAOS_RANGE_REMAINING,

    SIMULATED,
}

data class VehicleSnapshot(
    val doors: Map<DoorId, PanelState> = DoorId.entries.associateWith { PanelState(null, false) },
    val windows: Map<WindowId, PanelState> = WindowId.entries.associateWith { PanelState(null, false) },
    val fuelPercent: Int? = null,
    val fuelReadable: Boolean = false,
    val socPercent: Int? = null,
    val socReadable: Boolean = false,
    val odometerKm: Float? = null,
    val odometerReadable: Boolean = false,
    val rangeMeters: Float? = null,
    val rangeSource: RangeSource = RangeSource.UNAVAILABLE,
    val vin: String? = null,
    val vinReadable: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val lastError: String? = null,
)

data class DrivingSnapshot(
    val gear: GearPosition = GearPosition.UNKNOWN,
    val gearRaw: Int? = null,
    val gearAvailable: Boolean = false,
    val uxAvailable: Boolean = false,
    val requiresDistractionOptimization: Boolean = false,
    val restrictionMask: Int = 0,
    val maxRestrictedStringLength: Int = 0,
    val motion: Motion = Motion.UNKNOWN,
)

/**
 * Coordinate destination. NaviHelper has no POI name search.
 * Whether an empty poiId is accepted by the navigation app is unverified.
 */
data class PlaceRequest(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String = "",
    val poiId: String = "",
    val poiSubId: String = "",
)

data class DestinationSnapshot(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val remainDistance: Int,
    val remainTime: Int,
    val tollFare: Int,
)

data class ArrivalSnapshot(
    val name: String,
    val address: String,
    val duration: Int,
    val distance: Int,
    val arrivedAtEpochMs: Long,
)

/**
 * TripInfo fields. The scraped property pages only declare the types.
 * The feasibility review reads distance as meters, eta as seconds,
 * estimatedSocAtDest as percent, and energyConsumption as Wh.
 */
data class TripEstimate(
    val distance: Int,
    val eta: Int,
    val estimatedSocAtDestPercent: Int,
    val energyConsumption: Double,
)

object SamplePlaces {
    val seoulStation = PlaceRequest(
        latitude = 37.554722,
        longitude = 126.970833,
        name = "서울역",
        address = "서울특별시 용산구 한강대로 405",
    )
}
