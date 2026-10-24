package com.krazylabs.miri.mvp

/**
 * Catalog sentences play a raw clip when the file is present.
 * A missing catalog file is spoken with device TTS.
 * If that engine is missing or not ready within the warm-up cap, the caller
 * plays the chime. Unknown keys, including the turn-signal prompt, are not spoken.
 */
enum class VoicePath { CLIP, TTS, DROP, CHIME }

object VoiceFallback {
    fun catalog(key: String, rawResId: Int): VoicePath {
        if (!VoiceClips.contains(key)) return VoicePath.DROP
        if (rawResId == 0) return VoicePath.TTS
        return VoicePath.CLIP
    }

    /** The TTS engine is missing, or it did not become ready inside the warm-up cap. */
    fun whenEngineUnavailable(): VoicePath = VoicePath.CHIME
}
