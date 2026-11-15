package com.example.pleosstarter.school

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.example.pleosstarter.school.parking.Level
import com.example.pleosstarter.school.parking.ParkingPhrases
import com.example.pleosstarter.school.parking.RecentStop
import com.example.pleosstarter.school.parking.Silence
import com.example.pleosstarter.school.parking.StopChoice
import com.example.pleosstarter.school.parking.StopRecord
import com.example.pleosstarter.school.parking.Verdict
import com.example.pleosstarter.school.parking.ZoneJudge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// 주정차 판정: 기어 P → 판정 카드(성적표 위) → 정차 기록, 설정 > 정보
// plan-school-duo-screens.md 부록 C ("잠깐만요"를 학교에 합침, 현규 확정 11/3)

/** 떠 있는 판정 카드 한 장 */
data class ParkingCard(val verdict: Verdict, val stopId: Long, val note: String?, val showDisclaimer: Boolean)

private val STOP_TIME = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)

/** 지금 자리 판정. 위치가 없으면 null (FusedLocation 우선, 없으면 내비 위치) */
internal fun SchoolActivity.judgeNow(): Verdict? {
    val fix = location.current() ?: return null
    val now = Calendar.getInstance()
    val recent = progress.stops
        .filter { now.timeInMillis - it.id <= ZoneJudge.REPEAT_WINDOW_MS }
        .map { RecentStop(it.lat, it.lng, it.id) }
    return judge.judge(fix.lat, fix.lng, now, recent)
}

/** 🟢 판정인지 (데이터 없는 지역은 빼고). "6대 구역 알기" 별 조건 */
internal fun Verdict.safe(): Boolean = level == Level.GREEN && silence != Silence.NO_DATA

/**
 * 기어 P 순간 한 번: 기록을 남기고, 침묵 조건이 아니면 카드를 띄운다 (부록 C-3·C-4).
 * 단원 잠금과 상관없이 모두에게 동작한다 (안전 안내라서). 별은 단원 4가 열린 뒤에만.
 */
internal fun SchoolActivity.afterPark(v: Verdict?) {
    if (v == null) {
        log("위치를 아직 못 받아 주정차 판정을 건너뜀")
        return
    }
    val now = System.currentTimeMillis()
    val prev = progress.stops.firstOrNull()
    var rec = StopRecord.of(v, now)
    // 🔴·🟠 뒤 5분 안에 30m 넘게 옮겨 다시 P → 🟢 이면 "위험 자리 피하기"
    if (v.safe() && prev != null && prev.risky && now - prev.id <= SchoolProgress.AWARD_WINDOW_MS &&
        ZoneJudge.distanceM(prev.lat, prev.lng, v.lat, v.lng) > ZoneJudge.REJUDGE_MOVE_M
    ) rec = rec.copy(movedOk = true)
    progress.addStop(rec)
    val note = if (rec.movedOk) awardNote(Skill.RISK_AVOID) else null
    // 침묵: 주차장 30m 안 · 같은 자리 10분 안 · 데이터 없는 지역은 1번만
    when (v.silence) {
        Silence.IN_PARKING_LOT, Silence.REPEAT -> if (note == null) return
        Silence.NO_DATA -> {
            if (progress.noDataTold) return
            progress.noDataTold = true
        }
        Silence.NONE -> {}
    }
    parkingCard = ParkingCard(v, rec.id, note, !progress.disclaimerShown)
    progress.disclaimerShown = true
    // 🔴·🟠은 TTS 한 번 (코치 10초 간격 규칙은 적용 안 함). 🟢는 소리 없이
    if (v.level != Level.GREEN && v.silence == Silence.NONE) ParkingPhrases.warning(v)?.let { speak(it) }
    if (v.silence == Silence.NO_DATA) speak(ParkingPhrases.NO_DATA)
    handler.removeCallbacks(parkingTimeout)
    handler.postDelayed(parkingTimeout, if (v.level == Level.GREEN) GREEN_CLOSE_MS else CARD_TIMEOUT_MS)
    render()
}

