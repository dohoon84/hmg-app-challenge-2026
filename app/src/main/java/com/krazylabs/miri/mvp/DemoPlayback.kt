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
