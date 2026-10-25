package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.PlaceRequest

/** Debug builds send the scripted places. Release builds do nothing. */
interface DemoActions {
    fun requestDemoRoute()
    fun requestDemoRoad()
    fun requestReturnGuide(place: PlaceRequest?)

    /** Spec rows for the debug scenario. Release returns an empty list. */
    fun demoReturnPlaces(): List<ReturnPlace>
}
