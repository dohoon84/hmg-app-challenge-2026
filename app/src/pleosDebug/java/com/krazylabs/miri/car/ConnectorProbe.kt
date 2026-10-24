@file:Suppress("DEPRECATION")

package com.krazylabs.miri.car

import android.car.Car
import android.car.CarInfoManager
import android.car.VehiclePropertyIds
import android.util.Log
import com.krazylabs.miri.domain.ConnectorProbeText

/**
 * Debug log for M6. Prints the three connector reads on one line:
 * CarPropertyManager (VHAL / EvChargingConnectorType), CarInfoManager.getEvConnectorTypes()
 * (android.car.EvConnectorType), and the Pleos EvConnectorType name for the property ints.
 *
 * Pleos CarInfo exposes VIN, CRN, and capability only, so the Pleos column is the SDK enum
 * applied to the same CarPropertyManager integers.
 */
internal object ConnectorProbe {
    private const val TAG = "MiriConnector"

    fun log(car: Car?, propertyValues: IntArray): String {
        val line = ConnectorProbeText.line(
            VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
            propertyValues,
            readInfoManager(car),
            pleosLabels(propertyValues),
        )
        Log.i(TAG, line)
        return line
    }

    fun readInfoManager(car: Car?): IntArray? {
        if (car == null) return null
        return try {
            val info = car.getCarManager(Car.INFO_SERVICE) as? CarInfoManager ?: return null
            info.evConnectorTypes
        } catch (error: Throwable) {
            null
        }
    }

    fun pleosLabels(values: IntArray): String = try {
        PleosConnectorNames.labels(values)
    } catch (error: Throwable) {
        "error"
    }
}
