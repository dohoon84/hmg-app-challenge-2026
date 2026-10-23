package io.hmg.appchallenge.foundation.driving

import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDrivingStateRepository @Inject constructor(
    world: FakeCabinWorld,
) : DrivingStateRepository {
    private val started = AtomicBoolean(false)
    override val state: StateFlow<DrivingSnapshot> = world.driving

    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        startCount += 1
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        stopCount += 1
    }
}
