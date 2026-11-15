package com.example.pleosstarter.school

import android.content.Context
import android.graphics.Color
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.TextUtils
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// 학교 화면 공통 색과 그리기 도우미. 화면 파일(…Screens.kt)이 같이 쓴다.
// 밝은 바탕 + 흰 카드 + 파랑 하나만 강조 (plan-school-duo-screens.md 부록 F, 진범 미니게임 앱과 같은 값).
// 진범 colors_minigame.xml을 가져다 쓰지 않고 같은 값을 여기에 둔다 (브랜치 충돌 방지, F-5)

/** 화면 바탕 (mg_bg) */
internal val BG = Color.parseColor("#F2F4F6")
/** 카드 (mg_surface) */
internal val CARD = Color.parseColor("#FFFFFF")
/** 제목·주 글자 (mg_text) */
internal val TEXT = Color.parseColor("#191F28")
/** 설명 글자 (mg_text_sub) */
internal val MUTED = Color.parseColor("#4E5968")
/** 힌트·잠김 글자. 대비 4.5:1을 넘게 #8B95A1 → #5F6B7A (부록 H-①: 흰 카드 5.43 · 회색 바탕 4.92) */
internal val HINT = Color.parseColor("#5F6B7A")
/** 하나뿐인 강조색: 주 버튼·선택·합격 점수 (mg_primary) */
internal val PRIMARY = Color.parseColor("#2E6BF6")
/** 파랑 **글자**. 연파랑·회색 바탕 위에서도 4.5:1이 넘게 (부록 H-①, 5.28). 버튼 바탕 파랑은 [PRIMARY] 그대로 */
internal val PRIMARY_TEXT = Color.parseColor("#2459D6")
/** 아이콘 받침·선택된 메뉴 바탕 (mg_primary_soft) */
internal val PRIMARY_SOFT = Color.parseColor("#E9F0FE")
/** 잘함 = 파랑으로 통일 (F-1) */
internal val GOOD = PRIMARY
/** 놓침 (mg_x) / 놓친 카드 바탕 (mg_x_soft) */
internal val BAD = Color.parseColor("#E5484D")
internal val BAD_SOFT = Color.parseColor("#FDEDEE")
/** 빨강 **글자** (부록 H-①: 흰 바탕 5.18 · 연빨강 바탕 4.57). 바탕·막대처럼 장식 빨강은 [BAD] 그대로 */
internal val BAD_TEXT = Color.parseColor("#C9353A")
/** 별·배지·졸업에만 */
internal val GOLD = Color.parseColor("#F5A524")
/** 주행 화면 "상황 안내" 아이콘 받침 (판단: 금색을 옅게) */
internal val GOLD_SOFT = Color.parseColor("#FEF3DC")
/** 판정 카드 뒤 어둡게 깔리는 막 */
internal val SCRIM = Color.parseColor("#99000000")
internal val SIDEBAR = Color.parseColor("#FFFFFF")
/** 표지판 초록 (단원 시험 통과 등 길 안내 느낌에만) */
internal val BAND = Color.parseColor("#1F8A4C")
/** 구분선·잠긴 칸 (mg_line) */
internal val LINE = Color.parseColor("#E5E8EB")
internal val LOCKED_NODE = LINE
/** 진행 막대의 빈 칸 */
internal val TRACK = LINE
/** 파랑 위 흰 글자 (mg_on_primary) */
internal val INK = Color.parseColor("#FFFFFF")
/** 보조 버튼·칩 바탕 (mg_surface_alt) */
internal val RAISED = Color.parseColor("#F2F4F6")
/** 화면 바깥 여백 (F-2: 좌우 56dp, 위아래 48dp) */
internal const val PAGE_H_DP = 56
internal const val PAGE_V_DP = 48
/** 보조 글자 최소 크기 (부록 H-③: 본문 20·행동 22 → 24sp). 제목 34·큰 숫자 72는 그대로 */
internal const val MIN_SP = 24f
/** 누르는 것의 최소 높이·폭 (부록 H-②, Google 차량 기준 76×76dp) */
internal const val TOUCH_DP = 76

/**
 * 캐릭터는 이 함수 하나로만 그린다 (부록 B-10). 캐릭터가 정해지면 여기만 바꾼다.
 * 경로 지금 노드 옆, 미리 배우기 카드, 성적표 위, 축하 화면에서 쓴다.
 */
internal fun character(): String = "🐣"

internal fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

internal fun Context.row() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

internal fun Context.weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    .apply { setMargins(dp(8), 0, dp(8), 0) }

internal fun Context.rounded(color: Int, radiusDp: Int = 16) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
}

/** 흰 카드: 둥글기 28, 안 여백 36/32, 카드 사이 24. 그림자·테두리 없이 바탕과의 차이로만 구분 (F-2) */
internal fun Context.card(fill: (LinearLayout) -> Unit): LinearLayout = column().apply {
    background = rounded(CARD, 28)
    setPadding(dp(36), dp(32), dp(36), dp(32))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(24) }
    fill(this)
}

