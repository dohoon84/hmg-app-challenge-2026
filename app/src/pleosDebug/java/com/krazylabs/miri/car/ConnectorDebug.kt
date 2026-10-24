package com.krazylabs.miri.car

import android.car.Car

/**
 * Debug-only connector log. Release builds use the no-op in src/pleosRelease.
 */
internal object ConnectorDebug {
    private var car: Car? = null

    fun attach(car: Car) {
        this.car = car
    }

    fun clear() {
        car = null
    }

    fun log(values: IntArray) {
        try {
            ConnectorProbe.log(car, values)
        } catch (error: Throwable) {
            // The identity read still stands when the diagnostic line fails.
        }
    }

    fun probe(read: () -> IntArray): String? = try {
        ConnectorProbe.log(car, read())
    } catch (error: Throwable) {
        null
    }
}
