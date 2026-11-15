package com.example.pleosstarter.school

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 운전 카드 23장 (0.18.0, plan-school-drive-cards-3d-handoff.md, 현규 확정 11/10).
 * 카드 글·그림은 assets/school_web/cards.html 에 있고, 여기는 앱이 판정에 쓰는 것만 둔다.
 *
 * - [coaches]: 이 카드의 주행 중 안내. 카드 "안내 끔"이면 화면·음성에는 안 내고 기록만 한다
 * - [judge]: 이번 주행에 이 상황이 있었는지(null) / 실수 없이 했는지(true·false)
 */
class DriveCard(
    val id: String,
    val tier: Int,
    /** 판정하는 카드인지 (등급 "카드만"·"안내만"은 직접 표시만) */
    val judged: Boolean,
    val coaches: Set<Coach>,
    val judge: (TripResult) -> Boolean? = { null },
)

object DriveCards {
    /** 실수 없는 주행 연속 횟수 (프로토타입 NEED) */
    const val NEED = 3
    private const val PREFS = "drive_cards"
    private const val KEY_STATE = "state"
    private const val KEY_NEW = "new_master"

    private fun has(r: TripResult, vararg c: Coach) = c.any { it in r.coaches }

    val ALL: List<DriveCard> = listOf(
        // 1단계 · 안전 기본
        DriveCard("signal_on", 1, true, setOf(Coach.NO_SIGNAL_TURN, Coach.SIGNAL_TURN_OK, Coach.TURN_LEFT_SOON, Coach.TURN_LEFT_SOON_OK, Coach.RIGHT_SIGNAL, Coach.RIGHT_SIGNAL_OK)) { r ->
            if (r.turns == 0) null else r.turnsWithoutSignal == 0
        },
        DriveCard("signal_off", 1, true, setOf(Coach.SIGNAL_LEFT_ON)) { r -> if (r.turns == 0) null else r.signalLeftOn == 0 },
        DriveCard("park_p", 1, true, emptySet()) { r -> r.endBrake },
        DriveCard("headlight", 1, true, setOf(Coach.NIGHT, Coach.NIGHT_NO_HEADLIGHT)) { r -> if (!r.night) null else !has(r, Coach.NIGHT_NO_HEADLIGHT) },
        DriveCard("rain_light", 1, true, setOf(Coach.RAIN_NO_HEADLIGHT)) { r -> if (!r.rain) null else !has(r, Coach.RAIN_NO_HEADLIGHT) },
        // 판단: 출구 관련 안내가 한 번이라도 나온 주행만 "고속도로 출구 상황"으로 본다
        DriveCard("hw_stop", 1, true, setOf(Coach.EXIT_MISSED_STOP, Coach.EXIT_MISSED_REVERSE)) { r ->
            if (!has(r, Coach.EXIT_SOON_R, Coach.EXIT_SOON_L, Coach.EXIT_SOON_C, Coach.EXIT_MISSED_STOP, Coach.EXIT_MISSED_REVERSE)) null
            else !has(r, Coach.EXIT_MISSED_STOP, Coach.EXIT_MISSED_REVERSE)
        },
        // 2단계 · 교차로·날씨
        DriveCard("right_red", 2, false, setOf(Coach.RIGHT_RED)),
        DriveCard("left_wait", 2, true, setOf(Coach.LEFT_WAIT, Coach.LEFT_WAIT_OK)) { r ->
            if (!has(r, Coach.LEFT_WAIT, Coach.LEFT_WAIT_OK, Coach.LEFT_WAIT_MISSED)) null else !has(r, Coach.LEFT_WAIT, Coach.LEFT_WAIT_MISSED)
        },
        DriveCard("rain", 2, true, setOf(Coach.RAIN)) { r ->
            val wet = r.sense.rainKmh
            val dry = r.sense.clearKmh
            if (!r.rain || wet == null || dry == null || dry <= 0f) null else wet <= dry * 0.8f
        },
        // 판단: 차선은 못 보므로 우회전 전에 깜빡이 경고가 없었으면 실수 없음
        DriveCard("right_lane", 2, true, setOf(Coach.RIGHT_LANE)) { r -> if (!has(r, Coach.RIGHT_LANE)) null else !has(r, Coach.RIGHT_SIGNAL) },
        DriveCard("high_beam", 2, true, setOf(Coach.HIGH_BEAM)) { r -> if (!r.night) null else !has(r, Coach.HIGH_BEAM) },
        DriveCard("interior", 2, true, setOf(Coach.CABIN_LIGHT)) { r -> !has(r, Coach.CABIN_LIGHT) },
        DriveCard("fog", 2, true, setOf(Coach.FOG, Coach.FOG_OK)) { r -> !has(r, Coach.FOG) || r.fogFixed },
        // 3단계 · 고속도로
        DriveCard("exit_lane", 3, true, setOf(Coach.EXIT_SOON_R, Coach.EXIT_SOON_L, Coach.EXIT_SOON_C, Coach.EXIT_SIGNAL_R, Coach.EXIT_SIGNAL_L, Coach.EXIT_SIGNAL_OK)) { r ->
            if (!has(r, Coach.EXIT_SOON_R, Coach.EXIT_SOON_L, Coach.EXIT_SOON_C)) null else !has(r, Coach.EXIT_SIGNAL_R, Coach.EXIT_SIGNAL_L)
        },
        DriveCard("merge", 3, true, setOf(Coach.MERGE)) { r -> if (r.sense.merges == 0 && !has(r, Coach.MERGE)) null else r.sense.mergeBrakes == 0 },
        // 판단: 크루즈는 앞을 봤는지 못 보므로, 크루즈를 쓴 주행에서 위험 경고가 없었으면 실수 없음
        DriveCard("cruise", 3, true, setOf(Coach.CRUISE)) { r -> if (!has(r, Coach.CRUISE)) null else true },
        // 4단계 · 운전 센스
        DriveCard("one_lane", 4, true, emptySet()) { r -> if (r.sense.laneChanges == 0) null else r.sense.doubleLaneChanges == 0 },
        DriveCard("early_slow", 4, true, emptySet()) { r -> if (r.sense.naviTurns == 0) null else r.sense.earlyTurns >= r.sense.naviTurns },
        DriveCard("wobble", 4, true, setOf(Coach.WOBBLE)) { r -> if (r.steadiness == null) null else !has(r, Coach.WOBBLE) },
        DriveCard("seat_heat", 4, true, setOf(Coach.SEAT_HEAT)) { r -> !has(r, Coach.SEAT_HEAT) },
        DriveCard("manners", 4, false, emptySet()),
        // 판단: 부드럽게 세우기 = 브레이크부터 멈춤까지 15m 이상 (3D 맵 노란 선 15m와 같게)
        DriveCard("soft_stop", 4, true, emptySet()) { r -> r.sense.stopDistM?.let { it >= 15f } },
        DriveCard("bump", 4, false, emptySet()),
    )

