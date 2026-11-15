package com.example.pleosstarter.school

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.DrivingMode
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.SteeringWheel
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.pleosstarter.school.parking.LocationSource
import com.example.pleosstarter.school.parking.NaviClient
import com.example.pleosstarter.school.parking.StopChoice
import com.example.pleosstarter.school.parking.ZoneJudge
import java.util.Locale

/**
 * 초보 탈출 학교 — 주행 성적표.
 *
 * 기어가 D가 되면 주행 시작, P로 돌아오면 성적표를 보여 주고 졸업 진도·배지에 반영한다.
 * - 정차(P): 졸업 진도, 배지, 마지막 성적표 (보는 화면)
 * - 주행 중(D): 화면은 큰 글씨 한두 줄만. 깜빡이 없이 핸들을 꺾으면 짧은 코치 한마디
 * 쓰는 신호: 기어, 방향지시등, 핸들 각도, 브레이크 페달, 총 주행거리. 호출 흐름은 MainActivity 참고.
 */
class SchoolActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle
    private lateinit var gear: Gear
    private lateinit var turnSignal: TurnSignal
    private lateinit var steeringWheel: SteeringWheel
    private lateinit var brake: Brake
    private lateinit var odometer: Odometer
    private lateinit var wiper: Wiper
    private lateinit var light: Light
    private lateinit var drivingMode: DrivingMode
    private lateinit var seat: Seat
    private lateinit var hvac: HVAC
    // 공조 전원·에어컨·내외기는 좌석 5개를 묶은 영역으로만 응답한다 (dev-environment-capabilities.md)
    private val allSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    internal lateinit var progress: SchoolProgress
    // 주정차 판정 (부록 C): 위치는 FusedLocation 우선, 없으면 내비. 길 안내는 내비
    internal lateinit var navi: NaviClient
    internal lateinit var location: LocationSource
    internal val judge = ZoneJudge()
    /** 기어 P 직후 성적표 위에 뜨는 판정 카드 */
    internal var parkingCard: ParkingCard? = null
    /** 판정 카드가 10초(🟢는 3초) 안에 응답이 없으면 닫는다 */
    internal val parkingTimeout = Runnable { closeParkingCard(StopChoice.NONE) }
    /** 기록 화면 위 [주행] 0 / [정차] 1 */
    internal var recordTab = 0
    /** 내비 위 코치 배너 (부록 D). 우리 앱이 화면 앞에 없을 때만 쓴다 */
    private lateinit var banner: CoachBanner
    private var inFront = false
    internal val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // 최신 신호값 (UI 스레드에서만 읽고 쓴다)
    /**
     * 차량 신호 (부록 H-⑥): null = 확인 중, true = 들어옴, false = 끊김.
     * 판단: 기어·주행 거리 값이 한 번이라도 오면 "들어옴", 그 두 구독·읽기가 실패하거나 SDK 초기화가 실패하면 "끊김".
     * 다른 신호(시트·크루즈 등)는 차종에 따라 원래 없을 수 있어 보지 않는다
     */
    private var signalOk: Boolean? = null
    /** 신호가 확인 중·끊김이 된 시각. 1초 안이면 아무것도 안 띄우고, 10초 넘으면 [다시 연결] (부록 H-⑥·⑧) */
    private var signalLostAt = SystemClock.elapsedRealtime()
    /** 시험용: adb로 `--ez no_signal true`를 주면 신호가 안 오는 것처럼 (디버그 빌드만) */
    private var fakeNoSignal = false
    private var currentGear: VehicleGear? = null
    private var brakePressed: Boolean? = null
    private var odometerKm: Float? = null

    // 주행 한 번의 상태
    private var driving = false
    private var tripStartKm: Float? = null
    private var tripStartBrake: Boolean? = null
    /** 회전 깜빡이 판정 (부록 I-2 L2: 주행 중만·방향 일치·시작·끝 100m 제외) */
    private val detector = TurnJudge()
    /** 달림·서 있음·추정 속도 (부록 I-2 L3·L4) */
    private val motion = MotionTracker()
    /** 운전 센스 값 (부록 I-5) */
    private val sense = DriveSenseMeter()
    /** 마지막 핸들 각도 (핸들 안정도 1초 표본 L10, 잔 조향) */
    private var steeringAngle: Float? = null
    private val leftOnDetector = SignalLeftOnDetector()
    private val leftWait = LeftWaitDetector()
    /** 내비 다음 회전 미리 알림 T7·R1 (부록 E-6·E-7) */
    private val turnAhead = TurnAheadCoach()
    /** 미리 켜서 칭찬받은 시각. 곧이어 핸들로 꺾으면 깜빡이 횟수를 두 번 세지 않으려고 */
    private val earlyAt = mutableListOf<Long>()
    private var earlyOverlap = 0
    private var turnLine: String? = null
    private var tickCount = 0
    internal val engine = CoachEngine()
    private val road = RoadConditionDetector()
    private val safety = SafetyDetector()
    private val wobble = WobbleDetector()
    private val seatHeat = SeatHeatDetector()
    /** 크루즈가 켜져 있는지 (C1, 부록 G-2) */
    private var cruiseOn = false
    private val fog = FogDetector()
    private val habit = HabitMeter()
    /** 이번 주행에서 받은 코치와 그때 거리 (기록 > 코치 카드) */
    private val hits = mutableListOf<CoachHit>()

    // 화면에 보여 줄 것
    private var coachMessage: String? = null
    private var coachGood: Boolean? = true
    /** 방금 끝난 주행 결과 (성적표 맨 위 목표·축하 화면). 성적표를 닫으면 null */
    internal var outcome: TripOutcome? = null
    /** 주행을 마치고 성적표를 처음 보는 중 */
    internal var justFinished = false
    /** 학습 경로에서 말풍선이 펼쳐진 노드 (L2). 없으면 null */
    internal var openNode: PathNode? = null
    /** 잠긴 노드를 눌렀을 때 흔들 노드 (부록 B-4) */
    internal var shakeNode: PathNode? = null
    /** 이번 주행을 시작할 때의 "이번 주행 연습하기" 기술 */
    private var tripTarget: Skill? = null
    /** 배우기에서 기본과 반대로 접거나 펼친 단원. 기본은 지금 하는 단원만 펼침 (부록 F-7) */
    internal var openUnits = mutableSetOf<PathUnit>()
    // 같은 화면을 다시 그릴 때 스크롤 자리를 지키려고 (누를 때마다 확 튀지 않게)
    private var pageScroll: ScrollView? = null
    private var shownScreen: SchoolScreen? = null
    /** 떠 있는 확인 창. 기어 D가 되면 닫는다 (부록 B-1) */
    internal var dialog: AlertDialog? = null

    private val root by lazy { FrameLayout(this) }
    /** 홈·운전 카드·3D 운전 연습 WebView (0.18.0). 처음 P 화면을 그릴 때 만든다 */
    private var webOrNull: DriveCardsWeb? = null
    private val web: DriveCardsWeb get() = webOrNull ?: DriveCardsWeb(this).also { webOrNull = it }
    internal val screens = ScreenStack()

    // ── Callback 리스너 ──────────────────────────────────────────────────

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            runOnUiThread {
                onSignal(true)
                onGear(gear)
            }
        }

        override fun onFailed(e: Exception) {
            log("registerGearSelection 실패", e)
            runOnUiThread { onSignal(false) }
        }
    }

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            runOnUiThread { onTurnSignal(state) }
        }

        override fun onFailed(e: Exception) = log("registerTurnSignalState 실패", e)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            runOnUiThread { onSteering(angle) }
        }

        override fun onFailed(e: Exception) = log("registerSteeringWheelAngle 실패", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            runOnUiThread {
                val now = SystemClock.elapsedRealtime()
                // 브레이크를 새로 밟은 순간: 부드럽게 세우기·미리 줄이기·합류·정체 (부록 I-5)
                if (driving && status == true && brakePressed == false) sense.onBrakePress(motion.moving(now), motion.km)
                brakePressed = status
                habit.onBrake(status, driving)
                webOrNull?.push("brake", status)
            }
        }

        override fun onFailed(e: Exception) = log("registerBrakePedalStatus 실패", e)
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            runOnUiThread {
                onSignal(true)
                motion.onDistance(value, SystemClock.elapsedRealtime())
                odometerKm = value
                leftOnDetector.onDistance(value, SystemClock.elapsedRealtime())
                safety.onDistance(value, SystemClock.elapsedRealtime())
                onDistance(value)
            }
        }

        override fun onFailed(e: Exception) {
            log("registerDistanceDriven 실패", e)
            runOnUiThread { onSignal(false) }
        }
    }

    // 비·밤 코치용: 와이퍼, 전조등(하향)·상향등·실내등
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            runOnUiThread {
                road.wiperOn = state == WiperState.ON
                webOrNull?.push("wiper", state == WiperState.ON)
            }
        }

        override fun onFailed(e: Exception) = log("registerWiperState 실패", e)
    }

    private fun lightListener(name: String, apply: (Boolean) -> Unit) = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            runOnUiThread { apply(state == LightState.ON) }
        }

        override fun onFailed(e: Exception) = log("$name 구독 실패", e)
    }

    private val lowBeamListener = lightListener("전조등") { road.lowBeamOn = it; webOrNull?.push("light", it) }
    private val highBeamListener = lightListener("상향등") { road.highBeamOn = it; webOrNull?.push("high", it) }
    private val cabinListener = lightListener("실내등") { road.cabinOn = it; webOrNull?.push("interior", it) }

    // 크루즈 C1 (부록 G-2): 켜짐(ENABLED)·작동(ACTIVATED)이면 켠 것으로 본다
    private val cruiseListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) {
            runOnUiThread { cruiseOn = state == CruiseControl.ENABLED || state == CruiseControl.ACTIVATED }
        }

        override fun onFailed(e: Exception) = log("registerCruiseControlState 실패", e)
    }

    // 히터 대신 열선 E1 (부록 G-2): 운전석 열선 단계 (0 = 꺼짐)
    private val seatHeatListener = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(level: Int?) {
            runOnUiThread { seatHeat.seatHeat = level }
        }

        override fun onFailed(e: Exception) = log("registerHvacSeatTemperature 실패", e)
    }

    // 김 서림 코치(F1)용: 공조 전원·에어컨·내외기·앞유리 성에 제거·설정 온도·실내 온도
    private val hvacPowerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(state: Boolean?) {
            runOnUiThread {
                fog.powerOn = state == true
                seatHeat.powerOn = state == true
            }
        }

        override fun onFailed(e: Exception) = log("registerHvacPowerState 실패", e)
    }
    private val hvacAcListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(state: Boolean?) {
            runOnUiThread { fog.acOn = state == true }
        }

        override fun onFailed(e: Exception) = log("registerHvacAcState 실패", e)
    }
    private val hvacRecircListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
            runOnUiThread { fog.recircOn = state == true }
        }

        override fun onFailed(e: Exception) = log("registerHvacRecirculationState 실패", e)
    }
    private val hvacDefrostListener = object : HvacDefrosterStateListener {
        override fun onHvacDefrosterStateUpdated(state: Boolean?) {
            runOnUiThread {
                fog.defrostOn = state == true
                webOrNull?.push("defog", state == true)
            }
        }

        override fun onFailed(e: Exception) = log("registerHvacDefrosterState 실패", e)
    }
    private val hvacSetTempListener = object : HvacTemperatureValueListener {
        override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) {
            runOnUiThread {
                fog.setTempC = celsius
                seatHeat.setTempC = celsius
            }
        }

        override fun onFailed(e: Exception) = log("registerHvacTemperature 실패", e)
    }
    private val hvacCabinTempListener = object : HvacTemperatureValueListener {
        override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) {
            runOnUiThread { fog.cabinTempC = celsius }
        }

        override fun onFailed(e: Exception) = log("registerHvacTemperatureCurrent 실패", e)
    }

    // ── 생명주기 ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = SchoolProgress(applicationContext)
        engine.voiceEnabled = progress.voiceOn
        root.setBackgroundColor(BG)
        setContentView(root)
        // 시스템 뒤로 가기: 화면 스택을 한 칸씩 나오고, 배우기 첫 화면이면 원래대로 앱을 닫는다
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!driving && goBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        // TTS는 선택 기능: 엔진·한국어 데이터가 없으면 조용히 글자 안내만 한다
        tts = TextToSpeech(applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                ttsReady = t.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE
                log("TTS ${if (ttsReady) "사용 가능" else "한국어 없음 → 글자만"}")
            } else {
                log("TTS 초기화 실패 → 글자만")
            }
        }

        fakeNoSignal = debuggable() && intent?.getBooleanExtra(EXTRA_NO_SIGNAL, false) == true
        vehicle = Vehicle(applicationContext)
        initVehicle()
        gear = vehicle.getGear()
        turnSignal = vehicle.getTurnSignal()
        steeringWheel = vehicle.getSteeringWheel()
        brake = vehicle.getBrake()
        odometer = vehicle.getOdometer()
        wiper = vehicle.getWiper()
        light = vehicle.getLight()
        drivingMode = vehicle.getDrivingMode()
        seat = vehicle.getSeat()
        hvac = vehicle.getHVAC()
        navi = NaviClient(this) { log(it) }
        navi.onTurns = { list ->
            val before = turnAhead.current
            turnAhead.onTurns(list, odometerKm)
            if (turnAhead.current != before) log("내비 다음 회전: ${turnAhead.current} (목록 ${list.size}개)")
        }
        navi.onReRoute = {
            safety.onReRoute(SystemClock.elapsedRealtime())
            log("내비 경로 다시 짬")
        }
        navi.initialize()
        location = LocationSource(this, navi) { log(it) }
        banner = CoachBanner(this)
        // 실행 중 허락: FUSED_LOCATION(거절하면 내비 위치로 판정), POST_NOTIFICATIONS(첫 실행 때 한 번, 거절하면 음성만)
        val ask = mutableListOf<String>()
        if (checkSelfPermission(FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED) location.start() else ask += FUSED_LOCATION
        if (!banner.allowed && !progress.notifAsked) {
            ask += CoachBanner.PERMISSION
            progress.notifAsked = true
        }
        if (ask.isNotEmpty()) requestPermissions(ask.toTypedArray(), REQ_LOCATION)

        // 내비로 화면을 바꿔도 주행 기록이 끊기지 않게, 화면(onStart/onStop)이 아니라 앱 생성/종료에 맞춘다
        gear.registerGearSelection(gearListener)
        turnSignal.registerTurnSignalState(turnListener)
        steeringWheel.registerSteeringWheelAngle(steeringListener)
        brake.registerBrakePedalStatus(brakeListener)
        odometer.registerDistanceDriven(odometerListener)
        wiper.registerWiperState(windshield, wiperListener)
        light.registerLowBeamLightState(lowBeamListener)
        light.registerHighBeamLightState(highBeamListener)
        light.registerCabinLightState(cabinListener)
        drivingMode.registerCruiseControlState(cruiseListener)
        seat.registerHvacSeatTemperature(driverSeat, seatHeatListener)
        hvac.registerHvacPowerState(allSeats, hvacPowerListener)
        hvac.registerHvacAcState(allSeats, hvacAcListener)
        hvac.registerHvacRecirculationState(allSeats, hvacRecircListener)
        hvac.registerHvacDefrosterState(windshield, hvacDefrostListener)
        hvac.registerHvacTemperature(driverSeat, hvacSetTempListener)
        hvac.registerHvacTemperatureCurrent(allSeats, hvacCabinTempListener)

        // 내비를 앞에 띄워도 얼지 않게 (Android 14는 뒤로 간 앱을 약 10초 뒤 얼림)
        SchoolKeepAlive.start(this) { log(it) }

        loadCurrent()
        watchSignal()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 시험용: 켜진 채로 신호 끊김을 흉내 내거나 되돌린다 (디버그 빌드만)
        if (debuggable() && intent.hasExtra(EXTRA_NO_SIGNAL)) {
            fakeNoSignal = intent.getBooleanExtra(EXTRA_NO_SIGNAL, false)
            log("시험: 신호 끊김 흉내 $fakeNoSignal")
            if (fakeNoSignal) onSignal(false) else reconnect()
        }
    }

    private fun debuggable() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** SDK 초기화. 실패해도 앱은 켜 두고 "차량 신호 확인 중"으로 (부록 H-⑥) */
    private fun initVehicle() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            log("Vehicle 초기화 실패", e)
            signalOk = false // 화면은 아직 안 그렸을 수 있어 값만 바꾼다. 1초·10초 뒤 watchSignal이 그린다
        }
    }

    /** 차량 신호가 들어오거나 끊겼을 때. 끊긴 동안 코치는 멈추고, 돌아오면 저절로 다시 (부록 H-⑥) */
    private fun onSignal(ok: Boolean) {
        val value = ok && !fakeNoSignal
        if (signalOk == value) return
        if (signalOk == true && !value) signalLostAt = SystemClock.elapsedRealtime()
        signalOk = value
        log("차량 신호 ${if (value) "들어옴" else "끊김"}")
        if (!value) watchSignal()
        render()
    }

    /** 신호가 없으면 1초 뒤(확인 중 표시)·10초 뒤([다시 연결]) 화면을 다시 그린다 */
    private fun watchSignal() {
        handler.removeCallbacks(signalRender)
        val waited = SystemClock.elapsedRealtime() - signalLostAt
        if (waited < SIGNAL_SHOW_MS) handler.postDelayed(signalRender, SIGNAL_SHOW_MS - waited)
        if (waited < SIGNAL_GIVE_UP_MS) handler.postDelayed(signalRender, SIGNAL_GIVE_UP_MS - waited)
    }

    private val signalRender = Runnable { if (signalOk != true) render() }

    /** 신호가 없어 보여 줄 단계: 0 = 보여 주지 않음(1초 안 또는 들어옴), 1 = 확인 중, 2 = 연결 확인 + [다시 연결] */
    private fun signalStage(): Int {
        if (signalOk == true) return 0
        val waited = SystemClock.elapsedRealtime() - signalLostAt
        return when {
            waited < SIGNAL_SHOW_MS -> 0
            waited < SIGNAL_GIVE_UP_MS -> 1
            else -> 2
        }
    }

    /** [다시 연결]: SDK를 다시 열고 지금 값을 다시 읽는다 */
    private fun reconnect() {
        signalOk = null
        signalLostAt = SystemClock.elapsedRealtime()
        initVehicle()
        loadCurrent()
        watchSignal()
        render()
    }

    /** 메뉴 화면 맨 위 신호 안내 (부록 H-⑥) */
    private fun signalCard(stage: Int): LinearLayout = card {
        it.addView(row().apply {
            addView(iconBox("📡", 84, if (stage == 2) BAD_SOFT else RAISED))
            addView(column().apply {
                setPadding(dp(24), 0, 0, 0)
                addView(text(if (stage == 2) "차량 연결을 확인해 주세요" else "📡 차량 신호 확인 중", 28f, TEXT, bold = true))
                addView(text(if (stage == 2) "시동이 켜져 있는지 본 뒤 다시 연결해요. 저장된 기록은 그대로예요"
                    else "신호가 돌아오면 이어서 기록해요 · 코치는 잠시 쉬어요", 24f, MUTED).padTop(4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        if (stage == 2) it.addView(button("다시 연결", primary = true) { reconnect() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(20) }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        permissions.forEachIndexed { i, p ->
            val granted = grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
            when {
                p == FUSED_LOCATION && granted -> location.start()
                p == FUSED_LOCATION -> log("위치 퍼미션 거절 → 내비 위치로 주정차 판정")
                p == CoachBanner.PERMISSION && !granted -> log("알림 퍼미션 거절 → 내비 위 배너 없이 음성만")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        banner.clear()
        webOrNull?.view?.onResume()
    }

    override fun onPause() {
        inFront = false
        webOrNull?.view?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webOrNull?.view?.destroy()
        tts?.shutdown()
        gear.unregisterGearSelection(gearListener)
        turnSignal.unregisterTurnSignalState(turnListener)
        steeringWheel.unregisterSteeringWheelAngle(steeringListener)
        brake.unregisterBrakePedalStatus(brakeListener)
        odometer.unregisterDistanceDriven(odometerListener)
        wiper.unregisterWiperState(windshield, wiperListener)
        light.unregisterLowBeamLightState(lowBeamListener)
        light.unregisterHighBeamLightState(highBeamListener)
        light.unregisterCabinLightState(cabinListener)
        drivingMode.unregisterCruiseControlState(cruiseListener)
        seat.unregisterHvacSeatTemperature(driverSeat, seatHeatListener)
        hvac.unregisterHvacPowerState(allSeats, hvacPowerListener)
        hvac.unregisterHvacAcState(allSeats, hvacAcListener)
        hvac.unregisterHvacRecirculationState(allSeats, hvacRecircListener)
        hvac.unregisterHvacDefrosterState(windshield, hvacDefrostListener)
        hvac.unregisterHvacTemperature(driverSeat, hvacSetTempListener)
        hvac.unregisterHvacTemperatureCurrent(allSeats, hvacCabinTempListener)
        banner.clear()
        location.stop()
        SchoolKeepAlive.stop(this)
        navi.release()
        vehicle.release()
        super.onDestroy()
    }

    /** 앱을 켠 순간의 값을 한 번 읽는다. 이후는 구독으로 갱신된다. */
    private fun loadCurrent() {
        brake.getCurrentBrakePedalStatus(
            onComplete = { s -> runOnUiThread { if (brakePressed == null) brakePressed = s } },
            onFailed = { e -> log("getCurrentBrakePedalStatus 실패", e) }
        )
        odometer.getCurrentDistanceDriven(
            onComplete = { km -> runOnUiThread {
                onSignal(true)
                if (odometerKm == null) { odometerKm = km; turnAhead.onDistance(km) }
            } },
            onFailed = { e -> log("getCurrentDistanceDriven 실패", e); runOnUiThread { onSignal(false) } }
        )
        turnSignal.getCurrentTurnSignalState(
            onComplete = { s -> runOnUiThread { onTurnSignal(s) } },
            onFailed = { e -> log("getCurrentTurnSignalState 실패", e) }
        )
        wiper.getCurrentWiperState(windshield,
            onComplete = { s -> runOnUiThread { road.wiperOn = s == WiperState.ON } },
            onFailed = { e -> log("getCurrentWiperState 실패", e) }
        )
        light.getCurrentLowBeamLightState(
            onComplete = { s -> runOnUiThread { road.lowBeamOn = s == LightState.ON } },
            onFailed = { e -> log("getCurrentLowBeamLightState 실패", e) }
        )
        light.getCurrentHighBeamLightState(
            onComplete = { s -> runOnUiThread { road.highBeamOn = s == LightState.ON } },
            onFailed = { e -> log("getCurrentHighBeamLightState 실패", e) }
        )
        light.getCurrentCabinLightState(
            onComplete = { s -> runOnUiThread { road.cabinOn = s == LightState.ON } },
            onFailed = { e -> log("getCurrentCabinLightState 실패", e) }
        )
        drivingMode.getCruiseControlState(
            { s -> runOnUiThread { cruiseOn = s == CruiseControl.ENABLED || s == CruiseControl.ACTIVATED } },
            { e -> log("getCruiseControlState 실패", e) },
        )
        seat.getHvacSeatTemperature(driverSeat,
            { level -> runOnUiThread { seatHeat.seatHeat = level } },
            { e -> log("getHvacSeatTemperature 실패", e) },
        )
        hvac.getHvacPowerState(allSeats,
            onComplete = { s -> runOnUiThread { fog.powerOn = s == true } },
            onFailed = { e -> log("getHvacPowerState 실패", e) }
        )
        hvac.getHvacAcState(allSeats,
            onComplete = { s -> runOnUiThread { fog.acOn = s == true } },
            onFailed = { e -> log("getHvacAcState 실패", e) }
        )
        hvac.getHvacRecirculationState(allSeats,
            onComplete = { s -> runOnUiThread { fog.recircOn = s == true } },
            onFailed = { e -> log("getHvacRecirculationState 실패", e) }
        )
        hvac.getHvacDefrosterState(windshield,
            onComplete = { s -> runOnUiThread { fog.defrostOn = s == true } },
            onFailed = { e -> log("getHvacDefrosterState 실패", e) }
        )
        hvac.getHvacTemperature(driverSeat, TemperatureUnit.CELSIUS,
            onComplete = { t -> runOnUiThread { fog.setTempC = t } },
            onFailed = { e -> log("getHvacTemperature 실패", e) }
        )
        hvac.getHvacTemperatureCurrent(allSeats,
            onComplete = { t -> runOnUiThread { fog.cabinTempC = t } },
            onFailed = { e -> log("getHvacTemperatureCurrent 실패", e) }
        )
        gear.getCurrentGearSelectionState(
            onComplete = { g -> runOnUiThread {
                onSignal(true)
                if (currentGear == null) onGear(g)
            } },
            onFailed = { e -> log("getCurrentGearSelectionState 실패", e); runOnUiThread { onSignal(false) } }
        )
    }

    // ── 주행 흐름 ────────────────────────────────────────────────────────

    private fun onGear(g: VehicleGear?) {
        val previous = currentGear
        currentGear = g
        if (g == previous) return
        // D·R·N: 어느 깊이든 쌓인 화면을 비우고 확인 창·판정 카드도 닫는다 (부록 B-1)
        if (g != null && g != VehicleGear.GEAR_PARK) {
            // 3D 운전 연습은 P에서만 (0.18.0). 홈으로 돌려 3D를 멈춘다
            webOrNull?.leave()
            screens.open(SchoolMenu.LEARN)
            openNode = null
            justFinished = false
            dialog?.dismiss()
            dialog = null
            parkingCard = null
            handler.removeCallbacks(parkingTimeout)
        }
        when {
            g == VehicleGear.GEAR_DRIVE && !driving -> startTrip(knownStart = previous != null)
            g == VehicleGear.GEAR_PARK && driving -> endTrip()
            // 주행 기록 없이 P가 돼도(예: R → P) 자리 판정은 한다
            g == VehicleGear.GEAR_PARK && previous != null -> {
                render()
                afterPark(judgeNow())
            }
            else -> render()
        }
    }

    private fun startTrip(knownStart: Boolean) {
        driving = true
        justFinished = false
        outcome = null
        tripTarget = progress.target
        // 어느 깊이에 있든 쌓인 화면을 비우고, 열려 있던 확인 창도 닫는다 (부록 B-1)
        screens.open(SchoolMenu.LEARN)
        openNode = null
        dialog?.dismiss()
        dialog = null
        hits.clear()
        habit.reset(brakePressed)
        parkingCard = null
        handler.removeCallbacks(parkingTimeout)
        tripStartKm = odometerKm
        // 앱을 켜기 전부터 D였다면 출발 순간을 못 봤으므로 감점하지 않는다
        tripStartBrake = if (knownStart) brakePressed else null
        detector.reset() // 깜빡이 현재 상태는 유지하고 횟수만 0으로
        sense.reset()
        sense.minMinuteSec = if (progress.demoMode) DEMO_MINUTE_SEC else 60
        leftOnDetector.reset()
        leftWait.reset()
        turnAhead.resetTrip()
        safety.reset()
        wobble.reset()
        seatHeat.reset()
        turnAhead.laneGuide = progress.laneGuide
        earlyAt.clear()
        earlyOverlap = 0
        turnLine = null
        road.reset()
        fog.reset()
        engine.startTrip()
        handler.removeCallbacks(leftOnTick)
        handler.postDelayed(leftOnTick, 1_000L)
        showMessage("출발합니다. 천천히 안전하게 가세요", good = true, speak = true)
        log("주행 시작 (출발 브레이크 $tripStartBrake)")
    }

    private fun endTrip() {
        driving = false
        handler.removeCallbacks(leftOnTick)
        val startKm = tripStartKm
        val endKm = odometerKm
        val distance = if (startKm != null && endKm != null) (endKm - startKm).coerceAtLeast(0f) else 0f
        // 기어 P 순간 자리 판정 (부록 C-3). 🟢면 "6대 구역 알기" 별 조건
        val verdict = judgeNow()
        // 0.18.0 판단: 움직이지 않은 주행(10m 미만, 예: P에서 D 갔다 바로 P)은 기록·성적표·카드 판정 없이 홈으로 (1차 검증 ④)
        if (distance < MIN_TRIP_KM) {
            coachMessage = null
            log("주행 종료: ${distance}km라 기록하지 않음")
            screens.open(SchoolMenu.LEARN)
            render()
            afterPark(verdict)
            return
        }
        // 끝나기 전 100m 안의 회전은 빼고 센다 (부록 I-2 L2)
        val (withSignal, withoutSignal) = detector.counted(distance, edgeKm())
        val result = TripResult(
            distanceKm = distance,
            startBrake = tripStartBrake,
            endBrake = brakePressed,
            // 내비 회전 전에 미리 켠 것(T7·R1 칭찬)도 깜빡이 먼저 켠 회전으로 센다 (부록 E-6·E-7)
            turnsWithSignal = (withSignal + turnAhead.earlyOk - earlyOverlap).coerceAtLeast(0),
            turnsWithoutSignal = withoutSignal,
            signalLeftOn = leftOnDetector.count,
            rain = road.sawRain,
            night = road.sawNight,
            fogFixed = fog.fixed,
            coaches = engine.received().map { it.first }.toSet(),
            steadiness = habit.steadiness(if (progress.demoMode) HabitMeter.DEMO_MIN_SAMPLES else HabitMeter.MIN_SAMPLES),
            brakesPerKm = habit.brakesPerKm(distance),
            parkedSafe = verdict?.safe() == true,
            sense = sense.result(),
        )
        outcome = progress.finishTrip(result, hits.toList(), habit.brakes, tripTarget)
        // 운전 카드: 실수 없는 주행 세기·마스터 (0.18.0)
        DriveCards.onTripFinished(this, result)
        // 성적표 → (축하 화면) → 홈 (0.18.0: 뒤로 가면 바로 홈)
        screens.open(SchoolMenu.LEARN)
        screens.push(SchoolScreen.Report(null))
        justFinished = true
        coachMessage = null
        log("주행 종료: ${result.score}점, ${distance}km, 깜빡이 ${result.turnsWithSignal}/${result.turns}")
        speak("주행 끝. ${result.score}점입니다")
        render()
        afterPark(verdict)
    }

    private fun onTurnSignal(state: TurnSignalValue?) {
        val now = SystemClock.elapsedRealtime()
        detector.onTurnSignal(state, now)
        if (driving) sense.onTurnSignal(state, now)
        leftOnDetector.onTurnSignal(state, now)
        leftWait.onTurnSignal(state, now)
        turnAhead.onTurnSignal(state)
        webOrNull?.push("signal", when (state) {
            TurnSignalValue.LEFT -> "L"
            TurnSignalValue.RIGHT -> "R"
            else -> null
        })
    }

    private fun onDistance(km: Float?) {
        turnAhead.onDistance(km)
    }

    // 주행 중 1초마다: 깜빡이를 켜 두고 잊었는지 본다 (깜빡이 알림은 바뀔 때만 오므로)
    private val leftOnTick = object : Runnable {
        override fun run() {
            if (!driving) return
            val now = SystemClock.elapsedRealtime()
            motion.tick(now)
            // 달림 = 최근 3초 주행거리가 늘어남 (부록 I-2 L3). 오토홀드·신호 대기에서도 맞게
            val running = motion.moving(now)
            val g0 = currentGear
            // 좌회전 대기 "서 있음" = D + 주행거리 3초 그대로 (L4)
            leftWait.onStanding(g0 == VehicleGear.GEAR_DRIVE && motion.stillFor(now, MotionTracker.WINDOW_MS), now)
            // 핸들 안정도는 1초마다 표본 1개 (L10)
            habit.sample(steeringAngle, running && g0 == VehicleGear.GEAR_DRIVE)
            if (leftOnDetector.check(now)) offer(Coach.SIGNAL_LEFT_ON)
            // T7이 나온 회전에서는 좌회전 대기(T3)를 다시 말하지 않는다
            leftWait.check(now)?.let { if (!turnAhead.leftWarned) offer(it) }
            turnAhead.check()?.let { c ->
                if (offer(c)) {
                    turnAhead.accepted(c)
                    if (c == Coach.TURN_LEFT_SOON_OK || c == Coach.RIGHT_SIGNAL_OK) earlyAt += now
                }
            }
            if (++tickCount % 3 == 0) navi.refreshTurns()
            val turnNow = turnAhead.current
            val turnLeft = turnAhead.remaining()
            turnAhead.takePassed().forEach { sense.onTurnPassed(it, tripKmExact()) }
            sense.tick(SenseTick(
                moving = running, speedKmh = motion.speedKmh(now), reverse = g0 == VehicleGear.GEAR_REVERSE,
                wiper = road.wiperOn, brake = brakePressed, recirc = fog.recircOn, angle = steeringAngle,
                turn = turnNow, turnLeftM = turnLeft,
                // 판단: 기록만 하는 값이라 시연 모드도 내비 정체를 그대로 센다 (X4 코치와 달리)
                jam = navi.congestedAhead, tripKm = tripKmExact(),
            ), motion.km)
            // R2 우회전 빨간불 멈춤 (부록 I-5 7, 안내만): 우회전 50m 안 + 주행 중, 한 주행 1번
            if (turnNow?.dir == TurnAhead.Dir.RIGHT && turnLeft != null && turnLeft in 0.0..RIGHT_RED_M && running) offer(Coach.RIGHT_RED)
            val line = turnAhead.line()
            if (line != turnLine) {
                turnLine = line
                render()
            }
            road.check(now, running, isNight()).forEach { offer(it) }
            val g = currentGear
            // X4 진출로 놓치고 멈춤·후진 (부록 G-3). 시연 모드는 출구를 지나치기만 해도 "놓침"
            if (turnAhead.takeExitPassed()) safety.onExitPassed(now, progress.demoMode)
            safety.check(now, drive = g == VehicleGear.GEAR_DRIVE, reverse = g == VehicleGear.GEAR_REVERSE,
                // 판단: 시연 모드(에뮬레이터)는 차가 지도 위에서 안 움직여 정체 구간이 늘 앞에 있으므로 정체를 보지 않는다
                congested = !progress.demoMode && navi.congestedAhead)?.let { offer(it) }
            // C1 크루즈: 이번 주행에서 처음 켜면 (한 주행 1번은 엔진이 지킴)
            if (cruiseOn) offer(Coach.CRUISE)
            // E1 히터 대신 열선
            val heatHold = if (progress.demoMode) SeatHeatDetector.DEMO_HOLD_MS else SeatHeatDetector.HOLD_MS
            seatHeat.check(now, g == VehicleGear.GEAR_DRIVE, heatHold)?.let { offer(it) }
            val fogHold = if (progress.demoMode) FogDetector.DEMO_HOLD_MS else FogDetector.HOLD_MS
            fog.check(now, fogHold)?.let { c -> if (offer(c) && c == Coach.FOG) fog.warned = true }
            handler.postDelayed(this, 1_000L)
        }
    }

    private fun onSteering(angle: Float?) {
        val now = SystemClock.elapsedRealtime()
        steeringAngle = angle
        webOrNull?.steering(angle)
        leftOnDetector.onSteeringAngle(angle, now)
        val missed = leftWait.onSteering(angle, now)
        if (driving) sense.onSteering(angle, now)
        // H2 핸들 흔들림 = 긴장 (부록 G-3)
        if (wobble.onSteering(angle, now, driving && currentGear == VehicleGear.GEAR_DRIVE)) offer(Coach.WOBBLE)
        val used = detector.onSteering(angle, now, motion.moving(now), tripKmExact(), edgeKm())
        if (!driving) return
        if (missed != null) offer(missed)
        if (used == true) {
            // 30초 안에 미리 켜서 칭찬받은 회전이면 같은 회전으로 보고 한 번만 센다
            earlyAt.removeAll { now - it > 30_000L }
            if (earlyAt.isNotEmpty()) {
                earlyAt.removeAt(0)
                earlyOverlap++
            }
        }
        if (used != null) offer(if (used) Coach.SIGNAL_TURN_OK else Coach.NO_SIGNAL_TURN)
    }

    /** 코치를 엔진 규칙(우선순위·간격·1번)에 맞춰 화면·음성에 낸다 */
    /** @return 엔진이 받아들였으면 true (간격·우선순위로 버려지면 false) */
    private fun offer(c: Coach): Boolean {
        // 차량 신호가 없는 동안은 코치를 멈춘다 (틀린 칭찬·경고 방지, 부록 H-⑥)
        if (signalOk != true) return false
        // 졸업생은 칭찬·상황 안내를 말하지 않고 기록만 (부록 G-2 12번, 설정에서 다시 켬)
        val silent = (progress.graduated && progress.quietGraduate && c.quietForGraduate) ||
            // 운전 카드에서 안내를 껐거나 아직 안 열린 단계면 화면·음성 없이 기록만 (0.18.0)
            !DriveCards.coachOn(this, c)
        val d = engine.offer(c, SystemClock.elapsedRealtime(), silent) ?: return false
        log("코치 ${c.code} ${c.label}${if (d.speak) " (음성)" else ""}")
        if (driving && hits.size < TripRecord.MAX_HITS) hits += CoachHit(c, tripKm())
        if (d.show) showMessage(c.message, c.good, d.speak)
        return true
    }

    /** 이번 주행 시작부터 달린 거리 (소수 1자리) */
    private fun tripKm(): Float {
        val start = tripStartKm ?: return 0f
        val now = odometerKm ?: return 0f
        return Math.round((now - start).coerceAtLeast(0f) * 10) / 10f
    }

    /** 이번 주행 시작부터 달린 거리 (반올림 없이) */
    private fun tripKmExact(): Float {
        val start = tripStartKm ?: return 0f
        val now = odometerKm ?: return 0f
        return (now - start).coerceAtLeast(0f)
    }

    /** 회전을 세지 않는 시작·끝 구간 (부록 I-2 L2, 100m [가정]). 판단: 시연 모드는 짧게 달려 보이므로 0 */
    private fun edgeKm(): Float = if (progress.demoMode) 0f else TURN_EDGE_KM

    /** 시연 모드면 늘 밤, 아니면 그날 서울 일몰~일출 (부록 I-2 L6, 법 제37조 1항 1호) */
    private fun isNight(): Boolean = progress.demoMode || SunTimes.isNight()

    private fun showMessage(message: String, good: Boolean?, speak: Boolean) {
        coachMessage = message
        coachGood = good
        if (speak) speak(message)
        // 우리 앱이 앞에 없으면(내비 등) 배너로 한 줄. 칭찬은 배너를 띄우지 않는다 (부록 D)
        if (!inFront && driving && good != true) banner.show(message, if (good == null) "💡" else "⚠")
        render()
        handler.removeCallbacks(clearCoach)
        handler.postDelayed(clearCoach, COACH_SHOW_MS)
    }

    private val clearCoach = Runnable {
        coachMessage = null
        render()
    }

    internal fun speak(text: String) {
        if (ttsReady && engine.voiceEnabled) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach")
    }

    // ── 화면 ─────────────────────────────────────────────────────────────

    /**
     * 주행 중: 흰 바탕 위 큰 글씨 한두 줄만 (부록 F-4 ④, 현규 "주행 화면도 흰색이 좋지").
     * 코치 종류는 글자색이 아니라 위 아이콘 받침 색으로만 구분: 칭찬 파랑 / 고칠 것 빨강 / 상황 안내 금색
     */
    private fun renderDriving(page: LinearLayout) {
        page.minimumHeight = resources.displayMetrics.heightPixels
        page.gravity = Gravity.CENTER
        page.setBackgroundColor(CARD)
        val stage = signalStage()
        if (stage > 0) {
            renderNoSignal(page, stage)
            return
        }
        val message = coachMessage
        val (icon, fill) = when {
            message == null -> "🚗" to RAISED
            coachGood == true -> "👍" to PRIMARY_SOFT
            coachGood == null -> "💡" to GOLD_SOFT
            else -> "⚠" to BAD_SOFT
        }
        page.addView(column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(iconBox(icon, 120, fill))
        })
        page.addView(text("주행 중", 22f, HINT, bold = true, center = true).padTop(20))
        page.addView(
            text(message ?: "앞을 보세요", if (message != null) 72f else 48f, TEXT, bold = true, center = true)
                .apply { setPadding(0, dp(12), 0, 0) }
        )
        page.addView(
            text("정차해서 기어를 P로 놓으면 성적표가 나와요", 20f, MUTED, center = true)
                .apply { setPadding(0, dp(40), 0, 0) }
        )
        val target = tripTarget
        if (driving && target != null) {
            page.addView(text("🎯 목표: ${target.title}", 22f, PRIMARY, bold = true, center = true).apply { setPadding(0, dp(16), 0, 0) })
        }
        // 내비 다음 회전 (부록 E-6 §5-1 C안): 주행 화면 아래 작은 알약
        turnLine?.let {
            page.addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(20), 0, 0)
                addView(chip(it, TEXT).apply { background = rounded(RAISED, 20) })
            })
        }
    }

    /**
     * 주행 중 차량 신호 없음 (부록 H-⑥): 숫자 자리에 0 대신 "—"(회색) + 칩 "📡 차량 신호 확인 중".
     * 10초 넘으면 "차량 연결을 확인해 주세요" + [다시 연결]
     */
    private fun renderNoSignal(page: LinearLayout, stage: Int) {
        page.addView(column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(iconBox("📡", 120, if (stage == 2) BAD_SOFT else RAISED))
            addView(chip(if (stage == 2) "📡 차량 연결을 확인해 주세요" else "📡 차량 신호 확인 중", TEXT).apply {
                background = rounded(RAISED, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24) }
            })
        })
        page.addView(text("— km", 72f, HINT, bold = true, center = true).padTop(16))
        page.addView(text("이번 주행 거리", 24f, HINT, center = true))
        page.addView(text("신호가 돌아오면 이어서 기록해요 · 코치는 잠시 쉬어요", 24f, MUTED, center = true).padTop(32))
        if (stage == 2) page.addView(column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(button("다시 연결", primary = true) { reconnect() }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24) }
            })
        })
    }

    internal fun render() {
        root.removeAllViews()
        render0()
        // 다 그린 뒤 한 번 훑어 누르는 곳 76dp·긴 글 말줄임을 맞춘다 (부록 H-②·④)
        root.fitForCar()
    }

    private fun render0() {
        // 기어가 P(또는 아직 모름)일 때만 사이드바와 메뉴 화면. D·R·N이면 사이드바째 사라지고 주행 화면만 (§2-0)
        val parked = currentGear == null || currentGear == VehicleGear.GEAR_PARK
        if (driving || !parked) {
            val page = column().apply { setPadding(dp(PAGE_H_DP), dp(PAGE_V_DP), dp(PAGE_H_DP), dp(PAGE_V_DP)) }
            renderDriving(page)
            root.addView(ScrollView(this).apply { setBackgroundColor(CARD); addView(page) })
            return
        }
        // 0.18.0 홈 = 운전 카드 WebView (프로토타입 홈). 사이드바 없음 (plan-school-reset-basics §7)
        if (isHome()) {
            val fresh = shownScreen !== screens.current
            root.addView(web.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            if (fresh) web.refresh()
            signalStage().takeIf { it > 0 }?.let { stage ->
                root.addView(signalCard(stage), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(PAGE_H_DP), dp(PAGE_V_DP), dp(PAGE_H_DP), 0) })
            }
            parkingCard?.let { root.addView(parkingOverlay(it), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }
            pageScroll = null
            shownScreen = screens.current
            return
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // 0.18.0: 사이드바(미션·레벨·배지 메뉴)는 숨김. 데이터와 화면 코드는 그대로 둔다 (현규 11/9 "미션 빼고, 레벨 빼고, 배지도 빼자")
        if (SHOW_SIDEBAR) {
            row.addView(sidebar(), LinearLayout.LayoutParams(dp(SIDEBAR_DP), LinearLayout.LayoutParams.MATCH_PARENT))
            row.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
        }
        val page = column().apply { setPadding(dp(PAGE_H_DP), dp(PAGE_V_DP), dp(PAGE_H_DP), dp(PAGE_V_DP)) }
        val keepY = if (screens.current === shownScreen) pageScroll?.scrollY else null
        signalStage().takeIf { it > 0 }?.let { page.addView(signalCard(it)) }
        renderScreen(page, screens.current)
        val scroll = ScrollView(this).apply { addView(page) }
        row.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(row)
        pageScroll = scroll
        shownScreen = screens.current
        parkingCard?.let { root.addView(parkingOverlay(it), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }
        // 같은 화면을 다시 그릴 때(카드 펼침 등)는 스크롤 자리를 그대로
        if (keepY != null) scroll.post { scroll.scrollTo(0, keepY) }
    }

    /** 왼쪽 사이드바: 흰 바탕, 메뉴 5개 + 맨 아래 설정. 지금 메뉴만 연파랑 알약 (부록 B-1 폭 96dp, F-4 ①) */
    private fun sidebar(): LinearLayout = column().apply {
        setBackgroundColor(SIDEBAR)
        val cell = resources.displayMetrics.heightPixels / 7
        setPadding(0, dp(8), 0, dp(8))
        val current = screens.current.menu
        SchoolMenu.values().filter { it != SchoolMenu.SETTINGS }.forEach { m -> addView(menuItem(m, m == current, cell)) }
        addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(menuItem(SchoolMenu.SETTINGS, current == SchoolMenu.SETTINGS, cell, small = true))
    }

    private fun menuItem(m: SchoolMenu, selected: Boolean, maxHeight: Int, small: Boolean = false): LinearLayout = column().apply {
        gravity = Gravity.CENTER
        val color = if (selected) PRIMARY else MUTED
        addView(text(m.icon, if (small) 24f else 30f, color, center = true, lines = 1, scale = false))
        // 미션에 남은 목표가 있으면 빨간 점, 시연 모드면 설정 아래 "시연" (부록 B-9)
        val label = when {
            m == SchoolMenu.MISSION && missionLeft() -> "${m.label} ●"
            else -> m.label
        }
        addView(text(label, if (small) 16f else 18f, if (m == SchoolMenu.MISSION && missionLeft() && !selected) BAD else color,
            bold = selected, center = true, lines = 1,
            // 판단: 사이드바 폭 96dp에 "기록 ●"이 한 줄로 들어가야 해서 이름만 24sp 예외 (아이콘 30sp가 주 표시)
            minSp = 0f, scale = false))
        if (m == SchoolMenu.SETTINGS && progress.demoMode) addView(text("시연", 14f, PRIMARY, bold = true, center = true, lines = 1, minSp = 0f, scale = false))
        if (selected) background = rounded(PRIMARY_SOFT, 20)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, minOf(maxHeight, dp(if (small) 96 else 104)))
            .apply { setMargins(dp(6), dp(2), dp(6), dp(2)) }
        // 지금 메뉴를 다시 눌러도 그 메뉴 첫 화면으로 (부록 B-1)
        setOnClickListener {
            screens.open(m)
            openNode = null
            justFinished = false
            render()
        }
    }

    /** 오늘의 목표가 남았는지 (사이드바 미션 점) */
    private fun missionLeft(): Boolean = progress.goals().any { !it.done }

    /** 깊은 화면(L2~) 위쪽의 [←] 와 제목 */
    internal fun header(page: LinearLayout, title: String) {
        val row = row()
        if (!isHome()) {
            row.addView(text(if (screens.current is SchoolScreen.Top || screens.size == 2) "← 홈" else "←", 34f, TEXT, bold = true).apply {
                setPadding(0, 0, dp(20), 0)
                setOnClickListener { goBack() }
            })
        }
        row.addView(text(title, 34f, TEXT, bold = true, lines = 1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(16) })
    }

    /** 한 칸 뒤로. 나갈 곳이 없으면 false */
    internal fun goBack(): Boolean {
        // 배우기 첫 화면에서 말풍선이 열려 있으면 그것부터 닫는다
        if (openNode != null && screens.current is SchoolScreen.Top) {
            openNode = null
            render()
            return true
        }
        if (!screens.back()) return false
        if (screens.current !is SchoolScreen.Report && screens.current !is SchoolScreen.Celebrate) justFinished = false
        render()
        return true
    }

    /** 지금 홈(운전 카드 WebView)인지. 배우기 첫 화면 자리를 홈으로 쓴다 */
    private fun isHome(): Boolean = screens.current.let { it is SchoolScreen.Top && it.menu == SchoolMenu.LEARN }

    /** 홈(WebView)에서 앱 화면 열기. 뒤로 가면 바로 홈 */
    internal fun openFromWeb(key: String) {
        if (driving || !isHome()) return
        screens.open(SchoolMenu.LEARN)
        when (key) {
            "report" -> screens.push(if (progress.trips > 0) SchoolScreen.Report(null) else SchoolScreen.SampleReport)
            "record" -> screens.push(SchoolScreen.Top(SchoolMenu.RECORD))
            "settings" -> screens.push(SchoolScreen.Top(SchoolMenu.SETTINGS))
            else -> return
        }
        render()
    }

    /** 메뉴를 바꿔 그 첫 화면으로 */
    internal fun openMenu(m: SchoolMenu) {
        screens.open(m)
        openNode = null
        justFinished = false
        render()
    }

    /** 화면을 한 칸 쌓고 그린다 */
    internal fun push(s: SchoolScreen) {
        screens.push(s)
        render()
    }

    private fun renderScreen(page: LinearLayout, s: SchoolScreen) {
        when (s) {
            is SchoolScreen.Top -> when (s.menu) {
                SchoolMenu.LEARN -> renderLearn(page)
                SchoolMenu.MISSION -> renderMission(page)
                SchoolMenu.RECORD -> renderRecord(page)
                SchoolMenu.BADGE -> renderBadges(page)
                SchoolMenu.MY -> renderMy(page)
                SchoolMenu.SETTINGS -> renderSettings(page)
            }
            is SchoolScreen.Guide -> renderGuide(page, s.unit)
            is SchoolScreen.Preview -> renderPreview(page, s.skill, s.page)
            is SchoolScreen.Report -> renderReport(page, s)
            SchoolScreen.SampleReport -> renderSampleReport(page)
            is SchoolScreen.ReportItem -> renderReportItem(page, s)
            is SchoolScreen.CoachCard -> renderCoachCard(page, s)
            SchoolScreen.Stats -> renderStats(page)
            is SchoolScreen.Celebrate -> renderCelebrate(page, s.index)
            SchoolScreen.Streak -> renderStreak(page)
            is SchoolScreen.WeekTrips -> renderWeekTrips(page, s.week)
            is SchoolScreen.BadgeDetail -> renderBadgeDetail(page, s.key)
            SchoolScreen.Certificate -> renderCertificate(page)
            SchoolScreen.Profile -> renderProfile(page)
            SchoolScreen.Versions -> renderVersions(page)
            is SchoolScreen.Basis -> renderBasis(page, s.highlight)
            SchoolScreen.Info -> renderInfo(page)
        }
    }

    /**
     * 공유: Android 공유 창. 받을 앱이 없으면 문구를 창에 보여 주고 [복사] (부록 B-10)
     */
    internal fun share(body: String) {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "공유"
        )
        val canShare = Intent(Intent.ACTION_SEND).setType("text/plain").resolveActivity(packageManager) != null
        if (canShare) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                log("공유 실패", e)
            }
        }
        // 차량 OS에는 공유 앱이 없을 수 있다
        dialog = AlertDialog.Builder(this)
            .setTitle("공유 문구")
            .setMessage(body)
            .setPositiveButton("복사") { _, _ ->
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("초보 탈출 학교", body))
                Toast.makeText(this, "복사했어요", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    internal fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    internal fun log(message: String, e: Exception? = null) {
        // VehicleException이면 message에 오류 코드 이름이 들어 있다
        if (e != null) Log.w(TAG, "$message → ${e.message ?: e}", e) else Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "SchoolActivity"
        const val COACH_SHOW_MS = 4_000L
        const val SIGNAL_SHOW_MS = 1_000L
        const val TURN_EDGE_KM = 0.1f
        const val RIGHT_RED_M = 50.0
        /** 판단: 시연 모드에서 1분당 값(잔 조향·정체)을 내는 최소 시간 */
        const val DEMO_MINUTE_SEC = 10
        const val SIGNAL_GIVE_UP_MS = 10_000L
        const val EXTRA_NO_SIGNAL = "no_signal"
        const val SIDEBAR_DP = 96
        /** 이보다 짧은 주행은 기록하지 않는다 (0.18.0, 약 10m. float 오차로 딱 10m가 빠지지 않게 9m) */
        const val MIN_TRIP_KM = 0.009f
        /** 0.18.0: 사이드바 숨김. true로 바꾸면 예전 메뉴가 돌아온다 */
        const val SHOW_SIDEBAR = false
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val REQ_LOCATION = 1
    }
}
