package io.hmg.appchallenge.foundation.speech

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RawClipPlayerTest {
    @Test
    fun missingClipDoesNothing() {
        val player = RawClipPlayer(RuntimeEnvironment.getApplication(), clipResId = 0)
        player.play()
        player.stop()
    }
}
