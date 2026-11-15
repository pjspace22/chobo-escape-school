package com.example.pleosstarter

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vehicle SDK 스타터.
 *
 * SDK 사용 흐름 (네 가지 API 타입):
 *   Vehicle(context) → initialize() → getXxx()로 도메인 객체 획득
 *   → Capability(지원 여부·범위) → Get(현재 값) → Callback(변경 구독) → Set(제어)
 *   → release()
 *
 * 모든 결과는 IPC라 비동기 콜백으로 온다. 콜백 스레드가 보장되지 않으므로 UI 갱신은 runOnUiThread로 한다.
 * 에뮬레이터 값은 Pleos Connect Virtual Console(adb forward tcp:8088 tcp:8088 → http://127.0.0.1:8088)에서 바꿀 수 있다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var door: Door
    private lateinit var brake: Brake
    private lateinit var odometer: Odometer

    // 운전석 문. 다른 문을 보려면 DoorValue만 바꾸면 된다 (ROW_1_RIGHT, REAR 등)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private var doorBounds: AreaBounds<Int>? = null
    private var currentDoorPosition: Int? = null

    private lateinit var doorValue: TextView
    private lateinit var brakeValue: TextView
    private lateinit var odometerValue: TextView
    private lateinit var logText: TextView
    private lateinit var doorToggleButton: Button

    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    // ── Callback: 값이 바뀔 때마다 호출된다. 등록한 것과 같은 객체로 해제해야 한다.
    private val doorPositionListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            runOnUiThread { showDoorPosition(pos) }
        }

        override fun onFailed(e: Exception) {
            log("registerDoorPosition 실패", e)
        }
    }

    private val brakePedalListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            runOnUiThread { showBrake(status) }
        }

        override fun onFailed(e: Exception) {
            log("registerBrakePedalStatus 실패", e)
        }
    }

    private val distanceDrivenListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            runOnUiThread { showOdometer(value) }
        }

        override fun onFailed(e: Exception) {
            log("registerDistanceDriven 실패", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        doorValue = findViewById(R.id.doorValue)
        brakeValue = findViewById(R.id.brakeValue)
        odometerValue = findViewById(R.id.odometerValue)
        logText = findViewById(R.id.logText)
        doorToggleButton = findViewById(R.id.doorToggleButton)

        doorToggleButton.setOnClickListener { toggleDriverDoor() }
        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            checkCapabilities()
        }

        // 1) SDK 연결
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        log("Vehicle.initialize() 호출")

        // 2) 도메인 객체 획득
        door = vehicle.getDoor()
        brake = vehicle.getBrake()
        odometer = vehicle.getOdometer()

        // 3) 지원 여부 확인 → 읽을 수 있으면 현재 값 조회
        checkCapabilities()
    }

    override fun onStart() {
        super.onStart()
        // 4) 화면이 보이는 동안만 변경 알림 구독
        door.registerDoorPosition(driverDoor, doorPositionListener)
        brake.registerBrakePedalStatus(brakePedalListener)
        odometer.registerDistanceDriven(distanceDrivenListener)
        log("변경 알림 구독 시작")
    }

    override fun onStop() {
        door.unregisterDoorPosition(driverDoor, doorPositionListener)
        brake.unregisterBrakePedalStatus(brakePedalListener)
        odometer.unregisterDistanceDriven(distanceDrivenListener)
        log("변경 알림 구독 해제")
        super.onStop()
    }

    override fun onDestroy() {
        // 6) SDK 리소스 해제
        vehicle.release()
        super.onDestroy()
    }

    // ── Capability + Get ────────────────────────────────────────────────

    private fun checkCapabilities() {
        door.checkDoorPositionCapabilities(
            onComplete = { config: CapabilityConfig, areaMap: Map<DoorArea, AreaBounds<Int>> ->
                runOnUiThread {
                    doorBounds = areaMap[driverDoor]
                    log("Door 지원: ${config.accessType}, 범위 ${doorBounds?.let { "${it.minValue}..${it.maxValue}" } ?: "없음"}")
                    // 쓰기 권한이 있고 범위를 알 때만 제어 버튼 활성화
                    doorToggleButton.isEnabled = config.accessType.canWrite() && doorBounds != null
                    if (config.accessType.canRead()) loadDoorPosition()
                }
            },
            onFailed = { e -> log("checkDoorPositionCapabilities 실패", e) }
        )

        brake.checkBrakePedalStatusCapabilities(
            onComplete = { config ->
                log("Brake 지원: ${config.accessType}")
                if (config.accessType.canRead()) loadBrake()
            },
            onFailed = { e -> log("checkBrakePedalStatusCapabilities 실패", e) }
        )

        odometer.checkCurrentDistanceDrivenCapabilities(
            onComplete = { config ->
                log("Odometer 지원: ${config.accessType}")
                if (config.accessType.canRead()) loadOdometer()
            },
            onFailed = { e -> log("checkCurrentDistanceDrivenCapabilities 실패", e) }
        )
    }

    private fun loadDoorPosition() {
        door.getCurrentDoorPosition(
            area = driverDoor,
            onComplete = { pos -> runOnUiThread { showDoorPosition(pos) } },
            onFailed = { e -> log("getCurrentDoorPosition 실패", e) }
        )
    }

    private fun loadBrake() {
        brake.getCurrentBrakePedalStatus(
            onComplete = { status -> runOnUiThread { showBrake(status) } },
            onFailed = { e -> log("getCurrentBrakePedalStatus 실패", e) }
        )
    }

    private fun loadOdometer() {
        odometer.getCurrentDistanceDriven(
            onComplete = { km -> runOnUiThread { showOdometer(km) } },
            onFailed = { e -> log("getCurrentDistanceDriven 실패", e) }
        )
    }

    // ── Set ─────────────────────────────────────────────────────────────

    private fun toggleDriverDoor() {
        val bounds = doorBounds ?: return
        // 최대값(완전 열림)은 차량마다 다르므로 Capability에서 받은 범위를 쓴다
        val isOpen = (currentDoorPosition ?: bounds.minValue) > bounds.minValue
        val target = if (isOpen) bounds.minValue else bounds.maxValue

        door.setDoorPosition(
            area = driverDoor,
            value = target,
            // 요청이 전달됐다는 뜻일 뿐, 실제 값은 Get 또는 Callback으로 확인해야 한다
            onComplete = { _, requested -> log("setDoorPosition 요청 완료: $requested") },
            onFailed = { e -> log("setDoorPosition 실패", e) }
        )
    }

    // ── 화면 표시 ───────────────────────────────────────────────────────

    private fun showDoorPosition(pos: Int?) {
        currentDoorPosition = pos
        val bounds = doorBounds
        doorValue.text = when {
            pos == null -> getString(R.string.unknown)
            bounds != null && pos <= bounds.minValue -> "닫힘 ($pos)"
            else -> "열림 ($pos)"
        }
    }

    private fun showBrake(pressed: Boolean?) {
        brakeValue.text = when (pressed) {
            true -> "밟음"
            false -> "안 밟음"
            null -> getString(R.string.unknown)
        }
    }

    private fun showOdometer(km: Float?) {
        odometerValue.text = km?.let { String.format(Locale.KOREA, "%,.1f km", it) }
            ?: getString(R.string.unknown)
    }

    private fun log(message: String, e: Exception? = null) {
        val line = "${timeFormat.format(Date())} $message" + (e?.let { " → ${describe(it)}" } ?: "")
        if (e != null) Log.w(TAG, line, e) else Log.d(TAG, line)
        runOnUiThread {
            logLines.addFirst(line)
            while (logLines.size > MAX_LOG_LINES) logLines.removeLast()
            logText.text = logLines.joinToString("\n")
        }
    }

    // 문서에는 VehicleException.errorCode가 있지만, 2.3.2 배포본에서는 VehicleException이 난독화되고
    // ErrorCode 클래스도 빠져 있어 쓸 수 없다 → 메시지로만 원인을 본다
    private fun describe(e: Exception): String = e.message ?: e.toString()

    private fun AccessType.canRead() = this == AccessType.READ || this == AccessType.READ_WRITE
    private fun AccessType.canWrite() = this == AccessType.WRITE || this == AccessType.READ_WRITE

    private companion object {
        const val TAG = "PleosStarter"
        const val MAX_LOG_LINES = 50
    }
}
