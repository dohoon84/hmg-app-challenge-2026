package com.krazylabs.miri.mvp

/**
 * Catalog sentences: a present raw clip plays, a missing file drops that sentence.
 * A missing catalog sentence is not spoken with device TTS.
 * When every sentence is dropped, the caller plays the chime.
 * The turn-signal prompt is the chime only.
 */
enum class VoicePath { CLIP, DROP, CHIME }

object VoiceFallback {
    fun catalog(key: String, rawResId: Int): VoicePath {
        if (!VoiceClips.contains(key)) return VoicePath.DROP
        if (rawResId == 0) return VoicePath.DROP
        return VoicePath.CLIP
    }

    fun whenAllDropped(): VoicePath = VoicePath.CHIME
}
