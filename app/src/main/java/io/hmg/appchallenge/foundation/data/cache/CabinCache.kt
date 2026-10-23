package io.hmg.appchallenge.foundation.data.cache

import io.hmg.appchallenge.foundation.data.local.CabinDao
import io.hmg.appchallenge.foundation.data.local.NavCacheEntity
import io.hmg.appchallenge.foundation.data.local.SINGLETON_ID
import io.hmg.appchallenge.foundation.data.local.VehicleCacheEntity
import io.hmg.appchallenge.foundation.domain.ArrivalSnapshot
import io.hmg.appchallenge.foundation.domain.DestinationSnapshot
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.TripEstimate
import io.hmg.appchallenge.foundation.domain.VehicleSnapshot
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
                vin = snapshot.vin,
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
