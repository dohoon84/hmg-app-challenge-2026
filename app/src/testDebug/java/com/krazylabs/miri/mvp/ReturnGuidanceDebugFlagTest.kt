package com.krazylabs.miri.mvp

import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.domain.PlaceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnGuidanceDebugFlagTest {
    @Test
    fun theDebugBuildShowsTheButtonAndSendsNoRoute() {
        assertTrue(BuildConfig.RETURN_GUIDANCE)
        assertTrue(ReturnGuidanceFeature.visible())
        val routes = mutableListOf<PlaceRequest>()
        val note = ReturnGuidanceFeature.offer(PlaceRequest(37.5, 127.0, "대여한 곳")) { routes += it }
        assertEquals(ReturnGuidanceFeature.NOTE, note)
        assertTrue(routes.isEmpty())
    }
}
