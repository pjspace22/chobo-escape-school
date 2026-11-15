package com.example.pleosstarter.school

import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import java.util.Locale

// 👤 마이(L1) → 프로필 고치기·졸업장 크게(L2), ⚙ 설정(L1) → 버전 기록(L2)
// plan-school-duo-screens.md §6, 부록 B-9

private fun km(v: Float) = "%.1f".format(Locale.KOREA, v)

/** 👤 마이 L1: 아바타 크게, 닉네임, 면허 시기, Lv 막대, 졸업장, 통계 요약 4칸 */
internal fun SchoolActivity.renderMy(page: LinearLayout) {
    header(page, "마이")
    val rank = Rank.of(progress.passes)
    page.addView(card {
        it.addView(row().apply {
            addView(text(progress.avatar, 72f, TEXT).apply { setPadding(0, 0, dp(24), 0) })
            addView(column().apply {
                addView(text("${progress.nickname} 님", 32f, TEXT, bold = true))
                addView(text(
                    "${rank.title} · " + (SchoolProgress.LICENSE_LABELS.getOrNull(progress.license) ?: "면허 시기 비어 있음"),
                    22f, if (rank == Rank.GRADUATE) GOLD else GOOD,
                ).padTop(4))
                addView(text("프로필 고치기 ›", 22f, PRIMARY, bold = true).padTop(6))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        it.setOnClickListener { push(SchoolScreen.Profile) }
        val xpIn = progress.xp % SchoolProgress.XP_PER_LEVEL
        it.addView(text("Lv.${progress.level} · 다음 레벨까지 ${SchoolProgress.XP_PER_LEVEL - xpIn} XP", 22f, TEXT).padTop(16))
        it.addView(LinearLayout(this), LinearLayout.LayoutParams(1, dp(8)))
        it.addView(meter(xpIn, SchoolProgress.XP_PER_LEVEL, PRIMARY))
    })
    page.addView(card {
        it.addView(text("🎓 졸업장  ›", 26f, if (progress.graduated) GOLD else MUTED, bold = true))
        it.addView(text(
            if (progress.graduated) "졸업일 ${progress.graduatedOn ?: ""}" else "합격 ${SchoolProgress.GRADUATE_PASSES}번이면 받아요 (지금 ${progress.passes}번)",
            20f, MUTED,
        ).padTop(6))
        it.setOnClickListener { push(SchoolScreen.Certificate) }
    })
    // 운전 ○일째 (부록 I-5 35). 판단: 통계 문구 없이 날짜만
    progress.drivingDays?.let { days ->
        page.addView(card {
            it.addView(text("📅 첫 주행부터 ${days}일째", 26f, TEXT, bold = true))
            it.addView(text(
                if (days >= SchoolProgress.DAYS_BADGE) "첫 주행 ${progress.firstTripDate} · 100일 배지를 받았어요"
                else "첫 주행 ${progress.firstTripDate} · ${SchoolProgress.DAYS_BADGE}일이 되면 배지 \"첫 100일\"",
                20f, MUTED,
            ).padTop(6))
        })
    }
    // 통계 요약 4칸
    val stats = listOf(
        "총 주행" to "${progress.trips}회",
        "누적 거리" to "${km(progress.totalKm)}km",
        "최고 점수" to "${progress.bestScore}점",
        "모은 별" to "${progress.path().totalStars}개",
    )
    val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    stats.forEach { (label, value) ->
        line.addView(column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(CARD)
            setPadding(dp(8), dp(16), dp(8), dp(16))
            addView(text(value, 30f, PRIMARY, bold = true, center = true))
            addView(text(label, 20f, MUTED, center = true).padTop(4))
            setOnClickListener { openMenu(SchoolMenu.RECORD) }
        }, weighted())
    }
    page.addView(line)
}

/** 마이 > 프로필 고치기 (L2): 닉네임 8자, 아바타 6개, 면허 시기 3버튼 */
internal fun SchoolActivity.renderProfile(page: LinearLayout) {
    header(page, "프로필 고치기")
    val name = EditText(this).apply {
        setText(progress.nickname)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(TEXT)
        filters = arrayOf(InputFilter.LengthFilter(SchoolProgress.NICKNAME_MAX))
        isSingleLine = true
    }
    page.addView(card {
        it.addView(text("닉네임 (최대 ${SchoolProgress.NICKNAME_MAX}자)", 22f, MUTED))
        it.addView(name)
        it.addView(button("닉네임 저장", primary = true) {
            progress.nickname = name.text.toString().trim().ifEmpty { SchoolProgress.DEFAULT_NICKNAME }
            render()
        })
    })
    page.addView(card {
        it.addView(text("아바타", 22f, MUTED))
        val avatars = row().apply { setPadding(0, dp(8), 0, 0) }
        SchoolProgress.AVATARS.forEach { a ->
            avatars.addView(text(a, 48f, TEXT, center = true).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                if (a == progress.avatar) background = rounded(PRIMARY_SOFT, 16).apply { setStroke(dp(2), PRIMARY) }
                setOnClickListener {
                    progress.avatar = a
                    render()
                }
            }, weighted())
        }
        it.addView(avatars)
    })
    page.addView(card {
        it.addView(text("면허 시기", 22f, MUTED))
        val buttons = row().apply { setPadding(0, dp(8), 0, 0) }
        SchoolProgress.LICENSE_LABELS.forEachIndexed { i, label ->
            buttons.addView(button(label, primary = progress.license == i) {
                progress.license = i
                render()
            }, weighted())
        }
        it.addView(buttons)
    })
}

/** 마이 > 졸업장 크게 */
internal fun SchoolActivity.renderCertificate(page: LinearLayout) {
    header(page, "졸업장")
    if (progress.graduated) {
        page.addView(card {
            it.addView(text("🎓 초보 탈출 학교 졸업장", 30f, GOLD, bold = true))
            it.addView(text(
                "${progress.nickname} 님은 합격 주행 ${progress.passes}회, 깜빡이 사용 ${progress.signalUses}회,\n" +
                    "총 ${km(progress.totalKm)}km를 달려 초보 운전 과정을 마쳤습니다.\n" +
                    (progress.graduatedOn ?: ""),
                22f, TEXT,
            ).apply { setPadding(0, dp(12), 0, dp(12)) })
            it.addView(button("졸업장 자랑하기 (공유)", primary = true) {
                share(ShareText.graduation(progress.passes, progress.signalUses, progress.totalKm))
            })
        })
    } else {
        // 빈 화면 (부록 H-⑦): 아이콘 + 이유 + 채우는 법(진도 막대) + 버튼
        page.addView(card {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.addView(iconBox("🎓", 96, RAISED))
            it.addView(text("아직 졸업 전이에요", 28f, TEXT, bold = true, center = true).padTop(20))
            it.addView(text("합격 주행 ${SchoolProgress.GRADUATE_PASSES}번이면 졸업장이 여기 생겨요 · 지금 ${progress.passes} / ${SchoolProgress.GRADUATE_PASSES}번",
                24f, MUTED, center = true).apply { setPadding(0, dp(8), 0, dp(16)) })
            it.addView(bar(progress.passes.coerceAtMost(SchoolProgress.GRADUATE_PASSES), SchoolProgress.GRADUATE_PASSES))
            it.addView(button("오늘 배울 것 보기 ›", primary = true) { openMenu(SchoolMenu.LEARN) }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24) }
            })
        })
    }
}

