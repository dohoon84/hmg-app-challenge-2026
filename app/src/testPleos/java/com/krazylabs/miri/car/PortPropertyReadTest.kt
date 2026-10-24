package com.krazylabs.miri.car

import android.car.VehiclePropertyIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortPropertyReadTest {
    @Test
    fun fuelDoorAndEvPortReadInt32() {
        val port = RecordingPort()
        assertEquals(3, readScalarProperty(port, VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION, 0))
        assertEquals(3, readScalarProperty(port, VehiclePropertyIds.INFO_EV_PORT_LOCATION, 0))
        assertEquals(0, port.arrayReads)
        assertEquals(2, port.intReads)
    }

    @Test
    fun aMissingScalarStaysEmpty() {
        val port = RecordingPort(available = false)
        assertNull(readScalarProperty(port, VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION, 0))
        assertEquals(0, port.intReads)
        assertEquals(0, port.arrayReads)
    }

    private class RecordingPort(
        private val available: Boolean = true,
    ) : PropertyPort {
        var intReads = 0
        var arrayReads = 0

        override fun isPropertyAvailable(propertyId: Int, areaId: Int): Boolean = available

        override fun registerCallback(listener: PortListener, propertyId: Int, rate: Float): Boolean = true

        override fun unregisterCallback(listener: PortListener) = Unit

        override fun getIntProperty(propertyId: Int, areaId: Int): Int {
            intReads += 1
            return 3
        }

        override fun getIntArrayProperty(propertyId: Int, areaId: Int): IntArray {
            arrayReads += 1
            return intArrayOf(3)
        }

        override fun getFloatProperty(propertyId: Int, areaId: Int): Float = 0f
    }
}
