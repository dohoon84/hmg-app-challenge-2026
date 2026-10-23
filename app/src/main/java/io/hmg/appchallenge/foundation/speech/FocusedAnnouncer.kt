package io.hmg.appchallenge.foundation.speech

import android.os.Looper
import androidx.annotation.MainThread

/**
 * Asks for transient ducking focus, waits [LEADING_SILENCE_MS], then speaks.
 * A ready [SpeechEngine] is preferred. Init failure, a missing engine, a synchronous
 * speak error, or an async utterance error falls back to [ChimePlayer]. Both paths
 * share this focus and the leading gap. Focus is released when speech or the chime
 * finishes, and also on stop, suspend, or any focus loss. A new announcement abandons
 * the previous grant before requesting another one.
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
    private var holdingFocus = false

    @MainThread
    override fun announce(text: String) {
        checkMainThread()
        if (suspended) return
        if (focus.navigationOrAssistantActive()) return
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
            playSelected(text, gen)
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
        tts.shutdown()
    }

    @MainThread
    override fun onForeground() {
        checkMainThread()
        suspended = false
    }

    private fun playSelected(text: String, gen: Int) {
        val token = ++outputToken
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
    }
}
