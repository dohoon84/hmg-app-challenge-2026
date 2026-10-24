package com.krazylabs.miri.domain

/**
 * AAOS INFO_* integers (API 34 fuel type and [android.car.PortLocationType]).
 * Empty or unknown values stay empty.
 * Connector integers are VHAL / [android.car.hardware.property.EvChargingConnectorType]
 * because the card reads them from CarPropertyManager. See [ConnectorCodes].
 */
enum class FuelKind {
    GASOLINE,
    DIESEL,
    LPG,
    HYBRID,
    ELECTRIC,
}

enum class PortSide {
    FRONT_LEFT,
    FRONT_RIGHT,
    REAR_LEFT,
    REAR_RIGHT,
    FRONT,
    REAR,
}

data class CarIdentity(
    val fuels: List<FuelKind> = emptyList(),
    val fuelDoor: PortSide? = null,
    val evPort: PortSide? = null,
    val connectorDcCombo: Boolean = false,
) {
    val evOnly: Boolean get() = fuels.size == 1 && fuels[0] == FuelKind.ELECTRIC
    val fuelKnown: Boolean get() = fuels.isNotEmpty()
    val portForCards: PortSide? get() = if (evOnly) evPort else fuelDoor
}

object CarFacts {
    const val FUEL_UNLEADED = 1
    const val FUEL_DIESEL_1 = 3
    const val FUEL_DIESEL_2 = 4
    const val FUEL_LPG = 7
    const val FUEL_ELECTRIC = 10
    const val PORT_FRONT_LEFT = 1
    const val PORT_FRONT_RIGHT = 2
    const val PORT_REAR_RIGHT = 3
    const val PORT_REAR_LEFT = 4
    const val PORT_FRONT = 5
    const val PORT_REAR = 6

    /**
     * [connector] is the raw CarPropertyManager array ([ConnectorCodes.DC_COMBO] is {5, 6}).
     * CCS1 and CCS2 set [CarIdentity.connectorDcCombo]. CHAdeMO (4), AC (1–3), Tesla (7), and unknown do not.
     */
    fun from(
        fuelTypes: IntArray,
        fuelDoor: Int?,
        evPort: Int?,
        connector: IntArray,
    ): CarIdentity = CarIdentity(
        fuels = fuelTypes.toList().mapNotNull(::fuel).distinct(),
        fuelDoor = fuelDoor?.let(::port),
        evPort = evPort?.let(::port),
        connectorDcCombo = ConnectorCodes.dcCombo(connector),
    )

    fun fuel(code: Int): FuelKind? = when (code) {
        FUEL_UNLEADED -> FuelKind.GASOLINE
        FUEL_DIESEL_1, FUEL_DIESEL_2 -> FuelKind.DIESEL
        FUEL_LPG -> FuelKind.LPG
        FUEL_ELECTRIC -> FuelKind.ELECTRIC
        else -> null
    }

    fun port(code: Int): PortSide? = when (code) {
        PORT_FRONT_LEFT -> PortSide.FRONT_LEFT
        PORT_FRONT_RIGHT -> PortSide.FRONT_RIGHT
        PORT_REAR_RIGHT -> PortSide.REAR_RIGHT
        PORT_REAR_LEFT -> PortSide.REAR_LEFT
        PORT_FRONT -> PortSide.FRONT
        PORT_REAR -> PortSide.REAR
        else -> null
    }
}

object GearBackend {
    const val AAOS = "AAOS"
    const val PLEOS = "PLEOS"

    /** Null, blank, and any value other than PLEOS stay on the AAOS gear property. */
    fun normalize(raw: String?): String {
        if (raw != null && raw.equals(PLEOS, ignoreCase = true)) return PLEOS
        return AAOS
    }
}
