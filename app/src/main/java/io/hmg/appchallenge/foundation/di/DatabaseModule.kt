package io.hmg.appchallenge.foundation.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.hmg.appchallenge.foundation.data.cache.CabinCache
import io.hmg.appchallenge.foundation.data.cache.RoomCabinCache
import io.hmg.appchallenge.foundation.data.local.CabinDao
import io.hmg.appchallenge.foundation.data.local.FoundationDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoundationDatabase =
        Room.databaseBuilder(context, FoundationDatabase::class.java, "foundation.db")
            .build()

    @Provides
    fun cabinDao(database: FoundationDatabase): CabinDao = database.cabinDao()

    @Provides
    @Singleton
    fun cabinCache(dao: CabinDao): CabinCache = RoomCabinCache(dao)
}
