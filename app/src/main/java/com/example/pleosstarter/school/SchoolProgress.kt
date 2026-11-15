package com.example.pleosstarter.school

import android.content.Context
import com.example.pleosstarter.school.parking.StopRecord
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 배지 정의. 주행이 끝날 때 [earnedBy]로 이번 주행에서 얻었는지 판단한다. */
enum class Badge(val title: String, val hint: String) {
    FIRST_TRIP("첫 출발", "처음으로 주행을 마치기"),
    SIGNAL_MASTER("깜빡이 마스터", "핸들을 2번 이상 꺾으며 매번 깜빡이 켜기"),
    SAFE_PARKING("안전 주차", "브레이크를 밟고 P로 정차하기"),
    FIRST_KM("첫 1km", "한 번에 1km 이상 주행하기"),
    PERFECT("만점 운전", "합격 기록이 되는 주행에서 100점 받기"),
    CLEAN_SIGNAL("깔끔한 깜빡이", "합격 기록 주행에서 깜빡이를 매번 켜고, 켜 둔 채 잊지 않기"),
    FIRST_RAIN("첫 빗길", "와이퍼를 켜고 주행을 마치기"),
    FIRST_NIGHT("첫 밤길", "전조등을 켜고 주행을 마치기"),
    FOG_FIX("김 서림 해결", "김 서림 코치를 듣고 외기·에어컨·성에 제거로 바꾸기"),
    FIRST_CRUISE("첫 크루즈", "크루즈를 켜고 주행을 마치기"),
    // 부록 I-5 (35): 첫 주행부터 100일째. 통계 문구("초보 사고가 첫 100일에…")는 쓰지 않는다 (공식 통계 미확인)
    DAYS_100("첫 100일", "첫 주행부터 ${SchoolProgress.DAYS_BADGE}일째가 되기"),
    GRADUATE("졸업생", "합격 ${SchoolProgress.GRADUATE_PASSES}번으로 초보 졸업하기");

    fun earnedBy(r: TripResult): Boolean = when (this) {
        FIRST_TRIP -> true
        SIGNAL_MASTER -> r.turnsWithSignal >= 2 && r.turnsWithoutSignal == 0
        SAFE_PARKING -> r.endBrake == true
        FIRST_KM -> r.distanceKm >= 1f
        PERFECT -> r.qualified && r.score == 100
        CLEAN_SIGNAL -> r.qualified && r.turnsWithoutSignal == 0 && r.signalLeftOn == 0
        FIRST_RAIN -> r.rain
        FIRST_NIGHT -> r.night
        FOG_FIX -> r.fogFixed
        FIRST_CRUISE -> Coach.CRUISE in r.coaches
        DAYS_100 -> false // 날짜로 판단하므로 SchoolProgress.finishTrip에서 준다
        GRADUATE -> false // 누적 합격 횟수로 판단하므로 SchoolProgress.finishTrip에서 준다
    }
}

/** 오늘의 목표 하나: 문구와 진행 (부록 B-5) */
data class DailyGoal(val title: String, val value: Int, val max: Int) {
    val done: Boolean get() = value >= max
}

/** 판정 카드에서 준 별 (주행 끝 계산 밖). [unitPassed]는 그 별로 통과한 단원 */
data class SkillAward(val stars: Int, val unitPassed: PathUnit?)

/** 축하 화면 한 장 (부록 B-1: 성적표 다음에 한 장씩) */
data class Celebration(val icon: String, val title: String, val message: String)

/** 주행 하나를 마친 결과: 성적표·축하 화면에 쓴다 */
data class TripOutcome(
    val record: TripRecord,
    val newBadges: List<BadgeItem>,
    val unitsPassed: List<PathUnit>,
    val xp: Int,
    val levelUp: Int?,
    val graduatedNow: Boolean,
    val goalsCompleted: Boolean,
) {
    /** 축하 화면 순서: 단원 통과 → 새 배지 → 레벨 업 → 졸업 → 오늘의 목표 상자 */
    fun celebrations(): List<Celebration> = buildList {
        unitsPassed.forEach { u ->
            // 단원 2를 통과하면 단원 3과 "운전 센스"가 같이 열린다 (부록 I-5)
            val next = PathUnit.values().filter { it.opensAfter == u }
            add(Celebration("🏁", "단원 ${u.no} 통과!",
                (if (next.isNotEmpty()) "새로 열림: " + next.joinToString(", ") { "단원 ${it.no} · ${it.title}" } else "다음 단원은 졸업하면 열려요") +
                    "\n단원 배지 + ${SchoolProgress.XP_UNIT} XP"))
        }
        newBadges.filter { !(it is BadgeItem.Plain && it.badge == Badge.GRADUATE) && it !is BadgeItem.UnitPass }
            .forEach { b -> add(Celebration(b.icon, "새 배지: ${b.title}", b.hint)) }
        levelUp?.let { add(Celebration("⬆", "레벨 업! Lv.$it", "꾸준히 달린 만큼 레벨이 올라요")) }
        if (graduatedNow) add(Celebration("🎓", "초보 졸업!", "합격 ${SchoolProgress.GRADUATE_PASSES}번을 채웠어요. 이제 꼭 필요한 말만 할게요. 졸업장은 마이 메뉴에 있어요"))
        if (goalsCompleted) add(Celebration("🎁", "오늘의 목표 3개 완료!", "상자를 열었어요 + ${SchoolProgress.XP_GOAL} XP"))
    }
}

