package com.krazylabs.miri.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.mvp.PassiveScenario
import com.krazylabs.miri.mvp.ScenarioInput
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PassiveScenarioModule {
    @Binds
    @Singleton
    abstract fun scenario(impl: PassiveScenario): ScenarioInput
}
