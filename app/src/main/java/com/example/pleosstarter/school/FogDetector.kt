package com.example.pleosstarter.school

/**
 * 김 서림 코치 F1·F1+ (plan-school-coach.md §2-3, 현규 아이디어).
 *
 * - F1 김 서림 위험: 공조 켜짐 + 내기순환 + 에어컨 꺼짐 + 앞유리 성에 제거 꺼짐 + 히터가 [holdMs] 이어짐
 *   히터 = 설정 온도 ≥ 24℃, 또는 설정 온도가 실내 온도보다 2℃ 이상 높음
 * - F1+ 해결: F1을 들은 뒤 외기로 바꾸거나, 에어컨을 켜거나, 성에 제거를 켜면 칭찬 + "김 서림 해결" 배지
 *
 * 기획의 "달림"은 주행 중(기어 D~P 사이)으로 본다. 신호 대기로 브레이크를 밟을 때마다
 * 5분을 다시 세면 실제로는 거의 안 나오기 때문이다. 이 감지기는 주행 중에만 불린다.
 * 시각은 호출하는 쪽이 넣는다.
 */
class FogDetector {
    var powerOn = false
    var acOn = false
    var recircOn = false
    var defrostOn = false
    /** 운전석 설정 온도(℃) */
    var setTempC: Float? = null
    /** 실내 온도(℃) */
    var cabinTempC: Float? = null

    /** 이번 주행에서 F1을 듣고 고쳤는지 (김 서림 해결 배지) */
    var fixed = false
        private set

    private var since: Long? = null

    /** F1이 실제로 화면에 나갔는지. 코치 엔진이 간격 때문에 버리면 다음 틱에 다시 낸다 */
    var warned = false

    fun reset() {
        since = null
        warned = false
        fixed = false
    }

    /** 히터처럼 따뜻한 바람을 내고 있는지 */
    private fun heating(): Boolean {
        val set = setTempC ?: return false
        val cabin = cabinTempC
        return set >= HEAT_SET_C || (cabin != null && set >= cabin + HEAT_DIFF_C)
    }

    /** 주기적으로(1초마다) 부른다. [holdMs]는 보통 5분, 시연 모드 30초 */
    fun check(nowMs: Long, holdMs: Long): Coach? {
        val risky = powerOn && recircOn && !acOn && !defrostOn && heating()
        if (warned && !fixed && (!recircOn || acOn || defrostOn)) {
            fixed = true
            return Coach.FOG_OK
        }
        if (!risky) {
            since = null
            return null
        }
        val start = since ?: nowMs.also { since = it }
        return if (!warned && nowMs - start >= holdMs) Coach.FOG else null
    }

    companion object {
        const val HOLD_MS = 5 * 60_000L
        const val DEMO_HOLD_MS = 30_000L
        const val HEAT_SET_C = 24f
        const val HEAT_DIFF_C = 2f
    }
}
