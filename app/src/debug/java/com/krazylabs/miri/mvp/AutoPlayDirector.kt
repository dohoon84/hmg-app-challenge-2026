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
 * Plays F1, F2, D1, D2, F3, C1, C3, and C2 by swapping in [ScriptedCabinFeed]
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
        begin(host, scope, realGear, fromStep = null, holdScale = 1f, profile = null, seconds = null)
    }

    override fun playFrom(
        host: DemoPlaybackHost,
        scope: CoroutineScope,
        realGear: GearPosition,
        fromStep: String?,
        holdScale: Float,
    ) {
        begin(host, scope, realGear, fromStep, holdScale, profile = null, seconds = null)
    }

    override fun playCut(
        host: DemoPlaybackHost,
        scope: CoroutineScope,
        realGear: GearPosition,
        fromStep: String?,
        holdScale: Float,
        profile: String?,
        seconds: Int?,
    ) {
        begin(host, scope, realGear, fromStep, holdScale, profile, seconds)
    }

    private fun begin(
        host: DemoPlaybackHost,
        scope: CoroutineScope,
        realGear: GearPosition,
        fromStep: String?,
        holdScale: Float,
        profile: String?,
        seconds: Int?,
    ) {
        if (profile != null && profile != FilmingCommands.PROFILE_STORYBOARD_V1) return
        val storyboard = profile == FilmingCommands.PROFILE_STORYBOARD_V1
        val beats = if (storyboard) StoryboardScript.beats() else AutoPlayScript.beats()
        if (realGear != GearPosition.PARK) return
        if (fromStep != null && fromStep !in beats) return
        stop()
        this.host = host
        val steps = if (storyboard) {
            StoryboardScript.paced(fromStep, holdScale, seconds)
        } else {
            AutoPlayScript.paced(fromStep, holdScale, seconds)
        }
        val gen = generation
        _running.value = true
        cabin.engage(soc = if (storyboard) StoryboardScript.SOC_CARD else 90)
        cabin.followEvSoc = storyboard
        job = scope.launch {
            try {
                runScript(host, steps)
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

    private suspend fun runScript(host: DemoPlaybackHost, steps: List<AutoStep>) {
        host.setFastForward(true)
        host.rewindToIntake()
        for (step in steps) {
            if (step.ms > 0L) host.setFastForward(false)
            when (step) {
                is AutoStep.Show -> {
                    _caption.value = step.caption
                    _highlight.value = DemoHighlight.NONE
                    host.refresh()
                    hold(step.ms)
                }
                is AutoStep.Highlight -> {
                    _highlight.value = step.which
                    host.refresh()
                    hold(step.ms)
                    _highlight.value = DemoHighlight.NONE
                }
                is AutoStep.ChooseRental -> host.chooseRental()
                is AutoStep.PickTime -> {
                    host.selectReturnDay(AutoPlayFeed.RETURN_DAY)
                    host.selectReturnMinute(AutoPlayFeed.RETURN_MINUTE)
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
                            returnInMinutes = if (step.fixedClock) null else 120,
                            fixedReturn = step.fixedClock,
                            chargeTargetPercent = 80,
                            remainSeconds = F3Rules.DEMO_ETA_SECONDS,
                            armNewRental = false,
                        ),
                    )
                }
                is AutoStep.Gear -> {
                    if (step.gear == GearPosition.DRIVE) cabin.addDrivenKm()
                    cabin.gear = step.gear
                    _caption.value = step.caption
                    host.refresh()
                    hold(step.ms)
                }
                is AutoStep.Body -> {
                    cabin.rearDoorOpen = step.rearDoorOpen
                    cabin.soc = step.soc
                    host.refresh()
                    hold(step.ms)
                }
                is AutoStep.Epilogue -> host.setEpilogue(step.which)
                is AutoStep.Rewind -> host.rewindToIntake()
            }
        }
    }

    private suspend fun hold(ms: Long) {
        if (ms > 0L) wait(ms)
    }

    private fun cleanup(host: DemoPlaybackHost) {
        host.setFastForward(false)
        cabin.release()
        _highlight.value = DemoHighlight.NONE
        _caption.value = ""
        _running.value = false
        host.setEpilogue(null)
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
    data class ReturnWindow(val fixedClock: Boolean = false, override val ms: Long = 0) : AutoStep
    data class Gear(val gear: GearPosition, val caption: String, val beat: String? = null, override val ms: Long) : AutoStep
    data class Body(val rearDoorOpen: Boolean, val soc: Int, override val ms: Long = 0) : AutoStep
    data class Epilogue(val which: String, override val ms: Long = 0) : AutoStep
    data class Rewind(override val ms: Long = 0) : AutoStep
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
        AutoStep.Epilogue("C3"),
        AutoStep.Body(rearDoorOpen = false, soc = 90),
        AutoStep.Show("C3", "다녀왔어요", 7_000),
        AutoStep.Epilogue("C2"),
        AutoStep.Show("C2", "점검 끝", 3_000),
        AutoStep.Rewind(),
    )

    fun beats(): List<String> = steps.mapNotNull { beatOf(it) }

    fun beatOf(step: AutoStep): String? = when (step) {
        is AutoStep.Show -> step.beat
        is AutoStep.Gear -> step.beat
        else -> null
    }

    fun totalMs(): Long = steps.sumOf { it.ms }

    /**
     * Full script when [fromStep] is null. Steps before that beat keep their
     * actions and drop their hold, so a jump still builds the cabin state.
     * [holdScale] applies from the beat onward. [seconds] replaces that cut's
     * summed hold. [totalMs] stays the unscaled sum.
     */
    fun paced(fromStep: String?, holdScale: Float, seconds: Int? = null): List<AutoStep> =
        paceSteps(steps, fromStep, holdScale, seconds)
}

