package com.krazylabs.miri.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import com.krazylabs.miri.domain.DrivingSnapshot
import com.krazylabs.miri.domain.GearSelectionCodes
import com.krazylabs.miri.domain.MotionResolver
import com.krazylabs.miri.domain.SignalLoss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One AAOS Car connection shared by gear, UX restrictions, and the static INFO_* identity.
 *
 * Gear comes from [VehiclePropertyIds.GEAR_SELECTION] (permission CAR_POWERTRAIN).
 * Pleos Vehicle.getGear() is not called here. The vehicle repository may overlay it
 * only when the gear backend is PLEOS.
 *
 * Remaining range is not read from RANGE_REMAINING. android.car CAR_ENERGY is not used.
 *
 * Car.createCar(listener) is API 30 and VehiclePropertyIds is API 29. minSdk stays 28 so the
 * package can be installed more widely; below API 30 this client publishes [SignalLoss]
 * and does not call those APIs. The contest image is API 34.
 *
 * Every callback and every CabinSession start/stop runs on the main looper
 * (AOSP posts CarServiceLifecycleListener there; ProcessLifecycleOwner is main).
 * The fields below are plain vars for that reason. [start] and [stop] reject any other thread.
 * There is no test-only registration branch in this class.
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
@MainThread
@Singleton
class PleosCarClient internal constructor(
    private val context: Context,
    private val connector: CarConnector?,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, null)

    private val users = AtomicInteger(0)
    private val connectGen = AtomicInteger(0)
    private val _driving = MutableStateFlow(DrivingSnapshot())
    val driving: StateFlow<DrivingSnapshot> = _driving

    private val _identity = MutableStateFlow(AaosIdentity())
    val identity: StateFlow<AaosIdentity> = _identity

    private var link: CarLink? = null
    private var pending: CarLink? = null
    private var closing: CarLink? = null
    private var releasedLink: CarLink? = null
    private var propertyPort: PropertyPort? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var gearRaw: Int? = null
    private var gearAvailable = false
    private var uxAvailable = false
    private var requiresDistractionOptimization = false
    private var restrictionMask = 0
    private var maxRestrictedStringLength = 0
    private var gearOverride: com.krazylabs.miri.domain.GearPosition? = null

    private val portListener = object : PortListener {
        override fun onChange(propertyId: Int, value: Any?) {
            when (propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> {
                    gearRaw = value as? Int
                    gearAvailable = true
                    publishDriving()
                }
            }
        }

        override fun onError(propertyId: Int, areaId: Int) {
            if (propertyId == VehiclePropertyIds.GEAR_SELECTION) {
                gearAvailable = false
                gearRaw = null
                publishDriving()
            }
        }
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        applyUx(restrictions)
    }

    private val host = object : CarHost {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun bindProperties(port: PropertyPort) {
            propertyPort = port
            subscribeProperties(port)
        }

        @RequiresApi(Build.VERSION_CODES.R)
        override fun bindUx(car: Car) {
            val ux = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
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

        override fun unbind() {
            unregisterManagers()
        }
    }

    fun start() {
        checkMainThread()
        if (users.incrementAndGet() != 1) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            clearSignals()
            return
        }
        connect()
    }

    fun stop() {
        checkMainThread()
        if (users.decrementAndGet() > 0) return
        disconnect()
    }

    fun refreshIdentity() {
        checkMainThread()
        val port = propertyPort ?: return
        readIdentity(port)
    }

    /** Ignored unless the gear backend is PLEOS. AAOS GEAR_SELECTION stays primary. */
    fun overlayGear(position: com.krazylabs.miri.domain.GearPosition) {
        checkMainThread()
        if (com.krazylabs.miri.BuildConfig.GEAR_BACKEND != "PLEOS") return
        gearOverride = position
        gearAvailable = true
        publishDriving()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun connect() {
        val gen = connectGen.incrementAndGet()
        // A second disconnect of the same wrapper is skipped only inside this generation.
        // connect() clears releasedLink, so a late callback from the previous Car, after a new
        // start, may disconnect once more. AOSP Car.disconnect() is idempotent.
        releasedLink = null
        val opening = connector ?: AndroidCarConnector(context)
        try {
            val opened = opening.open(host) { created, ready ->
                checkMainThread()
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
    private fun subscribeProperties(port: PropertyPort) {
        gearAvailable = try {
            port.isPropertyAvailable(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
        } catch (error: Throwable) {
            false
        }
        if (gearAvailable) {
            try {
                port.registerCallback(
                    portListener,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                )
                gearRaw = port.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
            } catch (error: Throwable) {
                gearAvailable = false
                gearRaw = null
            }
        }
        readIdentity(port)
        publishDriving()
    }

    private fun readIdentity(port: PropertyPort) {
        _identity.value = AaosIdentity(
            fuelTypes = readInts(port, VehiclePropertyIds.INFO_FUEL_TYPE),
            fuelDoor = readInts(port, VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION),
            evPort = readInts(port, VehiclePropertyIds.INFO_EV_PORT_LOCATION),
            connector = readInts(port, VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE),
        )
    }

    private fun readInts(port: PropertyPort, propertyId: Int): IntArray = try {
        if (!port.isPropertyAvailable(propertyId, GLOBAL_AREA)) {
            intArrayOf()
        } else {
            port.getIntArrayProperty(propertyId, GLOBAL_AREA)
        }
    } catch (error: Throwable) {
        intArrayOf()
    }

    private fun applyUx(restrictions: CarUxRestrictions) {
        uxAvailable = true
        requiresDistractionOptimization = restrictions.isRequiresDistractionOptimization
        restrictionMask = restrictions.activeRestrictions
        maxRestrictedStringLength = restrictions.maxRestrictedStringLength
        publishDriving()
    }

    private fun publishDriving() {
        val gear = gearOverride ?: GearSelectionCodes.toGear(if (gearAvailable) gearRaw else null)
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

    private fun clearSignals() {
        val lost = SignalLoss.drivingSnapshot()
        gearRaw = lost.gearRaw
        gearAvailable = lost.gearAvailable
        uxAvailable = lost.uxAvailable
        requiresDistractionOptimization = lost.requiresDistractionOptimization
        restrictionMask = lost.restrictionMask
        maxRestrictedStringLength = lost.maxRestrictedStringLength
        gearOverride = null
        _identity.value = AaosIdentity()
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
        clearSignals()
    }

    private fun unregisterManagers() {
        try {
            uxManager?.unregisterListener()
        } catch (error: Throwable) {
            // The UX service may already be gone.
        }
        try {
            propertyPort?.unregisterCallback(portListener)
        } catch (error: Throwable) {
            // Ignore a disconnect race.
        }
        propertyPort = null
        uxManager = null
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

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PleosCarClient runs only on the main thread"
        }
    }

    private companion object {
        const val GLOBAL_AREA = 0
    }
}

data class AaosIdentity(
    val fuelTypes: IntArray = intArrayOf(),
    val fuelDoor: IntArray = intArrayOf(),
    val evPort: IntArray = intArrayOf(),
    val connector: IntArray = intArrayOf(),
)

/** Property reads shared by [CarPropertyManager] and the connect tests. */
internal interface PropertyPort {
    fun isPropertyAvailable(propertyId: Int, areaId: Int): Boolean
    fun registerCallback(listener: PortListener, propertyId: Int, rate: Float): Boolean
    fun unregisterCallback(listener: PortListener)
    fun getIntProperty(propertyId: Int, areaId: Int): Int
    fun getIntArrayProperty(propertyId: Int, areaId: Int): IntArray
    fun getFloatProperty(propertyId: Int, areaId: Int): Float
}

internal interface PortListener {
    fun onChange(propertyId: Int, value: Any?)
    fun onError(propertyId: Int, areaId: Int)
}

/** Test seam. Production calls [Car.createCar]; tests deliver the listener themselves. */
internal fun interface CarConnector {
    /** @return the session [Car.createCar] returned, even when the listener has not run yet. */
    fun open(host: CarHost, listener: (CarLink, Boolean) -> Unit): CarLink
}

internal interface CarHost {
    fun bindProperties(port: PropertyPort)
    fun bindUx(car: Car)
    fun unbind()
}

/** One Car session. [disconnect] is safe to call again while a release is already in progress. */
internal interface CarLink {
    fun disconnect()
    fun bindSignals()
    fun unbindSignals()
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidCarConnector(
    private val context: Context,
) : CarConnector {
    override fun open(host: CarHost, listener: (CarLink, Boolean) -> Unit): CarLink {
        val handler = Handler(Looper.getMainLooper())
        var session: AndroidCarLink? = null
        val car = Car.createCar(
            context,
            handler,
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
            Car.CarServiceLifecycleListener { created, ready ->
                // Sync dispatch runs before createCar returns, so reuse the wrapper either way.
                val link = session ?: AndroidCarLink(created, host).also { session = it }
                listener(link, ready)
            },
        )
        return session ?: AndroidCarLink(car, host).also { session = it }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidCarLink(
    private val car: Car,
    private val host: CarHost,
) : CarLink {
    override fun disconnect() {
        car.disconnect()
    }

    override fun bindSignals() {
        val manager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (manager != null) {
            host.bindProperties(CarPropertyPort(manager))
        }
        host.bindUx(car)
    }

    override fun unbindSignals() {
        host.unbind()
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class CarPropertyPort(
    private val manager: CarPropertyManager,
) : PropertyPort {
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private var listener: PortListener? = null

    override fun isPropertyAvailable(propertyId: Int, areaId: Int): Boolean =
        manager.isPropertyAvailable(propertyId, areaId)

    override fun registerCallback(listener: PortListener, propertyId: Int, rate: Float): Boolean {
        this.listener = listener
        val current = callback ?: object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                this@CarPropertyPort.listener?.onChange(value.propertyId, value.value)
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                this@CarPropertyPort.listener?.onError(propId, areaId)
            }
        }.also { callback = it }
        return manager.registerCallback(current, propertyId, rate)
    }

    override fun unregisterCallback(listener: PortListener) {
        callback?.let { manager.unregisterCallback(it) }
        callback = null
        this.listener = null
    }

    override fun getIntProperty(propertyId: Int, areaId: Int): Int =
        manager.getIntProperty(propertyId, areaId)

    override fun getIntArrayProperty(propertyId: Int, areaId: Int): IntArray =
        manager.getIntArrayProperty(propertyId, areaId)

    override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
        manager.getFloatProperty(propertyId, areaId)
}
