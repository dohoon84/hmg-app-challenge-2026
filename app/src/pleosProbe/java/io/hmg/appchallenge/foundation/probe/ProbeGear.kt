package io.hmg.appchallenge.foundation.probe

import io.hmg.appchallenge.foundation.domain.GearSelectionCodes

internal fun isReverseToPark(previousRaw: Int?, currentRaw: Int?): Boolean =
    previousRaw == GearSelectionCodes.REVERSE && currentRaw == GearSelectionCodes.PARK

internal fun isPleosReverseToPark(previous: String?, current: String?): Boolean =
    previous == "GEAR_REVERSE" && current == "GEAR_PARK"
