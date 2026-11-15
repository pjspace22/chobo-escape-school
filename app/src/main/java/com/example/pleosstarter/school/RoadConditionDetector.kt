package com.example.pleosstarter.school

/**
 * 비·밤 상황 코치를 고른다 (코치 W1·W2·N1·N2·N3·N4, plan-school-coach.md §2-3).
 *
 * - W1 빗길: 와이퍼가 [holdMs](10초) 넘게 켜짐 (부록 I-2 L7: 워셔·한 번 닦기 제외 [가정])
 * - W2 비 오는데 전조등 꺼짐: 와이퍼 켜짐 + 전조등 꺼짐이 [holdMs] 이어짐
 * - N1 밤길: 전조등 켜짐 + 밤 시간 (L5: 낮 터널·주간 전조등 제외)
 * - N2 상향등: 상향등 켜짐
 * - N3 밤인데 전조등 꺼짐: 밤(시계 또는 시연 모드) + 달림 + 전조등 꺼짐이 [holdMs] 이어짐 (미등만 켠 경우 포함)
 * - N4 실내등: 달림 + 실내등 켜짐이 [holdMs] 이어짐
 * "달림" = 최근 3초 주행거리가 늘어남 (L3, [MotionTracker]). "밤" = 그날 서울 일몰~일출 (L6, [SunTimes]).
 * 한 주행에 1번 규칙은 [CoachEngine]이 지킨다. 첫 빗길·첫 밤길 배지도 같은 조건.
 * 시각은 호출하는 쪽이 넣는다.
 */
class RoadConditionDetector(private val holdMs: Long = 10_000L) {
    var wiperOn = false
    var lowBeamOn = false
    var highBeamOn = false
    var cabinOn = false

    /** 이번 주행에서 와이퍼·전조등을 켠 적이 있는지 (첫 빗길·첫 밤길 배지) */
    var sawRain = false
        private set
    var sawNight = false
        private set

    private val since = mutableMapOf<String, Long>()

    fun reset() {
        since.clear()
        sawRain = false
        sawNight = false
    }

    /** 주기적으로(1초마다) 부른다. 지금 낼 만한 코치를 우선순위 상관없이 모두 돌려준다. */
    fun check(nowMs: Long, running: Boolean, night: Boolean): List<Coach> = buildList {
        if (held("rain", wiperOn, nowMs)) {
            sawRain = true
            add(Coach.RAIN)
        }
        if (held("rain_no_light", wiperOn && !lowBeamOn, nowMs)) add(Coach.RAIN_NO_HEADLIGHT)
        if (lowBeamOn && night) {
            sawNight = true
            add(Coach.NIGHT)
        }
        if (highBeamOn) add(Coach.HIGH_BEAM)
        if (held("night_no_light", night && running && !lowBeamOn, nowMs)) add(Coach.NIGHT_NO_HEADLIGHT)
        if (held("cabin", running && cabinOn, nowMs)) add(Coach.CABIN_LIGHT)
    }

    /** [condition]이 [holdMs] 넘게 이어졌는지. 조건이 깨지면 다시 센다. */
    private fun held(key: String, condition: Boolean, nowMs: Long): Boolean {
        if (!condition) {
            since.remove(key)
            return false
        }
        val start = since.getOrPut(key) { nowMs }
        return nowMs - start >= holdMs
    }

}
