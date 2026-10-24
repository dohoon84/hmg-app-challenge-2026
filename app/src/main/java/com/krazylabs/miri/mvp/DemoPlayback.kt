package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class DemoHighlight { NONE, RENTAL, RETURN_TIME, RETURN_PLACE, CONFIRM }

/**
 * Debug auto-play. Release builds bind [IdlePlayback].
 * The debug player and the stand-in cabin are not in the release APK.
 */
interface DemoPlayback {
    val running: StateFlow<Boolean>
    val caption: StateFlow<String>
    val captionsOn: StateFlow<Boolean>
    val highlight: StateFlow<DemoHighlight>

    fun toggleCaptions()
    fun play(host: DemoPlaybackHost, scope: CoroutineScope, realGear: GearPosition = GearPosition.PARK)

    /**
     * [fromStep] is a beat id, or null to start at F1.
     * [holdScale] multiplies each step's hold from that beat. Earlier steps
     * still run so the cabin state is in place, with a zero hold.
     * Real gear still has to be Park. Release uses the default, which ignores both.
     */
    fun playFrom(
        host: DemoPlaybackHost,
        scope: CoroutineScope,
        realGear: GearPosition,
        fromStep: String?,
        holdScale: Float,
    ) {
        play(host, scope, realGear)
    }

    fun onRealGear(gear: GearPosition) = Unit
    fun stop()
}

interface DemoPlaybackHost {
    fun refresh()
    fun rewindToIntake()
    fun chooseRental()
    fun defaultReturnMinute(): Int
    fun selectReturnMinute(minuteOfDay: Int)
    fun selectReturnDay(offset: Int)
    fun selectReturnPlace(placeId: String)
    fun confirmReturn()
    fun scenario(): ScenarioOverrides
    fun applyScenario(next: ScenarioOverrides)
    fun clearDemo()

    /** Auto-play epilogue. "C3" then "C2". Null on the product path. */
    fun setEpilogue(which: String?) {}
}

class IdlePlayback @Inject constructor() : DemoPlayback {
    override val running: StateFlow<Boolean> = MutableStateFlow(false)
    override val caption: StateFlow<String> = MutableStateFlow("")
    override val captionsOn: StateFlow<Boolean> = MutableStateFlow(false)
    override val highlight: StateFlow<DemoHighlight> = MutableStateFlow(DemoHighlight.NONE)
    override fun toggleCaptions() = Unit
    override fun play(host: DemoPlaybackHost, scope: CoroutineScope, realGear: GearPosition) = Unit
    override fun stop() = Unit
}
