package io.hmg.appchallenge.foundation.navi

import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.TripEstimate
import kotlinx.coroutines.flow.StateFlow

interface NavigationRepository {
    val link: StateFlow<LinkState>
    val destination: StateFlow<DestinationSnapshot?>
    val arrival: StateFlow<ArrivalSnapshot?>
    val trip: StateFlow<TripEstimate?>
    val statusMessage: StateFlow<String?>

    fun start()
    fun stop()
    fun setDestination(request: PlaceRequest)
    fun requestTrip(request: PlaceRequest)
    fun refreshDestination()
}
