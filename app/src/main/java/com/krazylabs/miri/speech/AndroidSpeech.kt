package com.krazylabs.miri.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.krazylabs.miri.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speech attributes. The usage follows [SpeechUsage], which defaults to sonification.
 * Navigation guidance is opt-in because [SpeechFocus.navigationOrAssistantActive]
 * would then treat our own utterance as navigation.
 */
internal fun speechAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(SpeechUsage.usage())
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * Client around [TextToSpeech] so tests can supply a fake binding.
 * The platform factory is the default. [AndroidTtsEngine]'s public constructor
 * keeps that default so Hilt still calls `AndroidTtsEngine(context)`.
 */
internal interface TtsClient {
    fun setAudioAttributes(attributes: AudioAttributes)
    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?)
    fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String): Int
    fun stop()
    fun shutdown()
}

internal fun interface TtsClientFactory {
    fun create(context: Context, onInit: (TtsClient, Int) -> Unit): TtsClient
}

private class PlatformTtsClient(
    context: Context,
    onInit: (TtsClient, Int) -> Unit,
) : TtsClient {
    private val engine: TextToSpeech = TextToSpeech(context) { status -> onInit(this, status) }

    override fun setAudioAttributes(attributes: AudioAttributes) {
        engine.setAudioAttributes(attributes)
    }

    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?) {
        engine.setOnUtteranceProgressListener(listener)
    }

    override fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String): Int =
        engine.speak(text, queueMode, params, utteranceId)

    override fun stop() {
        engine.stop()
    }

    override fun shutdown() {
        engine.shutdown()
    }
}

private object PlatformTtsClientFactory : TtsClientFactory {
    override fun create(context: Context, onInit: (TtsClient, Int) -> Unit): TtsClient =
        PlatformTtsClient(context, onInit)
}

/**
 * [warmUp] binds [TextToSpeech] once and does not speak. [speak] before [ready]
 * fails so the announcer can wait a bounded time and fall back to the chime.
 * [shutdown] drops the binding. A late [onInit] or utterance callback from that
 * client cannot mark a later client ready or finish its utterance.
 * Nothing constructs the engine at process start.
 */
