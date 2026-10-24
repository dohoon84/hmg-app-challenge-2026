package io.hmg.appchallenge.foundation.speech

import android.media.MediaPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RawClipPlayerTest {
    @After
    fun resetPlayer() {
        ShadowMediaPlayer.resetStaticState()
    }

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

    @Test
    fun completionInvokesOnFinishedOnce() {
        val shadow = playChime()
        val finished = finishedCount
        shadow.invokeCompletionListener()
        assertEquals(finished + 1, finishedCount)
        shadow.invokeCompletionListener()
        assertEquals(finished + 1, finishedCount)
    }

    @Test
    fun errorInvokesOnFinishedOnce() {
        val shadow = playChime()
        shadow.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(1, finishedCount)
        shadow.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(1, finishedCount)
    }

    private var finishedCount = 0

    private fun playChime(): ShadowMediaPlayer {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_000, 0) }
        var created: MediaPlayer? = null
        ShadowMediaPlayer.setCreateListener { player, _ -> created = player }
        finishedCount = 0
        val player = RawClipPlayer(RuntimeEnvironment.getApplication())
        player.play { finishedCount += 1 }
        val mediaPlayer = created
        assertNotNull(mediaPlayer)
        assertEquals(0, finishedCount)
        return Shadows.shadowOf(mediaPlayer)
    }
}
