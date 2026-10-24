package io.hmg.appchallenge.foundation.speech

import android.os.Looper
import androidx.annotation.MainThread

/**
 * Asks for transient ducking focus, waits [LEADING_SILENCE_MS], then speaks.
 * A ready [SpeechEngine] is preferred. The engine is warmed on [onForeground] and
 * again at the start of [announce], so binding overlaps the focus request and the
 * leading silence. After that silence, [SpeechEngine.whenReady] races the remaining
 * time until [ENGINE_READY_WAIT_MS] from the focus grant (the silence counts toward
 * that cap). Ready speech starts immediately. The cap still falls back to
 * [ChimePlayer]. Init failure, a synchronous speak error, or an async utterance
 * error also falls back to the chime. Both paths share this focus and the leading
 * gap. Focus is released when speech or the chime finishes, and also on stop,
 * suspend, or any focus loss. A new announcement abandons the previous grant
 * before requesting another one.
 * Navigation guidance or the assistant already playing cancels the turn.
 */
class FocusedAnnouncer(
    private val tts: SpeechEngine,
    private val chime: ChimePlayer,
    private val focus: SpeechFocus,
    private val scheduler: SpeechScheduler,
    private val leadingSilenceMs: Long = LEADING_SILENCE_MS,
) : CabinAnnouncer {
    private var generation = 0
    private var outputToken = 0
    private var suspended = false
    private var ticket: SpeechTicket? = null
    private var readyTicket: SpeechTicket? = null
    private var holdingFocus = false

    @MainThread
    override fun announce(text: String) {
        checkMainThread()
        if (suspended) return
        if (focus.navigationOrAssistantActive()) return
        // Binding starts before focus is requested so init overlaps the grant and the silence.
        tts.warmUp()
        // Drop the previous grant before the new request so focus requests do not stack.
        endTurn()
        val gen = generation
        // The focus callback can run before requestMayDuck returns.
        holdingFocus = true
        val granted = focus.requestMayDuck { _ ->
            if (gen != generation) return@requestMayDuck
            endTurn()
        }
        if (!granted) {
            if (generation == gen) releaseFocus()
            return
        }
        if (gen != generation || suspended) return
        ticket = scheduler.post(leadingSilenceMs) {
            if (gen != generation || suspended) return@post
            if (focus.navigationOrAssistantActive()) {
                endTurn()
                return@post
            }
            playWhenReady(text, gen, waitedMs = leadingSilenceMs)
        }
    }

    @MainThread
    override fun stop() {
        checkMainThread()
        endTurn()
    }

    @MainThread
    override fun onSuspend() {
        checkMainThread()
        suspended = true
        endTurn()
        // The process memory budget is 400MB and nothing speaks off-screen, so the
        // binding is dropped here. The next onForeground pre-warms, and announce waits
        // up to ENGINE_READY_WAIT_MS before the chime. Keeping the binding across
        // suspend would make the first utterance instant but hold the TTS service
        // for the process lifetime.
        tts.shutdown()
    }

    @MainThread
    override fun onForeground() {
        checkMainThread()
        suspended = false
        tts.warmUp()
    }

    /**
     * [waitedMs] is time since the focus grant. The leading silence is the first slice.
     * After that, [SpeechEngine.whenReady] and the remaining time until
     * [ENGINE_READY_WAIT_MS] race. A leading silence already past that cap chimes now.
     */
    private fun playWhenReady(text: String, gen: Int, waitedMs: Long) {
        if (tts.ready || waitedMs >= ENGINE_READY_WAIT_MS) {
            playSelected(text, gen)
            return
        }
        val remaining = ENGINE_READY_WAIT_MS - waitedMs
        val timeout = scheduler.post(remaining) {
            readyTicket?.cancel()
            readyTicket = null
            if (gen != generation || suspended) return@post
            if (focus.navigationOrAssistantActive()) {
                endTurn()
                return@post
            }
            playSelected(text, gen)
        }
        ticket = timeout
        readyTicket = tts.whenReady {
            if (gen != generation || suspended) return@whenReady
            timeout.cancel()
            if (ticket === timeout) ticket = null
            if (focus.navigationOrAssistantActive()) {
                endTurn()
                return@whenReady
            }
            playSelected(text, gen)
        }
    }

    private fun playSelected(text: String, gen: Int) {
        val token = ++outputToken
        if (!tts.ready) {
            fallbackToChime(token, gen)
            return
        }
        try {
            tts.speak(
                text,
                onDone = { finishOutput(token, gen) },
                onError = { fallbackToChime(token, gen) },
                onStop = { finishOutput(token, gen) },
            )
        } catch (error: Throwable) {
            tts.stop()
            fallbackToChime(token, gen)
        }
    }

    private fun fallbackToChime(token: Int, gen: Int) {
        if (token != outputToken || gen != generation) return
        val chimeToken = ++outputToken
        chime.play {
            if (chimeToken == outputToken && gen == generation) finishTurn(gen)
        }
    }

    private fun finishOutput(token: Int, gen: Int) {
        if (token != outputToken || gen != generation) return
        finishTurn(gen)
    }

    /** Releases focus for this turn. A newer announcement has already moved [generation]. */
    private fun finishTurn(gen: Int) {
        if (gen != generation || !holdingFocus) return
        releaseFocus()
    }

    private fun endTurn() {
        generation += 1
        outputToken += 1
        readyTicket?.cancel()
        readyTicket = null
        ticket?.cancel()
        ticket = null
        releaseFocus()
        tts.stop()
        chime.stop()
    }

    private fun releaseFocus() {
        if (!holdingFocus) return
        holdingFocus = false
        focus.abandon()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "FocusedAnnouncer runs only on the main thread"
        }
    }

    companion object {
        const val LEADING_SILENCE_MS = 175L

        /**
         * Bound on waiting for [SpeechEngine.ready], measured from the focus grant.
         * 1500ms is the product cap (about 1.5s). It includes [LEADING_SILENCE_MS].
         * It is not 1500ms added after the silence. A ready engine speaks when the
         * silence ends. If it becomes ready later, speech starts then. The chime
         * runs only when the cap is reached first.
         */
        const val ENGINE_READY_WAIT_MS = 1_500L
    }
}
