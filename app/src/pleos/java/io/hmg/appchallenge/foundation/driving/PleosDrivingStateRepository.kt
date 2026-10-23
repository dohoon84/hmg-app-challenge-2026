package io.hmg.appchallenge.foundation.driving

import io.hmg.appchallenge.foundation.car.PleosCarClient
import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PleosDrivingStateRepository @Inject constructor(
    private val carClient: PleosCarClient,
) : DrivingStateRepository {
    override val state: StateFlow<DrivingSnapshot> = carClient.driving

    override fun start() = carClient.start()

    override fun stop() = carClient.stop()
}
