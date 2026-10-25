package com.krazylabs.miri.vehicle

import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

interface VehicleRepository {
    val link: StateFlow<LinkState>
    val snapshot: StateFlow<VehicleSnapshot>

    fun start()
    fun stop()
    fun refresh()

    /** Debug override. Blank keeps the build-time gear backend. */
    fun setGearBackend(mode: String) {}
}
