package com.example.pleosstarter.school

/**
 * 히터 대신 열선 E1 (plan-school-duo-screens.md 부록 G-2 14번).
 *
 * 기어 D + 공조 켜짐 + 설정 온도 ≥ [HOT_SET_C]℃ + 운전석 열선 꺼짐(통풍 포함)이 [holdMs] 이어지면 한 번 (한 주행 1번은 엔진이 지킴).
 * 판단: "히터 켬"은 공조 전원 켜짐 + 설정 온도로 본다 (SDK에 히터 단독 신호가 없음).
 * 시각은 호출하는 쪽이 넣는다.
 */
class SeatHeatDetector {
    var powerOn = false
    var setTempC: Float? = null
    /** 운전석 시트 단계. 1~3 = 열선, 0 = 꺼짐, 음수 = 통풍 (콘솔 실측) */
    var seatHeat: Int? = null

    private var since: Long? = null

    fun reset() {
        since = null
    }

    /** 1초마다. [holdMs]는 보통 5분, 시연 모드 30초 */
    fun check(nowMs: Long, drive: Boolean, holdMs: Long): Coach? {
        val hot = (setTempC ?: 0f) >= HOT_SET_C
        if (!(drive && powerOn && hot && (seatHeat ?: 0) <= 0)) {
            since = null
            return null
        }
        val start = since ?: nowMs.also { since = it }
        return if (nowMs - start >= holdMs) Coach.SEAT_HEAT else null
    }

    companion object {
        const val HOT_SET_C = 26f
        const val HOLD_MS = 5 * 60_000L
        const val DEMO_HOLD_MS = 30_000L
    }
}
