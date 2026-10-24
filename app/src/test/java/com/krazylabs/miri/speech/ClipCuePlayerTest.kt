package com.krazylabs.miri.speech

import com.krazylabs.miri.mvp.VoiceClips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(2, harness.chime.plays)
        assertEquals(0, harness.tts.speaks)
        assertNull(harness.scheduler.pending)
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
        val opened = mutableListOf<Int>()
        val clips = mutableListOf<FakeClip>()
        val clipIds = mutableMapOf<String, Int>()
        val player = ClipCuePlayer(
            chime,
            scheduler,
            tts,
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
        var warmUps = 0
        var last: String? = null
        private var speaking = false
        private var failed = false
        private var onDone: (() -> Unit)? = null
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
        }

        override fun stop() {
            if (!speaking && !failed) return
            stops += 1
            speaking = false
            failed = false
            onDone = null
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
