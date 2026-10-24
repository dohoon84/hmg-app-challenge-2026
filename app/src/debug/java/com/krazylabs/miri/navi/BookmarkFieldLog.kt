package com.krazylabs.miri.navi

import android.util.Log

/** Field-by-field bookmark log. Debug builds only; release logs the count. */
fun logBookmarkFields(
    latitude: Double,
    longitude: Double,
    displayLatitude: Double?,
    displayLongitude: Double?,
    name: String?,
    poiId: String?,
    poiSubId: String?,
    address: String?,
    category: String?,
    provider: String?,
) {
    Log.i(
        "MiriNav",
        "bookmark lat=$latitude lon=$longitude " +
            "displayLat=$displayLatitude displayLon=$displayLongitude " +
            "name=$name poiId=$poiId poiSubId=$poiSubId " +
            "address=$address category=$category provider=$provider",
    )
}
