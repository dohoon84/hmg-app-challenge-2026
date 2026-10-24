package com.krazylabs.miri.mvp

/**
 * The card-voice half of [com.krazylabs.miri.ui.MiriViewModel]'s playback choice.
 * [CardSession.once] has already spent the utterance, so a line that cannot play
 * over navigation is held here instead of dropped. It plays when guidance goes
 * quiet. It is dropped when the card closes, the gear is D or R, or [HOLD_CAP_MS]
 * passes. Drive cues do not use this gate; they already wait inside [DriveCueEngine].
 */
class CardVoiceGate(
    private val capMs: Long = HOLD_CAP_MS,
) {
    private var pending: VoicePlay = VoicePlay.None
    private var sinceMs: Long? = null

    fun decide(
        offered: VoicePlay,
        cardOpen: Boolean,
        moving: Boolean,
        navSpeaking: Boolean,
        nowMs: Long,
    ): VoicePlay {
        if (moving || !cardOpen) {
            clear()
            return VoicePlay.None
        }
        if (offered != VoicePlay.None) {
            pending = offered
            sinceMs = nowMs
        }
        val waiting = pending
        if (waiting == VoicePlay.None) return VoicePlay.None
        val since = sinceMs ?: nowMs
        if (nowMs - since >= capMs) {
            clear()
            return VoicePlay.None
        }
        if (navSpeaking) return VoicePlay.None
        clear()
        return waiting
    }

    private fun clear() {
        pending = VoicePlay.None
        sinceMs = null
    }

    companion object {
        const val HOLD_CAP_MS = 10_000L
    }
}
