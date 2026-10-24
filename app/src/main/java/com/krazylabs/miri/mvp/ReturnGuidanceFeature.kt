package com.krazylabs.miri.mvp

import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.domain.PlaceRequest

/**
 * F3 "반납 장소로 안내".
 * [BuildConfig.RETURN_GUIDANCE] is true on debug and false on release.
 * Release does not draw the button. Debug draws the dashed suggestion.
 *
 * [offer] is the only hook for NaviHelper.requestRoute. NAVI_ROUTE_SEARCH
 * is Coming Soon, so the hook does not call [requestRoute].
 */
object ReturnGuidanceFeature {
    const val NOTE = "권한 공개 후 제공"

    fun visible(enabled: Boolean = BuildConfig.RETURN_GUIDANCE): Boolean = enabled

    fun offer(
        place: PlaceRequest?,
        enabled: Boolean = BuildConfig.RETURN_GUIDANCE,
        requestRoute: (PlaceRequest) -> Unit,
    ): String {
        if (!enabled) return ""
        // When NAVI_ROUTE_SEARCH leaves Coming Soon, the only edit is:
        // if (place != null) requestRoute(place)
        return pendingNote(place, requestRoute)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun pendingNote(place: PlaceRequest?, requestRoute: (PlaceRequest) -> Unit): String = NOTE
}
