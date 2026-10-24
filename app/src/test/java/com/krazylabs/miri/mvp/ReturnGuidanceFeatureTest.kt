package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.PlaceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnGuidanceFeatureTest {
    private val place = PlaceRequest(37.5, 127.0, "대여한 곳")

    @Test
    fun aReleaseFlagHidesTheButtonAndSendsNoRoute() {
        assertFalse(ReturnGuidanceFeature.visible(enabled = false))
        val routes = mutableListOf<PlaceRequest>()
        val note = ReturnGuidanceFeature.offer(place, enabled = false) { routes += it }
        assertEquals("", note)
        assertTrue(routes.isEmpty())
    }

    @Test
    fun anEnabledFlagShowsTheNoteAndSendsNoRoute() {
        assertTrue(ReturnGuidanceFeature.visible(enabled = true))
        val routes = mutableListOf<PlaceRequest>()
        val note = ReturnGuidanceFeature.offer(place, enabled = true) { routes += it }
        assertEquals(ReturnGuidanceFeature.NOTE, note)
        assertEquals("권한 공개 후 제공", note)
        assertTrue(routes.isEmpty())
        val missing = ReturnGuidanceFeature.offer(null, enabled = true) { routes += it }
        assertEquals(ReturnGuidanceFeature.NOTE, missing)
        assertTrue(routes.isEmpty())
    }
}
