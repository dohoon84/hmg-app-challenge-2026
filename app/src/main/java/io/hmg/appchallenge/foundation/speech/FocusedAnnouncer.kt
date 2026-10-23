package io.hmg.appchallenge.foundation.speech

/**
 * Asks for transient ducking focus, waits [LEADING_SILENCE_MS], then speaks.
 * A ready [SpeechEngine] is preferred. Init failure, a missing engine, or a speak
 * error falls back to [ChimePlayer]. Both paths share this focus and the leading gap.
 * Navigation guidance or the assistant already playing, or any focus loss, cancels the turn.
 */
class FocusedAnnouncer(
    private val tts: SpeechEngine,
    private val chime: ChimePlayer,
    private val focus: SpeechFocus,
    private val scheduler: SpeechScheduler,
    private val leadingSilenceMs: Long = LEADING_SILENCE_MS,
) : CabinAnnouncer {
    private var generation = 0
    private var suspended = false
    private var ticket: SpeechTicket? = null
    private var holdingFocus = false

    override fun announce(text: String) {
        if (suspended) return
        if (focus.navigationOrAssistantActive()) return
        val gen = ++generation
        ticket?.cancel()
        ticket = null
        // The focus callback can run before requestMayDuck returns.
        holdingFocus = true
        val granted = focus.requestMayDuck { _ ->
            if (gen != generation) return@requestMayDuck
            cancelTurn()
        }
        if (!granted) {
            if (generation == gen) holdingFocus = false
            return
        }
        if (gen != generation || suspended) return
        ticket = scheduler.post(leadingSilenceMs) {
            if (gen != generation || suspended) return@post
            if (focus.navigationOrAssistantActive()) {
                cancelTurn()
                return@post
            }
            playSelected(text)
        }
    }

    override fun stop() {
        cancelTurn()
    }

    override fun onSuspend() {
        suspended = true
        cancelTurn()
    }

    override fun onForeground() {
        suspended = false
    }

    private fun playSelected(text: String) {
        if (tts.ready) {
            try {
                tts.speak(text)
                return
            } catch (error: Throwable) {
                tts.stop()
            }
        }
        chime.play()
    }

    private fun cancelTurn() {
        generation += 1
        ticket?.cancel()
        ticket = null
        tts.stop()
        chime.stop()
        if (holdingFocus) {
            holdingFocus = false
            focus.abandon()
        }
    }

    companion object {
        const val LEADING_SILENCE_MS = 175L
    }
}
