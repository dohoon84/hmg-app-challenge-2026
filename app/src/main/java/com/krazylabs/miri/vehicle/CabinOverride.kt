package com.krazylabs.miri.vehicle

import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.VehicleSnapshot

/**
 * Debug playback can stand in for gear, doors, and charge.
 * Release returns the live values unchanged.
 */
interface CabinOverride {
    fun gear(live: GearPosition): GearPosition = live
    fun overlay(live: VehicleSnapshot): VehicleSnapshot = live
}
