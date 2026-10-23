package io.hmg.appchallenge.foundation.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import io.hmg.appchallenge.foundation.R

class AndroidTtsEngine(
    context: Context,
) : SpeechEngine {
    @Volatile override var ready: Boolean = false
        private set

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    override fun speak(text: String) {
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) error("TextToSpeech.speak failed")
    }

    override fun stop() {
        tts.stop()
    }

    private companion object {
        const val UTTERANCE_ID = "foundation-announce"
    }
}

/**
 * Neutral chime in res/raw. [clipResId] 0, or a clip the platform cannot open, does nothing.
 */
class RawClipPlayer(
    private val context: Context,
    private val clipResId: Int = R.raw.announce_chime,
) : ChimePlayer {
    private var player: MediaPlayer? = null

    override fun play() {
        stop()
        if (clipResId == 0) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val created = MediaPlayer.create(context, clipResId, attributes, 0) ?: return
        player = created
        created.setOnCompletionListener { finished ->
            finished.release()
            if (player === finished) player = null
        }
        created.start()
    }

    override fun stop() {
        val current = player ?: return
        player = null
        try {
            current.stop()
        } catch (error: Throwable) {
            // Already stopped.
        }
        current.release()
    }
}

class AndroidSpeechFocus(
    context: Context,
) : SpeechFocus {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null

    override fun requestMayDuck(onChange: (SpeechFocusLoss) -> Unit): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                val loss = when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> SpeechFocusLoss.LOST
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> SpeechFocusLoss.LOST_TRANSIENT
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> SpeechFocusLoss.LOST_TRANSIENT_CAN_DUCK
                    else -> null
                }
                if (loss != null) onChange(loss)
            }, handler)
            .build()
        request = next
        return audio.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }

    override fun navigationOrAssistantActive(): Boolean =
        audio.activePlaybackConfigurations.any { config ->
            val usage = config.audioAttributes.usage
            usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
                usage == AudioAttributes.USAGE_ASSISTANT
        }
}

class MainThreadScheduler : SpeechScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(delayMs: Long, block: () -> Unit): SpeechTicket {
        val runnable = Runnable { block() }
        handler.postDelayed(runnable, delayMs)
        return SpeechTicket { handler.removeCallbacks(runnable) }
    }
}
