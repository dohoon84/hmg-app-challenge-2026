package com.krazylabs.miri.di

import com.krazylabs.miri.mvp.DemoPlayback
import com.krazylabs.miri.mvp.IdlePlayback
import com.krazylabs.miri.vehicle.CabinOverride
import com.krazylabs.miri.vehicle.LiveCabin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveCabinModule {
    @Binds
    @Singleton
    abstract fun cabin(impl: LiveCabin): CabinOverride

    @Binds
    @Singleton
    abstract fun playback(impl: IdlePlayback): DemoPlayback
}
