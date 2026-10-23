package io.hmg.appchallenge.foundation.perf

import android.os.SystemClock
import android.util.Log
import io.hmg.appchallenge.foundation.BuildConfig

/**
 * Debug-only elapsed time from [markProcessStart] to the first composed frame.
 * Release builds keep the calls and do not log.
 */
object LaunchTiming {
    private var processStartMs = 0L

    fun markProcessStart() {
        if (!BuildConfig.DEBUG) return
        processStartMs = SystemClock.elapsedRealtime()
    }

    fun markFirstFrame() {
        if (!BuildConfig.DEBUG) return
        val started = processStartMs
        if (started == 0L) return
        processStartMs = 0L
        val elapsed = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "first frame ${elapsed}ms budget ${PerformanceBudget.LAUNCH_MS}ms")
    }

    private const val TAG = "LaunchTiming"
}
