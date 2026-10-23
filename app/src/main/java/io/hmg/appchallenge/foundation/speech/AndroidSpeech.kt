package io.hmg.appchallenge.foundation.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.hmg.appchallenge.foundation.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sonification plus speech, matching [AndroidSpeechFocus].
 * [AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE] is intentionally not used:
 * [SpeechFocus.navigationOrAssistantActive] would then treat our own utterance as navigation.
 */
internal fun speechAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * Creates [TextToSpeech] on the first [speak]. [shutdown] drops the binding.
 * The next [speak] creates it again. Nothing constructs the engine at process start.
 */
class AndroidTtsEngine(
    context: Context,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attributes = speechAudioAttributes()
    private val lock = Any()
    private val utterance = AtomicInteger(0)

    @Volatile override var ready: Boolean = false
        private set

    @Volatile private var initStatus: Int = STATUS_UNKNOWN
    private var tts: TextToSpeech? = null

    override fun speak(
        text: String,
        onDone: () -> Unit,
        onError: () -> Unit,
        onStop: () -> Unit,
    ) {
        val engine = engine()
        if (!ready) error("TextToSpeech is not ready")
        val id = "foundation-announce-${utterance.incrementAndGet()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = deliver(utteranceId, id, onDone)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = deliver(utteranceId, id, onError)

            override fun onError(utteranceId: String?, errorCode: Int) = deliver(utteranceId, id, onError)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = deliver(utteranceId, id, onStop)
        })
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            engine.setOnUtteranceProgressListener(null)
            error("TextToSpeech.speak failed")
        }
    }

    override fun stop() {
        synchronized(lock) { tts }?.stop()
    }

    override fun shutdown() {
        val current = synchronized(lock) {
            val current = tts
            tts = null
            ready = false
            initStatus = STATUS_UNKNOWN
            current
        }
        current?.stop()
        current?.shutdown()
    }

    private fun engine(): TextToSpeech {
        synchronized(lock) { tts }?.let { return it }
        val created = TextToSpeech(appContext) { status -> onInit(status) }
        synchronized(lock) {
            if (tts == null) {
                tts = created
                val status = initStatus
                if (status != STATUS_UNKNOWN) applyInit(created, status)
            } else {
                created.shutdown()
            }
        }
        return synchronized(lock) { tts ?: created }
    }

    private fun onInit(status: Int) {
        synchronized(lock) {
            initStatus = status
            val current = tts ?: return
            applyInit(current, status)
        }
    }

    private fun applyInit(engine: TextToSpeech, status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                engine.setAudioAttributes(attributes)
            } catch (error: Throwable) {
                // Some engines reject attributes before the binding is usable. Ready still stands.
            }
            ready = true
        } else {
            ready = false
        }
    }

    private fun deliver(utteranceId: String?, expected: String, block: () -> Unit) {
        if (utteranceId != expected) return
        mainHandler.post(block)
    }

    private companion object {
        const val STATUS_UNKNOWN = Int.MIN_VALUE
    }
}

/**
 * Neutral chime in res/raw. [clipResId] 0, or a clip the platform cannot open,
 * still runs [onFinished] so the caller can drop audio focus.
 */
class RawClipPlayer(
    private val context: Context,
    private val clipResId: Int = R.raw.announce_chime,
) : ChimePlayer {
    private var player: MediaPlayer? = null

    override fun play(onFinished: () -> Unit) {
        stop()
        if (clipResId == 0) {
            onFinished()
            return
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = MediaPlayer.create(context, clipResId, attributes, 0)
        if (created == null) {
            onFinished()
            return
        }
        player = created
        var delivered = false
        fun finish(mp: MediaPlayer) {
            if (delivered || player !== mp) return
            delivered = true
            player = null
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.release()
            onFinished()
        }
        created.setOnCompletionListener { finished -> finish(finished) }
        created.setOnErrorListener { mp, _, _ ->
            finish(mp)
            true
        }
        try {
            created.start()
        } catch (error: Throwable) {
            finish(created)
        }
    }

    override fun stop() {
        val current = player ?: return
        player = null
        current.setOnCompletionListener(null)
        current.setOnErrorListener(null)
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
        // A second announcement must not leave the previous request on the focus stack.
        abandon()
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes())
            .setOnAudioFocusChangeListener({ change ->
                // LOSS_TRANSIENT_CAN_DUCK cancels too. A guidance chime should not keep
                // playing under someone else's duck; the next announce can request again.
                val loss = when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> SpeechFocusLoss.LOST
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> SpeechFocusLoss.LOST_TRANSIENT
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> SpeechFocusLoss.LOST_TRANSIENT_CAN_DUCK
                    else -> null
                }
                if (loss != null) onChange(loss)
            }, handler)
            .build()
        val granted = audio.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        request = if (granted) next else null
        return granted
    }

    override fun abandon() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }

    override fun navigationOrAssistantActive(): Boolean =
        // Pleos navigation and Gleo are expected to use these usages.
        // Whether activePlaybackConfigurations actually reports them is unverified on device.
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
