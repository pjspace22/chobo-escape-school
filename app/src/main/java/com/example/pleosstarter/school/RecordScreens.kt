package com.example.pleosstarter.school

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 📒 기록: 목록(L1) → 성적표(L2) → 항목 상세(L3) → 그 순간 코치 카드(L4), 통계(L2), 축하 화면
// plan-school-duo-screens.md §4, 부록 B-1·B-8·B-11, plan-school-coach.md §3-1 (H1·B1·A1)

/** 통계 막대 칸 수 (최근 10번) */
private const val CHART_SLOTS = 10

private val TIME = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)

private fun TripRecord.whenText(): String = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()).format(TIME)

private fun km(v: Float) = "%.1f".format(Locale.KOREA, v)

/** 합격·연습·아쉬움 표시와 색 */
private fun TripResult.verdict(): Pair<String, Int> = when {
    !qualified -> "연습" to MUTED
    passed -> "합격" to GOOD
    else -> "다시 도전" to BAD
}

/** 기록 목록 한 줄 (미션 > 주 > 주행에서도 쓴다) */
internal fun SchoolActivity.tripRow(rec: TripRecord, menu: SchoolMenu): View = row().apply {
    val r = rec.result
    val (label, color) = r.verdict()
    setPadding(0, dp(12), 0, dp(12))
    addView(text(rec.whenText(), 22f, if (r.qualified) TEXT else MUTED),
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
    addView(text("${r.score}", 28f, if (r.qualified) color else MUTED, bold = true),
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f))
    addView(text("$label ›", 22f, color, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f))
    setOnClickListener { push(SchoolScreen.Report(rec.id, menu)) }
}

/** 📒 기록 L1: 요약 줄 + [통계] + 최근 30개 (최신 위) */
internal fun SchoolActivity.renderRecord(page: LinearLayout) {
    header(page, "기록")
    page.addView(text(
        "주행 ${progress.trips}회 · 합격 ${progress.passes}회 · 최고 ${progress.bestScore}점 · 누적 ${km(progress.totalKm)}km",
        22f, TEXT,
    ).apply { setPadding(0, dp(8), 0, dp(12)) })
    // [주행] [정차] 둥근 세그먼트 탭: 고른 쪽만 색 채움 (부록 C-1, E-5)
    val tabs = row().apply {
        background = rounded(CARD, 24)
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }
    listOf("🚗 주행", "🅿 정차").forEachIndexed { i, label ->
        val on = recordTab == i
        tabs.addView(text(label, 22f, if (on) INK else MUTED, bold = true, center = true).apply {
            if (on) background = rounded(PRIMARY, 20)
            setPadding(0, dp(12), 0, dp(12))
            pressable()
            setOnClickListener {
                recordTab = i
                render()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    page.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(12) })
    if (recordTab == 1) {
        page.addView(card {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
            stopList(it)
        })
        return
    }
    // 목록은 불러오는 중 / 비었음 / 내용 / 오류 중 하나만 (부록 H-⑧).
    // 판단: 기록은 기기 안 저장소에서 바로 읽어 1초 안에 끝나므로 "불러오는 중"은 띄우지 않는다
    val records = progress.loadRecords().getOrElse {
        page.addView(errorState("기록") { render() })
        return
    }
    if (records.isEmpty()) {
        page.addView(noTripsState())
        return
    }
    page.addView(button("📊 통계 보기 ›") { push(SchoolScreen.Stats) }.onPage())
    page.addView(card {
        (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        it.addView(text("최근 주행 ${records.size}개 (최대 ${TripRecord.MAX_RECORDS}개)", 20f, MUTED))
        records.forEach { rec -> it.addView(tripRow(rec, SchoolMenu.RECORD)) }
    })
}

/** A1 별: 80점 ★ · 90점 ★★ · 100점 ★★★. 불합격이면 빈 별 */
private fun scoreStars(score: Int): Int = when {
    score >= 100 -> 3
    score >= 90 -> 2
    score >= TripResult.PASS_SCORE -> 1
    else -> 0
}

/** 성적표 맨 위 별 3개. 하나씩 0.3초 간격으로 작게 → 1.2배 → 1배 (새 라이브러리 없음) */
private fun SchoolActivity.starRow(score: Int): LinearLayout = row().apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(text(character(), 44f, TEXT).apply { setPadding(0, 0, dp(16), 0) })
    val n = scoreStars(score)
    for (i in 0 until 3) {
        val star = text(if (i < n) "★" else "☆", 56f, if (i < n) GOLD else LINE, bold = true)
        addView(star)
        if (i < n && ANIMATIONS) {
            star.scaleX = 0.3f
            star.scaleY = 0.3f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(star, "scaleX", 0.3f, 1.2f, 1f),
                    ObjectAnimator.ofFloat(star, "scaleY", 0.3f, 1.2f, 1f),
                )
                startDelay = i * 300L
                duration = 400L
                start()
            }
        }
    }
}

