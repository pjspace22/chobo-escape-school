package com.example.pleosstarter.school

/** 학년: 인정 주행 합격 횟수에 따라 정해진다. 홈 화면 맨 위 한 줄에 쓴다. */
enum class Rank(val title: String) {
    NEWBIE("신입생"),
    FIRST("1학년"),
    SECOND("2학년"),
    GRADUATE("졸업생");

    /** 홈 화면 응원 문구 */
    val message: String
        get() = when (this) {
            NEWBIE -> "첫 합격까지 ${TripResult.PASS_SCORE}점!"
            FIRST -> "졸업까지 합격 2번 남았어요"
            SECOND -> "다음 합격이면 졸업이에요"
            GRADUATE -> "초보 탈출을 축하해요"
        }

    companion object {
        fun of(passes: Int): Rank = when {
            passes >= SchoolProgress.GRADUATE_PASSES -> GRADUATE
            passes == 2 -> SECOND
            passes == 1 -> FIRST
            else -> NEWBIE
        }
    }
}

/**
 * SNS 공유 문구 (글자만). 위치·차량번호·이름 같은 개인정보는 넣지 않는다.
 * 앱 문구에는 우리 해시태그만 쓰고, 공모전 챌린지 해시태그는 홍보 영상에만 쓴다.
 */
object ShareText {
    private const val TAG = "#초보탈출학교"

    /** 이번 성적표 공유. 합격(인정 주행)이면 자랑, 아니면 다음 다짐 */
    fun trip(r: TripResult, passes: Int): String {
        val left = (SchoolProgress.GRADUATE_PASSES - passes).coerceAtLeast(0)
        return when {
            r.qualified && r.passed ->
                "🚗 초보 탈출 학교 ${passes}번째 합격! 오늘 ${r.score}점, " +
                    "깜빡이 ${r.turnsWithSignal}/${r.turns}번 성공. " +
                    (if (left > 0) "졸업까지 ${left}번 남았어요 " else "졸업까지 한 걸음! ") + TAG
            else ->
                "오늘은 ${r.score}점. 다음엔 ${weakest(r)}부터 고쳐 볼게요 💪 $TAG"
        }
    }

    fun badge(b: Badge): String = "🏅 '${b.title}' 배지 획득! ${b.hint} $TAG"

    fun graduation(passes: Int, signalUses: Int, totalKm: Float): String =
        "🎓 초보 운전 졸업했습니다! 합격 ${passes}회, 깜빡이 ${signalUses}번, " +
            "${"%.1f".format(totalKm)}km. 이제 초보 스티커 뗄까요? $TAG"

    /** 가장 점수를 많이 놓친 항목의 이름 */
    private fun weakest(r: TripResult): String = listOf(
        "출발 브레이크" to r.startMax - r.startPoints,
        "깜빡이 켜기" to r.turnMax - r.turnPoints,
        "깜빡이 끄기" to r.leftOnMax - r.leftOnPoints,
        "정차 브레이크" to r.endMax - r.endPoints,
    ).maxByOrNull { it.second }?.first ?: "안전 운전"
}
