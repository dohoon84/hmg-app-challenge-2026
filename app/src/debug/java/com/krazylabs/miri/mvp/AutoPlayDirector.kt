package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.vehicle.ScriptedCabinFeed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays F1 through C2 on the real card session by swapping in [ScriptedCabinFeed]
 * and demo route numbers. It does not open the navigation app or write vehicle
 * properties. Release builds do not contain this class.
 */
@Singleton
class AutoPlayDirector private constructor(
    private val cabin: ScriptedCabinFeed,
    private val wait: suspend (Long) -> Unit,
) : DemoPlayback {
    private val _running = MutableStateFlow(false)
    private val _caption = MutableStateFlow("")
    private val _captionsOn = MutableStateFlow(false)
    private val _highlight = MutableStateFlow(DemoHighlight.NONE)
    override val running: StateFlow<Boolean> = _running
    override val caption: StateFlow<String> = _caption
    override val captionsOn: StateFlow<Boolean> = _captionsOn
    override val highlight: StateFlow<DemoHighlight> = _highlight

    private var job: Job? = null
    private var generation = 0
    private var host: DemoPlaybackHost? = null

    @Inject constructor(cabin: ScriptedCabinFeed) : this(cabin, { delay(it) })

    companion object {
        fun forTest(cabin: ScriptedCabinFeed, wait: suspend (Long) -> Unit) =
            AutoPlayDirector(cabin, wait)
    }

    override fun toggleCaptions() {
        _captionsOn.value = !_captionsOn.value
    }

    override fun play(host: DemoPlaybackHost, scope: CoroutineScope, realGear: GearPosition) {
        if (realGear != GearPosition.PARK) return
        stop()
        this.host = host
        val gen = generation
        _running.value = true
        cabin.engage()
        job = scope.launch {
            try {
                runScript(host)
            } finally {
                if (gen == generation) cleanup(host)
            }
        }
    }

    override fun onRealGear(gear: GearPosition) {
        if (gear != GearPosition.PARK && _running.value) stop()
    }

    override fun stop() {
        generation += 1
        job?.cancel()
        job = null
        host?.let { cleanup(it) }
        _running.value = false
    }

    private suspend fun runScript(host: DemoPlaybackHost) {
        host.rewindToIntake()
        for (step in AutoPlayScript.steps) {
            when (step) {
                is AutoStep.Show -> {
                    _caption.value = step.caption
                    _highlight.value = DemoHighlight.NONE
                    host.refresh()
                    wait(step.ms)
                }
                is AutoStep.Highlight -> {
                    _highlight.value = step.which
                    host.refresh()
                    wait(step.ms)
                    _highlight.value = DemoHighlight.NONE
                }
                is AutoStep.ChooseRental -> host.chooseRental()
                is AutoStep.PickTime -> {
                    val minute = host.defaultReturnMinute()
                    if (minute < 0) {
                        host.selectReturnDay(1)
                        host.selectReturnMinute(0)
                    } else {
                        host.selectReturnMinute(minute)
                    }
                }
                is AutoStep.PickPlace -> host.selectReturnPlace(F2Draft.PLACE_PICKUP)
                is AutoStep.Confirm -> host.confirmReturn()
                is AutoStep.Road -> {
                    val current = host.scenario()
                    host.applyScenario(
                        current.copy(
                            pretendTolls = step.tolls,
                            pretendHighways = step.highways,
                            pretendCharging = step.charging,
                            armNewRental = false,
                        ),
                    )
                }
                is AutoStep.ReturnWindow -> {
                    val current = host.scenario()
                    host.applyScenario(
                        current.copy(
                            pretendCharging = false,
                            pretendTolls = null,
                            pretendHighways = null,
                            returnInMinutes = 120,
                            chargeTargetPercent = 80,
                            armNewRental = false,
                        ),
                    )
                }
                is AutoStep.Gear -> {
                    if (step.gear == GearPosition.DRIVE) cabin.addDrivenKm()
                    cabin.gear = step.gear
                    _caption.value = step.caption
                    host.refresh()
                    wait(step.ms)
                }
                is AutoStep.Body -> {
                    cabin.rearDoorOpen = step.rearDoorOpen
                    cabin.soc = step.soc
                    host.refresh()
                    wait(step.ms)
                }
            }
        }
    }

    private fun cleanup(host: DemoPlaybackHost) {
        cabin.release()
        _highlight.value = DemoHighlight.NONE
        _caption.value = ""
        _running.value = false
        host.clearDemo()
        host.refresh()
    }
}

