package com.example.pleosstarter.school

/**
 * 한 번의 주행(기어 D로 출발 → P로 정차) 결과와 점수 계산 (점수 v3, 부록 I-2 L1).
 *
 * 속도 신호가 없어서 "급가속·급제동" 대신, 신호로 확인되는 초보 습관을 본다.
 * - 핸들을 크게 꺾을 때마다 깜빡이를 먼저 켰는가 (50점, 꺾은 적이 없으면 기회 없음 = 0점) — 법 제38조
 * - 깜빡이를 켜 둔 채 방치하지 않았는가 (25점, 방치 0회 25 · 1회 12 · 2회 이상 0) — 법 제38조, 시행규칙 별표 26
 * - 정차할 때 브레이크를 밟은 채 P를 넣었는가 (25점) — 현대 사용설명서 '변속 위치 선택'
 * 출발 브레이크(예전 20점)는 뺐다: 현대차는 브레이크를 안 밟으면 P에서 R·D로 안 바뀐다(시프트 록) → 실차는 늘 만점.
 * 판단: 예전 기록(점수 v2)은 다시 계산하지 않고 그때 배점 그대로 보여 준다 ([scoring] = 2).
 *
 * 합격·졸업 횟수에는 "인정 주행"(거리 [QUALIFY_KM] 이상 + 핸들 꺾기 [QUALIFY_TURNS]회 이상)만 센다.
 * 그렇지 않으면 성적표에 "연습 주행"으로 표시한다 (가만히 출발·정차만 해서 합격하는 것을 막기 위해).
 * 시작 때 이미 D였던 경우처럼 값을 못 받은 브레이크 항목(null)은 감점하지 않는다.
 */
data class TripResult(
    val distanceKm: Float,
    val startBrake: Boolean?,
    val endBrake: Boolean?,
    val turnsWithSignal: Int,
    val turnsWithoutSignal: Int,
    /** 깜빡이를 켠 채 15초 넘게 방치한 횟수 */
    val signalLeftOn: Int = 0,
    /** 이번 주행에서 와이퍼를 켰는지 (첫 빗길 배지, 점수에는 안 들어감) */
    val rain: Boolean = false,
    /** 이번 주행에서 전조등을 켰는지 (첫 밤길 배지, 점수에는 안 들어감) */
    val night: Boolean = false,
    /** 김 서림 코치를 듣고 외기·에어컨·성에 제거로 고쳤는지 (김 서림 해결 배지, 점수에는 안 들어감) */
    val fogFixed: Boolean = false,
    /** 이번 주행에서 받은 코치 (학습 경로 별 조건에 쓴다) */
    val coaches: Set<Coach> = emptySet(),
    /** H1 핸들 안정도(%). 직진 표본이 모자라면 null */
    val steadiness: Int? = null,
    /** B1 1km당 브레이크 횟수. 인정 거리가 안 되면 null */
    val brakesPerKm: Float? = null,
    /** 기어 P 판정이 🟢였는지 (데이터 있는 지역에서, 주정차 단원 "6대 구역 알기" 별) */
    val parkedSafe: Boolean = false,
    /** 운전 센스 값 (부록 I-5, 점수에 안 씀) */
    val sense: SenseStats = SenseStats(),
    /** 점수 배점 판. 3 = 깜빡이 50·방치 25·P 25, 2 = 예전(출발 20·깜빡이 40·방치 20·P 20) */
    val scoring: Int = SCORING_V3,
) {
    val v3: Boolean get() = scoring >= SCORING_V3
    /** 항목 만점 (배점 판에 따라) */
    val startMax: Int get() = if (v3) 0 else 20
    val turnMax: Int get() = if (v3) TURN_POINTS else 40
    val leftOnMax: Int get() = if (v3) LEFT_ON_POINTS else 20
    val endMax: Int get() = if (v3) END_POINTS else 20

    /** 좌회전 기다리며 깜빡이를 켜 칭찬(T3+)을 받았는지 */
    val leftWaitOk: Boolean get() = Coach.LEFT_WAIT_OK in coaches

    val turns: Int get() = turnsWithSignal + turnsWithoutSignal

    val startPoints: Int get() = if (startBrake == false) 0 else startMax
    val endPoints: Int get() = if (endBrake == false) 0 else endMax
    val turnPoints: Int get() = if (turns == 0) 0 else turnMax * turnsWithSignal / turns
    val leftOnPoints: Int
        get() = when (signalLeftOn) {
            0 -> leftOnMax
            1 -> leftOnMax / 2
            else -> 0
        }

    val score: Int get() = startPoints + turnPoints + leftOnPoints + endPoints

    val enoughDistance: Boolean get() = distanceKm >= QUALIFY_KM
    val enoughTurns: Boolean get() = turns >= QUALIFY_TURNS

    /** 인정 주행: 합격·졸업 횟수와 "만점 운전" 배지에 들어가는 주행 */
    val qualified: Boolean get() = enoughDistance && enoughTurns

    val passed: Boolean get() = score >= PASS_SCORE

    /** 연습 주행이면 이유를 돌려준다 (인정 주행이면 빈 목록) */
    fun practiceReasons(): List<String> = buildList {
        if (!enoughDistance) add("${QUALIFY_KM}km 넘게 달려야 합격 기록에 들어가요")
        if (!enoughTurns) add("핸들을 ${QUALIFY_TURNS}번 이상 꺾어야 깜빡이 실력을 볼 수 있어요")
    }

    /** 항목별 한 줄 피드백: (잘했나, 문구) */
    fun feedback(): List<Pair<Boolean, String>> = listOfNotNull(
        if (!v3) when (startBrake) {
            true -> true to "출발할 때 브레이크를 밟고 D를 넣었어요 ($startPoints/$startMax)"
            false -> false to "출발할 때는 브레이크를 밟은 채로 D를 넣으세요 ($startPoints/$startMax)"
            null -> true to "출발 습관은 이번엔 확인하지 못했어요 ($startPoints/$startMax)"
        } else null,
        when {
            turns == 0 -> false to "핸들을 크게 꺾지 않아 깜빡이를 볼 기회가 없었어요 ($turnPoints/$turnMax)"
            turnsWithoutSignal == 0 -> true to "핸들을 꺾은 ${turns}번 모두 깜빡이를 먼저 켰어요 ($turnPoints/$turnMax)"
            else -> false to "핸들을 꺾은 ${turns}번 중 ${turnsWithoutSignal}번은 깜빡이 없이 꺾었어요 ($turnPoints/$turnMax)"
        },
        if (signalLeftOn == 0) true to "깜빡이를 켜 둔 채 잊지 않았어요 ($leftOnPoints/$leftOnMax)"
        else false to "깜빡이를 켜 둔 채 ${signalLeftOn}번 잊었어요 ($leftOnPoints/$leftOnMax)",
        when (endBrake) {
            true -> true to "정차할 때 브레이크를 밟고 P를 넣었어요 ($endPoints/$endMax)"
            false -> false to "정차할 때는 브레이크를 밟은 채로 P를 넣으세요 ($endPoints/$endMax)"
            null -> true to "정차 습관은 이번엔 확인하지 못했어요 ($endPoints/$endMax)"
        },
    )

    companion object {
        const val TURN_POINTS = 50
        const val LEFT_ON_POINTS = 25
        const val END_POINTS = 25
        const val SCORING_V3 = 3
        const val PASS_SCORE = 80
        const val QUALIFY_KM = 0.3f
        const val QUALIFY_TURNS = 2
    }
}
