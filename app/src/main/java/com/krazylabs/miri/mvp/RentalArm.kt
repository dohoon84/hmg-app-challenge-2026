package com.krazylabs.miri.mvp

/**
 * New rental: locked for the threshold, then unlocked, driver seated, and Park.
 * Occupancy null does not block the trigger. A forced arm is the debug one-shot.
 */
class RentalArm {
    private var lockedSince: Long? = null
    private var armed = false
    private var ready = false
    private var consumed = false

    fun observe(
        locked: Boolean?,
        occupied: Boolean?,
        parked: Boolean,
        nowMs: Long,
        thresholdMs: Long,
        force: Boolean,
    ) {
        if (force) {
            armed = true
            ready = true
            return
        }
        if (locked == null) return
        if (locked) {
            if (lockedSince == null) lockedSince = nowMs
            if (nowMs - (lockedSince ?: nowMs) >= thresholdMs) armed = true
            return
        }
        lockedSince = null
        if (armed && parked && occupied != false) ready = true
    }

    fun primed(): Boolean = ready

    /** True once, when a new rental should clear the previous return answers. */
    fun takeEdge(): Boolean {
        if (!ready || consumed) return false
        consumed = true
        return true
    }
}