/**
 * 학교 진도를 기기에 저장한다 (SharedPreferences).
 * 졸업 진도·배지·별·단원·XP·오늘의 목표·연속 주행·프로필·최근 주행 30개.
 */
class SchoolProgress(context: Context) {
    private val prefs = context.getSharedPreferences("school_progress", Context.MODE_PRIVATE)

    val trips: Int get() = prefs.getInt("trips", 0)
    val passes: Int get() = prefs.getInt("passes", 0)
    val bestScore: Int get() = prefs.getInt("best", 0)
    val totalKm: Float get() = prefs.getFloat("km", 0f)
    val graduated: Boolean get() = passes >= GRADUATE_PASSES

    /** 지금까지 깜빡이를 먼저 켜고 꺾은 횟수 (졸업장·깜빡이 장인 배지) */
    val signalUses: Int get() = prefs.getInt("signal_uses", 0)

    /** 음성 코치 켜짐 여부 (기본 켜짐). 글자 코치는 이 값과 상관없이 나온다 */
    var voiceOn: Boolean
        get() = prefs.getBoolean("voice_on", true)
        set(value) = prefs.edit().putBoolean("voice_on", value).apply()

    /** 졸업하면 칭찬·상황 안내는 끄고 꼭 필요한 말만 (부록 G-2 12번). 기본 켬, 설정에서 끔 */
    var quietGraduate: Boolean
        get() = prefs.getBoolean("quiet_graduate", true)
        set(value) = prefs.edit().putBoolean("quiet_graduate", value).apply()

    /** 우회전 "맨 오른쪽 차선으로" 미리 안내 (부록 E-7). 고른 적 없으면 졸업 전 켬, 졸업하면 끔 */
    var laneGuide: Boolean
        get() = if (prefs.contains("lane_guide")) prefs.getBoolean("lane_guide", true) else !graduated
        set(value) = prefs.edit().putBoolean("lane_guide", value).apply()

    /** 시연 모드: 늘 밤, 김 서림 30초, 별 한 번에 3개 */
    var demoMode: Boolean
        get() = prefs.getBoolean("demo_mode", false)
        set(value) = prefs.edit().putBoolean("demo_mode", value).apply()

    /** 졸업한 날짜(예: 2026-11-03). 아직이면 null */
    val graduatedOn: String? get() = prefs.getString("graduated_on", null)

    val badges: Set<Badge>
        get() = (prefs.getStringSet("badges", emptySet()) ?: emptySet())
            .mapNotNull { name -> Badge.values().firstOrNull { it.name == name } }
            .toSet()

    // ── 프로필 (부록 B-9) ──

    var nickname: String
        get() = prefs.getString("nickname", null) ?: DEFAULT_NICKNAME
        set(value) = prefs.edit().putString("nickname", value.take(NICKNAME_MAX)).apply()

    var avatar: String
        get() = prefs.getString("avatar", null) ?: AVATARS.first()
        set(value) = prefs.edit().putString("avatar", value).apply()

    /** 면허 시기: [LICENSE_LABELS]의 번호, 비었으면 -1 */
    var license: Int
        get() = prefs.getInt("license", -1)
        set(value) = prefs.edit().putInt("license", value).apply()

    // ── 기록 (부록 B-8) ──

    /** 최근 주행 (최신이 앞) */
    val records: List<TripRecord> get() = TripRecord.listFromJson(prefs.getString("records", null))

    fun record(id: Long): TripRecord? = records.firstOrNull { it.id == id }

