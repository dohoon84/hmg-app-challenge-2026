package io.hmg.appchallenge.foundation.speech

/**
 * Foreground announcements. [onSuspend] stops output and drops audio focus.
 * [onForeground] only clears that gate. It does not replay the last utterance.
 */
interface CabinAnnouncer {
    fun announce(text: String)
    fun stop()
    fun onSuspend()
    fun onForeground()
}

/** Platform [android.speech.tts.TextToSpeech]. The Pleos TTS SDK is not used. */
interface SpeechEngine {
    val ready: Boolean
    fun speak(text: String)
    fun stop()
}

/** Pre-recorded clip. A missing clip is a no-op. */
interface ChimePlayer {
    fun play()
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
