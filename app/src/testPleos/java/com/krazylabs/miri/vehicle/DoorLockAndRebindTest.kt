package com.krazylabs.miri.vehicle

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import com.krazylabs.miri.car.PleosCarClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DoorLockAndRebindTest {
    @Test
    fun combinedAreaAndAPerDoorAreaBothContainTheDriverBit() {
        val combined = DoorArea(
            DoorValue.ROW_1_LEFT,
            DoorValue.ROW_1_RIGHT,
            DoorValue.ROW_2_LEFT,
            DoorValue.ROW_2_RIGHT,
        )
        val hood = DoorArea(DoorValue.HOOD)
        val driver = DoorArea(DoorValue.ROW_1_LEFT)
        assertEquals(0x55, combined.driverBits())
        assertEquals(0x10000000, hood.driverBits())
        assertEquals(listOf(combined, driver), listOf(hood, combined, driver).driverLockAreas())
    }

    @Test
    fun driverBitRejectsTheHood() {
        assertTrue(DoorLockAreas.containsDriver(0x55))
        assertTrue(DoorLockAreas.containsDriver(0x01))
        assertTrue(!DoorLockAreas.containsDriver(0x10000000))
    }

    @Test
    fun aLaterCarReadyGenerationOpensTheVehicleListenersAgain() {
        val context = RuntimeEnvironment.getApplication()
        val client = PleosCarClient(context, null)
        val repo = PleosVehicleRepository(context, client)
        var opens = 0
        repo.reopenForTest = { opens += 1 }
        repo.acceptBind(1)
        repo.acceptBind(1)
        repo.acceptBind(2)
        assertEquals(2, opens)
    }
}
