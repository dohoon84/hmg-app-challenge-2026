package io.hmg.appchallenge.foundation.probe

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper

/**
 * Read-only playback configurations. Used to separate navigation speech from a gear chime.
 * No audio focus request and no playback.
 */
internal class AudioPlaybackProbe(
    context: Context,
    private val log: ProbeLog,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var registered = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            logConfigs("change", configs.orEmpty())
        }
    }

    fun start() {
        logNames()
        try {
            audio.registerAudioPlaybackCallback(callback, main)
            registered = true
            logEvent("register", mapOf("result" to "registered"))
        } catch (error: Throwable) {
            logEvent("register", probeFailure(error))
        }
        readActive("startup")
    }

    fun release() {
        if (!registered) return
        registered = false
        try {
            audio.unregisterAudioPlaybackCallback(callback)
            logEvent("unregister", mapOf("result" to "called"))
        } catch (error: Throwable) {
            logEvent("unregister", probeFailure(error))
        }
    }

    private fun readActive(reason: String) {
        try {
            logConfigs(reason, audio.activePlaybackConfigurations.orEmpty())
        } catch (error: Throwable) {
            logEvent("active", probeFailure(error) + mapOf("reason" to reason))
        }
    }

    private fun logConfigs(reason: String, configs: List<AudioPlaybackConfiguration>) {
        logEvent(
            "playback",
            ProbeClocks.fields() + mapOf(
                "reason" to reason,
                "count" to configs.size,
                "configs" to configs.map { configFields(it) },
            ),
        )
    }

    private fun configFields(config: AudioPlaybackConfiguration): Map<String, Any?> {
        val attributes = runCatching { config.audioAttributes }.getOrNull()
        val usage = attributes?.usage
        val contentType = attributes?.contentType
        val player = reflectPlayer(config)
        return mapOf(
            "usage" to usage,
            "usageName" to usage?.let { usageName(it) },
            "contentType" to contentType,
            "contentTypeName" to contentType?.let { contentTypeName(it) },
            "playerState" to player.state,
            "playerStateName" to player.stateName,
            "playerStateAccess" to player.access,
            "dump" to runCatching { config.toString() }.getOrNull(),
        )
    }

    private fun logNames() {
        logEvent(
            "usage_table",
            mapOf(
                "usages" to usageTable,
                "contentTypes" to contentTypeTable,
                "playerStates" to playerStateTable,
                "note" to
                    "Public API 34 SDK has no USAGE_SAFETY or USAGE_EMERGENCY. " +
                    "Names come from runtime AudioAttributes fields when the image exposes them. " +
                    "AOSP @SystemApi fallbacks kept only if the field is absent: " +
                    "EMERGENCY=1000, SAFETY=1001, VEHICLE_STATUS=1002, ANNOUNCEMENT=1003. " +
                    "getPlayerState is hidden; it is read by reflection when present.",
            ),
        )
    }

    private fun logEvent(event: String, fields: Map<String, Any?>) {
        log.write(TAG, event, bg = false, fields = fields)
    }

    private data class PlayerRead(val state: Any?, val stateName: Any?, val access: String)

    private fun reflectPlayer(config: AudioPlaybackConfiguration): PlayerRead {
        val method = playerStateMethod ?: return PlayerRead(null, null, "no_method")
        return try {
            val state = method.invoke(config) as? Int
            PlayerRead(state, state?.let { playerStateTable[it] ?: it.toString() }, "reflected")
        } catch (error: Throwable) {
            PlayerRead(null, error.message, "error")
        }
    }

    private fun usageName(value: Int): String = usageTable[value] ?: "UNKNOWN_$value"

    private fun contentTypeName(value: Int): String = contentTypeTable[value] ?: "UNKNOWN_$value"

    companion object {
        const val TAG = "PROBE_AUDIO"

        private val usageTable: Map<Int, String> = staticIntNames(AudioAttributes::class.java, "USAGE_")
            .let { reflected ->
                val fallback = mapOf(
                    1000 to "EMERGENCY",
                    1001 to "SAFETY",
                    1002 to "VEHICLE_STATUS",
                    1003 to "ANNOUNCEMENT",
                )
                fallback + reflected
            }

        private val contentTypeTable: Map<Int, String> =
            staticIntNames(AudioAttributes::class.java, "CONTENT_TYPE_")

        private val playerStateTable: Map<Int, String> =
            staticIntNames(AudioPlaybackConfiguration::class.java, "PLAYER_STATE_")

        private val playerStateMethod = AudioPlaybackConfiguration::class.java.methods.firstOrNull { method ->
            method.name == "getPlayerState" && method.parameterCount == 0
        }

        private fun staticIntNames(cls: Class<*>, prefix: String): Map<Int, String> {
            val names = linkedMapOf<Int, String>()
            cls.declaredFields.forEach { field ->
                if (!field.name.startsWith(prefix) || field.type != Int::class.javaPrimitiveType) return@forEach
                val value = runCatching {
                    field.isAccessible = true
                    field.getInt(null)
                }.getOrNull() ?: return@forEach
                names.putIfAbsent(value, field.name.removePrefix(prefix))
            }
            return names
        }
    }
}
