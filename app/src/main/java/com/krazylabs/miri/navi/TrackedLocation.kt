package com.krazylabs.miri.navi

import com.krazylabs.miri.mvp.usableFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Applies one [getCurrentLocationInfo] callback.
 * ORIGIN stores the rental fix and bumps [hereSerial].
 * ROUTE updates [road]'s route id and the live [here], and leaves the origin fix.
 * A stale callback, including one from before a reconnect, changes nothing.
 */
class TrackedLocation(
    private val gate: LocationRequestGate = LocationRequestGate(),
) {
    private val _road = MutableStateFlow(RoadCounts())
    val road: StateFlow<RoadCounts> = _road

    private val _here = MutableStateFlow<HereFix?>(null)
    val here: StateFlow<HereFix?> = _here

    private val _originFix = MutableStateFlow<HereFix?>(null)
    val originFix: StateFlow<HereFix?> = _originFix

    private val _hereSerial = MutableStateFlow(0L)
    val hereSerial: StateFlow<Long> = _hereSerial

    fun markReady(nowMs: Long) = gate.markReady(nowMs)

    fun requestOrigin() = gate.request()

    fun originAwaiting(): Boolean = gate.originAwaiting()

    fun shouldIssueOrigin(nowMs: Long): Boolean = gate.shouldIssue(nowMs)

    fun markOriginIssued() = gate.markIssued()

    fun beginRouteRead(): Boolean = gate.beginRouteRead()

    fun abandonInflight() = gate.abandonInflight()

    fun onCurrentLocation(
        routeId: String,
        latitude: Double,
        longitude: Double,
        address: String,
    ): LocationKind? {
        val kind = gate.consume() ?: return null
        _road.update { it.copy(routeId = routeId) }
        val fix = if (usableFix(latitude, longitude)) HereFix(latitude, longitude, address) else null
        if (fix != null) _here.value = fix
        if (kind == LocationKind.ORIGIN && fix != null) {
            _originFix.value = fix
            _hereSerial.value = _hereSerial.value + 1
        }
        return kind
    }

    /** Toll and highway counts from TBT. The route id stays whatever the last fix reported. */
    fun noteCounts(routing: Boolean, tolls: Int, highways: Int) {
        _road.update { it.copy(routing = routing, tolls = tolls, highways = highways) }
    }

    fun clearRoute() {
        _road.value = RoadCounts()
    }
}
