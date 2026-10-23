package io.hmg.appchallenge.foundation.driving

import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import kotlinx.coroutines.flow.StateFlow

interface DrivingStateRepository {
    val state: StateFlow<DrivingSnapshot>

    fun start()
    fun stop()
}
