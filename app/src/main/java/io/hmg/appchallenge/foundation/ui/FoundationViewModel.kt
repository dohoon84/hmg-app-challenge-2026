package io.hmg.appchallenge.foundation.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.hmg.appchallenge.foundation.data.cache.CabinCache
import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.DistractionPolicy
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.domain.KeyboardPolicy
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.PlaceParser
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.ShellPolicy
import io.hmg.appchallenge.foundation.domain.TripEstimate
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import io.hmg.appchallenge.foundation.driving.DrivingStateRepository
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.sim.CabinSimulator
import io.hmg.appchallenge.foundation.vehicle.VehicleRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoundationUi(
    val vehicleLink: LinkState = LinkState.IDLE,
    val navLink: LinkState = LinkState.IDLE,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val driving: DrivingSnapshot = DrivingSnapshot(),
    val shell: AppShell = AppShell.FULL,
    val keyboardAllowed: Boolean = true,
    val destination: DestinationSnapshot? = null,
    val arrival: ArrivalSnapshot? = null,
    val trip: TripEstimate? = null,
    val navStatus: String? = null,
    val formError: String? = null,
    val cachedSoc: Int? = null,
    val cachedAtEpochMs: Long = 0L,
    val simulatorEnabled: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val drivingStateRepository: DrivingStateRepository,
    private val navigationRepository: NavigationRepository,
    private val cabinSimulator: CabinSimulator,
    private val cabinCache: CabinCache,
    private val distractionPolicy: DistractionPolicy,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val formError = MutableStateFlow(savedStateHandle.get<String>(FORM_ERROR))

    val ui: StateFlow<FoundationUi> = combine(
        combine(
            vehicleRepository.link,
            vehicleRepository.snapshot,
            drivingStateRepository.state,
        ) { link, snapshot, driving ->
            VehicleSlice(link, snapshot, driving)
        },
        combine(
            navigationRepository.link,
            navigationRepository.destination,
            navigationRepository.arrival,
            navigationRepository.trip,
            navigationRepository.statusMessage,
        ) { link, destination, arrival, trip, status ->
            NavSlice(link, destination, arrival, trip, status)
        },
        cabinCache.observeVehicle(),
        formError,
    ) { vehicleSlice, navSlice, cache, error ->
        FoundationUi(
            vehicleLink = vehicleSlice.link,
            navLink = navSlice.link,
            vehicle = vehicleSlice.snapshot,
            driving = vehicleSlice.driving,
            shell = ShellPolicy.select(
                motion = vehicleSlice.driving.motion,
                uxAvailable = vehicleSlice.driving.uxAvailable,
                failClosed = distractionPolicy.failClosed,
            ),
            keyboardAllowed = KeyboardPolicy.allowsKeyboard(
                mask = vehicleSlice.driving.restrictionMask,
                uxAvailable = vehicleSlice.driving.uxAvailable,
                failClosed = distractionPolicy.failClosed,
            ),
            destination = navSlice.destination,
            arrival = navSlice.arrival,
            trip = navSlice.trip,
            navStatus = navSlice.status,
            formError = error,
            cachedSoc = cache?.socPercent,
            cachedAtEpochMs = cache?.updatedAtEpochMs ?: 0L,
            simulatorEnabled = cabinSimulator.enabled,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialUi(),
    )

    init {
        viewModelScope.launch {
            vehicleRepository.snapshot.debounce(500).collect { snapshot ->
                try {
                    cabinCache.saveVehicle(snapshot, drivingStateRepository.state.value.motion)
                } catch (error: Throwable) {
                    Log.w(TAG, "vehicle cache", error)
                }
            }
        }
        viewModelScope.launch {
            combine(
                navigationRepository.destination,
                navigationRepository.trip,
                navigationRepository.arrival,
            ) { destination, trip, arrival ->
                Triple(destination, trip, arrival)
            }.debounce(500).collect { (destination, trip, arrival) ->
                try {
                    cabinCache.saveNavigation(
                        destination = destination,
                        trip = trip,
                        arrival = arrival,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                } catch (error: Throwable) {
                    Log.w(TAG, "nav cache", error)
                }
            }
        }
    }

    fun setParked(parked: Boolean) = cabinSimulator.setParked(parked)

    fun toggleDriverDoor() {
        val open = vehicleRepository.snapshot.value.doors[DoorId.ROW_1_LEFT]?.open == true
        cabinSimulator.setDriverDoorOpen(!open)
    }

    fun nudgeSoc(delta: Int) = cabinSimulator.nudgeSoc(delta)

    fun simulateArrival() = cabinSimulator.simulateArrival()

    fun setSampleDestination() = cabinSimulator.setSampleDestination()

    fun requestSampleTrip() = cabinSimulator.requestSampleTrip()

    fun setDestination(request: PlaceRequest) {
        setFormError(null)
        navigationRepository.setDestination(request)
    }

    fun requestTrip(request: PlaceRequest) {
        setFormError(null)
        navigationRepository.requestTrip(request)
    }

    fun submitDestination(latitude: String, longitude: String, name: String, asTrip: Boolean) {
        val request = PlaceParser.parse(latitude, longitude, name)
        if (request == null) {
            setFormError("위도 -90~90, 경도 -180~180 사이의 숫자여야 합니다")
            return
        }
        setFormError(null)
        if (asTrip) navigationRepository.requestTrip(request) else navigationRepository.setDestination(request)
    }

    fun refreshDestination() = navigationRepository.refreshDestination()

    fun onEnergyPermissionResult(granted: Boolean) {
        if (granted) vehicleRepository.refresh()
    }

    private fun setFormError(value: String?) {
        formError.value = value
        if (value == null) {
            savedStateHandle.remove<String>(FORM_ERROR)
        } else {
            savedStateHandle[FORM_ERROR] = value
        }
    }

    private fun initialUi(): FoundationUi {
        val failClosed = distractionPolicy.failClosed
        return FoundationUi(
            shell = if (failClosed) AppShell.REDUCED else AppShell.FULL,
            keyboardAllowed = !failClosed,
            simulatorEnabled = cabinSimulator.enabled,
            formError = savedStateHandle.get<String>(FORM_ERROR),
        )
    }

    private data class VehicleSlice(
        val link: LinkState,
        val snapshot: VehicleSnapshot,
        val driving: DrivingSnapshot,
    )

    private data class NavSlice(
        val link: LinkState,
        val destination: DestinationSnapshot?,
        val arrival: ArrivalSnapshot?,
        val trip: TripEstimate?,
        val status: String?,
    )

    private companion object {
        const val TAG = "FoundationViewModel"
        const val FORM_ERROR = "formError"
    }
}
