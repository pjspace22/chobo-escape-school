package com.example.pleosstarter.school.parking

import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 판정 결과 단계. 🔴 6대 구역 / 🟠 단속 카메라·보호구역 시간 밖 / 🟢 해당 없음 */
enum class Level { RED, ORANGE, GREEN }

/** 왜 말하지 않는지 (기획서 §5 침묵 조건) */
enum class Silence {
    NONE,
    IN_PARKING_LOT, // ② 주차장 반경 30m 안
    REPEAT,         // ③ 같은 자리(30m) 10분 안 재정차
    NO_DATA,        // ⑤ 데이터 없는 지역
}

data class NearLot(val lot: ParkingLot, val distanceM: Double)

data class Verdict(
    val level: Level,
    val zone: Zone?,
    val distanceM: Double?,
    val silence: Silence,
    val nearestLot: NearLot?,
    val lat: Double,
    val lng: Double,
)

/** 최근 정차 자리 (침묵 조건 ③용) */
data class RecentStop(val lat: Double, val lng: Double, val atMillis: Long)

/**
 * 위치 → 🔴/🟠/🟢 판정. 화면·SDK와 무관한 순수 계산이라 따로 둔다.
 * 규칙은 기획서 plan-idea-parking-alert.md §5.
 */
class ZoneJudge(
    private val zones: List<Zone> = SampleZones.zones,
    private val lots: List<ParkingLot> = SampleZones.parkingLots,
    private val regions: List<Region> = SampleZones.regions,
) {

    fun judge(lat: Double, lng: Double, now: Calendar, recent: List<RecentStop>): Verdict {
        val lot = nearestLot(lat, lng)
        fun verdict(level: Level, zone: Zone? = null, d: Double? = null, silence: Silence = Silence.NONE) =
            Verdict(level, zone, d, silence, lot, lat, lng)

        if (regions.none { it.contains(lat, lng) }) return verdict(Level.GREEN, silence = Silence.NO_DATA)
        if (lot != null && lot.distanceM <= PARKING_LOT_RADIUS_M) {
            return verdict(Level.GREEN, silence = Silence.IN_PARKING_LOT)
        }

        // 반경 안에 든 구역 중 우선순위가 가장 높은(같으면 가까운) 하나만 말한다
        val hit = zones
            .map { it to distanceM(lat, lng, it.lat, it.lng) }
            .filter { (z, d) -> d <= z.type.radiusM }
            .minWithOrNull(compareBy({ it.first.type.priority }, { it.second }))

        val level = when {
            hit == null -> Level.GREEN
            hit.first.type == ZoneType.CCTV -> Level.ORANGE
            hit.first.type == ZoneType.SCHOOL && !isSchoolHours(now) -> Level.ORANGE
            else -> Level.RED
        }

        val repeated = recent.any {
            now.timeInMillis - it.atMillis <= REPEAT_WINDOW_MS && distanceM(lat, lng, it.lat, it.lng) <= REPEAT_RADIUS_M
        }
        val silence = if (level != Level.GREEN && repeated) Silence.REPEAT else Silence.NONE
        return verdict(level, hit?.first, hit?.second, silence)
    }

    fun nearestLot(lat: Double, lng: Double): NearLot? =
        lots.map { NearLot(it, distanceM(lat, lng, it.lat, it.lng)) }.minByOrNull { it.distanceM }

    companion object {
        const val PARKING_LOT_RADIUS_M = 30.0
        const val REPEAT_RADIUS_M = 30.0
        const val REPEAT_WINDOW_MS = 10 * 60 * 1000L
        /** P 상태에서 이만큼 움직이면 다시 판정 */
        const val REJUDGE_MOVE_M = 30.0

        /** 어린이보호구역 주민신고: 평일 08~20시 (주말 제외, 공휴일은 아직 안 봄) */
        fun isSchoolHours(c: Calendar): Boolean {
            val day = c.get(Calendar.DAY_OF_WEEK)
            val hour = c.get(Calendar.HOUR_OF_DAY)
            return day != Calendar.SATURDAY && day != Calendar.SUNDAY && hour in 8 until 20
        }

        /** 두 점 사이 거리(m), 하버사인 */
        fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
