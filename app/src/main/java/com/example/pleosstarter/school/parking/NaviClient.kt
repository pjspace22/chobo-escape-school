package com.example.pleosstarter.school.parking

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.constants.TurnType
import com.example.pleosstarter.school.TurnAhead
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * NaviHelper: 현재 위치(대체용)와 공영주차장 길 안내.
 * 에뮬레이터에서 내비 위치는 37.4127, 127.0947(성남 수정구 시흥동)로 고정이다.
 */
class NaviClient(context: Context, private val log: (String) -> Unit) {

    /** 회전 안내 목록이 올 때 (UI 스레드). 경로가 끝나면 빈 목록 */
    var onTurns: (List<TurnAhead>) -> Unit = {}

    /** 내비가 경로를 다시 짰을 때 (UI 스레드). X4 진출로 놓침 판단 */
    var onReRoute: () -> Unit = {}

    /** 차 앞 [CONGESTION_NEAR_M] 안에 정체 구간이 있는지 (경로 상태를 받을 때마다 갱신) */
    var congestedAhead = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    private var connected = false

    /** 마지막으로 받은 내비 위치 */
    var lastLocation: Fix? = null
        private set

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            main.post { lastLocation = Fix(info.latitude, info.longitude, Fix.Source.NAVI, System.currentTimeMillis()) }
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            log("내비 경로 시작")
            runCatching { navi.getTBTInfo() }
        }

        override fun onTBTInfo(list: List<TBTInfo>) {
            val turns = list.map { TurnAhead(dir(it.type), it.distance, it.description) }
            main.post { onTurns(turns) }
        }

        override fun onReRouteEnded() = main.post {
            onReRoute()
            refreshTurns()
        }.let { }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            // 판단: 정체 구간 시작(offset)이 차 앞 500m 안이면 "정체 중"으로 본다
            val near = info.trafficStatus?.congestionItemList.orEmpty().any { it.offset <= CONGESTION_NEAR_M }
            main.post { congestedAhead = near }
        }

        override fun onRouteEnded() = main.post { onTurns(emptyList()) }.let { }
        override fun onRouteCancelled() = main.post { onTurns(emptyList()) }.let { }
        override fun onError(code: NaviErrorCode) = log("내비 오류 $code")
    }

    fun initialize() {
        navi.addListener(listener)
        navi.initialize(
            onConnected = {
                main.post { connected = true }
                log("내비 연결됨")
                navi.getCurrentLocationInfo()
                runCatching { navi.getTBTInfo() } // 앱을 켜기 전부터 경로 안내 중일 수 있다
            },
            onFailed = { log("내비 연결 실패 ${it.message}") },
        )
    }

    /** 회전 안내를 다시 받는다 (결과는 [onTurns]) */
    fun refreshTurns() {
        if (connected) runCatching {
            navi.getTBTInfo()
            navi.getRouteStateInfo()
        }
    }

    fun refreshLocation() {
        if (connected) navi.getCurrentLocationInfo()
    }

    fun requestRoute(lat: Double, lng: Double, name: String): Boolean {
        if (!connected) {
            log("내비 연결 전이라 길 안내 못 함")
            return false
        }
        // 길 안내는 NAVI_ROUTE_SEARCH 퍼미션이 따로 필요하다 (11/3 에뮬레이터 실측). 없으면 앱이 죽지 않게 false
        return runCatching {
            navi.requestRoute(RouteInfo(lat, lng, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
            log("내비 길 안내 요청: $name")
            true
        }.getOrElse {
            log("내비 길 안내 실패 ${it.message}")
            false
        }
    }

    /**
     * 내비 회전 종류 → 코치가 보는 방향 (SDK TurnType 43종 중에서 고름).
     * - T7 좌회전·유턴, R1 우회전 계열 (plan-school-coach.md §2-1-1, 부록 E-7)
     * - 판단(부록 G-3): X1 출구·분기 = IC 나가기(EXIT_IC)·차로 유지 분기(KEEP)·휴게소·졸음쉼터 진입
     *   (휴게소·졸음쉼터는 오른쪽으로 나가므로 오른쪽), X3 합류 = IC 들어가기(ENTER_IC)·분기 합류(DEVIDE_MERGE)
     * - 고가·지하차도 진입/회피(ENTER/AVOID_OVER·UNDER)는 같은 길을 이어 가는 안내라 넣지 않음
     */
    private fun dir(t: TurnType?): TurnAhead.Dir = when (t) {
        TurnType.TURN_LEFT, TurnType.TURN_UTURN -> TurnAhead.Dir.LEFT
        TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnAhead.Dir.RIGHT
        TurnType.TURN_EXIT_IC_R, TurnType.TURN_KEEP_R, TurnType.TURN_ENTER_SERVICEAREA, TurnType.TURN_RESTAREA -> TurnAhead.Dir.EXIT_R
        TurnType.TURN_EXIT_IC_L, TurnType.TURN_KEEP_L -> TurnAhead.Dir.EXIT_L
        TurnType.TURN_EXIT_IC_C -> TurnAhead.Dir.EXIT_C
        TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_C, TurnType.TURN_DEVIDE_MERGE -> TurnAhead.Dir.MERGE
        else -> TurnAhead.Dir.OTHER
    }

    companion object {
        const val CONGESTION_NEAR_M = 500.0
    }

    fun release() {
        navi.removeListener(listener)
        navi.release()
    }
}
