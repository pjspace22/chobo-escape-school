package com.example.pleosstarter.school

/**
 * 학습 경로의 단원 (plan-school-duo-screens.md §3-1, 부록 B-3·B-12).
 *
 * @param testStars 단원 시험 통과에 필요한 기술 별 합 (+ 그 주행이 80점 이상 인정 주행)
 */
enum class PathUnit(val no: Int, val title: String, val testStars: Int, val guide: String) {
    // 판단(부록 G-1): "브레이크 밟고 출발" 노드를 빼서 기술이 1개 → 시험 별 4 → 2 (별 3개 중 2개, 다른 단원과 같은 2/3 비율)
    START(1, "출발과 정차", 2, "기어를 바꿀 땐 늘 브레이크부터. 출발과 정차의 기본이에요"),
    SIGNAL(2, "깜빡이", 6, "깜빡이는 내 생각을 뒤차와 옆차에 알리는 말이에요"),
    WEATHER(3, "날씨와 밤", 4, "비·밤·김 서림처럼 앞이 잘 안 보일 때 지킬 것들이에요"),
    PARKING(4, "주정차 규칙", 4, "세워도 되는 자리와 안 되는 자리를 알아봐요. 기어를 P에 두면 앱이 자리를 봐 줘요"),
    // 부록 I-5 새 단원. 단원 2 시험을 통과하면 열림. 판단: 단원 번호 5 (위험 피하기는 6으로 밀림),
    // 시험 별 9 (만점 13 = 3 + 3 + 3 + 3 + 1, 다른 단원과 같은 2/3 비율)
    SENSE(5, "운전 센스", 9, "멀리 보고 미리 줄이고, 한 번에 하나씩. 같이 탄 사람이 편한 운전을 배워요"),
    // 졸업생 심화 과정 (부록 G-4). 졸업하거나 Lv.10이면 열림. 판단: 시험 별 5 (만점 7 = 3 + 1 + 3)
    DEFENSIVE(6, "위험 피하기", 5, "갑자기 막히거나 끼어들거나 미끄러질 때, 몸이 먼저 움직이도록 미리 배워요"),
    ;

    /** 이 단원을 여는 앞 단원 시험 (위험 피하기는 졸업·레벨로 열려 null) */
    val opensAfter: PathUnit?
        get() = when (this) {
            START, DEFENSIVE -> null
            SENSE -> SIGNAL
            else -> values()[ordinal - 1]
        }

    val skills: List<Skill> get() = Skill.values().filter { it.unit == this }

    /** 단원에서 모을 수 있는 별 합 */
    val maxStars: Int get() = skills.sumOf { it.maxStars }

    /** 잠겨 있을 때 보이는 한 줄 */
    val lockHint: String
        get() = opensAfter?.let { "단원 ${it.no} 시험을 통과하면 열려요" }
            ?: "졸업하거나 Lv.${LearningPath.DEFENSIVE_LEVEL}이 되면 열려요"
}

/** 공식 출처가 없는 카드의 출처 표시 (부록 I-3: 공식 출처 없음을 그대로) */
internal const val GENERAL_TIP = "일반 운전 요령"

/** 카드 3장이 같은 출처 */
private fun same(src: String) = List(3) { src }

/** 별 조건에 쓰는 "내 평균" (지난 인정 주행 5번, 3번 미만이면 null. 부록 I-1) */
data class TripHistory(val brakesPerKm: Float? = null, val stopDistM: Float? = null)

/**
 * 경로의 기술 노드 하나 = 코치 묶음.
 *
 * @param nextStar 말풍선의 "다음 별" 조건 한 줄
 * @param cards 미리 배우기 3장: 왜? / 어떻게? / 앱이 보는 것 (부록 A 문구 그대로)
 * @param cardTitles 카드마다 제목이 따로 있으면 (단원 5, 부록 G-4-1). 없으면 왜?/어떻게?/앱이 보는 것
 * @param sources 카드 아래 작은 회색 출처 (단원 5)
 */
