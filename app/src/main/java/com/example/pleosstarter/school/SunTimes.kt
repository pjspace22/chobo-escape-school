package com.example.pleosstarter.school

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 그날 서울의 일출·일몰 (부록 I-2 L6). 법 제37조 1항 1호 "해가 진 후부터 해가 뜨기 전까지"를 밤으로 본다.
 *
 * NOAA "General Solar Position Calculations" 근사식(분 단위 오차 1~2분)으로 앱 안에서 계산한다. 외부 API 없음.
 * 위치는 서울 시청 근처 37.57N, 126.98E 하나로 고정 (판단: 위치마다 다시 계산하지 않음, 전국 차이는 30분 안쪽).
 */
object SunTimes {
    const val LAT = 37.57
    const val LON = 126.98
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /** (일출, 일몰) 한국 시각, 자정부터 분 */
    fun riseSetMinutes(date: LocalDate): Pair<Double, Double> {
        val gamma = 2 * Math.PI / 365.0 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val lat = Math.toRadians(LAT)
        // 90.833° = 대기 굴절과 해 반지름을 넣은 일출·일몰 천정각
        val ha = Math.toDegrees(acos(cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)))
        val riseUtc = 720 - 4 * (LON + ha) - eqTime
        val setUtc = 720 - 4 * (LON - ha) - eqTime
        return (riseUtc + 540) to (setUtc + 540)
    }

    /** 지금이 밤(일몰 뒤 ~ 일출 전)인지 */
    fun isNight(now: ZonedDateTime = ZonedDateTime.now(KST)): Boolean {
        val t = now.withZoneSameInstant(KST)
        val minutes = t.hour * 60 + t.minute
        val (rise, set) = riseSetMinutes(t.toLocalDate())
        return minutes < rise || minutes >= set
    }

    /** "17:28" 꼴 (근거 화면 표시용) */
    fun hhmm(minutes: Double): String {
        val m = Math.round(minutes).toInt()
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
