package com.example.pleosstarter.school.parking

import kotlin.math.roundToInt

/**
 * 판정 카드 문구와 TTS (plan-idea-parking-alert.md §9, plan-school-duo-screens.md 부록 C-4).
 * 말투는 "단속될 수 있어요"(단정하지 않음). parking-alert 브랜치 Phrases에서 학교에 필요한 것만 옮김:
 * 음성 "응/아니"·재탑승 시간 문구는 이번에 뺐다 (C-5).
 */
object ParkingPhrases {

    /** 🔴·🟠일 때 한 번 말하는 문구 */
    fun warning(v: Verdict): String? {
        val z = v.zone ?: return null
        return when {
            v.level == Level.ORANGE && z.type == ZoneType.CCTV ->
                "근처에 주정차 단속 카메라가 있어요. 오래 세우시면 단속될 수 있어요."
            v.level == Level.ORANGE && z.type == ZoneType.SCHOOL ->
                "어린이보호구역이에요. 지금은 신고 시간은 아니지만 주정차는 피해 주세요."
            z.type == ZoneType.BUS_STOP -> "여기 버스정류장 10미터 안이에요. 1분이면 신고될 수 있어요."
            z.type == ZoneType.HYDRANT -> "소화전 바로 옆이에요. 조금만 옮겨 주세요."
            z.type == ZoneType.SCHOOL -> "어린이보호구역이에요. 지금은 주정차가 금지된 시간이에요."
            z.type == ZoneType.CROSSWALK -> "횡단보도 바로 앞이에요. 1분이면 신고될 수 있어요."
            else -> null
        }
    }

    /** 카드 한 줄 (예: "버스정류장 12m 안이에요") */
    fun cardLine(v: Verdict): String {
        if (v.silence == Silence.NO_DATA) return "이 지역은 아직 정보가 없어요"
        val z = v.zone ?: return "단속 구역 아님"
        val d = v.distanceM?.let { " ${it.roundToInt()}m" } ?: ""
        return "${z.type.label}$d 안이에요"
    }

    fun guide(lot: NearLot) = "가장 가까운 ${spokenName(lot)}, ${meters(lot.distanceM)}로 안내할게요."

    const val DECLINED = "알겠어요"
    const val NO_DATA = "이 지역은 아직 단속 구역 정보가 없어요."
    const val DISCLAIMER = "과태료는 알림과 관계없이 부과될 수 있어요"
    const val DATA_SOURCE = "지금은 시연용 샘플 좌표예요 (실제 공공데이터 아님). " +
        "서울 버스정류소(OA-15067)·소방용수(OA-21306)·자치구 단속 카메라·전국 횡단보도·어린이보호구역·주차장 표준데이터로 바꿀 예정이에요."

    // "(샘플)" 같은 꼬리표는 소리로 읽지 않는다
    private fun spokenName(lot: NearLot) = lot.lot.name.substringBefore(" (").trim()

    // 10m 단위로 반올림해서 말한다
    fun meters(m: Double) = "${((m / 10).roundToInt() * 10).coerceAtLeast(10)}미터"
}
