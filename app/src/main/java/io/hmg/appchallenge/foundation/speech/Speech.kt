package io.hmg.appchallenge.foundation.speech

/**
 * Foreground announcements. [onSuspend] stops output, drops audio focus, and
 * shuts the engine down. [onForeground] clears that gate and pre-warms the
 * engine. It does not replay the last utterance.
 */
interface CabinAnnouncer {
    fun announce(text: String)
    fun stop()
    fun onSuspend()
    fun onForeground()
}

/**
 * Platform [android.speech.tts.TextToSpeech]. The Pleos TTS SDK is not used.
 * [warmUp] binds the engine without speaking. [speak] requires [ready].
 * Completion is reported on the caller thread the implementation chooses;
 * [io.hmg.appchallenge.foundation.speech.FocusedAnnouncer] expects those
 * callbacks on the main thread.
 */
interface SpeechEngine {
    val ready: Boolean

    /** Idempotent. Starts binding so a later [speak] can succeed. */
    fun warmUp()

    fun speak(
        text: String,
        onDone: () -> Unit,
        onError: () -> Unit,
        onStop: () -> Unit,
    )
    fun stop()
    fun shutdown()
}

/** Pre-recorded clip. A missing clip still invokes [onFinished] so focus can be released. */
interface ChimePlayer {
    fun play(onFinished: () -> Unit)
    fun stop()
}

enum class SpeechFocusLoss {
    LOST,
    LOST_TRANSIENT,
    LOST_TRANSIENT_CAN_DUCK,
}

interface SpeechFocus {
    fun requestMayDuck(onChange: (SpeechFocusLoss) -> Unit): Boolean
    fun abandon()
    fun navigationOrAssistantActive(): Boolean
}

fun interface SpeechTicket {
    fun cancel()
}

fun interface SpeechScheduler {
    fun post(delayMs: Long, block: () -> Unit): SpeechTicket
}
