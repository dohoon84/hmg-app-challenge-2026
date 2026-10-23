package io.hmg.appchallenge.foundation.perf

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import io.hmg.appchallenge.foundation.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only elapsed time from process start to the first drawn frame.
 * [Process.getStartElapsedRealtime] includes zygote fork and bindApplication.
 * The callback is the next Choreographer frame, then [Activity.reportFullyDrawn].
 * Release builds keep the call and do not log.
 */
object LaunchTiming {
    private val reported = AtomicBoolean(false)

    fun onFirstDraw(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val started = Process.getStartElapsedRealtime()
        Choreographer.getInstance().postFrameCallback {
            if (!reported.compareAndSet(false, true)) return@postFrameCallback
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "first draw ${elapsed}ms budget ${PerformanceBudget.LAUNCH_MS}ms")
            try {
                activity.reportFullyDrawn()
            } catch (error: Throwable) {
                Log.w(TAG, "reportFullyDrawn", error)
            }
        }
    }

    private const val TAG = "LaunchTiming"
}