/** 84dp 둥근 사각 받침 안의 이모지 (진범 홈 카드 아이콘과 같은 꼴, F-2) */
internal fun Context.iconBox(emoji: String, sizeDp: Int = 84, fill: Int = PRIMARY_SOFT): TextView =
    text(emoji, sizeDp * 0.42f, TEXT, center = true, lines = 1, minSp = 0f).apply {
        background = rounded(fill, (sizeDp * 0.2f).toInt())
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** 칸 나눈 진행 막대 (졸업 진도처럼 몇 번 중 몇 번) */
internal fun Context.bar(value: Int, max: Int): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16))
    for (i in 0 until max) {
        addView(View(context).apply {
            background = rounded(if (i < value) GOOD else TRACK, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
        })
    }
}

/** 이어진 진행 막대 (37 / 50 처럼 큰 수) */
internal fun Context.meter(value: Int, max: Int, color: Int = GOOD): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    background = rounded(TRACK, 8)
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16))
    val filled = if (max <= 0) 0f else value.coerceIn(0, max).toFloat() / max
    if (filled > 0f) addView(View(context).apply { background = rounded(color, 8) },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, filled))
    if (filled < 1f) addView(View(context), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - filled))
}

/**
 * 글자 한 덩이. 부록 H:
 * - ③ [minSp](기본 24sp)보다 작게 쓰지 않는다. 사이드바 이름처럼 칸이 좁은 곳만 minSp = 0으로 뺀다
 * - ① 파랑·빨강 글자는 대비가 높은 글자용 색([PRIMARY_TEXT]·[BAD_TEXT])으로 바꿔 칠한다
 * - ④ [lines] > 0이면 그 줄 수에서 "…"로 자른다. Android 13+는 한국어를 단어(구) 단위로 줄바꿈
 * - [scale] = false면 글꼴 크기 설정을 따르지 않는다 (폭이 고정된 사이드바만. 글꼴 200%에서 "미션…"으로 잘려서)
 */
internal fun Context.text(
    s: String, sp: Float, color: Int, bold: Boolean = false, center: Boolean = false,
    lines: Int = 0, minSp: Float = MIN_SP, scale: Boolean = true,
) = TextView(this).apply {
    text = s
    setTextSize(if (scale) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_DIP, maxOf(sp, minSp))
    setTextColor(textColor(color))
    if (bold) setTypeface(typeface, Typeface.BOLD)
    if (center) gravity = Gravity.CENTER
    if (lines > 0) {
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
}

/** 글자에 쓰는 색: 바탕용 파랑·빨강이 들어오면 글자용 진한 값으로 (부록 H-①) */
internal fun textColor(c: Int): Int = when (c) {
    PRIMARY -> PRIMARY_TEXT
    BAD -> BAD_TEXT
    else -> c
}

/**
 * 큰 숫자(점수 72sp 등): 1줄로, 칸이 모자라면 [maxSp]에서 24sp까지 줄인다 (부록 H-④ autosize).
 * "100점"이 "10 / 0점"으로 깨지지 않게. 자동 축소는 폭이 정해져 있어야 해서 [widthDp]를 준다
 */
internal fun TextView.autoFit(maxSp: Float, widthDp: Int): TextView = apply {
    maxLines = 1
    setAutoSizeTextTypeUniformWithConfiguration(MIN_SP.toInt(), maxSp.toInt(), 2, TypedValue.COMPLEX_UNIT_SP)
    layoutParams = LinearLayout.LayoutParams((widthDp * resources.displayMetrics.density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
}

/**
 * 다 그린 화면을 한 번 훑어 차 화면 규칙을 맞춘다 (부록 H-②·④). render() 끝에서 부른다.
 * - ② 누르는 것(클릭 리스너가 있는 것)은 높이·폭 76dp 이상, 글자는 세로 가운데
 * - ④ 가로 줄 안의 글자 = 목록 줄의 제목·값 → 굵은 글자 1줄, 보통 글자 2줄 + "…".
 *   줄 안 세로 묶음(아이콘 옆 제목·설명)도 같다. 카드 본문·상세 문장은 자르지 않는다 (판단: 상세 화면이 곧 "자세히")
 */
internal fun View.fitForCar() {
    val min = (TOUCH_DP * resources.displayMetrics.density).toInt()
    if (isClickable && this !is ScrollView) {
        if (minimumHeight < min) minimumHeight = min
        if (minimumWidth < min) minimumWidth = min
        if (this is TextView && this !is Button) gravity = (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) or Gravity.CENTER_VERTICAL
    }
    if (this is TextView && this !is Button && maxLines == Int.MAX_VALUE) {
        val p = parent as? LinearLayout
        val inRow = p?.orientation == LinearLayout.HORIZONTAL
        val inRowBlock = p?.orientation == LinearLayout.VERTICAL && (p.parent as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL
        if (inRow || inRowBlock) {
            maxLines = if (typeface?.isBold == true) 1 else 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).fitForCar()
}

/**
 * 빈 화면 (부록 H-⑦): 아이콘 받침 + 왜 비었는지 + 채우는 법 + 주 버튼 1개 (+ 보조 버튼).
 * 글만 "아직 …없어요"로 두지 않는다
 */
internal fun Context.emptyState(
    icon: String, reason: String, how: String,
    action: String, onAction: () -> Unit,
    second: String? = null, onSecond: (() -> Unit)? = null,
): LinearLayout = card {
    it.gravity = Gravity.CENTER_HORIZONTAL
    it.addView(iconBox(icon, 96, RAISED))
    it.addView(text(reason, 28f, TEXT, bold = true, center = true).padTop(20))
    it.addView(text(how, 24f, MUTED, center = true).padTop(8))
    it.addView(button(action, primary = true, onClick = onAction).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(24) }
    })
    if (second != null && onSecond != null) it.addView(button(second, onClick = onSecond).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(24) }
    })
}

/**
 * 불러오지 못함 (부록 H-⑧): "무엇을 + 어떻게", 사용자 탓 없이, 기록이 안전하다는 말 먼저 + [다시 시도]
 */
internal fun Context.errorState(what: String, onRetry: () -> Unit): LinearLayout = card {
    it.gravity = Gravity.CENTER_HORIZONTAL
    it.addView(iconBox("⚠️", 96, BAD_SOFT))
    it.addView(text("${what}을 불러오지 못했어요", 28f, TEXT, bold = true, center = true).padTop(20))
    it.addView(text("저장된 기록은 그대로예요. 다시 시도해 주세요", 24f, MUTED, center = true).padTop(8))
    it.addView(button("다시 시도", primary = true, onClick = onRetry).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(24) }
    })
}

