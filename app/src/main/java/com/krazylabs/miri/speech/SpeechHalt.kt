package com.krazylabs.miri.speech

import com.krazylabs.miri.domain.GearPosition

/**
 * Edges that stop card and cue audio before this tick's utterance.
 * Shifting into D or R, navigation guidance starting, or the screen leaving
 * the foreground. A gear that stays in D does not stop, so a turn-signal
 * chime can start on a later tick.
 */
class SpeechHalt {
    private var gear: GearPosition? = null
    private var nav = false

    fun shouldStop(visible: Boolean, gear: GearPosition, navGuidance: Boolean): Boolean {
        val enteredDrive = (gear == GearPosition.DRIVE || gear == GearPosition.REVERSE) && gear != this.gear
        val navStarted = navGuidance && !nav
        this.gear = gear
        nav = navGuidance
        return !visible || enteredDrive || navStarted
    }
}
