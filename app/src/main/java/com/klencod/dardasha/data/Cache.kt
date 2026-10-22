package com.klencod.dardasha.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

/**
 * نسخة محلية من بيانات الحساب (رسائل، أشخاص، تفاعلات...) تُحفظ على الجهاز.
 * بفضلها تُعرض القائمة والمحادثات فوراً عند فتح التطبيق (مثل واتساب) ثم تتحدّث من الإنترنت بصمت.
 */
@Serializable
data class CacheSnapshot(
    val msgs: List<Message> = emptyList(),
    val peers: List<Profile> = emptyList(),
    val nicks: Map<String, String> = emptyMap(),
    val reactions: List<Reaction> = emptyList(),
    val blocked: List<String> = emptyList(),
)

object Cache {
    const val MAX_MSGS = 1000

    private fun file(ctx: Context, id: String) = File(ctx.applicationContext.filesDir, "cache_$id.json")

    fun load(ctx: Context, id: String): CacheSnapshot? = runCatching {
        val f = file(ctx, id)
        if (!f.exists()) null else AppJson.decodeFromString(CacheSnapshot.serializer(), f.readText())
    }.getOrNull()

    fun save(ctx: Context, id: String, s: CacheSnapshot) {
        runCatching {
            val f = file(ctx, id)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(CacheSnapshot.serializer(), s))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    fun delete(ctx: Context, id: String) {
        runCatching { file(ctx, id).delete() }
    }
}
