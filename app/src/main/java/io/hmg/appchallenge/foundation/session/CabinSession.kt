package io.hmg.appchallenge.foundation.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.hmg.appchallenge.foundation.driving.DrivingStateRepository
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.vehicle.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts Vehicle, Car, and NaviHelper while the process is in the foreground
 * and releases them on stop so a later start is a new session.
 */
@Singleton
class CabinSession @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val drivingStateRepository: DrivingStateRepository,
    private val navigationRepository: NavigationRepository,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        vehicleRepository.start()
        drivingStateRepository.start()
        navigationRepository.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        navigationRepository.stop()
        vehicleRepository.stop()
        drivingStateRepository.stop()
    }
}
