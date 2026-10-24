package com.krazylabs.miri.speech

import android.media.AudioAttributes

/**
 * Playback usage for focus, TTS, and clips.
 * The default is [SONIFICATION]. [NAVIGATION_GUIDANCE] is a debug comparison for M6:
 * [SpeechFocus.navigationOrAssistantActive] treats that usage as navigation, so our own
 * utterance can look like guidance and hold card voice or D2-b until it ends.
 */
object SpeechUsage {
    const val SONIFICATION = "SONIFICATION"
    const val NAVIGATION_GUIDANCE = "NAVIGATION_GUIDANCE"

    @Volatile
    var mode: String = SONIFICATION
        private set

    fun apply(mode: String) {
        this.mode = if (mode == NAVIGATION_GUIDANCE) NAVIGATION_GUIDANCE else SONIFICATION
    }

    fun usage(): Int = if (mode == NAVIGATION_GUIDANCE) {
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
    } else {
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
    }
}
