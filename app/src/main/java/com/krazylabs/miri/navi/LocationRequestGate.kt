package com.krazylabs.miri.navi

/**
 * getCurrentLocationInfo runs about 3 seconds after the nav link is READY,
 * and only for a request this gate issued. A refreshRoute callback is not one.
 */
class LocationRequestGate(
    private val delayAfterReadyMs: Long = LOCATION_AFTER_READY_MS,
) {
    private var readyAtMs: Long? = null
    private var requested = false
    private var issued = false

    fun markReady(nowMs: Long) {
        if (readyAtMs == null) readyAtMs = nowMs
    }

    fun request() {
        requested = true
        issued = false
    }

    fun shouldIssue(nowMs: Long): Boolean {
        val ready = readyAtMs ?: return false
        return requested && !issued && nowMs - ready >= delayAfterReadyMs
    }

    fun markIssued() {
        issued = true
    }

    /** True only for the callback of a request this gate has already sent. */
    fun acceptCallback(): Boolean {
        if (!requested || !issued) return false
        requested = false
        issued = false
        return true
    }

    companion object {
        const val LOCATION_AFTER_READY_MS = 3_000L
    }
}
