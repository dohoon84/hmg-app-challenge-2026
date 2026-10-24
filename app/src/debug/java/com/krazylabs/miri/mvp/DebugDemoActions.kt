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

    override fun demoReturnPlaces(): List<ReturnPlace> = debugReturnPlaces()
}

/** The three F2 rows from the light mockup, with the labels the frame uses. */
fun debugReturnPlaces(): List<ReturnPlace> = listOf(
    ReturnPlace(
        id = "daechi",
        name = "대치동 공영주차장",
        caption = "대여한 곳",
        latitude = 37.4995,
        longitude = 127.0635,
        distance = "2.4 km",
    ),
    ReturnPlace(
        id = "yeoksam",
        name = "역삼 영업소",
        caption = "영업소",
        latitude = 37.5007,
        longitude = 127.0365,
        distance = "3.1 km",
    ),
    ReturnPlace(
        id = "pangyo",
        name = "판교역 환승주차장",
        caption = "최근 목적지",
        latitude = 37.3948,
        longitude = 127.1113,
        distance = "12.8 km",
    ),
)
