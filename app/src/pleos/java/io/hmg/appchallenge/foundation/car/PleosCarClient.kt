package io.hmg.appchallenge.foundation.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.domain.GearSelectionCodes
import io.hmg.appchallenge.foundation.domain.MotionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One AAOS Car connection shared by gear, UX restrictions, and the optional range property.
 *
 * Gear comes from [VehiclePropertyIds.GEAR_SELECTION] (permission CAR_POWERTRAIN, normal).
 * Vehicle.getGear() exists on the 2.3.2 AAR but the published class page sample is stale
 * and the planning guide says gear is standard AAOS data. This class does not call it.
 *
 * Remaining range is [VehiclePropertyIds.RANGE_REMAINING] (permission CAR_ENERGY, dangerous).
 * The Vehicle SDK has no range getter. Whether Pleos exposes this property is unverified.
 */
@Singleton
class PleosCarClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val users = AtomicInteger(0)
    private val _driving = MutableStateFlow(DrivingSnapshot())
    val driving: StateFlow<DrivingSnapshot> = _driving

    private val _rangeMeters = MutableStateFlow<Float?>(null)
    val rangeMeters: StateFlow<Float?> = _rangeMeters

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var propertyCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var gearRaw: Int? = null
    private var gearAvailable = false
    private var uxAvailable = false
    private var requiresDistractionOptimization = false
    private var restrictionMask = 0
    private var maxRestrictedStringLength = 0
    private var rangeRegistered = false

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        applyUx(restrictions)
    }

    fun start() {
        if (users.incrementAndGet() != 1) return
        try {
            car = Car.createCar(
                context,
                null,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { created, ready ->
                    if (!ready) {
                        clearSignals()
                        return@CarServiceLifecycleListener
                    }
                    bind(created)
                },
            )
        } catch (error: Throwable) {
            clearSignals()
        }
    }

    fun stop() {
        if (users.decrementAndGet() > 0) return
        disconnect()
    }

    fun refreshRange() {
        val manager = propertyManager ?: return
        readRange(manager)
    }

    private fun bind(created: Car) {
        val manager = created.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        propertyManager = manager
        if (manager != null) {
            subscribeProperties(manager)
        }
        val ux = created.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        uxManager = ux
        if (ux != null) {
            uxAvailable = true
            try {
                ux.registerListener(uxListener)
                ux.currentCarUxRestrictions?.let(::applyUx)
            } catch (error: Throwable) {
                uxAvailable = false
            }
        }
        publishDriving()
    }

    private fun subscribeProperties(manager: CarPropertyManager) {
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> {
                        gearRaw = value.value as? Int
                        gearAvailable = true
                        publishDriving()
                    }
                    VehiclePropertyIds.RANGE_REMAINING -> {
                        _rangeMeters.value = numericFloat(value.value)
                    }
                }
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                if (propId == VehiclePropertyIds.GEAR_SELECTION) {
                    gearAvailable = false
                    gearRaw = null
                    publishDriving()
                }
            }
        }
        propertyCallback = callback
        gearAvailable = try {
            manager.isPropertyAvailable(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
        } catch (error: Throwable) {
            false
        }
        if (gearAvailable) {
            try {
                manager.registerCallback(
                    callback,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                )
                gearRaw = manager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
            } catch (error: Throwable) {
                gearAvailable = false
                gearRaw = null
            }
        }
        readRange(manager)
        publishDriving()
    }

    private fun readRange(manager: CarPropertyManager) {
        if (!hasEnergyPermission()) {
            _rangeMeters.value = null
            return
        }
        val available = try {
            manager.isPropertyAvailable(VehiclePropertyIds.RANGE_REMAINING, GLOBAL_AREA)
        } catch (error: Throwable) {
            false
        }
        if (!available) {
            _rangeMeters.value = null
            return
        }
        val callback = propertyCallback
        if (!rangeRegistered && callback != null) {
            rangeRegistered = try {
                manager.registerCallback(
                    callback,
                    VehiclePropertyIds.RANGE_REMAINING,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                )
            } catch (error: Throwable) {
                false
            }
        }
        _rangeMeters.value = try {
            manager.getFloatProperty(VehiclePropertyIds.RANGE_REMAINING, GLOBAL_AREA)
        } catch (error: Throwable) {
            null
        }
    }

    private fun applyUx(restrictions: CarUxRestrictions) {
        uxAvailable = true
        requiresDistractionOptimization = restrictions.isRequiresDistractionOptimization
        restrictionMask = restrictions.activeRestrictions
        maxRestrictedStringLength = restrictions.maxRestrictedStringLength
        publishDriving()
    }

    private fun publishDriving() {
        val gear = GearSelectionCodes.toGear(if (gearAvailable) gearRaw else null)
        _driving.value = DrivingSnapshot(
            gear = gear,
            gearRaw = gearRaw,
            gearAvailable = gearAvailable,
            uxAvailable = uxAvailable,
            requiresDistractionOptimization = requiresDistractionOptimization,
            restrictionMask = restrictionMask,
            maxRestrictedStringLength = maxRestrictedStringLength,
            motion = MotionResolver.resolve(gear, requiresDistractionOptimization),
        )
    }

    private fun hasEnergyPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Car.PERMISSION_ENERGY) == PackageManager.PERMISSION_GRANTED

    private fun clearSignals() {
        gearRaw = null
        gearAvailable = false
        uxAvailable = false
        requiresDistractionOptimization = false
        restrictionMask = 0
        maxRestrictedStringLength = 0
        _rangeMeters.value = null
        publishDriving()
    }

    private fun disconnect() {
        try {
            uxManager?.unregisterListener()
        } catch (error: Throwable) {
            // The UX service may already be gone.
        }
        val callback = propertyCallback
        if (callback != null) {
            try {
                propertyManager?.unregisterCallback(callback)
            } catch (error: Throwable) {
                // Ignore a disconnect race.
            }
        }
        try {
            car?.disconnect()
        } catch (error: Throwable) {
            // Ignore a disconnect race.
        }
        propertyCallback = null
        propertyManager = null
        uxManager = null
        car = null
        rangeRegistered = false
        clearSignals()
    }

    private fun numericFloat(value: Any?): Float? = when (value) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        else -> null
    }

    private companion object {
        const val GLOBAL_AREA = 0
    }
}
