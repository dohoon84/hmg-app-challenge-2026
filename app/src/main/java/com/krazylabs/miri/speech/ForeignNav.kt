package com.krazylabs.miri.speech

import android.media.AudioAttributes

/**
 * Navigation or assistant audio that is not this process.
 * Our own guidance-usage playback shares the uid, so it must not look like
 * the nav app starting. Sonification playback is a different usage and does
 * not hide a foreign nav stream.
 */
fun foreignNavOrAssistant(usage: Int, clientUid: Int, selfUid: Int): Boolean {
    val nav = usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
        usage == AudioAttributes.USAGE_ASSISTANT
    return nav && clientUid != selfUid
}
