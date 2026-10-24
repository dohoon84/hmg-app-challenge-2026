package io.hmg.appchallenge.foundation.probe

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One JSON object per line under the app external files dir, plus logcat.
 * Logcat is split so a long field dump is not truncated by the 4KB line cap.
 */
internal class ProbeLog(
    context: Context,
    private val onUi: (String) -> Unit,
) {
    val directory: File
    private val lock = Any()

    init {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        directory = File(root, "probe")
        directory.mkdirs()
    }

    fun write(tag: String, event: String, bg: Boolean, fields: Map<String, Any?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val json = JSONObject()
        json.put("tsEpochMs", now)
        json.put("ts", FORMATTER.format(Instant.ofEpochMilli(now)))
        json.put("tag", tag)
        json.put("event", event)
        json.put("bg", bg)
        fields.forEach { (key, value) -> json.put(key, toJson(value)) }
        val line = json.toString()
        val chunks = line.chunked(LOGCAT_CHUNK)
        if (chunks.size == 1) {
            Log.i(tag, line)
        } else {
            chunks.forEachIndexed { index, chunk ->
                Log.i(tag, "part ${index + 1}/${chunks.size} $chunk")
            }
        }
        synchronized(lock) {
            runCatching {
                File(directory, "$tag.jsonl").appendText(line + "\n")
            }.onFailure { error ->
                Log.e(tag, "jsonl write failed: ${error.message}")
            }
        }
        val summary = buildString {
            append(event)
            append(" bg=")
            append(bg)
            fields.forEach { (key, value) ->
                if (key == "dump") return@forEach
                append(' ')
                append(key)
                append('=')
                append(value)
            }
        }.take(UI_LIMIT)
        onUi("$tag $summary")
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray -> value
        is Number, is Boolean, is String -> value
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, item) -> put(key.toString(), toJson(item)) }
        }
        is Iterable<*> -> JSONArray().apply { value.forEach { put(toJson(it)) } }
        is IntArray -> JSONArray().apply { value.forEach { put(it) } }
        is FloatArray -> JSONArray().apply { value.forEach { put(it.toDouble()) } }
        is DoubleArray -> JSONArray().apply { value.forEach { put(it) } }
        else -> value.toString()
    }

    private companion object {
        const val LOGCAT_CHUNK = 3000
        const val UI_LIMIT = 420
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    }
}

internal fun probeFailure(error: Throwable): Map<String, Any?> {
    val permission = error is SecurityException ||
        error.message?.contains("permission", ignoreCase = true) == true
    val result = when {
        permission -> "permission"
        error is IllegalArgumentException -> "unavailable"
        else -> "error"
    }
    return mapOf(
        "result" to result,
        "exception" to error.javaClass.name,
        "message" to (error.message ?: ""),
    )
}
