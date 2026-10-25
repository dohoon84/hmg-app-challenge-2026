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

    /** Increments each time the rental-origin request stores a fix. Route refreshes do not. */
    val hereSerial: StateFlow<Long>
        get() = NavIdle.hereSerial

    /**
     * The fix accepted for the rental-origin request.
     * Route refreshes update [here] and must not replace this.
     */
    val originFix: StateFlow<HereFix?>
        get() = NavIdle.originFix
    val bookmarks: StateFlow<List<PlaceRequest>>
        get() = NavIdle.bookmarks

    fun start()
    fun stop()
    fun setDestination(request: PlaceRequest)
    fun requestTrip(request: PlaceRequest)
    fun refreshDestination()

    /** Re-read gear-independent nav state. Default is a no-op so test fakes stay valid. */
    fun refreshRoute() {}

    /**
     * Ask for [getCurrentLocationInfo]. If the link is not ready yet, the
     * read runs when it becomes ready. Does not open the navigation screen.
     */
    fun requestCurrentLocation() {}

    /**
     * Issues a location read about 3 seconds after the link is READY, and only
     * when [requestCurrentLocation] asked for one. Default does nothing.
     */
    fun pollLocation(nowMs: Long) {}

    /** Ask for [getBookmarkInfo]. The list arrives on the bookmark callback. */
    fun refreshBookmarks() {}
}

private object NavIdle {
    val tripSerial: StateFlow<Long> = MutableStateFlow(0L)
    val charging: StateFlow<Boolean> = MutableStateFlow(false)
    val recents: StateFlow<List<PlaceRequest>> = MutableStateFlow(emptyList())
    val here: StateFlow<HereFix?> = MutableStateFlow(null)
    val hereSerial: StateFlow<Long> = MutableStateFlow(0L)
    val originFix: StateFlow<HereFix?> = MutableStateFlow(null)
    val bookmarks: StateFlow<List<PlaceRequest>> = MutableStateFlow(emptyList())
}