/**
 * 성적표 항목 카드 4장 (부록 E-1: B-13 아이콘을 "풀어서").
 * @param ok 잘함 true, 놓침 false, 이번엔 해당 없음(꺾은 적 없음) null
 */
private data class ItemCard(
    val item: TripItem, val icon: String, val name: String, val ok: Boolean?, val count: String, val desc: String,
    /** 결과 말 대신 쓸 글 (운전 센스 카드는 점수가 아니라 "기록") */
    val tag: String? = null,
)

private fun TripResult.cards(): List<ItemCard> {
    val startOk = startPoints == startMax
    val signalOk = if (turns == 0) null else turnsWithoutSignal == 0
    val stopOk = endPoints == endMax
    // 점수 v3(0.17.0~)는 출발 브레이크 대신 "운전 센스" 카드 (부록 I-2 L1, I-5). 예전 기록은 그때 카드 그대로
    val first = if (v3) ItemCard(TripItem.SENSE, "🧭", "운전 센스", null,
        if (qualified) "${sense.lines()}가지 기록" else "인정 주행에서 봐요",
        "점수와 따로, 지난 5번과 비교해요", tag = "기록")
    else ItemCard(TripItem.START, "🦶", "출발", startOk,
        if (startBrake == null) "출발 순간은 못 봤어요" else if (startOk) "브레이크 밟고 D" else "브레이크 없이 D",
        if (startOk) "브레이크 밟고 출발했어요" else "다음엔: 브레이크 밟고 D 넣기")
    return listOf(
        first,
        ItemCard(TripItem.SIGNAL, "↪", "깜빡이", signalOk,
            if (turns == 0) "크게 꺾은 적 없어요" else "${turns}번 중 ${turnsWithSignal}번 켰어요",
            when (signalOk) {
                null -> "꺾을 때 깜빡이를 먼저 켜 봐요"
                true -> "꺾기 전에 깜빡이를 먼저 켰어요"
                false -> "다음엔: 꺾기 전에 깜빡이 먼저"
            }),
        ItemCard(TripItem.LEFT_ON, "💡", "끄기", signalLeftOn == 0,
            if (signalLeftOn == 0) "잘 껐어요" else "${signalLeftOn}번 켜 둔 채 달렸어요",
            if (signalLeftOn == 0) "다 돌고 깜빡이를 껐어요" else "주행 중 깜빡이를 켜 둔 채 달렸어요"),
        ItemCard(TripItem.STOP, "🛑", "정차", stopOk,
            if (stopOk) "브레이크 밟고 P" else "브레이크 없이 P",
            // 부록 I-2 L8: 판정은 "P 넣을 때 브레이크"라 "부드럽게"라고 하지 않는다
            if (stopOk) "브레이크를 밟은 채 P를 넣었어요" else "다음엔: 브레이크 밟고 P 넣기"),
    )
}

/**
 * 항목 카드 한 장: 아이콘 받침 + 이름 + 결과 말 / 횟수 / 설명 한 줄 + 오른쪽 ›.
 * 흰 카드, 놓친 카드만 빨강 바탕(부록 F-4). 그림자·테두리 없이 눌림 효과로 버튼처럼
 */
