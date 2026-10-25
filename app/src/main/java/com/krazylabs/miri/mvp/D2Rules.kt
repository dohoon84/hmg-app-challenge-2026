package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.PortSide
import java.util.Locale

data class D2Face(
    val title: String,
    val lines: List<String>,
    val chips: List<String>,
    val clips: List<String>,
    val arrivalClips: List<String>,
    val port: PortSide?,
    val routeKey: String = "",
    /** Road id that [routeKey] was promoted from, when the destination arrives later. */
    val routeId: String = "",
    /**
     * Badge chosen from the fuel kind. Null is an EV stop.
     * The screen branches on this value, not on the title or the body.
     */
    val fuelBadge: String? = null,
)

/**
 * One key for the D2 card and for D2-b.
 * A destination is its name plus lat/lon rounded to 4 decimals, so a cleared
 * routeId or a reroute does not change the key. [routeId] is used only when
 * there is no destination. An empty result is not recorded in the route set.
 *
 * A destination that arrives late in the same park session changes this key
 * from the route id to the destination key. D2 must not show again for that
 * stop. [com.krazylabs.miri.mvp.CardSession] keeps a park-session latch for it.
 */
object RouteKey {
    fun of(destination: DestinationSnapshot?, routeId: String?): String {
        if (destination == null) return routeId?.trim().orEmpty()
        val name = destination.name.trim()
        val lat = "%.4f".format(Locale.US, destination.latitude)
        val lon = "%.4f".format(Locale.US, destination.longitude)
        return "$name@$lat,$lon"
    }
}

object CarSpec {
    /** No reviewed spec pack is shipped. CAR_INFO is the only source, so it agrees with itself. */
    fun agrees(identity: CarIdentity): Boolean = true
}

/**
 * D2 common conditions. An empty fuel type or an empty port drops the whole card.
 * An unknown connector drops only the connector sentence and chip.
 * A missing port clip, including a center port or a multi-fuel car, keeps the card
 * and drops every D2-a and D2-b sentence together.
 */
object D2Rules {
    fun offer(
        identity: CarIdentity,
        role: DestinationRole,
        specAgrees: Boolean = true,
        routeKey: String = "",
    ): D2Face? {
        if (!specAgrees || !identity.fuelKnown) return null
        val ev = identity.evOnly
        val port = identity.portForCards ?: return null
        if (ev && role != DestinationRole.CHARGING) return null
        if (!ev && role != DestinationRole.GAS) return null
        val chips = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val fuelBadge = if (ev) {
            null
        } else if (identity.fuels.size == 1) {
            fuelChip(identity.fuels[0])
        } else {
            MULTI_FUEL_CHIP
        }
        val portClip = if (ev) chargePortClip(port) else fuelSideClip(port)
        val fuelKey = if (ev) {
            "vo_fuel_electric"
        } else if (identity.fuels.size == 1) {
            fuelClip(identity.fuels[0])
        } else {
            null
        }
        if (ev) {
            lines += "충전구는 ${chargePhrase(port)}에 있어요."
            chips += "전기"
            if (identity.connectorDcCombo) {
                lines += "커넥터: DC 콤보"
                chips += "DC 콤보"
            }
        } else {
            val side = fuelSide(port)
            lines += if (side == null) "주유구는 ${chargePhrase(port)}에 있어요." else "주유구는 ${side}에 있어요."
            chips += fuelBadge ?: MULTI_FUEL_CHIP
        }
        val speak = portClip != null && fuelKey != null
        val clips = if (speak) {
            buildList {
                add(fuelKey)
                add(portClip)
                if (ev && identity.connectorDcCombo) add("vo_connector_dc_combo")
            }
        } else {
            emptyList()
        }
        val arrival = if (speak) {
            listOf(if (ev) "vo_arrive_ev_soon" else "vo_arrive_gas_soon", portClip)
        } else {
            emptyList()
        }
        return D2Face(
            title = if (ev) "충전소에 가요" else "주유소에 가요",
            lines = lines,
            chips = chips,
            clips = clips,
            arrivalClips = arrival,
            port = port,
            routeKey = routeKey,
            fuelBadge = fuelBadge,
        )
    }

    fun chargePhrase(port: PortSide): String = when (port) {
        PortSide.REAR_RIGHT -> "오른쪽 뒤"
        PortSide.REAR_LEFT -> "왼쪽 뒤"
        PortSide.FRONT_RIGHT -> "오른쪽 앞"
        PortSide.FRONT_LEFT -> "왼쪽 앞"
        PortSide.FRONT -> "앞"
        PortSide.REAR -> "뒤"
    }

    fun chargePortClip(port: PortSide): String? = when (port) {
        PortSide.REAR_RIGHT -> "vo_charge_port_rear_right"
        PortSide.REAR_LEFT -> "vo_charge_port_rear_left"
        PortSide.FRONT_LEFT -> "vo_charge_port_left"
        PortSide.FRONT_RIGHT -> "vo_charge_port_right"
        PortSide.FRONT, PortSide.REAR -> null
    }

    fun fuelSide(port: PortSide): String? = when (port) {
        PortSide.FRONT_LEFT, PortSide.REAR_LEFT -> "왼쪽"
        PortSide.FRONT_RIGHT, PortSide.REAR_RIGHT -> "오른쪽"
        PortSide.FRONT, PortSide.REAR -> null
    }

    fun fuelSideClip(port: PortSide): String? = when (fuelSide(port)) {
        "왼쪽" -> "vo_port_left"
        "오른쪽" -> "vo_port_right"
        else -> null
    }

    /**
     * Pill text for a fuel stop. Single-fuel labels come from [fuelChip].
     * A multi-fuel car, and a card with no fuel chip, stay [MULTI_FUEL_CHIP].
     */
    fun fuelBadge(chips: List<String>): String {
        val known = buildSet {
            add(MULTI_FUEL_CHIP)
            FuelKind.entries.forEach { kind ->
                if (kind != FuelKind.ELECTRIC) add(fuelChip(kind))
            }
        }
        return chips.firstOrNull { it in known } ?: MULTI_FUEL_CHIP
    }

    private const val MULTI_FUEL_CHIP = "연료"

    private fun fuelChip(kind: FuelKind): String = when (kind) {
        FuelKind.GASOLINE -> "휘발유"
        FuelKind.DIESEL -> "경유"
        FuelKind.LPG -> "LPG"
        FuelKind.HYBRID -> "하이브리드"
        FuelKind.ELECTRIC -> "전기"
    }

    private fun fuelClip(kind: FuelKind): String? = when (kind) {
        FuelKind.GASOLINE -> "vo_fuel_gasoline"
        FuelKind.DIESEL -> "vo_fuel_diesel"
        FuelKind.LPG -> "vo_fuel_lpg"
        FuelKind.HYBRID -> "vo_fuel_hybrid"
        FuelKind.ELECTRIC -> "vo_fuel_electric"
    }
}