/**
 * CF cut table. The rehearsal script stays on [AutoPlayScript].
 * Visible cards use [SOC_CARD]. [SOC_CHARGE] is the charging cut's fallback
 * and is not itself a card. F3 uses the F2 chip. The rear door closes and
 * stays checked before C2. There is no 다녀왔어요 beat and no in-app caption.
 */
object StoryboardScript {
    const val SOC_CARD = 80
    const val SOC_CHARGE = 60

    val steps: List<AutoStep> = listOf(
        AutoStep.Show("F1", "", 5_000),
        AutoStep.ChooseRental(),
        AutoStep.PickTime(),
        AutoStep.PickPlace(),
        AutoStep.Show("F2", "", 7_000),
        AutoStep.Confirm(),
        AutoStep.Road(tolls = 2, highways = 1, charging = false),
        AutoStep.Gear(GearPosition.DRIVE, "", ms = 0),
        AutoStep.Gear(GearPosition.PARK, "", beat = "D1", ms = 6_000),
        AutoStep.Road(tolls = null, highways = null, charging = true),
        AutoStep.Gear(GearPosition.DRIVE, "", beat = "DRIVE1", ms = 6_000),
        AutoStep.Gear(GearPosition.PARK, "", beat = "D2", ms = 7_000),
        AutoStep.Gear(GearPosition.DRIVE, "", beat = "DRIVE2", ms = 3_000),
        AutoStep.Body(rearDoorOpen = false, soc = SOC_CHARGE, ms = 0),
        AutoStep.Body(rearDoorOpen = false, soc = SOC_CARD),
        AutoStep.ReturnWindow(fixedClock = true),
        AutoStep.Gear(GearPosition.PARK, "", beat = "F3", ms = 9_000),
        AutoStep.Gear(GearPosition.DRIVE, "", ms = 0),
        AutoStep.Body(rearDoorOpen = true, soc = SOC_CARD),
        AutoStep.Gear(GearPosition.PARK, "", beat = "C1", ms = 6_000),
        AutoStep.Body(rearDoorOpen = false, soc = SOC_CARD, ms = FilmingChrome.CHECKED_HOLD_MS),
        AutoStep.Show("C2", "", 4_000),
        AutoStep.Rewind(),
    )

    fun beats(): List<String> = steps.mapNotNull { AutoPlayScript.beatOf(it) }.distinct()

    fun paced(fromStep: String?, holdScale: Float, seconds: Int? = null): List<AutoStep> =
        paceSteps(steps, fromStep, holdScale, seconds)

    /** Milliseconds from this beat through the steps before the next beat. */
    fun holdMs(beat: String): Long = cutHold(steps, beat)
}

internal fun paceSteps(
    steps: List<AutoStep>,
    fromStep: String?,
    holdScale: Float,
    seconds: Int? = null,
): List<AutoStep> {
    val scale = if (holdScale.isFinite() && holdScale > 0f) holdScale else 1f
    val start = if (fromStep == null) {
        0
    } else {
        val index = steps.indexOfFirst { AutoPlayScript.beatOf(it) == fromStep }
        if (index < 0) return emptyList()
        index
    }
    val paced = steps.mapIndexed { index, step ->
        step.scaled(if (index < start) 0f else scale)
    }.toMutableList()
    val overrideMs = seconds?.takeIf { it > 0 }?.times(1_000L) ?: return paced
    var end = start
    for (index in start + 1 until paced.size) {
        if (AutoPlayScript.beatOf(paced[index]) != null) break
        end = index
    }
    val positive = (start..end).filter { paced[it].ms > 0L }
    if (positive.isEmpty()) {
        paced[start] = paced[start].withMs(overrideMs)
        return paced
    }
    val current = positive.sumOf { paced[it].ms }
    var used = 0L
    positive.forEachIndexed { nth, index ->
        val ms = if (nth == positive.lastIndex) {
            overrideMs - used
        } else {
            val share = (paced[index].ms.toDouble() / current.toDouble() * overrideMs.toDouble()).toLong()
            used += share
            share
        }
        paced[index] = paced[index].withMs(ms)
    }
    return paced
}

internal fun cutHold(steps: List<AutoStep>, beat: String): Long {
    val start = steps.indexOfFirst { AutoPlayScript.beatOf(it) == beat }
    if (start < 0) return 0L
    var end = start
    for (index in start + 1 until steps.size) {
        if (AutoPlayScript.beatOf(steps[index]) != null) break
        end = index
    }
    return steps.subList(start, end + 1).sumOf { it.ms }
}

private fun AutoStep.scaled(factor: Float): AutoStep {
    if (factor == 1f || ms == 0L) return this
    val next = (ms.toDouble() * factor.toDouble()).toLong()
    if (next == ms) return this
    return withMs(next)
}

private fun AutoStep.withMs(next: Long): AutoStep = when (this) {
    is AutoStep.Show -> copy(ms = next)
    is AutoStep.Highlight -> copy(ms = next)
    is AutoStep.ChooseRental -> copy(ms = next)
    is AutoStep.PickTime -> copy(ms = next)
    is AutoStep.PickPlace -> copy(ms = next)
    is AutoStep.Confirm -> copy(ms = next)
    is AutoStep.Road -> copy(ms = next)
    is AutoStep.ReturnWindow -> copy(ms = next)
    is AutoStep.Gear -> copy(ms = next)
    is AutoStep.Body -> copy(ms = next)
    is AutoStep.Epilogue -> copy(ms = next)
    is AutoStep.Rewind -> copy(ms = next)
}
