package com.krazylabs.miri.mvp

/**
 * voice-check §3-d. 29 names. No turn-signal clips and no toll-zero clip.
 * A missing raw file drops that sentence. It is not spoken with TTS.
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

    fun contains(name: String): Boolean = name in names
}
