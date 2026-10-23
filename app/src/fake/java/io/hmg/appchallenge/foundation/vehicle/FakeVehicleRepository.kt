package io.hmg.appchallenge.foundation.vehicle

import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeVehicleRepository @Inject constructor(
    world: FakeCabinWorld,
) : VehicleRepository {
    private val _link = MutableStateFlow(LinkState.READY)
    override val link: StateFlow<LinkState> = _link
    override val snapshot: StateFlow<VehicleSnapshot> = world.vehicle

    override fun start() {
        _link.value = LinkState.READY
    }

    override fun stop() = Unit

    override fun refresh() = Unit
}
