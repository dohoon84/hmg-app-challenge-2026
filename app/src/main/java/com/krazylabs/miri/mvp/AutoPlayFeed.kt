package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition

/**
 * What auto-play is allowed to read.
 * The saved return chip and the real destination stay out, so the order does
 * not change with the wall clock or with a charger left in navigation.
 */
data class AutoPlayNav(
    val charging: Boolean,
    val routeId: String,
    val routeKey: String,
    val tolls: Int,
    val highways: Int,
    val routing: Boolean,
    val remainMeters: Int?,
    val remainSeconds: Int?,
    /** Null keeps F3 closed. A saved form time is not a trigger while playing. */
    val f3MinutesLeft: Long?,
)

object AutoPlayFeed {
    const val CHARGE_KEY = "demo-charge"

    /** 모레 12:00. That chip is more than 24 hours away at every clock. */
    const val RETURN_DAY = 2
    const val RETURN_MINUTE = 12 * 60

    fun resolve(
        playing: Boolean,
        pretendCharging: Boolean,
        returnInMinutes: Int?,
        scenarioRemainMeters: Int?,
        scenarioRemainSeconds: Int?,
        realCharging: Boolean,
        realRouteId: String,
        realRouteKey: String,
        realTolls: Int,
        realHighways: Int,
        realRouting: Boolean,
        realRemainMeters: Int?,
        realRemainSeconds: Int?,
        formMinutesLeft: Long?,
    ): AutoPlayNav {
        if (!playing) {
            return AutoPlayNav(
                charging = pretendCharging || realCharging,
                routeId = realRouteId,
                routeKey = realRouteKey,
                tolls = realTolls,
                highways = realHighways,
                routing = realRouting,
                remainMeters = scenarioRemainMeters ?: realRemainMeters,
                remainSeconds = scenarioRemainSeconds ?: realRemainSeconds,
                f3MinutesLeft = returnInMinutes?.toLong() ?: formMinutesLeft,
            )
        }
        return AutoPlayNav(
            charging = pretendCharging,
            routeId = if (pretendCharging) CHARGE_KEY else "",
            routeKey = if (pretendCharging) CHARGE_KEY else "",
            tolls = 0,
            highways = 0,
            routing = false,
            remainMeters = scenarioRemainMeters,
            remainSeconds = scenarioRemainSeconds,
            f3MinutesLeft = returnInMinutes?.toLong(),
        )
    }

    /**
     * The park card is the one D2 sound. Offering the face while the script
     * is still in Drive records the key and speaks the arrival cue as well.
     */
    fun cardD2(playing: Boolean, gear: GearPosition, face: D2Face?): D2Face? =
        if (playing && gear != GearPosition.PARK) null else face
}
