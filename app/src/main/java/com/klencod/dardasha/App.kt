package com.klencod.dardasha

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.klencod.dardasha.data.AppSettings

object AppState {
    @Volatile var foreground = false
    /** الحساب النشط حالياً (لمعرفة هل الإشعار القادم يخصّ حساباً آخر محفوظاً على الجهاز). */
    @Volatile var activeUserId: String? = null
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.klencod.dardasha.data.AppSettings.init(this)
        com.klencod.dardasha.data.Media.init(this)
        com.klencod.dardasha.data.Keep.init(this)
        // تهيئة عميل Supabase في خيط خلفي كي لا تُعطّل أول شاشة
        Thread { runCatching { com.klencod.dardasha.data.Supa.client } }.start()
        // القناة القديمة الثابتة استُبدلت بقنوات حسب إعدادات الصوت/الاهتزاز (انظر Notifier.channelId)
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(Notifier.CHANNEL)
        Notifier.channelId(this)
    }
}

object Notifier {
    const val CHANNEL = "messages"

    /**
     * قناة الإشعارات حسب خياري الصوت والاهتزاز (إعدادات أندرويد لا تسمح بتعديل القناة بعد إنشائها،
     * لذلك نُنشئ قناة لكل تركيبة عند الحاجة).
     */
    fun channelId(ctx: Context): String {
        val sound = AppSettings.notifSound
        val vib = AppSettings.notifVibrate
        val id = "messages_s${if (sound) 1 else 0}_v${if (vib) 1 else 0}"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "الرسائل", NotificationManager.IMPORTANCE_HIGH)
            if (!sound) ch.setSound(null, null)
            ch.enableVibration(vib)
            nm.createNotificationChannel(ch)
        }
        return id
    }
    const val EXTRA_PEER = "peer"
    const val EXTRA_ACCOUNT = "account"

    /** إشعار تجريبي من الإعدادات (يستخدم نفس القناة وإعدادات الصوت/الاهتزاز، ولا يفتح محادثة). */
    fun test(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val pi = PendingIntent.getActivity(
            ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channelId(ctx))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("دردشة")
            .setContentText("هذا إشعار تجريبي ✅")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(7, n) }
    }

    /** [accountId] = الحساب المستلِم؛ عند الضغط يبدّل التطبيق إليه ثم يفتح المحادثة. */
    fun show(ctx: Context, peerId: String, title: String, text: String, accountId: String? = null) {
        AppSettings.init(ctx)
        if (!AppSettings.notifications) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PEER, peerId)
            .apply { if (accountId != null) putExtra(EXTRA_ACCOUNT, accountId) }
        val nid = (accountId.orEmpty() + peerId).hashCode()
        val pi = PendingIntent.getActivity(
            ctx, nid, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channelId(ctx))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(if (AppSettings.notifPreview) text else "رسالة جديدة")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(nid, n) }
    }
}
