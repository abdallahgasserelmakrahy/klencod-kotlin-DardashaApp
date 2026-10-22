package com.klencod.dardasha.data

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** تسجيل توكن FCM لدى Supabase. كل شيء داخل runCatching: التطبيق يعمل حتى لو لم يُضبط Firebase بعد. */
object Push {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val KEY = "fcm_token"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("dardasha", 0)

    /** يجلب التوكن الحالي ويسجّله للمستخدم المسجَّل دخوله. */
    fun sync(ctx: Context) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) register(ctx, t) }
        }
    }

    fun register(ctx: Context, token: String) {
        prefs(ctx).edit().putString(KEY, token).apply()
        scope.launch {
            runCatching {
                Supa.client.auth.awaitInitialization()
                if (Supa.client.auth.currentUserOrNull() != null) Repo.registerPushToken(token)
            }
        }
    }

    /** يُستدعى قبل تسجيل الخروج (والمستخدم ما زال مسجَّلاً) كي لا تصل إشعارات الحساب القديم. */
    suspend fun unregister(ctx: Context) {
        val t = prefs(ctx).getString(KEY, null) ?: return
        runCatching { Repo.unregisterPushToken(t) }
    }
}
