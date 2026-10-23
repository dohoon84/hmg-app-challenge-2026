package io.hmg.appchallenge.foundation.car

import android.car.Car
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
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

        assertEquals(0, connector.link.binds)
        assertEquals(1, connector.link.disconnects)

        connector.listener.invoke(connector.link, true)

        assertEquals(0, connector.link.binds)
        assertEquals(1, connector.link.disconnects)
    }

    @Test
    fun serviceRestartUnbindsThenRebindsRangeOnTheSameCar() {
        val connector = ScriptedConnector(deliverImmediately = false)
        val range = RecordingRange()
        val application = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(application).grantPermissions(Car.PERMISSION_ENERGY)
        val client = PleosCarClient(application, connector, range)

        client.start()
        connector.listener.invoke(connector.link, true)

        assertEquals(1, connector.link.binds)
        assertEquals(0, connector.link.unbinds)
        assertEquals(1, range.registrations)
        assertEquals(0, connector.link.disconnects)
        assertFalse(client.driving.value.requiresDistractionOptimization)

        connector.listener.invoke(connector.link, false)

        assertEquals(1, connector.link.binds)
        assertEquals(1, connector.link.unbinds)
        assertEquals(0, connector.link.disconnects)
        assertFalse(client.driving.value.uxAvailable)
        assertTrue(client.driving.value.requiresDistractionOptimization)
        assertNull(client.rangeMeters.value)

        connector.listener.invoke(connector.link, true)

        assertEquals(2, connector.link.binds)
        assertEquals(1, connector.link.unbinds)
        assertEquals(2, range.registrations)
        assertEquals(0, connector.link.disconnects)
    }

    private class ScriptedConnector(
        private val deliverImmediately: Boolean,
    ) : CarConnector {
        val link = RecordingLink()
        lateinit var listener: (CarLink, Boolean) -> Unit

        override fun open(listener: (CarLink, Boolean) -> Unit): CarLink {
            this.listener = listener
            link.onDisconnect = { listener(link, false) }
            if (deliverImmediately) listener(link, true)
            return link
        }
    }

    private class RecordingLink : CarLink {
        var binds = 0
        var unbinds = 0
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

        override fun unbindSignals() {
            unbinds += 1
        }
    }

    private class RecordingRange : RangeRegistrar {
        var registrations = 0

        override fun register(): Boolean {
            registrations += 1
            return true
        }
    }
}
