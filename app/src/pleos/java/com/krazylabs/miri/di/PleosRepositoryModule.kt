package com.krazylabs.miri.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.domain.DistractionPolicy
import com.krazylabs.miri.domain.StaticDistractionPolicy
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.driving.PleosDrivingStateRepository
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.navi.PleosNavigationRepository
import com.krazylabs.miri.sim.CabinSimulator
import com.krazylabs.miri.sim.DisabledCabinSimulator
import com.krazylabs.miri.vehicle.PleosVehicleRepository
import com.krazylabs.miri.vehicle.VehicleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PleosRepositoryModule {
    @Binds
    @Singleton
    abstract fun vehicle(impl: PleosVehicleRepository): VehicleRepository

    @Binds
    @Singleton
    abstract fun driving(impl: PleosDrivingStateRepository): DrivingStateRepository

    @Binds
    @Singleton
    abstract fun navigation(impl: PleosNavigationRepository): NavigationRepository

    @Binds
    @Singleton
    abstract fun simulator(impl: DisabledCabinSimulator): CabinSimulator
}

@Module
@InstallIn(SingletonComponent::class)
object PleosPolicyModule {
    @Provides
    fun distractionPolicy(): DistractionPolicy = StaticDistractionPolicy(failClosed = true)
}
