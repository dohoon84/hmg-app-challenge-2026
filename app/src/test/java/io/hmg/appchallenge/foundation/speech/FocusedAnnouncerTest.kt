package io.hmg.appchallenge.foundation.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusedAnnouncerTest {
    @Test
    fun readyEngineSpeaksAfterTheLeadingSilence() {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        assertEquals(FocusedAnnouncer.LEADING_SILENCE_MS, harness.scheduler.delayMs)
        assertEquals(1, harness.focus.requests)
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals("안녕", harness.tts.last)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun engineNotReadyPlaysTheClip() {
        val harness = Harness(ready = false)
        harness.announcer.announce("안녕")
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(1, harness.chime.plays)
    }

    @Test
    fun speakFailureFallsBackToTheClip() {
        val harness = Harness(ready = true, speakFails = true)
        harness.announcer.announce("안녕")
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(1, harness.tts.stops)
        assertEquals(1, harness.chime.plays)
    }

    @Test
    fun focusLossDuringSilenceDoesNotPlay() {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.focus.onChange?.invoke(SpeechFocusLoss.LOST)
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        assertEquals(1, harness.focus.abandons)
        assertNull(harness.scheduler.pending)
    }

    @Test
    fun suspendAbandonsFocusAndDropsTheDelayedCallback() {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.announcer.onSuspend()
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        assertEquals(1, harness.focus.abandons)
        val requests = harness.focus.requests
        harness.announcer.announce("다시")
        assertEquals(requests, harness.focus.requests)
    }

    @Test
    fun foregroundAllowsANewAnnouncementWithoutReplaying() {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.announcer.onSuspend()
        harness.announcer.onForeground()
        assertEquals(0, harness.tts.speaks)
        harness.announcer.announce("다시")
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals("다시", harness.tts.last)
    }

    @Test
    fun navigationAlreadyPlayingDoesNotStart() {
        val harness = Harness(ready = true)
        harness.focus.navigationActive = true
        harness.announcer.announce("안녕")
        assertEquals(0, harness.focus.requests)
        assertNull(harness.scheduler.pending)
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
    }

    private class Harness(
        ready: Boolean,
        speakFails: Boolean = false,
    ) {
        val tts = FakeEngine(ready, speakFails)
        val chime = FakeChime()
        val focus = FakeFocus()
        val scheduler = ManualScheduler()
        val announcer = FocusedAnnouncer(tts, chime, focus, scheduler)
    }

    private class FakeEngine(
        override var ready: Boolean,
        private val speakFails: Boolean,
    ) : SpeechEngine {
        var speaks = 0
        var stops = 0
        var last: String? = null

        override fun speak(text: String) {
            speaks += 1
            last = text
            if (speakFails) error("tts")
        }

        override fun stop() {
            stops += 1
        }
    }

    private class FakeChime : ChimePlayer {
        var plays = 0
        override fun play() {
            plays += 1
        }
        override fun stop() = Unit
    }

    private class FakeFocus : SpeechFocus {
        var requests = 0
        var abandons = 0
        var navigationActive = false
        var onChange: ((SpeechFocusLoss) -> Unit)? = null

        override fun requestMayDuck(onChange: (SpeechFocusLoss) -> Unit): Boolean {
            requests += 1
            this.onChange = onChange
            return true
        }

        override fun abandon() {
            abandons += 1
        }

        override fun navigationOrAssistantActive(): Boolean = navigationActive
    }

    private class ManualScheduler : SpeechScheduler {
        var pending: (() -> Unit)? = null
        var delayMs: Long = -1

        override fun post(delayMs: Long, block: () -> Unit): SpeechTicket {
            this.delayMs = delayMs
            pending = block
            return SpeechTicket {
                if (pending === block) pending = null
            }
        }

        fun fire() {
            val block = pending
            pending = null
            block?.invoke()
        }
    }
}
