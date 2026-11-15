package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import org.json.JSONObject
import kotlin.math.abs

/**
 * 한 주행의 "운전 센스" 값 (부록 I-5). 성적표 "운전 센스" 카드와 단원 "운전 센스" 별 조건에 쓴다.
 * 점수·합격·졸업에는 넣지 않는다. 추정(주행거리÷시간) 값은 화면에 "추정" 칩.
 *
 * @param rainKmh 와이퍼 켠 동안 평균 추정 속도 (11, 추정)
 * @param clearKmh 와이퍼 끈 동안 평균 추정 속도 (11의 "맑을 때", 추정)
 * @param microSteerPerMin 직진 1분당 핸들 방향 바꿈 ±2° 넘게 (15, 직접)
 * @param jamBrakesPerMin 내비 정체 구간 1분당 브레이크 (16, 직접+계산)
 * @param recircMaxMin 내기 순환이 이어진 가장 긴 시간(분) (20, 직접)
 * @param reverseMaxKmh 기어 R 동안 최고 추정 속도 (24, 추정)
 * @param stopDistM 브레이크 켠 순간 → 멈춤까지 평균 거리(m) ("부드럽게 세우기" 3, 추정)
 * @param coasts 브레이크 없이 3초 넘게 속도가 줄어든 횟수 (관성 주행 18, 추정)
 * @param naviTurns 지나간 내비 좌·우회전 수 / [earlyTurns] 그중 30m 넘게 앞에서 브레이크를 처음 밟은 수 ("미리 줄이기" 6, 계산)
 * @param laneChanges 추정 차로 변경 / [doubleLaneChanges] 10초 안 같은 방향 연속 ("한 번에 한 차로" 9, 추정)
 * @param merges 지나간 합류 / [mergeBrakes] 그중 마지막 100m에서 브레이크 밟은 수 ("합류 속도 맞추기" 14, 계산)
 */
data class SenseStats(
    val rainKmh: Float? = null,
    val clearKmh: Float? = null,
    val microSteerPerMin: Float? = null,
    val jamBrakesPerMin: Float? = null,
    val recircMaxMin: Float? = null,
    val reverseMaxKmh: Float? = null,
    val stopDistM: Float? = null,
    val coasts: Int = 0,
    val naviTurns: Int = 0,
    val earlyTurns: Int = 0,
    val laneChanges: Int = 0,
    val doubleLaneChanges: Int = 0,
    val merges: Int = 0,
    val mergeBrakes: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        rainKmh?.let { put("rain", it.toDouble()) }
        clearKmh?.let { put("clear", it.toDouble()) }
        microSteerPerMin?.let { put("micro", it.toDouble()) }
        jamBrakesPerMin?.let { put("jam", it.toDouble()) }
        recircMaxMin?.let { put("recirc", it.toDouble()) }
        reverseMaxKmh?.let { put("rev", it.toDouble()) }
        stopDistM?.let { put("stop", it.toDouble()) }
        put("coast", coasts)
        put("nt", naviTurns)
        put("et", earlyTurns)
        put("lc", laneChanges)
        put("dlc", doubleLaneChanges)
        put("mg", merges)
        put("mb", mergeBrakes)
    }

    companion object {
        fun fromJson(o: JSONObject?): SenseStats {
            if (o == null) return SenseStats()
            fun f(k: String): Float? = if (o.has(k)) o.getDouble(k).toFloat() else null
            return SenseStats(
                rainKmh = f("rain"), clearKmh = f("clear"), microSteerPerMin = f("micro"), jamBrakesPerMin = f("jam"),
                recircMaxMin = f("recirc"), reverseMaxKmh = f("rev"), stopDistM = f("stop"),
                coasts = o.optInt("coast", 0), naviTurns = o.optInt("nt", 0), earlyTurns = o.optInt("et", 0),
                laneChanges = o.optInt("lc", 0), doubleLaneChanges = o.optInt("dlc", 0),
                merges = o.optInt("mg", 0), mergeBrakes = o.optInt("mb", 0),
            )
        }
    }
}

/** 1초마다 [DriveSenseMeter.tick]에 넣는 지금 상태 */
data class SenseTick(
    val moving: Boolean,
    val speedKmh: Float?,
    val reverse: Boolean,
    val wiper: Boolean,
    val brake: Boolean?,
    val recirc: Boolean,
    val angle: Float?,
    /** 내비 다음 회전 (없으면 null)과 남은 거리 */
    val turn: TurnAhead?,
    val turnLeftM: Double?,
    val jam: Boolean,
    /** 주행 시작부터 달린 거리 (km) */
    val tripKm: Float,
)

/**
 * "운전 센스" 값을 한 주행 동안 모은다 (부록 I-5). 시각은 호출하는 쪽이 넣는다.
 * 기준값([가정])은 근거 화면에도 같은 숫자로 적는다.
 */