/** ⚙ 설정: 음성 끄기·시연 모드·기록 초기화·버전 기록. 말투 고르기는 11/16 이후 (부록 B-9) */
internal fun SchoolActivity.renderSettings(page: LinearLayout) {
    header(page, "설정")
    page.addView(card {
        it.addView(text("🔊 음성 코치", 26f, TEXT, bold = true))
        it.addView(text(if (engine.voiceEnabled) "켜짐 · 글자 코치는 늘 나와요" else "꺼짐 · 글자 코치만 나와요", 20f, MUTED).padTop(4))
        it.addView(button(if (engine.voiceEnabled) "음성 끄기" else "음성 켜기") {
            engine.voiceEnabled = !engine.voiceEnabled
            progress.voiceOn = engine.voiceEnabled
            render()
        })
    })
    if (progress.graduated) page.addView(card {
        // 졸업하면 잔소리 줄이기 (부록 G-2 12번)
        it.addView(text("🤫 졸업생은 꼭 필요한 말만", 26f, TEXT, bold = true))
        it.addView(text(
            if (progress.quietGraduate) "켜짐 · 칭찬·상황 안내(비·밤·크루즈·차선·열선)는 말하지 않고 기록만 해요. 위험·깜빡이·고속도로·우회전 빨간불 안내는 그대로"
            else "꺼짐 · 졸업 전처럼 모든 코치를 말해요",
            20f, MUTED,
        ).padTop(4))
        it.addView(button(if (progress.quietGraduate) "모든 코치 다시 듣기" else "꼭 필요한 말만 듣기") {
            progress.quietGraduate = !progress.quietGraduate
            render()
        })
    })
    page.addView(card {
        it.addView(text("↱ 차선 미리 안내", 26f, TEXT, bold = true))
        it.addView(text(
            if (progress.laneGuide) "켜짐 · 내비 우회전 500m 전에 \"맨 오른쪽 차선으로\" 알려 줘요"
            else "꺼짐 · 우회전 100m 전 깜빡이 안내만 해요" + if (progress.graduated) " (졸업생 기본)" else "",
            20f, MUTED,
        ).padTop(4))
        it.addView(button(if (progress.laneGuide) "차선 안내 끄기" else "차선 안내 켜기") {
            progress.laneGuide = !progress.laneGuide
            render()
        })
    })
    page.addView(card {
        it.addView(text("🎬 시연 모드", 26f, TEXT, bold = true))
        it.addView(text(
            if (progress.demoMode) "켜짐 · 늘 밤으로 판단, 김 서림 30초, 별 한 번에 3개. 끌 때까지 유지돼요"
            else "꺼짐 · 켜면 늘 밤, 김 서림 30초, 별이 한 번에 3개 올라요",
            20f, if (progress.demoMode) PRIMARY else MUTED,
        ).padTop(4))
        it.addView(button(if (progress.demoMode) "시연 모드 끄기" else "시연 모드 켜기") {
            progress.demoMode = !progress.demoMode
            render()
        })
    })
    page.addView(card {
        it.addView(text("📚 코치 근거 ›", 26f, TEXT, bold = true))
        it.addView(text("코치·점수·별이 무엇을 보고 어디에 근거하는지, 판정 등급(직접·계산·추정)", 20f, MUTED).padTop(6))
        it.setOnClickListener { push(SchoolScreen.Basis()) }
    })
    page.addView(card {
        it.addView(text("ℹ 정보 ›", 26f, TEXT, bold = true))
        it.addView(text("주정차 알림 안내·데이터 출처", 20f, MUTED).padTop(6))
        it.setOnClickListener { push(SchoolScreen.Info) }
    })
    page.addView(card {
        it.addView(text("버전 기록 ›", 26f, TEXT, bold = true))
        it.addView(text("지금 버전 ${appVersion()} · 무엇이 언제 바뀌었는지 볼 수 있어요", 20f, MUTED).padTop(6))
        it.setOnClickListener { push(SchoolScreen.Versions) }
    })
    page.addView(button("기록 초기화") {
        dialog = AlertDialog.Builder(this)
            .setMessage("모든 기록·별·배지·프로필이 지워져요. 되돌릴 수 없어요")
            .setPositiveButton("지우기") { _, _ ->
                progress.reset()
                justFinished = false
                outcome = null
                openMenu(SchoolMenu.LEARN)
            }
            .setNegativeButton("취소", null)
            .show()
    }.onPage().apply { alpha = 0.7f })
}

/** 설정 > 버전 기록: 버전마다 날짜와 바뀐 것 (SchoolVersion.HISTORY) */
internal fun SchoolActivity.renderVersions(page: LinearLayout) {
    header(page, "버전 기록")
    page.addView(text("지금 설치된 버전: ${appVersion()}", 22f, PRIMARY, bold = true).apply { setPadding(0, dp(8), 0, dp(16)) })
    SchoolVersion.HISTORY.forEach { v ->
        page.addView(card {
            it.addView(text("v${v.version}  ·  ${v.date}", 24f, if (v.version == appVersion()) PRIMARY else TEXT, bold = true))
            v.changes.forEach { c -> it.addView(text("· $c", 20f, MUTED).padTop(6)) }
        })
    }
}
