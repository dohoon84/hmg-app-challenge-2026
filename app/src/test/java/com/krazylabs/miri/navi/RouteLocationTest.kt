package com.krazylabs.miri.navi

import com.krazylabs.miri.domain.CarIdentity
import com.krazylabs.miri.domain.FuelKind
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.PortSide
import com.krazylabs.miri.mvp.CardSession
import com.krazylabs.miri.mvp.CheckItem
import com.krazylabs.miri.mvp.CheckKind
import com.krazylabs.miri.mvp.CheckRules
import com.krazylabs.miri.mvp.D2Rules
import com.krazylabs.miri.mvp.DeckInput
import com.krazylabs.miri.mvp.DestinationRole
import com.krazylabs.miri.mvp.MiriPane
import com.krazylabs.miri.mvp.RentalOrigin
import com.krazylabs.miri.mvp.TodayRoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLocationTest {
    @Test
    fun emptyRouteThenTwoRealRoutesShowD1WithoutPretendOrTheOrigin() {
        val navi = TrackedLocation()
        navi.markReady(0)
        navi.requestOrigin()
        assertFalse(navi.beginRouteRead())
        assertTrue(navi.shouldIssueOrigin(LocationRequestGate.LOCATION_AFTER_READY_MS))
        navi.markOriginIssued()
        assertEquals(
            LocationKind.ORIGIN,
            navi.onCurrentLocation("", 37.50, 127.02, "대여한 곳"),
        )
        assertEquals(37.50, navi.originFix.value!!.latitude, 0.0)
        assertEquals(1L, navi.hereSerial.value)

        assertTrue(navi.beginRouteRead())
        assertEquals(
            LocationKind.ROUTE,
            navi.onCurrentLocation("", 37.51, 127.01, "이동"),
        )
        assertEquals("", navi.road.value.routeId)
        assertEquals(37.51, navi.here.value!!.latitude, 0.0)
        assertEquals(37.50, navi.originFix.value!!.latitude, 0.0)
        assertEquals(1L, navi.hereSerial.value)
        assertNull(
            TodayRoad.face(
                routeId = navi.road.value.routeId,
                routing = true,
                tolls = 2,
                highways = 1,
                pretendTolls = null,
                pretendHighways = null,
            ),
        )

        val held = CardSession()
        val byRoute = D2Rules.offer(ev(), DestinationRole.CHARGING, routeKey = "route-9")!!
            .copy(routeId = "route-9")
        val latePlace = byRoute.copy(routeKey = "다른 충전소@37.5100,127.0400", routeId = "")
        assertEquals(
            MiriPane.CHECK,
            held.step(
                park(0).copy(
                    d2 = byRoute,
                    checkItems = listOf(CheckItem(CheckKind.DOOR, "오른쪽 뒷문이 열려 있어요.")),
                    kind = "rental",
                    returnSaved = true,
                ),
            ).pane,
        )
        held.step(drive().copy(d2 = latePlace))
        assertEquals(
            MiriPane.D2,
            held.step(park(1_000).copy(d2 = latePlace, kind = "rental", returnSaved = true)).pane,
        )

        val session = CardSession()
        val alpha = emit(navi, "alpha", 37.60)
        assertEquals("alpha", alpha.routeId)
        assertNotEquals("pretend", alpha.routeId)
        assertEquals(MiriPane.D1, session.step(park(0).copy(d1 = alpha)).pane)
        session.step(drive())
        val beta = emit(navi, "beta", 37.70)
        assertEquals("beta", beta.routeId)
        assertEquals(MiriPane.D1, session.step(park(8_000).copy(d1 = beta)).pane)

        var origin = RentalOrigin().begin(currentSerial = 0L, nowMs = 0L)
        val fix = navi.originFix.value!!
        origin = origin.arrive(navi.hereSerial.value, fix.latitude, fix.longitude, fix.address)
        assertTrue(origin.located)
        val here = navi.here.value!!
        origin = origin.arrive(navi.hereSerial.value, here.latitude, here.longitude, here.address)
        assertEquals(37.50, origin.latitude)
        assertEquals(37.70, here.latitude, 0.0)
    }

    @Test
    fun reconnectDropsTheStaleFixAndWaitsThreeSeconds() {
        val navi = TrackedLocation()
        navi.markReady(1_000)
        navi.requestOrigin()
        navi.markOriginIssued()
        navi.markReady(20_000)
        assertNull(navi.onCurrentLocation("stale", 37.10, 127.10, "옛 길"))
        assertNull(navi.originFix.value)
        assertEquals("", navi.road.value.routeId)
        assertFalse(navi.shouldIssueOrigin(20_000 + 2_999))
        assertTrue(navi.shouldIssueOrigin(20_000 + LocationRequestGate.LOCATION_AFTER_READY_MS))
        navi.markOriginIssued()
        assertEquals(
            LocationKind.ORIGIN,
            navi.onCurrentLocation("", 37.50, 127.02, "대여한 곳"),
        )
        assertEquals(37.50, navi.originFix.value!!.latitude, 0.0)
    }

    private fun emit(navi: TrackedLocation, routeId: String, latitude: Double) =
        run {
            assertTrue(navi.beginRouteRead())
            assertEquals(LocationKind.ROUTE, navi.onCurrentLocation(routeId, latitude, 127.10, "도로"))
            navi.noteCounts(routing = true, tolls = 2, highways = 1)
            val road = navi.road.value
            TodayRoad.face(
                routeId = road.routeId,
                routing = road.routing,
                tolls = road.tolls,
                highways = road.highways,
                pretendTolls = null,
                pretendHighways = null,
            )!!
        }

    private fun ev() = CarIdentity(
        fuels = listOf(FuelKind.ELECTRIC),
        evPort = PortSide.REAR_RIGHT,
        connectorDcCombo = true,
    )

    private fun park(now: Long) = deck(GearPosition.PARK, now)

    private fun drive() = deck(GearPosition.DRIVE, 0)

    private fun deck(gear: GearPosition, now: Long) = DeckInput(
        gear = gear,
        visible = true,
        edgesOpen = true,
        ownCar = false,
        kind = null,
        returnSaved = false,
        showIntake = false,
        checkItems = emptyList(),
        checkNote = null,
        checkTitle = CheckRules.TITLE,
        reshowCheck = false,
        f4 = null,
        d2 = null,
        f3 = null,
        d1 = null,
        nowMs = now,
        cardMs = 6_000L,
        showGuide = false,
    )
}
