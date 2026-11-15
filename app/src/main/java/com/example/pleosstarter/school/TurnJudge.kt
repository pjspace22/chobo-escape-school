package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import kotlin.math.abs

/**
 * 회전할 때 깜빡이를 먼저 켰는지 (점수 "깜빡이 먼저" 50점, 코치 T1·T2, 부록 I-2 L2).
 *
 * 진범 signal/TurnSignalUsageDetector와 같은 생각(핸들 ±[steerDeg]° 넘는 순간, [graceMs] 안에 켰으면 사용)에 세 가지를 더한다:
 * ① 최근 3초 주행거리가 늘었을 때만 센다 (멈춰서·주차장에서 돌린 것 제외)
 * ② 깜빡이 방향 = 꺾는 방향일 때만 "켬" (핸들 각도는 앱 기준 왼쪽이 +)
 * ③ 주행 시작 뒤·끝나기 전 [edgeKm](100m)는 안 센다 [가정: 주차장 출입]. 끝 쪽은 주행이 끝난 뒤 [counted]에서 뺀다
 * 시각·거리는 호출하는 쪽이 넣는다.
 */
class TurnJudge(
    private val steerDeg: Float = 30f,
    private val graceMs: Long = 3_000L,
) {
    private data class Turn(val km: Float, val used: Boolean)

    private val turns = mutableListOf<Turn>()
    private var signal: TurnSignalValue? = null
    private var leftOnAt: Long? = null
    private var rightOnAt: Long? = null
    private var turning = false

    fun onTurnSignal(value: TurnSignalValue?, nowMs: Long) {
        signal = value
        if (value == TurnSignalValue.LEFT) leftOnAt = nowMs
        if (value == TurnSignalValue.RIGHT) rightOnAt = nowMs
    }

    /**
     * @param tripKm 주행 시작부터 달린 거리
     * @return 새로 센 회전이면 깜빡이 사용 여부, 아니면 null
     */
    fun onSteering(angle: Float?, nowMs: Long, moving: Boolean, tripKm: Float, edgeKm: Float): Boolean? {
        if (angle == null) return null
        val nowTurning = abs(angle) >= steerDeg
        val started = nowTurning && !turning
        turning = nowTurning
        if (!started || !moving || tripKm < edgeKm) return null
        val sig = signal ?: return null
        val left = angle > 0
        val want = if (left) TurnSignalValue.LEFT else TurnSignalValue.RIGHT
        val onAt = if (left) leftOnAt else rightOnAt
        val used = sig == want || onAt?.let { nowMs - it <= graceMs } == true
        turns += Turn(tripKm, used)
        return used
    }

    /** 끝나기 전 [edgeKm] 안의 회전을 뺀 (깜빡이 켬, 안 켬) */
    fun counted(totalKm: Float, edgeKm: Float): Pair<Int, Int> {
        val kept = turns.filter { it.km <= totalKm - edgeKm }
        return kept.count { it.used } to kept.count { !it.used }
    }

    fun reset() {
        turns.clear()
        turning = false
    }
}
