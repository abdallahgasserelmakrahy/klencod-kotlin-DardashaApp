package com.klencod.dardasha

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klencod.dardasha.data.Push

/**
 * يستقبل إشعارات FCM حتى والتطبيق مغلق.
 * الرسائل من نوع data فقط (بدون notification) ليُستدعى onMessageReceived دائماً،
 * ونبني الإشعار بأنفسنا ليفتح المحادثة المناسبة عند الضغط.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.register(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val sender = d["sender"] ?: return
        val receiver = d["receiver"]
        // والتطبيق مفتوح يتولّى الاتصال اللحظي (Realtime) عرض رسائل الحساب النشط؛ نتجنّب التكرار.
        // أما رسائل حساب آخر محفوظ على الجهاز فلا يسمعها الـ Realtime، فنعرضها دائماً.
        if (AppState.foreground && (receiver == null || receiver == AppState.activeUserId)) return
        Notifier.show(this, sender, d["title"] ?: "رسالة جديدة", d["body"] ?: "", receiver)
    }
}
