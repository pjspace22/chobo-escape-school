package com.example.pleosstarter.school

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 내비를 앞에 띄워도 앱이 얼지 않게 붙잡아 두는 포그라운드 서비스 (현규 확정 11/3, PM 전달).
 *
 * Android 14는 화면 뒤로 간 앱을 약 10초 뒤 얼린다(isFrozen) → 차량 콜백·코치·배너·주행 기록이 멈춘다.
 * 이 서비스가 떠 있으면 프로세스가 "포그라운드 서비스" 등급이라 얼지 않는다. 하는 일은 없고 붙잡기만 한다.
 * - 차량 콜백은 지금처럼 SchoolActivity가 받는다 (구조는 그대로)
 * - 앱이 앞에 있을 때(onCreate) 켜고, 앱이 끝날 때(onDestroy) 끈다. 뒤에서 켜는 건 Android가 막는다
 * - 종류는 specialUse: location 종류는 표준 위치 퍼미션이 필요한데 우리는 Pleos FUSED_LOCATION만 있어서
 */
class SchoolKeepAlive : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주행 코치 켜 둠", NotificationManager.IMPORTANCE_LOW).apply {
                description = "내비를 보고 있어도 주행 기록·코치가 멈추지 않게 앱을 켜 둬요"
                setSound(null, null)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, SchoolActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("초보 탈출 학교")
            .setContentText("주행 코치가 켜져 있어요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "school_keep_alive"
        private const val NOTIFICATION_ID = 7002

        /** 앱이 앞에 있을 때만 부른다. 실패해도(퍼미션 없음 등) 앱은 그대로 돈다 → 뒤에서는 예전처럼 얼 뿐 */
        fun start(context: Context, log: (String) -> Unit) {
            runCatching { context.startForegroundService(Intent(context, SchoolKeepAlive::class.java)) }
                .onFailure { log("포그라운드 서비스 시작 실패 → 뒤로 가면 얼 수 있음: ${it.message}") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SchoolKeepAlive::class.java))
        }
    }
}
