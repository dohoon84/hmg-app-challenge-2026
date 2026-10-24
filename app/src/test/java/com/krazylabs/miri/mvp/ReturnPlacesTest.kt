package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GeoEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnPlacesTest {
    private val demo = listOf(
        ReturnPlace("daechi", "대치동 공영주차장", "대여한 곳", 37.4995, 127.0635, "2.4 km"),
        ReturnPlace("yeoksam", "역삼 영업소", "영업소", 37.5007, 127.0365, "3.1 km"),
        ReturnPlace("pangyo", "판교역 환승주차장", "최근 목적지", 37.3948, 127.1113, "12.8 km"),
        ReturnPlace(F2Draft.PLACE_PICKUP, F2Draft.PICKUP_LABEL, F2Draft.PICKUP_LABEL, 1.0, 2.0, "9.9 km"),
    )

    @Test
    fun aNewRentalClearsThePreviousOrigin() {
        val previous = RentalOrigin(37.1, 127.2, "옛 주소", pending = false, seenSerial = 2)
        val next = previous.begin(2)
        assertNull(next.latitude)
        assertNull(next.longitude)
        assertEquals("", next.address)
        assertTrue(next.pending)
        val stale = next.arrive(2, 37.1, 127.2, "옛 주소")
        assertTrue(stale.pending)
        assertNull(stale.latitude)
    }

    @Test
    fun theOriginRowIsAlwaysFirstAndNotARecentPlace() {
        val recent = NearbyPlace("recent:1", "역삼 영업소", "최근 목적지", 37.5, 127.1)
        val rows = assembleReturnPlaces(
            origin = originRow(RentalOrigin(pending = true, seenSerial = 4), null, null),
            destination = null,
            recents = listOf(recent),
            selected = null,
            hereLatitude = null,
            hereLongitude = null,
            demo = demo,
        )
        assertEquals(F2Draft.PLACE_PICKUP, rows.first().id)
        assertEquals(F2Draft.PICKUP_LABEL, rows.first().name)
        assertEquals(ORIGIN_PENDING, rows.first().caption)
        assertEquals("역삼 영업소", rows.getOrNull(1)?.name)
        assertFalse(rows.any { it.id == "daechi" })
    }

    @Test
    fun pendingOriginFillsWhenANewerFixArrives() {
        val started = RentalOrigin(37.1, 127.2, "옛 주소").begin(3)
        val waiting = originRow(started, 37.4, 127.0)
        assertEquals(ORIGIN_PENDING, waiting.caption)
        assertNull(waiting.distance)
        val arrived = started.arrive(4, 37.51, 127.0, "수정구 시흥동")
        assertFalse(arrived.pending)
        assertEquals("수정구 시흥동", arrived.address)
        val filled = originRow(arrived, 37.5, 127.0)
        assertEquals(F2Draft.PICKUP_LABEL, filled.name)
        assertEquals("수정구 시흥동", filled.caption)
        assertEquals(straightLineLabel(37.5, 127.0, 37.51, 127.0), filled.distance)
        assertEquals("", originRow(arrived.copy(address = ""), 37.5, 127.0).caption)
    }

    @Test
    fun straightLineKilometresUseOneDecimal() {
        assertEquals("2.4 km", kmDistance(2400))
        assertEquals("0.0 km", kmDistance(0))
        assertEquals("1.1 km", kmDistance(1111))
        assertEquals("0.0 km", kmDistance(GeoEstimate.haversineMeters(37.5, 127.0, 37.5, 127.0)))
        val moved = straightLineLabel(37.5, 127.0, 37.51, 127.0)
        assertTrue(moved.endsWith(" km"))
        assertEquals(moved, kmDistance(GeoEstimate.haversineMeters(37.5, 127.0, 37.51, 127.0)))
    }

    @Test
    fun orderIsOriginThenDestinationThenRecents() {
        val dest = NearbyPlace(PLACE_DEST_ID, "서울역", "서울 중구", 37.55, 126.97)
        val recent = NearbyPlace("recent:1", "역삼 영업소", "최근 목적지", 37.50, 127.03)
        val origin = RentalOrigin(37.5, 127.0, "수정구 시흥동", pending = false, seenSerial = 1)
        val rows = assembleReturnPlaces(
            originRow(origin, 37.5, 127.0),
            dest,
            listOf(recent),
            selected = null,
            hereLatitude = 37.5,
            hereLongitude = 127.0,
            demo = demo,
        )
        assertEquals(listOf("대여한 곳", "서울역", "역삼 영업소"), rows.map { it.name })
        assertFalse(rows.any { it.id == "daechi" || it.id == "pangyo" })
        assertTrue(rows.first().distance!!.endsWith(" km"))
    }

    @Test
    fun demoRowsFollowTheOriginOnlyWhenNothingRealDoes() {
        val rows = assembleReturnPlaces(
            originRow(RentalOrigin(pending = true, seenSerial = 1), null, null),
            destination = null,
            recents = emptyList(),
            selected = null,
            hereLatitude = null,
            hereLongitude = null,
            demo = demo,
        )
        assertEquals(F2Draft.PLACE_PICKUP, rows.first().id)
        assertEquals("대여한 곳", rows.first().name)
        assertEquals(ORIGIN_PENDING, rows.first().caption)
        assertEquals(listOf("대치동 공영주차장", "역삼 영업소"), rows.drop(1).map { it.name })
    }

    @Test
    fun aBookmarkRowUsesNameThenAddressThenCoordinates() {
        assertEquals("집", bookmarkRowLabel(" 집 ", "서울", 37.1, 127.1))
        assertEquals("서울시 강남구", bookmarkRowLabel("  ", "서울시 강남구", 37.1, 127.1))
        assertEquals("37.5,127.0", bookmarkRowLabel(null, "", 37.5, 127.0))
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
