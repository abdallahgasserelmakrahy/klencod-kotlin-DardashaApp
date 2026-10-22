package com.klencod.dardasha

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.data.WaApps
import com.klencod.dardasha.data.WaInbox

/** يلتقط إشعارات واتساب وواتساب للأعمال على هذا الجهاز فقط، وبعد أن يفعّل المستخدم الميزة ويمنح الإذن. */
class WaListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        AppSettings.init(applicationContext)
        if (!AppSettings.waInbox || sbn.packageName !in WaApps.packages) return
        runCatching { WaInbox.handle(applicationContext, sbn) }
    }
}