    private val byCoach: Map<Coach, DriveCard> = ALL.flatMap { c -> c.coaches.map { it to c } }.toMap()

    class CardState(var streak: Int, var m: String?, var coach: Boolean)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** cards.html 이 그대로 읽는 JSON ({id:{streak,m,coach}}) */
    fun rawState(ctx: Context): String = prefs(ctx).getString(KEY_STATE, "{}") ?: "{}"

    fun saveRaw(ctx: Context, json: String) {
        // 깨진 값은 저장하지 않는다
        if (runCatching { JSONObject(json) }.isSuccess) prefs(ctx).edit().putString(KEY_STATE, json).apply()
    }

    fun load(ctx: Context): MutableMap<String, CardState> {
        val o = runCatching { JSONObject(rawState(ctx)) }.getOrElse { JSONObject() }
        return ALL.associate { c ->
            val s = o.optJSONObject(c.id)
            c.id to if (s == null) CardState(0, null, c.tier == 1)
            else CardState(s.optInt("streak", 0), s.optString("m").takeIf { s.has("m") && !s.isNull("m") && it.isNotEmpty() }, s.optBoolean("coach", c.tier == 1))
        }.toMutableMap()
    }

    private fun save(ctx: Context, st: Map<String, CardState>) {
        val o = runCatching { JSONObject(rawState(ctx)) }.getOrElse { JSONObject() }
        st.forEach { (id, s) ->
            o.put(id, JSONObject().put("streak", s.streak).put("m", s.m ?: JSONObject.NULL).put("coach", s.coach))
        }
        prefs(ctx).edit().putString(KEY_STATE, o.toString()).apply()
    }

    private fun tierDone(st: Map<String, CardState>, n: Int) = ALL.filter { it.tier == n }.all { st[it.id]?.m != null }
    private fun unlocked(st: Map<String, CardState>, n: Int) = n == 1 || tierDone(st, n - 1)

    /**
     * 주행 중 이 코치를 화면·음성으로 낼지. 카드 안내가 꺼져 있으면 false (기록은 남긴다).
     * 판단: 위험(우선순위 1, 고속도로 멈춤·후진)은 카드와 상관없이 늘 낸다
     */
    fun coachOn(ctx: Context, c: Coach): Boolean {
        if (c.priority == 1) return true
        val card = byCoach[c] ?: return true
        val st = load(ctx)
        val s = st[card.id] ?: return true
        return s.coach && unlocked(st, card.tier)
    }

    /**
     * 주행을 마치면 카드마다 실수 없는 주행을 센다 (프로토타입 drive()와 같은 규칙).
     * 상황이 없었던 카드는 그대로, 실수가 있으면 0부터, 3번 연속이면 마스터 + 안내 끔.
     * 판단: 시연 모드 주행도 센다 (발표에서 보여 주려고)
     */
    fun onTripFinished(ctx: Context, r: TripResult) {
        val st = load(ctx)
        val fresh = mutableListOf<String>()
        ALL.filter { it.judged }.forEach { c ->
            val s = st[c.id] ?: return@forEach
            if (s.m != null) return@forEach
            when (c.judge(r)) {
                null -> Unit
                false -> s.streak = 0
                true -> {
                    s.streak++
                    if (s.streak >= NEED) {
                        val was = tierDone(st, c.tier)
                        s.m = "auto"
                        s.coach = false
                        fresh += c.id
                        // 한 단계를 다 마스터하면 다음 단계 안내를 켠다 (프로토타입 checkTier)
                        if (!was && tierDone(st, c.tier)) ALL.filter { it.tier == c.tier + 1 }.forEach { n -> st[n.id]?.let { it.coach = it.m == null } }
                    }
                }
            }
        }
        save(ctx, st)
        if (fresh.isNotEmpty()) {
            val list = newMaster(ctx) + fresh
            prefs(ctx).edit().putString(KEY_NEW, JSONArray(list).toString()).apply()
        }
    }

    /** 주행으로 새로 마스터해서 아직 홈에서 축하하지 않은 카드 */
    fun newMaster(ctx: Context): List<String> {
        val a = runCatching { JSONArray(prefs(ctx).getString(KEY_NEW, "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }
    }

    fun ackMaster(ctx: Context, id: String) {
        val list = newMaster(ctx).filter { it != id }
        prefs(ctx).edit().putString(KEY_NEW, JSONArray(list).toString()).apply()
    }
}
