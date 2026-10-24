package com.krazylabs.miri.speech

import com.krazylabs.miri.mvp.VoiceClips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipCuePlayerTest {
    @Test
    fun presentClipPlaysAndDoesNotSpeak() {
        val harness = Harness(ready = true, clipResId = 12)
        harness.player.clips(listOf("vo_fuel_electric"))
        assertEquals(1, harness.tts.warmUps)
        assertEquals(FocusedAnnouncer.LEADING_SILENCE_MS, harness.scheduler.delayMs)
        assertEquals(0, harness.tts.speaks)
        harness.scheduler.fire()
        assertEquals(listOf(12), harness.opened)
        assertEquals(1, harness.clips.single().plays)
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        harness.clips.single().finish()
        assertNull(harness.scheduler.pending)
        assertEquals(0, harness.tts.speaks)
    }

    @Test
    fun missingClipSpeaksWhenTheEngineIsReady() {
        val harness = Harness(ready = true, clipResId = 0)
        harness.player.clips(listOf(VoiceClips.H1))
        assertEquals(1, harness.tts.warmUps)
        assertEquals(0, harness.tts.speaks)
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(VoiceClips.H1_TEXT, harness.tts.last)
        assertEquals(0, harness.chime.plays)
        assertEquals(0, harness.opened.size)
    }

    @Test
    fun engineNotReadyWithinTheCapPlaysTheChime() {
        val harness = Harness(ready = false, clipResId = 0)
        harness.player.clips(listOf("vo_arrive_ev_soon"))
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        assertEquals(
            FocusedAnnouncer.ENGINE_READY_WAIT_MS - FocusedAnnouncer.LEADING_SILENCE_MS,
            harness.scheduler.delayMs,
        )
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(1, harness.chime.plays)
        assertNull(harness.scheduler.pending)
    }

    @Test
    fun laterSentenceDoesNotWaitAnotherCap() {
        val harness = Harness(ready = false, clipResId = 0)
        harness.player.clips(listOf("vo_fuel_electric", "vo_charge_port_rear_right"))
        harness.scheduler.fire()
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        harness.chime.finish()
        assertEquals(GAP_MS, harness.scheduler.delayMs)
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        assertEquals(0, harness.tts.speaks)
        assertNull(harness.scheduler.pending)
        assertEquals(1, harness.focus.abandons)
    }

    @Test
    fun oneBatchRingsTheChimeOnceAndStillPlaysALaterClip() {
        val harness = Harness(ready = false, clipResId = 0)
        harness.clipIds["vo_charge_port_rear_right"] = 9
        harness.player.clips(listOf("vo_fuel_electric", "vo_charge_port_rear_right"))
        harness.scheduler.fire()
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        harness.chime.finish()
        assertEquals(GAP_MS, harness.scheduler.delayMs)
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        assertEquals(listOf(9), harness.opened)
        assertEquals(1, harness.clips.single().plays)
        harness.clips.single().finish()
        assertEquals(1, harness.focus.abandons)
    }

    @Test
    fun mayDuckIsRequestedAfterTheLeadingSilence() {
        val harness = Harness(ready = true, clipResId = 12)
        harness.player.clips(listOf("vo_fuel_electric"))
        assertEquals(0, harness.focus.requests)
        assertEquals(0, harness.opened.size)
        assertEquals(FocusedAnnouncer.LEADING_SILENCE_MS, harness.scheduler.delayMs)
        harness.scheduler.fire()
        assertEquals(1, harness.focus.requests)
        assertEquals(1, harness.clips.single().plays)
        assertEquals(0, harness.focus.abandons)
        harness.clips.single().finish()
        assertEquals(1, harness.focus.abandons)
    }

    @Test
    fun deniedFocusDoesNotPlay() {
        val clips = Harness(ready = true, clipResId = 12, grant = false)
        clips.player.clips(listOf("vo_fuel_electric"))
        assertEquals(0, clips.focus.requests)
        clips.scheduler.fire()
        assertEquals(1, clips.focus.requests)
        assertEquals(1, clips.focus.abandons)
        assertEquals(0, clips.opened.size)
        assertEquals(0, clips.chime.plays)
        val chime = Harness(ready = true, clipResId = 12, grant = false)
        chime.player.chime()
        assertEquals(1, chime.focus.requests)
        assertEquals(1, chime.focus.abandons)
        assertEquals(0, chime.chime.plays)
    }

    @Test
    fun threeSentencesRingTheChimeOnce() {
        val harness = Harness(ready = false, clipResId = 0)
        harness.player.clips(listOf("vo_fuel_electric", "vo_charge_port_rear_right", "vo_connector_dc_combo"))
        harness.scheduler.fire()
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        harness.chime.finish()
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        harness.scheduler.fire()
        assertEquals(1, harness.chime.plays)
        assertEquals(0, harness.tts.speaks)
        assertNull(harness.scheduler.pending)
    }

    @Test
    fun ttsStopEndsTheBatchAndAbandonsFocus() {
        val harness = Harness(ready = true, clipResId = 0)
        harness.player.clips(listOf(VoiceClips.H1, "vo_charge_port_left"))
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(1, harness.focus.requests)
        harness.tts.interrupt()
        assertEquals(1, harness.focus.abandons)
        harness.tts.complete()
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertNull(harness.scheduler.pending)
    }

    @Test
    fun lostFocusStopsTheClipAndTts() {
        val clip = Harness(ready = true, clipResId = 4)
        clip.player.clips(listOf("vo_fuel_electric"))
        clip.scheduler.fire()
        assertEquals(1, clip.clips.single().plays)
        clip.focus.lose(SpeechFocusLoss.LOST)
        assertEquals(1, clip.clips.single().stops)
        assertTrue(clip.tts.stopCalls >= 1)
        assertEquals(1, clip.focus.abandons)
        clip.clips.single().finish()
        assertEquals(0, clip.tts.speaks)

        val spoken = Harness(ready = true, clipResId = 0)
        spoken.player.clips(listOf(VoiceClips.H1))
        spoken.scheduler.fire()
        assertEquals(1, spoken.tts.speaks)
        spoken.focus.lose(SpeechFocusLoss.LOST_TRANSIENT_CAN_DUCK)
        assertEquals(1, spoken.tts.stops)
        assertEquals(1, spoken.focus.abandons)
    }

    @Test
    fun pendingEngineSpeaksWhenItBecomesReadyDuringTheWait() {
        val harness = Harness(pending = true, clipResId = 0)
        harness.player.clips(listOf("vo_connector_dc_combo", "vo_charge_port_left"))
        assertEquals(1, harness.tts.warmUps)
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        harness.tts.becomeReady()
        assertEquals(1, harness.tts.speaks)
        assertEquals("커넥터는 디씨 콤보예요.", harness.tts.last)
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        harness.tts.complete()
        assertEquals(GAP_MS, harness.scheduler.delayMs)
        harness.scheduler.fire()
        assertEquals(2, harness.tts.speaks)
        assertEquals("충전구는 왼쪽이에요.", harness.tts.last)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun clipThenMissingFileSpeaksTheSecondSentence() {
        val harness = Harness(ready = true, clipResId = 0)
        harness.clipIds["vo_fuel_electric"] = 7
        harness.player.clips(listOf("vo_fuel_electric", "vo_charge_port_rear_right"))
        harness.scheduler.fire()
        assertEquals(listOf(7), harness.opened)
        assertEquals(0, harness.tts.speaks)
        harness.clips.single().finish()
        assertEquals(GAP_MS, harness.scheduler.delayMs)
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals("충전구는 오른쪽 뒤예요.", harness.tts.last)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun turnSignalStaysChimeOnly() {
        val harness = Harness(ready = true, clipResId = 12)
        harness.player.chime()
        assertEquals(1, harness.chime.plays)
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.tts.warmUps)
        harness.player.clips(listOf("vo_turn_signal_left", "vo_turn_signal_right"))
        assertEquals(2, harness.chime.plays)
        assertEquals(0, harness.tts.warmUps)
        assertEquals(0, harness.tts.speaks)
        assertNull(harness.scheduler.pending)
    }

    @Test
    fun stopDuringTheWaitDoesNotSpeakOrChime() {
        val harness = Harness(pending = true, clipResId = 0)
        harness.player.clips(listOf("vo_brief_intro"))
        harness.scheduler.fire()
        harness.player.stop()
        harness.tts.becomeReady()
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun speakFailureFallsBackToTheChime() {
        val harness = Harness(ready = true, clipResId = 0, speakFails = true)
        harness.player.clips(listOf("vo_check_done"))
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(1, harness.tts.stops)
        assertEquals(1, harness.chime.plays)
    }

    private class Harness(
        ready: Boolean = true,
        pending: Boolean = false,
        speakFails: Boolean = false,
        clipResId: Int,
        grant: Boolean = true,
    ) {
        val tts = FakeEngine(
            when {
                pending -> Readiness.PENDING
                ready -> Readiness.READY
                else -> Readiness.NEVER
            },
            speakFails,
        )
        val chime = FakeChime()
        val scheduler = ManualScheduler()
        val focus = FakeFocus(grant)
        val opened = mutableListOf<Int>()
        val clips = mutableListOf<FakeClip>()
        val clipIds = mutableMapOf<String, Int>()
        val player = ClipCuePlayer(
            chime,
            scheduler,
            tts,
            focus,
            clipId = { key -> clipIds[key] ?: clipResId },
            openClip = { id ->
                opened += id
                FakeClip().also { clips += it }
            },
        )
    }

    private enum class Readiness { READY, NEVER, PENDING }

    private class FakeEngine(
        private val mode: Readiness,
        private val speakFails: Boolean = false,
    ) : SpeechEngine {
        override var ready: Boolean = mode == Readiness.READY
        var speaks = 0
        var stops = 0
        var stopCalls = 0
        var warmUps = 0
        var last: String? = null
        private var speaking = false
        private var failed = false
        private var onDone: (() -> Unit)? = null
        private var onStopped: (() -> Unit)? = null
        private val readyListeners = mutableListOf<() -> Unit>()

        override fun warmUp() {
            warmUps += 1
            if (mode == Readiness.READY) ready = true
        }

        override fun whenReady(onReady: () -> Unit): SpeechTicket {
            if (ready) {
                onReady()
                return SpeechTicket {}
            }
            readyListeners += onReady
            return SpeechTicket { readyListeners.remove(onReady) }
        }

        fun becomeReady() {
            ready = true
            val listeners = readyListeners.toList()
            readyListeners.clear()
            listeners.forEach { it() }
        }

        override fun speak(
            text: String,
            onDone: () -> Unit,
            onError: () -> Unit,
            onStop: () -> Unit,
        ) {
            if (!ready) error("TextToSpeech is not ready")
            speaks += 1
            last = text
            if (speakFails) {
                failed = true
                error("tts")
            }
            speaking = true
            this.onDone = onDone
            this.onStopped = onStop
        }

        fun interrupt() {
            val callback = onStopped
            onStopped = null
            speaking = false
            callback?.invoke()
        }

        override fun stop() {
            stopCalls += 1
            if (!speaking && !failed) return
            stops += 1
            speaking = false
            failed = false
            onDone = null
            onStopped = null
        }

        override fun shutdown() {
            readyListeners.clear()
            stop()
            ready = false
        }

        fun complete() {
            onDone?.invoke()
        }
    }

    private class FakeChime : ChimePlayer {
        var plays = 0
        private var onFinished: (() -> Unit)? = null

        override fun play(onFinished: () -> Unit) {
            plays += 1
            this.onFinished = onFinished
        }

        override fun stop() {
            onFinished = null
        }

        fun finish() {
            val callback = onFinished
            onFinished = null
            callback?.invoke()
        }
    }

    private class FakeClip : ChimePlayer {
        var plays = 0
        var stops = 0
        private var onFinished: (() -> Unit)? = null

        override fun play(onFinished: () -> Unit) {
            plays += 1
            this.onFinished = onFinished
        }

        override fun stop() {
            stops += 1
            onFinished = null
        }

        fun finish() {
            val callback = onFinished
            onFinished = null
            callback?.invoke()
        }
    }

    private class FakeFocus(private val grant: Boolean) : SpeechFocus {
        var requests = 0
        var abandons = 0
        private var listener: ((SpeechFocusLoss) -> Unit)? = null

        override fun requestMayDuck(onChange: (SpeechFocusLoss) -> Unit): Boolean {
            requests += 1
            listener = onChange
            return grant
        }

        override fun abandon() {
            abandons += 1
            listener = null
        }

        override fun navigationOrAssistantActive(): Boolean = false

        fun lose(loss: SpeechFocusLoss) {
            listener?.invoke(loss)
        }
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
            val block = pending ?: return
            pending = null
            delayMs = -1
            block()
        }
    }

    private companion object {
        const val GAP_MS = 250L
    }
}
