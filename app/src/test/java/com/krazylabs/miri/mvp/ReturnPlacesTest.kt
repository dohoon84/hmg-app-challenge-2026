package com.krazylabs.miri.mvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnPlacesTest {
    private val demo = listOf(
        ReturnPlace("pickup", "대치동 공영주차장", "대여한 곳", 37.4995, 127.0635, "2.4 km"),
        ReturnPlace("yeoksam", "역삼 영업소", "영업소", 37.5007, 127.0365, "3.1 km"),
        ReturnPlace("pangyo", "판교역 환승주차장", "최근 목적지", 37.3948, 127.1113, "12.8 km"),
    )

    @Test
    fun anEmptyLiveListUsesTheThreeDemoRows() {
        val rows = assembleReturnPlaces(null, emptyList(), 37.5, 127.0, demo)
        assertEquals(listOf("대치동 공영주차장", "역삼 영업소", "판교역 환승주차장"), rows.map { it.name })
        assertEquals(listOf("대여한 곳", "영업소", "최근 목적지"), rows.map { it.caption })
        assertEquals(listOf("2.4 km", "3.1 km", "12.8 km"), rows.map { it.distance })
    }

    @Test
    fun threeNamedPlacesStayAndDropTheDemo() {
        val live = (0 until 4).map { index ->
            NearbyPlace("p$index", "장소 $index", "최근 목적지", 37.5 + index * 0.01, 127.0)
        }
        val rows = assembleReturnPlaces(null, live, 37.5, 127.0, demo)
        assertEquals(listOf("장소 0", "장소 1", "장소 2"), rows.map { it.name })
        assertTrue(rows[0].distance!!.endsWith(" km"))
        assertFalse(rows.any { it.name == "대치동 공영주차장" })
    }

    @Test
    fun aShortLiveListIsFilledWithoutRepeatingAName() {
        val live = listOf(
            NearbyPlace("yeoksam", "역삼 영업소", "영업소", 37.5007, 127.0365),
            NearbyPlace("blank", "  ", "최근 목적지", 0.0, 0.0),
        )
        val rows = assembleReturnPlaces(null, live, null, null, demo)
        assertEquals(listOf("역삼 영업소", "대치동 공영주차장", "판교역 환승주차장"), rows.map { it.name })
        assertNull(rows[0].distance)
    }

    @Test
    fun aNamedOriginLeadsTheList() {
        val origin = NearbyPlace("origin", "대여 지점", "대여한 곳", 37.5, 127.0)
        val rows = assembleReturnPlaces(origin, emptyList(), null, null, demo)
        assertEquals(listOf("대여 지점", "대치동 공영주차장", "역삼 영업소"), rows.map { it.name })
    }

    @Test
    fun nothingLiveAndNoDemoKeepsThePickupRow() {
        val rows = assembleReturnPlaces(null, emptyList(), null, null, emptyList())
        assertEquals(1, rows.size)
        assertEquals(F2Draft.PLACE_PICKUP, rows[0].id)
        assertEquals(F2Draft.PICKUP_LABEL, rows[0].name)
        assertEquals(F2Draft.PICKUP_LABEL, rows[0].caption)
        assertNull(rows[0].distance)
    }

    @Test
    fun aForcedNewRentalOpensF1EvenWhenTheLockIsKnown() {
        assertTrue(intakeVisible(doorLocked = true, kind = null, ownCar = false, primed = false, forcedNewRental = true))
        assertFalse(intakeVisible(doorLocked = true, kind = "rental", ownCar = false, primed = false, forcedNewRental = true))
        assertFalse(intakeVisible(doorLocked = null, kind = null, ownCar = true, primed = false, forcedNewRental = true))
        assertTrue(intakeVisible(doorLocked = null, kind = null, ownCar = false, primed = false, forcedNewRental = false))
        assertTrue(intakeVisible(doorLocked = false, kind = null, ownCar = false, primed = true, forcedNewRental = false))
        assertFalse(intakeVisible(doorLocked = true, kind = null, ownCar = false, primed = false, forcedNewRental = false))
    }
}
