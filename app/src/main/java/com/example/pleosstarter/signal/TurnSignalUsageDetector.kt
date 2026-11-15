package com.example.pleosstarter.signal

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import kotlin.math.abs

/**
 * "운전 나이" 해 보기용: 핸들을 크게 꺾기 시작한 순간에 방향지시등을 썼는지 센다.
 *
 * - 핸들 각도 절댓값이 [steerThresholdDeg] 이상이 되는 순간을 "꺾기 시작"으로 본다
 * - 그 순간 깜빡이가 켜져 있거나, [signalGraceMs] 안에 켜져 있었으면 "깜빡이 사용"
 *   (깜빡이를 먼저 켜고 끈 뒤 꺾는 경우, 깜빡임 때문에 잠깐 OFF가 오는 경우를 봐 줌)
 * - 깜빡이 값을 아직 못 받았으면(null) 판단하지 않는다
 *
 * 기준값은 임시. 에뮬레이터에서 각도 범위·부호를 보고 조정한다.
 * 시각은 호출하는 쪽이 넣는다 (SDK·Android 없이 로직만 확인할 수 있게).
 */
class TurnSignalUsageDetector(
    private val steerThresholdDeg: Float = 30f,
    private val signalGraceMs: Long = 3_000L,
) {
    var withSignal = 0
        private set
    var withoutSignal = 0
        private set

    private var signal: TurnSignalValue? = null
    private var lastSignalOnAt: Long? = null
    private var turning = false

    fun onTurnSignal(value: TurnSignalValue?, nowMs: Long) {
        signal = value
        if (value == TurnSignalValue.LEFT || value == TurnSignalValue.RIGHT) lastSignalOnAt = nowMs
    }

    /** @return 이번 호출에서 새로 센 결과(true = 깜빡이 사용). 꺾기 시작한 순간이 아니면 null */
    fun onSteeringAngle(angle: Float?, nowMs: Long): Boolean? {
        if (angle == null) return null
        val nowTurning = abs(angle) >= steerThresholdDeg
        val started = nowTurning && !turning
        turning = nowTurning
        val current = signal ?: return null
        if (!started) return null

        val used = current != TurnSignalValue.OFF ||
            lastSignalOnAt?.let { nowMs - it <= signalGraceMs } == true
        if (used) withSignal++ else withoutSignal++
        return used
    }

    fun reset() {
        withSignal = 0
        withoutSignal = 0
    }
}
