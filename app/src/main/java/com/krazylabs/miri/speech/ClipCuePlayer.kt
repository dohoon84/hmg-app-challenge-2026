package com.krazylabs.miri.speech

import android.content.Context
import com.krazylabs.miri.mvp.VoiceClips

/**
 * Plays raw clips in order. A missing clip is dropped and is not read with TTS.
 * If every clip is missing, the chime plays. The turn-signal prompt uses [chime] only.
 */
class ClipCuePlayer(
    private val context: Context,
    private val chime: ChimePlayer,
    private val scheduler: SpeechScheduler,
) {
    private var ticket: SpeechTicket? = null
    private var clip: RawClipPlayer? = null

    fun stop() {
        ticket?.cancel()
        ticket = null
        clip?.stop()
        clip = null
        chime.stop()
    }

    fun chime() {
        stop()
        chime.play {}
    }

    fun clips(keys: List<String>) {
        stop()
        val ids = keys.filter { VoiceClips.contains(it) }.map { key ->
            context.resources.getIdentifier(key, "raw", context.packageName)
        }.filter { it != 0 }
        if (ids.isEmpty()) {
            chime.play {}
            return
        }
        playAt(ids, 0, leading = true)
    }

    private fun playAt(ids: List<Int>, index: Int, leading: Boolean) {
        if (index >= ids.size) return
        ticket = scheduler.post(if (leading) LEADING_MS else GAP_MS) {
            val player = RawClipPlayer(context, ids[index])
            clip = player
            player.play { playAt(ids, index + 1, leading = false) }
        }
    }

    private companion object {
        const val LEADING_MS = 175L
        const val GAP_MS = 250L
    }
}
