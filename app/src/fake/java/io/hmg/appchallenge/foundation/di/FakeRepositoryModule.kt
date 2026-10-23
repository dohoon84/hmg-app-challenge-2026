package io.hmg.appchallenge.foundation.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.hmg.appchallenge.foundation.domain.DistractionPolicy
import io.hmg.appchallenge.foundation.domain.StaticDistractionPolicy
import io.hmg.appchallenge.foundation.driving.DrivingStateRepository
import io.hmg.appchallenge.foundation.driving.FakeDrivingStateRepository
import io.hmg.appchallenge.foundation.navi.FakeNavigationRepository
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.sim.CabinSimulator
import io.hmg.appchallenge.foundation.sim.FakeCabinWorld
import io.hmg.appchallenge.foundation.vehicle.FakeVehicleRepository
import io.hmg.appchallenge.foundation.vehicle.VehicleRepository
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
