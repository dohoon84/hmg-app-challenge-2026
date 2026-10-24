package com.krazylabs.miri.sim

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisabledCabinSimulator @Inject constructor() : CabinSimulator {
    override val enabled: Boolean = false

    override fun setParked(parked: Boolean) = Unit

    override fun setDriverDoorOpen(open: Boolean) = Unit

    override fun nudgeSoc(delta: Int) = Unit

    override fun simulateArrival() = Unit

    override fun setSampleDestination() = Unit

    override fun requestSampleTrip() = Unit
}
