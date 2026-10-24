package com.krazylabs.miri.navi

/**
 * One location read is in flight at a time.
 * The rental origin is the callback of the request this gate issued, about
 * 3 seconds after READY. A route refresh is a different callback: it may
 * update the route id and the live fix, and it is not the origin.
 * Each READY, including a reconnect, starts that wait over. A callback from
 * the previous link is dropped.
 */
class LocationRequestGate(
    private val delayAfterReadyMs: Long = LOCATION_AFTER_READY_MS,
) {
    private var readyAtMs: Long? = null
    private var requested = false
    private var issued = false
    private var generation = 0
    private var inflight = LocationKind.NONE
    private var inflightGeneration = 0

    fun markReady(nowMs: Long) {
        readyAtMs = nowMs
        issued = false
        generation += 1
        inflight = LocationKind.NONE
    }

    fun request() {
        requested = true
        issued = false
    }

    /** True while the origin request has not accepted its callback yet. */
    fun originAwaiting(): Boolean = requested

    fun shouldIssue(nowMs: Long): Boolean {
        if (inflight == LocationKind.ROUTE) return false
        val ready = readyAtMs ?: return false
        return requested && !issued && nowMs - ready >= delayAfterReadyMs
    }

    fun markIssued() {
        issued = true
        generation += 1
        inflight = LocationKind.ORIGIN
        inflightGeneration = generation
    }

    /**
     * A route-id refresh. False while the origin request is in flight or
     * another location read has not come back yet, so the two responses
     * cannot be mixed.
     */
    fun beginRouteRead(): Boolean {
        if (requested || inflight != LocationKind.NONE) return false
        generation += 1
        inflight = LocationKind.ROUTE
        inflightGeneration = generation
        return true
    }

    /** The call was not sent. The origin request, if any, can be issued later. */
    fun abandonInflight() {
        inflight = LocationKind.NONE
        issued = false
        generation += 1
    }

    /** True only for the callback of an origin request this gate has sent. */
    fun acceptCallback(): Boolean {
        if (inflight != LocationKind.ORIGIN) return false
        return consume() == LocationKind.ORIGIN
    }

    /** ORIGIN, ROUTE, or null when this callback is stale. */
    fun consume(): LocationKind? {
        val kind = inflight
        val gen = inflightGeneration
        if (kind == LocationKind.NONE || gen != generation) {
            inflight = LocationKind.NONE
            return null
        }
        inflight = LocationKind.NONE
        if (kind == LocationKind.ORIGIN) {
            if (!requested || !issued) return null
            requested = false
            issued = false
        }
        return kind
    }

    companion object {
        const val LOCATION_AFTER_READY_MS = 3_000L
    }
}

enum class LocationKind { NONE, ORIGIN, ROUTE }
