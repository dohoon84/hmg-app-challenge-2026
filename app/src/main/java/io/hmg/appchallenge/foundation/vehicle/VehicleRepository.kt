package io.hmg.appchallenge.foundation.vehicle

import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

interface VehicleRepository {
    val link: StateFlow<LinkState>
    val snapshot: StateFlow<VehicleSnapshot>

    fun start()
    fun stop()
    fun refresh()
}
