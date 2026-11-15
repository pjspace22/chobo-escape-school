package com.example.pleosstarter.school

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 내비 위 코치 배너 (plan-school-duo-screens.md 부록 D, 현규 확정 11/4).
 *
 * 주행 중 코치가 나올 때 우리 앱이 화면 앞에 없으면(내비 등이 앞) 알림 배너로 한 줄 띄운다.
 * - `IMPORTANCE_HIGH` + `CATEGORY_MESSAGE` (실측: 다른 category는 차 화면에 안 뜸)
 * - 9초 뒤 앱이 직접 지운다 (뜨는 데 걸리는 시간 때문에 실제로는 약 8초 보임, 부록 H-⑤). 같은 알림 ID로 내용만 바꿔 쌓이지 않게
 * - 제목 앞에 코치 종류 아이콘(⚠ 고칠 것 / 💡 상황 안내)을 붙여 색만으로 구분하지 않는다 (부록 H-⑤)
 * - 누르면 우리 앱으로 (기어에 따라 주행 화면 또는 성적표)
 * - POST_NOTIFICATIONS를 거절했으면 아무것도 안 하고 음성만 남는다
 */
class CoachBanner(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable { manager.cancel(NOTIFICATION_ID) }

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주행 중 코치", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "다른 앱(내비 등)을 보고 있을 때 코치 한 줄을 잠깐 띄워요"
                setSound(null, null) // 음성 코치(TTS)가 따로 있어 알림 소리는 끈다
                enableVibration(false)
            }
        )
    }

    /** 알림을 띄울 수 있는지 (Android 13 이상은 실행 중 허락이 필요) */
    val allowed: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** @param icon 코치 종류 아이콘. 판단: 알림 작은 아이콘은 그림 파일만 돼서 제목 앞 이모지로 붙인다 */
    fun show(message: String, icon: String? = null) {
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, SchoolActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (icon != null) "$icon $message" else message)
            .setContentText("초보 탈출 학교 · 코치")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        manager.notify(NOTIFICATION_ID, n)
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, SHOW_MS)
    }

    fun clear() {
        handler.removeCallbacks(hide)
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val PERMISSION = "android.permission.POST_NOTIFICATIONS"
        private const val CHANNEL_ID = "school_coach"
        private const val NOTIFICATION_ID = 7001
        /** 배너를 지우기까지 (부록 E-3 6초 → 부록 H-⑤ 9초, 실제 약 8초. 운전자는 2초씩 끊어 보므로 한 번 놓쳐도 다시 볼 수 있게) */
        const val SHOW_MS = 9_000L
    }
}
