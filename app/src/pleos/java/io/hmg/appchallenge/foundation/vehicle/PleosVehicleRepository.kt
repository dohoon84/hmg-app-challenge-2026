package io.hmg.appchallenge.foundation.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.hmg.appchallenge.foundation.car.PleosCarClient
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PanelState
import io.hmg.appchallenge.foundation.domain.RangeSource
import io.hmg.appchallenge.foundation.domain.SignalAccess
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import io.hmg.appchallenge.foundation.domain.WindowId
import io.hmg.appchallenge.foundation.domain.allowsRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vehicle SDK 2.3.2 wrapper. Every read is gated on check*Capabilities access READ or READ_WRITE.
 * initialize() has no connection callback in this version, so a successful return is treated as ready
 * and a later capability failure is stored on [VehicleSnapshot.lastError].
 *
 * Not used: getGear(), hidden battery/port listeners, deprecated setRangeRemainingDistance,
 * deprecated getCRNInfo. Remaining range is read from AAOS, not from the SDK.
 *
 * The docs name the failure type VehicleException. In the 2.3.2 AAR that type is obfuscated
 * (callback.exception.a), so failures are recorded from Exception.message only.
 */
@Singleton
class PleosVehicleRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val carClient: PleosCarClient,
) : VehicleRepository {
    private val vehicle = Vehicle(context)
    private val started = AtomicBoolean(false)
    private val listeners = ListenerSession()
    @Volatile private var sessionToken = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rangeJob: Job? = null

    private val _link = MutableStateFlow(LinkState.IDLE)
    override val link: StateFlow<LinkState> = _link

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot

    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) {
            if (live(sessionToken)) updateFuel(value)
        }
        override fun onFailed(error: Exception) = note(error)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            if (live(sessionToken)) updateSoc(value)
        }
        override fun onFailed(error: Exception) = note(error)
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            if (live(sessionToken)) updateOdometer(value)
        }
        override fun onFailed(error: Exception) = note(error)
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        sessionToken = listeners.open()
        _link.value = LinkState.CONNECTING
        carClient.start()
        rangeJob = scope.launch {
            carClient.rangeMeters.collect { meters ->
                _snapshot.update { current ->
                    current.copy(
                        rangeMeters = meters,
                        rangeSource = if (meters == null) {
                            RangeSource.UNAVAILABLE
                        } else {
                            RangeSource.AAOS_RANGE_REMAINING
                        },
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                }
            }
        }
        try {
            vehicle.initialize()
        } catch (error: Throwable) {
            _link.value = LinkState.FAILED
            note(error)
            return
        }
        _link.value = LinkState.READY
        subscribeDoors()
        subscribeWindows()
        subscribeFuel()
        subscribeSoc()
        subscribeOdometer()
        subscribeVin()
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        rangeJob?.cancel()
        rangeJob = null
        listeners.close()
        sessionToken = 0
        try {
            vehicle.release()
        } catch (error: Throwable) {
            note(error)
        }
        carClient.stop()
        _link.value = LinkState.IDLE
    }

    override fun refresh() {
        carClient.refreshRange()
    }

    private fun subscribeDoors() {
        val token = sessionToken
        val door = VehicleDomains.door(vehicle)
        door.checkDoorPositionCapabilities(
            { config, areaMap ->
                if (!live(token)) return@checkDoorPositionCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    markDoorsUnreadable()
                    return@checkDoorPositionCapabilities
                }
                val supported = areaMap.keys.mapNotNull { it.toDoorId() }.toSet()
                DoorId.entries.forEach { id ->
                    if (id !in supported) {
                        updateDoor(id, position = null, readable = false)
                        return@forEach
                    }
                    val area = DoorArea(id.toDoorValue())
                    val listener = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) {
                            if (live(token)) updateDoor(id, pos, readable = true)
                        }
                        override fun onFailed(error: Exception) = note(error)
                    }
                    try {
                        door.registerDoorPosition(area, listener)
                        track(token) { door.unregisterDoorPosition(area, listener) }
                        door.getCurrentDoorPosition(
                            area,
                            { pos -> if (live(token)) updateDoor(id, pos, readable = true) },
                            { error -> note(error) },
                        )
                    } catch (error: Throwable) {
                        note(error)
                        updateDoor(id, position = null, readable = false)
                    }
                }
            },
            { error ->
                markDoorsUnreadable()
                note(error)
            },
        )
    }

    private fun subscribeWindows() {
        val token = sessionToken
        val window = VehicleDomains.window(vehicle)
        window.checkWindowPositionCapabilities(
            { config, areaMap ->
                if (!live(token)) return@checkWindowPositionCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    markWindowsUnreadable()
                    return@checkWindowPositionCapabilities
                }
                val supported = areaMap.keys.mapNotNull { it.toWindowId() }.toSet()
                WindowId.entries.forEach { id ->
                    if (id !in supported) {
                        updateWindow(id, position = null, readable = false)
                        return@forEach
                    }
                    val area = WindowArea(id.toWindowValue())
                    val listener = object : WindowPositionListener {
                        override fun onWindowPositionUpdated(position: Int?) {
                            if (live(token)) updateWindow(id, position, readable = true)
                        }
                        override fun onFailed(error: Exception) = note(error)
                    }
                    try {
                        window.registerWindowPosition(area, listener)
                        track(token) { window.unregisterWindowPosition(area, listener) }
                        window.getCurrentWindowPosition(
                            area,
                            { position -> if (live(token)) updateWindow(id, position, readable = true) },
                            { error -> note(error) },
                        )
                    } catch (error: Throwable) {
                        note(error)
                        updateWindow(id, position = null, readable = false)
                    }
                }
            },
            { error ->
                markWindowsUnreadable()
                note(error)
            },
        )
    }

    private fun subscribeFuel() {
        val token = sessionToken
        val fuel = VehicleDomains.fuel(vehicle)
        fuel.checkFuelPercentCapabilities(
            { config ->
                if (!live(token)) return@checkFuelPercentCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    _snapshot.update { it.copy(fuelReadable = false, fuelPercent = null) }
                    return@checkFuelPercentCapabilities
                }
                try {
                    fuel.registerFuelPercent(fuelListener)
                    track(token) { fuel.unregisterFuelPercent(fuelListener) }
                    fuel.getFuelPercent(
                        { value -> if (live(token)) updateFuel(value) },
                        { error -> note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                _snapshot.update { it.copy(fuelReadable = false, fuelPercent = null) }
                note(error)
            },
        )
    }

    private fun subscribeSoc() {
        val token = sessionToken
        val battery = VehicleDomains.evBattery(vehicle)
        battery.checkEvBatteryStateOfChargeCapabilities(
            { config ->
                if (!live(token)) return@checkEvBatteryStateOfChargeCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    _snapshot.update { it.copy(socReadable = false, socPercent = null) }
                    return@checkEvBatteryStateOfChargeCapabilities
                }
                try {
                    battery.registerEvBatteryStateOfCharge(socListener)
                    track(token) { battery.unregisterEvBatteryStateOfCharge(socListener) }
                    battery.getEvBatteryStateOfCharge(
                        { value -> if (live(token)) updateSoc(value) },
                        { error -> note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                _snapshot.update { it.copy(socReadable = false, socPercent = null) }
                note(error)
            },
        )
    }

    private fun subscribeOdometer() {
        val token = sessionToken
        val odometer = VehicleDomains.odometer(vehicle)
        odometer.checkCurrentDistanceDrivenCapabilities(
            { config ->
                if (!live(token)) return@checkCurrentDistanceDrivenCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    _snapshot.update { it.copy(odometerReadable = false, odometerKm = null) }
                    return@checkCurrentDistanceDrivenCapabilities
                }
                try {
                    odometer.registerDistanceDriven(odometerListener)
                    track(token) { odometer.unregisterDistanceDriven(odometerListener) }
                    odometer.getCurrentDistanceDriven(
                        { value -> if (live(token)) updateOdometer(value) },
                        { error -> note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                _snapshot.update { it.copy(odometerReadable = false, odometerKm = null) }
                note(error)
            },
        )
    }

    private fun subscribeVin() {
        val token = sessionToken
        val info = VehicleDomains.carInfo(vehicle)
        info.checkVINInfoCapabilities(
            { config ->
                if (!live(token)) return@checkVINInfoCapabilities
                if (!config.accessType.toSignal().allowsRead()) {
                    _snapshot.update { it.copy(vinReadable = false, vin = null) }
                    return@checkVINInfoCapabilities
                }
                info.getVINInfo(
                    { value ->
                        if (!live(token)) return@getVINInfo
                        _snapshot.update {
                            it.copy(
                                vin = value,
                                vinReadable = true,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    },
                    { error -> note(error) },
                )
            },
            { error ->
                _snapshot.update { it.copy(vinReadable = false, vin = null) }
                note(error)
            },
        )
    }

    private fun updateDoor(id: DoorId, position: Int?, readable: Boolean) {
        _snapshot.update { current ->
            current.copy(
                doors = current.doors + (id to PanelState(position, readable)),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun updateWindow(id: WindowId, position: Int?, readable: Boolean) {
        _snapshot.update { current ->
            current.copy(
                windows = current.windows + (id to PanelState(position, readable)),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun updateFuel(value: Int?) {
        _snapshot.update {
            it.copy(fuelPercent = value, fuelReadable = true, updatedAtEpochMs = System.currentTimeMillis())
        }
    }

    private fun updateSoc(value: Int?) {
        _snapshot.update {
            it.copy(socPercent = value, socReadable = true, updatedAtEpochMs = System.currentTimeMillis())
        }
    }

    private fun updateOdometer(value: Float?) {
        _snapshot.update {
            it.copy(odometerKm = value, odometerReadable = true, updatedAtEpochMs = System.currentTimeMillis())
        }
    }

    private fun markDoorsUnreadable() {
        _snapshot.update { current ->
            current.copy(doors = DoorId.entries.associateWith { PanelState(null, false) })
        }
    }

    private fun markWindowsUnreadable() {
        _snapshot.update { current ->
            current.copy(windows = WindowId.entries.associateWith { PanelState(null, false) })
        }
    }

    private fun live(token: Int): Boolean = listeners.isCurrent(token)

    private fun track(token: Int, cleanup: () -> Unit) {
        listeners.track(token, cleanup)
    }

    private fun note(error: Throwable) {
        _snapshot.update { it.copy(lastError = error.message ?: error.javaClass.simpleName) }
    }
}

private fun AccessType.toSignal(): SignalAccess = when (this) {
    AccessType.NONE -> SignalAccess.NONE
    AccessType.READ -> SignalAccess.READ
    AccessType.WRITE -> SignalAccess.WRITE
    AccessType.READ_WRITE -> SignalAccess.READ_WRITE
}

private fun DoorArea.toDoorId(): DoorId? {
    val items = values
    if (items.size != 1) return null
    return when (items[0]) {
        DoorValue.ROW_1_LEFT -> DoorId.ROW_1_LEFT
        DoorValue.ROW_1_RIGHT -> DoorId.ROW_1_RIGHT
        DoorValue.ROW_2_LEFT -> DoorId.ROW_2_LEFT
        DoorValue.ROW_2_RIGHT -> DoorId.ROW_2_RIGHT
        DoorValue.ROW_3_LEFT -> DoorId.ROW_3_LEFT
        DoorValue.ROW_3_RIGHT -> DoorId.ROW_3_RIGHT
        DoorValue.HOOD -> DoorId.HOOD
        DoorValue.REAR -> DoorId.REAR
    }
}

private fun DoorId.toDoorValue(): DoorValue = when (this) {
    DoorId.ROW_1_LEFT -> DoorValue.ROW_1_LEFT
    DoorId.ROW_1_RIGHT -> DoorValue.ROW_1_RIGHT
    DoorId.ROW_2_LEFT -> DoorValue.ROW_2_LEFT
    DoorId.ROW_2_RIGHT -> DoorValue.ROW_2_RIGHT
    DoorId.ROW_3_LEFT -> DoorValue.ROW_3_LEFT
    DoorId.ROW_3_RIGHT -> DoorValue.ROW_3_RIGHT
    DoorId.HOOD -> DoorValue.HOOD
    DoorId.REAR -> DoorValue.REAR
}

private fun WindowArea.toWindowId(): WindowId? {
    val items = values
    if (items.size != 1) return null
    return when (items[0]) {
        WindowValue.ROW_1_LEFT -> WindowId.ROW_1_LEFT
        WindowValue.ROW_1_RIGHT -> WindowId.ROW_1_RIGHT
        WindowValue.ROW_2_LEFT -> WindowId.ROW_2_LEFT
        WindowValue.ROW_2_RIGHT -> WindowId.ROW_2_RIGHT
        WindowValue.FRONT_WINDSHIELD -> WindowId.FRONT_WINDSHIELD
        WindowValue.REAR_WINDSHIELD -> WindowId.REAR_WINDSHIELD
        WindowValue.ROOF_TOP_1 -> WindowId.ROOF_TOP_1
        WindowValue.ROOF_TOP_2 -> WindowId.ROOF_TOP_2
    }
}

private fun WindowId.toWindowValue(): WindowValue = when (this) {
    WindowId.ROW_1_LEFT -> WindowValue.ROW_1_LEFT
    WindowId.ROW_1_RIGHT -> WindowValue.ROW_1_RIGHT
    WindowId.ROW_2_LEFT -> WindowValue.ROW_2_LEFT
    WindowId.ROW_2_RIGHT -> WindowValue.ROW_2_RIGHT
    WindowId.FRONT_WINDSHIELD -> WindowValue.FRONT_WINDSHIELD
    WindowId.REAR_WINDSHIELD -> WindowValue.REAR_WINDSHIELD
    WindowId.ROOF_TOP_1 -> WindowValue.ROOF_TOP_1
    WindowId.ROOF_TOP_2 -> WindowValue.ROOF_TOP_2
}
