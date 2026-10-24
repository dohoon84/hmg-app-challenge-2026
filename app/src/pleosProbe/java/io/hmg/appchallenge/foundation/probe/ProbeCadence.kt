package io.hmg.appchallenge.foundation.probe

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object ProbeClocks {
    private val kst = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        .withZone(ZoneId.of("Asia/Seoul"))

    fun fields(elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Map<String, Any?> {
        val epochMs = System.currentTimeMillis()
        return mapOf(
            "epochMs" to epochMs,
            "elapsedRealtimeMs" to elapsedRealtimeMs,
            "kst" to kst.format(Instant.ofEpochMilli(epochMs)),
        )
    }
}

/**
 * Elapsed-time gaps between remaining-distance callbacks.
 * [deltaSameMs] is since the previous callback of the same name.
 * [deltaAnyMs] is since the previous callback of any tracked name.
 */
internal class CadenceTracker {
    private val lock = Any()
    private val lastSame = mutableMapOf<String, Long>()
    private var lastAny: Long? = null

    fun mark(event: String, elapsedRealtimeMs: Long): Map<String, Any?> = synchronized(lock) {
        val previousSame = lastSame[event]
        val previousAny = lastAny
        lastSame[event] = elapsedRealtimeMs
        lastAny = elapsedRealtimeMs
        mapOf(
            "deltaSameMs" to previousSame?.let { elapsedRealtimeMs - it },
            "deltaAnyMs" to previousAny?.let { elapsedRealtimeMs - it },
        )
    }
}
