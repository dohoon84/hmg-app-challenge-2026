package io.hmg.appchallenge.foundation.probe

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * After an R to P gear change, wait and ask the activity task to come forward.
 * Does not change the gear. REORDER_TASKS is declared only on the probe manifest.
 */
internal class FrontTaskProbe(
    private val log: ProbeLog,
    private val host: () -> Activity?,
) {
    private val main = Handler(Looper.getMainLooper())
    var enabled: Boolean = false
    var reorderToFront: Boolean = false

    private var lastScheduleElapsed = 0L
    private var attemptElapsed = 0L
    private var awaitingResume = false
    private var pending: Runnable? = null

    fun onReverseToPark(source: String, fromName: String, toName: String) {
        main.post { schedule(source, fromName, toName) }
    }

    fun onHostResume() {
        val elapsed = SystemClock.elapsedRealtime()
        val since = if (attemptElapsed > 0L) elapsed - attemptElapsed else null
        logEvent(
            "onResume",
            mapOf(
                "followedAttempt" to awaitingResume,
                "sinceAttemptMs" to since,
                "taskId" to host()?.taskId,
                "frontTest" to enabled,
            ),
        )
        awaitingResume = false
    }

    fun onHostPause() {
        logEvent(
            "onPause",
            mapOf(
                "taskId" to host()?.taskId,
                "frontTest" to enabled,
                "awaitingResume" to awaitingResume,
            ),
        )
    }

    fun release() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }

    private fun schedule(source: String, fromName: String, toName: String) {
        val now = SystemClock.elapsedRealtime()
        val base = mapOf(
            "source" to source,
            "from" to fromName,
            "to" to toName,
            "frontTest" to enabled,
            "reorderToFront" to reorderToFront,
        )
        if (!enabled) {
            logEvent("gear_transition", base + mapOf("result" to "skipped", "detail" to "frontTest is off"))
            return
        }
        if (lastScheduleElapsed != 0L && now - lastScheduleElapsed < DEBOUNCE_MS) {
            logEvent("gear_transition", base + mapOf("result" to "debounced", "sinceScheduleMs" to now - lastScheduleElapsed))
            return
        }
        lastScheduleElapsed = now
        pending?.let { main.removeCallbacks(it) }
        val task = Runnable { attempt(source, fromName, toName) }
        pending = task
        main.postDelayed(task, DELAY_MS)
        logEvent(
            "scheduled",
            base + mapOf("result" to "scheduled", "delayMs" to DELAY_MS),
        )
    }

    private fun attempt(source: String, fromName: String, toName: String) {
        pending = null
        val activity = host()
        val fields = mutableMapOf<String, Any?>(
            "source" to source,
            "from" to fromName,
            "to" to toName,
            "reorderToFront" to reorderToFront,
        )
        if (activity == null || activity.isDestroyed) {
            logEvent("moveTaskToFront", fields + mapOf("result" to "no_activity"))
            return
        }
        attemptElapsed = SystemClock.elapsedRealtime()
        awaitingResume = true
        fields["taskId"] = activity.taskId
        try {
            val manager = activity.getSystemService(ActivityManager::class.java)
            manager.moveTaskToFront(activity.taskId, 0)
            logEvent("moveTaskToFront", fields + mapOf("result" to "called"))
        } catch (error: Throwable) {
            logEvent("moveTaskToFront", fields + probeFailure(error))
        }
        if (!reorderToFront) {
            logEvent("reorderToFront", fields + mapOf("result" to "skipped"))
            return
        }
        try {
            val intent = Intent(activity, activity.javaClass).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            activity.startActivity(intent)
            logEvent("reorderToFront", fields + mapOf("result" to "called"))
        } catch (error: Throwable) {
            logEvent("reorderToFront", fields + probeFailure(error))
        }
    }

    private fun logEvent(event: String, fields: Map<String, Any?>) {
        log.write(TAG, event, bg = false, fields = ProbeClocks.fields() + fields)
    }

    companion object {
        const val TAG = "PROBE_FRONT"
        const val DELAY_MS = 1_500L
        private const val DEBOUNCE_MS = 2_000L
    }
}
