package io.hmg.appchallenge.foundation.car

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * android.car.Car in the SDK is a stub, so these tests inject a [CarConnector].
 * AOSP delivers the ready callback on the main thread before createCar returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PleosCarClientConnectTest {
    @Test
    fun synchronousReadyBindsBeforeConnectReturns() {
        val connector = ScriptedConnector(deliverImmediately = true)
        val client = PleosCarClient(RuntimeEnvironment.getApplication(), connector)

        client.start()

        assertEquals(1, connector.link.binds)
        assertEquals(0, connector.link.disconnects)

        client.stop()

        assertEquals(1, connector.link.binds)
        assertEquals(1, connector.link.disconnects)
    }

    @Test
    fun readyAfterStopIsDisconnectedAndNotBound() {
        val connector = ScriptedConnector(deliverImmediately = false)
        val client = PleosCarClient(RuntimeEnvironment.getApplication(), connector)

        client.start()
        client.stop()
        connector.listener.invoke(connector.link, true)

        assertEquals(0, connector.link.binds)
        assertEquals(1, connector.link.disconnects)
    }

    private class ScriptedConnector(
        private val deliverImmediately: Boolean,
    ) : CarConnector {
        val link = RecordingLink()
        lateinit var listener: (CarLink, Boolean) -> Unit

        override fun open(listener: (CarLink, Boolean) -> Unit) {
            this.listener = listener
            link.onDisconnect = { listener(link, false) }
            if (deliverImmediately) listener(link, true)
        }
    }

    private class RecordingLink : CarLink {
        var binds = 0
        var disconnects = 0
        var onDisconnect: (() -> Unit)? = null

        override fun disconnect() {
            disconnects += 1
            val reenter = onDisconnect
            onDisconnect = null
            reenter?.invoke()
        }

        override fun bindSignals() {
            binds += 1
        }
    }
}
