package com.krazylabs.miri.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * android.car.Car in the SDK is a stub, so these tests inject a [CarConnector].
 * AOSP delivers the ready callback on the main thread before createCar returns.
 * The fake link registers through [PropertyPort], the same path production uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PleosCarClientConnectTest {
    @Test
    fun synchronousReadyBindsBeforeConnectReturns() {
        val connector = ScriptedConnector(deliverImmediately = true)
        val client = PleosCarClient(RuntimeEnvironment.getApplication(), connector)

        onMain { client.start() }

        assertEquals(1, connector.link.binds)
        assertEquals(0, connector.link.disconnects)

        onMain { client.stop() }

        assertEquals(1, connector.link.binds)
        assertEquals(1, connector.link.disconnects)
    }

    @Test
    fun readyAfterStopIsDisconnectedAndNotBound() {
        val connector = ScriptedConnector(deliverImmediately = false)
        val client = PleosCarClient(RuntimeEnvironment.getApplication(), connector)

        onMain {
            client.start()
            client.stop()
        }

        assertEquals(0, connector.link.binds)
        assertEquals(1, connector.link.disconnects)

        onMain { connector.listener.invoke(connector.link, true) }

        assertEquals(0, connector.link.binds)
        assertEquals(1, connector.link.disconnects)
    }

    @Test
    fun serviceRestartRebindsGearWithoutRangeOnTheSameCar() {
        val connector = ScriptedConnector(deliverImmediately = false)
        val application = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(application).grantPermissions(Car.PERMISSION_ENERGY)
        val client = PleosCarClient(application, connector)

        onMain {
            client.start()
            connector.listener.invoke(connector.link, true)
        }

        assertEquals(1, connector.link.binds)
        assertEquals(0, connector.link.unbinds)
        assertEquals(1, connector.link.port.gearRegistrations)
        assertEquals(0, connector.link.port.rangeRegistrations)
        assertEquals(0, connector.link.port.unregisters)
        assertEquals(0, connector.link.disconnects)

        onMain { connector.listener.invoke(connector.link, false) }

        assertEquals(1, connector.link.binds)
        assertEquals(1, connector.link.unbinds)
        assertEquals(1, connector.link.port.unregisters)
        assertEquals(0, connector.link.disconnects)
        assertFalse(client.driving.value.uxAvailable)
        assertTrue(client.driving.value.requiresDistractionOptimization)

        onMain { connector.listener.invoke(connector.link, true) }

        assertEquals(2, connector.link.binds)
        assertEquals(1, connector.link.unbinds)
        assertEquals(2, connector.link.port.gearRegistrations)
        assertEquals(0, connector.link.port.rangeRegistrations)
        assertEquals(0, connector.link.disconnects)
    }

    @Test
    fun startOffTheMainThreadIsRejected() {
        val client = PleosCarClient(
            RuntimeEnvironment.getApplication(),
            ScriptedConnector(deliverImmediately = false),
        )
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        Thread {
            try {
                client.start()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                latch.countDown()
            }
        }.start()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(failure.get() is IllegalStateException)
    }

    private class ScriptedConnector(
        private val deliverImmediately: Boolean,
    ) : CarConnector {
        val link = RecordingLink()
        lateinit var listener: (CarLink, Boolean) -> Unit

        override fun open(host: CarHost, listener: (CarLink, Boolean) -> Unit): CarLink {
            this.listener = listener
            link.host = host
            link.onDisconnect = { listener(link, false) }
            if (deliverImmediately) listener(link, true)
            return link
        }
    }

    private class RecordingLink : CarLink {
        var binds = 0
        var unbinds = 0
        var disconnects = 0
        lateinit var host: CarHost
        val port = FakePropertyPort()
        var onDisconnect: (() -> Unit)? = null

        override fun disconnect() {
            disconnects += 1
            val reenter = onDisconnect
            onDisconnect = null
            reenter?.invoke()
        }

        override fun bindSignals() {
            binds += 1
            host.bindProperties(port)
        }

        override fun unbindSignals() {
            unbinds += 1
            host.unbind()
        }
    }

    private class FakePropertyPort : PropertyPort {
        var gearRegistrations = 0
        var rangeRegistrations = 0
        var unregisters = 0

        override fun isPropertyAvailable(propertyId: Int, areaId: Int): Boolean = true

        override fun registerCallback(listener: PortListener, propertyId: Int, rate: Float): Boolean {
            when (propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> gearRegistrations += 1
                VehiclePropertyIds.RANGE_REMAINING -> rangeRegistrations += 1
            }
            return true
        }

        override fun unregisterCallback(listener: PortListener) {
            unregisters += 1
        }

        override fun getIntProperty(propertyId: Int, areaId: Int): Int = 0

        override fun getIntArrayProperty(propertyId: Int, areaId: Int): IntArray = intArrayOf()

        override fun getFloatProperty(propertyId: Int, areaId: Int): Float = 0f
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
}
