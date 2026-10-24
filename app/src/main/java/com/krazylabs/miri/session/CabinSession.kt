package com.krazylabs.miri.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.speech.CabinAnnouncer
import com.krazylabs.miri.vehicle.VehicleRepository
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
    private val announcer: CabinAnnouncer,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        announcer.onForeground()
        vehicleRepository.start()
        drivingStateRepository.start()
        navigationRepository.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        announcer.onSuspend()
        navigationRepository.stop()
        vehicleRepository.stop()
        drivingStateRepository.stop()
    }
}
