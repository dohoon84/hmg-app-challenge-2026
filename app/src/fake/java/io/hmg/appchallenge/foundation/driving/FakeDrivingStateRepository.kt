package io.hmg.appchallenge.foundation.driving

import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDrivingStateRepository @Inject constructor(
    world: FakeCabinWorld,
) : DrivingStateRepository {
    override val state: StateFlow<DrivingSnapshot> = world.driving

    override fun start() = Unit

    override fun stop() = Unit
}
