package com.krazylabs.miri.data.cache

import com.krazylabs.miri.data.local.CabinDao
import com.krazylabs.miri.data.local.NavCacheEntity
import com.krazylabs.miri.data.local.SINGLETON_ID
import com.krazylabs.miri.data.local.VehicleCacheEntity
import com.krazylabs.miri.domain.ArrivalSnapshot
import com.krazylabs.miri.domain.DestinationSnapshot
import com.krazylabs.miri.domain.Motion
import com.krazylabs.miri.domain.TripEstimate
import com.krazylabs.miri.domain.VehicleSnapshot
import com.krazylabs.miri.domain.maskVin
import kotlinx.coroutines.flow.Flow

interface CabinCache {
    fun observeVehicle(): Flow<VehicleCacheEntity?>
    fun observeNav(): Flow<NavCacheEntity?>
    suspend fun saveVehicle(snapshot: VehicleSnapshot, motion: Motion)
    suspend fun saveNavigation(
        destination: DestinationSnapshot?,
        trip: TripEstimate?,
        arrival: ArrivalSnapshot?,
        updatedAtEpochMs: Long,
    )
}

class RoomCabinCache(
    private val dao: CabinDao,
) : CabinCache {
    override fun observeVehicle(): Flow<VehicleCacheEntity?> = dao.observeVehicle()

    override fun observeNav(): Flow<NavCacheEntity?> = dao.observeNav()

    override suspend fun saveVehicle(snapshot: VehicleSnapshot, motion: Motion) {
        dao.upsertVehicle(
            VehicleCacheEntity(
                singletonId = SINGLETON_ID,
                updatedAtEpochMs = snapshot.updatedAtEpochMs,
                socPercent = snapshot.socPercent,
                fuelPercent = snapshot.fuelPercent,
                odometerKm = snapshot.odometerKm,
                rangeMeters = snapshot.rangeMeters,
                rangeSource = snapshot.rangeSource.name,
                vin = maskVin(snapshot.vin),
                motion = motion.name,
                openDoorCount = snapshot.doors.values.count { it.open },
            ),
        )
    }

    override suspend fun saveNavigation(
        destination: DestinationSnapshot?,
        trip: TripEstimate?,
        arrival: ArrivalSnapshot?,
        updatedAtEpochMs: Long,
    ) {
        dao.upsertNav(
            NavCacheEntity(
                singletonId = SINGLETON_ID,
                updatedAtEpochMs = updatedAtEpochMs,
                destinationName = destination?.name,
                latitude = destination?.latitude,
                longitude = destination?.longitude,
                remainDistance = destination?.remainDistance,
                remainTime = destination?.remainTime,
                tripDistance = trip?.distance,
                tripEta = trip?.eta,
                estimatedSoc = trip?.estimatedSocAtDestPercent,
                arrivalName = arrival?.name,
                arrivalAtEpochMs = arrival?.arrivedAtEpochMs,
            ),
        )
    }
}