sealed interface AutoStep {
    val ms: Long

    data class Show(val beat: String, val caption: String, override val ms: Long) : AutoStep
    data class Highlight(val which: DemoHighlight, override val ms: Long = AutoPlayScript.HIGHLIGHT_MS) : AutoStep
    data class ChooseRental(override val ms: Long = 0) : AutoStep
    data class PickTime(override val ms: Long = 0) : AutoStep
    data class PickPlace(override val ms: Long = 0) : AutoStep
    data class Confirm(override val ms: Long = 0) : AutoStep
    data class Road(
        val tolls: Int?,
        val highways: Int?,
        val charging: Boolean,
        override val ms: Long = 0,
    ) : AutoStep
    data class ReturnWindow(override val ms: Long = 0) : AutoStep
    data class Gear(val gear: GearPosition, val caption: String, val beat: String? = null, override val ms: Long) : AutoStep
    data class Body(val rearDoorOpen: Boolean, val soc: Int, override val ms: Long = 0) : AutoStep
}

object AutoPlayScript {
    const val HIGHLIGHT_MS = 1_000L
    private const val DRIVE_MS = 2_000L
    private const val CARD_MS = 21_000L

    val steps: List<AutoStep> = listOf(
        AutoStep.Show("F1", "정차 중", 7_000),
        AutoStep.Highlight(DemoHighlight.RENTAL),
        AutoStep.ChooseRental(),
        AutoStep.Show("F2", "렌터카를 골랐어요", 2_000),
        AutoStep.PickTime(),
        AutoStep.Highlight(DemoHighlight.RETURN_TIME),
        AutoStep.PickPlace(),
        AutoStep.Highlight(DemoHighlight.RETURN_PLACE),
        AutoStep.Highlight(DemoHighlight.CONFIRM),
        AutoStep.Confirm(),
        AutoStep.Show("F2_DONE", "반납 시각을 정했어요", 4_000),
        AutoStep.Road(tolls = 2, highways = 1, charging = false),
        AutoStep.Gear(GearPosition.DRIVE, "주행 후 정차", ms = DRIVE_MS),
        AutoStep.Gear(GearPosition.PARK, "D → P", beat = "D1", ms = CARD_MS),
        AutoStep.Road(tolls = null, highways = null, charging = true),
        AutoStep.Gear(GearPosition.DRIVE, "충전소로 가는 길", ms = DRIVE_MS),
        AutoStep.Gear(GearPosition.PARK, "D → P", beat = "D2", ms = CARD_MS),
        AutoStep.Gear(GearPosition.DRIVE, "반납 시간이 다가와요", ms = DRIVE_MS),
        AutoStep.ReturnWindow(),
        AutoStep.Gear(GearPosition.PARK, "반납 준비", beat = "F3", ms = 8_000),
        AutoStep.Gear(GearPosition.DRIVE, "반납 장소 도착", ms = DRIVE_MS),
        AutoStep.Body(rearDoorOpen = true, soc = 60),
        AutoStep.Gear(GearPosition.PARK, "반납 장소 도착", beat = "C1", ms = 8_000),
        AutoStep.Body(rearDoorOpen = false, soc = 60, ms = 1_000),
        AutoStep.Body(rearDoorOpen = false, soc = 90),
        AutoStep.Show("C2", "점검 끝", 6_000),
    )

    fun beats(): List<String> = steps.mapNotNull { step ->
        when (step) {
            is AutoStep.Show -> step.beat
            is AutoStep.Gear -> step.beat
            else -> null
        }
    }

    fun totalMs(): Long = steps.sumOf { it.ms }
}
