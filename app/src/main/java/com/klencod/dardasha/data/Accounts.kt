package com.klencod.dardasha.data

import android.content.Context
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * حساب محفوظ على هذا الجهاز (للتنقّل بين أكثر من حساب مثل فيسبوك).
 * [session] هي جلسة Supabase كاملة (JSON) نستعيدها عند التبديل دون طلب كلمة المرور.
 */
@Serializable
data class SavedAccount(
    val id: String,
    val email: String = "",
    val username: String = "",
    val displayName: String? = null,
    val avatarPath: String? = null,
    val session: String = "",
) {
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: username.ifBlank { email.ifBlank { "حساب" } }
    val handle: String get() = if (username.isNotBlank()) "@$username" else email
}

/** تخزين الحسابات في SharedPreferences (نفس مكان تخزين مكتبة Supabase لجلسة الحساب النشط). */
object Accounts {
    private const val FILE = "dardasha_accounts"
    private const val KEY = "list"
    private val SER = ListSerializer(SavedAccount.serializer())

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<SavedAccount> =
        runCatching { sp(ctx).getString(KEY, null)?.let { AppJson.decodeFromString(SER, it) } }
            .getOrNull().orEmpty()

    @Synchronized
    private fun write(ctx: Context, l: List<SavedAccount>) {
        sp(ctx).edit().putString(KEY, AppJson.encodeToString(SER, l)).apply()
    }

    fun get(ctx: Context, id: String): SavedAccount? = all(ctx).firstOrNull { it.id == id }

    /** يحفظ/يحدّث جلسة الحساب (يُستدعى عند كل تسجيل دخول أو تجديد للتوكن، لأن refresh token يتبدّل). */
    @Synchronized
    fun saveSession(ctx: Context, s: UserSession) {
        val u = s.user ?: return
        val js = AppJson.encodeToString(UserSession.serializer(), s)
        val l = all(ctx).toMutableList()
        val i = l.indexOfFirst { it.id == u.id }
        val old = l.getOrNull(i)
        val acc = (old ?: SavedAccount(id = u.id)).copy(
            email = u.email ?: old?.email.orEmpty(),
            session = js,
        )
        if (i >= 0) l[i] = acc else l.add(acc)
        write(ctx, l)
    }

    fun session(ctx: Context, id: String): UserSession? =
        get(ctx, id)?.session?.takeIf { it.isNotBlank() }?.let {
            runCatching { AppJson.decodeFromString(UserSession.serializer(), it) }.getOrNull()
        }

    /** يحدّث الاسم/الصورة المعروضة في قائمة الحسابات. */
    @Synchronized
    fun updateProfile(ctx: Context, p: Profile) {
        val l = all(ctx).toMutableList()
        val i = l.indexOfFirst { it.id == p.id }
        if (i < 0) return
        val n = l[i].copy(username = p.username, displayName = p.displayName, avatarPath = p.avatarPath)
        if (n != l[i]) { l[i] = n; write(ctx, l) }
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        write(ctx, all(ctx).filterNot { it.id == id })
    }
}
