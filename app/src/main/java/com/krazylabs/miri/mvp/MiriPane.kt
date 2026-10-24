package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition

enum class MiriPane {
    ICON,
    F1,
    F2,
}

fun miriPane(gear: GearPosition, ownCar: Boolean, kindChosen: Boolean, returnSaved: Boolean): MiriPane {
    if (gear != GearPosition.PARK) return MiriPane.ICON
    if (ownCar || returnSaved) return MiriPane.ICON
    if (!kindChosen) return MiriPane.F1
    return MiriPane.F2
}

fun arrivalClips(charging: Boolean): List<String> = listOf(
    if (charging) "vo_arrive_ev_soon" else "vo_arrive_gas_soon",
)

fun destinationRole(name: String): DestinationRole {
    if (name.contains("충전")) return DestinationRole.CHARGING
    val gas = listOf("주유소", "GS칼텍스", "SK", "S-OIL", "현대오일뱅크", "알뜰")
    if (gas.any { name.contains(it) }) return DestinationRole.GAS
    return DestinationRole.OTHER
}

enum class DestinationRole {
    CHARGING,
    GAS,
    OTHER,
}
