package com.krazylabs.miri.perf

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.krazylabs.miri.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Elapsed time from process start to the first drawn frame.
 * [Process.getStartElapsedRealtime] includes zygote fork and bindApplication.
 * The callback is the next Choreographer frame, about one frame before the first
 * draw has fully completed. [Activity.reportFullyDrawn] runs in debug and release
 * so a release Macrobenchmark can see fully drawn. The elapsed log is debug-only.
 */
object LaunchTiming {
    private val reported = AtomicBoolean(false)

    fun onFirstDraw(activity: Activity) {
        val started = Process.getStartElapsedRealtime()
        Choreographer.getInstance().postFrameCallback {
            if (!reported.compareAndSet(false, true)) return@postFrameCallback
            report(activity, SystemClock.elapsedRealtime() - started)
        }
    }

    private fun report(activity: Activity, elapsedMs: Long) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "first draw ${elapsedMs}ms budget ${PerformanceBudget.LAUNCH_MS}ms")
        }
        try {
            activity.reportFullyDrawn()
        } catch (error: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "reportFullyDrawn", error)
            }
        }
    }

    private const val TAG = "LaunchTiming"
}
