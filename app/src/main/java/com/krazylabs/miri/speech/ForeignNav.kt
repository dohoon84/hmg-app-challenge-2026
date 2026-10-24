package com.krazylabs.miri.speech

import android.media.AudioAttributes

/**
 * Navigation or assistant audio that is not this process.
 * Our own guidance-usage playback shares the uid, so it must not look like
 * the nav app starting. Sonification playback is a different usage and does
 * not hide a foreign nav stream.
 * A uid of 0 or less is not an app. [playbackIsNavApp] sends that to the
 * guidance-mode fallback instead of this check.
 */
fun foreignNavOrAssistant(usage: Int, clientUid: Int, selfUid: Int): Boolean {
    val nav = usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
        usage == AudioAttributes.USAGE_ASSISTANT
    return nav && clientUid != selfUid
}

/**
 * Whether this playback configuration is the nav app.
 * A positive uid uses [foreignNavOrAssistant]. A missing uid, or one
 * anonymized to 0 or below, uses the fallback: count the usage only when
 * we are not ourselves playing in guidance mode. That keeps a real nav
 * stream visible during sonification, and keeps our own guidance clip
 * from stopping the voice.
 */
fun playbackIsNavApp(usage: Int, clientUid: Int?, selfUid: Int, guidanceMode: Boolean): Boolean {
    if (clientUid != null && clientUid > 0) return foreignNavOrAssistant(usage, clientUid, selfUid)
    val nav = usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
        usage == AudioAttributes.USAGE_ASSISTANT
    return nav && !guidanceMode
}
