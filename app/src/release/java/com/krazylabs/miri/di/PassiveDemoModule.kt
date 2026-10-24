package com.krazylabs.miri.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.mvp.DemoActions
import com.krazylabs.miri.mvp.PassiveDemo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PassiveDemoModule {
    @Binds
    @Singleton
    abstract fun demo(impl: PassiveDemo): DemoActions
}
