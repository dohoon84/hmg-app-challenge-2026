package com.krazylabs.miri.navi

import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.TripEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HereFix(
    val latitude: Double,
    val longitude: Double,
    val address: String,
)

data class RoadCounts(
    val routing: Boolean = false,
    val routeId: String = "",
    val tolls: Int = 0,
    val highways: Int = 0,
) {
    companion object {
        val IDLE: StateFlow<RoadCounts> = MutableStateFlow(RoadCounts())
    }
}

interface NavigationRepository {
    val link: StateFlow<LinkState>
    val destination: StateFlow<DestinationSnapshot?>
    val arrival: StateFlow<ArrivalSnapshot?>
    val trip: StateFlow<TripEstimate?>

    /** Increments when a trip response for the latest [requestTrip] is stored. */
    val tripSerial: StateFlow<Long>
        get() = NavIdle.tripSerial
    val statusMessage: StateFlow<String?>
    val road: StateFlow<RoadCounts>
        get() = RoadCounts.IDLE
    val chargingStation: StateFlow<Boolean>
        get() = NavIdle.charging
    val recentPlaces: StateFlow<List<PlaceRequest>>
        get() = NavIdle.recents
    val here: StateFlow<HereFix?>
        get() = NavIdle.here

    fun start()
    fun stop()
    fun setDestination(request: PlaceRequest)
    fun requestTrip(request: PlaceRequest)
    fun refreshDestination()

    /** Re-read gear-independent nav state. Default is a no-op so test fakes stay valid. */
    fun refreshRoute() {}
}

private object NavIdle {
    val tripSerial: StateFlow<Long> = MutableStateFlow(0L)
    val charging: StateFlow<Boolean> = MutableStateFlow(false)
    val recents: StateFlow<List<PlaceRequest>> = MutableStateFlow(emptyList())
    val here: StateFlow<HereFix?> = MutableStateFlow(null)
}
