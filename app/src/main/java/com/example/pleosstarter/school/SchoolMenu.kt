package com.example.pleosstarter.school

/**
 * 정차(P) 화면 왼쪽 사이드바 메뉴 (plan-school-duo-screens.md §2-0, 박현규 결정 11/2).
 * 위에서부터 5개, 맨 아래에 [SETTINGS]를 따로 떨어뜨려 둔다.
 */
enum class SchoolMenu(val icon: String, val label: String) {
    LEARN("🏠", "배우기"),
    MISSION("🎯", "미션"),
    RECORD("📒", "기록"),
    BADGE("🏅", "배지"),
    MY("👤", "마이"),
    SETTINGS("⚙", "설정"),
}

/** 성적표 항목 상세(L3)에서 고르는 항목 */
enum class TripItem(val title: String) {
    START("출발"),
    SIGNAL("깜빡이 켜기"),
    LEFT_ON("깜빡이 방치"),
    STOP("정차"),
    SENSE("운전 센스"),
    HABIT("주행 습관"),
}

/**
 * 화면 쌓기(스택)의 한 칸. 메뉴의 첫 화면([Top]) 위에 깊은 화면을 쌓고,
 * 뒤로 가기·[←]로 한 칸씩 나온다. Activity는 하나 (§1-3).
 */
sealed class SchoolScreen(val menu: SchoolMenu) {
    /** 메뉴를 눌렀을 때의 첫 화면 (L1) */
    class Top(menu: SchoolMenu) : SchoolScreen(menu)

    /** 배우기 > 단원 가이드북 (L2) */
    class Guide(val unit: PathUnit) : SchoolScreen(SchoolMenu.LEARN)

    /** 배우기 > 미리 배우기 카드 (L3, 넘기면 L4). [page]는 0부터 */
    class Preview(val skill: Skill, val page: Int) : SchoolScreen(SchoolMenu.LEARN)

    /** 성적표 (L2). [id]가 null이면 가장 최근 주행. 미션 > 주 > 주행에서도 연다 */
    class Report(val id: Long?, menu: SchoolMenu = SchoolMenu.RECORD) : SchoolScreen(menu)

    /** 성적표 > 항목 상세 (L3) */
    class ReportItem(val id: Long, val item: TripItem, menu: SchoolMenu = SchoolMenu.RECORD) : SchoolScreen(menu)

    /** 항목 상세 > 그 순간 코치 카드 (L4) */
    class CoachCard(val id: Long, val index: Int, menu: SchoolMenu = SchoolMenu.RECORD) : SchoolScreen(menu)

    /** 기록 > 예시 성적표 (기록이 없을 때, 부록 H-⑦) */
    object SampleReport : SchoolScreen(SchoolMenu.RECORD)

    /** 기록 > 통계 (L2) */
    object Stats : SchoolScreen(SchoolMenu.RECORD)

    /** 주행을 마친 뒤 축하 화면 한 장 (성적표 다음) */
    class Celebrate(val index: Int) : SchoolScreen(SchoolMenu.RECORD)

    /** 미션 > 연속 주행 달력 (L2) */
    object Streak : SchoolScreen(SchoolMenu.MISSION)

    /** 미션 > 달력 > 그 주의 주행 (L3) */
    class WeekTrips(val week: Long) : SchoolScreen(SchoolMenu.MISSION)

    /** 배지 > 상세 (L2) */
    class BadgeDetail(val key: String) : SchoolScreen(SchoolMenu.BADGE)

    /** 마이 > 졸업장 크게 (L2) */
    object Certificate : SchoolScreen(SchoolMenu.MY)

    /** 마이 > 프로필 고치기 (L2) */
    object Profile : SchoolScreen(SchoolMenu.MY)

    /** 설정 > 정보: 면책 고지·데이터 출처 (L2, 부록 C-1) */
    object Info : SchoolScreen(SchoolMenu.SETTINGS)

    /** 설정 > 코치 근거 (부록 I-4). [highlight]는 성적표 코치 카드에서 눌렀을 때 강조할 줄 */
    class Basis(val highlight: String? = null) : SchoolScreen(SchoolMenu.SETTINGS)

    /** 설정 > 버전 기록 (L2) */
    object Versions : SchoolScreen(SchoolMenu.SETTINGS)
}

/** 화면 스택. 비면 안 되므로 늘 맨 아래에 메뉴 첫 화면이 있다 */
class ScreenStack {
    private val stack = ArrayDeque<SchoolScreen>().apply { add(SchoolScreen.Top(SchoolMenu.LEARN)) }

    val current: SchoolScreen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || current.menu != SchoolMenu.LEARN
    val size: Int get() = stack.size

    /** 메뉴를 바꾼다. 깊이는 초기화 */
    fun open(menu: SchoolMenu) {
        stack.clear()
        stack.add(SchoolScreen.Top(menu))
    }

    fun push(s: SchoolScreen) {
        stack.add(s)
    }

    /** 한 칸 나온다. 메뉴 첫 화면이면 배우기로. 더 나갈 곳이 없으면 false */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeLast()
            return true
        }
        if (current.menu != SchoolMenu.LEARN) {
            open(SchoolMenu.LEARN)
            return true
        }
        return false
    }
}