enum class Skill(
    val unit: PathUnit,
    val icon: String,
    val title: String,
    val nextStar: String,
    val cards: List<String>,
    val cardTitles: List<String>? = null,
    val sources: List<String>? = null,
) {
    // 부록 I-2 L8: 판정(P 넣을 때 브레이크)에 맞게 이름을 "세우고 P 넣기"로. "부드럽게 세우기"는 단원 운전 센스의 새 기술
    SOFT_STOP(
        PathUnit.START, "🛑", "세우고 P 넣기", "인정 주행에서 브레이크를 밟고 P 넣기",
        listOf(
            "세울 때 브레이크를 밟고 있어야 차가 굴러가지 않아요",
            "완전히 선 다음, 브레이크를 밟은 채로 P를 넣어요",
            "P를 넣는 순간 브레이크가 밟혀 있었는지 봐요",
        ),
        sources = same("현대 사용설명서 '변속 위치 선택'"),
    ),
    SIGNAL_FIRST(
        PathUnit.SIGNAL, "↪", "꺾기 전에 켜기", "인정 주행에서 꺾을 때마다 깜빡이 먼저 켜기",
        listOf(
            "뒤차와 옆차는 깜빡이로만 내 생각을 알 수 있어요",
            "핸들 왼쪽 레버: 왼쪽은 아래로, 오른쪽은 위로",
            "핸들이 크게 돌 때 깜빡이가 먼저 켜져 있었는지 봐요",
        ),
        sources = same("도로교통법 제38조, 시행령 별표 2"),
    ),
    SIGNAL_OFF(
        PathUnit.SIGNAL, "💡", "켜고 잊지 않기", "인정 주행에서 깜빡이를 쓰고, 켜 둔 채 잊지 않기",
        listOf(
            "켜 둔 깜빡이는 \"곧 돌아요\"라는 거짓말이 돼요",
            "다 돈 뒤 레버가 안 돌아오면 손으로 가운데로",
            "핸들이 똑바른데 깜빡이가 오래 켜져 있으면 알려 줘요",
        ),
        sources = same("도로교통법 제38조, 면허시험 채점기준(시행규칙 별표 26)"),
    ),
    LEFT_WAIT(
        PathUnit.SIGNAL, "⬅", "좌회전 기다릴 때", "인정 주행에서 좌회전 기다리며 깜빡이 켜기",
        listOf(
            "멈춰 있을 때 켜야 뒤차가 미리 차로를 바꿀 수 있어요",
            "서서 기다리는 동안 왼쪽 깜빡이를 켜고, 핸들은 똑바로",
            "멈춰 서 있고 핸들이 왼쪽인데 깜빡이가 꺼져 있으면 알려 줘요",
        ),
        sources = same("도로교통법 제38조"),
    ),
    RAIN_DAY(
        PathUnit.WEATHER, "🌧", "비 오는 날", "인정 주행에서 와이퍼를 켜고 전조등도 같이 켜기",
        listOf(
            "비 오면 앞이 흐리고 바퀴가 잘 미끄러져요",
            "와이퍼를 켜고, 낮이어도 전조등을 켜요",
            "와이퍼가 켜졌는지, 그때 전조등도 켜졌는지 봐요",
        ),
        sources = same("도로교통법 제37조, 시행규칙 제19조"),
    ),
    NIGHT_ROAD(
        PathUnit.WEATHER, "🌙", "밤길", "인정 주행에서 전조등을 켜고 상향등·실내등 코치 0번",
        listOf(
            "밤에는 앞이 잘 안 보이고, 다른 차도 나를 잘 못 봐요",
            "어두워지면 전조등, 마주 오는 차가 있으면 상향등은 끄기",
            "전조등·상향등·실내등이 켜졌는지 봐요",
        ),
        sources = same("도로교통법 제37조, 시행령 제20조, 현대 사용설명서 '실내등'"),
    ),
    FOG_CLEAR(
        PathUnit.WEATHER, "🌫", "김 서림", "김 서림 코치를 듣고 외기·에어컨·성에 제거로 바꾸기 (연습 주행도 돼요)",
        listOf(
            "히터만 틀고 내기순환이면 유리에 김이 서려요",
            "외기로 바꾸거나 에어컨·성에 제거를 켜요",
            "공조가 내기순환·히터만인 채로 오래 가는지 봐요",
        ),
        sources = same("현대 사용설명서 '김 서림 제거'"),
    ),
    // 주정차 단원: 문구·숫자는 plan-school-duo-screens.md 부록 C-2 (정책브리핑 2023-08-10 기준)
    ZONE_KNOW(
        PathUnit.PARKING, "🅿", "6대 구역 알기", "인정 주행을 마치고 안전한 자리에 세우기 (판정 🟢)",
        listOf(
            "주민신고는 1분이면 돼요",
            "소화전 5m·버스정류장 10m·교차로 5m·횡단보도·어린이보호구역·인도",
            "세운 자리가 이 구역 근처인지 지도 자료로 봐요",
        ),
        sources = same("정책브리핑 2023-08-10"),
    ),
    RISK_AVOID(
        PathUnit.PARKING, "🚫", "위험 자리 피하기", "🔴·🟠 판정 뒤 [주차장 안내]를 고르거나, 5분 안에 옮겨 다시 세워 🟢",
        listOf(
            "잠깐도 신고될 수 있어요",
            "카드가 빨갛게 뜨면 공영주차장으로 옮겨요",
            "판정 뒤 무엇을 골랐는지 봐요",
        ),
        sources = same("정책브리핑 2023-08-10"),
    ),
    // 단원 "운전 센스" (부록 I-5). 등급·출처는 CoachBasis와 같은 내용
    SMOOTH_STOP(
        PathUnit.SENSE, "🪶", "부드럽게 세우기", "인정 주행에서 브레이크~멈춤 거리가 내 평균 이상 (일찍·약하게, 추정)",
        listOf(
            "일찍 약하게 밟고, 멈추기 직전에 살짝 풀어요",
            "면허시험도 급브레이크는 감점이에요",
            "앱은 브레이크를 밟은 곳부터 선 곳까지 거리를 봐요 (주행거리로 어림한 추정)",
        ),
        cardTitles = listOf("일찍, 약하게", "급브레이크는 감점", "앱이 보는 것"),
        sources = listOf("현대 사용설명서 '경제적 운전' · 일반 운전 요령", "도로교통법 시행규칙 별표 26", "앱 판정 (추정)"),
    ),
    EASE_OFF(
        PathUnit.SENSE, "🐢", "미리 줄이기", "인정 주행에서 내비 회전 절반 이상을 30m 넘게 앞에서 줄이기",
        listOf(
            "회전할 때는 미리 천천히 (서행)",
            "빨간불이 보이면 미리 가속 페달에서 발을 떼요",
            "앱은 회전 앞에서 브레이크를 처음 밟은 곳을 봐요. 30m보다 앞이면 ○",
        ),
        cardTitles = listOf("회전은 서행", "빨간불이면 미리 발 떼기", "앱이 보는 것"),
        sources = listOf("도로교통법 제25조 · 시행규칙 별표 26", "한국교통안전공단 경제운전 '관성주행'", "앱 판정 (계산: 내비 남은 거리)"),
    ),
    ONE_LANE(
        PathUnit.SENSE, "↔", "한 번에 한 차로", "인정 주행에서 차로 변경 1번 이상, 연달아 두 차로 0번 (추정)",
        listOf(
            "차로는 한 번에 하나씩 바꿔요",
            "꼭 필요할 때만 바꾸면 덜 위험하고 연비도 좋아요",
            "앱은 깜빡이와 핸들로 차로 변경을 어림해요. 차선은 못 봐요",
        ),
        cardTitles = listOf("한 번에 하나", "필요할 때만", "앱이 보는 것"),
        sources = listOf("도로교통법 제19조 3항 · 시행규칙 별표 26", "한국교통안전공단 경제운전", "앱 판정 (추정: 깜빡이+핸들)"),
    ),
    MERGE_SPEED(
        PathUnit.SENSE, "⤴", "합류 속도 맞추기", "합류 1번 이상, 마지막 100m에서 브레이크 0번",
        listOf(
            "가속 차로에서 본선 속도에 맞춰 들어가요",
            "본선을 달리는 차의 길을 막지 않아요",
            "앱은 합류 마지막 100m에서 브레이크를 밟았는지 봐요",
        ),
        cardTitles = listOf("속도 맞추기", "본선 우선", "앱이 보는 것"),
        sources = listOf("도로교통공단 안내 (원문 미열람)", "도로교통법 제65조 1항", "앱 판정 (계산: 내비 합류 거리)"),
    ),
    MANNERS(
        PathUnit.SENSE, "🤝", "길 위 매너", "미리 배우기 카드 3장을 다 읽기 (별 1개)",
        listOf(
            "꼬리물기·경적 계속 울리기는 안 돼요. 1차로는 추월할 때만 써요",
            "양보받으면 손을 들어 고마움을 표시해요. 비상등은 관습이지 법 신호가 아니에요",
            "같이 탄 사람이 흔들리지 않게 부드럽게. 면허시험도 급브레이크는 감점이에요",
        ),
        cardTitles = listOf("하지 않을 것", "고마움은 손으로", "같이 탄 사람 생각"),
        sources = listOf(
            "도로교통법 제25조 5항 · 제49조 1항 8호 · 시행규칙 별표 9",
            "정책브리핑 2021-08-12",
            "도로교통법 시행규칙 별표 26 · 현대 사용설명서",
        ),
    ),
    // 위험 피하기 단원: 카드 문구·출처는 plan-school-duo-screens.md 부록 G-4-1 (기획팀 11/8)
    SUDDEN_STOP(
        PathUnit.DEFENSIVE, "🚨", "앞이 갑자기 막힐 때",
        "인정 주행에서 1km당 브레이크가 지난 5번 평균보다 적기 (덜 밟았다 = 멀리 보고 거리를 둔 간접 신호)",
        listOf(
            "시속 60이면 45m, 고속도로 시속 100이면 100m쯤 떨어져요",
            "젖은 길은 서는 데 1.8배 더 걸려요. 거리도 두 배로, 속도는 20% 줄여요",
            "브레이크를 밟는 동시에 옆 차선이 비었는지 봐요. 비상등을 켜 뒤차에 알려요",
        ),
        cardTitles = listOf("속도만큼 거리", "비 오면 두 배", "밟으면서 옆을 봐요"),
        // 부록 I-3: 공식 출처로 바꿈. ①-1 공단 웹진 원문은 미열람(기획서 인용 전 재확인)
        sources = listOf(
            "도로교통공단 안전거리 안내 · 도로교통법 제19조 1항",
            "한국교통안전공단 보도자료 2025-07-18 · 시행규칙 제19조",
            GENERAL_TIP,
        ),
    ),
    CUT_IN(
        PathUnit.DEFENSIVE, "↘", "옆 차가 끼어들 때", "미리 배우기 카드 3장을 다 읽기 (별 1개)",
        listOf(
            "끼어드는 차를 보면 경적보다 먼저 가속 페달에서 발을 떼요. 공간이 생겨요",
            "옆 차의 비스듬히 뒤는 그 운전자가 못 보는 자리예요. 빨리 지나가거나 뒤로 빠져요",
            "트럭·버스는 못 보는 자리가 넓어요. 옆에 나란히 달리지 말아요",
        ),
        cardTitles = listOf("경적보다 발", "옆 차 뒤에 오래 있지 않기", "큰 차 옆은 짧게"),
        sources = listOf(GENERAL_TIP, GENERAL_TIP, GENERAL_TIP),
    ),
    SKID(
        PathUnit.DEFENSIVE, "🧊", "미끄러질 때", "와이퍼를 켠 인정 주행에서 핸들 흔들림 코치 0번",
        listOf(
            "젖은 길은 최고속도의 20%, 앞이 100m도 안 보이는 폭우면 50% 줄여요",
            "급하게 서야 하면 브레이크를 끝까지 꾹 밟고 떼지 마세요. 핸들로 피할 수 있어요",
            "차가 돌면 놀라서 반대로 꺾기 쉬워요. 가고 싶은 쪽을 보고 핸들을 천천히",
        ),
        cardTitles = listOf("비 오면 20%, 폭우면 절반", "ABS는 꾹", "가고 싶은 쪽을 봐요"),
        sources = listOf("도로교통법 시행규칙 제19조 2항", "현대자동차 사용설명서 \"ABS\"", GENERAL_TIP),
    ),
    ;

    /** 주행으로 판정할 수 없어 미리 배우기 카드 3장을 다 읽으면 별 1개 (끼어들기 G-4, 길 위 매너 I-5) */
    val cardOnly: Boolean get() = this == CUT_IN || this == MANNERS

    /** 별을 받으려면 인정 주행이어야 하는지. 김 서림·주정차 판정·카드 읽기는 예외 (부록 B-2, G-4) */
    val needsQualified: Boolean get() = this != FOG_CLEAR && this != RISK_AVOID && !cardOnly

    /** 이 기술의 별 최대 개수. 카드만 기술은 1개 */
    val maxStars: Int get() = if (cardOnly) 1 else LearningPath.MAX_STARS

    /** 별 판정 등급 (부록 I-1) */
    val grade: Grade
        get() = when (this) {
            ZONE_KNOW, RISK_AVOID, EASE_OFF, MERGE_SPEED -> Grade.CALC
            SMOOTH_STOP, ONE_LANE -> Grade.EST
            CUT_IN, MANNERS -> Grade.CARD
            else -> Grade.DIRECT
        }

    /**
     * 이 주행이 별 +1 조건을 채웠는지 (부록 B-2 표, I-2·I-5).
     * @param h 지난 인정 주행 5번 평균 (3번 미만이면 비교 못 해 별 없음)
     */
    fun metBy(r: TripResult, h: TripHistory = TripHistory()): Boolean = when (this) {
        SOFT_STOP -> r.endPoints == r.endMax
        SIGNAL_FIRST -> r.turns > 0 && r.turnsWithoutSignal == 0
        SIGNAL_OFF -> r.turnsWithSignal >= 1 && r.signalLeftOn == 0
        LEFT_WAIT -> r.leftWaitOk
        RAIN_DAY -> r.rain && Coach.RAIN_NO_HEADLIGHT !in r.coaches
        NIGHT_ROAD -> r.night && NIGHT_MISTAKES.none { it in r.coaches }
        FOG_CLEAR -> r.fogFixed
        ZONE_KNOW -> r.parkedSafe
        RISK_AVOID -> false // 판정 카드에서 고른 것으로 따로 준다 (SchoolProgress.awardSkill)
        // L9: 지난 5번 평균보다 1km당 브레이크가 적음
        SUDDEN_STOP -> r.brakesPerKm.let { b -> b != null && h.brakesPerKm != null && b < h.brakesPerKm }
        CUT_IN, MANNERS -> false // 미리 배우기 카드 3장을 다 읽으면 따로 준다
        SKID -> r.rain && Coach.WOBBLE !in r.coaches
        SMOOTH_STOP -> r.sense.stopDistM.let { d -> d != null && h.stopDistM != null && d >= h.stopDistM }
        EASE_OFF -> r.sense.naviTurns >= 1 && r.sense.earlyTurns * 2 >= r.sense.naviTurns
        ONE_LANE -> r.sense.laneChanges >= 1 && r.sense.doubleLaneChanges == 0
        MERGE_SPEED -> r.sense.merges >= 1 && r.sense.mergeBrakes == 0
    }

    /** 말풍선의 "지난 주행" 한 줄 */
    fun lastLine(r: TripResult): String = "지난 주행: " + when (this) {
        SOFT_STOP -> when (r.endBrake) {
            true -> "브레이크 밟고 P ○"
            false -> "브레이크 없이 P ×"
            null -> "정차 순간은 못 봤어요"
        }
        SIGNAL_FIRST -> if (r.turns == 0) "크게 꺾은 적 없음" else "꺾은 ${r.turns}번 중 깜빡이 먼저 ${r.turnsWithSignal}번"
        SIGNAL_OFF -> "깜빡이 방치 ${r.signalLeftOn}번"
        LEFT_WAIT -> if (r.leftWaitOk) "기다리며 깜빡이 켬 ○" else "기다리며 켠 적 없음"
        RAIN_DAY -> when {
            !r.rain -> "와이퍼 안 켬"
            Coach.RAIN_NO_HEADLIGHT in r.coaches -> "와이퍼 ○ · 전조등 ×"
            else -> "와이퍼 ○ · 전조등 ○"
        }
        NIGHT_ROAD -> when {
            !r.night -> "전조등 안 켬"
            NIGHT_MISTAKES.any { it in r.coaches } -> "전조등 ○ · 상향등·실내등 코치 받음"
            else -> "전조등 ○ · 코치 0번"
        }
        FOG_CLEAR -> if (r.fogFixed) "김 서림 해결 ○" else "김 서림 해결 없음"
        ZONE_KNOW -> if (r.parkedSafe) "안전한 자리에 세움 🟢" else "🟢 자리에 세우지 못함"
        RISK_AVOID -> "정차 기록에서 판정을 볼 수 있어요"
        SUDDEN_STOP -> r.brakesPerKm?.let { b -> "1km에 브레이크 ${"%.1f".format(b)}번" } ?: "거리가 짧아 재지 못했어요"
        CUT_IN, MANNERS -> "카드를 읽으면 별이 올라요"
        SMOOTH_STOP -> r.sense.stopDistM?.let { d -> "브레이크~멈춤 평균 ${d.toInt()}m (추정)" } ?: "멈춘 적이 없어요"
        EASE_OFF -> if (r.sense.naviTurns == 0) "내비 회전이 없었어요" else "내비 회전 ${r.sense.naviTurns}번 중 미리 줄임 ${r.sense.earlyTurns}번"
        ONE_LANE -> "차로 변경 ${r.sense.laneChanges}번 · 연달아 ${r.sense.doubleLaneChanges}번 (추정)"
        MERGE_SPEED -> if (r.sense.merges == 0) "합류가 없었어요" else "합류 ${r.sense.merges}번 중 마지막 100m 브레이크 ${r.sense.mergeBrakes}번"
        SKID -> when {
            !r.rain -> "와이퍼 안 켬"
            Coach.WOBBLE in r.coaches -> "와이퍼 ○ · 핸들 흔들림 코치 받음"
            else -> "와이퍼 ○ · 핸들 흔들림 0번"
        }
    }

    companion object {
        const val CARD_COUNT = 3
        val CARD_TITLES = listOf("왜?", "어떻게?", "앱이 보는 것")
        private val NIGHT_MISTAKES = setOf(Coach.HIGH_BEAM, Coach.NIGHT_NO_HEADLIGHT, Coach.CABIN_LIGHT)

        /** 기록 > 코치 카드에서 건너갈 기술 (부록 B-8). 연결 없는 코치는 null */
        fun of(c: Coach): Skill? = when (c) {
            Coach.WOBBLE -> SKID
            // T7(곧 좌회전 미리 켬)도 "꺾기 전에 켜기" (부록 E-6). R1은 판정 없는 안내라 노드 없음
            Coach.NO_SIGNAL_TURN, Coach.SIGNAL_TURN_OK, Coach.TURN_LEFT_SOON, Coach.TURN_LEFT_SOON_OK -> SIGNAL_FIRST
            Coach.SIGNAL_LEFT_ON -> SIGNAL_OFF
            Coach.LEFT_WAIT, Coach.LEFT_WAIT_OK, Coach.LEFT_WAIT_MISSED -> LEFT_WAIT
            Coach.RAIN, Coach.RAIN_NO_HEADLIGHT -> RAIN_DAY
            Coach.NIGHT, Coach.HIGH_BEAM, Coach.NIGHT_NO_HEADLIGHT, Coach.CABIN_LIGHT -> NIGHT_ROAD
            Coach.FOG, Coach.FOG_OK -> FOG_CLEAR
            else -> null
        }
    }
}

