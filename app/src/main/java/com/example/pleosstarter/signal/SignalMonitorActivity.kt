package com.example.pleosstarter.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.SteeringWheel
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.pleosstarter.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 차량 신호 점검 화면.
 *
 * 앱 아이디어("운전 나이", "차 미니게임천국")에 필요한 신호가 에뮬레이터에서 실제로 오는지,
 * 얼마나 자주 오는지 확인한다. 신호마다 Capability → Get → Callback(구독) 순서로 호출하고,
 * 줄마다 현재 값과 변경 알림 횟수·간격을 보여 준다.
 *
 * 아래쪽 "해 보기"는 신호를 조합한 간단한 예: 정차(P) 여부, 핸들을 꺾을 때 깜빡이를 썼는지.
 * 호출 흐름 설명은 MainActivity 참고.
 */
class SignalMonitorActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var steeringWheel: SteeringWheel
    private lateinit var turnSignal: TurnSignal
    private lateinit var gear: Gear
    private lateinit var brake: Brake
    private lateinit var odometer: Odometer
    private lateinit var seat: Seat
    private lateinit var window: Window
    private lateinit var door: Door

    // 운전석 기준. 다른 자리는 Value만 바꾸면 된다
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private var windowBounds: AreaBounds<Int>? = null
    private var doorBounds: AreaBounds<Int>? = null

    private lateinit var steeringRow: SignalRow
    private lateinit var turnRow: SignalRow
    private lateinit var gearRow: SignalRow
    private lateinit var brakeRow: SignalRow
    private lateinit var odometerRow: SignalRow
    private lateinit var beltRow: SignalRow
    private lateinit var windowRow: SignalRow
    private lateinit var doorRow: SignalRow
    private lateinit var eventText: TextView
    private lateinit var logText: TextView

    private val detector = TurnSignalUsageDetector()
    private var currentGear: VehicleGear? = null

    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)

    // ── Callback 리스너: 등록한 것과 같은 객체로 해제해야 한다

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            runOnUiThread {
                steeringRow.showUpdate(formatAngle(angle))
                onSteering(angle)
            }
        }

        override fun onFailed(e: Exception) = log("registerSteeringWheelAngle 실패", e)
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            runOnUiThread {
                turnRow.showUpdate(formatTurnSignal(state))
                onTurnSignal(state)
            }
        }

        override fun onFailed(e: Exception) = log("registerTurnSignalState 실패", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            runOnUiThread {
                gearRow.showUpdate(formatGear(gear))
                onGear(gear)
            }
        }

        override fun onFailed(e: Exception) = log("registerGearSelection 실패", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            runOnUiThread { brakeRow.showUpdate(formatBrake(status)) }
        }

        override fun onFailed(e: Exception) = log("registerBrakePedalStatus 실패", e)
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            runOnUiThread { odometerRow.showUpdate(formatKm(value)) }
        }

        override fun onFailed(e: Exception) = log("registerDistanceDriven 실패", e)
    }

    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) {
            runOnUiThread { beltRow.showUpdate(formatBelt(state)) }
        }

        override fun onFailed(e: Exception) = log("registerSeatBeltBuckledState 실패", e)
    }

    private val windowListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            runOnUiThread { windowRow.showUpdate(formatPosition(position, windowBounds)) }
        }

        override fun onFailed(e: Exception) = log("registerWindowPosition 실패", e)
    }

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            runOnUiThread { doorRow.showUpdate(formatPosition(pos, doorBounds)) }
        }

        override fun onFailed(e: Exception) = log("registerDoorPosition 실패", e)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signal_monitor)

        val list = findViewById<LinearLayout>(R.id.signalList)
        steeringRow = SignalRow(list, R.string.signal_steering)
        turnRow = SignalRow(list, R.string.signal_turn)
        gearRow = SignalRow(list, R.string.signal_gear)
        brakeRow = SignalRow(list, R.string.signal_brake)
        odometerRow = SignalRow(list, R.string.signal_odometer)
        beltRow = SignalRow(list, R.string.signal_belt)
        windowRow = SignalRow(list, R.string.signal_window)
        doorRow = SignalRow(list, R.string.signal_door)
        eventText = findViewById(R.id.eventText)
        logText = findViewById(R.id.logText)
        showEvents()

        findViewById<Button>(R.id.refreshButton).setOnClickListener { loadAll() }
        findViewById<Button>(R.id.resetButton).setOnClickListener { resetCounts() }

        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        log("Vehicle.initialize() 호출")

        steeringWheel = vehicle.getSteeringWheel()
        turnSignal = vehicle.getTurnSignal()
        gear = vehicle.getGear()
        brake = vehicle.getBrake()
        odometer = vehicle.getOdometer()
        seat = vehicle.getSeat()
        window = vehicle.getWindow()
        door = vehicle.getDoor()

        loadAll()
    }

    override fun onStart() {
        super.onStart()
        steeringWheel.registerSteeringWheelAngle(steeringListener)
        turnSignal.registerTurnSignalState(turnSignalListener)
        gear.registerGearSelection(gearListener)
        brake.registerBrakePedalStatus(brakeListener)
        odometer.registerDistanceDriven(odometerListener)
        seat.registerSeatBeltBuckledState(driverSeat, beltListener)
        window.registerWindowPosition(driverWindow, windowListener)
        door.registerDoorPosition(driverDoor, doorListener)
        log("변경 알림 구독 시작")
    }

    override fun onStop() {
        steeringWheel.unregisterSteeringWheelAngle(steeringListener)
        turnSignal.unregisterTurnSignalState(turnSignalListener)
        gear.unregisterGearSelection(gearListener)
        brake.unregisterBrakePedalStatus(brakeListener)
        odometer.unregisterDistanceDriven(odometerListener)
        seat.unregisterSeatBeltBuckledState(driverSeat, beltListener)
        window.unregisterWindowPosition(driverWindow, windowListener)
        door.unregisterDoorPosition(driverDoor, doorListener)
        log("변경 알림 구독 해제")
        super.onStop()
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }

    // ── Capability + Get ────────────────────────────────────────────────

    private fun loadAll() {
        steeringWheel.checkSteeringWheelAngleCapabilities(
            onComplete = { config, bounds ->
                log("SteeringWheel 지원: ${config.accessType}, 범위 ${bounds.minValue}..${bounds.maxValue}")
                if (config.accessType.canRead()) {
                    steeringWheel.getCurrentSteeringWheelAngle(
                        onComplete = { angle -> runOnUiThread { steeringRow.showCurrent(formatAngle(angle)) } },
                        onFailed = { e -> log("getCurrentSteeringWheelAngle 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkSteeringWheelAngleCapabilities 실패", e) }
        )

        turnSignal.checkTurnSignalStateCapabilities(
            onComplete = { config ->
                log("TurnSignal 지원: ${config.accessType}")
                if (config.accessType.canRead()) {
                    turnSignal.getCurrentTurnSignalState(
                        onComplete = { state ->
                            runOnUiThread {
                                turnRow.showCurrent(formatTurnSignal(state))
                                onTurnSignal(state)
                            }
                        },
                        onFailed = { e -> log("getCurrentTurnSignalState 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkTurnSignalStateCapabilities 실패", e) }
        )

        // Gear는 Capability API가 없어서 바로 조회한다.
        // SDK 2.3.2에서 deprecated("Google GEAR_SELECTION property를 쓰라") 표시가 있지만 아직 동작은 한다.
        // 에뮬레이터에서 값이 안 오면 Android CarPropertyManager의 GEAR_SELECTION으로 바꾸는 걸 검토

        gear.getCurrentGearSelectionState(
            onComplete = { g ->
                runOnUiThread {
                    gearRow.showCurrent(formatGear(g))
                    onGear(g)
                }
            },
            onFailed = { e -> log("getCurrentGearSelectionState 실패", e) }
        )

        brake.checkBrakePedalStatusCapabilities(
            onComplete = { config ->
                log("Brake 지원: ${config.accessType}")
                if (config.accessType.canRead()) {
                    brake.getCurrentBrakePedalStatus(
                        onComplete = { status -> runOnUiThread { brakeRow.showCurrent(formatBrake(status)) } },
                        onFailed = { e -> log("getCurrentBrakePedalStatus 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkBrakePedalStatusCapabilities 실패", e) }
        )

        odometer.checkCurrentDistanceDrivenCapabilities(
            onComplete = { config ->
                log("Odometer 지원: ${config.accessType}")
                if (config.accessType.canRead()) {
                    odometer.getCurrentDistanceDriven(
                        onComplete = { km -> runOnUiThread { odometerRow.showCurrent(formatKm(km)) } },
                        onFailed = { e -> log("getCurrentDistanceDriven 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkCurrentDistanceDrivenCapabilities 실패", e) }
        )

        seat.checkSeatBeltBuckledCapabilities(
            onComplete = { config, areas ->
                val supported = areas.contains(driverSeat)
                log("SeatBelt 지원: ${config.accessType}, 운전석 ${if (supported) "지원" else "미지원"} (지원 좌석 ${areas.size}곳)")
                if (config.accessType.canRead()) {
                    seat.getCurrentSeatBeltBuckledState(
                        area = driverSeat,
                        onComplete = { state -> runOnUiThread { beltRow.showCurrent(formatBelt(state)) } },
                        onFailed = { e -> log("getCurrentSeatBeltBuckledState 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkSeatBeltBuckledCapabilities 실패", e) }
        )

        window.checkWindowPositionCapabilities(
            onComplete = { config, areaMap ->
                runOnUiThread {
                    windowBounds = areaMap[driverWindow]
                    log("Window 지원: ${config.accessType}, 범위 ${windowBounds?.let { "${it.minValue}..${it.maxValue}" } ?: "없음"}")
                }
                if (config.accessType.canRead()) {
                    window.getCurrentWindowPosition(
                        area = driverWindow,
                        onComplete = { pos -> runOnUiThread { windowRow.showCurrent(formatPosition(pos, windowBounds)) } },
                        onFailed = { e -> log("getCurrentWindowPosition 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkWindowPositionCapabilities 실패", e) }
        )

        door.checkDoorPositionCapabilities(
            onComplete = { config, areaMap ->
                runOnUiThread {
                    doorBounds = areaMap[driverDoor]
                    log("Door 지원: ${config.accessType}, 범위 ${doorBounds?.let { "${it.minValue}..${it.maxValue}" } ?: "없음"}")
                }
                if (config.accessType.canRead()) {
                    door.getCurrentDoorPosition(
                        area = driverDoor,
                        onComplete = { pos -> runOnUiThread { doorRow.showCurrent(formatPosition(pos, doorBounds)) } },
                        onFailed = { e -> log("getCurrentDoorPosition 실패", e) }
                    )
                }
            },
            onFailed = { e -> log("checkDoorPositionCapabilities 실패", e) }
        )
    }

    // ── 해 보기: 신호 조합 (UI 스레드에서만 호출) ─────────────────────────

    private fun onSteering(angle: Float?) {
        when (detector.onSteeringAngle(angle, SystemClock.elapsedRealtime())) {
            true -> log("이벤트: 깜빡이 켜고 핸들 꺾음 (${formatAngle(angle)})")
            false -> log("이벤트: 깜빡이 없이 핸들 꺾음 (${formatAngle(angle)})")
            null -> return
        }
        showEvents()
    }

    private fun onTurnSignal(state: TurnSignalValue?) {
        detector.onTurnSignal(state, SystemClock.elapsedRealtime())
    }

    private fun onGear(g: VehicleGear?) {
        currentGear = g
        showEvents()
    }

    private fun resetCounts() {
        detector.reset()
        listOf(steeringRow, turnRow, gearRow, brakeRow, odometerRow, beltRow, windowRow, doorRow)
            .forEach { it.resetStats() }
        showEvents()
        log("횟수 초기화")
    }

    private fun showEvents() {
        val parked = when (currentGear) {
            null -> getString(R.string.unknown)
            VehicleGear.GEAR_PARK -> "예 (미니게임 가능)"
            else -> "아니오"
        }
        eventText.text = "정차(P): $parked\n" +
            "핸들 꺾을 때 깜빡이 사용 ${detector.withSignal}회 · 안 씀 ${detector.withoutSignal}회\n" +
            "(기준: 핸들 ±30° 이상, 깜빡이 3초 이내)"
    }

    // ── 표시 형식 ───────────────────────────────────────────────────────

    // 부호(왼쪽/오른쪽)는 에뮬레이터에서 확인 전이라 숫자 그대로 보여 준다
    private fun formatAngle(angle: Float?) =
        angle?.let { String.format(Locale.KOREA, "%.1f°", it) } ?: getString(R.string.unknown)

    private fun formatTurnSignal(state: TurnSignalValue?) = when (state) {
        TurnSignalValue.OFF -> "꺼짐"
        TurnSignalValue.LEFT -> "◀ 왼쪽"
        TurnSignalValue.RIGHT -> "오른쪽 ▶"
        null -> getString(R.string.unknown)
    }

    private fun formatGear(g: VehicleGear?) = when (g) {
        null -> getString(R.string.unknown)
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        VehicleGear.GEAR_UNKNOWN -> "알 수 없음"
        else -> g.name.removePrefix("GEAR_") + "단"
    }

    private fun formatBrake(pressed: Boolean?) = when (pressed) {
        true -> "밟음"
        false -> "안 밟음"
        null -> getString(R.string.unknown)
    }

    private fun formatKm(km: Float?) =
        km?.let { String.format(Locale.KOREA, "%,.1f km", it) } ?: getString(R.string.unknown)

    private fun formatBelt(buckled: Boolean?) = when (buckled) {
        true -> "맴"
        false -> "안 맴"
        null -> getString(R.string.unknown)
    }

    // 창문·문 위치: 최솟값이 닫힘. 범위는 차량마다 달라서 Capability 값으로 판단한다
    private fun formatPosition(pos: Int?, bounds: AreaBounds<Int>?): String = when {
        pos == null -> getString(R.string.unknown)
        bounds == null -> "$pos"
        pos <= bounds.minValue -> "닫힘 ($pos)"
        else -> "열림 ($pos / ${bounds.maxValue})"
    }

    // ── 로그 ────────────────────────────────────────────────────────────

    private fun log(message: String, e: Exception? = null) {
        // VehicleException이면 message에 오류 코드 이름(예: ERROR_SECURITY_PERMISSION)이 들어 있다
        val line = "${timeFormat.format(Date())} $message" + (e?.let { " → ${it.message ?: it}" } ?: "")
        if (e != null) Log.w(TAG, line, e) else Log.d(TAG, line)
        runOnUiThread {
            logLines.addFirst(line)
            while (logLines.size > MAX_LOG_LINES) logLines.removeLast()
            logText.text = logLines.joinToString("\n")
        }
    }

    private fun AccessType.canRead() = this == AccessType.READ || this == AccessType.READ_WRITE

    private companion object {
        const val TAG = "SignalMonitor"
        const val MAX_LOG_LINES = 100
    }
}
