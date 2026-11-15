package com.example.pleosstarter.school

/**
 * 주행 중 코치 목록 (기획: materials/documents/plan-school-coach.md §2).
 *
 * @param label 성적표 "오늘 받은 코치"에 쓰는 짧은 이름
 * @param priority 1 위험 > 2 깜빡이 > 3 상황 > 4 안내. 숫자가 작을수록 우선
 * @param voice true면 음성(TTS)으로도 말한다. 칭찬은 글자만이라 false
 * @param good 칭찬(초록)이면 true, 고칠 것(빨강)이면 false, 상황 안내(노랑)면 null
 * @param oncePerTrip 한 주행에 한 번만 나온다
 * @param repeatGapMs 다시 나오려면 필요한 최소 간격 (oncePerTrip=false일 때)
 * @param recordOnly 화면·음성에는 안 나오고 성적표 기록에만 남긴다 (위험 계열은 message가 "오늘 배운 것" 설명)
 */
enum class Coach(
    val label: String,
    val message: String,
    val priority: Int,
    val voice: Boolean,
    val good: Boolean?,
    val oncePerTrip: Boolean = true,
    val repeatGapMs: Long = 0L,
    val recordOnly: Boolean = false,
) {
    NO_SIGNAL_TURN("깜빡이 없이 꺾음", "깜빡이부터 켜세요", 2, true, false, oncePerTrip = false, repeatGapMs = 20_000L),
    SIGNAL_TURN_OK("깜빡이 먼저 켬", "좋아요. 깜빡이를 먼저 켰어요", 2, false, true, oncePerTrip = false),
    LEFT_WAIT("좌회전 대기 중 깜빡이", "좌회전 기다리면 왼쪽 깜빡이 먼저. 핸들은 똑바로", 2, true, false),
    LEFT_WAIT_OK("좌회전 대기 중 깜빡이 켬", "좋아요. 뒤차가 알 수 있어요", 2, false, true),
    LEFT_WAIT_MISSED("멈춰서 기다릴 때 깜빡이 놓침", "", 2, false, false, recordOnly = true),
    // 내비 다음 회전 미리 알림 (부록 E-6 T7, E-7 R1). 회전 하나에 1번은 TurnAheadCoach가 지킨다
    TURN_LEFT_SOON("곧 좌회전인데 깜빡이 꺼짐", "곧 좌회전이에요. 깜빡이를 미리", 2, true, false, oncePerTrip = false),
    TURN_LEFT_SOON_OK("좌회전 전 깜빡이 미리 켬", "좋아요. 미리 켰어요", 2, false, true, oncePerTrip = false),
    RIGHT_LANE("우회전 차선 미리", "곧 우회전이에요. 맨 오른쪽 차선으로 미리 옮겨요", 4, true, null, oncePerTrip = false),
    RIGHT_SIGNAL("우회전 전 깜빡이", "오른쪽 깜빡이를 켜요", 2, true, false, oncePerTrip = false),
    RIGHT_SIGNAL_OK("우회전 전 깜빡이 미리 켬", "좋아요. 미리 켰어요", 2, false, true, oncePerTrip = false),
    // 부록 I-5 (7): 우회전 50m 안 + 주행 중, 한 주행 1번. 판정 없이 안내만. 보행자 안전이라 졸업생 조용 모드에서도 말함
    RIGHT_RED("우회전 빨간불 멈춤", "빨간불이면 횡단보도 앞에서 먼저 멈춰요", 3, true, null),
    // 고속도로 출구·분기·합류·연속 회전 (부록 G-3 X1·X2·X3). 회전 하나에 1번은 TurnAheadCoach가 지킨다
    EXIT_SOON_R("오른쪽 출구 미리", "곧 오른쪽 출구예요. 지금부터 오른쪽 차선으로", 2, true, null, oncePerTrip = false),
    EXIT_SOON_L("왼쪽 출구 미리", "곧 왼쪽 출구예요. 지금부터 왼쪽 차선으로", 2, true, null, oncePerTrip = false),
    EXIT_SOON_C("출구 미리", "곧 출구예요. 미리 차선을 맞춰요", 2, true, null, oncePerTrip = false),
    EXIT_SIGNAL_R("출구 전 오른쪽 깜빡이", "오른쪽 깜빡이를 켜요", 2, true, false, oncePerTrip = false),
    EXIT_SIGNAL_L("출구 전 왼쪽 깜빡이", "왼쪽 깜빡이를 켜요", 2, true, false, oncePerTrip = false),
    EXIT_SIGNAL_OK("출구 전 깜빡이 미리 켬", "좋아요. 미리 켰어요", 2, false, true, oncePerTrip = false),
    TWO_TURNS_L("연속 회전 (다음 좌회전)", "꺾자마자 바로 좌회전이에요. 왼쪽 차선을 유지해요", 2, true, null, oncePerTrip = false),
    TWO_TURNS_R("연속 회전 (다음 우회전)", "꺾자마자 바로 우회전이에요. 오른쪽 차선을 유지해요", 2, true, null, oncePerTrip = false),
    MERGE("합류", "합류는 가속 차선 끝까지 달리며 왼쪽을 보고 들어가요", 2, true, null, oncePerTrip = false),
    SIGNAL_LEFT_ON("깜빡이 방치", "주행 중에 깜빡이가 계속 켜져 있어요", 2, true, false, oncePerTrip = false, repeatGapMs = 20_000L),

    // 부록 I-3 문구: 시행규칙 제19조 2항 (비 오면 20% 감속)
    RAIN("빗길", "비 오는 길이에요. 속도를 20% 줄이고 거리를 넉넉히", 3, true, null),
    RAIN_NO_HEADLIGHT("비 오는데 전조등 꺼짐", "비 올 땐 전조등도 켜 주세요", 3, true, false),
    NIGHT("밤길", "밤길이에요. 거리를 넉넉히", 3, true, null),
    HIGH_BEAM("상향등", "마주 오는 차가 있으면 하향등으로", 3, true, null),
    NIGHT_NO_HEADLIGHT("밤인데 전조등 꺼짐", "밤이에요. 전조등을 켜세요", 3, true, false),
    CABIN_LIGHT("실내등 켠 채 주행", "실내등을 끄면 바깥이 더 잘 보여요", 3, true, false),
    FOG("김 서림 위험", "내기순환에 히터만 켜면 김이 서리기 쉬워요. 외기로 바꿔 주세요", 3, true, false),
    FOG_OK("김 서림 해결", "좋아요. 유리가 맑게 유지돼요", 3, false, true),
    // 다시 살린 코치 (부록 G-2): 크루즈 C1, 히터 대신 열선
    // 부록 I-3 문구: 현대 사용설명서 '스마트 크루즈 컨트롤'(브레이크에 발 올리고 운전 금지) / '전기차 주행 정보'(비교 수치 없음)
    CRUISE("크루즈", "크루즈 중에도 앞을 보고, 필요하면 바로 브레이크를", 3, true, null),
    SEAT_HEAT("히터 대신 열선", "히터 온도를 낮추고 열선을 쓰면 주행거리에 도움돼요", 3, true, null),
    // 핸들 흔들림 = 긴장 (부록 G-3 H2). 칭찬처럼 부드럽게, 한 주행 1번
    WOBBLE("핸들 흔들림", "어깨 힘을 빼고 멀리 봐요", 3, true, null),

    // 위험 (부록 G-3 X4): 고속도로에서 진출로를 놓치고 멈춤·후진. 우선순위 최상, 음성 간격도 무시
    EXIT_MISSED_STOP("진출로 놓치고 멈춤", "멈추면 위험해요. 다음 출구로 가면 돼요", 1, true, false, oncePerTrip = false, repeatGapMs = 30_000L),
    EXIT_MISSED_REVERSE("고속도로 후진", "고속도로에서 후진은 절대 안 돼요", 1, true, false, oncePerTrip = false, repeatGapMs = 30_000L),
    ;
    // S1~S4(브레이크 없이 D·문·트렁크·후진)는 차가 이미 막거나 경고해서 뺐다 (부록 G-1)

    /** 성적표 "오늘 배운 것"에 설명을 싣는 코치 (위험 계열) */
    val lesson: Boolean get() = priority == 1

    /**
     * 졸업생에게는 말하지 않는 코치 (부록 G-2 12번, §2-4 "졸업하면 잔소리가 줄어요").
     * 칭찬과 상황 안내(W1·N1·N2·C1·R1·열선)는 끄고, 위험·깜빡이·G-3 코치(출구·연속 회전·합류·H2·X4)는 남긴다.
     * 끈 코치도 기록(성적표·배지)에는 남는다
     */
    val quietForGraduate: Boolean
        get() = good == true || this in setOf(RAIN, NIGHT, HIGH_BEAM, CRUISE, RIGHT_LANE, SEAT_HEAT)

    /** 기획 문서의 코치 번호 (plan-school-coach.md §2). 기록 화면 코치 카드에 쓴다 */
    val code: String
        get() = when (this) {
            NO_SIGNAL_TURN -> "T1"
            SIGNAL_TURN_OK -> "T2"
            LEFT_WAIT -> "T3"
            LEFT_WAIT_OK -> "T3+"
            LEFT_WAIT_MISSED -> "T4"
            SIGNAL_LEFT_ON -> "T5"
            TURN_LEFT_SOON -> "T7"
            TURN_LEFT_SOON_OK -> "T7+"
            RIGHT_LANE, RIGHT_SIGNAL -> "R1"
            RIGHT_SIGNAL_OK -> "R1+"
            RIGHT_RED -> "R2"
            RAIN -> "W1"
            RAIN_NO_HEADLIGHT -> "W2"
            NIGHT -> "N1"
            HIGH_BEAM -> "N2"
            NIGHT_NO_HEADLIGHT -> "N3"
            CABIN_LIGHT -> "N4"
            FOG -> "F1"
            FOG_OK -> "F1+"
            EXIT_SOON_R, EXIT_SOON_L, EXIT_SOON_C, EXIT_SIGNAL_R, EXIT_SIGNAL_L -> "X1"
            EXIT_SIGNAL_OK -> "X1+"
            TWO_TURNS_L, TWO_TURNS_R -> "X2"
            MERGE -> "X3"
            EXIT_MISSED_STOP, EXIT_MISSED_REVERSE -> "X4"
            CRUISE -> "C1"
            SEAT_HEAT -> "E1"
            WOBBLE -> "H2"
        }

    /** 칭찬 코치 (오늘의 목표 ③에 센다, plan-school-duo-screens.md 부록 B-5) */
    val praise: Boolean get() = this == SIGNAL_TURN_OK || this == LEFT_WAIT_OK || this == TURN_LEFT_SOON_OK || this == RIGHT_SIGNAL_OK ||
        this == EXIT_SIGNAL_OK
}

