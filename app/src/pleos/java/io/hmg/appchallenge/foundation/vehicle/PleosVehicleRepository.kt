package io.hmg.appchallenge.foundation.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
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
import io.hmg.appchallenge.foundation.domain.SeatId
import io.hmg.appchallenge.foundation.domain.SignalAccess
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import io.hmg.appchallenge.foundation.domain.seatSupport
import io.hmg.appchallenge.foundation.domain.unavailableSeats
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
 * Each [start] constructs a new [Vehicle]. Re-initializing a released instance is not documented.
 *
 * Not used: getGear(), hidden battery/port listeners, deprecated setRangeRemainingDistance,
 * deprecated getCRNInfo, seat move, HVAC, and occupancy/belt value listeners.
 * Seat support is a capability query only. Remaining range is read from AAOS, not from the SDK.
 * Vehicle Control APIs are Coming Soon and are not called.
 *
 * The docs name the failure type VehicleException. In the 2.3.2 AAR that type is obfuscated
 * (callback.exception.a), so failures are recorded from Exception.message only.
 */
@Singleton
class PleosVehicleRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val carClient: PleosCarClient,
) : VehicleRepository {
    private val appContext = context
    private var vehicle: Vehicle? = null
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
        override fun onFailed(error: Exception) {
            if (live(sessionToken)) note(error)
        }
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            if (live(sessionToken)) updateSoc(value)
        }
        override fun onFailed(error: Exception) {
            if (live(sessionToken)) note(error)
        }
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            if (live(sessionToken)) updateOdometer(value)
        }
        override fun onFailed(error: Exception) {
            if (live(sessionToken)) note(error)
        }
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
        val created = Vehicle(appContext)
        vehicle = created
        try {
            created.initialize()
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
        subscribeSeats()
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        rangeJob?.cancel()
        rangeJob = null
        listeners.close()
        sessionToken = 0
        val current = vehicle
        vehicle = null
        try {
            current?.release()
        } catch (error: Throwable) {
            note(error)
        }
        carClient.stop()
        _link.value = LinkState.IDLE
        // The next session must not keep the previous capability answer if initialize fails.
        _snapshot.update { it.copy(seats = unavailableSeats()) }
    }

    override fun refresh() {
        carClient.refreshRange()
    }

    private fun subscribeDoors() {
        val token = sessionToken
        val current = vehicle ?: return
        val door = VehicleDomains.door(current)
        try {
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
                        override fun onFailed(error: Exception) {
                            if (live(token)) note(error)
                        }
                    }
                    try {
                        door.registerDoorPosition(area, listener)
                        track(token) { door.unregisterDoorPosition(area, listener) }
                        door.getCurrentDoorPosition(
                            area,
                            { pos -> if (live(token)) updateDoor(id, pos, readable = true) },
                            { error -> if (live(token)) note(error) },
                        )
                    } catch (error: Throwable) {
                        note(error)
                        updateDoor(id, position = null, readable = false)
                    }
                }
            },
            { error ->
                if (!live(token)) return@checkDoorPositionCapabilities
                markDoorsUnreadable()
                note(error)
            },
        )
        } catch (error: Throwable) {
            markDoorsUnreadable()
            note(error)
        }
    }

    private fun subscribeWindows() {
        val token = sessionToken
        val current = vehicle ?: return
        val window = VehicleDomains.window(current)
        try {
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
                        override fun onFailed(error: Exception) {
                            if (live(token)) note(error)
                        }
                    }
                    try {
                        window.registerWindowPosition(area, listener)
                        track(token) { window.unregisterWindowPosition(area, listener) }
                        window.getCurrentWindowPosition(
                            area,
                            { position -> if (live(token)) updateWindow(id, position, readable = true) },
                            { error -> if (live(token)) note(error) },
                        )
                    } catch (error: Throwable) {
                        note(error)
                        updateWindow(id, position = null, readable = false)
                    }
                }
            },
            { error ->
                if (!live(token)) return@checkWindowPositionCapabilities
                markWindowsUnreadable()
                note(error)
            },
        )
        } catch (error: Throwable) {
            markWindowsUnreadable()
            note(error)
        }
    }

    private fun subscribeFuel() {
        val token = sessionToken
        val current = vehicle ?: return
        val fuel = VehicleDomains.fuel(current)
        try {
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
                        { error -> if (live(token)) note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                if (!live(token)) return@checkFuelPercentCapabilities
                _snapshot.update { it.copy(fuelReadable = false, fuelPercent = null) }
                note(error)
            },
        )
        } catch (error: Throwable) {
            _snapshot.update { it.copy(fuelReadable = false, fuelPercent = null) }
            note(error)
        }
    }

    private fun subscribeSoc() {
        val token = sessionToken
        val current = vehicle ?: return
        val battery = VehicleDomains.evBattery(current)
        try {
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
                        { error -> if (live(token)) note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                if (!live(token)) return@checkEvBatteryStateOfChargeCapabilities
                _snapshot.update { it.copy(socReadable = false, socPercent = null) }
                note(error)
            },
        )
        } catch (error: Throwable) {
            _snapshot.update { it.copy(socReadable = false, socPercent = null) }
            note(error)
        }
    }

    private fun subscribeOdometer() {
        val token = sessionToken
        val current = vehicle ?: return
        val odometer = VehicleDomains.odometer(current)
        try {
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
                        { error -> if (live(token)) note(error) },
                    )
                } catch (error: Throwable) {
                    note(error)
                }
            },
            { error ->
                if (!live(token)) return@checkCurrentDistanceDrivenCapabilities
                _snapshot.update { it.copy(odometerReadable = false, odometerKm = null) }
                note(error)
            },
        )
        } catch (error: Throwable) {
            _snapshot.update { it.copy(odometerReadable = false, odometerKm = null) }
            note(error)
        }
    }

    private fun subscribeVin() {
        val token = sessionToken
        val current = vehicle ?: return
        val info = VehicleDomains.carInfo(current)
        try {
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
                    { error -> if (live(token)) note(error) },
                )
            },
            { error ->
                if (!live(token)) return@checkVINInfoCapabilities
                _snapshot.update { it.copy(vinReadable = false, vin = null) }
                note(error)
            },
        )
        } catch (error: Throwable) {
            _snapshot.update { it.copy(vinReadable = false, vin = null) }
            note(error)
        }
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

    private fun subscribeSeats() {
        val token = sessionToken
        val current = vehicle ?: return
        val seat = VehicleDomains.seat(current)
        var occupancy: Pair<SignalAccess, Set<SeatId>>? = null
        var belt: Pair<SignalAccess, Set<SeatId>>? = null
        fun publish() {
            val occ = occupancy ?: return
            val bel = belt ?: return
            if (!live(token)) return
            _snapshot.update { snapshot ->
                snapshot.copy(
                    seats = seatSupport(occ.second, bel.second, occ.first, bel.first),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
        try {
        seat.checkSeatOccupancyCapabilities(
            { config, areas ->
                occupancy = config.accessType.toSignal() to areas.mapNotNull { it.toSeatId() }.toSet()
                publish()
            },
            { error ->
                occupancy = SignalAccess.NONE to emptySet()
                if (live(token)) note(error)
                publish()
            },
        )
        seat.checkSeatBeltBuckledCapabilities(
            { config, areas ->
                belt = config.accessType.toSignal() to areas.mapNotNull { it.toSeatId() }.toSet()
                publish()
            },
            { error ->
                belt = SignalAccess.NONE to emptySet()
                if (live(token)) note(error)
                publish()
            },
        )
        } catch (error: Throwable) {
            note(error)
            if (live(token)) {
                _snapshot.update { it.copy(seats = unavailableSeats()) }
            }
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

private fun SeatArea.toSeatId(): SeatId? {
    val items = values
    if (items.size != 1) return null
    // Left-hand drive. Center and third-row areas are not a named seat.
    return when (items[0]) {
        SeatValue.ROW_1_LEFT -> SeatId.DRIVER
        SeatValue.ROW_1_RIGHT -> SeatId.PASSENGER
        SeatValue.ROW_2_LEFT -> SeatId.REAR_LEFT
        SeatValue.ROW_2_RIGHT -> SeatId.REAR_RIGHT
        else -> null
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
