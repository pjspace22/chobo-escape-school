package com.example.pleosstarter.school

/**
 * 차가 움직이는지·추정 속도 (부록 I-1·I-2 L3). 속도 신호가 없어서 주행거리(km, 1m 단위)로 본다.
 *
 * - 달림 = 최근 [WINDOW_MS] 안에 주행거리가 늘었음 (빗길·밤·실내등·핸들 안정도·회전 판정이 모두 이것을 쓴다)
 * - 서 있음 = 주행거리가 [ms] 동안 그대로 (좌회전 대기 L4는 3초, 멈춤은 2초)
 * - 추정 속도(km/h) = 최근 3초 주행거리 증가(m) ÷ 3초 × 3.6. 짧은 순간 값은 쓰지 않는다 (등급 "추정", 점수에 안 씀)
 * 시각은 호출하는 쪽이 넣는다. [tick]을 1초마다 불러 3초 전 값을 남긴다.
 */
class MotionTracker {
    /** 지금 주행거리 (km) */
    var km: Float? = null
        private set
    private var lastMoveAt: Long? = null
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun onDistance(value: Float?, nowMs: Long) {
        if (value == null) return
        val prev = km
        if (prev != null && value > prev) lastMoveAt = nowMs
        km = value
    }

    /** 1초마다: 지금 값을 남긴다 (추정 속도용) */
    fun tick(nowMs: Long) {
        val k = km ?: return
        samples.addLast(nowMs to k)
        while (samples.size > 1 && nowMs - samples.first().first > KEEP_MS) samples.removeFirst()
    }

    /** 최근 3초 안에 주행거리가 늘었는지 */
    fun moving(nowMs: Long): Boolean = lastMoveAt?.let { nowMs - it <= WINDOW_MS } == true

    /** 주행거리가 [ms] 넘게 그대로인지 (한 번도 안 늘었으면 true) */
    fun stillFor(nowMs: Long, ms: Long): Boolean = lastMoveAt?.let { nowMs - it >= ms } ?: true

    /** 추정 속도 km/h. 3초 전 값이 없으면 null */
    fun speedKmh(nowMs: Long): Float? {
        val k = km ?: return null
        val then = samples.lastOrNull { nowMs - it.first >= WINDOW_MS }?.second ?: return null
        return ((k - then) * 1000f * 1.2f).coerceAtLeast(0f) // m ÷ 3초 × 3.6
    }

    companion object {
        const val WINDOW_MS = 3_000L
        private const val KEEP_MS = 10_000L
    }
}
