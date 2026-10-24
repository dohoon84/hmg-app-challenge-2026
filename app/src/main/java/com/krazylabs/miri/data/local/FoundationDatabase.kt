package com.krazylabs.miri.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicle_cache")
data class VehicleCacheEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val updatedAtEpochMs: Long,
    val socPercent: Int?,
    val fuelPercent: Int?,
    val odometerKm: Float?,
    val rangeMeters: Float?,
    val rangeSource: String,
    val vin: String?,
    val motion: String,
    val openDoorCount: Int,
)

@Entity(tableName = "nav_cache")
data class NavCacheEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val updatedAtEpochMs: Long,
    val destinationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val remainDistance: Int?,
    val remainTime: Int?,
    val tripDistance: Int?,
    val tripEta: Int?,
    val estimatedSoc: Int?,
    val arrivalName: String?,
    val arrivalAtEpochMs: Long?,
)

const val SINGLETON_ID = 1

@Dao
interface CabinDao {
    @Upsert
    suspend fun upsertVehicle(entity: VehicleCacheEntity)

    @Query("SELECT * FROM vehicle_cache WHERE singletonId = :id")
    fun observeVehicle(id: Int = SINGLETON_ID): Flow<VehicleCacheEntity?>

    @Upsert
    suspend fun upsertNav(entity: NavCacheEntity)

    @Query("SELECT * FROM nav_cache WHERE singletonId = :id")
    fun observeNav(id: Int = SINGLETON_ID): Flow<NavCacheEntity?>
}

@Database(
    entities = [VehicleCacheEntity::class, NavCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FoundationDatabase : RoomDatabase() {
    abstract fun cabinDao(): CabinDao
}