private fun SchoolActivity.itemCard(c: ItemCard, onClick: () -> Unit): View = row().apply {
    val missed = c.ok == false
    val color = when (c.ok) { true -> PRIMARY; false -> BAD; null -> HINT }
    background = rounded(if (missed) BAD_SOFT else CARD, 28)
    setPadding(dp(28), dp(24), dp(20), dp(24))
    addView(column().apply {
        addView(row().apply {
            addView(iconBox(c.icon, 64, if (missed) CARD else PRIMARY_SOFT))
            addView(text(c.name, 28f, if (missed) BAD else TEXT, bold = true).apply { setPadding(dp(16), 0, dp(12), 0) })
            addView(text(c.tag ?: when (c.ok) { true -> "잘했어요"; false -> "놓쳤어요"; null -> "이번엔 없음" }, 22f, color, bold = true))
        })
        addView(text(c.count, 24f, if (missed) BAD else TEXT, bold = true).padTop(14))
        addView(text(c.desc, 20f, MUTED).padTop(4))
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    addView(text("›", 40f, HINT))
    pressable()
    setOnClickListener { onClick() }
}

/** 문장 1줄: 만점이면 "완벽해요!", 아니면 가장 많이 놓친 항목 하나 */
private fun TripResult.oneLine(): String {
    if (!qualified) return "연습 주행이에요 · ${TripResult.QUALIFY_KM}km, 핸들 ${TripResult.QUALIFY_TURNS}번을 넘기면 합격 기록"
    if (score == 100) return "완벽해요!"
    val weakest = listOf(
        "브레이크 밟고 D 넣기" to startMax - startPoints,
        "꺾기 전에 깜빡이 먼저" to turnMax - turnPoints,
        "다 돌면 깜빡이 끄기" to leftOnMax - leftOnPoints,
        "브레이크 밟고 P 넣기" to endMax - endPoints,
    ).maxByOrNull { it.second }!!
    return "다음엔: ${weakest.first}"
}

/** 주행 기록이 하나도 없을 때 (부록 H-⑦): 이유 + 채우는 법 + [오늘 배울 것 보기] + 보조 [예시 성적표 보기] */
private fun SchoolActivity.noTripsState() = emptyState(
    "📒", "아직 주행 기록이 없어요", "기어를 D로 두고 출발하면 성적표가 여기 쌓여요",
    "오늘 배울 것 보기 ›", { openMenu(SchoolMenu.LEARN) },
    "예시 성적표 보기", { push(SchoolScreen.SampleReport) },
)

/**
 * 기록 > 예시 성적표 (부록 H-⑦). 시연 첫 화면이 비어 보이지 않게 샘플을 보여 준다.
 * 화면 위 "예시" 띠, 실제 기록·XP와 섞지 않음(저장 안 함). 판단: 예시 안의 카드·버튼은 눌러도 아무 일 없게 막는다
 */
internal fun SchoolActivity.renderSampleReport(page: LinearLayout) {
    header(page, "예시 성적표")
    page.addView(text("예시 · 실제 기록이 아니에요. 첫 주행을 마치면 내 성적표가 이렇게 나와요", 24f, TEXT, bold = true, lines = 2).apply {
        // 흰 글자는 금색 위에서 대비가 낮아 연금색 바탕 + 진한 글자 (부록 H-①)
        background = rounded(GOLD_SOFT, 16).apply { setStroke(dp(2), GOLD) }
        setPadding(dp(24), dp(14), dp(24), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(16) }
    })
    val body = column()
    reportBody(body, sampleTrip(), SchoolMenu.RECORD)
    body.disableClicks()
    page.addView(body)
}

private fun android.view.View.disableClicks() {
    setOnClickListener(null)
    isClickable = false
    if (this is android.view.ViewGroup) for (i in 0 until childCount) getChildAt(i).disableClicks()
}

/** 예시 성적표에 쓰는 주행 하나 (판단: 합격·깜빡이 한 번 놓침·빗길로 항목 카드가 골고루 보이게) */
private fun sampleTrip() = TripRecord(
    id = System.currentTimeMillis(),
    result = TripResult(
        distanceKm = 6.4f, startBrake = true, endBrake = true,
        turnsWithSignal = 5, turnsWithoutSignal = 1, signalLeftOn = 0,
        rain = true, coaches = setOf(Coach.NO_SIGNAL_TURN, Coach.SIGNAL_TURN_OK),
        steadiness = 86, brakesPerKm = 2.1f, parkedSafe = true,
    ),
    hits = listOf(CoachHit(Coach.SIGNAL_TURN_OK, 1.2f), CoachHit(Coach.NO_SIGNAL_TURN, 3.8f)),
    starsUp = emptyList(), target = null, brakes = 13,
)

/** 기록 > 성적표 (L2). 주행을 마치면 바로 여기. 첫 화면 = 큰 점수 + 아이콘 4개 + 문장 1줄 (부록 B-13) */
internal fun SchoolActivity.renderReport(page: LinearLayout, s: SchoolScreen.Report) {
    val rec = s.id?.let { progress.record(it) } ?: progress.records.firstOrNull()
    header(page, if (justFinished) "이번 주행 성적표" else "지난 주행 성적표")
    if (rec == null) {
        page.addView(noTripsState())
        return
    }
    reportBody(page, rec, s.menu)
}

private fun SchoolActivity.reportBody(page: LinearLayout, rec: TripRecord, menu: SchoolMenu) {
    val r = rec.result
    page.addView(row().apply {
        addView(starRow(r.score), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("${rec.whenText()} · ↻", 20f, MUTED).apply { setOnClickListener { render() } })
    })
    page.addView(column().also {
        // 큰 점수(파랑, 불합격 빨강) + "합격"(제목) + "· +150 XP"(회색) (부록 F-4)
        val (verdict, color) = when {
            !r.qualified -> "연습" to HINT
            r.passed -> "합격" to PRIMARY
            else -> "아쉬워요" to BAD
        }
        val xp = outcome?.takeIf { o -> justFinished && o.record.id == rec.id }?.xp
        it.addView(row().apply {
            // 큰 숫자는 칸에 맞춰 줄인다 (부록 H-④). 판단: 칸 폭 150dp = 72sp "100"이 들어가는 폭
            addView(text("${r.score}", 72f, color, bold = true).autoFit(72f, 150))
            addView(text(verdict, 34f, TEXT, bold = true).apply { setPadding(dp(24), 0, 0, 0) })
            // 0.18.0: 레벨(XP)은 화면에서 숨김. 기록은 그대로 쌓는다
            if (!HIDE_LEVEL_MISSION_BADGE) xp?.let { v -> addView(text(" · +$v XP", 24f, MUTED)) }
        })
        // 항목 카드 2×2 (부록 E-1, F-4): 흰 카드 + 아이콘 받침, 놓친 카드만 빨강 바탕. 누르면 항목 상세(L3)
        r.cards().chunked(2).forEachIndexed { line, pair ->
            it.addView(row().apply {
                setPadding(0, if (line == 0) dp(16) else dp(16), 0, 0)
                pair.forEachIndexed { i, c ->
                    addView(itemCard(c) { push(SchoolScreen.ReportItem(rec.id, c.item, menu)) },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { setMargins(if (i == 0) 0 else dp(12), 0, if (i == 0) dp(12) else 0, 0) })
                }
            })
        }
        // 문장 1줄. "이번 주행 연습" 결과가 있으면 그 자리를 대신한다
        val t = rec.target
        val (line, lineColor) = when {
            t != null && t in rec.starsUp -> "🎯 ${t.title} 별 +1!" to GOOD
            t != null -> "🎯 ${t.title}: 다음엔 ${t.nextStar}" to PRIMARY
            r.score == 100 && r.qualified -> r.oneLine() to GOOD
            else -> r.oneLine() to TEXT
        }
        it.addView(text(line, 26f, lineColor, bold = true).padTop(24))
        if (rec.starsUp.isNotEmpty() && !HIDE_LEVEL_MISSION_BADGE) {
            it.addView(text("★ " + rec.starsUp.joinToString(" · ") { sk -> sk.title }, 22f, GOLD).padTop(8))
        }
        it.addView(text("주행 습관 자세히 ›", 22f, PRIMARY, bold = true).apply {
            setPadding(0, dp(12), 0, dp(16))
            setOnClickListener { push(SchoolScreen.ReportItem(rec.id, TripItem.HABIT, menu)) }
        })
    })
    val buttons = row()
    buttons.addView(button("성적표 공유") { share(ShareText.trip(r, progress.passes)) }.onPage(), weighted())
    if (justFinished) {
        // 닫으면 축하 화면 한 장씩 → 배우기 첫 화면 (부록 B-1)
        buttons.addView(button("닫기", primary = true) {
            // 0.18.0: 단원·배지·레벨·목표 축하 화면은 숨김 (카드 마스터 축하는 홈에서)
            if (!HIDE_LEVEL_MISSION_BADGE && outcome?.celebrations().orEmpty().isNotEmpty()) push(SchoolScreen.Celebrate(0)) else finishReport()
        }, weighted())
    }
    page.addView(buttons)
}

/**
 * 0.18.0 (현규 11/9 "미션 빼고, 레벨 빼고, 배지도 빼자"): 미션·레벨·배지를 화면에서 숨긴다.
 * 데이터(XP·배지·목표·별)는 그대로 쌓으므로 false로 바꾸면 돌아온다
 */
internal const val HIDE_LEVEL_MISSION_BADGE = true

/** 성적표·축하 화면을 다 보고 배우기 첫 화면으로 */
private fun SchoolActivity.finishReport() {
    justFinished = false
    outcome = null
    openMenu(SchoolMenu.LEARN)
}

private fun habitLine(r: TripResult): String {
    val h1 = r.steadiness?.let { "핸들 안정도 $it%" } ?: "핸들 안정도는 측정하기엔 짧았어요"
    val b1 = r.brakesPerKm?.let { " · 1km에 브레이크 ${km(it)}번" } ?: ""
    return h1 + b1
}

/** 성적표 > 항목 상세 (L3): 점수 근거 + 그 항목에서 받은 코치(누르면 L4) */
internal fun SchoolActivity.renderReportItem(page: LinearLayout, s: SchoolScreen.ReportItem) {
    val rec = progress.record(s.id) ?: return
    val r = rec.result
    header(page, s.item.title)
    page.addView(text("${rec.whenText()} · ${km(r.distanceKm)}km · ${habitLine(r)}", 20f, MUTED).apply { setPadding(0, dp(6), 0, dp(10)) })
    r.practiceReasons().forEach { reason -> page.addView(text("연습 주행: $reason", 20f, MUTED)) }
    page.addView(card {
        fun line(t: String, color: Int = TEXT) = it.addView(text(t, 24f, color).padTop(6))
        when (s.item) {
            TripItem.START -> {
                line("점수 ${r.startPoints} / ${r.startMax}", PRIMARY)
                line(when (r.startBrake) {
                    true -> "기어를 D로 넣을 때 브레이크를 밟고 있었어요"
                    false -> "브레이크를 안 밟고 D를 넣었어요 (S1)"
                    null -> "앱을 켜기 전부터 D여서 출발 순간은 못 봤어요 (감점 없음)"
                })
            }
            TripItem.SENSE -> senseLines(it, rec)
            TripItem.SIGNAL -> {
                line("점수 ${r.turnPoints} / ${r.turnMax}", PRIMARY)
                line(if (r.turns == 0) "핸들을 크게(30° 넘게) 꺾은 적이 없어요" else "꺾기 ${r.turns}번 중 ${r.turnsWithSignal}번 깜빡이를 먼저 켰어요")
            }
            TripItem.LEFT_ON -> {
                line("점수 ${r.leftOnPoints} / ${r.leftOnMax}", PRIMARY)
                line("주행 중에 깜빡이를 켠 채 15초 넘게 달린 횟수 ${r.signalLeftOn}번 (0번 ${r.leftOnMax}점 · 1번 ${r.leftOnMax / 2}점 · 2번 이상 0점)")
            }
            TripItem.STOP -> {
                line("점수 ${r.endPoints} / ${r.endMax}", PRIMARY)
                line(when (r.endBrake) {
                    true -> "P를 넣을 때 브레이크를 밟고 있었어요"
                    false -> "브레이크를 안 밟고 P를 넣었어요"
                    null -> "정차 순간은 못 봤어요 (감점 없음)"
                })
            }
            TripItem.HABIT -> {
                line("점수에는 들어가지 않아요", MUTED)
                line(r.steadiness?.let { v -> "핸들 안정도 $v% · 직진할 때 핸들이 5° 넘게 흔들린 비율로 재요" }
                    ?: "핸들 안정도: 직진 구간이 짧아서 측정하기엔 짧았어요")
                r.steadiness?.takeIf { v -> v < 70 }?.let { line("다음 주행 숙제: 어깨 힘을 빼고 핸들은 부드럽게", PRIMARY) }
                val b1 = r.brakesPerKm
                if (b1 == null) {
                    line("1km당 브레이크: ${TripResult.QUALIFY_KM}km 넘게 달린 주행에서 보여요")
                } else {
                    line("1km에 브레이크 ${km(b1)}번 (이번 주행 ${rec.brakes}번)")
                    val past = progress.records.filter { o -> o.id < rec.id }.mapNotNull { o -> o.result.brakesPerKm }.take(5)
                    if (past.isNotEmpty()) {
                        val avg = past.average().toFloat()
                        when {
                            b1 < avg -> line("지난 ${past.size}번 평균 ${km(avg)}번보다 부드럽게 달렸어요", GOOD)
                            b1 > avg * 1.5f -> line("앞차와 거리를 넉넉히 두면 브레이크가 줄어요 (평균 ${km(avg)}번)", PRIMARY)
                            else -> line("지난 ${past.size}번 평균 ${km(avg)}번과 비슷해요", MUTED)
                        }
                    }
                }
            }
        }
    })
    // 이 항목과 관련된 코치 (L4로)
    val related = rec.hits.withIndex().filter { (_, h) -> itemOf(h.coach) == s.item }
    page.addView(card {
        it.addView(text("이 항목에서 받은 코치", 24f, TEXT, bold = true))
        if (related.isEmpty()) {
            it.addView(text("받은 코치가 없어요", 22f, MUTED).padTop(8))
        } else {
            related.forEach { (i, h) ->
                if (h.coach.lesson) it.addView(text("📘 ${h.coach.message}", 22f, TEXT).padTop(10))
                it.addView(text("${km(h.km)}km 지점 · ${h.coach.code} ${h.coach.label}  ›", 22f,
                    when (h.coach.good) { true -> GOOD; false -> BAD; null -> TEXT }).apply {
                    setPadding(0, dp(10), 0, 0)
                    setOnClickListener { push(SchoolScreen.CoachCard(rec.id, i, s.menu)) }
                })
            }
        }
    })
}

/** 코치가 성적표 어느 항목 밑에 붙는지 */
private fun itemOf(c: Coach): TripItem = when (c) {
    Coach.NO_SIGNAL_TURN, Coach.SIGNAL_TURN_OK, Coach.LEFT_WAIT, Coach.LEFT_WAIT_OK, Coach.LEFT_WAIT_MISSED,
    Coach.TURN_LEFT_SOON, Coach.TURN_LEFT_SOON_OK, Coach.RIGHT_LANE, Coach.RIGHT_SIGNAL, Coach.RIGHT_SIGNAL_OK -> TripItem.SIGNAL
    Coach.SIGNAL_LEFT_ON -> TripItem.LEFT_ON
    else -> TripItem.HABIT
}

/** 항목 상세 > 그 순간 코치 카드 (L4) → 관련 기술 말풍선으로 건너감 (기록 → 배우기) */
internal fun SchoolActivity.renderCoachCard(page: LinearLayout, s: SchoolScreen.CoachCard) {
    val rec = progress.record(s.id) ?: return
    val hit = rec.hits.getOrNull(s.index) ?: return
    val c = hit.coach
    header(page, "${c.code} ${c.label}")
    page.addView(card {
        it.addView(text("${km(hit.km)}km 지점 · ${rec.whenText()}", 22f, MUTED))
        it.addView(text("“${c.message.ifEmpty { c.label }}”", 32f,
            when (c.good) { true -> GOOD; false -> BAD; null -> TEXT }, bold = true).apply { setPadding(0, dp(16), 0, dp(8)) })
        it.addView(text(when {
            c.recordOnly && c.lesson -> "주행 중엔 말하지 않고 성적표에 남긴 내용이에요"
            c.voice -> "주행 중 화면과 음성으로 알려 드렸어요"
            else -> "주행 중 화면 글자로 알려 드렸어요"
        }, 20f, MUTED))
    })
    // 이 코치의 근거 (부록 I-4): 등급 + 출처, 누르면 설정 > 코치 근거의 그 줄로
    CoachBasis.rowOf(c)?.let { b ->
        page.addView(card {
            it.addView(row().apply {
                addView(text("근거", 26f, TEXT, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(gradeChip(b.grade))
            })
            it.addView(text("보는 것: ${b.what}", 22f, TEXT).padTop(8))
            it.addView(text("출처: ${b.source}", 22f, HINT).padTop(4))
            it.addView(text("코치 근거 전체 보기 ›", 22f, PRIMARY, bold = true).padTop(8).apply {
                setOnClickListener { push(SchoolScreen.Basis(b.key)) }
            })
        })
    }
    val skill = Skill.of(c)
    if (skill != null) {
        page.addView(button("관련 기술 보기: ${skill.icon} ${skill.title} ›", primary = true) {
            screens.open(SchoolMenu.LEARN)
            openNode = PathNode.SkillNode(skill)
            justFinished = false
            render()
        })
    } else {
        page.addView(text("이 코치와 연결된 학습 기술은 아직 없어요", 20f, MUTED).padTop(8))
    }
}

/**
 * 막대그래프 하나. [line]이 있으면 그 값에 가로선 (합격선 80).
 * 값마다 (높이 값, 색, 아래 글자, 누르면 할 일)
 */
private fun SchoolActivity.barChart(
    bars: List<Triple<Float, Int, String>>,
    max: Float,
    line: Float?,
    onClick: (Int) -> Unit,
): View {
    val heightDp = 220
    val frame = FrameLayout(this)
    val barsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
    }
    bars.forEachIndexed { i, (v, color, _) ->
        val h = (dp(heightDp) * (v / max).coerceIn(0.02f, 1f)).toInt()
        barsRow.addView(View(this).apply {
            background = rounded(color, 6)
            setOnClickListener { onClick(i) }
        }, LinearLayout.LayoutParams(0, h, 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
    }
    // 주행이 적어도 막대 폭이 늘 같게 빈 칸을 채운다
    repeat(CHART_SLOTS - bars.size) { barsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
    frame.addView(barsRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(heightDp)))
    if (line != null) {
        frame.addView(View(this).apply { setBackgroundColor(TEXT) }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM,
        ).apply { bottomMargin = (dp(heightDp) * (line / max)).toInt() })
    }
    return column().apply {
        addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)))
        val labels = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bars.forEach { (_, _, label) ->
            labels.addView(text(label, 16f, MUTED, center = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        repeat(CHART_SLOTS - bars.size) { labels.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
        addView(labels)
    }
}

/** 기록 > 통계 (L2): 최근 10번 점수(합격선 80), 핸들 안정도, 1km당 브레이크. 막대 누르면 그 성적표 */
internal fun SchoolActivity.renderStats(page: LinearLayout) {
    header(page, "통계")
    val recent = progress.records.take(10).reversed()
    if (recent.size < 2) {
        page.addView(text("주행 2번부터 그래프가 생겨요", 24f, MUTED).padTop(16))
        return
    }
    page.addView(card {
        it.addView(text("최근 ${recent.size}번 점수 · 검은 선 = 합격선 ${TripResult.PASS_SCORE}점", 24f, TEXT, bold = true))
        it.addView(text("회색 = 연습 주행 · 막대를 누르면 그 성적표", 18f, MUTED).apply { setPadding(0, dp(4), 0, dp(12)) })
        it.addView(barChart(
            recent.map { rec ->
                val r = rec.result
                Triple(r.score.toFloat(), if (!r.qualified) TRACK else if (r.passed) GOOD else BAD, "${r.score}")
            },
            100f, TripResult.PASS_SCORE.toFloat(),
        ) { i -> push(SchoolScreen.Report(recent[i].id)) })
    })
    val withH1 = recent.filter { r -> r.result.steadiness != null }
    val withB1 = recent.filter { r -> r.result.brakesPerKm != null }
    page.addView(card {
        it.addView(text("핸들 안정도 추이", 24f, TEXT, bold = true))
        if (withH1.isEmpty()) {
            it.addView(text("직진을 충분히 달린 주행부터 보여요", 20f, MUTED).padTop(6))
        } else {
            it.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
            it.addView(barChart(withH1.map { r -> Triple(r.result.steadiness!!.toFloat(), GOOD, "${r.result.steadiness}%") }, 100f, null) { i ->
                push(SchoolScreen.Report(withH1[i].id))
            })
        }
        it.addView(text("1km당 브레이크 추이 (낮을수록 부드러움)", 24f, TEXT, bold = true).padTop(20))
        if (withB1.isEmpty()) {
            it.addView(text("${TripResult.QUALIFY_KM}km 넘게 달린 주행부터 보여요", 20f, MUTED).padTop(6))
        } else {
            val max = withB1.maxOf { r -> r.result.brakesPerKm!! }.coerceAtLeast(1f)
            it.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
            it.addView(barChart(withB1.map { r -> Triple(r.result.brakesPerKm!!, PRIMARY, km(r.result.brakesPerKm!!)) }, max, null) { i ->
                push(SchoolScreen.Report(withB1[i].id))
            })
        }
    })
}

/** 주행을 마친 뒤 축하 화면 한 장 (단원 통과·새 배지·레벨 업·졸업·목표 상자) */
internal fun SchoolActivity.renderCelebrate(page: LinearLayout, index: Int) {
    val list = outcome?.celebrations().orEmpty()
    val c = list.getOrNull(index)
    header(page, "축하해요")
    if (c == null) {
        page.addView(button("배우기로") { finishReport() })
        return
    }
    page.addView(column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(40), 0, dp(24))
        val icon = text(c.icon, 96f, TEXT, center = true)
        addView(icon)
        addView(text(c.title, 40f, GOLD, bold = true, center = true).padTop(16))
        addView(text(c.message, 26f, TEXT, center = true).padTop(12))
        addView(text("${character()} 잘했어요!", 26f, GOOD, center = true).padTop(24))
        if (ANIMATIONS) {
            icon.scaleX = 0.3f
            icon.scaleY = 0.3f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, "scaleX", 0.3f, 1.2f, 1f),
                    ObjectAnimator.ofFloat(icon, "scaleY", 0.3f, 1.2f, 1f),
                )
                duration = 500L
                start()
            }
        }
    })
    page.addView(text("${index + 1} / ${list.size}", 20f, MUTED, center = true))
    if (index + 1 < list.size) {
        page.addView(button("다음 ›", primary = true) { push(SchoolScreen.Celebrate(index + 1)) })
    } else {
        page.addView(button("배우기로", primary = true) { finishReport() })
    }
}

