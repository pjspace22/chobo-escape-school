package com.example.pleosstarter.school

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 홈·운전 카드·3D 운전 연습 화면 (0.18.0). 기획팀 원본 HTML을 assets/school_web/ 에 그대로 넣고 WebView로 띄운다.
 * 인터넷 없이 열린다 (three.js r128·GLTFLoader는 assets 파일, 아이오닉 5 모델은 sim.html 안에 들어 있음).
 *
 * 차 신호 → 3D: [push]가 SIM.setInput({...})을 부른다. 핸들은 늘, 나머지(브레이크·깜빡이·등·와이퍼·실내등·김 서림)는
 * 실제로 바뀔 때만 넘긴다. 판단: 맵마다 "깜빡이 켜진 채 출발" 같은 시작 상태가 있어서, 열자마자 실제 값으로 덮지 않으려고.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class DriveCardsWeb(private val act: SchoolActivity) {
    val view: WebView = WebView(act)
    /** 지금 3D 연습 화면인지 */
    var onSim = false
        private set
    /** 마지막으로 잰 3D 프레임 속도 (보고·로그용) */
    var lastFps: Float? = null
        private set
    private var steer: Float? = null

    init {
        // 디버그 빌드만: PC 크롬에서 화면 안을 볼 수 있게 (검증용)
        if (act.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        view.setBackgroundColor(BG)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 판단: 원본 틀(폭 1280px)을 화면에 맞춰 키운다 (meta viewport width=1280)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
        }
        view.isVerticalScrollBarEnabled = false
        view.addJavascriptInterface(Bridge(), "Android")
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val msg = m.message()
                if (msg.startsWith("SIM_FPS ")) lastFps = msg.split(" ").getOrNull(1)?.toFloatOrNull()
                Log.d(TAG, "${msg.take(300)} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})")
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            // assets 밖(인터넷)으로는 나가지 않는다
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = !r.url.toString().startsWith(BASE)

            override fun onPageFinished(v: WebView, url: String) {
                Log.d(TAG, "열림 $url")
                onSim = url.contains("sim.html")
                if (onSim) steer?.let { push("steerDeg", it) }
            }
        }
        home()
    }

    fun home() = view.loadUrl(BASE + "cards.html")

    fun sim(id: String? = null) = view.loadUrl(BASE + "sim.html" + (id?.let { "#$it" } ?: ""))

    /** 다른 화면(성적표·설정)에서 홈으로 돌아올 때 상태·기록을 다시 읽어 그린다 */
    fun refresh() {
        if (!onSim) view.evaluateJavascript("window.refreshApp&&refreshApp()", null)
    }

    /** P가 아니게 되면 3D를 멈추려고 홈으로 */
    fun leave() {
        if (onSim) home()
    }

    /** 핸들 각도 (왼쪽 +). ±540°를 넘으면 자른다 */
    fun steering(angle: Float?) {
        val a = angle ?: return
        steer = a.coerceIn(-540f, 540f)
        push("steerDeg", steer)
    }

    fun push(key: String, value: Any?) {
        if (key != "steerDeg") Log.d(TAG, "차 신호 $key=$value (3D ${if (onSim) "켜짐" else "꺼짐"})")
        // 값을 모르면(null) 넘기지 않는다. 깜빡이만 null = 꺼짐
        if (!onSim || (value == null && key != "signal")) return
        val js = JSONObject().put(key, value ?: JSONObject.NULL).toString()
        view.evaluateJavascript("window.SIM&&SIM.setInput($js)", null)
    }

    /** 홈 칸에 넣을 실제 값 */
    private fun appData(): String {
        val p = act.progress
        val o = JSONObject().put("demo", p.demoMode).put("trips", p.trips)
        val recs = p.loadRecords().getOrNull().orEmpty()
        recs.firstOrNull()?.let { r ->
            val res = r.result
            val today = LocalDate.now()
            val whenText = when (r.date) {
                today -> "오늘"
                today.minusDays(1) -> "어제"
                else -> "${r.date.monthValue}/${r.date.dayOfMonth}"
            }
            o.put("last", JSONObject()
                .put("when", whenText)
                // 1km 미만은 m로 (짧은 주행도 바뀐 게 보이게, 2차 검증)
                .put("km", if (res.distanceKm < 1f) "${Math.round(res.distanceKm * 1000)}" else String.format("%.1f", res.distanceKm))
                .put("unit", if (res.distanceKm < 1f) "m" else "km")
                .put("score", res.score)
                .put("sig", res.turnsWithSignal)
                .put("turns", res.turns)
                .put("leftOn", res.signalLeftOn))
        }
        // 내 기록: 회전이 있던 최근 10번의 "꺾기 전 깜빡이" 비율 (오래된 것부터)
        val hist = recs.filter { it.result.turns > 0 }.take(10).reversed().map { it.result.turnsWithSignal * 100 / it.result.turns }
        o.put("hist", JSONArray(hist))
        o.put("newMaster", JSONArray(DriveCards.newMaster(act)))
        return o.toString()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun home() = act.runOnUiThread { this@DriveCardsWeb.home() }

        /** 홈에서 앱 화면 열기: report · record · settings */
        @JavascriptInterface
        fun open(key: String) = act.runOnUiThread { act.openFromWeb(key) }

        @JavascriptInterface
        fun loadState(): String = DriveCards.rawState(act)

        @JavascriptInterface
        fun saveState(json: String) = DriveCards.saveRaw(act, json)

        @JavascriptInterface
        fun appData(): String = runCatching { this@DriveCardsWeb.appData() }.getOrElse {
            Log.w(TAG, "appData 실패", it)
            "{}"
        }

        @JavascriptInterface
        fun ackMaster(id: String) = DriveCards.ackMaster(act, id)
    }

    companion object {
        private const val TAG = "SchoolWeb"
        const val BASE = "file:///android_asset/school_web/"
    }
}
