package io.hmg.appchallenge.foundation.domain

object MotionResolver {
    fun resolve(gear: GearPosition, requiresDistractionOptimization: Boolean): Motion {
        if (requiresDistractionOptimization) return Motion.DRIVING
        return when (gear) {
            GearPosition.PARK -> Motion.PARKED
            GearPosition.UNKNOWN -> Motion.UNKNOWN
            GearPosition.NEUTRAL,
            GearPosition.REVERSE,
            GearPosition.DRIVE,
            GearPosition.OTHER,
            -> Motion.DRIVING
        }
    }
}

/**
 * Pleos has no trustworthy shell until both gear and UX restrictions have been read.
 * Fake builds pass failClosed = false so a simulated park still shows the full UI.
 */
object ShellPolicy {
    fun select(motion: Motion, uxAvailable: Boolean, failClosed: Boolean): AppShell {
        if (failClosed && (!uxAvailable || motion == Motion.UNKNOWN)) return AppShell.REDUCED
        return when (motion) {
            Motion.DRIVING -> AppShell.REDUCED
            Motion.PARKED, Motion.UNKNOWN -> AppShell.FULL
        }
    }
}

/**
 * CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD. Value is checked against android.car in unit tests.
 */
object UxRestrictionBits {
    const val NO_KEYBOARD = 8
}

object KeyboardPolicy {
    fun allowsKeyboard(mask: Int, uxAvailable: Boolean, failClosed: Boolean): Boolean {
        if (failClosed && !uxAvailable) return false
        return mask and UxRestrictionBits.NO_KEYBOARD == 0
    }
}

/** Snapshot written when CarService is down or the Car APIs cannot be used. */
object SignalLoss {
    fun drivingSnapshot(): DrivingSnapshot {
        val requiresDistractionOptimization = true
        return DrivingSnapshot(
            gear = GearPosition.UNKNOWN,
            gearRaw = null,
            gearAvailable = false,
            uxAvailable = false,
            requiresDistractionOptimization = requiresDistractionOptimization,
            restrictionMask = 0,
            maxRestrictedStringLength = 0,
            motion = MotionResolver.resolve(GearPosition.UNKNOWN, requiresDistractionOptimization),
        )
    }
}

/** Last four characters only. Null and empty stay as they are. */
fun maskVin(vin: String?): String? {
    if (vin.isNullOrEmpty()) return vin
    if (vin.length <= VIN_VISIBLE_TAIL) return "*".repeat(vin.length)
    val hidden = vin.length - VIN_VISIBLE_TAIL
    return "*".repeat(hidden) + vin.takeLast(VIN_VISIBLE_TAIL)
}

private const val VIN_VISIBLE_TAIL = 4

interface DistractionPolicy {
    val failClosed: Boolean
}

class StaticDistractionPolicy(
    override val failClosed: Boolean,
) : DistractionPolicy

object PanePolicy {
    const val TWO_PANE_MIN_WIDTH_DP = 800

    fun useTwoPanes(landscape: Boolean, widthDp: Int): Boolean =
        landscape || widthDp >= TWO_PANE_MIN_WIDTH_DP
}

object PlaceParser {
    fun parse(latitude: String, longitude: String, name: String): PlaceRequest? {
        val lat = latitude.trim().toDoubleOrNull() ?: return null
        val lon = longitude.trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val label = name.trim().ifEmpty { "좌표 목적지" }
        return PlaceRequest(latitude = lat, longitude = lon, name = label)
    }
}

object GeoEstimate {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
            Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (EARTH_RADIUS_METERS * c).toInt().coerceAtLeast(0)
    }

    fun etaSeconds(distanceMeters: Int, speedKmh: Double = 40.0): Int {
        if (speedKmh <= 0.0 || distanceMeters <= 0) return 0
        return ((distanceMeters / 1000.0) / speedKmh * 3600.0).toInt()
    }
}

fun shortenForRestriction(text: String, maxLength: Int, restricted: Boolean): String {
    if (!restricted || maxLength <= 0 || text.length <= maxLength) return text
    return text.take(maxLength)
}
