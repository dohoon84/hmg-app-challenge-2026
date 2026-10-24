package com.krazylabs.miri.data

import androidx.room.Room
import com.krazylabs.miri.data.cache.RoomCabinCache
import com.krazylabs.miri.data.local.FoundationDatabase
import com.krazylabs.miri.domain.Motion
import com.krazylabs.miri.domain.VehicleSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CabinDaoTest {
    @Test
    fun upsertStoresAMaskedVin() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, FoundationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val cache = RoomCabinCache(database.cabinDao())
            cache.saveVehicle(
                VehicleSnapshot(
                    vin = "KMHXXXX1234567890",
                    vinReadable = true,
                    updatedAtEpochMs = 10L,
                ),
                Motion.PARKED,
            )
            val stored = cache.observeVehicle().first()
            assertEquals("*************7890", stored?.vin)
            assertEquals(Motion.PARKED.name, stored?.motion)
            assertEquals(10L, stored?.updatedAtEpochMs)
        } finally {
            database.close()
        }
    }
}
