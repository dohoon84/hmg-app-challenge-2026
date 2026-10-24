package com.krazylabs.miri.sim

import com.krazylabs.miri.domain.PlaceRequest

/** Debug destination. Not part of the pleos release. */
object SamplePlaces {
    val seoulStation = PlaceRequest(
        latitude = 37.554722,
        longitude = 126.970833,
        name = "서울역",
        address = "서울특별시 용산구 한강대로 405",
    )
}
