package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.TurnLamps

enum class CueKind {
    NONE,
    CHIME,
    CLIPS,
}

data class DriveCue(
    val kind: CueKind = CueKind.NONE,
    val clips: List<String> = emptyList(),
    val turnSpeeches: Int = 0,
)

data class DriveCueInput(
    val nowMs: Long,
    val driveGear: Boolean,
    val lamps: TurnLamps = TurnLamps(),
    val navVoice: Boolean = false,
    val holdMs: Long = 20_000L,
    val acceptEdges: Boolean = true,
    val routeId: String = "",
    val d2bEligible: Boolean = false,
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
    val d2bClips: List<String> = emptyList(),
    val navWaitMs: Long = 5_000L,
    val navMinRemainMeters: Int = 150,
    val arriveMeters: Int = 500,
    val arriveSeconds: Int = 45,
)

/**
 * Driving exceptions: a turn-signal chime, and D2-b clips.
 * Hazard (both lamps) never chimes and does not spend a speech.
 * When the chime and D2-b would start together, only D2-b plays.
 */
class DriveCueEngine(
    private val maxTurns: Int = 2,
) {
    var turnSpeeches: Int = 0
        private set

    private var holdSideKey: String? = null
    private var holdSince: Long? = null
    private var holdConsumed: Boolean = false
    private var spokenRoute: String? = null
    private var waitFrom: Long? = null

    fun resetRental() {
        turnSpeeches = 0
        clearHold()
        spokenRoute = null
        waitFrom = null
    }

    fun step(input: DriveCueInput): DriveCue {
        val chimeReady = updateTurn(input)
        val arrivalReady = updateArrival(input)
        if (arrivalReady) {
            if (chimeReady) {
                holdConsumed = true
            }
            spokenRoute = input.routeId
            waitFrom = null
            return DriveCue(CueKind.CLIPS, input.d2bClips, turnSpeeches)
        }
        if (chimeReady) {
            holdConsumed = true
            turnSpeeches += 1
            return DriveCue(CueKind.CHIME, emptyList(), turnSpeeches)
        }
        return DriveCue(CueKind.NONE, emptyList(), turnSpeeches)
    }

    private fun updateTurn(input: DriveCueInput): Boolean {
        val side = input.lamps.side
        if (!input.acceptEdges || !input.driveGear || input.lamps.hazard || side == null) {
            clearHold()
            return false
        }
        val key = side.name
        if (holdSideKey != key) {
            holdSideKey = key
            holdSince = input.nowMs
            holdConsumed = false
            return false
        }
        if (holdConsumed) return false
        val since = holdSince ?: return false
        if (input.nowMs - since < input.holdMs) return false
        if (input.navVoice || turnSpeeches >= maxTurns) {
            holdConsumed = true
            return false
        }
        return true
    }

    private fun updateArrival(input: DriveCueInput): Boolean {
        if (!input.acceptEdges || !input.driveGear || !input.d2bEligible || input.routeId.isEmpty()) {
            waitFrom = null
            return false
        }
        if (spokenRoute == input.routeId) return false
        val near = (input.remainMeters != null && input.remainMeters <= input.arriveMeters) ||
            (input.remainSeconds != null && input.remainSeconds <= input.arriveSeconds)
        if (!near) {
            waitFrom = null
            return false
        }
        if (input.navVoice) {
            val started = waitFrom ?: input.nowMs.also { waitFrom = it }
            if (input.nowMs - started < input.navWaitMs) return false
            // Still speaking after the cap: omit this route. Do not talk over navigation.
            spokenRoute = input.routeId
            waitFrom = null
            return false
        }
        if (waitFrom != null) {
            // Navigation ended early. The 150 m floor still applies.
            waitFrom = null
            val remain = input.remainMeters
            if (remain != null && remain >= input.navMinRemainMeters) return true
            spokenRoute = input.routeId
            return false
        }
        return true
    }

    private fun clearHold() {
        holdSideKey = null
        holdSince = null
        holdConsumed = false
    }
}
