package com.example.pleosstarter.school

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * 판정 등급 (부록 I-1). 모든 코치·점수·별에 하나씩 붙인다.
 * @param scored 점수·졸업에 쓰는지 (추정·안내만·카드만은 안 씀)
 */
enum class Grade(val label: String, val scored: Boolean, val meaning: String) {
    DIRECT("직접", true, "차 신호 그대로 (깜빡이·기어·와이퍼·전조등·공조·핸들·브레이크 켬/끔)"),
    CALC("계산", true, "신호 + 정해진 식 (내비 남은 거리 = 처음 거리 − 주행거리 변화)"),
    EST("추정", false, "주행거리 ÷ 시간으로 속도·감속을 어림. 점수·졸업에 안 씀"),
    INFO("안내만", false, "판정 없이 알려 주기만"),
    CARD("카드만", false, "배우기 카드를 다 읽으면 별 1"),
}

/** 근거 표 한 줄 (부록 I-4). [coaches]는 성적표 코치 줄에서 이 줄로 건너갈 때 쓴다 */
data class BasisRow(
    val key: String,
    val name: String,
    val grade: Grade,
    val what: String,
    val source: String,
    val coaches: Set<Coach> = emptySet(),
)

/**
 * 모든 코치·점수의 근거 (plan-school-duo-screens.md 부록 I-4 표 그대로). 설정 > "코치 근거" 화면이 이 순서로 보여 준다.
 * [가정]은 공식 숫자가 없어 앱이 정한 기준값 (동작은 그대로 두고 표시만, 부록 I 원칙)
 */
