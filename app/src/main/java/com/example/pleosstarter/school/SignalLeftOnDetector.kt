package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import kotlin.math.abs

/**
 * 깜빡이를 켠 뒤 핸들을 거의 안 돌린 채(직진) **주행 중에** [graceMs] 넘게 켜져 있으면 "방치 1회".
 * 한 번 켠 깜빡이는 꺼질 때까지 한 번만 센다.
 *
 * 주행 중 = 최근 [movingMs] 안에 주행거리가 늘었음 (부록 E-2, 속도 신호가 없어서 거리로 판단).
 * 신호 대기처럼 멈춰 있으면 시간을 멈춘다(0으로 되돌리지 않음). 다시 움직이면 이어서 센다.
 *
 * 깜빡이 변경 알림은 바뀔 때만 오므로, 화면 쪽에서 주기적으로 [check]를 불러 줘야 한다.
 * 시각은 호출하는 쪽이 넣는다 (SDK·Android 없이 로직만 확인할 수 있게).
 */
class SignalLeftOnDetector(
    private val straightDeg: Float = 30f,
    private val graceMs: Long = 15_000L,
    private val movingMs: Long = 3_000L,
) {
    var count = 0
        private set

    private var signalOn = false
    private var angle = 0f
    /** 직진·깜빡이 켜짐·주행 중으로 쌓은 시간 */
    private var heldMs = 0L
    private var lastUpdate: Long? = null
    private var lastKm: Float? = null
    private var movedAt: Long? = null
    private var counted = false

    fun onTurnSignal(value: TurnSignalValue?, nowMs: Long) {
        signalOn = value == TurnSignalValue.LEFT || value == TurnSignalValue.RIGHT
        if (!signalOn) counted = false
        update(nowMs)
    }

    fun onSteeringAngle(value: Float?, nowMs: Long) {
        if (value != null) angle = value
        update(nowMs)
    }

    /** 주행거리(km)가 올 때마다. 늘었으면 그 시각을 "움직임"으로 적는다 */
    fun onDistance(km: Float?, nowMs: Long) {
        if (km == null) return
        update(nowMs)
        val last = lastKm
        if (last != null && km > last) movedAt = nowMs
        lastKm = km
    }

    /** @return 방치로 새로 센 순간이면 true */
    fun check(nowMs: Long): Boolean {
        update(nowMs)
        if (counted || heldMs < graceMs) return false
        counted = true
        count++
        return true
    }

    fun reset() {
        count = 0
        counted = false
        heldMs = 0L
        lastUpdate = null
        movedAt = null
    }

    // 깜빡이가 켜져 있고 핸들이 직진 범위이고 주행 중일 때만 시간을 쌓는다.
    // 깜빡이를 끄거나 핸들을 크게 돌리면 처음부터 다시, 멈춰 있으면 그대로 둔다
    private fun update(nowMs: Long) {
        val straightOn = signalOn && abs(angle) < straightDeg
        val last = lastUpdate
        lastUpdate = nowMs
        if (!straightOn) {
            heldMs = 0L
            return
        }
        val moving = movedAt?.let { nowMs - it <= movingMs } ?: false
        if (last != null && moving) heldMs += nowMs - last
    }
}
