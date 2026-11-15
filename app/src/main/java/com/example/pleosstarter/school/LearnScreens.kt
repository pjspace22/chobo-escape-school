package com.example.pleosstarter.school

import android.animation.ObjectAnimator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

// 🏠 배우기: 이어서 하기 카드 + 단원 카드·기술 줄(L1) · 기술 카드 시트(L2) · 단원 가이드북(L2) · 미리 배우기 카드(L3~L4)
// plan-school-duo-screens.md §3, 부록 A·B-2~B-4, 부록 F-7 (도로 그림 E-4를 대체)

/** 단원 카드 머리의 아이콘 (판단: 단원 첫 기술과 어울리는 이모지) */
private val PathUnit.icon: String
    get() = when (this) {
        PathUnit.START -> "🚦"
        PathUnit.SIGNAL -> "↪"
        PathUnit.WEATHER -> "🌧"
        PathUnit.PARKING -> "🅿"
        PathUnit.SENSE -> "🧭"
        PathUnit.DEFENSIVE -> "🛡"
    }

/** 별 3개: 채운 별 금색, 빈 별 힌트색 */
private fun SchoolActivity.stars(n: Int, sp: Float = 24f, empty: Int = HINT, max: Int = LearningPath.MAX_STARS): LinearLayout = row().apply {
    for (i in 0 until max) addView(text(if (i < n) "★" else "☆", sp, if (i < n) GOLD else empty, bold = true))
}

/**
 * 배우기 맨 위: 화면 제목 + "닉네임 님 · 등급" + 칩 줄 🔥 🎯 Lv (부록 F-7 "지금과 같음").
 * 🔥·🎯·Lv는 미션 메뉴로
 */
internal fun SchoolActivity.topLine(page: LinearLayout) {
    val rank = Rank.of(progress.passes)
    page.addView(text("배우기", 34f, TEXT, bold = true))
    page.addView(text("${progress.avatar} ${progress.nickname} 님 · ${rank.title}", 20f,
        if (rank == Rank.GRADUATE) GOLD else MUTED, bold = true).padTop(8))
    val goalsDone = progress.goals().count { it.done }
    page.addView(row().apply {
        setPadding(0, dp(16), 0, dp(24))
        addView(chip("🔥 ${progress.streak()}주").apply { pressable(); setOnClickListener { openMenu(SchoolMenu.MISSION) } })
        addView(chip("🎯 $goalsDone/3").apply { pressable(); setOnClickListener { openMenu(SchoolMenu.MISSION) } })
        addView(chip("Lv.${progress.level}", selected = true).apply { pressable(); setOnClickListener { openMenu(SchoolMenu.MISSION) } })
    })
}

internal fun SchoolActivity.renderLearn(page: LinearLayout) {
    // 카드 시트가 열려 있으면 빈 곳을 눌러도 닫힌다 (부록 B-4)
    if (openNode != null) page.setOnClickListener {
        openNode = null
        render()
    }
    topLine(page)
    val path = progress.path()
    val current = path.current()
    page.addView(continueCard(path, current))

    // 지금 하는 단원만 펼쳐 둔다. 완료·잠긴 단원은 접힌 한 줄, 누르면 펼침 (openUnits는 "기본과 반대로 바꾼" 단원)
    val currentUnit = when (current) {
        is PathNode.SkillNode -> current.skill.unit
        is PathNode.Test -> current.unit
        PathNode.Graduate -> null
    }
    // 단원 1~4 → 졸업 시험 → 단원 5 "위험 피하기"(졸업생 심화, 부록 G-4)
    fun unit(u: PathUnit) {
        val open = openNode.let { n -> (n is PathNode.SkillNode && n.skill.unit == u) || (n is PathNode.Test && n.unit == u) }
        val expanded = open || ((u == currentUnit) != (u in openUnits))
        page.addView(unitCard(path, u, expanded, current))
    }
    PathUnit.values().filter { it != PathUnit.DEFENSIVE }.forEach { unit(it) }
    page.addView(graduateCard(path))
    unit(PathUnit.DEFENSIVE)
}

/**
 * "이어서 하기" 큰 카드 (파랑 바탕 + 흰 글자, 부록 F-7). 지금 할 노드 + 캐릭터 말풍선을 대신한다.
 * 기술이면 [이번 주행 연습하기], 시험·졸업이면 [기준 보기]로 카드 시트를 연다
 */