object CoachBasis {
    val ROWS: List<BasisRow> = listOf(
        BasisRow("turn", "깜빡이 없이 꺾음 / 먼저 켬", Grade.DIRECT,
            "주행 중 핸들 30° 넘게 꺾는 순간 같은 방향 깜빡이가 켜져 있었거나 3초 안에 켰나 (시작·끝 100m 제외 [가정])",
            "도로교통법 제38조 1항, 시행령 별표 2 (회전 30m 이상 앞에서 신호)",
            setOf(Coach.NO_SIGNAL_TURN, Coach.SIGNAL_TURN_OK)),
        BasisRow("left_on", "깜빡이 방치", Grade.DIRECT,
            "핸들 직진(±30°) + 주행 중 깜빡이 15초 [가정]",
            "법 제38조 1항 \"끝날 때까지\" + 시행규칙 별표 26 (진로변경 뒤 10m 넘게 켜 두면 감점)",
            setOf(Coach.SIGNAL_LEFT_ON)),
        BasisRow("left_wait", "좌회전 대기 깜빡이 / 놓침", Grade.DIRECT,
            "D + 멈춤(주행거리 3초 그대로) + 핸들 왼쪽 10° [가정] + 왼쪽 깜빡이",
            "도로교통법 제38조 1항",
            setOf(Coach.LEFT_WAIT, Coach.LEFT_WAIT_OK, Coach.LEFT_WAIT_MISSED)),
        BasisRow("turn_soon", "곧 좌회전 / 우회전 깜빡이 미리", Grade.CALC,
            "내비 회전 남은 거리 좌 300m · 우 100m + 깜빡이",
            "도로교통법 제38조, 시행령 별표 2",
            setOf(Coach.TURN_LEFT_SOON, Coach.TURN_LEFT_SOON_OK, Coach.RIGHT_SIGNAL, Coach.RIGHT_SIGNAL_OK)),
        BasisRow("right_lane", "우회전 차선 미리", Grade.CALC,
            "내비 우회전 500m",
            "도로교통법 제25조 1항 (미리 우측 가장자리로 서행)",
            setOf(Coach.RIGHT_LANE)),
        BasisRow("right_red", "우회전 빨간불 멈춤", Grade.INFO,
            "내비 우회전 50m 안 + 주행 중, 한 주행 1번",
            "시행규칙 별표 2 (적색 등화: 정지 후 우회전), 법 제27조 1항",
            setOf(Coach.RIGHT_RED)),
        BasisRow("exit", "출구 미리 / 출구 깜빡이", Grade.CALC,
            "내비 출구 1km / 300m",
            "시행령 별표 2 (고속도로 100m 이상 앞에서 신호) + 일반 운전 요령(미리 차로)",
            setOf(Coach.EXIT_SOON_R, Coach.EXIT_SOON_L, Coach.EXIT_SOON_C, Coach.EXIT_SIGNAL_R, Coach.EXIT_SIGNAL_L, Coach.EXIT_SIGNAL_OK)),
        BasisRow("two_turns", "연속 회전", Grade.CALC,
            "다음 회전까지 200m 이하",
            "일반 운전 요령",
            setOf(Coach.TWO_TURNS_L, Coach.TWO_TURNS_R)),
        BasisRow("merge", "합류", Grade.CALC,
            "내비 합류 300m",
            "도로교통법 제65조 1항 (본선 통행 방해 금지) + 도로교통공단 안내(가속차로에서 속도 맞추기, 원문 미열람)",
            setOf(Coach.MERGE)),
        BasisRow("exit_missed", "진출로 놓치고 멈춤 / 고속도로 후진", Grade.CALC,
            "출구 지남 + 경로 다시 짬 + 멈춤 3초 / 기어 R (직접+계산)",
            "도로교통법 제62조(후진 금지), 제64조(정차 금지)",
            setOf(Coach.EXIT_MISSED_STOP, Coach.EXIT_MISSED_REVERSE)),
        BasisRow("rain", "빗길", Grade.DIRECT,
            "와이퍼 10초 [가정]",
            "도로교통법 시행규칙 제19조 2항",
            setOf(Coach.RAIN)),
        BasisRow("rain_light", "비 + 전조등 꺼짐", Grade.DIRECT,
            "와이퍼 + 전조등 꺼짐 10초",
            "도로교통법 제37조 1항 2호",
            setOf(Coach.RAIN_NO_HEADLIGHT)),
        BasisRow("night", "밤길 / 밤 + 전조등 꺼짐", Grade.DIRECT,
            "전조등 · 밤 시간(그날 서울 일몰~일출) · 주행",
            "도로교통법 제37조 1항 1호",
            setOf(Coach.NIGHT, Coach.NIGHT_NO_HEADLIGHT)),
        BasisRow("high_beam", "상향등", Grade.DIRECT,
            "상향등 켜짐",
            "도로교통법 제37조 2항, 시행령 제20조",
            setOf(Coach.HIGH_BEAM)),
        BasisRow("cabin", "실내등", Grade.DIRECT,
            "주행 중 실내등 10초",
            "현대 사용설명서 '실내등' (법 근거 없음)",
            setOf(Coach.CABIN_LIGHT)),
        BasisRow("fog", "김 서림 / 해결", Grade.DIRECT,
            "내기 + 에어컨 끔 + 성에 제거 끔 + 히터(24℃) 5분 [가정]",
            "현대 사용설명서 '오토 디포그'·'김 서림 제거'",
            setOf(Coach.FOG, Coach.FOG_OK)),
        BasisRow("cruise", "크루즈", Grade.DIRECT,
            "크루즈 켬",
            "현대 사용설명서 '스마트 크루즈 컨트롤'",
            setOf(Coach.CRUISE)),
        BasisRow("seat_heat", "열선", Grade.DIRECT,
            "D + 공조 26℃ + 열선 꺼짐 5분 [가정]",
            "현대 사용설명서 '전기차 주행 정보'",
            setOf(Coach.SEAT_HEAT)),
        BasisRow("wobble", "핸들 흔들림", Grade.DIRECT,
            "직진 중 10초에 ±3° 4번 [가정]",
            "시행규칙 별표 26 (핸들조작 미숙 감점) + 일반 운전 요령(어깨 힘 빼고 멀리)",
            setOf(Coach.WOBBLE)),
        BasisRow("score_turn", "점수: 깜빡이 먼저 ${TripResult.TURN_POINTS}", Grade.DIRECT,
            "위 \"깜빡이 없이 꺾음\"",
            "도로교통법 제38조"),
        BasisRow("score_left_on", "점수: 방치 없음 ${TripResult.LEFT_ON_POINTS}", Grade.DIRECT,
            "위 \"깜빡이 방치\"",
            "도로교통법 제38조, 시행규칙 별표 26"),
        BasisRow("score_stop", "점수: P 넣을 때 브레이크 ${TripResult.END_POINTS}", Grade.DIRECT,
            "기어 P 순간 브레이크",
            "현대 사용설명서 '변속 위치 선택'(브레이크 밟은 상태에서 P)"),
        BasisRow("qualify", "인정 주행 ${TripResult.QUALIFY_KM}km · 회전 ${TripResult.QUALIFY_TURNS}번", Grade.DIRECT,
            "주행거리 · 회전 수",
            "앱 규칙 [가정]"),
        BasisRow("parking", "주정차 판정", Grade.CALC,
            "세운 자리 ↔ 6대 구역 지도",
            "정책브리핑(korea.kr) 2023-08-10"),
        BasisRow("sense", "운전 센스 카드 (빗길 속도·잔 조향·정체·내기 순환·후진 속도)", Grade.EST,
            "추정 속도 = 최근 3초 주행거리 증가 ÷ 3초. 공식 숫자가 없으면 지난 인정 주행 5번 평균과 비교 (점수에 안 씀)",
            "시행규칙 제19조 · 별표 26 · 한국교통안전공단 경제운전 · 현대 사용설명서 '주위 안전 확인'"),
    )