    /** 기록 목록 화면용: 읽다가 깨졌으면 빈 목록 대신 실패로 (부록 H-⑧ "다 불러오기 전에는 없어요 금지") */
    fun loadRecords(): Result<List<TripRecord>> = TripRecord.listLoad(prefs.getString("records", null))

    /** 마지막 주행 결과. 기록 목록이 생기기 전 버전의 마지막 성적표도 읽는다 */
    val lastResult: TripResult?
        get() {
            records.firstOrNull()?.let { return it.result }
            if (!prefs.contains("last_with")) return null
            return TripResult(
                distanceKm = prefs.getFloat("last_km", 0f),
                startBrake = prefs.getInt("last_start", -1).toBool(),
                endBrake = prefs.getInt("last_end", -1).toBool(),
                turnsWithSignal = prefs.getInt("last_with", 0),
                turnsWithoutSignal = prefs.getInt("last_without", 0),
                signalLeftOn = prefs.getInt("last_lefton", 0),
                scoring = 2,
            )
        }

    /**
     * "내 평균" (부록 I-1): 같은 항목의 지난 인정 주행 5번 평균. 값이 있는 주행이 3번 미만이면 null
     * ("기록이 쌓이면 비교해 드려요")
     */
    fun myAverage(before: Long = Long.MAX_VALUE, pick: (TripResult) -> Float?): Float? {
        val values = records.filter { it.id < before && it.result.qualified }.mapNotNull { pick(it.result) }.take(AVG_TRIPS)
        return if (values.size < AVG_MIN) null else values.average().toFloat()
    }

    /** 별 조건용 내 평균 (L9 브레이크, 부드럽게 세우기) */
    fun history(before: Long = Long.MAX_VALUE) = TripHistory(
        brakesPerKm = myAverage(before) { it.brakesPerKm },
        stopDistM = myAverage(before) { it.sense.stopDistM },
    )

    /** 첫 주행 날짜 (운전 ○일째, 부록 I-5 35). 판단: 이 기능 전 사용자는 남은 기록 중 가장 오래된 주행 날짜 */
    val firstTripDate: LocalDate?
        get() = prefs.getString("first_trip_day", null)?.let { LocalDate.parse(it) } ?: records.lastOrNull()?.date

