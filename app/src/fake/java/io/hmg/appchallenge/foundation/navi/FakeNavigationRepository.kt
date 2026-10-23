package io.hmg.appchallenge.foundation.navi

import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.TripEstimate
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNavigationRepository @Inject constructor(
    private val world: FakeCabinWorld,
) : NavigationRepository {
    private val _link = MutableStateFlow(LinkState.READY)
    override val link: StateFlow<LinkState> = _link
    override val destination: StateFlow<DestinationSnapshot?> = world.destination
    override val arrival: StateFlow<ArrivalSnapshot?> = world.arrival
    override val trip: StateFlow<TripEstimate?> = world.trip
    override val statusMessage: StateFlow<String?> = world.statusMessage

    override fun start() {
        _link.value = LinkState.READY
    }

    override fun stop() = Unit

    override fun setDestination(request: PlaceRequest) = world.setDestination(request)

    override fun requestTrip(request: PlaceRequest) = world.requestTrip(request)

    override fun refreshDestination() = world.refreshDestination()
}
