package com.krazylabs.miri.sim

/**
 * Debug controls for the fake flavor. The pleos flavor binds a disabled implementation.
 * Korea is left-hand drive, so the driver door is DoorId.ROW_1_LEFT.
 */
interface CabinSimulator {
    val enabled: Boolean

    fun setParked(parked: Boolean)
    fun setDriverDoorOpen(open: Boolean)
    fun nudgeSoc(delta: Int)
    fun simulateArrival()

    /** Fake flavor only. Pleos binds a no-op so release builds have no sample destination. */
    fun setSampleDestination()
    fun requestSampleTrip()
}
