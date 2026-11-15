package com.example.pleosstarter.school

import android.view.Gravity
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 🎯 미션: 오늘의 목표 3개(L1) · 연속 주행 달력(L2) → 그 주의 주행(L3) → 성적표
// plan-school-duo-screens.md §3-6·§3-7, 부록 B-5

private val DAY = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)

/** 🎯 미션 L1 */
internal fun SchoolActivity.renderMission(page: LinearLayout) {
    header(page, "미션")
    val goals = progress.goals()
    page.addView(card {
        it.addView(text("🎯 오늘의 목표  ${goals.count { g -> g.done }} / ${goals.size}", 28f, TEXT, bold = true))
        it.addView(text("하나에 +${SchoolProgress.XP_GOAL} XP, 셋 다 하면 상자 + ${SchoolProgress.XP_GOAL} XP · 밤 12시에 새로 시작", 18f, MUTED).padTop(4))
        goals.forEach { g ->
            it.addView(row().apply {
                setPadding(0, dp(16), 0, dp(6))
                addView(text((if (g.done) "✅ " else "⬜ ") + g.title, 24f, if (g.done) GOOD else TEXT, bold = g.done),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text("${g.value} / ${g.max}", 22f, if (g.done) GOOD else MUTED))
            })
            it.addView(meter(g.value, g.max, PRIMARY))
        }
        if (goals.all { g -> g.done }) it.addView(text("🎁 오늘 상자를 열었어요!", 24f, GOLD, bold = true).padTop(16))
    })
    val streak = progress.streak()
    page.addView(card {
        // 제목(굵게) → 큰 숫자 + 옆에 최고(작게 회색) → 얇은 구분선 → 조건(작게 회색) (부록 E-5)
        it.addView(text("🔥 연속 주행  ›", 28f, TEXT, bold = true))
        it.addView(row().apply {
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.BOTTOM
            if (streak > 0) {
                addView(text("${streak}주 연속", 48f, PRIMARY, bold = true))
                addView(text("최고 ${progress.bestStreak}주", 20f, MUTED).apply { setPadding(dp(16), 0, 0, dp(10)) })
            } else {
                addView(text("이번 주에 한 번 달리면 🔥가 시작돼요", 24f, MUTED))
            }
        })
        if (streak > 0 && progress.weekOf(LocalDate.now()) !in progress.activeWeeks) {
            it.addView(text("이번 주에 한 번 달리면 🔥 ${streak + 1}주가 돼요", 20f, GOOD).padTop(6))
        }
        it.addView(divider())
        it.addView(text("한 주(월~일)에 ${TripResult.QUALIFY_KM}km 넘게 1번 달리면 성공", 18f, MUTED))
        it.setOnClickListener { push(SchoolScreen.Streak) }
    })
}

/** 미션 > 연속 주행 달력 (L2): 최근 12주, 한 줄 = 한 주 */
internal fun SchoolActivity.renderStreak(page: LinearLayout) {
    header(page, "🔥 연속 주행")
    val streak = progress.streak()
    page.addView(text(if (streak > 0) "${streak}주 연속" else "아직 시작 전", 48f, PRIMARY, bold = true).padTop(8))
    page.addView(text("최고 기록 ${progress.bestStreak}주", 24f, TEXT).padTop(4))
    if (streak == 0) page.addView(text("이번 주에 한 번 달리면 🔥가 시작돼요", 22f, MUTED).padTop(8))
    val weeks = progress.activeWeeks
    val thisWeek = progress.weekOf(LocalDate.now())
    val records = progress.records
    page.addView(card {
        (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
        for (w in thisWeek downTo thisWeek - 11) {
            val start = progress.weekStart(w)
            val count = records.count { r -> progress.weekOf(r.date) == w }
            it.addView(row().apply {
                setPadding(0, dp(8), 0, dp(8))
                addView(text("${start.format(DAY)} ~ ${start.plusDays(6).format(DAY)}" + if (w == thisWeek) " (이번 주)" else "",
                    22f, TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(if (w in weeks) "🔥" else "·", 26f, if (w in weeks) GOLD else HINT))
                addView(text(if (count > 0) "  주행 ${count}번 ›" else "", 20f, MUTED))
                if (count > 0) setOnClickListener { push(SchoolScreen.WeekTrips(w)) }
            })
        }
    })
}

/** 미션 > 달력 > 그 주의 주행 (L3). 누르면 그 성적표 */
internal fun SchoolActivity.renderWeekTrips(page: LinearLayout, week: Long) {
    val start = progress.weekStart(week)
    header(page, "${start.format(DAY)} ~ ${start.plusDays(6).format(DAY)} 주행")
    val list = progress.records.filter { r -> progress.weekOf(r.date) == week }
    if (list.isEmpty()) {
        // 빈 화면 (부록 H-⑦)
        page.addView(emptyState("🗓", "이 주의 주행은 볼 수 없어요", "기록은 최근 ${TripRecord.MAX_RECORDS}개까지만 남아요. 🔥 연속 기록은 그대로예요",
            "최근 기록 보기 ›", { openMenu(SchoolMenu.RECORD) }))
        return
    }
    page.addView(card {
        list.forEach { rec -> it.addView(tripRow(rec, SchoolMenu.MISSION)) }
    })
}
