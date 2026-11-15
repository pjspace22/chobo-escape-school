package com.example.pleosstarter.school

/**
 * 단계형 배지 4개 (Ⅰ·Ⅱ·Ⅲ, plan-school-duo-screens.md 부록 B-7).
 * 값은 누적 기록에서 읽고, 단계를 처음 넘은 날짜는 [SchoolProgress]가 남긴다.
 */
enum class TierBadge(val icon: String, val title: String, val unit: String, val hint: String, val steps: List<Int>) {
    SIGNAL_PRO("🔦", "깜빡이 장인", "번", "깜빡이를 켜고 꺾은 횟수", listOf(10, 50, 200)),
    STEADY("🔥", "꾸준한 운전자", "주", "연속 주행 최고 기록", listOf(2, 4, 8)),
    DISTANCE("🛣", "거리 쌓기", "km", "누적 주행 거리", listOf(5, 30, 100)),
    STAR_COLLECTOR("⭐", "별 수집가", "개", "학습 경로에서 모은 별", listOf(5, 12, 24)),
    ;

    /** 지금 값으로 몇 단계까지 얻었는지 (0~3) */
    fun level(value: Int): Int = steps.count { value >= it }

    companion object {
        val ROMAN = listOf("Ⅰ", "Ⅱ", "Ⅲ")
    }
}

/** 배지 화면의 칸 하나. 기존 배지·단계형 배지·단원 배지를 한 줄로 다룬다 */
sealed class BadgeItem(val key: String) {
    class Plain(val badge: Badge) : BadgeItem(badge.name)
    class Tier(val tier: TierBadge) : BadgeItem(tier.name)
    class UnitPass(val unit: PathUnit) : BadgeItem("UNIT_${unit.name}")

    val icon: String
        get() = when (this) {
            is Plain -> PLAIN_ICONS[badge] ?: "🏅"
            is Tier -> tier.icon
            is UnitPass -> "🏁"
        }

    val title: String
        get() = when (this) {
            is Plain -> badge.title
            is Tier -> tier.title
            is UnitPass -> "단원 ${unit.no} 통과"
        }

    val hint: String
        get() = when (this) {
            is Plain -> badge.hint
            is Tier -> "${tier.hint} ${tier.steps.joinToString(" · ") { "$it${tier.unit}" }}"
            is UnitPass -> "학습 경로 단원 ${unit.no} · ${unit.title} 시험 통과"
        }

    companion object {
        private val PLAIN_ICONS = mapOf(
            Badge.FIRST_TRIP to "🚗",
            Badge.SIGNAL_MASTER to "↪",
            Badge.SAFE_PARKING to "🅿",
            Badge.FIRST_KM to "📏",
            Badge.PERFECT to "💯",
            Badge.CLEAN_SIGNAL to "✨",
            Badge.FIRST_RAIN to "🌧",
            Badge.FIRST_NIGHT to "🌙",
            Badge.FOG_FIX to "🌫",
            Badge.GRADUATE to "🎓",
            Badge.DAYS_100 to "📅",
        )

        /** 묶음 (부록 B-7). 배지 화면은 이 순서로 그린다 */
        val GROUPS: List<Pair<String, List<BadgeItem>>> = listOf(
            "주행 습관" to listOf(
                Plain(Badge.SIGNAL_MASTER), Plain(Badge.CLEAN_SIGNAL), Plain(Badge.SAFE_PARKING),
                Plain(Badge.PERFECT), Tier(TierBadge.SIGNAL_PRO),
            ),
            "첫 경험" to listOf(
                Plain(Badge.FIRST_TRIP), Plain(Badge.FIRST_KM), Plain(Badge.FIRST_RAIN),
                Plain(Badge.FIRST_NIGHT), Plain(Badge.FOG_FIX), Plain(Badge.FIRST_CRUISE),
            ),
            "학습 경로" to PathUnit.values().map { UnitPass(it) } + Tier(TierBadge.STAR_COLLECTOR),
            "꾸준함" to listOf(Tier(TierBadge.STEADY), Tier(TierBadge.DISTANCE), Plain(Badge.DAYS_100)),
            "졸업" to listOf(Plain(Badge.GRADUATE)),
        )

        val ALL: List<BadgeItem> get() = GROUPS.flatMap { it.second }

        /** 전체 단계 수: 기존 10 + 단계형 4×3 + 단원 3 = 25 */
        val TOTAL_STEPS: Int get() = Badge.values().size + TierBadge.values().size * 3 + PathUnit.values().size

        fun byKey(key: String): BadgeItem? = ALL.firstOrNull { it.key == key }
    }
}
