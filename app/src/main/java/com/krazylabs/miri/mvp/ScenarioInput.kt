package com.krazylabs.miri.mvp

import kotlinx.coroutines.flow.StateFlow

/**
 * Debug builds replace distances and timings. Release builds keep the product defaults.
 * The app never calls the Virtual Console.
 */
data class ScenarioOverrides(
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
    val returnInMinutes: Int? = null,
    /**
     * Filming profile only. F3 uses the saved F2 return chip instead of
     * now plus [returnInMinutes]. The product path leaves this false.
     */
    val fixedReturn: Boolean = false,
    val chargeTargetPercent: Int? = null,
    val pretendCharging: Boolean = false,
    val pretendTolls: Int? = null,
    val pretendHighways: Int? = null,
    val armNewRental: Boolean = false,
    val lightsUseDoorStandIn: Boolean = false,
    val showReturnGuide: Boolean = false,
    val lockMs: Long = 30 * 60 * 1000L,
    val cardMs: Long = 20_000L,
    val turnMs: Long = 20_000L,
    /** Blank keeps [com.krazylabs.miri.speech.SpeechUsage] on the build-config default. */
    val speechUsage: String = "",
    /** Blank keeps [com.krazylabs.miri.domain.GearBackend] on the build-config default. */
    val gearBackend: String = "",
) {
    /** Drops the panel's test numbers and voice so the next rental starts clean. */
    fun clearedForNewRental() = copy(
        remainMeters = null,
        remainSeconds = null,
        returnInMinutes = null,
        fixedReturn = false,
        chargeTargetPercent = null,
        pretendCharging = false,
        pretendTolls = null,
        pretendHighways = null,
        armNewRental = false,
        speechUsage = "",
    )
}

interface ScenarioInput {
    val overrides: StateFlow<ScenarioOverrides>
    fun update(next: ScenarioOverrides)
}
