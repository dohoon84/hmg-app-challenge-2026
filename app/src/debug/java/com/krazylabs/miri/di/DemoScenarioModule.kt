package com.krazylabs.miri.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.mvp.DemoScenarioStore
import com.krazylabs.miri.mvp.ScenarioInput
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoScenarioModule {
    @Binds
    @Singleton
    abstract fun scenario(impl: DemoScenarioStore): ScenarioInput
}
