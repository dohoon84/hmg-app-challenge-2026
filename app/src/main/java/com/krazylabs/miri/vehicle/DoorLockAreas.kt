package com.krazylabs.miri.vehicle

/**
 * DOOR_LOCK area ids. ROW_1_LEFT is bit 0x01. The emulator publishes one
 * combined area 0x55 (all four cabin doors) plus the hood 0x10000000.
 * A real car may publish one area per door. Either form is accepted when
 * the driver bit is set.
 */
object DoorLockAreas {
    const val DRIVER_BIT = 0x01

    fun containsDriver(areaBits: Int): Boolean = areaBits and DRIVER_BIT != 0

    fun mask(ids: Iterable<Int>): Int = ids.fold(0) { mask, id -> mask or id }
}
