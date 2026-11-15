package com.example.pleosstarter.school.parking

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper

/** 한 번 받은 위치 */
data class Fix(val lat: Double, val lng: Double, val source: Source, val atMillis: Long) {
    enum class Source(val label: String) { FUSED("GPS"), NAVI("내비") }
}

/**
 * 현재 위치: FusedLocation 우선, 없으면 NaviHelper (기획서 §6 "두 갈래").
 *
 * 에뮬레이터에서 `adb emu geo fix 경도 위도`로 움직이는 건 FusedLocation뿐이다.
 * FusedLocation은 콘솔에 "준비 중"이라, 값이 없을 때를 대비해 내비 위치로 넘어간다.
 */
class LocationSource(
    context: Context,
    private val navi: NaviClient,
    private val log: (String) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private val fused = FusedLocation(context.applicationContext)
    private var lastFused: Fix? = null

    /** 새 GPS 위치가 올 때마다 (P 상태에서 30m 이상 움직였는지 보려고) */
    var onUpdate: ((Fix) -> Unit)? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            if (location.latitude == 0.0 && location.longitude == 0.0) return
            val fix = Fix(location.latitude, location.longitude, Fix.Source.FUSED, System.currentTimeMillis())
            main.post {
                if (lastFused == null) log("GPS 위치 받음 (FusedLocation)")
                lastFused = fix
                onUpdate?.invoke(fix)
            }
        }

        override fun onError(exception: Exception) = log("FusedLocation 오류 ${exception.message}")
    }

    fun start() {
        runCatching {
            fused.initialize()
            fused.getLastKnownLocation(listener)
            fused.registerFusedLocationCallback(listener)
        }.onFailure { log("FusedLocation 시작 실패 ${it.message}") }
    }

    /** 지금 쓸 위치. GPS가 1분 안에 왔으면 GPS, 아니면 내비 */
    fun current(): Fix? {
        navi.refreshLocation()
        val f = lastFused
        if (f != null && System.currentTimeMillis() - f.atMillis < FRESH_MS) return f
        return navi.lastLocation ?: f
    }

    fun stop() {
        runCatching { fused.unregisterFusedLocationCallback(listener) }
        fused.release()
    }

    private companion object {
        const val FRESH_MS = 60_000L
    }
}
