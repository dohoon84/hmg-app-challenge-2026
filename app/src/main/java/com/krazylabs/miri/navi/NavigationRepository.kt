package com.krazylabs.miri.navi

import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.TripEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    val statusMessage: StateFlow<String?>
    val road: StateFlow<RoadCounts>
        get() = RoadCounts.IDLE

    fun start()
    fun stop()
    fun setDestination(request: PlaceRequest)
    fun requestTrip(request: PlaceRequest)
    fun refreshDestination()
}
