package com.krazylabs.miri.speech

import android.content.Context
import com.krazylabs.miri.mvp.VoiceClips
import com.krazylabs.miri.mvp.VoiceFallback
import com.krazylabs.miri.mvp.VoicePath
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays catalog sentences in order: raw clip, then device TTS, then the chime.
 * [SpeechEngine.warmUp] starts before the first gap. The leading silence counts
 * toward [FocusedAnnouncer.ENGINE_READY_WAIT_MS], the same cap as [FocusedAnnouncer].
 * A missing engine, or one that is still not ready when that cap is reached,
 * plays the chime for that sentence. Later sentences in the same cue do not
 * wait another cap. The turn-signal prompt uses [chime] only.
 */
class ClipCuePlayer(
    private val chime: ChimePlayer,
    private val scheduler: SpeechScheduler,
    private val tts: SpeechEngine,
    private val clipId: (String) -> Int,
    private val openClip: (Int) -> ChimePlayer,
) {
    constructor(
        context: Context,
        chime: ChimePlayer,
        scheduler: SpeechScheduler,
        tts: SpeechEngine,
    ) : this(
        chime,
        scheduler,
        tts,
        clipId = { key -> context.resources.getIdentifier(key, "raw", context.packageName) },
        openClip = { id -> RawClipPlayer(context, id) },
    )

    private var generation = 0
    private var ticket: SpeechTicket? = null
    private var readyTicket: SpeechTicket? = null
    private var clip: ChimePlayer? = null

    fun stop() {
        generation += 1
        ticket?.cancel()
        ticket = null
        readyTicket?.cancel()
        readyTicket = null
        clip?.stop()
        clip = null
        tts.stop()
        chime.stop()
    }

    fun chime() {
        stop()
        chime.play {}
    }

    fun clips(keys: List<String>) {
        stop()
        val gen = generation
        val steps = ArrayList<Step>(keys.size)
        for (key in keys) {
            val id = clipId(key)
            when (VoiceFallback.catalog(key, id)) {
                VoicePath.CLIP -> steps += Step.Clip(id)
                VoicePath.TTS -> {
                    val text = VoiceClips.spoken(key) ?: continue
                    steps += Step.Speak(text)
                }
                VoicePath.DROP, VoicePath.CHIME -> Unit
            }
        }
        if (steps.isEmpty()) {
            chime.play {}
            return
        }
        tts.warmUp()
        enqueue(steps, 0, leading = true, waitedMs = 0L, gen = gen)
    }

    private fun enqueue(
        steps: List<Step>,
        index: Int,
        leading: Boolean,
        waitedMs: Long,
        gen: Int,
    ) {
        if (gen != generation || index >= steps.size) return
        val delay = if (leading) FocusedAnnouncer.LEADING_SILENCE_MS else GAP_MS
        ticket = scheduler.post(delay) {
            if (gen != generation) return@post
            val waited = waitedMs + delay
            when (val step = steps[index]) {
                is Step.Clip -> {
                    val player = openClip(step.resId)
                    clip = player
                    player.play {
                        if (gen == generation) {
                            enqueue(steps, index + 1, leading = false, waitedMs = waited, gen = gen)
                        }
                    }
                }
                is Step.Speak -> speakOrChime(step.text, gen, waited) { elapsed ->
                    enqueue(steps, index + 1, leading = false, waitedMs = elapsed, gen = gen)
                }
            }
        }
    }

    /**
     * [waitedMs] is scheduled time since [clips] started. The leading silence is
     * the first slice and counts toward [FocusedAnnouncer.ENGINE_READY_WAIT_MS].
     */
    private fun speakOrChime(
        text: String,
        gen: Int,
        waitedMs: Long,
        onFinished: (Long) -> Unit,
    ) {
        if (gen != generation) return
        if (tts.ready || waitedMs >= FocusedAnnouncer.ENGINE_READY_WAIT_MS) {
            playSelected(text, gen) { onFinished(waitedMs) }
            return
        }
        val remaining = FocusedAnnouncer.ENGINE_READY_WAIT_MS - waitedMs
        val gate = AtomicBoolean(false)
        val timeout = scheduler.post(remaining) {
            readyTicket?.cancel()
            readyTicket = null
            if (!gate.compareAndSet(false, true)) return@post
            if (gen != generation) return@post
            playSelected(text, gen) { onFinished(FocusedAnnouncer.ENGINE_READY_WAIT_MS) }
        }
        ticket = timeout
        readyTicket = tts.whenReady {
            if (!gate.compareAndSet(false, true)) return@whenReady
            if (gen != generation) return@whenReady
            timeout.cancel()
            if (ticket === timeout) ticket = null
            playSelected(text, gen) { onFinished(waitedMs) }
        }
    }

    private fun playSelected(text: String, gen: Int, onFinished: () -> Unit) {
        if (gen != generation) return
        if (!tts.ready) {
            if (VoiceFallback.whenEngineUnavailable() != VoicePath.CHIME) return
            chime.play {
                if (gen == generation) onFinished()
            }
            return
        }
        try {
            tts.speak(
                text,
                onDone = { if (gen == generation) onFinished() },
                onError = {
                    if (gen == generation) {
                        chime.play { if (gen == generation) onFinished() }
                    }
                },
                onStop = { },
            )
        } catch (error: Throwable) {
            tts.stop()
            if (gen != generation) return
            chime.play { if (gen == generation) onFinished() }
        }
    }

    private sealed class Step {
        data class Clip(val resId: Int) : Step()
        data class Speak(val text: String) : Step()
    }

    private companion object {
        const val GAP_MS = 250L
    }
}
