package io.hmg.appchallenge.foundation.probe

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.hmg.appchallenge.foundation.navi.NavigationRepository
import io.hmg.appchallenge.foundation.session.ProbeNavigationHold

/**
 * Debug screen state for the signal probe. ProcessLifecycle decides whether the
 * NaviHelper listeners stay registered. The activity foreground flag is only the bg= marker.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProbeSessionEntry {
    fun navigationRepository(): NavigationRepository
}

internal class SignalProbe(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    val lines = mutableStateListOf<String>()
    var holdInBackground by mutableStateOf(true)
        private set
    var frontTest by mutableStateOf(false)
        private set
    var reorderToFront by mutableStateOf(false)
        private set
    var cadencePoll by mutableStateOf(false)
        private set
    var jsonDirectory by mutableStateOf("")
        private set

    private val log = ProbeLog(appContext) { line ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            push(line)
        } else {
            main.post { push(line) }
        }
    }
    private val navi = NaviSignalProbe(appContext, log) { !activityInForeground }
    private val vehicle = VehicleSignalProbe(appContext, log) { !activityInForeground }
    private var host: Activity? = null
    private val front = FrontTaskProbe(log) { host }
    private val audio = AudioPlaybackProbe(appContext, log)

    @Volatile
    private var activityInForeground = true
    private var started = false

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            navi.onProcessForeground()
            vehicle.onProcessForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            navi.onProcessBackground(holdInBackground)
            vehicle.onProcessBackground(holdInBackground)
        }
    }

    fun start() {
        if (started) return
        started = true
        jsonDirectory = log.directory.absolutePath
        ProbeNavigationHold.keepRegistered = holdInBackground
        log.write(
            TAG_INFO,
            "jsonl_directory",
            bg = false,
            fields = mapOf(
                "path" to jsonDirectory,
                "pull" to "adb pull $jsonDirectory",
            ),
        )
        logPermissions("startup")
        log.write(
            TAG_NAVI,
            "background_hold",
            bg = false,
            fields = mapOf(
                "keepRegistered" to holdInBackground,
                "note" to "Default is on. ProcessLifecycle onStop does not call NavigationRepository.stop while this is true. " +
                    "The probe NaviHelper is separate and follows the same flag. Vehicle and Car in CabinSession still stop.",
            ),
        )
        vehicle.onReverseToPark = front::onReverseToPark
        audio.start()
        navi.start()
        vehicle.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    fun attachHost(activity: Activity) {
        host = activity
    }

    fun onHostResume() = front.onHostResume()

    fun onHostPause() = front.onHostPause()

    fun setFrontFromIntent(intent: Intent) {
        if (intent.hasExtra(EXTRA_FRONT)) updateFrontTest(intent.getBooleanExtra(EXTRA_FRONT, false))
        if (intent.hasExtra(EXTRA_REORDER)) updateReorderToFront(intent.getBooleanExtra(EXTRA_REORDER, false))
    }

    fun setCadenceFromIntent(intent: Intent) {
        if (intent.hasExtra(EXTRA_CADENCE)) updateCadencePoll(intent.getBooleanExtra(EXTRA_CADENCE, false))
    }

    fun requestRouteFromIntent(intent: Intent) {
        val lat = extraDouble(intent, EXTRA_LAT) ?: return
        val lon = extraDouble(intent, EXTRA_LON) ?: return
        val name = intent.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: "adb destination"
        navi.requestDestination(lat, lon, name)
    }

    fun onActivityStart() {
        activityInForeground = true
    }

    fun onActivityStop() {
        activityInForeground = false
    }

    fun setHold(enabled: Boolean) {
        holdInBackground = enabled
        ProbeNavigationHold.keepRegistered = enabled
        log.write(
            TAG_NAVI,
            "background_hold",
            bg = !activityInForeground,
            fields = mapOf("keepRegistered" to enabled),
        )
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        log.write(
            TAG_INFO,
            "permission_result",
            bg = !activityInForeground,
            fields = mapOf("result" to result),
        )
        logPermissions("after_request")
        vehicle.readAgain()
    }

    fun requestJeju() = navi.requestJejuRental()

    fun requestGas() = navi.requestGasStationByName()

    fun trySimulation() = navi.trySimulationGuiding()

    fun pollNavi() = navi.pollGetters()

    fun readVehicle() = vehicle.readAgain()

    fun updateFrontTest(enabled: Boolean) {
        if (frontTest == enabled) return
        frontTest = enabled
        front.enabled = enabled
        log.write(
            FrontTaskProbe.TAG,
            "front_test",
            bg = !activityInForeground,
            fields = ProbeClocks.fields() + mapOf("enabled" to enabled, "reorderToFront" to reorderToFront),
        )
    }

    fun updateReorderToFront(enabled: Boolean) {
        if (reorderToFront == enabled) return
        reorderToFront = enabled
        front.reorderToFront = enabled
        log.write(
            FrontTaskProbe.TAG,
            "reorder_to_front",
            bg = !activityInForeground,
            fields = ProbeClocks.fields() + mapOf("enabled" to enabled, "frontTest" to frontTest),
        )
    }

    fun updateCadencePoll(enabled: Boolean) {
        if (cadencePoll == enabled) return
        cadencePoll = enabled
        navi.setCadencePoll(enabled)
    }

    fun release() {
        if (!started) return
        started = false
        audio.release()
        front.release()
        val process = ProcessLifecycleOwner.get().lifecycle
        val processInBackground = !process.currentState.isAtLeast(Lifecycle.State.STARTED)
        process.removeObserver(processObserver)
        ProbeNavigationHold.keepRegistered = false
        navi.release()
        vehicle.release()
        if (!processInBackground) return
        runCatching {
            val app = appContext as Application
            EntryPointAccessors.fromApplication(app, ProbeSessionEntry::class.java)
                .navigationRepository()
                .stop()
        }.onFailure { error ->
            log.write(TAG_NAVI, "product_navi_stop", bg = true, fields = probeFailure(error))
        }
    }

    private fun logPermissions(phase: String) {
        PERMISSIONS.forEach { name ->
            val granted = ContextCompat.checkSelfPermission(appContext, name) == PackageManager.PERMISSION_GRANTED
            val defined = runCatching { appContext.packageManager.getPermissionInfo(name, 0) }
            val info = defined.getOrNull()
            log.write(
                TAG_INFO,
                "permission",
                bg = false,
                fields = mapOf(
                    "phase" to phase,
                    "name" to name,
                    "granted" to granted,
                    "protection" to (info?.let { protectionLabel(it.protectionLevel) } ?: "not_defined_on_image"),
                    "protectionLevel" to (info?.protectionLevel ?: -1),
                ),
            )
        }
    }

    private fun protectionLabel(level: Int): String {
        val base = level and PermissionInfo.PROTECTION_MASK_BASE
        val baseName = when (base) {
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            4 -> "internal"
            else -> "base=$base"
        }
        val flags = level and PermissionInfo.PROTECTION_MASK_FLAGS
        return if (flags == 0) baseName else "$baseName|flags=$flags"
    }

    private fun extraDouble(intent: Intent, key: String): Double? {
        if (!intent.hasExtra(key)) return null
        return when (val value = intent.extras?.get(key)) {
            is Float -> value.toDouble()
            is Double -> value
            is String -> value.toDoubleOrNull()
            is Int -> value.toDouble()
            else -> null
        }
    }

    private fun push(line: String) {
        lines.add(0, line)
        while (lines.size > UI_LINES) {
            lines.removeAt(lines.lastIndex)
        }
    }

    companion object {
        const val TAG_NAVI = "PROBE_NAVI"
        const val TAG_INFO = "PROBE_INFO"
        private const val UI_LINES = 80

        val PERMISSIONS: Array<String> = arrayOf(
            "android.car.permission.CAR_INFO",
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_POWERTRAIN",
            "pleos.car.permission.CAR_INFO",
            "pleos.car.permission.CAR_ENERGY",
            "pleos.car.permission.CAR_POWERTRAIN",
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
            "android.car.permission.CAR_ENERGY_PORTS",
            "android.permission.REORDER_TASKS",
            "ai.pleos.playground.service.vehicle.VEHICLE_BINDING",
        )

        const val EXTRA_FRONT = "frontTest"
        const val EXTRA_REORDER = "reorderToFront"
        const val EXTRA_CADENCE = "cadencePoll"
        const val EXTRA_LAT = "destLat"
        const val EXTRA_LON = "destLon"
        const val EXTRA_NAME = "destName"
    }
}
