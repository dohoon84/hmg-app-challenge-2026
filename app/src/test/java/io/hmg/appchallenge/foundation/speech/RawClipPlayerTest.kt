package io.hmg.appchallenge.foundation.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RawClipPlayerTest {
    @Test
    fun missingClipFinishesOncePerPlay() {
        val player = RawClipPlayer(RuntimeEnvironment.getApplication(), clipResId = 0)
        var finished = 0
        player.play { finished += 1 }
        player.play { finished += 1 }
        assertEquals(2, finished)
        player.stop()
        assertEquals(2, finished)
    }
}
