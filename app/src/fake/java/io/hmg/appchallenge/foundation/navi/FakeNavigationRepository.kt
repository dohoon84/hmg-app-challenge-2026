package io.hmg.appchallenge.foundation.navi

import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.TripEstimate
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNavigationRepository @Inject constructor(
    private val world: FakeCabinWorld,
) : NavigationRepository {
    private val started = AtomicBoolean(false)
    private val _link = MutableStateFlow(LinkState.IDLE)
    override val link: StateFlow<LinkState> = _link
    override val destination: StateFlow<DestinationSnapshot?> = world.destination
    override val arrival: StateFlow<ArrivalSnapshot?> = world.arrival
    override val trip: StateFlow<TripEstimate?> = world.trip
    override val statusMessage: StateFlow<String?> = world.statusMessage

    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        startCount += 1
        _link.value = LinkState.READY
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        stopCount += 1
        _link.value = LinkState.IDLE
    }

    override fun setDestination(request: PlaceRequest) = world.setDestination(request)

    override fun requestTrip(request: PlaceRequest) = world.requestTrip(request)

    override fun refreshDestination() = world.refreshDestination()
}
