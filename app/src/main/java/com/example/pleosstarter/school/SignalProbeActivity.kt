package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 개발용: 초보 탈출 학교 확장에 쓸 신호(와이퍼, 전조등·상향등·안개등, 기어 R)가
 * 에뮬레이터에서 실제로 오는지 확인한다. 앱 목록에는 안 보이고 adb로 연다:
 * `adb shell am start -n com.example.pleosstarter/.school.SignalProbeActivity`
 *
 * 줄마다 "현재 값"과 마지막 변경 시각을 보여 주고, 아래에 API 로그(오류 메시지 포함)를 쌓는다.
 */
class SignalProbeActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var wiper: Wiper
    private lateinit var light: Light
    private lateinit var gear: Gear

    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val values = linkedMapOf(
        "와이퍼(앞유리)" to "—",
        "전조등(하향)" to "—",
        "상향등" to "—",
        "앞 안개등" to "—",
        "기어" to "—",
    )
    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private lateinit var text: TextView

    private fun lightListener(label: String) = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = set(label, "$state (변경 알림)")
        override fun onFailed(e: Exception) = log("$label 구독 실패", e)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = set("와이퍼(앞유리)", "$state (변경 알림)")
        override fun onFailed(e: Exception) = log("와이퍼 구독 실패", e)
    }

    private val lowBeamListener = lightListener("전조등(하향)")
    private val highBeamListener = lightListener("상향등")
    private val fogListener = lightListener("앞 안개등")

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = set("기어", "${gear?.name} (변경 알림)")
        override fun onFailed(e: Exception) = log("기어 구독 실패", e)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            setPadding(40, 30, 40, 30)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#12161C"))
            addView(text)
        })

        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        wiper = vehicle.getWiper()
        light = vehicle.getLight()
        gear = vehicle.getGear()

        wiper.getCurrentWiperState(windshield,
            onComplete = { set("와이퍼(앞유리)", "$it (조회)") },
            onFailed = { log("getCurrentWiperState 실패", it) })
        light.getCurrentLowBeamLightState(
            onComplete = { set("전조등(하향)", "$it (조회)") },
            onFailed = { log("getCurrentLowBeamLightState 실패", it) })
        light.getCurrentHighBeamLightState(
            onComplete = { set("상향등", "$it (조회)") },
            onFailed = { log("getCurrentHighBeamLightState 실패", it) })
        light.getCurrentFrontFogLightState(
            onComplete = { set("앞 안개등", "$it (조회)") },
            onFailed = { log("getCurrentFrontFogLightState 실패", it) })
        gear.getCurrentGearSelectionState(
            onComplete = { set("기어", "${it?.name} (조회)") },
            onFailed = { log("getCurrentGearSelectionState 실패", it) })

        wiper.registerWiperState(windshield, wiperListener)
        light.registerLowBeamLightState(lowBeamListener)
        light.registerHighBeamLightState(highBeamListener)
        light.registerFrontFogLightState(fogListener)
        gear.registerGearSelection(gearListener)
        render()
    }

    override fun onDestroy() {
        wiper.unregisterWiperState(windshield, wiperListener)
        light.unregisterLowBeamLightState(lowBeamListener)
        light.unregisterHighBeamLightState(highBeamListener)
        light.unregisterFrontFogLightState(fogListener)
        gear.unregisterGearSelection(gearListener)
        vehicle.release()
        super.onDestroy()
    }

    private fun set(label: String, value: String) {
        runOnUiThread {
            values[label] = "$value  [${timeFormat.format(Date())}]"
            logLines.addFirst("${timeFormat.format(Date())} $label = $value")
            while (logLines.size > 30) logLines.removeLast()
            render()
        }
    }

    private fun log(message: String, e: Exception) {
        // VehicleException이면 message에 오류 코드 이름이 들어 있다
        runOnUiThread {
            logLines.addFirst("${timeFormat.format(Date())} $message → ${e.message ?: e}")
            while (logLines.size > 30) logLines.removeLast()
            render()
        }
    }

    private fun render() {
        text.text = "신호 확인 (와이퍼·라이트·기어)\n\n" +
            values.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
            "\n\n── 로그 ──\n" + logLines.joinToString("\n")
    }
}
