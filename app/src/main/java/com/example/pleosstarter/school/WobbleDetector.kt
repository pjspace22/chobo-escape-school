package com.example.pleosstarter.school

import kotlin.math.abs

/**
 * 핸들 흔들림 = 긴장 H2 (plan-school-duo-screens.md 부록 G-3 24번).
 *
 * 직진 중(핸들 ±[straightDeg]° 안) [windowMs] 동안 핸들이 [swingDeg]° 넘게 [swings]번 이상 왔다 갔다 하면 한 번.
 * "왔다 갔다" = 바로 전과 반대 방향으로 [swingDeg]° 넘게 움직임. 기준값은 모두 [가정], 시연하며 조정.
 * 직진 범위를 벗어나면(실제로 꺾음) 처음부터 다시 센다. 시각은 호출하는 쪽이 넣는다.
 */
class WobbleDetector(
    private val straightDeg: Float = 10f,
    private val swingDeg: Float = 3f,
    private val swings: Int = 4,
    private val windowMs: Long = 10_000L,
) {
    private var anchor: Float? = null
    private var lastDir = 0
    private val swingAt = ArrayDeque<Long>()

    fun reset() {
        anchor = null
        lastDir = 0
        swingAt.clear()
    }

    /** @return 이번에 "긴장"으로 볼 만큼 흔들렸으면 true */
    fun onSteering(angle: Float?, nowMs: Long, driving: Boolean): Boolean {
        if (angle == null || !driving || abs(angle) > straightDeg) {
            reset()
            return false
        }
        val from = anchor ?: angle.also { anchor = it }
        val d = angle - from
        if (abs(d) < swingDeg) return false
        val dir = if (d > 0) 1 else -1
        // 방향이 바뀌었을 때만 한 번 "흔들림"으로 센다
        if (dir != lastDir) swingAt.addLast(nowMs)
        lastDir = dir
        anchor = angle
        while (swingAt.isNotEmpty() && nowMs - swingAt.first() > windowMs) swingAt.removeFirst()
        if (swingAt.size >= swings) {
            swingAt.clear()
            return true
        }
        return false
    }
}
