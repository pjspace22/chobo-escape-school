package com.example.pleosstarter.school

/**
 * 고속도로 위험 X4 "진출로 놓치고 멈춤" (plan-school-duo-screens.md 부록 G-3 1번).
 *
 * 예전 S1~S4(브레이크 없이 D·문·트렁크·후진)는 차가 이미 막거나 경고해서 뺐다 (부록 G-1).
 *
 * ① 출구·분기 회전을 지났는데 [REROUTE_MS] 안에 내비가 경로를 다시 짬 (= 출구를 놓침)
 *    시연 모드는 경로 다시 짜기가 에뮬레이터에서 안 될 수 있어 "출구를 지나침"만으로 본다 [가정]
 * ② 그 뒤 [WATCH_MS] 안에 멈춤(최근 3초 주행거리 그대로) + 기어 D + 앞에 정체 없음 → "멈추면 위험해요"
 *    같은 동안 기어 R → "고속도로에서 후진은 절대 안 돼요"
 * 시각은 호출하는 쪽이 넣는다.
 */
class SafetyDetector {
    private var exitPassedAt: Long? = null
    private var missedAt: Long? = null
    private var lastKm: Float? = null
    private var movedAt: Long? = null

    fun reset() {
        exitPassedAt = null
        missedAt = null
        movedAt = null
    }

    /** 출구·분기 회전을 지났을 때. [demo]면 바로 "놓침"으로 본다 */
    fun onExitPassed(nowMs: Long, demo: Boolean) {
        exitPassedAt = nowMs
        if (demo) missedAt = nowMs
    }

    /** 내비가 경로를 다시 짰을 때 */
    fun onReRoute(nowMs: Long) {
        val passed = exitPassedAt ?: return
        if (nowMs - passed <= REROUTE_MS) missedAt = nowMs
    }

    fun onDistance(km: Float?, nowMs: Long) {
        if (km == null) return
        val last = lastKm
        if (last != null && km > last) movedAt = nowMs
        lastKm = km
    }

    /** 1초마다 */
    fun check(nowMs: Long, drive: Boolean, reverse: Boolean, congested: Boolean): Coach? {
        val missed = missedAt ?: return null
        if (nowMs - missed > WATCH_MS) {
            missedAt = null
            return null
        }
        if (reverse) return Coach.EXIT_MISSED_REVERSE
        val stopped = movedAt?.let { nowMs - it > STOP_MS } ?: true
        return if (drive && stopped && !congested) Coach.EXIT_MISSED_STOP else null
    }

    companion object {
        /** 출구를 지난 뒤 경로 다시 짜기를 기다리는 시간 (부록 G-3: 30초) */
        const val REROUTE_MS = 30_000L
        /** 놓친 뒤 멈춤·후진을 지켜보는 시간 (판단: 2분) */
        const val WATCH_MS = 120_000L
        /** 이만큼 주행거리가 그대로면 멈춤 (부록 E-2와 같은 3초) */
        const val STOP_MS = 3_000L
    }
}
