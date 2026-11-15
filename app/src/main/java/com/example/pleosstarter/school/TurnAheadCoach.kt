package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import kotlin.math.roundToInt

/** 내비 회전 안내 한 칸을 SDK 없이 다루려고 옮긴 것 */
data class TurnAhead(val dir: Dir, val distanceM: Double, val description: String) {
    /** EXIT_* = 고속도로 출구·분기(오른쪽/왼쪽/가운데), MERGE = 합류·진입 (부록 G-3) */
    enum class Dir { LEFT, RIGHT, EXIT_R, EXIT_L, EXIT_C, MERGE, OTHER }

    val exit: Boolean get() = dir == Dir.EXIT_R || dir == Dir.EXIT_L || dir == Dir.EXIT_C

    /** 연속 회전(X2)에서 "다음 회전" 쪽: 왼쪽이면 true, 오른쪽 false, 알 수 없으면 null */
    val leftSide: Boolean?
        get() = when (dir) {
            Dir.LEFT, Dir.EXIT_L -> true
            Dir.RIGHT, Dir.EXIT_R -> false
            else -> null
        }
}

/**
 * 내비 다음 회전을 보고 미리 알려 준다 (부록 E-6 T7, E-7 R1 / plan-school-coach.md §2-1-1).
 *
 * 남은 거리 = D0 − (지금 주행거리 − O0) × 1000.
 * 회전 안내가 오면 D0·O0을 새로 적는다. 단 내비가 같은 회전을 "더 먼 거리"로 다시 보내면(에뮬레이터는 차가 지도 위에서 안 움직여 늘 같은 값)
 * 앱이 줄여 온 값을 그대로 쓴다 → 실차는 내비 값, 에뮬레이터는 주행거리로 줄어든다.
 *
 * - T7: 좌회전·유턴 + 300m 이하 + 왼쪽 깜빡이 꺼짐 → 음성. 켜져 있으면 칭찬 글자. 회전 하나에 1번
 * - R1: 우회전 + 500m 이하 → "맨 오른쪽 차선으로"(음성). 100m 이하 + 오른쪽 깜빡이 꺼짐 → "오른쪽 깜빡이를 켜요". 켜져 있으면 칭찬
 * - X1 출구·분기: 1km 이하 "곧 오른쪽 출구예요…", 300m 이하 + 그쪽 깜빡이 꺼짐 "깜빡이를 켜요" (부록 G-3)
 * - X2 연속 회전: 다음 회전까지 200m 이하 + 이번 회전 300m 이하 → "꺾자마자 바로 ○회전이에요"
 * - X3 합류: 합류·진입 300m 이하
 * - 출구를 지나면 [takeExitPassed]로 알린다 (X4 진출로 놓침 판단은 SafetyDetector)
 * - 엔진이 음성 간격(10초)으로 받지 않으면 다음 1초에 다시 낸다 (남은 거리가 0이 되기 전까지)
 * 시각·주행거리는 호출하는 쪽이 넣는다 (SDK·Android 없이 로직만 확인할 수 있게).
 */
