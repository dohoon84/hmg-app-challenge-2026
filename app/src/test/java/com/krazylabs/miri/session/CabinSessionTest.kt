package com.krazylabs.miri.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.DrivingSnapshot
import com.krazylabs.miri.domain.LinkState
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.TripEstimate
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.speech.CabinAnnouncer
import com.krazylabs.miri.vehicle.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CabinSessionTest {
    @Test
    fun startStopStartReentersEveryRepository() {
        val vehicle = CountingVehicle()
        val driving = CountingDriving()
        val navigation = CountingNavigation()
        val announcer = CountingAnnouncer()
        val session = CabinSession(vehicle, driving, navigation, announcer)
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = error("unused")
        }

        session.onStart(owner)
        session.onStop(owner)
        session.onStart(owner)

        assertEquals(2, vehicle.starts)
        assertEquals(1, vehicle.stops)
        assertEquals(2, driving.starts)
        assertEquals(1, driving.stops)
        assertEquals(2, navigation.starts)
        assertEquals(1, navigation.stops)
        assertEquals(2, announcer.foregrounds)
        assertEquals(1, announcer.suspends)
    }

    private class CountingVehicle : VehicleRepository {
        var starts = 0
        var stops = 0
        override val link: StateFlow<LinkState> = MutableStateFlow(LinkState.IDLE)
        override val snapshot: StateFlow<VehicleSnapshot> = MutableStateFlow(VehicleSnapshot())
        override fun start() {
            starts += 1
        }
        override fun stop() {
            stops += 1
        }
        override fun refresh() = Unit
    }

    private class CountingDriving : DrivingStateRepository {
        var starts = 0
        var stops = 0
        override val state: StateFlow<DrivingSnapshot> = MutableStateFlow(DrivingSnapshot())
        override fun start() {
            starts += 1
        }
        override fun stop() {
            stops += 1
        }
    }

    private class CountingNavigation : NavigationRepository {
        var starts = 0
        var stops = 0
        override val link: StateFlow<LinkState> = MutableStateFlow(LinkState.IDLE)
        override val destination: StateFlow<DestinationSnapshot?> = MutableStateFlow(null)
        override val arrival: StateFlow<ArrivalSnapshot?> = MutableStateFlow(null)
        override val trip: StateFlow<TripEstimate?> = MutableStateFlow(null)
        override val statusMessage: StateFlow<String?> = MutableStateFlow(null)
        override fun start() {
            starts += 1
        }
        override fun stop() {
            stops += 1
        }
        override fun setDestination(request: PlaceRequest) = Unit
        override fun requestTrip(request: PlaceRequest) = Unit
        override fun refreshDestination() = Unit
    }

    private class CountingAnnouncer : CabinAnnouncer {
        var foregrounds = 0
        var suspends = 0
        override fun announce(text: String) = Unit
        override fun stop() = Unit
        override fun onSuspend() {
            suspends += 1
        }
        override fun onForeground() {
            foregrounds += 1
        }
    }
}