    /** 첫 주행부터 오늘이 며칠째인지 (첫날 = 1일째). 주행 전이면 null */
    val drivingDays: Int?
        get() = firstTripDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() + 1 }

    // ── 학습 경로 (부록 B-2·B-3, 기존 사용자도 별 0·단원 1부터: B-12) ──

    fun stars(s: Skill): Int = prefs.getInt("star_${s.name}", 0)

    val passedUnits: Set<PathUnit>
        get() = (prefs.getStringSet("units_v2", emptySet()) ?: emptySet())
            .mapNotNull { name -> PathUnit.values().firstOrNull { it.name == name } }
            .toSet()

    fun path(): LearningPath = LearningPath(::stars, passes, passedUnits, level)

    /** "이번 주행 연습하기"로 고른 기술. 주행이 끝나면 비운다 (부록 B-4) */
    var target: Skill?
        get() = prefs.getString("target_skill", null)?.let { name -> Skill.values().firstOrNull { it.name == name } }
        set(value) = prefs.edit().putString("target_skill", value?.name).apply()

    // ── 정차 기록·판정 카드 (부록 C) ──

    /** 최근 정차 판정 (최신이 앞) */
    val stops: List<StopRecord> get() = StopRecord.listFromJson(prefs.getString("stops", null))

    fun addStop(s: StopRecord) {
        prefs.edit().putString("stops", StopRecord.listToJson((listOf(s) + stops).take(StopRecord.MAX))).apply()
    }

    fun updateStop(id: Long, change: (StopRecord) -> StopRecord) {
        prefs.edit().putString("stops", StopRecord.listToJson(stops.map { if (it.id == id) change(it) else it })).apply()
    }

    /** "이 지역은 아직 정보가 없어요"는 1번만 말한다 (부록 C-3) */
    var noDataTold: Boolean
        get() = prefs.getBoolean("nodata_told", false)
        set(value) = prefs.edit().putBoolean("nodata_told", value).apply()

    /** 알림(내비 위 코치 배너) 허락은 첫 실행 때 한 번만 묻는다 (부록 D) */
    var notifAsked: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(value) = prefs.edit().putBoolean("notif_asked", value).apply()

    /** 첫 판정 카드 아래 면책 한 줄은 1번만 (부록 C-1) */
    var disclaimerShown: Boolean
        get() = prefs.getBoolean("disclaimer_shown", false)
        set(value) = prefs.edit().putBoolean("disclaimer_shown", value).apply()

    /**
     * 주행 끝 계산 밖에서 별을 준다 (판정 카드 "위험 자리 피하기"). 단원이 열려 있어야 오른다.
     * 단원 시험은 방금(5분 안) 끝난 주행이 80점 이상 인정 주행이면 여기서 바로 판정한다.
     */
    fun awardSkill(s: Skill): SkillAward {
        val before = path()
        if (!before.unitOpen(s.unit) || before.stars(s) >= s.maxStars) return SkillAward(0, null)
        val old = stars(s)
        val new = if (demoMode) s.maxStars else old + 1
        val starLevel = TierBadge.STAR_COLLECTOR.level(tierValue(TierBadge.STAR_COLLECTOR))
        prefs.edit().putInt("star_${s.name}", new).apply()
        var gained = XP_STAR * (new - old)
        val today = today()
        val e = prefs.edit()
        val after = path()
        val last = records.firstOrNull()
        val unitPassed = s.unit.takeIf { u ->
            last != null && System.currentTimeMillis() - last.id <= AWARD_WINDOW_MS &&
                last.result.qualified && last.result.passed &&
                !after.unitPassed(u) && after.unitStars(u) >= u.testStars
        }
        if (unitPassed != null) {
            val key = BadgeItem.UnitPass(unitPassed).key
            e.putStringSet("units_v2", (passedUnits + unitPassed).map { it.name }.toSet())
                .putString("bdate_$key", today).putLong("btrip_$key", last!!.id)
            gained += XP_UNIT + XP_BADGE
        }
        val starLevelNow = TierBadge.STAR_COLLECTOR.level(path().totalStars)
        for (step in starLevel + 1..starLevelNow) {
            e.putString("bdate_${TierBadge.STAR_COLLECTOR.name}_$step", today)
            gained += XP_BADGE
        }
        e.putInt("xp", xp + gained).apply()
        return SkillAward(new - old, unitPassed)
    }

    // ── XP·레벨 (부록 B-6) ──

    val xp: Int get() = prefs.getInt("xp", 0)
    val level: Int get() = xp / XP_PER_LEVEL + 1

    // ── 오늘의 목표·연속 주행 (부록 B-5) ──

    private fun today(): String = LocalDate.now().toString()

    /** 오늘 값. 날짜가 바뀌었으면 0부터 */
    private fun daily(key: String): Int = if (prefs.getString("day", null) == today()) prefs.getInt(key, 0) else 0

    /** 오늘의 목표 3개 */
    fun goals(): List<DailyGoal> {
        val skill = path().currentSkill()
        return listOf(
            DailyGoal(
                if (skill != null) "${skill.title} 별 1개 올리기" else "인정 주행 90점 이상 1번",
                daily("day_goal1"), 1,
            ),
            DailyGoal("인정 주행 1번", daily("day_qualified"), 1),
            DailyGoal("칭찬 코치 2번 받기", daily("day_praise").coerceAtMost(PRAISE_GOAL), PRAISE_GOAL),
        )
    }

    /** 이번 주(월요일 0시 시작) 번호 */
    fun weekOf(date: LocalDate): Long = Math.floorDiv(date.toEpochDay() + 3, 7L)

    fun weekStart(week: Long): LocalDate = LocalDate.ofEpochDay(week * 7 - 3)

    /** 0.3km 이상 달린 주 */
    val activeWeeks: Set<Long>
        get() = (prefs.getStringSet("weeks", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    /** 지금 이어지는 연속 주 수. 이번 주에 아직 안 달렸으면 지난주까지 센다 */
    fun streak(): Int {
        val weeks = activeWeeks
        var w = weekOf(LocalDate.now())
        if (w !in weeks) w--
        var n = 0
        while (w in weeks) {
            n++
            w--
        }
        return n
    }

    val bestStreak: Int get() = maxOf(prefs.getInt("best_streak", 0), streak())

    // ── 배지 ──

    fun tierValue(t: TierBadge): Int = when (t) {
        TierBadge.SIGNAL_PRO -> signalUses
        TierBadge.STEADY -> bestStreak
        TierBadge.DISTANCE -> totalKm.toInt()
        TierBadge.STAR_COLLECTOR -> path().totalStars
    }

    /** 배지 칸 하나가 얻은 단계 수 (단계 없는 배지는 0 또는 1) */
    fun stepsOf(b: BadgeItem): Int = when (b) {
        is BadgeItem.Plain -> if (b.badge in badges) 1 else 0
        is BadgeItem.Tier -> b.tier.level(tierValue(b.tier))
        is BadgeItem.UnitPass -> if (b.unit in passedUnits) 1 else 0
    }

    val earnedSteps: Int get() = BadgeItem.ALL.sumOf { stepsOf(it) }

    /** 배지(단계형은 단계별 [step] 1~3)를 처음 얻은 날짜와 그 주행 */
    fun badgeDate(key: String, step: Int = 0): String? = prefs.getString("bdate_${stepKey(key, step)}", null)

    fun badgeTrip(key: String, step: Int = 0): Long? =
        prefs.getLong("btrip_${stepKey(key, step)}", 0L).takeIf { it != 0L }

    private fun stepKey(key: String, step: Int) = if (step > 0) "${key}_$step" else key

    // ── 주행 마치기 ──

    /**
     * 주행 결과를 저장하고 별·단원·배지·XP·목표를 한 번에 정리한다.
     * 순서: 별 → 단원 시험(바뀐 별로) → 배지 → 기록 → 연속 주행 → 오늘의 목표 → XP
     */
    fun finishTrip(r: TripResult, hits: List<CoachHit>, brakes: Int, target: Skill?): TripOutcome {
        val now = System.currentTimeMillis()
        val todayDate = today()
        val path0 = path()
        val goalSkill = path0.currentSkill()
        val goalsBefore = goals()
        val levelBefore = level
        val tiersBefore = TierBadge.values().associateWith { it.level(tierValue(it)) }
        val badgesBefore = badges
        val wasGraduated = graduated
        val edit = prefs.edit()

        // 별: 기술당 최대 +1, 시연 모드는 한 번에 3개. 내 평균과 비교하는 별은 이번 주행 전 기록으로 (부록 I-1)
        val earned = path0.starsEarned(r, history())
        var starGain = 0
        earned.forEach { s ->
            val old = stars(s)
            val new = if (demoMode) s.maxStars else (old + 1).coerceAtMost(s.maxStars)
            starGain += new - old
            edit.putInt("star_${s.name}", new)
        }
        edit.apply()

        // 단원 시험: 이번 주행이 80점 이상 인정 주행이고, 바뀐 별 합이 기준 이상
        val passedNow = mutableListOf<PathUnit>()
        if (r.qualified && r.passed) {
            PathUnit.values().forEach { u ->
                val p = LearningPath(::stars, passes, passedUnits + passedNow, level)
                if (p.unitOpen(u) && !p.unitPassed(u) && p.unitStars(u) >= u.testStars) passedNow += u
            }
        }

        val newPasses = passes + if (r.qualified && r.passed) 1 else 0
        if (!prefs.contains("first_trip_day")) prefs.edit().putString("first_trip_day", (firstTripDate ?: LocalDate.now()).toString()).apply()
        val earnedBadges = Badge.values().filter { it.earnedBy(r) } +
            (if (newPasses >= GRADUATE_PASSES) listOf(Badge.GRADUATE) else emptyList()) +
            (if ((drivingDays ?: 0) >= DAYS_BADGE) listOf(Badge.DAYS_100) else emptyList())
        val newPlain = earnedBadges.filter { it !in badgesBefore }

        val record = TripRecord(now, r, hits.take(TripRecord.MAX_HITS), earned, target, brakes)
        val week = weekOf(LocalDate.now())
        val e = prefs.edit()
            .putInt("trips", trips + 1)
            .putInt("passes", newPasses)
            .putInt("signal_uses", signalUses + r.turnsWithSignal)
            .putInt("best", maxOf(bestScore, r.score))
            .putFloat("km", totalKm + r.distanceKm)
            .putStringSet("badges", (badgesBefore + earnedBadges).map { it.name }.toSet())
            .putStringSet("units_v2", (passedUnits + passedNow).map { it.name }.toSet())
            .putString("records", TripRecord.listToJson((listOf(record) + records).take(TripRecord.MAX_RECORDS)))
            // 저장된 기록을 못 읽었으면 덮어쓰기 전에 원본을 따로 남긴다 ("저장된 기록은 그대로예요", 부록 H-⑧)
            .also { ed -> if (loadRecords().isFailure) ed.putString("records_backup", prefs.getString("records", null)) }
            .putString("target_skill", null)
        if (newPasses >= GRADUATE_PASSES && graduatedOn == null) e.putString("graduated_on", todayDate)
        if (r.distanceKm >= TripResult.QUALIFY_KM) e.putStringSet("weeks", (activeWeeks + week).map { it.toString() }.toSet())
        e.apply()
        prefs.edit().putInt("best_streak", bestStreak).apply()

        // 오늘의 목표: 날짜가 바뀌었으면 0부터
        val goal1 = if (goalSkill != null) goalSkill in earned else r.qualified && r.score >= 90
        val praise = hits.count { it.coach.praise }
        prefs.edit()
            .putString("day", todayDate)
            .putInt("day_goal1", maxOf(daily("day_goal1"), if (goal1) 1 else 0))
            .putInt("day_qualified", maxOf(daily("day_qualified"), if (r.qualified) 1 else 0))
            .putInt("day_praise", daily("day_praise") + praise)
            .apply()
        val goalsAfter = goals()
        val goalsNew = goalsAfter.indices.count { goalsAfter[it].done && !goalsBefore[it].done }
        val goalsCompleted = goalsAfter.all { it.done } && !goalsBefore.all { it.done }

        // 새 배지: 기존 배지 + 단계형 새 단계 + 단원 배지. 날짜와 그 주행을 남긴다
        val d = prefs.edit()
        val newItems = mutableListOf<BadgeItem>()
        newPlain.forEach { b ->
            newItems += BadgeItem.Plain(b)
            d.putString("bdate_${b.name}", todayDate).putLong("btrip_${b.name}", now)
        }
        var newTierSteps = 0
        TierBadge.values().forEach { t ->
            val after = t.level(tierValue(t))
            val before = tiersBefore.getValue(t)
            if (after > before) {
                newItems += BadgeItem.Tier(t)
                for (step in before + 1..after) {
                    newTierSteps++
                    d.putString("bdate_${t.name}_$step", todayDate).putLong("btrip_${t.name}_$step", now)
                }
            }
        }
        passedNow.forEach { u ->
            val item = BadgeItem.UnitPass(u)
            newItems += item
            d.putString("bdate_${item.key}", todayDate).putLong("btrip_${item.key}", now)
        }
        d.apply()

        // XP (부록 B-6, plan-school-screens.md §4-1)
        val base = if (!r.qualified && r.distanceKm < TripResult.QUALIFY_KM) 0 else (r.score / 10).coerceAtMost(10)
        val gained = base +
            (if (r.qualified && r.passed) XP_PASS else 0) +
            XP_BADGE * (newPlain.size + newTierSteps + passedNow.size) +
            XP_STAR * starGain +
            XP_UNIT * passedNow.size +
            XP_GOAL * goalsNew +
            (if (goalsCompleted) XP_GOAL else 0)
        prefs.edit().putInt("xp", xp + gained).apply()

        return TripOutcome(
            record = record,
            newBadges = newItems,
            unitsPassed = passedNow,
            xp = gained,
            levelUp = level.takeIf { it > levelBefore },
            graduatedNow = graduated && !wasGraduated,
            goalsCompleted = goalsCompleted,
        )
    }

    /** 모든 기록·별·배지·프로필을 지운다. 음성·시연 모드 설정만 남긴다 */
    fun reset() {
        val voice = voiceOn
        val demo = demoMode
        prefs.edit().clear().putBoolean("voice_on", voice).putBoolean("demo_mode", demo).apply()
    }

    private fun Int.toBool() = when (this) { 1 -> true; 0 -> false; else -> null }

    companion object {
        const val GRADUATE_PASSES = 3
        /** 내 평균: 지난 인정 주행 몇 번 / 최소 몇 번 있어야 비교 (부록 I-1) */
        const val AVG_TRIPS = 5
        const val AVG_MIN = 3
        const val DAYS_BADGE = 100
        const val XP_PER_LEVEL = 100
        const val XP_PASS = 10
        const val XP_BADGE = 20
        const val XP_STAR = 5
        const val XP_UNIT = 30
        const val XP_GOAL = 10
        const val PRAISE_GOAL = 2
        /** 판정 카드 별: 이 시간 안에 끝난 주행으로 단원 시험을 본다 / 옮겨 다시 세우기 인정 시간 */
        const val AWARD_WINDOW_MS = 5 * 60_000L
        const val NICKNAME_MAX = 8
        const val DEFAULT_NICKNAME = "운전자"
        val AVATARS = listOf("🐣", "🚗", "🐢", "🦊", "🐻", "🐰")
        val LICENSE_LABELS = listOf("면허 1년 안 됨", "면허 1~3년", "면허 3년 넘음")
    }
}
