package com.example.pleosstarter.school

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 주행 중 받은 코치 하나와 그때 주행 시작부터 달린 거리 */
data class CoachHit(val coach: Coach, val km: Float)

/**
 * 기록 목록의 주행 한 건 (plan-school-duo-screens.md 부록 B-8). 최근 30개만 남긴다.
 *
 * @param id 주행이 끝난 시각(ms). 목록·성적표를 찾는 열쇠로도 쓴다
 * @param starsUp 이 주행에서 별이 오른 기술
 * @param target 이 주행에 고른 "이번 주행 연습" 기술
 */
data class TripRecord(
    val id: Long,
    val result: TripResult,
    val hits: List<CoachHit>,
    val starsUp: List<Skill>,
    val target: Skill?,
    val brakes: Int,
) {
    val date: LocalDate get() = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("km", result.distanceKm.toDouble())
        put("start", result.startBrake.code())
        put("end", result.endBrake.code())
        put("with", result.turnsWithSignal)
        put("without", result.turnsWithoutSignal)
        put("leftOn", result.signalLeftOn)
        put("rain", result.rain)
        put("night", result.night)
        put("fog", result.fogFixed)
        put("coaches", JSONArray(result.coaches.map { it.name }))
        result.steadiness?.let { put("h1", it) }
        result.brakesPerKm?.let { put("b1", it.toDouble()) }
        put("brakes", brakes)
        put("parked", result.parkedSafe)
        put("sv", result.scoring)
        put("sense", result.sense.toJson())
        put("hits", JSONArray(hits.map { JSONObject().put("c", it.coach.name).put("km", it.km.toDouble()) }))
        put("stars", JSONArray(starsUp.map { it.name }))
        target?.let { put("target", it.name) }
    }

    companion object {
        const val MAX_RECORDS = 30
        const val MAX_HITS = 20

        fun fromJson(o: JSONObject): TripRecord = TripRecord(
            id = o.getLong("id"),
            result = TripResult(
                distanceKm = o.optDouble("km", 0.0).toFloat(),
                startBrake = o.optInt("start", -1).bool(),
                endBrake = o.optInt("end", -1).bool(),
                turnsWithSignal = o.optInt("with", 0),
                turnsWithoutSignal = o.optInt("without", 0),
                signalLeftOn = o.optInt("leftOn", 0),
                rain = o.optBoolean("rain", false),
                night = o.optBoolean("night", false),
                fogFixed = o.optBoolean("fog", false),
                coaches = o.optJSONArray("coaches").names().mapNotNull { coachOf(it) }.toSet(),
                steadiness = if (o.has("h1")) o.getInt("h1") else null,
                brakesPerKm = if (o.has("b1")) o.getDouble("b1").toFloat() else null,
                parkedSafe = o.optBoolean("parked", false),
                sense = SenseStats.fromJson(o.optJSONObject("sense")),
                // 0.17.0 전 기록은 배점 v2 그대로 (부록 I-6)
                scoring = o.optInt("sv", 2),
            ),
            hits = o.optJSONArray("hits")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val h = arr.getJSONObject(i)
                    coachOf(h.optString("c"))?.let { CoachHit(it, h.optDouble("km", 0.0).toFloat()) }
                }
            } ?: emptyList(),
            starsUp = o.optJSONArray("stars").names().mapNotNull { n -> Skill.values().firstOrNull { it.name == n } },
            target = o.optString("target", "").let { n -> Skill.values().firstOrNull { it.name == n } },
            brakes = o.optInt("brakes", 0),
        )

        fun listToJson(list: List<TripRecord>): String = JSONArray(list.map { it.toJson() }).toString()

        fun listFromJson(s: String?): List<TripRecord> = listLoad(s).getOrDefault(emptyList())

        /** 읽다가 깨졌으면 실패. 목록 화면이 "없어요" 대신 오류를 보이려고 (부록 H-⑧) */
        fun listLoad(s: String?): Result<List<TripRecord>> {
            if (s.isNullOrEmpty()) return Result.success(emptyList())
            return runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }
        }

        private fun coachOf(name: String): Coach? = Coach.values().firstOrNull { it.name == name }
        private fun JSONArray?.names(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
        private fun Boolean?.code() = when (this) { true -> 1; false -> 0; null -> -1 }
        private fun Int.bool() = when (this) { 1 -> true; 0 -> false; else -> null }
    }
}
