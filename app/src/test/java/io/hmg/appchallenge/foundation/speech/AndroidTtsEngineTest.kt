package io.hmg.appchallenge.foundation.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AndroidTtsEngineTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun warmUpCreatesTheClientOnce() = onMain {
        val factory = FakeFactory()
        val engine = AndroidTtsEngine(app, factory)
        assertEquals(0, factory.created)
        engine.warmUp()
        assertEquals(1, factory.created)
        assertFalse(engine.ready)
        engine.warmUp()
        assertEquals(1, factory.created)
    }

    @Test
    fun successInitMarksReadyAndSetsAttributes() = onMain {
        val factory = FakeFactory()
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        factory.latest.completeInit(TextToSpeech.SUCCESS)
        assertTrue(engine.ready)
        assertEquals(1, factory.latest.attributeSets)
    }

    @Test
    fun syncInitDuringCreateIsAppliedAfterTheClientIsAssigned() = onMain {
        val factory = FakeFactory(syncStatus = TextToSpeech.SUCCESS)
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        assertTrue(engine.ready)
        assertEquals(1, factory.latest.attributeSets)
    }

    @Test
    fun failedInitStaysNotReady() = onMain {
        val factory = FakeFactory(syncStatus = TextToSpeech.ERROR)
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        assertFalse(engine.ready)
        assertThrows(IllegalStateException::class.java) {
            engine.speak("안녕", {}, {}, {})
        }
    }

    @Test
    fun wrongUtteranceIdIsIgnoredAndTheMatchingIdCompletes() = onMain {
        val factory = FakeFactory(syncStatus = TextToSpeech.SUCCESS)
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        var done = 0
        engine.speak("안녕", onDone = { done += 1 }, onError = {}, onStop = {})
        val listener = factory.latest.listener
        listener.onDone("other")
        idleMain()
        assertEquals(0, done)
        listener.onDone(factory.latest.lastUtteranceId)
        idleMain()
        assertEquals(1, done)
    }

    @Test
    fun shutdownIgnoresALateCallbackAndDropsReady() = onMain {
        val factory = FakeFactory(syncStatus = TextToSpeech.SUCCESS)
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        var done = 0
        engine.speak("안녕", onDone = { done += 1 }, onError = {}, onStop = {})
        val listener = factory.latest.listener
        val id = factory.latest.lastUtteranceId
        engine.shutdown()
        assertFalse(engine.ready)
        listener.onDone(id)
        idleMain()
        assertEquals(0, done)
    }

    @Test
    fun lateInitOfTheOldClientDoesNotReadyTheNextEngine() = onMain {
        val factory = FakeFactory()
        val engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        val first = factory.latest
        engine.shutdown()
        engine.warmUp()
        val second = factory.latest
        assertEquals(2, factory.created)
        first.completeInit(TextToSpeech.SUCCESS)
        assertFalse(engine.ready)
        second.completeInit(TextToSpeech.SUCCESS)
        assertTrue(engine.ready)
        assertEquals(1, second.attributeSets)
        assertEquals(0, first.attributeSets)
    }

    @Test
    fun shutdownDuringCreateDoesNotAssignTheClient() = onMain {
        lateinit var engine: AndroidTtsEngine
        val factory = FakeFactory(duringCreate = { engine.shutdown() })
        engine = AndroidTtsEngine(app, factory)
        engine.warmUp()
        assertFalse(engine.ready)
        assertEquals(1, factory.latest.shutdowns)
        engine.warmUp()
        assertEquals(2, factory.created)
        assertFalse(engine.ready)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun onMain(block: () -> Unit) {
        val main = Looper.getMainLooper()
        if (Looper.myLooper() == main) {
            block()
            return
        }
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        Handler(main).post {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                latch.countDown()
            }
        }
        Shadows.shadowOf(main).idle()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }

    private class FakeFactory(
        private val syncStatus: Int? = null,
        private val duringCreate: (() -> Unit)? = null,
    ) : TtsClientFactory {
        val clients = mutableListOf<FakeClient>()
        val created: Int get() = clients.size
        val latest: FakeClient get() = clients.last()

        override fun create(context: Context, onInit: (TtsClient, Int) -> Unit): TtsClient {
            val client = FakeClient(onInit)
            clients += client
            duringCreate?.invoke()
            syncStatus?.let { status -> onInit(client, status) }
            return client
        }
    }

    private class FakeClient(
        private val onInit: (TtsClient, Int) -> Unit,
    ) : TtsClient {
        var attributeSets = 0
        var shutdowns = 0
        var lastUtteranceId: String? = null
        lateinit var listener: UtteranceProgressListener

        fun completeInit(status: Int) {
            onInit(this, status)
        }

        override fun setAudioAttributes(attributes: AudioAttributes) {
            attributeSets += 1
        }

        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener?) {
            if (listener != null) this.listener = listener
        }

        override fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String): Int {
            lastUtteranceId = utteranceId
            return TextToSpeech.SUCCESS
        }

        override fun stop() = Unit

        override fun shutdown() {
            shutdowns += 1
        }
    }
}