/** 코치 하나를 화면·음성에 낼지 정한 결과 */
data class CoachDecision(val show: Boolean, val speak: Boolean)

/**
 * 코치 엔진: 공통 규칙을 한곳에서 지킨다 (plan-school-coach.md §1).
 * - 같은 코치는 한 주행에 1번 (깜빡이 계열 일부는 최소 간격만 지키며 반복)
 * - 음성 코치 사이는 [VOICE_GAP_MS] 이상. 그 사이 생긴 음성 코치는 버린다 (나중에 몰아 말하지 않음)
 * - 화면에 코치가 떠 있는 동안 더 낮은 우선순위는 버린다
 * - 칭찬은 글자만, 음성 끄기를 누르면 [voiceEnabled]가 false
 * 시각은 호출하는 쪽이 넣는다 (Android 없이 로직만 확인할 수 있게).
 */
class CoachEngine {
    var voiceEnabled = true

    private val firedAt = mutableMapOf<Coach, Long>()
    private val counts = mutableMapOf<Coach, Int>()
    private val order = mutableListOf<Coach>()
    private var lastVoiceAt: Long? = null
    private var shownCoach: Coach? = null
    private var shownUntil = 0L

    /** 새 주행을 시작한다. 기록을 비운다. */
    fun startTrip() {
        firedAt.clear()
        counts.clear()
        order.clear()
        lastVoiceAt = null
        shownCoach = null
        shownUntil = 0L
    }