/** 큰 버튼: 둥글기 20, 높이 76dp 이상, 글자 24sp, 2줄까지 (F-2, 부록 H-②③④). 주 = 파랑+흰 글자, 보조 = 연회색+진한 글자 */
internal fun Context.button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    maxLines = 2
    ellipsize = TextUtils.TruncateAt.END
    setTextSize(TypedValue.COMPLEX_UNIT_SP, MIN_SP)
    stateListAnimator = null
    background = rounded(if (primary) PRIMARY else RAISED, 20)
    setTextColor(if (primary) INK else TEXT)
    setTypeface(typeface, Typeface.BOLD)
    minHeight = dp(TOUCH_DP)
    setPadding(dp(24), dp(14), dp(24), dp(14))
    pressable()
    setOnClickListener { onClick() }
}

/** 화면 바탕(연회색) 위에 바로 놓는 보조 버튼은 흰 바탕으로 (판단: 연회색 보조 버튼은 카드 안에서만 보임) */
internal fun Button.onPage(): Button = apply { background = context.rounded(CARD, 20) }

/**
 * 앱 애니메이션 켜고 끄기. 0.19.0 현규 결정(11/10): 에뮬레이터 렉 때문에 앱 안 3D·애니메이션은 사진(정지 화면)으로,
 * 움직임은 웹 버전에서 개발. 되돌리려면 true
 */
internal const val ANIMATIONS = false

/** 누르면 물결 + 살짝 작아졌다 돌아오는 눌림 효과 (부록 E-1 "버튼처럼"). ANIMATIONS=false면 없음 */
internal fun View.pressable(): View = apply {
    if (!ANIMATIONS) return@apply
    foreground = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(Color.argb(20, 0, 0, 0)), null, null, // 밝은 바탕이라 검정 물결
    )
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

/** 알약 칩 (헤더 🔥1주 🎯3/3 Lv.6). 흰 알약, [selected]면 연파랑 + 파랑 테두리 2dp (F-2). 1줄 + "…" (부록 H-④) */
internal fun Context.chip(label: String, color: Int = TEXT, selected: Boolean = false): TextView =
    text(label, 24f, if (selected) PRIMARY else color, bold = true, lines = 1).apply {
    background = rounded(if (selected) PRIMARY_SOFT else CARD, 20).apply { if (selected) setStroke(dp(2), PRIMARY) }
    setPadding(dp(16), dp(6), dp(16), dp(6))
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(0, 0, dp(8), 0) }
}

/** 얇은 구분선 */
internal fun Context.divider(): View = View(this).apply {
    setBackgroundColor(LINE)
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        .apply { setMargins(0, dp(12), 0, dp(12)) }
}

internal fun TextView.padTop(dpValue: Int): TextView = apply {
    setPadding(0, (dpValue * resources.displayMetrics.density).toInt(), 0, 0)
}
