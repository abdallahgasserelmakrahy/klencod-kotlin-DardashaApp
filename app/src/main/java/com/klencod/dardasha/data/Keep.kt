package com.klencod.dardasha.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * مخزن «منع الحذف»: نسخ محلية دائمة (filesDir، لا يمسحها «مسح الملفات المؤقتة») لوسائط الرسائل الواردة
 * (صور، صوت، ملفات) تُنزَّل فور وصول الرسالة، لتبقى متاحة حتى لو حذفها المرسِل من الخادم.
 */
object Keep {
    private const val MAX_BYTES = 25L * 1024 * 1024
    private var dir: File? = null
    private val busy = HashSet<String>()

    fun init(ctx: Context) {
        dir = File(ctx.applicationContext.filesDir, "keep").apply { mkdirs() }
    }

    private fun f(path: String): File? = dir?.let { File(it, path.hashCode().toUInt().toString(16) + "_" + path.length) }

    fun file(path: String): File? = f(path)?.takeIf { it.exists() && it.length() > 0 }
    fun has(path: String): Boolean = file(path) != null

    /** ينزّل [path] من [bucket] ويحفظه إن لم يكن محفوظاً. يتجاهل الملفات الكبيرة جداً. */
    suspend fun fetch(bucket: String, path: String) {
        val target = f(path) ?: return
        synchronized(busy) { if (has(path) || !busy.add(path)) return }
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val b = Repo.download(bucket, path)
                    if (b.isNotEmpty() && b.size <= MAX_BYTES) {
                        val tmp = File(target.parentFile, target.name + ".tmp")
                        tmp.writeBytes(b)
                        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                    }
                }
            }
        } finally {
            synchronized(busy) { busy.remove(path) }
        }
    }

    fun size(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearAll() {
        runCatching { dir?.listFiles()?.forEach { it.delete() } }
    }
}
