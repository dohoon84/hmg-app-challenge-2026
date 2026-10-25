package com.krazylabs.miri.di

import com.krazylabs.miri.mvp.AutoPlayDirector
import com.krazylabs.miri.mvp.DemoPlayback
import com.krazylabs.miri.mvp.Filming
import com.krazylabs.miri.mvp.FilmingController
import com.krazylabs.miri.vehicle.CabinOverride
import com.krazylabs.miri.vehicle.ScriptedCabinFeed
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoCabinModule {
    @Binds
    @Singleton
    abstract fun cabin(impl: ScriptedCabinFeed): CabinOverride

    @Binds
    @Singleton
    abstract fun playback(impl: AutoPlayDirector): DemoPlayback

    @Binds
    @Singleton
    abstract fun filming(impl: FilmingController): Filming
}
