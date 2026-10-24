package com.krazylabs.miri.car

import android.car.Car

/** Release builds do not log connector probes. */
internal object ConnectorDebug {
    fun attach(car: Car) = Unit

    fun clear() = Unit

    fun log(values: IntArray) = Unit

    fun probe(read: () -> IntArray): String? = null
}
