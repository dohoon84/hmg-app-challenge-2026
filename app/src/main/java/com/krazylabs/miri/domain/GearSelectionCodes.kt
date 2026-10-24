package com.krazylabs.miri.domain

/**
 * Integer values of android.car.VehicleGear on API 34.
 * GEAR_SELECTION (CAR_POWERTRAIN) is the primary gear source.
 * Pleos Vehicle.getGear() is a switchable fallback and is not called unless
 * the gear backend is PLEOS.
 */
object GearSelectionCodes {
    const val UNKNOWN = 0
    const val NEUTRAL = 1
    const val REVERSE = 2
    const val PARK = 4
    const val DRIVE = 8
    const val FIRST = 16
    const val SECOND = 32
    const val THIRD = 64
    const val FOURTH = 128
    const val FIFTH = 256
    const val SIXTH = 512
    const val SEVENTH = 1024
    const val EIGHTH = 2048
    const val NINTH = 4096

    private val driveGears = intArrayOf(
        DRIVE, FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH, SEVENTH, EIGHTH, NINTH,
    )

    fun toGear(raw: Int?): GearPosition = when (raw) {
        null -> GearPosition.UNKNOWN
        PARK -> GearPosition.PARK
        NEUTRAL -> GearPosition.NEUTRAL
        REVERSE -> GearPosition.REVERSE
        in driveGears -> GearPosition.DRIVE
        else -> GearPosition.OTHER
    }
}
