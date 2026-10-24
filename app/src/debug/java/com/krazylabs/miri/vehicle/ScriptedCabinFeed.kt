package com.krazylabs.miri.vehicle

import com.krazylabs.miri.domain.DoorId
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.PanelState
import com.krazylabs.miri.domain.VehicleSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stands in for gear, the rear door, and SOC while a debug playback runs.
 * It never writes Vehicle Control or the GNSS HAL. Release builds do not
 * contain this class.
 */
@Singleton
class ScriptedCabinFeed @Inject constructor() : CabinOverride {
    private val gate = AtomicBoolean(false)

    @Volatile var gear: GearPosition = GearPosition.PARK
    @Volatile var rearDoorOpen: Boolean = false
    @Volatile var soc: Int = 90

    val engaged: Boolean get() = gate.get()

    fun engage(gear: GearPosition = GearPosition.PARK, soc: Int = 90) {
        this.gear = gear
        this.soc = soc
        rearDoorOpen = false
        gate.set(true)
    }

    fun release() {
        gate.set(false)
        rearDoorOpen = false
    }

    override fun gear(live: GearPosition): GearPosition = if (engaged) gear else live

    override fun overlay(live: VehicleSnapshot): VehicleSnapshot {
        if (!engaged) return live
        val closed = PanelState(position = 0, readable = true)
        val doors = DoorId.entries.associateWith { id ->
            if (rearDoorOpen && id == DoorId.ROW_2_RIGHT) PanelState(position = 1, readable = true) else closed
        }
        return live.copy(doors = doors, socPercent = soc, socReadable = true)
    }
}