class TurnAheadCoach(
    private val leftM: Double = 300.0,
    private val laneM: Double = 500.0,
    private val rightSignalM: Double = 100.0,
    private val exitM: Double = 1_000.0,
    private val exitSignalM: Double = 300.0,
    private val twoTurnsM: Double = 200.0,
    private val mergeM: Double = 300.0,
) {
    /** R1 차선 안내를 할지 (졸업생 기본 끔, 설정에서 바꿈) */
    var laneGuide = true

    private var turns: List<TurnAhead> = emptyList()
    private var index = 0
    private var baseM = 0.0
    private var baseKm: Float? = null
    private var km: Float? = null
    private var signal: TurnSignalValue? = null

    // 지금 회전에서 이미 낸 것
    private val done = mutableSetOf<Coach>()
    // 앱 계산으로 지난 회전. 내비가 옛 목록을 다시 보내도(에뮬레이터) 되돌아가지 않게 앞에서 뺀다
    private val passed = mutableListOf<TurnAhead>()

    // 출구·분기 회전을 방금 지났는지 (X4)
    private var exitPassed = false
    /** 지난 회전 (운전 센스 "미리 줄이기·합류", 부록 I-5). [takePassed]로 읽으면 비운다 */
    private val justPassed = mutableListOf<TurnAhead>()

    /** 미리 켜서 칭찬받은 회전 수 (성적표 깜빡이 카드에 센다) */
    var earlyOk = 0
        private set

    /** 지금 회전에서 T7이 나왔는지 (그 회전엔 T3 좌회전 대기를 다시 말하지 않음) */
    val leftWarned: Boolean get() = Coach.TURN_LEFT_SOON in done || Coach.TURN_LEFT_SOON_OK in done

    val current: TurnAhead? get() = turns.getOrNull(index)

    /** 지금 회전까지 남은 거리(m). 안내가 없으면 null */
    fun remaining(): Double? {
        val t = current ?: return null
        val now = km ?: return baseM
        val start = baseKm ?: now
        return baseM - (now - start) * 1000.0
    }

    fun onTurns(all: List<TurnAhead>, nowKm: Float?) {
        if (nowKm != null) km = nowKm
        if (all.isEmpty()) {
            clearRoute()
            return
        }
        var skip = 0
        while (skip < all.size && passed.any { same(it, all[skip]) }) skip++
        val list = all.drop(skip)
        if (list.isEmpty()) return
        val first = list.first()
        val cur = current
        val left = remaining()
        // 같은 회전인데 내비 값이 앱이 줄여 온 값보다 크면(에뮬레이터) 무시
        if (cur != null && left != null && same(cur, first) && first.distanceM >= left) return
        if (cur == null || !same(cur, first)) done.clear()
        turns = list
        index = 0
        baseM = first.distanceM
        baseKm = km
    }

    fun onDistance(nowKm: Float?) {
        if (nowKm == null) return
        km = nowKm
        // 안내가 주행거리보다 먼저 왔으면(앱 켜자마자) 처음 받은 주행거리를 기준으로
        if (baseKm == null && turns.isNotEmpty()) baseKm = nowKm
    }

    fun onTurnSignal(value: TurnSignalValue?) {
        signal = value
    }

    fun clearRoute() {
        passed.clear()
        turns = emptyList()
        index = 0
        done.clear()
    }

    fun resetTrip() {
        earlyOk = 0
        justPassed.clear()
        done.clear()
        exitPassed = false
    }

    /** 출구·분기 회전을 지났으면 한 번 true (읽으면 지워짐) */
    fun takeExitPassed(): Boolean = exitPassed.also { exitPassed = false }

    /** 방금 지난 회전들 (읽으면 지워짐) */
    fun takePassed(): List<TurnAhead> = justPassed.toList().also { justPassed.clear() }

    /** 1초마다. 지금 낼 코치(없으면 null). 엔진이 받았으면 [accepted]를 불러 준다 */
    fun check(): Coach? {
        passIfDone()
        val t = current ?: return null
        val left = remaining() ?: return null
        // 깜빡이 안내가 먼저, 그다음 연속 회전·차선 안내 (판단: 지금 손이 해야 할 일부터)
        val own = when (t.dir) {
            TurnAhead.Dir.LEFT -> when {
                left > leftM || leftWarned -> null
                signal == TurnSignalValue.LEFT -> Coach.TURN_LEFT_SOON_OK
                else -> Coach.TURN_LEFT_SOON
            }
            TurnAhead.Dir.RIGHT -> when {
                left <= rightSignalM && Coach.RIGHT_SIGNAL !in done && Coach.RIGHT_SIGNAL_OK !in done ->
                    if (signal == TurnSignalValue.RIGHT) Coach.RIGHT_SIGNAL_OK else Coach.RIGHT_SIGNAL
                // 100m 안에 들어왔으면 차선 안내는 건너뛴다 (깜빡이 안내만)
                laneGuide && left <= laneM && left > rightSignalM && Coach.RIGHT_LANE !in done -> Coach.RIGHT_LANE
                else -> null
            }
            TurnAhead.Dir.EXIT_R, TurnAhead.Dir.EXIT_L, TurnAhead.Dir.EXIT_C -> exitCoach(t, left)
            TurnAhead.Dir.MERGE -> if (left <= mergeM && Coach.MERGE !in done) Coach.MERGE else null
            TurnAhead.Dir.OTHER -> null
        }
        return own ?: twoTurns(left)
    }

    // X1: 1km 안이면 차선 안내 한 번, 300m 안 + 그쪽 깜빡이 꺼짐이면 깜빡이 안내 (켜져 있으면 칭찬)
    private fun exitCoach(t: TurnAhead, left: Double): Coach? {
        val signalDone = Coach.EXIT_SIGNAL_R in done || Coach.EXIT_SIGNAL_L in done || Coach.EXIT_SIGNAL_OK in done
        if (left <= exitSignalM && !signalDone && t.dir != TurnAhead.Dir.EXIT_C) {
            val want = if (t.dir == TurnAhead.Dir.EXIT_R) TurnSignalValue.RIGHT else TurnSignalValue.LEFT
            return when {
                signal == want -> Coach.EXIT_SIGNAL_OK
                t.dir == TurnAhead.Dir.EXIT_R -> Coach.EXIT_SIGNAL_R
                else -> Coach.EXIT_SIGNAL_L
            }
        }
        val lane = when (t.dir) {
            TurnAhead.Dir.EXIT_R -> Coach.EXIT_SOON_R
            TurnAhead.Dir.EXIT_L -> Coach.EXIT_SOON_L
            else -> Coach.EXIT_SOON_C
        }
        // 300m 안에 처음 들어왔으면(내비를 늦게 켬) 차선 안내는 건너뛴다
        return if (left <= exitM && left > exitSignalM && lane !in done) lane else null
    }

    // X2: 다음 회전이 이번 회전 뒤 200m 안이고, 이번 회전까지 300m 안
    private fun twoTurns(left: Double): Coach? {
        val next = turns.getOrNull(index + 1) ?: return null
        if (left > leftM || next.distanceM > twoTurnsM) return null
        val c = when (next.leftSide) {
            true -> Coach.TWO_TURNS_L
            false -> Coach.TWO_TURNS_R
            null -> return null
        }
        return if (c !in done) c else null
    }

    fun accepted(c: Coach) {
        done += c
        if (c == Coach.TURN_LEFT_SOON_OK || c == Coach.RIGHT_SIGNAL_OK || c == Coach.EXIT_SIGNAL_OK) earlyOk++
    }

    /** 주행 화면 아래 한 줄. 좌·우회전이 1km 안일 때만 */
    fun line(): String? {
        val t = current ?: return null
        val left = remaining() ?: return null
        if (left <= 0 || left > 1_000) return null
        val m = (left / 10).roundToInt() * 10
        return when (t.dir) {
            TurnAhead.Dir.LEFT -> "↰ 좌회전까지 ${m}m" + (if (t.description.isNotBlank()) " · ${t.description}" else "")
            TurnAhead.Dir.RIGHT -> "↱ 우회전까지 ${m}m" + if (laneGuide) " · 맨 오른쪽 차선" else ""
            TurnAhead.Dir.EXIT_R -> "↗ 오른쪽 출구까지 ${m}m · 오른쪽 차선"
            TurnAhead.Dir.EXIT_L -> "↖ 왼쪽 출구까지 ${m}m · 왼쪽 차선"
            TurnAhead.Dir.EXIT_C -> "↑ 출구까지 ${m}m"
            TurnAhead.Dir.MERGE -> "⤴ 합류까지 ${m}m"
            TurnAhead.Dir.OTHER -> null
        }
    }

    // 남은 거리가 0 이하면 다음 회전으로. 목록 거리는 "앞 회전에서 그 회전까지" (11/3 실측: 80·380·730·1200·530…)
    private fun passIfDone() {
        while (true) {
            val left = remaining() ?: return
            if (left > 0) return
            val gone = current ?: return
            passed += gone
            justPassed += gone
            if (gone.exit) exitPassed = true
            val next = turns.getOrNull(index + 1)
            index++
            done.clear()
            if (next == null) {
                turns = emptyList()
                index = 0
                return
            }
            baseM = next.distanceM + left
            baseKm = km
        }
    }

    private fun same(a: TurnAhead, b: TurnAhead) = a.dir == b.dir && a.description == b.description
}
