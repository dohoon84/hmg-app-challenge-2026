package io.hmg.appchallenge.foundation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.hmg.appchallenge.foundation.driving.DrivingStateRepository
import io.hmg.appchallenge.foundation.driving.PleosDrivingStateRepository
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.navi.PleosNavigationRepository
import io.hmg.appchallenge.foundation.sim.CabinSimulator
import io.hmg.appchallenge.foundation.sim.DisabledCabinSimulator
import io.hmg.appchallenge.foundation.vehicle.PleosVehicleRepository
import io.hmg.appchallenge.foundation.vehicle.VehicleRepository
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
