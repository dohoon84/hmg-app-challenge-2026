package com.krazylabs.miri.speech

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
 * [whenReady] runs as soon as [ready] becomes true so a caller can race it
 * against a timeout. Completion and [whenReady] are reported on the main thread.
 * [com.krazylabs.miri.speech.FocusedAnnouncer] calls these on the main thread.
 */
interface SpeechEngine {
    val ready: Boolean

    /** Idempotent. Starts binding so a later [speak] can succeed. */
    fun warmUp()

    /**
     * Invokes [onReady] when [ready] is true. An engine that is already ready
     * invokes it inline on the caller thread. Otherwise the callback is posted
     * to the main thread. [SpeechTicket.cancel] drops it, and so does [shutdown].
     */
    fun whenReady(onReady: () -> Unit): SpeechTicket

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
