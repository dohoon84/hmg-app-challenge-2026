package com.krazylabs.miri.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.car.PleosCarClient
import com.krazylabs.miri.domain.CarFacts
import com.krazylabs.miri.domain.DoorId
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.PanelState
import com.krazylabs.miri.domain.RangeSource
import com.krazylabs.miri.domain.SeatId
import com.krazylabs.miri.domain.SignalAccess
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.domain.seatSupport
import com.krazylabs.miri.domain.unavailableSeats
import com.krazylabs.miri.domain.WindowId
import com.krazylabs.miri.domain.HazardDetector
import com.krazylabs.miri.domain.allowsRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vehicle SDK 2.3.2 wrapper. Every read is gated on check*Capabilities access READ or READ_WRITE.
 * initialize() has no connection callback in this version, so a successful return is treated as ready
 * and a later capability failure is stored on [VehicleSnapshot.lastError].
 * Each [start] constructs a new [Vehicle]. Re-initializing a released instance is not documented.
 *
 * getGear() runs only when the gear backend is PLEOS. AAOS GEAR_SELECTION stays the default.
 * Remaining range uses Odometer.getRangeRemainingDistance. setRangeRemainingDistance is not called.
 * Seat occupancy, door lock, cabin light, and reading lights are read. No set/control API is called.
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
    private var identityJob: Job? = null
    private val rereads = CopyOnWriteArrayList<() -> Unit>()
    private val hazardDetector = HazardDetector()

    /** Written on [scope] (main). Volatile so a late callback cannot publish a stale pair. */
    @Volatile private var seatOccupancy: Pair<SignalAccess, Set<SeatId>>? = null

    @Volatile private var seatBelt: Pair<SignalAccess, Set<SeatId>>? = null

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
        identityJob = scope.launch {
            carClient.identity.collect { raw ->
                _snapshot.update { current ->
                    current.copy(
                        identity = CarFacts.from(raw.fuelTypes, raw.fuelDoor, raw.evPort, raw.connector),
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
        subscribeTurnSignal()
        subscribeDoorLock()
        subscribeLights()
        subscribeRange()
        subscribePleosGear()
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        identityJob?.cancel()
        identityJob = null
        rereads.clear()
        seatOccupancy = null
        seatBelt = null
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
        carClient.refreshIdentity()
        rereads.toList().forEach { call ->
            try {
                call()
            } catch (error: Throwable) {
                note(error)
            }
        }
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
                        val read = {
                            door.getCurrentDoorPosition(
                                area,
                                { pos -> if (live(token)) updateDoor(id, pos, readable = true) },
                                { error -> if (live(token)) note(error) },
                            )
                        }
                        read()
                        rereads += read
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
                        val read = {
                            window.getCurrentWindowPosition(
                                area,
                                { position -> if (live(token)) updateWindow(id, position, readable = true) },
                                { error -> if (live(token)) note(error) },
                            )
                        }
                        read()
                        rereads += read
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
                    val read = {
                        fuel.getFuelPercent(
                            { value -> if (live(token)) updateFuel(value) },
                            { error -> if (live(token)) note(error) },
                        )
                    }
                    read()
                    rereads += read
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
                    val read = {
                        battery.getEvBatteryStateOfCharge(
                            { value -> if (live(token)) updateSoc(value) },
                            { error -> if (live(token)) note(error) },
                        )
                    }
                    read()
                    rereads += read
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

    private fun publishSeats(token: Int) {
        val occ = seatOccupancy ?: return
        val bel = seatBelt ?: return
        if (!live(token)) return
        _snapshot.update { snapshot ->
            snapshot.copy(
                seats = seatSupport(occ.second, bel.second, occ.first, bel.first),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun onMain(token: Int, block: () -> Unit) {
        scope.launch {
            if (!live(token)) return@launch
            block()
        }
    }

    private fun subscribeSeats() {
        val token = sessionToken
        val current = vehicle ?: return
        val seat = VehicleDomains.seat(current)
        try {
        seat.checkSeatOccupancyCapabilities(
            { config, areas ->
                onMain(token) {
                val ids = areas.mapNotNull { it.toSeatId() }.toSet()
                seatOccupancy = config.accessType.toSignal() to ids
                publishSeats(token)
                if (!config.accessType.toSignal().allowsRead()) {
                    return@onMain
                }
                val driver = areas.firstOrNull { it.toSeatId() == SeatId.DRIVER } ?: return@onMain
                val listener = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                        if (live(token)) updateOccupied(occupancy)
                    }

                    override fun onFailed(error: Exception) {
                        if (live(token)) note(error)
                    }
                }
                try {
                    seat.registerSeatOccupancy(driver, listener)
                    track(token) { seat.unregisterSeatOccupancy(driver, listener) }
                    val read = {
                        seat.getCurrentSeatOccupancy(
                            driver,
                            { value -> if (live(token)) updateOccupied(value) },
                            { error -> if (live(token)) note(error) },
                        )
                    }
                    read()
                    rereads += read
                } catch (error: Throwable) {
                    note(error)
                }
                }
            },
            { error ->
                onMain(token) {
                    seatOccupancy = SignalAccess.NONE to emptySet()
                    note(error)
                    publishSeats(token)
                }
            },
        )
        seat.checkSeatBeltBuckledCapabilities(
            { config, areas ->
                onMain(token) {
                    seatBelt = config.accessType.toSignal() to areas.mapNotNull { it.toSeatId() }.toSet()
                    publishSeats(token)
                }
            },
            { error ->
                onMain(token) {
                    seatBelt = SignalAccess.NONE to emptySet()
                    note(error)
                    publishSeats(token)
                }
            },
        )
        } catch (error: Throwable) {
            if (live(token)) {
                note(error)
                _snapshot.update { it.copy(seats = unavailableSeats()) }
            }
        }
    }

    private fun subscribeTurnSignal() {
        val token = sessionToken
        val current = vehicle ?: return
        val turn = VehicleDomains.turnSignal(current)
        try {
            turn.checkTurnSignalStateCapabilities(
                { config ->
                    if (!live(token)) return@checkTurnSignalStateCapabilities
                    if (!config.accessType.toSignal().allowsRead()) return@checkTurnSignalStateCapabilities
                    val listener = object : TurnSignalStateListener {
                        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                            if (live(token)) applyTurn(state?.name ?: "OFF")
                        }

                        override fun onFailed(error: Exception) {
                            if (live(token)) note(error)
                        }
                    }
                    try {
                        turn.registerTurnSignalState(listener)
                        track(token) { turn.unregisterTurnSignalState(listener) }
                        turn.getCurrentTurnSignalState(
                            { value -> if (live(token)) applyTurn(value?.name ?: "OFF") },
                            { error -> if (live(token)) note(error) },
                        )
                    } catch (error: Throwable) {
                        note(error)
                    }
                },
                { error -> if (live(token)) note(error) },
            )
        } catch (error: Throwable) {
            note(error)
        }
    }

    private fun subscribeDoorLock() {
        val token = sessionToken
        val current = vehicle ?: return
        val door = VehicleDomains.door(current)
        try {
            door.checkDoorLockCapabilities(
                { config, areas ->
                    if (!live(token) || !config.accessType.toSignal().allowsRead()) {
                        return@checkDoorLockCapabilities
                    }
                    val area = areas.firstOrNull { it.toDoorId() == DoorId.ROW_1_LEFT }
                        ?: return@checkDoorLockCapabilities
                    val listener = object : DoorLockStateListener {
                        override fun onDoorLockStateUpdated(state: Boolean?) {
                            if (live(token)) updateLock(state)
                        }

                        override fun onFailed(error: Exception) {
                            if (live(token)) note(error)
                        }
                    }
                    try {
                        door.registerDoorLockState(area, listener)
                        track(token) { door.unregisterDoorLockState(area, listener) }
                        val read = {
                            door.getDoorLockState(
                                area,
                                { value -> if (live(token)) updateLock(value) },
                                { error -> if (live(token)) note(error) },
                            )
                        }
                        read()
                        rereads += read
                    } catch (error: Throwable) {
                        note(error)
                    }
                },
                { error -> if (live(token)) note(error) },
            )
        } catch (error: Throwable) {
            note(error)
        }
    }

    private fun subscribeLights() {
        val token = sessionToken
        val current = vehicle ?: return
        val light = VehicleDomains.light(current)
        try {
            light.checkCabinLightStateCapabilities(
                { config, _ ->
                    if (!live(token) || !config.accessType.toSignal().allowsRead()) {
                        return@checkCabinLightStateCapabilities
                    }
                    val listener = object : LightStateListener {
                        override fun onLightStateUpdated(state: LightState?) {
                            if (live(token)) updateCabin(state)
                        }

                        override fun onFailed(error: Exception) {
                            if (live(token)) note(error)
                        }
                    }
                    try {
                        light.registerCabinLightState(listener)
                        track(token) { light.unregisterCabinLightState(listener) }
                        val read = {
                            light.getCurrentCabinLightState(
                                { value -> if (live(token)) updateCabin(value) },
                                { error -> if (live(token)) note(error) },
                            )
                        }
                        read()
                        rereads += read
                    } catch (error: Throwable) {
                        note(error)
                    }
                },
                { error -> if (live(token)) note(error) },
            )
            light.checkReadingLightStateCapabilities(
                { config, areaMap ->
                    if (!live(token) || !config.accessType.toSignal().allowsRead()) {
                        return@checkReadingLightStateCapabilities
                    }
                    areaMap.keys.forEach { area ->
                        val seat = area.toSeatId() ?: return@forEach
                        val listener = object : LightStateListener {
                            override fun onLightStateUpdated(state: LightState?) {
                                if (live(token)) updateReading(seat, state)
                            }

                            override fun onFailed(error: Exception) {
                                if (live(token)) note(error)
                            }
                        }
                        try {
                            light.registerReadingLightState(area, listener)
                            track(token) { light.unregisterReadingLightState(area, listener) }
                            val read = {
                                light.getCurrentReadingLightState(
                                    area,
                                    { value -> if (live(token)) updateReading(seat, value) },
                                    { error -> if (live(token)) note(error) },
                                )
                            }
                            read()
                            rereads += read
                        } catch (error: Throwable) {
                            note(error)
                        }
                    }
                },
                { error -> if (live(token)) note(error) },
            )
        } catch (error: Throwable) {
            note(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribeRange() {
        val token = sessionToken
        val current = vehicle ?: return
        val odometer = VehicleDomains.odometer(current)
        try {
            val listener = object : RangeRemainingListener {
                override fun onRangeRemainingChanged(value: Float?) {
                    if (live(token)) applyRange(value)
                }

                override fun onFailed(error: Exception) {
                    if (live(token)) note(error)
                }
            }
            odometer.registerRangeRemainingListener(listener)
            track(token) { odometer.unregisterRangeRemainingListener(listener) }
            val read = {
                odometer.getRangeRemainingDistance(
                    { value -> if (live(token)) applyRange(value) },
                    { error -> if (live(token)) note(error) },
                )
            }
            read()
            rereads += read
        } catch (error: Throwable) {
            note(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribePleosGear() {
        if (BuildConfig.GEAR_BACKEND != "PLEOS") return
        val token = sessionToken
        val current = vehicle ?: return
        val gear = VehicleDomains.gear(current)
        try {
            val listener = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    val position = gear?.toPosition() ?: return
                    if (live(token)) carClient.overlayGear(position)
                }

                override fun onFailed(error: Exception) {
                    if (live(token)) note(error)
                }
            }
            gear.registerGearSelection(listener)
            track(token) { gear.unregisterGearSelection(listener) }
            val read = {
                gear.getCurrentGearSelectionState(
                    { value ->
                        val position = value?.toPosition() ?: return@getCurrentGearSelectionState
                        if (live(token)) carClient.overlayGear(position)
                    },
                    { error -> if (live(token)) note(error) },
                )
            }
            read()
            rereads += read
        } catch (error: Throwable) {
            note(error)
        }
    }

    private fun updateLock(locked: Boolean?) {
        _snapshot.update { it.copy(doorLocked = locked, updatedAtEpochMs = System.currentTimeMillis()) }
    }

    private fun updateOccupied(occupied: Boolean?) {
        _snapshot.update { it.copy(driverOccupied = occupied, updatedAtEpochMs = System.currentTimeMillis()) }
    }

    private fun updateCabin(state: LightState?) {
        val on = lightOn(state) ?: return
        _snapshot.update { it.copy(cabinLightOn = on, updatedAtEpochMs = System.currentTimeMillis()) }
    }

    private fun updateReading(seat: SeatId, state: LightState?) {
        val on = lightOn(state) ?: return
        _snapshot.update { current ->
            current.copy(
                readingLights = current.readingLights + (seat to on),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun applyRange(value: Float?) {
        _snapshot.update {
            it.copy(
                rangeMeters = value,
                rangeSource = if (value == null) RangeSource.UNAVAILABLE else RangeSource.PLEOS_ODOMETER,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun applyTurn(valueName: String) {
        val lamps = hazardDetector.observe(valueName, System.currentTimeMillis())
        _snapshot.update { it.copy(turnLamps = lamps, updatedAtEpochMs = System.currentTimeMillis()) }
    }

    private fun live(token: Int): Boolean = listeners.isCurrent(token)

    private fun track(token: Int, cleanup: () -> Unit) {
        listeners.track(token, cleanup)
    }

    private fun note(error: Throwable) {
        _snapshot.update { it.copy(lastError = error.message ?: error.javaClass.simpleName) }
    }
}

private fun lightOn(state: LightState?): Boolean? = when (state) {
    LightState.ON -> true
    LightState.OFF, LightState.DAYTIME_RUNNING -> false
    null -> null
}

private fun VehicleGear.toPosition(): GearPosition = when (this) {
    VehicleGear.GEAR_PARK -> GearPosition.PARK
    VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
    VehicleGear.GEAR_DRIVE -> GearPosition.DRIVE
    VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
    else -> GearPosition.OTHER
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
