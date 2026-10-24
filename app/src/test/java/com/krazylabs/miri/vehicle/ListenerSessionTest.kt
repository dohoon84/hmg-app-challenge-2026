package com.krazylabs.miri.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerSessionTest {
    @Test
    fun closeDropsTheTokenAndRunsCleanups() {
        val session = ListenerSession()
        val token = session.open()
        var ran = 0
        assertTrue(session.track(token) { ran += 1 })
        assertTrue(session.isCurrent(token))
        session.close()
        assertEquals(1, ran)
        assertFalse(session.isCurrent(token))
    }

    @Test
    fun trackAfterCloseUnregistersImmediately() {
        val session = ListenerSession()
        val token = session.open()
        session.close()
        var ran = 0
        assertFalse(session.track(token) { ran += 1 })
        assertEquals(1, ran)
    }

    @Test
    fun startStopStartUsesANewToken() {
        val session = ListenerSession()
        val first = session.open()
        session.close()
        val second = session.open()
        assertFalse(session.isCurrent(first))
        assertTrue(session.isCurrent(second))
        assertTrue(first != second)
        var ran = 0
        assertFalse(session.track(first) { ran += 1 })
        assertTrue(session.track(second) { ran += 10 })
        assertEquals(1, ran)
        session.close()
        assertEquals(11, ran)
    }
}
