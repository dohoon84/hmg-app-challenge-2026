package io.hmg.appchallenge.foundation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxRestrictionStateTest {
    @Test
    fun unknownUxIsFullyRestrictedWhenFailClosed() {
        val state = UxRestrictionState.from(
            uxAvailable = false,
            mask = 0,
            maxRestrictedStringLength = 120,
            failClosed = true,
        )
        assertFalse(state.uxKnown)
        assertTrue(state.noDialpad)
        assertTrue(state.noFiltering)
        assertTrue(state.limitStringLength)
        assertTrue(state.noKeyboard)
        assertTrue(state.noVideo)
        assertTrue(state.limitContent)
        assertTrue(state.noSetup)
        assertTrue(state.noTextMessage)
        assertTrue(state.noVoiceTranscription)
        assertEquals(UxRestrictionState.UNKNOWN_MAX_STRING_LENGTH, state.maxRestrictedStringLength)
        assertEquals("목적지 이름", state.clip("목적지 이름"))
        assertEquals("SOC 80%", state.clip("SOC 80%"))
        assertEquals(120, state.clip("가".repeat(200)).length)
        assertEquals("문 열림", state.clip("문 열림", preserve = true))
    }

    @Test
    fun reportedZeroLengthDoesNotBlankAndSafetyCopyIsKept() {
        val state = UxRestrictionState.from(
            uxAvailable = true,
            mask = UxRestrictionBits.LIMIT_STRING_LENGTH,
            maxRestrictedStringLength = 0,
            failClosed = true,
        )
        assertEquals("경고", state.clip("경고"))
        val one = state.copy(maxRestrictedStringLength = 1)
        assertEquals("문 열림", one.clip("문 열림", preserve = true))
        assertEquals("문", one.clip("문 열림"))
    }

    @Test
    fun knownMaskExposesOnlyTheSetBits() {
        val state = UxRestrictionState.from(
            uxAvailable = true,
            mask = UxRestrictionBits.NO_VIDEO or UxRestrictionBits.LIMIT_STRING_LENGTH,
            maxRestrictedStringLength = 4,
            failClosed = true,
        )
        assertTrue(state.uxKnown)
        assertTrue(state.noVideo)
        assertTrue(state.limitStringLength)
        assertFalse(state.noKeyboard)
        assertFalse(state.noSetup)
        assertEquals("abcd", state.clip("abcdef"))
    }

    @Test
    fun fakeUnknownDoesNotInventRestrictions() {
        val state = UxRestrictionState.from(
            uxAvailable = false,
            mask = 0,
            maxRestrictedStringLength = 0,
            failClosed = false,
        )
        assertFalse(state.noKeyboard)
        assertFalse(state.noVideo)
        assertFalse(state.limitStringLength)
        assertEquals("그대로", state.clip("그대로"))
    }
}

class WindowTokenTest {
    @Test
    fun portraitIsTheDefaultAndDrivingStaysANormalWindow() {
        assertEquals(840, WindowTokens.appWidthDp(landscape = false))
        assertEquals(1700, WindowTokens.appWidthDp(landscape = true))
        assertEquals(1184, WindowTokens.APPLICATION_HEIGHT_DP)
        assertEquals(840, WindowTokens.DRIVING_VIEW_WIDTH_DP)
        assertEquals(1280, WindowTokens.DRIVING_VIEW_HEIGHT_DP)
        assertTrue(WindowTokens.DRAWS_IN_NORMAL_WINDOW)
    }
}
