package io.hmg.appchallenge.foundation.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.domain.GearSelectionCodes
import io.hmg.appchallenge.foundation.domain.MotionResolver
import io.hmg.appchallenge.foundation.domain.SignalLoss
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
 *
 * Car.createCar(listener) is API 30 and VehiclePropertyIds is API 29. minSdk stays 28 so the
 * package can be installed more widely; below API 30 this client publishes [SignalLoss]
 * and does not call those APIs. The contest image is API 34.
 *
 * Gear, UX, and property callbacks are delivered on the main looper. start/stop run on the
 * main thread via ProcessLifecycleOwner, so the snapshot fields are not written from other threads.
 *
 * When CarService is already up, AOSP android14 Car.createCar calls the lifecycle listener on the
 * calling thread before it returns (Car.java dispatchCarReadyToMainThread). CabinSession calls
 * this from the main thread, so the ready callback is synchronous and any field assigned from the
 * createCar return value is still null inside that callback. A generation token accepts that
 * callback and rejects one that arrives after stop or after a newer connect.
 *
 * When CarService is not up yet, createCar returns before the callback. That returned session is
 * kept and disconnected on stop, so it does not wait for a late callback or finalize.
 */
@Singleton
class PleosCarClient internal constructor(
    private val context: Context,
    private val connector: CarConnector?,
    private val rangeRegistrar: RangeRegistrar? = null,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, null)

    private val users = AtomicInteger(0)
    private val connectGen = AtomicInteger(0)
    private val _driving = MutableStateFlow(DrivingSnapshot())
    val driving: StateFlow<DrivingSnapshot> = _driving

    private val _rangeMeters = MutableStateFlow<Float?>(null)
    val rangeMeters: StateFlow<Float?> = _rangeMeters

    @Volatile private var link: CarLink? = null
    private var pending: CarLink? = null
    private var closing: CarLink? = null
    private var releasedLink: CarLink? = null
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            clearSignals()
            return
        }
        connect()
    }

    fun stop() {
        if (users.decrementAndGet() > 0) return
        disconnect()
    }

    fun refreshRange() {
        val manager = propertyManager ?: return
        readRange(manager)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun connect() {
        val gen = connectGen.incrementAndGet()
        releasedLink = null
        val opening = connector ?: AndroidCarConnector(
            context,
            bind = { created -> bind(created) },
            unbind = { unregisterManagers() },
        )
        try {
            val opened = opening.open { created, ready ->
                if (gen != connectGen.get() || users.get() == 0) {
                    release(created)
                    return@open
                }
                link = created
                if (!ready) {
                    // CarService restart: same Car, generation unchanged. Drop subscriptions only.
                    created.unbindSignals()
                    clearSignals()
                    return@open
                }
                created.bindSignals()
                // Production bind() already registered when it obtained a property manager.
                // Tests have no manager, so the same flag still records the registration.
                if (propertyManager == null) {
                    ensureRangeRegistered { rangeRegistrar?.register() ?: false }
                }
            }
            // The sync callback may already have stopped the session. Do not resurrect its Car.
            if (gen == connectGen.get() && users.get() > 0) {
                pending = opened
            } else {
                release(opened)
            }
        } catch (error: Throwable) {
            clearSignals()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun subscribeProperties(manager: CarPropertyManager) {
        rangeRegistered = false
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

    @RequiresApi(Build.VERSION_CODES.R)
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
        ensureRangeRegistered {
            registerRangeCallback(manager, propertyCallback)
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
        val lost = SignalLoss.drivingSnapshot()
        gearRaw = lost.gearRaw
        gearAvailable = lost.gearAvailable
        uxAvailable = lost.uxAvailable
        requiresDistractionOptimization = lost.requiresDistractionOptimization
        restrictionMask = lost.restrictionMask
        maxRestrictedStringLength = lost.maxRestrictedStringLength
        rangeRegistered = false
        _rangeMeters.value = null
        _driving.value = lost
    }

    private fun disconnect() {
        // Bump the generation before releasing so a listener re-entered from disconnect is stale.
        connectGen.incrementAndGet()
        unregisterManagers()
        val current = link
        val held = pending
        link = null
        pending = null
        if (current != null) release(current)
        // createCar's return value, including a Car whose ready callback has not arrived yet.
        if (held != null) release(held)
        rangeRegistered = false
        clearSignals()
    }

    private fun unregisterManagers() {
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
        propertyCallback = null
        propertyManager = null
        uxManager = null
    }

    /**
     * One registration decision for the real property manager and for tests.
     * [clearSignals] resets [rangeRegistered], so a later ready on the same Car registers again.
     */
    private fun ensureRangeRegistered(register: () -> Boolean) {
        if (rangeRegistered || !hasEnergyPermission()) return
        rangeRegistered = try {
            register()
        } catch (error: Throwable) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registerRangeCallback(
        manager: CarPropertyManager,
        callback: CarPropertyManager.CarPropertyEventCallback?,
    ): Boolean {
        if (callback == null) return false
        return manager.registerCallback(
            callback,
            VehiclePropertyIds.RANGE_REMAINING,
            CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
    }

    private fun release(target: CarLink) {
        if (closing === target || releasedLink === target) return
        val outer = closing
        closing = target
        try {
            target.disconnect()
        } catch (error: Throwable) {
            // The service may already be gone.
        } finally {
            releasedLink = target
            closing = outer
        }
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

/** Test seam. Production calls [Car.createCar]; tests deliver the listener themselves. */
internal fun interface CarConnector {
    /** @return the session [Car.createCar] returned, even when the listener has not run yet. */
    fun open(listener: (CarLink, Boolean) -> Unit): CarLink
}

/** One Car session. [disconnect] is safe to call again while a release is already in progress. */
internal interface CarLink {
    fun disconnect()
    fun bindSignals()
    fun unbindSignals()
}

/** Test double for RANGE_REMAINING registration when no [CarPropertyManager] exists. */
internal fun interface RangeRegistrar {
    fun register(): Boolean
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidCarConnector(
    private val context: Context,
    private val bind: (Car) -> Unit,
    private val unbind: () -> Unit,
) : CarConnector {
    override fun open(listener: (CarLink, Boolean) -> Unit): CarLink {
        val handler = Handler(Looper.getMainLooper())
        var session: AndroidCarLink? = null
        val car = Car.createCar(
            context,
            handler,
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
            Car.CarServiceLifecycleListener { created, ready ->
                // Sync dispatch runs before createCar returns, so reuse the wrapper either way.
                val link = session ?: AndroidCarLink(created, bind, unbind).also { session = it }
                listener(link, ready)
            },
        )
        return session ?: AndroidCarLink(car, bind, unbind).also { session = it }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidCarLink(
    private val car: Car,
    private val bind: (Car) -> Unit,
    private val unbind: () -> Unit,
) : CarLink {
    override fun disconnect() {
        car.disconnect()
    }

    override fun bindSignals() {
        bind(car)
    }

    override fun unbindSignals() {
        unbind()
    }
}
