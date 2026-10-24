package com.krazylabs.miri.mvp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The playback choice [com.krazylabs.miri.ui.MiriViewModel] makes for card voice.
 * [CardSession.once] has already consumed the line, so the gate holds it while
 * navigation is speaking instead of letting that tick play over guidance.
 */
class CardVoiceGateTest {
    private val clips = VoicePlay.Clips(listOf("vo_fuel_electric"))

    @Test
    fun cardVoiceWaitsForNavThenPlaysTheHeldLine() {
        val gate = CardVoiceGate()
        assertEquals(VoicePlay.None, gate.decide(clips, cardOpen = true, moving = false, navSpeaking = true, nowMs = 0))
        assertEquals(
            VoicePlay.None,
            gate.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = true, nowMs = 1_000),
        )
        assertEquals(
            clips,
            gate.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = false, nowMs = 2_000),
        )
        assertEquals(
            VoicePlay.None,
            gate.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = false, nowMs = 3_000),
        )
    }

    @Test
    fun quietNavPlaysTheCardVoiceImmediately() {
        val gate = CardVoiceGate()
        assertEquals(clips, gate.decide(clips, cardOpen = true, moving = false, navSpeaking = false, nowMs = 0))
    }

    @Test
    fun driveOrAClosedCardDropsTheHeldVoice() {
        val driving = CardVoiceGate()
        driving.decide(clips, cardOpen = true, moving = false, navSpeaking = true, nowMs = 0)
        assertEquals(
            VoicePlay.None,
            driving.decide(VoicePlay.None, cardOpen = true, moving = true, navSpeaking = false, nowMs = 1_000),
        )
        assertEquals(
            VoicePlay.None,
            driving.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = false, nowMs = 2_000),
        )
        val closed = CardVoiceGate()
        closed.decide(clips, cardOpen = true, moving = false, navSpeaking = true, nowMs = 0)
        assertEquals(
            VoicePlay.None,
            closed.decide(VoicePlay.None, cardOpen = false, moving = false, navSpeaking = false, nowMs = 1_000),
        )
    }

    @Test
    fun theHoldCapDropsTheVoice() {
        val gate = CardVoiceGate()
        gate.decide(clips, cardOpen = true, moving = false, navSpeaking = true, nowMs = 0)
        assertEquals(
            VoicePlay.None,
            gate.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = false, nowMs = CardVoiceGate.HOLD_CAP_MS),
        )
        assertEquals(
            VoicePlay.None,
            gate.decide(VoicePlay.None, cardOpen = true, moving = false, navSpeaking = false, nowMs = CardVoiceGate.HOLD_CAP_MS + 1),
        )
    }
}
