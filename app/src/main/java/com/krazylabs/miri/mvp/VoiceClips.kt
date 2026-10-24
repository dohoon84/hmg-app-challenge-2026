package com.krazylabs.miri.mvp

/**
 * voice-check §3-d. 29 filename slots. No turn-signal clips and no toll-zero clip.
 * A missing raw file is spoken with [spoken]. It is not a new filename.
 */
object VoiceClips {
    const val H1 = "vo_highway_entry"
    const val H1_TEXT =
        "오늘 길엔 고속도로 진입이 있어요. 출발 전에 운전 보조 버튼 위치를 봐 두세요."

    val names: List<String> = listOf(
        "vo_fuel_gasoline",
        "vo_fuel_diesel",
        "vo_fuel_lpg",
        "vo_fuel_hybrid",
        "vo_fuel_electric",
        "vo_port_left",
        "vo_port_right",
        "vo_charge_port_left",
        "vo_charge_port_right",
        "vo_charge_port_rear_right",
        "vo_charge_port_rear_left",
        "vo_toll_1",
        "vo_toll_2",
        "vo_toll_3",
        "vo_toll_4",
        "vo_toll_5",
        "vo_toll_6",
        "vo_toll_7",
        "vo_toll_8",
        "vo_toll_9",
        "vo_toll_many",
        H1,
        "vo_connector_dc_combo",
        "vo_arrive_gas_soon",
        "vo_arrive_ev_soon",
        "vo_brief_intro",
        "vo_return_arrived",
        "vo_return_check",
        "vo_check_done",
    )

    private val texts: Map<String, String> = mapOf(
        "vo_fuel_gasoline" to "이 차는 휘발유차예요.",
        "vo_fuel_diesel" to "이 차는 경유차예요.",
        "vo_fuel_lpg" to "이 차는 엘피지차예요.",
        "vo_fuel_hybrid" to "이 차는 하이브리드차예요.",
        "vo_fuel_electric" to "이 차는 전기차예요.",
        "vo_port_left" to "주유구는 왼쪽이에요.",
        "vo_port_right" to "주유구는 오른쪽이에요.",
        "vo_charge_port_left" to "충전구는 왼쪽이에요.",
        "vo_charge_port_right" to "충전구는 오른쪽이에요.",
        "vo_charge_port_rear_right" to "충전구는 오른쪽 뒤예요.",
        "vo_charge_port_rear_left" to "충전구는 왼쪽 뒤예요.",
        "vo_toll_1" to "오늘 길엔 톨게이트가 한 곳 있어요.",
        "vo_toll_2" to "오늘 길엔 톨게이트가 두 곳 있어요.",
        "vo_toll_3" to "오늘 길엔 톨게이트가 세 곳 있어요.",
        "vo_toll_4" to "오늘 길엔 톨게이트가 네 곳 있어요.",
        "vo_toll_5" to "오늘 길엔 톨게이트가 다섯 곳 있어요.",
        "vo_toll_6" to "오늘 길엔 톨게이트가 여섯 곳 있어요.",
        "vo_toll_7" to "오늘 길엔 톨게이트가 일곱 곳 있어요.",
        "vo_toll_8" to "오늘 길엔 톨게이트가 여덟 곳 있어요.",
        "vo_toll_9" to "오늘 길엔 톨게이트가 아홉 곳 있어요.",
        "vo_toll_many" to "오늘 길엔 톨게이트가 여러 곳 있어요.",
        H1 to H1_TEXT,
        "vo_connector_dc_combo" to "커넥터는 디씨 콤보예요.",
        "vo_arrive_gas_soon" to "잠시 후 주유소에 도착해요.",
        "vo_arrive_ev_soon" to "잠시 후 충전소에 도착해요.",
        "vo_brief_intro" to "오늘 길을 알려 드릴게요.",
        "vo_return_arrived" to "반납 장소에 도착했어요.",
        "vo_return_check" to "내리기 전에 두고 가는 물건이 없는지 확인해 주세요.",
        "vo_check_done" to "다 끝나면 알려 드릴게요.",
    )

    fun contains(name: String): Boolean = name in names

    /** Sentence for a filename slot. Null when the name is not one of the 29. */
    fun spoken(name: String): String? = texts[name]
}
