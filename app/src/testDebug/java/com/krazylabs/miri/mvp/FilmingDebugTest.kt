package com.krazylabs.miri.mvp

import android.content.Intent
import com.krazylabs.miri.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilmingDebugTest {
    @Test
    fun theDebugBuildShipsTheFilmingFlag() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(BuildConfig.FILMING)
    }

    @Test
    fun stepIdsMatchTheAutoPlayBeats() {
        assertEquals(FilmingCommands.steps, AutoPlayScript.beats())
        assertTrue(AutoPlayScript.beats().contains("C3"))
    }

    @Test
    fun theReceiverReadsTheBroadcastExtras() {
        val intent = Intent(FilmingCommands.ACTION_AUTOPLAY)
            .putExtra(FilmingCommands.EXTRA_START, true)
            .putExtra(FilmingCommands.EXTRA_STEP, "C3")
            .putExtra(FilmingCommands.EXTRA_HOLD, 1.5f)
        assertEquals(
            FilmingCommand.Play("C3", 1.5f),
            FilmingCommands.parse(intent.action, extrasOf(intent)),
        )

        val off = Intent(FilmingCommands.ACTION_FILMING).putExtra(FilmingCommands.EXTRA_ON, false)
        assertEquals(FilmingCommand.Set(false), FilmingCommands.parse(off.action, extrasOf(off)))

        val missing = Intent(FilmingCommands.ACTION_FILMING)
        assertEquals(FilmingCommand.Ignore, FilmingCommands.parse(missing.action, extrasOf(missing)))
    }
}
