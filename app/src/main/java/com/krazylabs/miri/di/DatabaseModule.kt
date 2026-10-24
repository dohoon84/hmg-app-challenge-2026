package com.krazylabs.miri.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.krazylabs.miri.data.cache.CabinCache
import com.krazylabs.miri.data.cache.RoomCabinCache
import com.krazylabs.miri.data.local.CabinDao
import com.krazylabs.miri.data.local.FoundationDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoundationDatabase =
        Room.databaseBuilder(context, FoundationDatabase::class.java, "foundation.db")
            // Phase 0 rows are a disposable cache. A version bump wipes foundation.db.
            // Replace this with explicit migrations before any durable idea data is stored.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun cabinDao(database: FoundationDatabase): CabinDao = database.cabinDao()

    @Provides
    @Singleton
    fun cabinCache(dao: CabinDao): CabinCache = RoomCabinCache(dao)
}
