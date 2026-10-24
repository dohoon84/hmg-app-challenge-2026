package com.krazylabs.miri.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun reconnectWaitsThreeSecondsAfterReadyBeforeTheOriginRequest() {
        val gate = LocationRequestGate()
        gate.markReady(1_000)
        gate.request()
        assertTrue(gate.shouldIssue(1_000 + LocationRequestGate.LOCATION_AFTER_READY_MS))
        gate.markIssued()
        gate.markReady(20_000)
        assertFalse(gate.acceptCallback())
        assertFalse(gate.shouldIssue(20_000 + 2_999))
        assertTrue(gate.shouldIssue(20_000 + LocationRequestGate.LOCATION_AFTER_READY_MS))
        gate.markIssued()
        assertTrue(gate.acceptCallback())
        assertFalse(gate.acceptCallback())
    }

    @Test
    fun aRouteReadWaitsWhileTheOriginRequestIsInFlight() {
        val gate = LocationRequestGate()
        gate.markReady(0)
        assertTrue(gate.beginRouteRead())
        gate.request()
        assertFalse(gate.shouldIssue(10_000))
        assertFalse(gate.acceptCallback())
        assertEquals(LocationKind.ROUTE, gate.consume())
        assertTrue(gate.shouldIssue(10_000))
        assertFalse(gate.beginRouteRead())
        assertFalse(gate.shouldIssue(2_999))
        assertTrue(gate.shouldIssue(LocationRequestGate.LOCATION_AFTER_READY_MS))
        gate.markIssued()
        assertEquals(LocationKind.ORIGIN, gate.consume())
        assertNull(gate.consume())
        assertTrue(gate.beginRouteRead())
    }
}