/** 운전 센스 카드에 값이 있는 줄 수 */
private fun SenseStats.lines(): Int = listOfNotNull(rainKmh, microSteerPerMin, jamBrakesPerMin, recircMaxMin, reverseMaxKmh).size

/**
 * 성적표 > 운전 센스 (부록 I-5). 점수와 따로, 각 줄 오른쪽에 "지난 5번보다 ↑↓", 추정 줄은 "추정" 칩.
 * 공식 숫자가 없는 값은 내 평균(지난 인정 주행 5번)과만 비교한다
 */
private fun SchoolActivity.senseLines(card: LinearLayout, rec: TripRecord) {
    val r = rec.result
    val s = r.sense
    if (!r.qualified) card.addView(text("연습 주행이라 비교는 하지 않아요. 값만 기록해요", 22f, MUTED))
    fun cmp(now: Float?, pick: (TripResult) -> Float?, lowerIsBetter: Boolean = true): String {
        if (now == null) return ""
        val avg = progress.myAverage(rec.id, pick) ?: return "기록이 쌓이면 비교해 드려요"
        if (avg <= 0f) return ""
        val pct = Math.round((now - avg) / avg * 100)
        val arrow = when {
            pct == 0 -> "→"
            (pct < 0) == lowerIsBetter -> "↓"
            else -> "↑"
        }
        return "지난 5번보다 ${kotlin.math.abs(pct)}% ${if (pct < 0) "줄었어요" else if (pct > 0) "늘었어요" else "같아요"} $arrow"
    }
    fun line(name: String, g: Grade, value: String, compare: String, note: String? = null, chip: String? = null) {
        card.addView(divider())
        card.addView(row().apply {
            addView(text(name, 26f, TEXT, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(gradeChip(g).apply { if (chip != null) text = chip })
        })
        card.addView(text(value, 24f, TEXT).padTop(6))
        if (compare.isNotEmpty()) card.addView(text(compare, 22f, PRIMARY).padTop(4))
        note?.let { card.addView(text(it, 20f, HINT).padTop(4)) }
    }
    val base = s.clearKmh ?: progress.myAverage(rec.id) { it.sense.clearKmh }
    line("빗길 속도", Grade.EST,
        when {
            s.rainKmh == null -> "와이퍼를 켜고 달린 구간이 없었어요"
            base == null || base <= 0f -> "비 올 때 평균 약 ${s.rainKmh.toInt()}km/h (맑을 때 기록이 쌓이면 비교해요)"
            else -> {
                val slower = Math.round((1 - s.rainKmh / base) * 100)
                if (slower >= 0) "비 올 때 ${slower}% 느리게 (법은 20% 감속)" else "비 올 때 ${-slower}% 더 빠르게 (법은 20% 감속)"
            }
        }, cmp(s.rainKmh, { it.sense.rainKmh }), "출처: 도로교통법 시행규칙 제19조 2항")
    line("잔 조향", Grade.DIRECT,
        s.microSteerPerMin?.let { "직진 1분에 핸들 방향 바꿈 ${"%.1f".format(it)}번" } ?: "직진 구간이 짧았어요",
        cmp(s.microSteerPerMin, { it.sense.microSteerPerMin }), "출처: 시행규칙 별표 26(핸들조작 미숙) · 일반 운전 요령")
    line("정체 길 가다 서다", Grade.CALC,
        s.jamBrakesPerMin?.let { "정체 구간 1분에 브레이크 ${"%.1f".format(it)}번" } ?: "내비 정체 구간이 없었어요",
        cmp(s.jamBrakesPerMin, { it.sense.jamBrakesPerMin }), "출처: 한국교통안전공단 경제운전 '정속주행'", chip = "직접+계산")
    line("내기 순환", Grade.DIRECT,
        s.recircMaxMin?.let { "가장 길게 ${"%.1f".format(it)}분 이어졌어요" } ?: "내기 순환을 쓰지 않았어요",
        cmp(s.recircMaxMin, { it.sense.recircMaxMin }),
        "가끔 외기로 바꿔요. 내기만 오래 쓰면 졸릴 수 있어요 — 도로공사 실험: 내기 30분이면 실내 CO₂가 크게 올라요 (공식 인용 기사)")
    line("후진 속도", Grade.EST,
        s.reverseMaxKmh?.let { "후진 최고 약 ${it.toInt()}km/h" } ?: "후진하지 않았어요",
        cmp(s.reverseMaxKmh, { it.sense.reverseMaxKmh }), "후진은 직접 뒤를 보며 천천히 — 현대 사용설명서 '주위 안전 확인' · 일반 운전 요령")
    // 단원 "운전 센스" 별에 쓰는 값
    card.addView(divider())
    card.addView(text("단원 운전 센스", 26f, TEXT, bold = true))
    listOf(Skill.SMOOTH_STOP, Skill.EASE_OFF, Skill.ONE_LANE, Skill.MERGE_SPEED).forEach { sk ->
        card.addView(text("${sk.icon} ${sk.title}: ${sk.lastLine(r).removePrefix("지난 주행: ")}", 22f, MUTED).padTop(6))
    }
    if (s.coasts > 0) card.addView(text("브레이크 없이 천천히 줄인 순간(관성) ${s.coasts}번 · 추정", 22f, MUTED).padTop(6))
}
