package com.krazylabs.miri.domain

enum class BlinkSide {
    LEFT,
    RIGHT,
}

/**
 * Pleos TurnSignalValue is only OFF, LEFT, or RIGHT. Both lamps on is the hazard case.
 * A direct both-on value is hazard even when the blink detector has not inferred it.
 */
data class TurnLamps(
    val left: Boolean = false,
    val right: Boolean = false,
) {
    val hazard: Boolean get() = left && right

    val side: BlinkSide?
        get() = when {
            hazard || (!left && !right) -> null
            left -> BlinkSide.LEFT
            else -> BlinkSide.RIGHT
        }
}

/**
 * LEFT and RIGHT both seen inside [windowMs] is hazard.
 * OFF between blinks does not forget the previous side. The lamps themselves go dark.
 * SDK listeners cannot report both lamps in one value.
 */
class HazardDetector(
    private val windowMs: Long = 800L,
) {
    private var previous: Pair<BlinkSide, Long>? = null

    fun observe(valueName: String, nowMs: Long): TurnLamps = when (valueName) {
        "OFF" -> TurnLamps()
        "LEFT" -> side(BlinkSide.LEFT, nowMs)
        "RIGHT" -> side(BlinkSide.RIGHT, nowMs)
        else -> TurnLamps()
    }

    private fun side(side: BlinkSide, nowMs: Long): TurnLamps {
        val prior = previous
        previous = side to nowMs
        if (prior != null && prior.first != side && nowMs - prior.second <= windowMs) {
            return TurnLamps(left = true, right = true)
        }
        return if (side == BlinkSide.LEFT) TurnLamps(left = true) else TurnLamps(right = true)
    }
}
