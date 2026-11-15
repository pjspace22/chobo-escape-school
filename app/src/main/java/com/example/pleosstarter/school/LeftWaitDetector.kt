package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue

/**
 * 좌회전 대기 중 깜빡이를 봤는지 판단한다 (코치 T3·T3+·T4).
 *
 * "서 있음" = 기어 D + 주행거리 3초 그대로 (부록 I-2 L4: 브레이크로 보면 오토홀드에서 못 잡음). 호출하는 쪽이 [onStanding]으로 넣는다.
 * - T3: 서 있고 + 핸들이 왼쪽으로 [leftAngle]° 이상 + 왼쪽 깜빡이 꺼짐
 * - T3+: 같은 상황에서 왼쪽 깜빡이를 켰음
 * - T4: 서 있다가 출발한 뒤 [moveWindowMs] 안에 왼쪽으로 [moveAngle]° 이상 꺾었는데,
 *   서 있는 동안 왼쪽 깜빡이를 한 번도 안 켰음
 * 핸들 각도는 앱 기준 왼쪽이 +이다 (콘솔과 부호가 반대).
 * 시각은 호출하는 쪽이 넣는다.
 */
class LeftWaitDetector(
    private val minStandMs: Long = 0L,
    private val leftAngle: Float = 10f,
    private val moveAngle: Float = 30f,
    private val moveWindowMs: Long = 5_000L,
) {
    private var standing: Boolean? = null
    private var standSince: Long? = null
    private var signal: TurnSignalValue? = null
    private var steering = 0f

    private var signalDuringStand = false
    private var releasedAt: Long? = null
    private var releasedWithSignal = false

    /** 1초마다: 지금 서 있는지 (D + 주행거리 3초 그대로) */
    fun onStanding(pressed: Boolean?, nowMs: Long) {
        val was = standing
        standing = pressed
        if (pressed == true && was != true) {
            standSince = nowMs
            signalDuringStand = signal == TurnSignalValue.LEFT
        } else if (pressed != true && was == true) {
            val since = standSince
            if (since != null && nowMs - since >= minStandMs) {
                releasedAt = nowMs
                releasedWithSignal = signalDuringStand
            }
            standSince = null
        }
    }

    fun onTurnSignal(value: TurnSignalValue?, nowMs: Long) {
        signal = value
        if (standSince != null && value == TurnSignalValue.LEFT) signalDuringStand = true
    }

    /** 핸들 알림마다 부른다. 서 있다 출발하며 놓친 좌회전(T4)이면 돌려준다. */
    fun onSteering(angle: Float?, nowMs: Long): Coach? {
        steering = angle ?: 0f
        val released = releasedAt ?: return null
        if (nowMs - released > moveWindowMs) {
            releasedAt = null
            return null
        }
        if (steering < moveAngle) return null
        releasedAt = null
        return if (releasedWithSignal) null else Coach.LEFT_WAIT_MISSED
    }

    /** 주기적으로(1초마다) 부른다. 좌회전 대기 중이면 T3 또는 T3+를 돌려준다. */
    fun check(nowMs: Long): Coach? {
        val since = standSince ?: return null
        if (standing != true || nowMs - since < minStandMs || steering < leftAngle) return null
        return if (signal == TurnSignalValue.LEFT) Coach.LEFT_WAIT_OK else Coach.LEFT_WAIT
    }

    fun reset() {
        standSince = null
        releasedAt = null
        signalDuringStand = false
        releasedWithSignal = false
        steering = 0f
        standing = null
    }
}