/** 판정 카드에서 별을 줬으면 한 줄 */
private fun SchoolActivity.awardNote(s: Skill): String? {
    val a = progress.awardSkill(s)
    if (a.stars == 0) return null
    return "★ ${s.title} 별 +${a.stars}" + (a.unitPassed?.let { u -> " · 단원 ${u.no} 통과!" } ?: "")
}

/** 카드 닫기. [choice]가 주차장 안내면 내비 경로를 요청한다 */
internal fun SchoolActivity.closeParkingCard(choice: StopChoice) {
    val card = parkingCard ?: return
    handler.removeCallbacks(parkingTimeout)
    parkingCard = null
    val v = card.verdict
    if (v.level != Level.GREEN) {
        progress.updateStop(card.stopId) { it.copy(choice = choice) }
        when (choice) {
            StopChoice.GUIDE -> {
                val lot = v.nearestLot
                if (lot != null && navi.requestRoute(lot.lot.lat, lot.lot.lng, lot.lot.name)) {
                    speak(ParkingPhrases.guide(lot))
                } else {
                    Toast.makeText(this, "지금은 길 안내를 열 수 없어요. 가까운 공영주차장: ${lot?.lot?.name ?: "정보 없음"}", Toast.LENGTH_LONG).show()
                }
                awardNote(Skill.RISK_AVOID)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            }
            else -> Toast.makeText(this, ParkingPhrases.DECLINED, Toast.LENGTH_SHORT).show()
        }
    }
    render()
}

