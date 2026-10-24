package com.krazylabs.miri.di

import org.junit.Assert.assertTrue
import org.junit.Test

class PleosDistractionPolicyTest {
    @Test
    fun pleosFailClosedPolicyIsOn() {
        assertTrue(PleosPolicyModule.distractionPolicy().failClosed)
    }
}
