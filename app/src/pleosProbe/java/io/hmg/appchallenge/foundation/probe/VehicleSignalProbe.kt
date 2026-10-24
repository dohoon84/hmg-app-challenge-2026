package io.hmg.appchallenge.foundation.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.CarInfo
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.Fuel
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.lang.reflect.Modifier

/**
 * Read-only gear and energy probe. AAOS CarPropertyManager plus the Pleos getters
 * that exist on Vehicle SDK 2.3.2. Set and control methods are not called.
 * Car property reads are attempted even when permission is missing so the failure is logged.
 */
@SuppressLint("NewApi", "MissingPermission")
internal class VehicleSignalProbe(
    context: Context,
    private val log: ProbeLog,
    private val background: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var stopped = false
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var gearCallbackRegistered = false
    private var vehicle: Vehicle? = null
    private var pleosGear: Gear? = null
    private var pleosFuel: Fuel? = null
    private var pleosBattery: EvBattery? = null
    private var pleosInfo: CarInfo? = null
    private var pleosGearRegistered = false
    private var pleosFuelRegistered = false
    private var pleosSocRegistered = false
    private var pleosLevelRegistered = false

    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.GEAR_SELECTION) return
            val raw = value.value as? Int
            gear(
                "change",
                mapOf(
                    "source" to "CarPropertyManager.GEAR_SELECTION",
                    "propertyId" to value.propertyId,
                    "areaId" to value.areaId,
                    "status" to value.status,
                    "raw" to raw,
                    "name" to gearName(raw),
                ),
            )
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            gear(
                "error",
                mapOf(
                    "source" to "CarPropertyManager.GEAR_SELECTION",
                    "propertyId" to propId,
                    "areaId" to areaId,
                    "result" to "error",
                ),
            )
        }
    }

    private val pleosGearListener = object : GearSelectionListener {
        override fun onSelected(selected: VehicleGear?) {
            gear(
                "change",
                mapOf(
                    "source" to "Vehicle.getGear",
                    "name" to (selected?.name ?: "null"),
                    "rawId" to selected?.let { gearRawId(it) },
                ),
            )
        }

        override fun onFailed(error: Exception) {
            gear("pleos_gear_failed", probeFailure(error) + mapOf("source" to "Vehicle.getGear"))
        }
    }

    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) {
            info("pleos_fuel_percent", mapOf("source" to "Fuel.registerFuelPercent", "value" to value))
        }

        override fun onFailed(error: Exception) {
            info("pleos_fuel_percent", probeFailure(error) + mapOf("source" to "Fuel.registerFuelPercent"))
        }
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            info(
                "pleos_ev_soc",
                mapOf("source" to "EvBattery.registerEvBatteryStateOfCharge", "value" to value),
            )
        }

        override fun onFailed(error: Exception) {
            info("pleos_ev_soc", probeFailure(error))
        }
    }

    private val levelListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(value: Float?) {
            info("pleos_ev_battery_level", mapOf("source" to "EvBattery.registerEvBatteryLevel", "value" to value))
        }

        override fun onFailed(error: Exception) {
            info("pleos_ev_battery_level", probeFailure(error))
        }
    }

    fun start() {
        stopped = false
        logPleosApiShape()
        connectPleos()
        connectCar()
    }

    fun onProcessBackground(hold: Boolean) {
        if (hold) {
            gear("process_background", mapOf("listeners" to "kept"))
            return
        }
        unregisterListeners()
        gear("process_background", mapOf("listeners" to "removed"))
    }

    fun onProcessForeground() {
        registerListeners()
        readAll("foreground")
    }

    fun readAgain() {
        readAll("button")
    }

    fun release() {
        stopped = true
        unregisterListeners()
        val currentCar = car
        car = null
        manager = null
        try {
            currentCar?.disconnect()
        } catch (error: Throwable) {
            info("car_disconnect", probeFailure(error))
        }
        val currentVehicle = vehicle
        vehicle = null
        pleosGear = null
        pleosFuel = null
        pleosBattery = null
        pleosInfo = null
        try {
            currentVehicle?.release()
            info("vehicle_release", mapOf("result" to "called"))
        } catch (error: Throwable) {
            info("vehicle_release", probeFailure(error))
        }
    }

    private fun connectPleos() {
        val created = Vehicle(appContext)
        vehicle = created
        try {
            created.initialize()
            info("vehicle_initialize", mapOf("result" to "returned"))
        } catch (error: Throwable) {
            info("vehicle_initialize", probeFailure(error))
            return
        }
        pleosGear = ProbeVehicleDomains.gear(created)
        pleosFuel = ProbeVehicleDomains.fuel(created)
        pleosBattery = ProbeVehicleDomains.evBattery(created)
        pleosInfo = ProbeVehicleDomains.carInfo(created)
        registerPleosListeners()
        readPleos("startup")
    }

    private fun connectCar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            info("car_connect", mapOf("result" to "unavailable", "sdk" to Build.VERSION.SDK_INT))
            return
        }
        connectCarR()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun connectCarR() {
        try {
            car = Car.createCar(
                appContext,
                main,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { created, ready ->
                    if (stopped) {
                        created.disconnect()
                        return@CarServiceLifecycleListener
                    }
                    car = created
                    if (!ready) {
                        info("car_connect", mapOf("result" to "not_ready"))
                        manager = null
                        return@CarServiceLifecycleListener
                    }
                    val property = created.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    manager = property
                    if (property == null) {
                        info("car_connect", mapOf("result" to "no_property_service"))
                        return@CarServiceLifecycleListener
                    }
                    info("car_connect", mapOf("result" to "ready"))
                    registerGearCallback()
                    readAaos("car_ready")
                },
            )
        } catch (error: Throwable) {
            info("car_connect", probeFailure(error))
        }
    }

    private fun readAll(reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readAaos(reason)
        } else {
            info("aaos_read", mapOf("result" to "unavailable", "sdk" to Build.VERSION.SDK_INT, "reason" to reason))
        }
        readPleos(reason)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readAaos(reason: String) {
        val property = manager
        if (property == null) {
            info("aaos_read", mapOf("result" to "car_not_ready", "reason" to reason))
            return
        }
        specs().forEach { spec -> readProperty(property, spec, reason) }
        readOptionalInt(property, "CURRENT_GEAR", reason)
        readGearSnapshot(property, reason)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readProperty(property: CarPropertyManager, spec: PropSpec, reason: String) {
        val granted = ContextCompat.checkSelfPermission(appContext, spec.permission) ==
            PackageManager.PERMISSION_GRANTED
        val base = mapOf(
            "property" to spec.name,
            "propertyId" to spec.id,
            "permission" to spec.permission,
            "granted" to granted,
            "reason" to reason,
            "unitNote" to spec.unitNote,
        )
        if (!granted) {
            info(
                "aaos_property",
                base + mapOf("result" to "permission", "detail" to "not granted before read"),
            )
        }
        try {
            val config = property.getCarPropertyConfig(spec.id)
            if (config == null) {
                info("aaos_property", base + mapOf("result" to "unavailable", "detail" to "no CarPropertyConfig"))
                return
            }
            val areas = config.areaIds.toList().ifEmpty { listOf(0) }
            val available = areas.filter { area ->
                try {
                    property.isPropertyAvailable(spec.id, area)
                } catch (error: Throwable) {
                    info(
                        "aaos_property",
                        base + probeFailure(error) + mapOf("areaId" to area, "detail" to "isPropertyAvailable"),
                    )
                    false
                }
            }
            if (available.isEmpty()) {
                info(
                    "aaos_property",
                    base + mapOf(
                        "result" to "unavailable",
                        "access" to config.access,
                        "areas" to areas,
                    ),
                )
                return
            }
            available.forEach { area ->
                val raw: Any = when (spec.kind) {
                    Kind.INT -> property.getIntProperty(spec.id, area)
                    Kind.FLOAT -> property.getFloatProperty(spec.id, area)
                    Kind.INT_ARRAY -> property.getIntArrayProperty(spec.id, area).toList()
                }
                info(
                    "aaos_property",
                    base + mapOf(
                        "result" to "ok",
                        "areaId" to area,
                        "access" to config.access,
                        "raw" to raw,
                        "named" to nameValue(spec, raw),
                    ),
                )
            }
        } catch (error: Throwable) {
            info("aaos_property", base + probeFailure(error))
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readGearSnapshot(property: CarPropertyManager, reason: String) {
        val id = VehiclePropertyIds.GEAR_SELECTION
        try {
            val available = property.isPropertyAvailable(id, 0)
            if (!available) {
                gear("read", mapOf("source" to "CarPropertyManager.GEAR_SELECTION", "result" to "unavailable", "reason" to reason))
                return
            }
            val raw = property.getIntProperty(id, 0)
            gear(
                "read",
                mapOf(
                    "source" to "CarPropertyManager.GEAR_SELECTION",
                    "result" to "ok",
                    "reason" to reason,
                    "raw" to raw,
                    "name" to gearName(raw),
                    "propertyId" to id,
                ),
            )
        } catch (error: Throwable) {
            gear("read", probeFailure(error) + mapOf("source" to "CarPropertyManager.GEAR_SELECTION", "reason" to reason))
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readOptionalInt(property: CarPropertyManager, fieldName: String, reason: String) {
        val id = VehiclePropertyIds::class.java.fields.firstOrNull { it.name == fieldName }?.getInt(null)
        if (id == null) {
            info("aaos_property", mapOf("property" to fieldName, "result" to "absent_in_sdk", "reason" to reason))
            return
        }
        readProperty(
            property,
            PropSpec(fieldName, id, Car.PERMISSION_POWERTRAIN, Kind.INT, "optional extra read"),
            reason,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registerGearCallback() {
        val property = manager ?: return
        if (gearCallbackRegistered) return
        try {
            val registered = property.registerCallback(
                gearCallback,
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
            )
            gearCallbackRegistered = registered
            gear(
                "register",
                mapOf(
                    "source" to "CarPropertyManager.GEAR_SELECTION",
                    "result" to if (registered) "registered" else "rejected",
                ),
            )
        } catch (error: Throwable) {
            gear("register", probeFailure(error) + mapOf("source" to "CarPropertyManager.GEAR_SELECTION"))
        }
    }

    private fun registerListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            registerGearCallback()
        }
        registerPleosListeners()
    }

    private fun registerPleosListeners() {
        val gearApi = pleosGear
        if (gearApi != null && !pleosGearRegistered) {
            try {
                gearApi.registerGearSelection(pleosGearListener)
                pleosGearRegistered = true
                gear("register", mapOf("source" to "Vehicle.getGear", "result" to "registered"))
            } catch (error: Throwable) {
                gear("register", probeFailure(error) + mapOf("source" to "Vehicle.getGear"))
            }
        }
        val fuel = pleosFuel
        if (fuel != null && !pleosFuelRegistered) {
            try {
                fuel.registerFuelPercent(fuelListener)
                pleosFuelRegistered = true
            } catch (error: Throwable) {
                info("pleos_fuel_percent", probeFailure(error) + mapOf("detail" to "register"))
            }
        }
        val battery = pleosBattery
        if (battery != null && !pleosSocRegistered) {
            try {
                battery.registerEvBatteryStateOfCharge(socListener)
                pleosSocRegistered = true
            } catch (error: Throwable) {
                info("pleos_ev_soc", probeFailure(error) + mapOf("detail" to "register"))
            }
        }
        if (battery != null && !pleosLevelRegistered) {
            try {
                battery.registerEvBatteryLevel(levelListener)
                pleosLevelRegistered = true
            } catch (error: Throwable) {
                info("pleos_ev_battery_level", probeFailure(error) + mapOf("detail" to "register"))
            }
        }
    }

    private fun unregisterListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val property = manager
            if (property != null && gearCallbackRegistered) {
                try {
                    property.unregisterCallback(gearCallback)
                } catch (error: Throwable) {
                    gear("unregister", probeFailure(error))
                }
            }
        }
        gearCallbackRegistered = false
        val gearApi = pleosGear
        if (gearApi != null && pleosGearRegistered) {
            try {
                gearApi.unregisterGearSelection(pleosGearListener)
            } catch (error: Throwable) {
                gear("unregister", probeFailure(error) + mapOf("source" to "Vehicle.getGear"))
            }
        }
        pleosGearRegistered = false
        val fuel = pleosFuel
        if (fuel != null && pleosFuelRegistered) {
            try {
                fuel.unregisterFuelPercent(fuelListener)
            } catch (error: Throwable) {
                info("pleos_fuel_percent", probeFailure(error))
            }
        }
        pleosFuelRegistered = false
        val battery = pleosBattery
        if (battery != null && pleosSocRegistered) {
            try {
                battery.unregisterEvBatteryStateOfCharge(socListener)
            } catch (error: Throwable) {
                info("pleos_ev_soc", probeFailure(error))
            }
        }
        pleosSocRegistered = false
        if (battery != null && pleosLevelRegistered) {
            try {
                battery.unregisterEvBatteryLevel(levelListener)
            } catch (error: Throwable) {
                info("pleos_ev_battery_level", probeFailure(error))
            }
        }
        pleosLevelRegistered = false
    }

    private fun readPleos(reason: String) {
        val gearApi = pleosGear
        if (gearApi == null) {
            gear("read", mapOf("source" to "Vehicle.getGear", "result" to "not_ready", "reason" to reason))
        } else {
            try {
                gearApi.getCurrentGearSelectionState(
                    { selected ->
                        gear(
                            "read",
                            mapOf(
                                "source" to "Vehicle.getGear",
                                "result" to "ok",
                                "reason" to reason,
                                "name" to (selected?.name ?: "null"),
                                "rawId" to selected?.let { gearRawId(it) },
                            ),
                        )
                    },
                    { error ->
                        gear("read", probeFailure(error) + mapOf("source" to "Vehicle.getGear", "reason" to reason))
                    },
                )
            } catch (error: Throwable) {
                gear("read", probeFailure(error) + mapOf("source" to "Vehicle.getGear", "reason" to reason))
            }
        }
        val fuel = pleosFuel
        if (fuel == null) {
            info("pleos_fuel_percent", mapOf("result" to "not_ready", "reason" to reason))
        } else {
            try {
                fuel.getFuelPercent(
                    { value -> info("pleos_fuel_percent", mapOf("result" to "ok", "reason" to reason, "value" to value, "source" to "Fuel.getFuelPercent")) },
                    { error -> info("pleos_fuel_percent", probeFailure(error) + mapOf("reason" to reason)) },
                )
            } catch (error: Throwable) {
                info("pleos_fuel_percent", probeFailure(error) + mapOf("reason" to reason))
            }
        }
        val battery = pleosBattery
        if (battery == null) {
            info("pleos_ev", mapOf("result" to "not_ready", "reason" to reason))
        } else {
            try {
                battery.getEvBatteryStateOfCharge(
                    { value -> info("pleos_ev_soc", mapOf("result" to "ok", "reason" to reason, "value" to value, "source" to "EvBattery.getEvBatteryStateOfCharge")) },
                    { error -> info("pleos_ev_soc", probeFailure(error) + mapOf("reason" to reason)) },
                )
                battery.getEvBatteryLevel(
                    { value -> info("pleos_ev_battery_level", mapOf("result" to "ok", "reason" to reason, "value" to value, "source" to "EvBattery.getEvBatteryLevel", "unitNote" to "SDK Float, unit not named on the method")) },
                    { error -> info("pleos_ev_battery_level", probeFailure(error) + mapOf("reason" to reason)) },
                )
                battery.getEvBatteryCapacity(
                    { value -> info("pleos_ev_battery_capacity", mapOf("result" to "ok", "reason" to reason, "value" to value, "source" to "EvBattery.getEvBatteryCapacity", "unitNote" to "SDK Float, unit not named on the method")) },
                    { error -> info("pleos_ev_battery_capacity", probeFailure(error) + mapOf("reason" to reason)) },
                )
            } catch (error: Throwable) {
                info("pleos_ev", probeFailure(error) + mapOf("reason" to reason))
            }
        }
        val infoApi = pleosInfo
        if (infoApi == null) {
            info("pleos_car_info", mapOf("result" to "not_ready", "reason" to reason))
            return
        }
        // vehicle-data CapabilityConfig is a runtime dependency, not on the compile classpath.
        readCapability(infoApi, reason)
    }

    private fun readCapability(infoApi: CarInfo, reason: String) {
        try {
            val method = infoApi.javaClass.methods.first { candidate ->
                candidate.name == "getCapabilityInfo" && candidate.parameterCount == 2
            }
            val onSuccess = object : kotlin.jvm.functions.Function1<Any?, Unit> {
                override fun invoke(raw: Any?) {
                    val configs = raw as? Map<*, *> ?: return
                    val interesting = configs.entries.filter { entry ->
                        val name = entry.key.toString().uppercase()
                        name.contains("FUEL") || name.contains("EV") || name.contains("INFO") ||
                            name.contains("RANGE") || name.contains("GEAR") || name.contains("PORT") ||
                            name.contains("CONNECTOR")
                    }
                    info(
                        "pleos_capability",
                        mapOf(
                            "reason" to reason,
                            "matched" to interesting.size,
                            "total" to configs.size,
                            "keys" to interesting.map { it.key.toString() },
                            "values" to interesting.associate { it.key.toString() to (it.value?.toString()?.take(300) ?: "null") },
                        ),
                    )
                }
            }
            val onError = object : kotlin.jvm.functions.Function1<Any?, Unit> {
                override fun invoke(raw: Any?) {
                    val error = raw as? Throwable
                    if (error != null) {
                        info("pleos_capability", probeFailure(error) + mapOf("reason" to reason))
                    }
                }
            }
            method.invoke(infoApi, onSuccess, onError)
        } catch (error: Throwable) {
            info("pleos_capability", probeFailure(error) + mapOf("reason" to reason, "detail" to "reflect"))
        }
    }

    private fun logPleosApiShape() {
        info(
            "sdk_shape",
            mapOf(
                "CarInfoMethods" to methodNames(CarInfo::class.java),
                "FuelMethods" to methodNames(Fuel::class.java),
                "EvBatteryReadMethods" to methodNames(EvBattery::class.java).filter { !it.startsWith("set") },
                "skippedSetMethods" to methodNames(EvBattery::class.java).filter { it.startsWith("set") },
                "GearMethods" to methodNames(Gear::class.java),
                "VehicleFuelTypeValue" to staticInts("ai.pleos.playground.vehicle.constant.VehicleFuelTypeValue"),
                "PortLocationTypeValue" to staticInts("ai.pleos.playground.vehicle.constant.PortLocationTypeValue"),
                "VehicleEvChargingConnectorType" to staticInts("ai.pleos.playground.vehicle.constant.VehicleEvChargingConnectorType"),
                "VehicleGear" to reflectEnum(VehicleGear::class.java),
                "android.car.FuelType" to staticInts("android.car.FuelType"),
                "android.car.PortLocationType" to staticInts("android.car.PortLocationType"),
                "android.car.EvConnectorType" to staticInts("android.car.EvConnectorType"),
                "android.car.VehicleGear" to staticInts("android.car.VehicleGear"),
                "limitation" to
                    "CarInfo exposes VIN, CRN, and getCapabilityInfo only. " +
                    "No Pleos getter for fuel type, fuel door location, EV connector type, or EV port location. " +
                    "Fuel has getFuelPercent and no fuel level or capacity. " +
                    "EvBattery has level, capacity, and state of charge, and no range getter. " +
                    "Vehicle.getGear, getEvBatteryLevel, and getEvBatteryCapacity are deprecated in 2.3.2 " +
                    "and tell the caller to use AAOS GEAR_SELECTION, EV_BATTERY_LEVEL, and INFO_EV_BATTERY_CAPACITY. " +
                    "The probe still calls the deprecated getters so both sides can be compared. " +
                    "The constant holders above are value tables, not read APIs. " +
                    "Set methods are listed and not called.",
            ),
        )
        val propertyNames = runCatching {
            Class.forName("ai.pleos.playground.vehicle.constant.PleosPropertyIds").enumConstants
                ?.map { (it as Enum<*>).name }
                ?.filter { name ->
                    name.contains("FUEL") || name.contains("EV_") || name.contains("RANGE") ||
                        name.contains("GEAR") || name.contains("PORT") || name.contains("CONNECTOR")
                }
        }.getOrElse { listOf(it.message ?: it.javaClass.simpleName) }
        info("pleos_property_id_names", mapOf("names" to propertyNames))
    }

    private fun specs(): List<PropSpec> {
        val specs = mutableListOf(
            PropSpec("INFO_FUEL_TYPE", VehiclePropertyIds.INFO_FUEL_TYPE, Car.PERMISSION_CAR_INFO, Kind.INT_ARRAY, "FuelType int array"),
            PropSpec("INFO_FUEL_DOOR_LOCATION", VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION, Car.PERMISSION_CAR_INFO, Kind.INT, "PortLocationType"),
            PropSpec("INFO_EV_CONNECTOR_TYPE", VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE, Car.PERMISSION_CAR_INFO, Kind.INT_ARRAY, "EvConnectorType int array"),
            PropSpec("INFO_EV_PORT_LOCATION", VehiclePropertyIds.INFO_EV_PORT_LOCATION, Car.PERMISSION_CAR_INFO, Kind.INT, "PortLocationType"),
            PropSpec("FUEL_LEVEL", VehiclePropertyIds.FUEL_LEVEL, Car.PERMISSION_ENERGY, Kind.FLOAT, "AAOS milliliters"),
            PropSpec("INFO_FUEL_CAPACITY", VehiclePropertyIds.INFO_FUEL_CAPACITY, Car.PERMISSION_CAR_INFO, Kind.FLOAT, "AAOS milliliters"),
            PropSpec("EV_BATTERY_LEVEL", VehiclePropertyIds.EV_BATTERY_LEVEL, Car.PERMISSION_ENERGY, Kind.FLOAT, "AAOS watt-hours"),
            PropSpec("INFO_EV_BATTERY_CAPACITY", VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, Car.PERMISSION_CAR_INFO, Kind.FLOAT, "AAOS watt-hours"),
            PropSpec("RANGE_REMAINING", VehiclePropertyIds.RANGE_REMAINING, Car.PERMISSION_ENERGY, Kind.FLOAT, "AAOS meters"),
        )
        val multi = VehiclePropertyIds::class.java.fields.firstOrNull { it.name == "INFO_MULTI_EV_PORT_LOCATIONS" }
        if (multi != null) {
            specs += PropSpec(
                "INFO_MULTI_EV_PORT_LOCATIONS",
                multi.getInt(null),
                Car.PERMISSION_CAR_INFO,
                Kind.INT_ARRAY,
                "PortLocationType array if the platform exposes it",
            )
        }
        return specs
    }

    private fun nameValue(spec: PropSpec, raw: Any): Any = when (spec.name) {
        "INFO_FUEL_TYPE" -> (raw as? List<*>)?.map { constName("android.car.FuelType", (it as Number).toInt()) } ?: raw
        "INFO_FUEL_DOOR_LOCATION", "INFO_EV_PORT_LOCATION" -> constName("android.car.PortLocationType", raw as Int)
        "INFO_EV_CONNECTOR_TYPE" -> (raw as? List<*>)?.map { constName("android.car.EvConnectorType", (it as Number).toInt()) } ?: raw
        "INFO_MULTI_EV_PORT_LOCATIONS" -> (raw as? List<*>)?.map { constName("android.car.PortLocationType", (it as Number).toInt()) } ?: raw
        "CURRENT_GEAR" -> gearName(raw as? Int)
        else -> raw
    }

    private fun gearName(raw: Int?): String {
        if (raw == null) return "null"
        return constName("android.car.VehicleGear", raw)
    }

    private fun constName(className: String, value: Int): String = try {
        val match = Class.forName(className).fields.firstOrNull { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == Int::class.javaPrimitiveType &&
                field.getInt(null) == value
        }
        match?.name ?: "UNMAPPED($value)"
    } catch (error: Throwable) {
        "UNMAPPED($value)"
    }

    private fun gearRawId(selected: VehicleGear): Any? =
        selected.javaClass.methods.firstOrNull { method ->
            method.name.startsWith("getId") && method.parameterCount == 0
        }?.let { method -> runCatching { method.invoke(selected) }.getOrNull() }

    private fun methodNames(cls: Class<*>): List<String> =
        cls.methods.map { it.name }.filter { !it.contains("$") && it != "equals" && it != "hashCode" && it != "toString" }
            .distinct()
            .sorted()

    private fun staticInts(className: String): Any = runCatching {
        Class.forName(className).fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .map { mapOf("name" to it.name, "value" to it.getInt(null)) }
    }.getOrElse { it.message ?: it.javaClass.name }

    private fun reflectEnum(cls: Class<*>): List<Map<String, Any?>> =
        (cls.enumConstants ?: emptyArray()).map { item ->
            val entry = item as Enum<*>
            mapOf("name" to entry.name, "ordinal" to entry.ordinal)
        }

    private fun gear(event: String, fields: Map<String, Any?>) {
        log.write(TAG_GEAR, event, background(), fields)
    }

    private fun info(event: String, fields: Map<String, Any?>) {
        log.write(TAG_INFO, event, background(), fields)
    }

    private enum class Kind { INT, FLOAT, INT_ARRAY }

    private data class PropSpec(
        val name: String,
        val id: Int,
        val permission: String,
        val kind: Kind,
        val unitNote: String,
    )

    private companion object {
        const val TAG_GEAR = "PROBE_GEAR"
        const val TAG_INFO = "PROBE_INFO"
    }
}
