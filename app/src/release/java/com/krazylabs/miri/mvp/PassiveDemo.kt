package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.PlaceRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassiveDemo @Inject constructor() : DemoActions {
    override fun requestDemoRoute() = Unit

    override fun requestDemoRoad() = Unit

    override fun requestReturnGuide(place: PlaceRequest?) = Unit

    override fun demoReturnPlaces(): List<ReturnPlace> = emptyList()
}
