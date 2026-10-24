package com.krazylabs.miri.mvp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only control for filming and auto-play. No button, no toast, no
 * permission. The release source set does not declare this receiver.
 */
class FilmingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FilmingBus.dispatch(FilmingCommands.parse(intent?.action, extrasOf(intent)))
    }
}

internal fun extrasOf(intent: Intent?): FilmingExtras {
    val extras = intent?.extras ?: return FilmingExtras()
    return FilmingExtras(
        on = if (extras.containsKey(FilmingCommands.EXTRA_ON)) extras.getBoolean(FilmingCommands.EXTRA_ON) else null,
        start = if (extras.containsKey(FilmingCommands.EXTRA_START)) {
            extras.getBoolean(FilmingCommands.EXTRA_START)
        } else {
            null
        },
        step = extras.getString(FilmingCommands.EXTRA_STEP),
        hold = if (extras.containsKey(FilmingCommands.EXTRA_HOLD)) extras.getFloat(FilmingCommands.EXTRA_HOLD) else null,
    )
}
