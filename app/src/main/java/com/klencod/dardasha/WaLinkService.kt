package com.klencod.dardasha

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.klencod.dardasha.data.WaLink

/**
 * خدمة أمامية (بإشعار ثابت) تُبقي اتصال أرقام واتساب المرتبطة حياً. بدونها يقتل أندرويد التطبيق في الخلفية
 * فينقطع الاتصال.
 */
class WaLinkService : Service() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "اتصال واتساب", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 9, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("دردشة")
            .setContentText("واتساب متصل")
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 4101, n, type)
        WaLink.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "wa_link"

        /** يشغّل الخدمة إن كان هناك رقم مرتبط (أو [force] عند بدء ربط جديد). يُستدعى والتطبيق ظاهر. */
        fun ensure(ctx: Context, force: Boolean = false) {
            if (!force && !WaLink.isLinked(ctx)) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, WaLinkService::class.java)) }
        }
    }
}
