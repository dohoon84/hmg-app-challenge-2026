package io.hmg.appchallenge.foundation.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.TripEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val helper = NaviHelper(context)
    private val started = AtomicBoolean(false)
    private val listenerAdded = AtomicBoolean(false)

    private val _link = MutableStateFlow(LinkState.IDLE)
    override val link: StateFlow<LinkState> = _link

    private val _destination = MutableStateFlow<DestinationSnapshot?>(null)
    override val destination: StateFlow<DestinationSnapshot?> = _destination

    private val _arrival = MutableStateFlow<ArrivalSnapshot?>(null)
    override val arrival: StateFlow<ArrivalSnapshot?> = _arrival

    private val _trip = MutableStateFlow<TripEstimate?>(null)
    override val trip: StateFlow<TripEstimate?> = _trip

    private val _status = MutableStateFlow<String?>(null)
    override val statusMessage: StateFlow<String?> = _status

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
        }

        override fun onError(errorCode: NaviErrorCode) {
            _status.value = errorCode.name
        }
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        _link.value = LinkState.CONNECTING
        ensureListener()
        try {
            helper.initialize(
                {
                    _link.value = LinkState.READY
                    refreshDestination()
                },
                { error ->
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
        if (listenerAdded.compareAndSet(true, false)) {
            try {
                helper.removeListener(listener)
            } catch (error: Throwable) {
                _status.value = error.message
            }
        }
        try {
            helper.release()
        } catch (error: Throwable) {
            _status.value = error.message
        }
        _link.value = LinkState.IDLE
    }

    override fun setDestination(request: PlaceRequest) {
        if (_link.value != LinkState.READY) {
            _status.value = "내비에 연결되지 않았습니다"
            return
        }
        try {
            helper.requestRoute(
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
        if (_link.value != LinkState.READY) {
            _status.value = "내비에 연결되지 않았습니다"
            return
        }
        try {
            helper.requestTrip(
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
        if (_link.value != LinkState.READY && _link.value != LinkState.CONNECTING) return
        try {
            helper.getDestinationInfo()
        } catch (error: Throwable) {
            _status.value = error.message ?: error.javaClass.simpleName
        }
    }

    private fun ensureListener() {
        if (listenerAdded.compareAndSet(false, true)) {
            helper.addListener(listener)
        }
    }
}
