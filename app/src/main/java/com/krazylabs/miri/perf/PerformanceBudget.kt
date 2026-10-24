package com.krazylabs.miri.perf

/**
 * Review budgets. These constants do not throttle the process.
 * Measure them with the adb commands in the README.
 */
object PerformanceBudget {
    const val LAUNCH_MS = 10_000
    const val TOUCH_RESPONSE_MS = 250
    const val DRIVING_CPU_PERCENT = 5
    const val PARKED_CPU_PERCENT = 25
    const val RAM_MB = 400
}
