package com.krazylabs.miri.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.domain.DistractionPolicy
import com.krazylabs.miri.domain.StaticDistractionPolicy
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.driving.FakeDrivingStateRepository
import com.krazylabs.miri.navi.FakeNavigationRepository
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.sim.CabinSimulator
import com.krazylabs.miri.sim.FakeCabinWorld
import com.krazylabs.miri.vehicle.FakeVehicleRepository
import com.krazylabs.miri.vehicle.VehicleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FakeRepositoryModule {
    @Binds
    @Singleton
    abstract fun vehicle(impl: FakeVehicleRepository): VehicleRepository

    @Binds
    @Singleton
    abstract fun driving(impl: FakeDrivingStateRepository): DrivingStateRepository

    @Binds
    @Singleton
    abstract fun navigation(impl: FakeNavigationRepository): NavigationRepository

    @Binds
    @Singleton
    abstract fun simulator(impl: FakeCabinWorld): CabinSimulator
}

@Module
@InstallIn(SingletonComponent::class)
object FakePolicyModule {
    @Provides
    fun distractionPolicy(): DistractionPolicy = StaticDistractionPolicy(failClosed = false)
}
