package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.PortSide

data class D2Face(
    val title: String,
    val lines: List<String>,
    val chips: List<String>,
    val clips: List<String>,
    val arrivalClips: List<String>,
    val port: PortSide?,
)

/**
 * D2 common conditions. An empty fuel type or an empty port drops the whole card.
 * An unknown connector drops only the connector sentence and chip.
 * A center port has no clip, so the card stays and that sentence is not spoken.
 */
object D2Rules {
    fun offer(
        identity: CarIdentity,
        role: DestinationRole,
        specAgrees: Boolean = true,
    ): D2Face? {
        if (!specAgrees || !identity.fuelKnown) return null
        val ev = identity.evOnly
        val port = identity.portForCards ?: return null
        if (ev && role != DestinationRole.CHARGING) return null
        if (!ev && role != DestinationRole.GAS) return null
        val clips = mutableListOf<String>()
        val chips = mutableListOf<String>()
        val lines = mutableListOf<String>()
        if (ev) {
            lines += "충전구는 ${chargePhrase(port)}에 있어요."
            chips += "전기"
            clips += "vo_fuel_electric"
            chargePortClip(port)?.let { clips += it }
            if (identity.connectorDcCombo) {
                lines += "커넥터: DC 콤보"
                chips += "DC 콤보"
                clips += "vo_connector_dc_combo"
            }
            val arrive = listOf("vo_arrive_ev_soon")
            return D2Face(
                title = "충전소에 가요",
                lines = lines,
                chips = chips,
                clips = clips,
                arrivalClips = arrive + listOfNotNull(chargePortClip(port)),
                port = port,
            )
        }
        val side = fuelSide(port)
        lines += if (side == null) "주유구는 ${chargePhrase(port)}에 있어요." else "주유구는 ${side}에 있어요."
        if (identity.fuels.size == 1) {
            chips += fuelChip(identity.fuels[0])
            fuelClip(identity.fuels[0])?.let { clips += it }
        } else {
            chips += "연료"
        }
        fuelSideClip(port)?.let { clips += it }
        return D2Face(
            title = "주유소에 가요",
            lines = lines,
            chips = chips,
            clips = clips,
            arrivalClips = listOf("vo_arrive_gas_soon") + listOfNotNull(fuelSideClip(port)),
            port = port,
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