/** 경로 화면의 노드 하나 */
sealed class PathNode {
    data class SkillNode(val skill: Skill) : PathNode()
    data class Test(val unit: PathUnit) : PathNode()
    object Graduate : PathNode()
}

enum class NodeState { LOCKED, OPEN, PRACTICE, DONE }

/**
 * 별·잠금 계산 (부록 B-2·B-3). 저장은 [SchoolProgress]가 하고 여기는 계산만.
 *
 * - 별은 주행이 끝날 때 기술마다 최대 +1 (최대 3). 잠긴 단원의 기술은 오르지 않는다. 시연 모드는 한 번에 3개
 * - 단원 시험: 그 단원 별 합 ≥ [PathUnit.testStars] + 이번 주행이 80점 이상 인정 주행이면 주행 끝에 자동 통과
 * - 잠금(박현규 11/2): 앞 단원 시험을 통과해야 다음 단원이 열린다
 * - 졸업(🎓)은 잠기지 않는다. 합격 3번
 */
class LearningPath(
    private val starOf: (Skill) -> Int,
    private val passes: Int,
    private val passedUnits: Set<PathUnit>,
    private val level: Int = 1,
) {
    val nodes: List<PathNode> = PathUnit.values().flatMap { u ->
        u.skills.map { PathNode.SkillNode(it) } + PathNode.Test(u)
    } + PathNode.Graduate

    fun stars(s: Skill): Int = starOf(s).coerceIn(0, s.maxStars)

    val totalStars: Int get() = Skill.values().sumOf { stars(it) }

    fun unitStars(u: PathUnit): Int = u.skills.sumOf { stars(it) }

    fun unitPassed(u: PathUnit): Boolean = u in passedUnits

    fun unitOpen(u: PathUnit): Boolean = when (u) {
        PathUnit.START -> true
        // 단원 5는 앞 단원 시험이 아니라 졸업 또는 레벨로 열린다 (부록 G-4)
        PathUnit.DEFENSIVE -> passes >= SchoolProgress.GRADUATE_PASSES || level >= DEFENSIVE_LEVEL
        else -> u.opensAfter?.let { unitPassed(it) } ?: true
    }

    fun state(n: PathNode): NodeState = when (n) {
        is PathNode.SkillNode -> when {
            !unitOpen(n.skill.unit) -> NodeState.LOCKED
            stars(n.skill) >= n.skill.maxStars -> NodeState.DONE
            stars(n.skill) > 0 -> NodeState.PRACTICE
            else -> NodeState.OPEN
        }
        is PathNode.Test -> when {
            !unitOpen(n.unit) -> NodeState.LOCKED
            unitPassed(n.unit) -> NodeState.DONE
            else -> NodeState.OPEN
        }
        PathNode.Graduate -> when {
            passes >= SchoolProgress.GRADUATE_PASSES -> NodeState.DONE
            passes > 0 -> NodeState.PRACTICE
            else -> NodeState.OPEN
        }
    }

    /** 지금 할 기술 (부록 B-5 ①): 열린 단원에서 별 3이 아닌 첫 기술. 다 했으면 null */
    fun currentSkill(): Skill? =
        Skill.values().firstOrNull { unitOpen(it.unit) && stars(it) < it.maxStars }

    /** 경로 화면 가운데에 올 노드: 지금 할 기술 → 아직 못 넘은 시험 → 졸업 */
    fun current(): PathNode =
        currentSkill()?.let { PathNode.SkillNode(it) }
            ?: nodes.firstOrNull { it is PathNode.Test && state(it) == NodeState.OPEN }
            ?: PathNode.Graduate

    /** 이번 주행으로 별이 오를 기술 */
    fun starsEarned(r: TripResult, h: TripHistory = TripHistory()): List<Skill> = Skill.values().filter { s ->
        unitOpen(s.unit) && stars(s) < s.maxStars && (r.qualified || !s.needsQualified) && s.metBy(r, h)
    }

    companion object {
        const val MAX_STARS = 3
        /** 단원 5 "위험 피하기"가 졸업 전에도 열리는 레벨 (부록 G-4) */
        const val DEFENSIVE_LEVEL = 10
    }
}
