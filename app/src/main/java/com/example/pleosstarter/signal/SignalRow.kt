package com.example.pleosstarter.signal

import android.os.SystemClock
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import com.example.pleosstarter.R

/**
 * 신호 점검 화면의 한 줄 (이름 / 현재 값 / 갱신 통계).
 *
 * 변경 알림(Callback)이 올 때마다 횟수와 직전 알림과의 간격을 센다.
 * 에뮬레이터에서 신호가 얼마나 자주 오는지(예: 핸들 각도 갱신 주기) 확인하는 용도.
 * UI 스레드에서만 호출한다.
 */
class SignalRow(parent: ViewGroup, @StringRes nameRes: Int) {

    private val value: TextView
    private val stats: TextView

    private var count = 0
    private var lastAt = 0L
    private var lastIntervalMs: Long? = null

    init {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_signal, parent, false)
        view.findViewById<TextView>(R.id.signalName).setText(nameRes)
        value = view.findViewById(R.id.signalValue)
        stats = view.findViewById(R.id.signalStats)
        parent.addView(view)
        showStats()
    }

    /** Get 결과: 값만 바꾸고 갱신 횟수는 세지 않는다 */
    fun showCurrent(text: String) {
        value.text = text
    }

    /** Callback 결과: 값을 바꾸고 갱신 횟수·간격을 센다 */
    fun showUpdate(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (lastAt > 0) lastIntervalMs = now - lastAt
        lastAt = now
        count++
        value.text = text
        showStats()
    }

    fun resetStats() {
        count = 0
        lastAt = 0L
        lastIntervalMs = null
        showStats()
    }

    private fun showStats() {
        stats.text = "갱신 ${count}회 · 간격 ${lastIntervalMs?.let { "${it}ms" } ?: "—"}"
    }
}
