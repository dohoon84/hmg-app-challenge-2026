package com.krazylabs.miri.car

import android.car.VehiclePropertyIds
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PleosConnectorProbeTest {
    @Test
    fun pleosNamesFollowTheVhalIds() {
        assertEquals("[IEC_TYPE_4_DC]", ConnectorProbe.pleosLabels(intArrayOf(4)))
        assertEquals("[IEC_TYPE_1_CCS_DC]", ConnectorProbe.pleosLabels(intArrayOf(5)))
        assertEquals("[IEC_TYPE_2_CCS_DC]", ConnectorProbe.pleosLabels(intArrayOf(6)))
        assertEquals("[IEC_TYPE_3_AC]", ConnectorProbe.pleosLabels(intArrayOf(3)))
        assertEquals("[TESLA_ROADSTER]", ConnectorProbe.pleosLabels(intArrayOf(7)))
        assertEquals("[]", ConnectorProbe.pleosLabels(intArrayOf(99)))
        assertNull(ConnectorProbe.readInfoManager(null))
    }

    @Test
    fun debugHookPrintsThePropertyValueAndPleosName() {
        onMain {
            val connector = ScriptedConnector()
            val client = PleosCarClient(RuntimeEnvironment.getApplication(), connector)
            client.start()
            shadowOf(Looper.getMainLooper()).idle()
            val line = client.debugProbeConnectors()
            assertTrue(line!!.contains("propertyId=${VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE}"))
            assertTrue(line.contains("CarPropertyManager=[5]"))
            assertTrue(line.contains("vhal=[CCS1]"))
            assertTrue(line.contains("CarInfoManager.getEvConnectorTypes()=unavailable"))
            assertTrue(line.contains("Pleos=[IEC_TYPE_1_CCS_DC]"))
            client.stop()
        }
    }

    private class ScriptedConnector : CarConnector {
        val link = RecordingLink()
        override fun open(host: CarHost, listener: (CarLink, Boolean) -> Unit): CarLink {
            link.host = host
            listener(link, true)
            return link
        }
    }

    private class RecordingLink : CarLink {
        lateinit var host: CarHost
        override fun disconnect() = Unit
        override fun bindSignals() {
            host.bindProperties(Port())
        }
        override fun unbindSignals() {
            host.unbind()
        }
    }

    private class Port : PropertyPort {
        override fun isPropertyAvailable(propertyId: Int, areaId: Int): Boolean = true
        override fun registerCallback(listener: PortListener, propertyId: Int, rate: Float): Boolean = true
        override fun unregisterCallback(listener: PortListener) = Unit
        override fun getIntProperty(propertyId: Int, areaId: Int): Int = 4
        override fun getIntArrayProperty(propertyId: Int, areaId: Int): IntArray =
            if (propertyId == VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE) intArrayOf(5) else intArrayOf()
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float = 0f
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            val error = java.util.concurrent.atomic.AtomicReference<Throwable>()
            val latch = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(Looper.getMainLooper()).post {
                try {
                    block()
                } catch (thrown: Throwable) {
                    error.set(thrown)
                } finally {
                    latch.countDown()
                }
            }
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            error.get()?.let { throw it }
        }
    }
}
