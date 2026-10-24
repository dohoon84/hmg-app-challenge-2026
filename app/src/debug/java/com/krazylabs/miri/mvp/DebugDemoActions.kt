package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.navi.NavigationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugDemoActions @Inject constructor(
    private val navigation: NavigationRepository,
    private val scenario: ScenarioInput,
) : DemoActions {
    override fun requestDemoRoute() {
        navigation.setDestination(
            PlaceRequest(
                latitude = 37.5056,
                longitude = 127.0539,
                name = "강남 충전소",
                address = "서울 강남구",
            ),
        )
    }

    override fun requestDemoRoad() {
        val current = scenario.overrides.value
        scenario.update(current.copy(pretendCharging = false, pretendTolls = 2, pretendHighways = 1))
        navigation.setDestination(
            PlaceRequest(
                latitude = 37.5547,
                longitude = 126.9707,
                name = "서울역",
                address = "서울 중구",
            ),
        )
    }

    override fun requestReturnGuide(place: PlaceRequest?) {
        if (place != null) navigation.setDestination(place)
    }
}
