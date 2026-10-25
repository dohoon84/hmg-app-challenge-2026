package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RentalDistanceTest {
    @Test
    fun aReadableGainBecomesTheReturnLine() {
        assertEquals(1_000f, RentalDistance.startToKeep(1_000f, readable = true))
        assertEquals(
            "이번 대여에서 212km를 달렸어요. 수고하셨어요.",
            RentalDistance.line(startKm = 1_000f, currentKm = 1_212.4f, readable = true),
        )
    }

    @Test
    fun anUnreadableOdometerKeepsNoStartAndNoLine() {
        assertNull(RentalDistance.startToKeep(1_212f, readable = false))
        assertNull(RentalDistance.line(startKm = 1_000f, currentKm = 1_212f, readable = false))
    }

    @Test
    fun aMissingStartHidesTheLine() {
        assertNull(RentalDistance.startToKeep(null, readable = true))
        assertNull(RentalDistance.line(startKm = null, currentKm = 1_212f, readable = true))
    }

    @Test
    fun aNegativeDistanceHidesTheLine() {
        assertNull(RentalDistance.line(startKm = 500f, currentKm = 400f, readable = true))
    }

    @Test
    fun zeroAndAFractionThatRoundsToZeroHideTheLine() {
        assertNull(RentalDistance.line(startKm = 100f, currentKm = 100f, readable = true))
        assertNull(RentalDistance.line(startKm = 100f, currentKm = 100.4f, readable = true))
    }

    @Test
    fun theFinishedCheckCarriesTheDistanceLine() {
        val session = CardSession()
        val line = RentalDistance.line(1_000f, 1_212.4f, readable = true)
        val open = DeckInput(
            gear = GearPosition.PARK,
            visible = true,
            edgesOpen = true,
            ownCar = false,
            kind = "rental",
            returnSaved = true,
            showIntake = false,
            checkItems = listOf(CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요.")),
            checkNote = null,
            checkTitle = CheckRules.TITLE,
            reshowCheck = false,
            f4 = null,
            d2 = null,
            f3 = null,
            d1 = null,
            nowMs = 0L,
            cardMs = 6_000L,
            showGuide = false,
            tripLine = line,
        )
        session.step(open)
        val done = session.step(open.copy(checkItems = emptyList()))
        assertEquals(listOf(CheckRules.DONE, line), done.card?.lines)
        val stay = session.step(open.copy(checkItems = emptyList(), nowMs = 1_000L))
        assertEquals(listOf(CheckRules.DONE, line), stay.card?.lines)
    }
}
