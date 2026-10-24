package com.krazylabs.miri.vehicle

import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.sim.FakeCabinWorld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeVehicleRepository @Inject constructor(
    world: FakeCabinWorld,
) : VehicleRepository {
    private val started = AtomicBoolean(false)
    private val _link = MutableStateFlow(LinkState.IDLE)
    override val link: StateFlow<LinkState> = _link
    override val snapshot: StateFlow<VehicleSnapshot> = world.vehicle

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

    override fun refresh() = Unit
}