private fun SchoolActivity.continueCard(path: LearningPath, current: PathNode): LinearLayout = column().apply {
    background = rounded(PRIMARY, 28)
    setPadding(dp(36), dp(32), dp(36), dp(32))
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(24) }
    addView(text("${character()} 이어서 하기", 22f, INK, bold = true))
    val title = row().apply { setPadding(0, dp(12), 0, 0) }
    when (current) {
        is PathNode.SkillNode -> {
            val s = current.skill
            title.addView(text("단원 ${s.unit.no} · ${s.title}  ", 34f, INK, bold = true))
            title.addView(stars(path.stars(s), 30f, PRIMARY_SOFT, s.maxStars)) // 파랑 위라 빈 별은 연파랑
        }
        is PathNode.Test -> title.addView(text("단원 ${current.unit.no} 시험 · 별 ${path.unitStars(current.unit)}/${current.unit.testStars}", 34f, INK, bold = true))
        PathNode.Graduate -> title.addView(text("🎓 졸업 시험 · 합격 ${progress.passes.coerceAtMost(SchoolProgress.GRADUATE_PASSES)}/${SchoolProgress.GRADUATE_PASSES}", 34f, INK, bold = true))
    }
    addView(title)
    val target = progress.target
    addView(text(
        if (target != null) "🎯 이번 주행 연습: ${target.title} · 기어를 D로 놓으면 시작해요" else "기어를 D로 놓으면 주행이 시작돼요",
        20f, INK,
    ).padTop(8))
    val skill = (current as? PathNode.SkillNode)?.skill
    val label = when {
        skill != null && progress.target == skill -> "연습 중 ✓"
        skill != null -> "이번 주행 연습하기 ›"
        else -> "기준 보기 ›"
    }
    addView(text(label, 22f, PRIMARY, bold = true, center = true).apply {
        background = rounded(CARD, 20)
        minHeight = dp(TOUCH_DP)
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(12), dp(28), dp(12))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(20) }
        pressable()
        setOnClickListener {
            if (skill != null) progress.target = if (progress.target == skill) null else skill
            else openNode = current
            render()
        }
    })
}

/**
 * 단원 카드 한 장 (부록 F-7). 펼침 = 머리(아이콘 받침 + 제목 + ★ n/9) + 기술 줄 + 단원 시험 줄.
 * 접힘 = 완료 "✓ 단원 1 · 출발과 정차 ★ 6/6" / 잠김 "🔒 단원 3 · 날씨와 밤 — …" 한 줄. 머리를 누르면 접고 펼친다
 */
