package com.krazylabs.miri.navi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRequestGateTest {
    @Test
    fun onlyTheIssuedRequestIsAccepted() {
        val gate = LocationRequestGate()
        gate.markReady(1_000)
        gate.request()
        assertFalse(gate.shouldIssue(1_000 + 2_999))
        assertFalse(gate.acceptCallback())
        assertTrue(gate.shouldIssue(1_000 + LocationRequestGate.LOCATION_AFTER_READY_MS))
        gate.markIssued()
        assertFalse(gate.shouldIssue(1_000 + 5_000))
        assertTrue(gate.acceptCallback())
        assertFalse(gate.acceptCallback())
    }

    @Test
    fun aRefreshBeforeTheRequestIsIssuedIsIgnored() {
        val gate = LocationRequestGate()
        gate.markReady(0)
        assertFalse(gate.acceptCallback())
        gate.request()
        assertFalse(gate.acceptCallback())
        assertTrue(gate.shouldIssue(LocationRequestGate.LOCATION_AFTER_READY_MS))
    }
}
