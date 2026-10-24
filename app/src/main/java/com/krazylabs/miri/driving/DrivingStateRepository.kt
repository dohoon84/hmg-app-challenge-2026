package com.krazylabs.miri.driving

import com.krazylabs.miri.domain.DrivingSnapshot
import kotlinx.coroutines.flow.StateFlow

interface DrivingStateRepository {
    val state: StateFlow<DrivingSnapshot>

    fun start()
    fun stop()
}