private fun SchoolActivity.unitCard(path: LearningPath, u: PathUnit, expanded: Boolean, current: PathNode): LinearLayout = card {
    val open = path.unitOpen(u)
    val passed = path.unitPassed(u)
    val max = u.maxStars
    val toggle = View.OnClickListener {
        if (!openUnits.remove(u)) openUnits.add(u)
        openNode = null
        render()
    }
    if (!expanded) {
        // 접힌 한 줄
        it.setPadding(dp(36), dp(24), dp(36), dp(24))
        it.addView(row().apply {
            addView(text(
                when {
                    !open -> "🔒 단원 ${u.no} · ${u.title}"
                    passed -> "✓ 단원 ${u.no} · ${u.title}"
                    else -> "단원 ${u.no} · ${u.title}"
                },
                24f, if (open) MUTED else HINT, bold = true,
            ), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(
                if (open) "★ ${path.unitStars(u)}/$max" else u.lockHint,
                if (open) 22f else 20f, if (open) PRIMARY else HINT, bold = open,
            ))
            addView(text("  ›", 28f, HINT))
        })
        it.setOnClickListener(toggle)
        return@card
    }
    // 펼친 머리
    it.addView(row().apply {
        addView(iconBox(u.icon, 84, if (open) PRIMARY_SOFT else LINE))
        addView(column().apply {
            setPadding(dp(24), 0, dp(12), 0)
            if (open && progress.graduated && !passed) addView(text("🎓 졸업생 심화 과정", 18f, GOLD, bold = true))
            addView(text("${if (open) "" else "🔒 "}단원 ${u.no} · ${u.title}", 30f, if (open) TEXT else HINT, bold = true))
            addView(text(
                if (open) "📖 가이드북 ›" else u.lockHint,
                20f, if (open) PRIMARY else HINT, bold = open,
            ).padTop(4).apply {
                if (open) setOnClickListener {
                    openNode = null
                    push(SchoolScreen.Guide(u))
                }
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(if (passed) "✓ 완료 ★ ${path.unitStars(u)}/$max" else "★ ${path.unitStars(u)}/$max", 24f,
            if (open) PRIMARY else HINT, bold = true))
        setOnClickListener(toggle)
    })
    val nodes = u.skills.map { s -> PathNode.SkillNode(s) } + PathNode.Test(u)
    nodes.forEach { n -> addNodeRow(it, path, n, n == current) }
}

/** 단원 카드 안 기술 한 줄 (이름 + 별 3개 + ›) 또는 단원 시험 줄. 누르면 바로 아래에 카드 시트 */
private fun SchoolActivity.addNodeRow(parent: LinearLayout, path: LearningPath, n: PathNode, isCurrent: Boolean) {
    val state = path.state(n)
    val locked = state == NodeState.LOCKED
    parent.addView(divider())
    val line = row().apply {
        minimumHeight = dp(TOUCH_DP)
        when (n) {
            is PathNode.SkillNode -> {
                val s = n.skill
                addView(text(s.icon, 26f, TEXT, center = true).apply { layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT) })
                addView(text((if (s == progress.target) "🎯 " else "") + s.title + if (isCurrent) "  · 지금" else "",
                    24f, if (locked) HINT else TEXT, bold = isCurrent),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                // 잠긴 단원은 이름만 미리 보여 준다 (별 없음)
                if (!locked) addView(stars(path.stars(s), max = s.maxStars))
            }
            is PathNode.Test -> {
                val u = n.unit
                addView(text("🏁", 26f, TEXT, center = true).apply { layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT) })
                addView(text("단원 시험", 24f, if (locked) HINT else TEXT, bold = isCurrent),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val ready = path.unitStars(u) >= u.testStars
                addView(text(
                    when {
                        path.unitPassed(u) -> "통과"
                        ready -> "${TripResult.PASS_SCORE}점 인정 주행이면 통과"
                        else -> "별 ${u.testStars}개 모이면 열려요"
                    },
                    20f, if (path.unitPassed(u) || ready) PRIMARY else HINT, bold = path.unitPassed(u),
                ))
            }
            PathNode.Graduate -> Unit
        }
        addView(text("  ›", 28f, HINT))
        pressable()
    }
    line.setOnClickListener {
        openNode = if (openNode == n) null else n
        shakeNode = if (locked) n else null
        render()
    }
    parent.addView(line)
    // 잠긴 줄은 살짝 흔든다 (부록 B-4)
    if (shakeNode == n) {
        shakeNode = null
        if (ANIMATIONS) ObjectAnimator.ofFloat(line, "translationX", 0f, 16f, -16f, 10f, -10f, 0f).setDuration(400).start()
    }
    if (openNode == n) parent.addView(bubble(path, n, state))
}

/** 맨 아래 졸업 시험 카드 (단원과 상관없이 합격 3번) */
private fun SchoolActivity.graduateCard(path: LearningPath): LinearLayout = card {
    val n = PathNode.Graduate
    it.addView(row().apply {
        addView(iconBox("🎓", 84, if (progress.graduated) GOLD_SOFT else PRIMARY_SOFT))
        addView(column().apply {
            setPadding(dp(24), 0, dp(12), 0)
            addView(text("졸업 시험", 30f, TEXT, bold = true))
            addView(text(if (progress.graduated) "졸업했어요! 졸업장은 마이 메뉴에" else "합격 ${SchoolProgress.GRADUATE_PASSES}번이면 졸업", 20f, MUTED).padTop(4))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("${progress.passes.coerceAtMost(SchoolProgress.GRADUATE_PASSES)}/${SchoolProgress.GRADUATE_PASSES}", 24f,
            if (progress.graduated) GOLD else PRIMARY, bold = true))
        pressable()
        setOnClickListener {
            openNode = if (openNode == n) null else n
            render()
        }
    })
    if (openNode == n) it.addView(bubble(path, n, path.state(n)))
}

internal fun starText(n: Int) = "★".repeat(n) + "☆".repeat(LearningPath.MAX_STARS - n)

/** 줄을 누르면 바로 아래에 펼쳐지는 카드 시트 (L2, §3-3·부록 B-3·B-4). 흰 카드 안이라 연회색 바탕 */
private fun SchoolActivity.bubble(path: LearningPath, n: PathNode, state: NodeState): LinearLayout = column().also {
    it.background = rounded(RAISED, 20)
    it.setPadding(dp(28), dp(24), dp(28), dp(24))
    it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(0, dp(8), 0, dp(8)) }
    // 아래에서 살짝 올라오며 나타남
    if (ANIMATIONS) {
        it.alpha = 0f
        it.translationY = dp(24).toFloat()
        it.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }
    it.setOnClickListener { } // 시트 안을 눌러도 닫히지 않게
    fun line(s: String, sp: Float, color: Int, bold: Boolean = false) =
        it.addView(text(s, sp, color, bold).padTop(8))
    if (state == NodeState.LOCKED) {
        val title = when (n) {
            is PathNode.SkillNode -> "${n.skill.icon} ${n.skill.title}"
            is PathNode.Test -> "🏁 단원 ${n.unit.no} 시험"
            PathNode.Graduate -> "🎓 졸업 시험"
        }
        it.addView(text(title, 28f, TEXT, bold = true))
        val hint = when (n) {
            is PathNode.SkillNode -> n.skill.unit.lockHint
            is PathNode.Test -> n.unit.lockHint
            PathNode.Graduate -> ""
        }
        line("🔒 $hint", 22f, MUTED)
        return@also
    }
    when (n) {
        is PathNode.SkillNode -> {
            val s = n.skill
            it.addView(text("${s.icon} ${s.title}", 28f, TEXT, bold = true))
            val got = path.stars(s)
            it.addView(row().apply {
                setPadding(0, dp(8), 0, 0)
                addView(stars(got, max = s.maxStars))
                addView(text("  " + if (got >= s.maxStars) "별을 다 모았어요" else "다음 별: ${s.nextStar}", 22f, TEXT, bold = true))
            })
            progress.lastResult?.let { r -> line(s.lastLine(r), 20f, MUTED) }
            // 판정 등급 (부록 I-1). 추정·카드만은 점수에 안 들어감
            line("판정: ${s.grade.label}" + if (s.grade.scored) "" else " · 점수·졸업엔 안 들어가요", 20f, HINT)
            line(if (s.needsQualified) "별은 인정 주행(${TripResult.QUALIFY_KM}km 이상 + 핸들 ${TripResult.QUALIFY_TURNS}번 이상)에서만 올라요"
                else "이 기술은 연습 주행에서도 별이 올라요", 18f, HINT)
            val buttons = row().apply { setPadding(0, dp(16), 0, 0) }
            buttons.addView(button("미리 배우기") { push(SchoolScreen.Preview(s, 0)) }.apply { background = rounded(CARD, 20) }, weighted())
            // 한 번에 하나. 다른 기술을 고르면 바뀐다
            buttons.addView(button(if (progress.target == s) "연습 중 ✓ (누르면 취소)" else "이번 주행 연습하기", primary = true) {
                progress.target = if (progress.target == s) null else s
                render()
            }, weighted())
            it.addView(buttons)
        }
        is PathNode.Test -> {
            val u = n.unit
            it.addView(text("🏁 단원 ${u.no} 시험", 28f, TEXT, bold = true))
            if (path.unitPassed(u)) line("통과했어요! 다음 단원이 열렸어요", 22f, PRIMARY, bold = true)
            line("① 단원 기술 별 ${path.unitStars(u)} / ${u.testStars}개", 22f,
                if (path.unitStars(u) >= u.testStars) PRIMARY else TEXT)
            line("② ${TripResult.PASS_SCORE}점 이상 인정 주행 1번 (①을 채운 뒤 주행이 끝날 때 판정)", 22f, TEXT)
            line("따로 시험 화면은 없어요. 두 가지를 채우면 자동으로 통과해요", 18f, HINT)
        }
        PathNode.Graduate -> {
            it.addView(text("🎓 졸업 시험", 28f, TEXT, bold = true))
            line(if (progress.graduated) "졸업했어요! 졸업장은 마이 메뉴에 있어요"
                else "합격 ${progress.passes} / ${SchoolProgress.GRADUATE_PASSES}번", 22f, if (progress.graduated) GOLD else TEXT, bold = true)
            line("합격 = ${TripResult.PASS_SCORE}점 이상 + 인정 주행. 단원과 상관없이 합격 ${SchoolProgress.GRADUATE_PASSES}번이면 졸업이에요", 18f, HINT)
        }
    }
}

/** 배우기 > 단원 가이드북 (L2, §3-5): 기술 목록·한 줄 팁·시험 조건 */
internal fun SchoolActivity.renderGuide(page: LinearLayout, u: PathUnit) {
    header(page, "📖 단원 ${u.no} · ${u.title}")
    page.addView(text(u.guide, 22f, TEXT).apply { setPadding(0, dp(8), 0, dp(16)) })
    val path = progress.path()
    u.skills.forEach { s ->
        page.addView(card {
            it.addView(text("${s.icon} ${s.title}   ${starText(path.stars(s))}", 26f, TEXT, bold = true))
            it.addView(text(s.cards[1], 22f, MUTED).padTop(6))
            it.addView(text("미리 배우기 ›", 22f, PRIMARY, bold = true).padTop(8))
            it.setOnClickListener { push(SchoolScreen.Preview(s, 0)) }
        })
    }
    page.addView(card {
        it.addView(text("🏁 단원 시험", 26f, TEXT, bold = true))
        it.addView(text("기술 별 합 ${u.testStars}개 이상 + ${TripResult.PASS_SCORE}점 이상 인정 주행 (지금 별 ${path.unitStars(u)}개)",
            22f, MUTED).padTop(6))
    })
}

/** 배우기 > 미리 배우기 카드 3장 (L3~L4, §3-4). 넘길 때마다 화면을 한 칸 쌓는다 */
internal fun SchoolActivity.renderPreview(page: LinearLayout, s: Skill, index: Int) {
    header(page, "${s.icon} ${s.title}")
    page.addView(text("미리 배우기 ${index + 1} / ${Skill.CARD_COUNT}", 22f, MUTED).apply { setPadding(0, dp(8), 0, dp(12)) })
    page.addView(card {
        it.addView(text("${character()} ${s.cardTitles?.get(index) ?: Skill.CARD_TITLES[index]}", 30f, PRIMARY, bold = true))
        it.addView(text(s.cards[index], 34f, TEXT, bold = true).apply { setPadding(0, dp(20), 0, dp(12)) })
        // 단원 5 카드는 아래에 작은 회색 출처 (부록 G-4-1)
        s.sources?.get(index)?.let { src -> it.addView(text("출처: $src", 18f, HINT).padTop(4)) }
        // "옆 차가 끼어들 때"는 주행으로 판정할 수 없어 카드 3장을 다 읽으면 별 1개 (부록 G-4)
        if (s.cardOnly && index == Skill.CARD_COUNT - 1 && progress.awardSkill(s).stars > 0) {
            it.addView(text("★ 카드를 다 읽어 별 +1", 24f, GOLD, bold = true).padTop(8))
        }
        // 마지막 카드: [이번 주행 연습]은 카드 안 보조 버튼으로. 앞 카드에도 같은 크기의 빈자리를 둬서
        // 아래 [다음]과 [완료]가 화면에서 정확히 같은 자리에 오게 한다
        val last = index == Skill.CARD_COUNT - 1
        it.addView(button(if (progress.target == s) "연습 중 ✓ (누르면 취소)" else "🎯 이번 주행 연습") {
            progress.target = if (progress.target == s) null else s
            render()
        }.apply { if (!last) visibility = View.INVISIBLE }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(20) })
    })
    if (index < Skill.CARD_COUNT - 1) {
        page.addView(button("다음 ›") { push(SchoolScreen.Preview(s, index + 1)) }.onPage())
    } else {
        // 마지막 카드: [다음]이 있던 자리에는 [완료]. [이번 주행 연습]은 위 카드 안에 따로 둬서
        // [다음]을 연달아 누르다가 연습이 걸리지 않게 한다 (기획팀 11/8)
        page.addView(button("완료", primary = true) {
            screens.open(SchoolMenu.LEARN)
            openNode = PathNode.SkillNode(s)
            render()
        })
    }
}