    /**
     * @param silent 기록(성적표·배지·목표)에는 남기되 화면·음성에는 내지 않는다 (졸업생 조용히, 부록 G-2)
     * @return 낼 것이 없으면 null
     */
    fun offer(c: Coach, nowMs: Long, silent: Boolean = false): CoachDecision? {
        val count = counts[c] ?: 0
        if (c.oncePerTrip && count > 0) return null
        val last = firedAt[c]
        if (last != null && nowMs - last < c.repeatGapMs) return null

        if (c.recordOnly || silent) {
            record(c, nowMs)
            return CoachDecision(show = false, speak = false)
        }

        val current = shownCoach
        if (current != null && nowMs < shownUntil && c.priority > current.priority) return null

        val wantsVoice = c.voice && voiceEnabled
        // 위험(우선순위 1)은 음성 간격을 기다리지 않는다 (부록 G-3 X4 "우선순위 최상")
        val voiceFree = c.priority == 1 || lastVoiceAt?.let { nowMs - it >= VOICE_GAP_MS } ?: true
        if (wantsVoice && !voiceFree) return null

        record(c, nowMs)
        shownCoach = c
        shownUntil = nowMs + SHOW_MS
        if (wantsVoice) lastVoiceAt = nowMs
        return CoachDecision(show = true, speak = wantsVoice)
    }

    /** 이번 주행에서 나온 코치를 처음 나온 순서대로, 횟수와 함께 */
    fun received(): List<Pair<Coach, Int>> = order.distinct().map { it to (counts[it] ?: 0) }

    private fun record(c: Coach, nowMs: Long) {
        firedAt[c] = nowMs
        counts[c] = (counts[c] ?: 0) + 1
        order.add(c)
    }

    companion object {
        const val VOICE_GAP_MS = 10_000L
        const val SHOW_MS = 4_000L
    }
}
