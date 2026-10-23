package io.hmg.appchallenge.foundation.ui

import androidx.lifecycle.SavedStateHandle
import io.hmg.appchallenge.foundation.data.cache.CabinCache
import io.hmg.appchallenge.foundation.data.local.NavCacheEntity
import io.hmg.appchallenge.foundation.data.local.VehicleCacheEntity
import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.DistractionPolicy
import io.hmg.appchallenge.foundation.domain.DrivingSnapshot
import io.hmg.appchallenge.foundation.domain.GearPosition
import io.hmg.appchallenge.foundation.domain.LinkState
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.PlaceRequest
import io.hmg.appchallenge.foundation.domain.StaticDistractionPolicy
import io.hmg.appchallenge.foundation.domain.TripEstimate
import io.hmg.appchallenge.foundation.domain.UxRestrictionBits
import io.hmg.appchallenge.foundation.domain.UxRestrictionState
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
import io.hmg.appchallenge.foundation.driving.DrivingStateRepository
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.sim.CabinSimulator
import io.hmg.appchallenge.foundation.vehicle.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationViewModelTest {
    @Test
    fun defaultUiFailsClosedWithoutBlankingShortCopy() {
        val ui = FoundationUi()
        assertFalse(ui.keyboardAllowed)
        assertTrue(ui.restrictions.limitStringLength)
        assertEquals("SOC 80%", ui.restrictions.clip("SOC 80%"))
        assertEquals("문 열림", ui.restrictions.clip("문 열림", preserve = true))
    }

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModelDoesNotStartRepositories() = runTest(dispatcher) {
        val vehicle = IdleVehicle()
        val viewModel = viewModel(vehicle, IdleDriving(), failClosed = true)
        val job = launch { viewModel.ui.collect {} }
        advanceUntilIdle()
        assertEquals(0, vehicle.starts)
        assertEquals(AppShell.REDUCED, viewModel.ui.value.shell)
        job.cancel()
    }

    @Test
    fun failClosedUnknownHidesTheFullShellAndKeyboard() = runTest(dispatcher) {
        val driving = IdleDriving()
        val viewModel = viewModel(IdleVehicle(), driving, failClosed = true)
        val job = launch { viewModel.ui.collect {} }
        advanceUntilIdle()
        assertEquals(AppShell.REDUCED, viewModel.ui.value.shell)
        assertFalse(viewModel.ui.value.keyboardAllowed)
        assertTrue(viewModel.ui.value.restrictions.noKeyboard)
        assertTrue(viewModel.ui.value.restrictions.noVideo)
        assertTrue(viewModel.ui.value.restrictions.limitStringLength)
        assertEquals("긴 문장", viewModel.ui.value.restrictions.clip("긴 문장"))
        assertEquals(
            UxRestrictionState.UNKNOWN_MAX_STRING_LENGTH,
            viewModel.ui.value.restrictions.maxRestrictedStringLength,
        )
        assertEquals("문 열림", viewModel.ui.value.restrictions.clip("문 열림", preserve = true))

        driving.state.value = DrivingSnapshot(
            gear = GearPosition.PARK,
            gearAvailable = true,
            uxAvailable = true,
            motion = Motion.PARKED,
        )
        advanceUntilIdle()
        assertEquals(AppShell.FULL, viewModel.ui.value.shell)
        assertTrue(viewModel.ui.value.keyboardAllowed)
        assertFalse(viewModel.ui.value.restrictions.noVideo)

        driving.state.value = driving.state.value.copy(
            restrictionMask = UxRestrictionBits.NO_KEYBOARD,
        )
        advanceUntilIdle()
        assertEquals(AppShell.FULL, viewModel.ui.value.shell)
        assertFalse(viewModel.ui.value.keyboardAllowed)
        assertFalse(viewModel.ui.value.restrictions.noVideo)
        assertTrue(viewModel.ui.value.restrictions.noKeyboard)
        job.cancel()
    }

    @Test
    fun invalidCoordinateSetsFormError() = runTest(dispatcher) {
        val viewModel = viewModel(IdleVehicle(), IdleDriving(), failClosed = false)
        val job = launch { viewModel.ui.collect {} }
        viewModel.submitDestination("nope", "1", "x", asTrip = false)
        advanceUntilIdle()
        assertEquals("위도 -90~90, 경도 -180~180 사이의 숫자여야 합니다", viewModel.ui.value.formError)
        assertEquals(AppShell.FULL, viewModel.ui.value.shell)
        job.cancel()
    }

    @Test
    fun formErrorIsRestoredFromSavedState() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val viewModel = viewModel(IdleVehicle(), IdleDriving(), failClosed = false, handle)
        val job = launch { viewModel.ui.collect {} }
        viewModel.submitDestination("nope", "1", "x", asTrip = false)
        advanceUntilIdle()
        assertEquals("위도 -90~90, 경도 -180~180 사이의 숫자여야 합니다", handle.get<String>("formError"))
        job.cancel()

        val restored = viewModel(IdleVehicle(), IdleDriving(), failClosed = false, handle)
        val restoredJob = launch { restored.ui.collect {} }
        advanceUntilIdle()
        assertEquals("위도 -90~90, 경도 -180~180 사이의 숫자여야 합니다", restored.ui.value.formError)
        restoredJob.cancel()
    }

    private fun viewModel(
        vehicle: VehicleRepository,
        driving: DrivingStateRepository,
        failClosed: Boolean,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = FoundationViewModel(
        vehicleRepository = vehicle,
        drivingStateRepository = driving,
        navigationRepository = IdleNavigation(),
        cabinSimulator = IdleSimulator,
        cabinCache = NoopCache,
        distractionPolicy = StaticDistractionPolicy(failClosed),
        savedStateHandle = savedStateHandle,
    )

    private class IdleVehicle : VehicleRepository {
        var starts = 0
        override val link: StateFlow<LinkState> = MutableStateFlow(LinkState.IDLE)
        override val snapshot: StateFlow<VehicleSnapshot> = MutableStateFlow(VehicleSnapshot())
        override fun start() {
            starts += 1
        }
        override fun stop() = Unit
        override fun refresh() = Unit
    }

    private class IdleDriving : DrivingStateRepository {
        override val state = MutableStateFlow(DrivingSnapshot())
        override fun start() = Unit
        override fun stop() = Unit
    }

    private class IdleNavigation : NavigationRepository {
        override val link: StateFlow<LinkState> = MutableStateFlow(LinkState.IDLE)
        override val destination: StateFlow<DestinationSnapshot?> = MutableStateFlow(null)
        override val arrival: StateFlow<ArrivalSnapshot?> = MutableStateFlow(null)
        override val trip: StateFlow<TripEstimate?> = MutableStateFlow(null)
        override val statusMessage: StateFlow<String?> = MutableStateFlow(null)
        override fun start() = Unit
        override fun stop() = Unit
        override fun setDestination(request: PlaceRequest) = Unit
        override fun requestTrip(request: PlaceRequest) = Unit
        override fun refreshDestination() = Unit
    }

    private object IdleSimulator : CabinSimulator {
        override val enabled = false
        override fun setParked(parked: Boolean) = Unit
        override fun setDriverDoorOpen(open: Boolean) = Unit
        override fun nudgeSoc(delta: Int) = Unit
        override fun simulateArrival() = Unit
        override fun setSampleDestination() = Unit
        override fun requestSampleTrip() = Unit
    }

    private object NoopCache : CabinCache {
        override fun observeVehicle(): Flow<VehicleCacheEntity?> = flowOf(null)
        override fun observeNav(): Flow<NavCacheEntity?> = flowOf(null)
        override suspend fun saveVehicle(snapshot: VehicleSnapshot, motion: Motion) = Unit
        override suspend fun saveNavigation(
            destination: DestinationSnapshot?,
            trip: TripEstimate?,
            arrival: ArrivalSnapshot?,
            updatedAtEpochMs: Long,
        ) = Unit
    }
}