class DriveSenseMeter(
    /** 판단: 1분당 값은 [minMinuteSec]초 넘게 모였을 때만 (시연 모드 10초) */
    var minMinuteSec: Int = 60,
) {
    private var rainSum = 0f
    private var rainN = 0
    private var clearSum = 0f
    private var clearN = 0
    private var straightSec = 0
    private var microFlips = 0
    private var anchor: Float? = null
    private var steerDir = 0
    private var jamSec = 0
    private var jamBrakes = 0
    private var recircRun = 0
    private var recircMax = 0
    private var reverseMax: Float? = null
    private var stopFromKm: Float? = null
    private val stopDists = mutableListOf<Float>()
    private val speeds = ArrayDeque<Float>()
    private var coastArmed = true
    private var coasts = 0
    private val firstBrakeLeft = mutableMapOf<String, Double>()
    private var naviTurns = 0
    private var earlyTurns = 0
    private var laneDir: TurnSignalValue? = null
    private var laneAt = 0L
    private var lanePhase = 0
    private var lastLaneDir: TurnSignalValue? = null
    private var lastLaneAt = 0L
    private var laneChanges = 0
    private var doubleLaneChanges = 0
    private var lastPassedKm: Float? = null
    private var mergeWatch = false
    private var mergeBraked = false
    private var merges = 0
    private var mergeBrakes = 0
    private var last: SenseTick? = null

    fun reset() {
        rainSum = 0f; rainN = 0; clearSum = 0f; clearN = 0
        straightSec = 0; microFlips = 0; anchor = null; steerDir = 0
        jamSec = 0; jamBrakes = 0; recircRun = 0; recircMax = 0; reverseMax = null
        stopFromKm = null; stopDists.clear(); speeds.clear(); coastArmed = true; coasts = 0
        firstBrakeLeft.clear(); naviTurns = 0; earlyTurns = 0
        laneDir = null; lanePhase = 0; lastLaneDir = null; laneChanges = 0; doubleLaneChanges = 0
        lastPassedKm = null; mergeWatch = false; mergeBraked = false; merges = 0; mergeBrakes = 0
        last = null
    }

    /** 1초마다 */
    fun tick(t: SenseTick, motionKm: Float?) {
        last = t
        val v = t.speedKmh
        // 11 빗길 속도: 달리는 동안의 추정 속도를 와이퍼 켬/끔으로 나눠 평균
        if (t.moving && v != null && !t.reverse) {
            if (t.wiper) { rainSum += v; rainN++ } else { clearSum += v; clearN++ }
        }
        // 15 잔 조향: 직진(다음 회전 300m 넘게 또는 없음) + 달림 + |각도| < 15°에서 1초 표본, 방향이 ±2° 넘게 바뀐 횟수
        val straight = t.turnLeftM == null || t.turnLeftM > STRAIGHT_TURN_M
        val a = t.angle
        if (t.moving && straight && a != null && abs(a) < 15f && !t.reverse) {
            straightSec++
            val base = anchor
            if (base == null) anchor = a else {
                when {
                    a - base > MICRO_DEG -> { if (steerDir == -1) microFlips++; steerDir = 1; anchor = a }
                    base - a > MICRO_DEG -> { if (steerDir == 1) microFlips++; steerDir = -1; anchor = a }
                    steerDir == 1 && a > base -> anchor = a
                    steerDir == -1 && a < base -> anchor = a
                }
            }
        } else {
            anchor = null
            steerDir = 0
        }
        // 16 정체 길: 내비 정체 구간에 있는 시간
        if (t.jam && !t.reverse) jamSec++
        // 20 내기 순환: 이어진 시간
        if (t.recirc) { recircRun++; recircMax = maxOf(recircMax, recircRun) } else recircRun = 0
        // 24 후진 속도
        if (t.reverse && v != null) reverseMax = maxOf(reverseMax ?: 0f, v)
        // 3 부드럽게 세우기: 브레이크를 켠 뒤 2초 넘게 서면 "브레이크 켠 곳 → 선 곳" 거리
        val from = stopFromKm
        if (from != null && !t.moving && motionKm != null) {
            stopDists += ((motionKm - from) * 1000f).coerceAtLeast(0f)
            stopFromKm = null
        }
        // 18 관성: 브레이크 없이 추정 속도가 3초 연속 줄어듦 (한 번 세면 브레이크·멈춤 뒤에 다시)
        if (t.moving && t.brake == false && v != null) {
            speeds.addLast(v)
            while (speeds.size > 4) speeds.removeFirst()
            if (coastArmed && speeds.size == 4 && speeds.zipWithNext().all { (x, y) -> y < x }) {
                coasts++
                coastArmed = false
            }
        } else {
            speeds.clear()
            if (!t.moving || t.brake == true) coastArmed = true
        }
        // 14 합류: 마지막 100m를 지켜본다
        if (t.turn?.dir == TurnAhead.Dir.MERGE && (t.turnLeftM ?: Double.MAX_VALUE) <= MERGE_WATCH_M) mergeWatch = true
    }

    /** 브레이크를 새로 밟은 순간 */
    fun onBrakePress(moving: Boolean, motionKm: Float?) {
        val t = last
        if (t?.jam == true) jamBrakes++
        if (moving && stopFromKm == null && motionKm != null) stopFromKm = motionKm
        // 6 미리 줄이기: 회전 300m 안에서 처음 밟은 곳의 남은 거리
        val turn = t?.turn
        val left = t?.turnLeftM
        if (turn != null && left != null && left <= EASE_WATCH_M &&
            (turn.dir == TurnAhead.Dir.LEFT || turn.dir == TurnAhead.Dir.RIGHT)
        ) firstBrakeLeft.putIfAbsent(key(turn), left)
        if (mergeWatch) mergeBraked = true
    }

    /** 깜빡이가 켜진 순간 (9 차로 변경 지켜보기 시작) */
    fun onTurnSignal(value: TurnSignalValue?, nowMs: Long) {
        if (value == TurnSignalValue.LEFT || value == TurnSignalValue.RIGHT) {
            laneDir = value
            laneAt = nowMs
            lanePhase = 0
        }
    }

    /**
     * 핸들 알림마다 (9 차로 변경).
     * 깜빡이 켠 뒤 10초 안에 그쪽으로 3~30° 갔다가 직진(±3° 안)으로 돌아오면 차로 변경 1번 (앞뒤 100m 안에 내비 회전이 있으면 회전으로 보고 안 셈)
     */
    fun onSteering(angle: Float?, nowMs: Long) {
        val dir = laneDir ?: return
        val a = angle ?: return
        if (nowMs - laneAt > LANE_WINDOW_MS) { laneDir = null; return }
        val toward = if (dir == TurnSignalValue.LEFT) a else -a
        when {
            abs(a) >= 30f -> laneDir = null // 크게 꺾음 = 회전
            lanePhase == 0 && toward >= 3f -> lanePhase = 1
            lanePhase == 1 && abs(a) < 3f -> {
                laneDir = null
                if (!nearNaviTurn()) {
                    laneChanges++
                    if (lastLaneDir == dir && nowMs - lastLaneAt <= LANE_WINDOW_MS) doubleLaneChanges++
                    lastLaneDir = dir
                    lastLaneAt = nowMs
                }
            }
        }
    }

    private fun nearNaviTurn(): Boolean {
        val t = last ?: return false
        val ahead = t.turnLeftM?.let { it <= NAVI_NEAR_M } == true
        val behind = lastPassedKm?.let { (t.tripKm - it) * 1000f <= NAVI_NEAR_M } == true
        return ahead || behind
    }

    /** 내비 회전을 지났을 때 */
    fun onTurnPassed(turn: TurnAhead, tripKm: Float) {
        lastPassedKm = tripKm
        when (turn.dir) {
            TurnAhead.Dir.LEFT, TurnAhead.Dir.RIGHT -> {
                naviTurns++
                if ((firstBrakeLeft[key(turn)] ?: 0.0) >= EARLY_M) earlyTurns++
                firstBrakeLeft.remove(key(turn))
            }
            TurnAhead.Dir.MERGE -> {
                merges++
                if (mergeBraked) mergeBrakes++
                mergeWatch = false
                mergeBraked = false
            }
            else -> Unit
        }
    }

    fun result(): SenseStats = SenseStats(
        rainKmh = if (rainN > 0) rainSum / rainN else null,
        clearKmh = if (clearN > 0) clearSum / clearN else null,
        microSteerPerMin = if (straightSec >= minMinuteSec) microFlips * 60f / straightSec else null,
        jamBrakesPerMin = if (jamSec >= minMinuteSec) jamBrakes * 60f / jamSec else null,
        recircMaxMin = if (recircMax > 0) recircMax / 60f else null,
        reverseMaxKmh = reverseMax,
        stopDistM = if (stopDists.isEmpty()) null else stopDists.average().toFloat(),
        coasts = coasts,
        naviTurns = naviTurns,
        earlyTurns = earlyTurns,
        laneChanges = laneChanges,
        doubleLaneChanges = doubleLaneChanges,
        merges = merges,
        mergeBrakes = mergeBrakes,
    )

    private fun key(t: TurnAhead) = "${t.dir}|${t.description}"

    companion object {
        /** 직진으로 보는 다음 회전까지 거리 (15) */
        const val STRAIGHT_TURN_M = 300.0
        /** 잔 조향 한 번 = 방향이 이만큼 넘게 바뀜 (15) */
        const val MICRO_DEG = 2f
        /** 6 미리 줄이기: 회전 [EASE_WATCH_M] 안에서 처음 밟은 곳이 [EARLY_M] 넘게 앞이면 "미리" (판단: 지켜보는 거리 300m) */
        const val EASE_WATCH_M = 300.0
        const val EARLY_M = 30.0
        const val MERGE_WATCH_M = 100.0
        const val LANE_WINDOW_MS = 10_000L
        const val NAVI_NEAR_M = 100f
    }
}
