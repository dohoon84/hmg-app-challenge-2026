package com.krazylabs.miri.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.TripEstimate
import com.krazylabs.miri.mvp.TbtGuard
import com.krazylabs.miri.mvp.countRoad
import com.krazylabs.miri.mvp.bookmarkRowLabel
import com.krazylabs.miri.mvp.navPlaceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NaviHelper 3.0.5. Destination is coordinate-based via [RouteInfo].
 * [requestTrip] asks for ETA without committing the active route.
 * Events depend on the navigation app. Emulator delivery is unverified.
 * Empty poiId is passed through; whether the nav app accepts it is unverified.
 */
@Singleton
class PleosNavigationRepository @Inject constructor(
    @ApplicationContext context: Context,
) : NavigationRepository {
    private val appContext = context
    @Volatile private var helper: NaviHelper? = null
    private val started = AtomicBoolean(false)

    private val _link = MutableStateFlow(LinkState.IDLE)
    override val link: StateFlow<LinkState> = _link

    private val _destination = MutableStateFlow<DestinationSnapshot?>(null)
    override val destination: StateFlow<DestinationSnapshot?> = _destination

    private val _arrival = MutableStateFlow<ArrivalSnapshot?>(null)
    override val arrival: StateFlow<ArrivalSnapshot?> = _arrival

    private val _trip = MutableStateFlow<TripEstimate?>(null)
    override val trip: StateFlow<TripEstimate?> = _trip
    private val _tripSerial = MutableStateFlow(0L)
    override val tripSerial: StateFlow<Long> = _tripSerial
    private var tripRequest = 0L

    private val _status = MutableStateFlow<String?>(null)
    override val statusMessage: StateFlow<String?> = _status

    private val _road = MutableStateFlow(RoadCounts())
    override val road: StateFlow<RoadCounts> = _road
    private val _charging = MutableStateFlow(false)
    override val chargingStation: StateFlow<Boolean> = _charging
    private val _recents = MutableStateFlow<List<PlaceRequest>>(emptyList())
    override val recentPlaces: StateFlow<List<PlaceRequest>> = _recents
    private val _bookmarks = MutableStateFlow<List<PlaceRequest>>(emptyList())
    override val bookmarks: StateFlow<List<PlaceRequest>> = _bookmarks
    private val _here = MutableStateFlow<HereFix?>(null)
    override val here: StateFlow<HereFix?> = _here
    private val _hereSerial = MutableStateFlow(0L)
    override val hereSerial: StateFlow<Long> = _hereSerial
    private val wantLocation = AtomicBoolean(false)
    /** A resume or reconnect still needs getDestinationInfo after the link is READY. */
    private val destinationAfterReady = AtomicBoolean(false)
    private val tbt = TbtGuard()
    private var routing = false

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            _destination.value = DestinationSnapshot(
                name = destinationInfo.name,
                address = destinationInfo.address,
                latitude = destinationInfo.latitude,
                longitude = destinationInfo.longitude,
                remainDistance = destinationInfo.remainDistance,
                remainTime = destinationInfo.remainTime,
                tollFare = destinationInfo.tollFare,
            )
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _arrival.value = ArrivalSnapshot(
                name = destinationArrivedInfo.name,
                address = destinationArrivedInfo.address,
                duration = destinationArrivedInfo.duration,
                distance = destinationArrivedInfo.distance,
                arrivedAtEpochMs = System.currentTimeMillis(),
            )
            _status.value = "도착"
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            _trip.value = TripEstimate(
                distance = tripInfo.distance,
                eta = tripInfo.eta,
                estimatedSocAtDestPercent = tripInfo.estimatedSocAtDest,
                energyConsumption = tripInfo.energyConsumption,
            )
            _tripSerial.value = tripRequest
        }

        override fun onError(errorCode: NaviErrorCode) {
            _status.value = errorCode.name
        }

        override fun onRouteStateInfo(info: ai.pleos.playground.navi.data.RouteStateInfo) {
            routing = info.isRouting
            if (!routing) tbt.clear()
            _road.update { it.copy(routing = routing, tolls = if (routing) it.tolls else 0, highways = if (routing) it.highways else 0) }
        }

        override fun onRouteCancelled() {
            routing = false
            tbt.clear()
            _road.value = RoadCounts()
            _destination.value = null
            _charging.value = false
        }

        override fun onTBTInfo(list: List<ai.pleos.playground.navi.data.TBTInfo>) {
            val names = list.map { it.type.name }
            val kept = tbt.accept(names, routing)
            val (tolls, highways) = countRoad(kept)
            _road.update { it.copy(tolls = tolls, highways = highways, routing = routing) }
        }

        override fun onDestinationChanged() {
            rereadDestination()
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            rereadDestination()
        }

        override fun onCurrentLocationInfo(info: ai.pleos.playground.navi.data.CurrentLocationInfo) {
            val id = info.routeId
            _road.update { it.copy(routeId = id) }
            _here.value = HereFix(info.latitude, info.longitude, info.address.orEmpty())
            _hereSerial.value = _hereSerial.value + 1
            wantLocation.set(false)
        }

        override fun onRecentDestinationInfo(list: List<ai.pleos.playground.navi.data.RecentDestinationInfo>) {
            _recents.value = list.take(RECENT_CAP).map { item ->
                val name = item.name.orEmpty()
                PlaceRequest(
                    latitude = item.latitude,
                    longitude = item.longitude,
                    name = name,
                    address = item.address.orEmpty(),
                    poiId = navPlaceId("recent", item.poiId.orEmpty(), name, item.latitude, item.longitude),
                    poiSubId = item.poiSubId.orEmpty(),
                )
            }
        }

        override fun onBookmarkInfo(list: List<BookmarkInfo>) {
            Log.i(NAV_TAG, "bookmarks count=${list.size}")
            list.forEach { item ->
                Log.i(
                    NAV_TAG,
                    "bookmark lat=${item.latitude} lon=${item.longitude} " +
                        "displayLat=${item.displayLatitude} displayLon=${item.displayLongitude} " +
                        "name=${item.name} poiId=${item.poiId} poiSubId=${item.poiSubId} " +
                        "address=${item.address} category=${item.category} provider=${item.poiProvider}",
                )
            }
            _bookmarks.value = list.map { item ->
                val address = item.address.orEmpty()
                val name = bookmarkRowLabel(item.name, address, item.latitude, item.longitude)
                PlaceRequest(
                    latitude = item.latitude,
                    longitude = item.longitude,
                    name = name,
                    address = address,
                    poiId = navPlaceId("bookmark", item.poiId.orEmpty(), name, item.latitude, item.longitude),
                    poiSubId = item.poiSubId.orEmpty(),
                )
            }
        }

        override fun onDrivingInfo(info: ai.pleos.playground.navi.data.DrivingInfo) {
            _charging.value = info.destination.isChargingStation
        }
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        destinationAfterReady.set(true)
        val created = NaviHelper(appContext)
        helper = created
        _link.value = LinkState.CONNECTING
        try {
            created.addListener(listener)
            created.initialize(
                {
                    if (!started.get() || helper !== created) return@initialize
                    _link.value = LinkState.READY
                    readDestination(created)
                    if (destinationAfterReady.getAndSet(false)) readDestination(created)
                    readLocation(created)
                    readBookmarks(created)
                },
                { error ->
                    if (!started.get() || helper !== created) return@initialize
                    _link.value = LinkState.FAILED
                    _status.value = error.message ?: error.javaClass.simpleName
                },
            )
        } catch (error: Throwable) {
            _link.value = LinkState.FAILED
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        val current = helper
        helper = null
        if (current != null) {
            try {
                current.removeListener(listener)
            } catch (error: Throwable) {
                _status.value = error.message
            }
            try {
                current.release()
            } catch (error: Throwable) {
                _status.value = error.message
            }
        }
        _link.value = LinkState.IDLE
    }

    override fun setDestination(request: PlaceRequest) {
        val current = helper
        if (_link.value != LinkState.READY || current == null) {
            _status.value = "내비에 연결되지 않았습니다"
            return
        }
        try {
            current.requestRoute(
                RouteInfo(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = request.name,
                    poiId = request.poiId,
                    address = request.address,
                    poiSubId = request.poiSubId,
                    routeOption = RouteOption.RECOMMENDED,
                ),
            )
            _status.value = "경로 요청"
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    override fun requestTrip(request: PlaceRequest) {
        val current = helper
        if (_link.value != LinkState.READY || current == null) {
            _status.value = "내비에 연결되지 않았습니다"
            return
        }
        try {
            tripRequest += 1
            current.requestTrip(
                TripRequest(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    poiId = request.poiId,
                    poiName = request.name,
                    poiSubId = request.poiSubId,
                ),
            )
            _status.value = "ETA 요청"
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    override fun refreshDestination() {
        val current = helper ?: return
        if (_link.value != LinkState.READY) return
        readDestination(current)
    }

    override fun refreshRoute() {
        val current = helper
        if (current == null || _link.value != LinkState.READY) {
            destinationAfterReady.set(true)
            return
        }
        listOf(
            { current.getRouteStateInfo() },
            { current.getTBTInfo() },
            { current.getDestinationInfo() },
            { current.getCurrentLocationInfo() },
            { current.getRecentDestinationInfo() },
            { current.getBookmarkInfo() },
        ).forEach { call ->
            try {
                call()
            } catch (error: Throwable) {
                _status.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    override fun requestCurrentLocation() {
        wantLocation.set(true)
        val current = helper ?: return
        readLocation(current)
    }

    override fun refreshBookmarks() {
        val current = helper ?: return
        readBookmarks(current)
    }

    private fun rereadDestination() {
        val current = helper
        if (current == null || !started.get() || _link.value != LinkState.READY) {
            destinationAfterReady.set(true)
            return
        }
        readDestination(current)
    }

    private fun readDestination(target: NaviHelper) {
        if (!started.get() || helper !== target || _link.value != LinkState.READY) return
        try {
            target.getDestinationInfo()
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun readLocation(target: NaviHelper) {
        if (!wantLocation.get() || !started.get() || helper !== target || _link.value != LinkState.READY) return
        try {
            target.getCurrentLocationInfo()
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun readBookmarks(target: NaviHelper) {
        if (!started.get() || helper !== target || _link.value != LinkState.READY) return
        try {
            target.getBookmarkInfo()
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    private companion object {
        const val NAV_TAG = "MiriNav"
        const val RECENT_CAP = 20
    }
}
