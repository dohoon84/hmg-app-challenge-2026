package com.krazylabs.miri.speech

import android.media.AudioAttributes
import com.krazylabs.miri.domain.GearPosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechHaltTest {
    @Test
    fun shiftingIntoDriveOrReverseStopsAndASteadyGearDoesNot() {
        val halt = SpeechHalt()
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.REVERSE, navGuidance = false))
    }

    @Test
    fun navGuidanceStartingStops() {
        val halt = SpeechHalt()
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = true))
    }

    @Test
    fun foreignGuidanceStopsWhileOurOwnGuidanceDoesNot() {
        val self = 10
        val other = 20
        val sonification = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        val guidance = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        val assistant = AudioAttributes.USAGE_ASSISTANT
        assertFalse(foreignNavOrAssistant(sonification, other, self))
        assertFalse(foreignNavOrAssistant(guidance, self, self))
        assertTrue(foreignNavOrAssistant(guidance, other, self))
        assertTrue(foreignNavOrAssistant(assistant, other, self))

        val whileWePlay = SpeechHalt()
        assertFalse(whileWePlay.shouldStop(visible = true, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(
            whileWePlay.shouldStop(
                visible = true,
                gear = GearPosition.PARK,
                navGuidance = foreignNavOrAssistant(guidance, other, self),
            ),
        )
        val ourGuidance = SpeechHalt()
        assertFalse(
            ourGuidance.shouldStop(
                visible = true,
                gear = GearPosition.PARK,
                navGuidance = foreignNavOrAssistant(guidance, self, self),
            ),
        )
    }

    @Test
    fun anonymizedGuidanceUidUsesTheFallbackAndDoesNotStopOurVoice() {
        val self = 10
        val guidance = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        val assistant = AudioAttributes.USAGE_ASSISTANT
        assertFalse(playbackIsNavApp(guidance, clientUid = -1, selfUid = self, guidanceMode = true))
        assertFalse(playbackIsNavApp(guidance, clientUid = 0, selfUid = self, guidanceMode = true))
        assertFalse(playbackIsNavApp(assistant, clientUid = null, selfUid = self, guidanceMode = true))
        assertTrue(playbackIsNavApp(guidance, clientUid = -1, selfUid = self, guidanceMode = false))
        assertTrue(playbackIsNavApp(guidance, clientUid = 20, selfUid = self, guidanceMode = true))
        assertFalse(playbackIsNavApp(guidance, clientUid = self, selfUid = self, guidanceMode = true))

        val halt = SpeechHalt()
        assertFalse(
            halt.shouldStop(
                visible = true,
                gear = GearPosition.PARK,
                navGuidance = playbackIsNavApp(guidance, clientUid = -1, selfUid = self, guidanceMode = true),
            ),
        )
    }

    @Test
    fun aHiddenScreenStops() {
        val halt = SpeechHalt()
        assertTrue(halt.shouldStop(visible = false, gear = GearPosition.PARK, navGuidance = false))
        assertTrue(halt.shouldStop(visible = false, gear = GearPosition.DRIVE, navGuidance = false))
        assertFalse(halt.shouldStop(visible = true, gear = GearPosition.DRIVE, navGuidance = false))
    }
}
