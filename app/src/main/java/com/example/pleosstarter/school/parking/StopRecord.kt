package com.example.pleosstarter.school.parking

import org.json.JSONArray
import org.json.JSONObject

/** 판정 카드에서 고른 것 */
enum class StopChoice(val label: String) {
    GUIDE("주차장 안내"),
    OK("괜찮아요"),
    NONE("응답 없음"),
}

/**
 * 정차 기록 한 건 = 기어 P 판정 결과 (기록 > [정차], 부록 C-1). 최근 30개만 남긴다.
 *
 * @param movedOk 🔴·🟠 뒤 5분 안에 30m 넘게 옮겨 다시 P → 🟢였는지 (위험 자리 피하기 별)
 */
data class StopRecord(
    val id: Long,
    val level: Level,
    val silence: Silence,
    val zoneType: ZoneType?,
    val zoneName: String?,
    val distanceM: Double?,
    val lat: Double,
    val lng: Double,
    val choice: StopChoice = StopChoice.NONE,
    val movedOk: Boolean = false,
) {
    val risky: Boolean get() = level != Level.GREEN

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("level", level.name)
        put("silence", silence.name)
        zoneType?.let { put("zone", it.name) }
        zoneName?.let { put("name", it) }
        distanceM?.let { put("d", it) }
        put("lat", lat)
        put("lng", lng)
        put("choice", choice.name)
        put("moved", movedOk)
    }

    companion object {
        const val MAX = 30

        fun of(v: Verdict, now: Long) = StopRecord(
            id = now, level = v.level, silence = v.silence, zoneType = v.zone?.type, zoneName = v.zone?.name,
            distanceM = v.distanceM, lat = v.lat, lng = v.lng,
        )

        fun fromJson(o: JSONObject) = StopRecord(
            id = o.getLong("id"),
            level = Level.valueOf(o.optString("level", Level.GREEN.name)),
            silence = runCatching { Silence.valueOf(o.optString("silence")) }.getOrDefault(Silence.NONE),
            zoneType = o.optString("zone", "").let { n -> ZoneType.values().firstOrNull { it.name == n } },
            zoneName = if (o.has("name")) o.getString("name") else null,
            distanceM = if (o.has("d")) o.getDouble("d") else null,
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            choice = runCatching { StopChoice.valueOf(o.optString("choice")) }.getOrDefault(StopChoice.NONE),
            movedOk = o.optBoolean("moved", false),
        )

        fun listToJson(list: List<StopRecord>): String = JSONArray(list.map { it.toJson() }).toString()

        fun listFromJson(s: String?): List<StopRecord> {
            if (s.isNullOrEmpty()) return emptyList()
            return runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}