class AndroidTtsEngine internal constructor(
    context: Context,
    private val clientFactory: TtsClientFactory,
) : SpeechEngine {
    constructor(context: Context) : this(context, PlatformTtsClientFactory)

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun currentAttributes(): AudioAttributes = speechAudioAttributes()
    private val lock = Any()
    private val utterance = AtomicInteger(0)
    private val session = AtomicInteger(0)

    @Volatile override var ready: Boolean = false
        private set

    private var client: TtsClient? = null
    private var pendingClient: TtsClient? = null
    private var pendingStatus: Int = STATUS_UNKNOWN
    private val readyCallbacks = mutableListOf<() -> Unit>()

    @MainThread
    override fun warmUp() {
        engine()
    }

    @MainThread
    override fun whenReady(onReady: () -> Unit): SpeechTicket {
        checkMainThread()
        val alreadyReady = synchronized(lock) {
            if (ready) {
                true
            } else {
                readyCallbacks += onReady
                false
            }
        }
        if (alreadyReady) onReady()
        return SpeechTicket {
            synchronized(lock) { readyCallbacks.remove(onReady) }
        }
    }

    @MainThread
    override fun speak(
        text: String,
        onDone: () -> Unit,
        onError: () -> Unit,
        onStop: () -> Unit,
    ) {
        val engine = engine()
        if (!ready) error("TextToSpeech is not ready")
        val sessionAtSpeak = session.get()
        val id = "foundation-announce-${utterance.incrementAndGet()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = deliver(utteranceId, id, sessionAtSpeak, onDone)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = deliver(utteranceId, id, sessionAtSpeak, onError)

            override fun onError(utteranceId: String?, errorCode: Int) =
                deliver(utteranceId, id, sessionAtSpeak, onError)

            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                deliver(utteranceId, id, sessionAtSpeak, onStop)
        })
        try {
            engine.setAudioAttributes(currentAttributes())
        } catch (error: Throwable) {
            // A bound engine can reject a usage change. The utterance still starts.
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            engine.setOnUtteranceProgressListener(null)
            error("TextToSpeech.speak failed")
        }
    }

    @MainThread
    override fun stop() {
        checkMainThread()
        synchronized(lock) { client }?.stop()
    }

    @MainThread
    override fun shutdown() {
        checkMainThread()
        val current = synchronized(lock) {
            session.incrementAndGet()
            val current = client
            client = null
            ready = false
            pendingClient = null
            pendingStatus = STATUS_UNKNOWN
            readyCallbacks.clear()
            current
        }
        current?.stop()
        current?.shutdown()
    }

    /**
     * Binds on the main thread. [session] is sampled around [TtsClientFactory.create]
     * so a [shutdown] that runs from the init callback cannot be assigned afterwards.
     */
    @MainThread
    private fun engine(): TtsClient {
        checkMainThread()
        synchronized(lock) { client }?.let { return it }
        val sessionAtStart = session.get()
        val created = clientFactory.create(appContext) { createdClient, status ->
            onInit(createdClient, status)
        }
        synchronized(lock) {
            if (session.get() != sessionAtStart) {
                created.shutdown()
                if (pendingClient === created) {
                    pendingClient = null
                    pendingStatus = STATUS_UNKNOWN
                }
            } else if (client == null) {
                client = created
                if (pendingClient === created) {
                    applyInit(created, pendingStatus)
                    pendingClient = null
                    pendingStatus = STATUS_UNKNOWN
                }
            } else {
                created.shutdown()
            }
        }
        return synchronized(lock) { client ?: created }
    }

    private fun onInit(createdClient: TtsClient, status: Int) {
        synchronized(lock) {
            val current = client
            if (current == null) {
                // TextToSpeech can call this from its constructor, before [engine] assigns [client].
                pendingClient = createdClient
                pendingStatus = status
                return
            }
            // A shutdown client, or the previous client after a new warmUp, must not flip ready.
            if (current !== createdClient) return
            applyInit(current, status)
        }
    }

    private fun applyInit(engine: TtsClient, status: Int) {
        val sessionAtReady = session.get()
        val listeners = mutableListOf<() -> Unit>()
        if (status == TextToSpeech.SUCCESS) {
            try {
                engine.setAudioAttributes(currentAttributes())
            } catch (error: Throwable) {
                // Some engines reject attributes before the binding is usable. Ready still stands.
            }
            ready = true
            listeners += readyCallbacks
            readyCallbacks.clear()
        } else {
            ready = false
        }
        if (listeners.isEmpty()) return
        mainHandler.post {
            if (sessionAtReady != session.get()) return@post
            listeners.forEach { it() }
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidTtsEngine runs only on the main thread"
        }
    }

    private fun deliver(utteranceId: String?, expected: String, sessionAtSpeak: Int, block: () -> Unit) {
        if (utteranceId != expected) return
        if (sessionAtSpeak != session.get()) return
        mainHandler.post {
            if (sessionAtSpeak != session.get()) return@post
            block()
        }
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
            .setUsage(SpeechUsage.usage())
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

    override fun navigationOrAssistantActive(): Boolean {
        // Pleos navigation and Gleo are expected to use these usages.
        // getClientUid is hidden. Our own session shares this process uid, so a
        // guidance-usage clip we are playing is not "nav started".
        // Whether activePlaybackConfigurations actually reports them is unverified on device.
        val self = android.os.Process.myUid()
        return audio.activePlaybackConfigurations.any { config ->
            val usage = config.audioAttributes.usage
            val uid = hiddenClientUid(config)
            if (uid != null) {
                foreignNavOrAssistant(usage, uid, self)
            } else {
                val nav = usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
                    usage == AudioAttributes.USAGE_ASSISTANT
                // No uid: sonification is a different usage, so a real nav stream still
                // counts. Guidance mode shares that usage, so ignore it rather than
                // stopping our own clip.
                nav && SpeechUsage.mode != SpeechUsage.NAVIGATION_GUIDANCE
            }
        }
    }
}

/** @hide AudioPlaybackConfiguration.getClientUid. Null when the platform hides it. */
private fun hiddenClientUid(config: AudioPlaybackConfiguration): Int? {
    val getter = clientUidGetter ?: return null
    return try {
        getter.invoke(config) as? Int
    } catch (_: ReflectiveOperationException) {
        null
    }
}

private val clientUidGetter: java.lang.reflect.Method? = try {
    AudioPlaybackConfiguration::class.java.getMethod("getClientUid")
} catch (_: NoSuchMethodException) {
    null
}

class MainThreadScheduler : SpeechScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(delayMs: Long, block: () -> Unit): SpeechTicket {
        val runnable = Runnable { block() }
        handler.postDelayed(runnable, delayMs)
        return SpeechTicket { handler.removeCallbacks(runnable) }
    }
}
