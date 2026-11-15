package com.example.pleosstarter.school

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 성적표 "주행 습관" 두 가지 (plan-school-coach.md §3-1). 점수에는 넣지 않고, 주행 중에는 말하지 않는다.
 *
 * - H1 핸들 안정도: 달림(최근 3초 주행거리 늘어남) + 핸들 |각도| < 15°(직진)에서 **1초마다 표본 하나** (부록 I-2 L10:
 *   SDK 알림 간격은 차마다 달라 값이 흔들림). 바로 전 표본과 5° 이상 차이 나면 "흔들림". 안정도 = (1 − 흔들림 ÷ 표본) × 100
 * - B1 브레이크 횟수: 브레이크 "안 밟음 → 밟음" 횟수 ÷ 주행 거리
 */
class HabitMeter {
    private var samples = 0
    private var wobbles = 0
    private var prevAngle: Float? = null
    private var lastBrake: Boolean? = null

    /** 이번 주행에서 브레이크를 새로 밟은 횟수 */
    var brakes = 0
        private set

    fun reset(brakeNow: Boolean?) {
        samples = 0
        wobbles = 0
        prevAngle = null
        brakes = 0
        lastBrake = brakeNow
    }

    /** 1초마다 */
    fun sample(angle: Float?, running: Boolean) {
        if (angle == null || !running || abs(angle) >= STRAIGHT_DEG) {
            prevAngle = null
            return
        }
        val prev = prevAngle
        samples++
        if (prev != null && abs(angle - prev) >= WOBBLE_DEG) wobbles++
        prevAngle = angle
    }

    fun onBrake(pressed: Boolean?, driving: Boolean) {
        if (driving && pressed == true && lastBrake == false) brakes++
        lastBrake = pressed
    }

    /** 핸들 안정도(%). 표본이 [minSamples]보다 적으면 null ("측정하기엔 짧았어요") */
    fun steadiness(minSamples: Int): Int? =
        if (samples < minSamples) null else ((1f - wobbles.toFloat() / samples) * 100).roundToInt()

    /** 1km당 브레이크. 인정 거리 미만이면 null (B1은 인정 주행일 때만 보여 준다) */
    fun brakesPerKm(km: Float): Float? = if (km < TripResult.QUALIFY_KM) null else brakes / km

    companion object {
        const val STRAIGHT_DEG = 15f
        const val WOBBLE_DEG = 5f
        const val MIN_SAMPLES = 30
        /** 시연 모드: 콘솔로 핸들을 몇 번만 바꿔도 보이게 [판단] */
        const val DEMO_MIN_SAMPLES = 5
    }
}
