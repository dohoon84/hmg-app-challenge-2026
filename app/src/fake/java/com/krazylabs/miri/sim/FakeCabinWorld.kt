package com.krazylabs.miri.sim

import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.DoorId
import com.krazylabs.miri.domain.DrivingSnapshot
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.GearSelectionCodes
import com.krazylabs.miri.domain.GeoEstimate
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.MotionResolver
import com.krazylabs.miri.domain.PanelState
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.RangeSource
import com.krazylabs.miri.domain.SeatId
import com.krazylabs.miri.domain.SignalAccess
import com.krazylabs.miri.domain.TripEstimate
import com.krazylabs.miri.domain.seatSupport
import com.krazylabs.miri.domain.UxRestrictionBits
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.domain.WindowId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory vehicle, gear, and navigation. No Pleos classes.
 * Origin is a fixed point in Gangnam so ETA changes when the coordinate fields change.
 */
@Singleton
class FakeCabinWorld @Inject constructor() : CabinSimulator {
    override val enabled: Boolean = true

    private val originLat = 37.4979
    private val originLon = 127.0276

    private val _vehicle = MutableStateFlow(initialVehicle())
    val vehicle: StateFlow<VehicleSnapshot> = _vehicle

    private val _driving = MutableStateFlow(parkedDriving())
    val driving: StateFlow<DrivingSnapshot> = _driving

    private val _link = MutableStateFlow(LinkState.READY)
    val link: StateFlow<LinkState> = _link

    private val _destination = MutableStateFlow<DestinationSnapshot?>(null)
    val destination: StateFlow<DestinationSnapshot?> = _destination

    private val _arrival = MutableStateFlow<ArrivalSnapshot?>(null)
    val arrival: StateFlow<ArrivalSnapshot?> = _arrival

    private val _trip = MutableStateFlow<TripEstimate?>(null)
    val trip: StateFlow<TripEstimate?> = _trip

    private val _status = MutableStateFlow<String?>("시뮬레이션")
    val statusMessage: StateFlow<String?> = _status

    override fun setParked(parked: Boolean) {
        _driving.value = if (parked) parkedDriving() else drivingNow()
    }

    override fun setDriverDoorOpen(open: Boolean) {
        _vehicle.update { current ->
            current.copy(
                doors = current.doors + (DoorId.ROW_1_LEFT to PanelState(if (open) 1 else 0, true)),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override fun nudgeSoc(delta: Int) {
        _vehicle.update { current ->
            val next = ((current.socPercent ?: 0) + delta).coerceIn(0, 100)
            current.copy(socPercent = next, updatedAtEpochMs = System.currentTimeMillis())
        }
    }

    override fun setSampleDestination() = setDestination(SamplePlaces.seoulStation)

    override fun requestSampleTrip() = requestTrip(SamplePlaces.seoulStation)

    override fun simulateArrival() {
        val dest = _destination.value
        val name = dest?.name ?: "서울역"
        val address = dest?.address ?: ""
        val distance = dest?.remainDistance ?: 0
        _arrival.value = ArrivalSnapshot(
            name = name,
            address = address,
            duration = dest?.remainTime ?: 0,
            distance = distance,
            arrivedAtEpochMs = System.currentTimeMillis(),
        )
        _destination.value = null
        _status.value = "도착 시뮬레이션: $name"
    }

    fun setDestination(request: PlaceRequest) {
        val distance = GeoEstimate.haversineMeters(originLat, originLon, request.latitude, request.longitude)
        val eta = GeoEstimate.etaSeconds(distance)
        _destination.value = DestinationSnapshot(
            name = request.name,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            remainDistance = distance,
            remainTime = eta,
            tollFare = 0,
        )
        _arrival.value = null
        _status.value = "시뮬레이션 경로"
    }

    fun requestTrip(request: PlaceRequest) {
        val distance = GeoEstimate.haversineMeters(originLat, originLon, request.latitude, request.longitude)
        val soc = _vehicle.value.socPercent ?: 0
        val drop = (distance / 5_000).coerceAtMost(soc)
        _trip.value = TripEstimate(
            distance = distance,
            eta = GeoEstimate.etaSeconds(distance),
            estimatedSocAtDestPercent = (soc - drop).coerceAtLeast(0),
            energyConsumption = distance / 10.0,
        )
        _status.value = "시뮬레이션 ETA"
    }

    fun refreshDestination() {
        val current = _destination.value ?: return
        setDestination(
            PlaceRequest(
                latitude = current.latitude,
                longitude = current.longitude,
                name = current.name,
                address = current.address,
            ),
        )
    }

    private fun initialVehicle(): VehicleSnapshot {
        val closed = PanelState(position = 0, readable = true)
        return VehicleSnapshot(
            doors = DoorId.entries.associateWith { closed },
            windows = WindowId.entries.associateWith { closed },
            fuelPercent = 40,
            fuelReadable = true,
            socPercent = 62,
            socReadable = true,
            odometerKm = 12_345.6f,
            odometerReadable = true,
            rangeMeters = 180_000f,
            rangeSource = RangeSource.SIMULATED,
            vin = "FAKE-NOT-A-VIN",
            vinReadable = true,
            seats = seatSupport(
                occupancyAreas = setOf(SeatId.DRIVER, SeatId.PASSENGER),
                beltAreas = setOf(SeatId.DRIVER, SeatId.PASSENGER),
                occupancyAccess = SignalAccess.READ,
                beltAccess = SignalAccess.READ,
            ),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun parkedDriving(): DrivingSnapshot = drivingSnapshot(
        gear = GearPosition.PARK,
        raw = GearSelectionCodes.PARK,
        requiresDistractionOptimization = false,
    )

    private fun drivingNow(): DrivingSnapshot = drivingSnapshot(
        gear = GearPosition.DRIVE,
        raw = GearSelectionCodes.DRIVE,
        requiresDistractionOptimization = true,
    )

    private fun drivingSnapshot(
        gear: GearPosition,
        raw: Int,
        requiresDistractionOptimization: Boolean,
    ): DrivingSnapshot = DrivingSnapshot(
        gear = gear,
        gearRaw = raw,
        gearAvailable = true,
        uxAvailable = true,
        requiresDistractionOptimization = requiresDistractionOptimization,
        restrictionMask = if (requiresDistractionOptimization) UxRestrictionBits.NO_KEYBOARD else 0,
        maxRestrictedStringLength = if (requiresDistractionOptimization) 60 else 0,
        motion = MotionResolver.resolve(gear, requiresDistractionOptimization),
    )
}