    fun rowOf(c: Coach): BasisRow? = ROWS.firstOrNull { c in it.coaches }
}

/** 등급 칩 (추정은 회색, 나머지는 연파랑) */
internal fun SchoolActivity.gradeChip(g: Grade) = chip(g.label, if (g == Grade.EST) MUTED else PRIMARY).apply {
    background = rounded(if (g == Grade.EST) RAISED else PRIMARY_SOFT, 20)
}

/**
 * 설정 > 코치 근거 (부록 I-4): 이름 / 등급 칩 / 무엇을 보나 한 줄 / 출처 한 줄. 시연·심사용.
 * 성적표 코치 카드에서 오면 그 줄을 연파랑으로 강조하고 그 자리로 내린다
 */
internal fun SchoolActivity.renderBasis(page: LinearLayout, highlight: String?) {
    header(page, "코치 근거")
    page.addView(text("모든 코치·점수·별이 무엇을 보고, 어디에 근거하는지예요. [가정]은 공식 숫자가 없어 앱이 정한 값이에요",
        22f, MUTED).apply { setPadding(0, dp(4), 0, dp(12)) })
    // 등급 설명
    page.addView(card {
        it.addView(text("판정 등급", 26f, TEXT, bold = true))
        Grade.values().forEach { g ->
            it.addView(row().apply {
                setPadding(0, dp(12), 0, 0)
                addView(gradeChip(g))
                addView(text(g.meaning, 22f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        val (rise, set) = SunTimes.riseSetMinutes(java.time.LocalDate.now())
        it.addView(text("오늘 서울 일출 ${SunTimes.hhmm(rise)} · 일몰 ${SunTimes.hhmm(set)} (밤 판단, NOAA 계산식)", 22f, HINT).padTop(12))
    })
    var target: LinearLayout? = null
    page.addView(text("코치·점수", 28f, TEXT, bold = true).apply { setPadding(0, dp(8), 0, dp(12)) })
    CoachBasis.ROWS.forEach { r ->
        val c = basisCard(r.name, r.grade, r.what, r.source, r.key == highlight)
        if (r.key == highlight) target = c
        page.addView(c)
    }
    page.addView(text("별 조건", 28f, TEXT, bold = true).apply { setPadding(0, dp(8), 0, dp(12)) })
    Skill.values().forEach { s ->
        page.addView(basisCard("${s.icon} ${s.title}", s.grade, s.nextStar, s.sources?.distinct()?.joinToString(" / ") ?: GENERAL_TIP, false))
    }
    target?.let { t -> page.post { (page.parent as? ScrollView)?.scrollTo(0, t.top) } }
}

private fun SchoolActivity.basisCard(name: String, g: Grade, what: String, source: String, on: Boolean): LinearLayout = card {
    if (on) it.background = rounded(PRIMARY_SOFT, 28)
    it.addView(row().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(text(name, 26f, TEXT, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(gradeChip(g))
    })
    it.addView(text("보는 것: $what", 22f, TEXT).padTop(8))
    it.addView(text("출처: $source", 22f, HINT).padTop(4))
}