/** 성적표 위에 겹쳐 뜨는 판정 카드 (부록 C-4) */
internal fun SchoolActivity.parkingOverlay(card: ParkingCard): View {
    val v = card.verdict
    val (dot, color) = when {
        v.silence == Silence.NO_DATA -> "⚪" to MUTED
        v.level == Level.RED -> "🔴" to BAD
        v.level == Level.ORANGE -> "🟠" to GOLD
        else -> "🟢" to GOOD
    }
    val box = card {
        it.addView(row().apply {
            addView(text(dot, 64f, TEXT).apply { setPadding(0, 0, dp(24), 0) })
            addView(column().apply {
                addView(text(ParkingPhrases.cardLine(v), 36f, color, bold = true))
                v.zone?.let { z -> addView(text(z.name, 20f, MUTED).padTop(4)) }
                if (v.silence == Silence.IN_PARKING_LOT) addView(text("주차장 안이에요", 20f, MUTED).padTop(4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        card.note?.let { n -> it.addView(text(n, 24f, GOLD, bold = true).padTop(12)) }
        if (v.level != Level.GREEN) {
            val buttons = row().apply { setPadding(0, dp(16), 0, 0) }
            v.nearestLot?.let { lot ->
                buttons.addView(button("주차장 안내 (${ParkingPhrases.meters(lot.distanceM)})", primary = true) {
                    closeParkingCard(StopChoice.GUIDE)
                }, weighted())
            }
            buttons.addView(button("괜찮아요") { closeParkingCard(StopChoice.OK) }, weighted())
            it.addView(buttons)
        } else {
            it.addView(button("닫기") { closeParkingCard(StopChoice.NONE) })
        }
        if (card.showDisclaimer) it.addView(text(ParkingPhrases.DISCLAIMER, 18f, MUTED).padTop(12))
    }
    return FrameLayout(this).apply {
        setBackgroundColor(SCRIM)
        isClickable = true // 뒤의 성적표가 눌리지 않게
        addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            .apply { setMargins(dp(160), 0, dp(160), 0) })
    }
}

/** 기록 > [정차]: 판정 결과 목록 (지도 없음, 1차) */
internal fun SchoolActivity.stopList(parent: LinearLayout) {
    val stops = progress.stops
    if (stops.isEmpty()) {
        // 빈 화면 (부록 H-⑦). 판단: 카드 안이라 아이콘+이유+방법+버튼을 카드 없이 바로 넣는다
        parent.gravity = Gravity.CENTER_HORIZONTAL
        parent.addView(iconBox("🅿", 96, RAISED))
        parent.addView(text("아직 정차 기록이 없어요", 28f, TEXT, bold = true, center = true).padTop(20))
        parent.addView(text("기어를 P에 두면 그 자리에 세워도 되는지 봐 드려요", 24f, MUTED, center = true).padTop(8))
        parent.addView(button("주정차 규칙 배우기 ›", primary = true) { push(SchoolScreen.Guide(PathUnit.PARKING)) }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        })
        return
    }
    parent.addView(text("최근 정차 ${stops.size}개 (최대 ${StopRecord.MAX}개)", 20f, MUTED))
    stops.forEach { s ->
        val (dot, color) = when {
            s.silence == Silence.NO_DATA -> "⚪" to MUTED
            s.level == Level.RED -> "🔴" to BAD
            s.level == Level.ORANGE -> "🟠" to GOLD
            else -> "🟢" to GOOD
        }
        val what = when {
            s.silence == Silence.NO_DATA -> "정보 없는 지역"
            s.silence == Silence.IN_PARKING_LOT -> "주차장 안"
            s.zoneType != null -> s.zoneType.label + (s.distanceM?.let { d -> " ${d.roundToInt()}m" } ?: "")
            else -> "단속 구역 아님"
        }
        val after = when {
            s.movedOk -> "옮겨서 🟢"
            s.risky -> s.choice.label
            else -> ""
        }
        parent.addView(row().apply {
            setPadding(0, dp(12), 0, dp(12))
            addView(text(Instant.ofEpochMilli(s.id).atZone(ZoneId.systemDefault()).format(STOP_TIME), 22f, TEXT),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
            addView(text("$dot $what", 22f, color, bold = s.risky), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
            addView(text(after, 20f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f))
        })
    }
}

/** 설정 > 정보: 면책 고지·데이터 출처 (부록 C-1) */
internal fun SchoolActivity.renderInfo(page: LinearLayout) {
    header(page, "정보")
    page.addView(card {
        it.addView(text("주정차 알림", 26f, TEXT, bold = true))
        it.addView(text(ParkingPhrases.DISCLAIMER, 24f, BAD, bold = true).padTop(8))
        it.addView(text("알림은 참고용이에요. 표지판과 현장 안내를 먼저 따르세요.", 20f, MUTED).padTop(6))
    })
    page.addView(card {
        it.addView(text("데이터 출처", 26f, TEXT, bold = true))
        it.addView(text(ParkingPhrases.DATA_SOURCE, 20f, MUTED).padTop(8))
        it.addView(text("미리 배우기 숫자(소화전 5m·버스정류장 10m·교차로 5m)는 정책브리핑 2023-08-10 기준이에요.", 20f, MUTED).padTop(6))
    })
    // 0.18.0 3D 운전 연습 → 0.19.0 연습 맵 사진: 사용 조건 문구 (사진 속 차 모델 CC BY 4.0, three.js MIT)
    page.addView(card {
        it.addView(text("연습 맵 사진", 26f, TEXT, bold = true))
        it.addView(text("사진 속 3D 모델: \"Hyundai Ioniq 5 - Lowpoly\" © andikapratamaw, CC BY 4.0 (sketchfab.com)", 20f, MUTED).padTop(8))
        it.addView(text("3D 도구: three.js r128 © three.js authors, MIT 라이선스", 20f, MUTED).padTop(6))
        it.addView(text("현대 로고·엠블럼은 넣지 않았어요.", 20f, MUTED).padTop(6))
    })
    page.addView(card {
        it.addView(text("앱 버전 ${appVersion()}", 22f, MUTED))
    })
}

private const val GREEN_CLOSE_MS = 3_000L
private const val CARD_TIMEOUT_MS = 10_000L
