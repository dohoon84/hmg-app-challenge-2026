package io.hmg.appchallenge.foundation.speech

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FocusedAnnouncerTest {
    @Test
    fun readyEngineSpeaksAfterTheLeadingSilence() = onMain {
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
        assertEquals(0, harness.focus.abandons)
    }

    @Test
    fun normalCompletionAbandonsFocusOnce() = onMain {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.scheduler.fire()
        harness.tts.complete()
        assertEquals(1, harness.focus.abandons)
        harness.tts.complete()
        assertEquals(1, harness.focus.abandons)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun repeatedAnnounceAbandonsThePreviousRequest() = onMain {
        val harness = Harness(ready = true)
        harness.announcer.announce("하나")
        harness.scheduler.fire()
        assertEquals(0, harness.focus.abandons)
        harness.announcer.announce("둘")
        assertEquals(1, harness.focus.abandons)
        assertEquals(2, harness.focus.requests)
        harness.scheduler.fire()
        assertEquals("둘", harness.tts.last)
        harness.tts.complete()
        assertEquals(2, harness.focus.abandons)
    }

    @Test
    fun asyncSpeakErrorPlaysTheChimeThenAbandons() = onMain {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.scheduler.fire()
        harness.tts.failAsync()
        assertEquals(1, harness.chime.plays)
        assertEquals(0, harness.focus.abandons)
        harness.chime.finish()
        assertEquals(1, harness.focus.abandons)
        assertEquals(1, harness.tts.speaks)
    }

    @Test
    fun engineNotReadyPlaysTheClipAfterTheBoundedWait() = onMain {
        val harness = Harness(ready = false)
        harness.announcer.announce("안녕")
        assertEquals(FocusedAnnouncer.LEADING_SILENCE_MS, harness.scheduler.delayMs)
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
    }

    @Test
    fun pendingEngineSpeaksWhenItBecomesReadyDuringTheWait() = onMain {
        val harness = Harness(pending = true)
        harness.announcer.onForeground()
        assertEquals(1, harness.tts.warmUps)
        harness.announcer.announce("안녕")
        assertEquals(2, harness.tts.warmUps)
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        harness.tts.becomeReady()
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals("안녕", harness.tts.last)
        assertEquals(0, harness.chime.plays)
    }

    @Test
    fun silenceLongerThanTheCapChimesWithoutASecondWait() = onMain {
        val tts = FakeEngine(Readiness.NEVER)
        val chime = FakeChime()
        val scheduler = ManualScheduler()
        val announcer = FocusedAnnouncer(
            tts,
            chime,
            FakeFocus(),
            scheduler,
            leadingSilenceMs = FocusedAnnouncer.ENGINE_READY_WAIT_MS + 100,
        )
        announcer.announce("안녕")
        scheduler.fire()
        assertEquals(0, tts.speaks)
        assertEquals(1, chime.plays)
        assertNull(scheduler.pending)
    }

    @Test
    fun speakFailureFallsBackToTheClip() = onMain {
        val harness = Harness(ready = true, speakFails = true)
        harness.announcer.announce("안녕")
        harness.scheduler.fire()
        assertEquals(1, harness.tts.speaks)
        assertEquals(1, harness.tts.stops)
        assertEquals(1, harness.chime.plays)
        assertEquals(0, harness.focus.abandons)
        harness.chime.finish()
        assertEquals(1, harness.focus.abandons)
    }

    @Test
    fun focusLossDuringSilenceDoesNotPlay() = onMain {
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
    fun suspendAbandonsFocusAndShutsTheEngineDown() = onMain {
        val harness = Harness(ready = true)
        harness.announcer.announce("안녕")
        harness.announcer.onSuspend()
        harness.scheduler.fire()
        assertEquals(0, harness.tts.speaks)
        assertEquals(0, harness.chime.plays)
        assertEquals(1, harness.focus.abandons)
        assertEquals(1, harness.tts.shutdowns)
        val requests = harness.focus.requests
        harness.announcer.announce("다시")
        assertEquals(requests, harness.focus.requests)
    }

    @Test
    fun foregroundAllowsANewAnnouncementWithoutReplaying() = onMain {
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
    fun navigationAlreadyPlayingDoesNotStart() = onMain {
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
        ready: Boolean = true,
        speakFails: Boolean = false,
        pending: Boolean = false,
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
        val focus = FakeFocus()
        val scheduler = ManualScheduler()
        val announcer = FocusedAnnouncer(tts, chime, focus, scheduler)
    }

    private enum class Readiness { READY, NEVER, PENDING }

    private class FakeEngine(
        private val mode: Readiness,
        private val speakFails: Boolean = false,
    ) : SpeechEngine {
        override var ready: Boolean = mode == Readiness.READY
        var speaks = 0
        var stops = 0
        var shutdowns = 0
        var warmUps = 0
        var last: String? = null
        private var speaking = false
        private var failed = false
        private var onDone: (() -> Unit)? = null
        private var onError: (() -> Unit)? = null
        private var onStopCallback: (() -> Unit)? = null

        override fun warmUp() {
            warmUps += 1
            if (mode == Readiness.READY) ready = true
        }

        fun becomeReady() {
            ready = true
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
            this.onError = onError
            this.onStopCallback = onStop
        }

        override fun stop() {
            if (!speaking && !failed) return
            stops += 1
            val callback = if (speaking) onStopCallback else null
            speaking = false
            failed = false
            onDone = null
            onError = null
            onStopCallback = null
            callback?.invoke()
        }

        override fun shutdown() {
            shutdowns += 1
            stop()
            ready = false
        }

        fun complete() {
            onDone?.invoke()
        }

        fun failAsync() {
            onError?.invoke()
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
            onFinished?.invoke()
        }
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

    private fun onMain(block: () -> Unit) {
        val main = Looper.getMainLooper()
        if (Looper.myLooper() == main) {
            block()
            return
        }
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        Handler(main).post {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                latch.countDown()
            }
        }
        Shadows.shadowOf(main).idle()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }
}
