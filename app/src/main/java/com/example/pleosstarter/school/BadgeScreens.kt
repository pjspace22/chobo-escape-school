package com.example.pleosstarter.school

import android.view.Gravity
import android.widget.LinearLayout

// 🏅 배지: 묶음별 격자(L1) → 배지 상세(L2, 단계형 Ⅰ·Ⅱ·Ⅲ) → [공유]
// plan-school-duo-screens.md §5, 부록 B-7·B-11

private const val GRID_COLUMNS = 5

/** 🏅 배지 L1: 맨 위 "배지 n / 25", 묶음마다 격자. 얻은 것 색, 아닌 것 회색 + 자물쇠 */
internal fun SchoolActivity.renderBadges(page: LinearLayout) {
    header(page, "배지  ${progress.earnedSteps} / ${BadgeItem.TOTAL_STEPS}")
    page.addView(text("단계형 배지는 Ⅰ·Ⅱ·Ⅲ 단계마다 하나로 세요", 18f, MUTED).apply { setPadding(0, dp(4), 0, dp(8)) })
    // 하나도 없으면 격자 위에 빈 화면 안내 (부록 H-⑦). 판단: 잠긴 배지 격자는 "무엇을 얻을 수 있는지"라 그대로 둔다
    if (progress.earnedSteps == 0) page.addView(emptyState("🏅", "아직 받은 배지가 없어요",
        "주행을 마치면 깜빡이·첫 빗길 같은 배지가 생겨요", "오늘 배울 것 보기 ›", { openMenu(SchoolMenu.LEARN) }).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
    })
    BadgeItem.GROUPS.forEach { (group, items) ->
        // 대분류 제목 줄: 아이콘 + 이름 + "3/5", 위 여백 24dp + 얇은 구분선 (부록 E-5)
        val got = items.count { progress.stepsOf(it) > 0 }
        page.addView(row().apply {
            setPadding(0, dp(24), 0, 0)
            addView(text("${groupIcon(group)} $group", 26f, TEXT, bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("$got/${items.size}", 22f, if (got == items.size) GOOD else MUTED, bold = true))
        })
        page.addView(divider())
        items.chunked(GRID_COLUMNS).forEach { chunk ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { b -> line.addView(badgeCell(b), weighted()) }
            repeat(GRID_COLUMNS - chunk.size) { line.addView(LinearLayout(this), weighted()) }
            // 줄 사이 16dp (좌우 간격 8+8과 같게). 없으면 위아래 칸이 붙어 겹쳐 보임
            page.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(16) })
        }
    }
}

private fun groupIcon(group: String) = when (group) {
    "주행 습관" -> "🚗"
    "첫 경험" -> "✨"
    "학습 경로" -> "🗺"
    "꾸준함" -> "🔥"
    "졸업" -> "🎓"
    else -> "🏅"
}

private fun SchoolActivity.badgeCell(b: BadgeItem): LinearLayout = column().apply {
    val steps = progress.stepsOf(b)
    val has = steps > 0
    gravity = Gravity.CENTER_HORIZONTAL
    background = rounded(if (has) CARD else LINE, 20)
    setPadding(dp(8), dp(14), dp(8), dp(14))
    addView(text(if (has) b.icon else "🔒", 40f, TEXT, center = true))
    val tier = (b as? BadgeItem.Tier)?.let { t -> if (steps > 0) " ${TierBadge.ROMAN[steps - 1]}" else "" } ?: ""
    addView(text(b.title + tier, 18f, if (has) GOLD else MUTED, bold = has, center = true).padTop(6))
    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    setOnClickListener { push(SchoolScreen.BadgeDetail(b.key)) }
}

/** 배지 > 상세 (L2): 얻는 법, 단계별 진행 막대·얻은 날짜, 얻게 된 주행, [공유] */
internal fun SchoolActivity.renderBadgeDetail(page: LinearLayout, key: String) {
    val b = BadgeItem.byKey(key) ?: return
    header(page, "${b.icon} ${b.title}")
    val steps = progress.stepsOf(b)
    page.addView(card {
        it.addView(text(b.hint, 24f, TEXT))
        when (b) {
            is BadgeItem.Tier -> {
                val t = b.tier
                val v = progress.tierValue(t)
                t.steps.forEachIndexed { i, need ->
                    val step = i + 1
                    val got = steps >= step
                    it.addView(row().apply {
                        setPadding(0, dp(16), 0, dp(6))
                        addView(text("${TierBadge.ROMAN[i]}  $need${t.unit}", 24f, if (got) GOLD else TEXT, bold = got),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(text(if (got) "얻음 ${progress.badgeDate(b.key, step) ?: ""}" else "${v.coerceAtMost(need)} / $need",
                            22f, if (got) GOOD else MUTED))
                    })
                    it.addView(meter(v, need, if (got) GOLD else GOOD))
                    if (got) tripLink(it, b.key, step)
                }
            }
            else -> {
                if (steps > 0) {
                    it.addView(text("얻음 ${progress.badgeDate(b.key) ?: ""}", 24f, GOOD, bold = true).padTop(12))
                    tripLink(it, b.key, 0)
                } else {
                    it.addView(text("아직이에요", 24f, MUTED).padTop(12))
                }
            }
        }
    })
    if (steps > 0) {
        val label = if (b is BadgeItem.Tier) "${b.title} ${TierBadge.ROMAN[steps - 1]}" else b.title
        page.addView(button("배지 자랑하기 (공유)", primary = true) {
            share("🏅 '$label' 배지 획득! ${b.hint} #초보탈출학교")
        })
    }
}

/** "이 주행에서 얻음 ›" 줄. 그 주행이 최근 30개 밖이면 안 보인다 */
private fun SchoolActivity.tripLink(parent: LinearLayout, key: String, step: Int) {
    val id = progress.badgeTrip(key, step) ?: return
    if (progress.record(id) == null) return
    parent.addView(text("이 주행에서 얻음 ›", 22f, PRIMARY, bold = true).apply {
        setPadding(0, dp(6), 0, 0)
        setOnClickListener { push(SchoolScreen.Report(id, SchoolMenu.BADGE)) }
    })
}
