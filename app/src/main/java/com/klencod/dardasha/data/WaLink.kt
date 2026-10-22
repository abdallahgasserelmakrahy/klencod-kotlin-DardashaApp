package com.klencod.dardasha.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors

/** حالة رقم واتساب مرتبط (أو قيد الربط) كجهاز مصاحب. */
class LinkAcc(val id: String) {
    /** connecting | qr | paircode | connected | disconnected | error */
    var status by mutableStateOf("connecting")
    var info by mutableStateOf("")
    var qr by mutableStateOf<String?>(null)
    var pairCode by mutableStateOf<String?>(null)
    val pending get() = id.startsWith("pending-")
}

/**
 * ربط أرقام واتساب كأجهزة مصاحبة عبر مكتبة whatsmeow (حزمة `wabridge` المبنية بـ gomobile).
 * كل الرسائل تصل إلى [WaInbox] فتظهر في شاشة واتساب مع رسائل الإشعارات.
 */
object WaLink {
    val accounts = mutableStateListOf<LinkAcc>()

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private const val PREFS = "dardasha_walink"
    private const val IDS = "ids"
    @Volatile private var started = false
    private var app: Context? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** هل يوجد رقم مرتبط على هذا الجهاز؟ (يُقرأ من التخزين فيعمل حتى قبل تشغيل المحرك). */
    fun isLinked(ctx: Context): Boolean = prefs(ctx).getStringSet(IDS, emptySet()).orEmpty().isNotEmpty()

    private fun saveId(ctx: Context, id: String, add: Boolean) {
        val s = prefs(ctx).getStringSet(IDS, emptySet()).orEmpty().toMutableSet()
        if (add) s.add(id) else s.remove(id)
        prefs(ctx).edit().putStringSet(IDS, s).apply()
    }

    private fun toast(msg: String) {
        val c = app ?: return
        ui.post { Toast.makeText(c, msg, Toast.LENGTH_LONG).show() }
    }

    private fun upsert(id: String): LinkAcc = accounts.firstOrNull { it.id == id } ?: LinkAcc(id).also { accounts.add(it) }

    // الجسر (wabridge.aar) يُستدعى بالانعكاس كي يُبنى التطبيق حتى لو لم يُبنَ الجسر؛ عندها تُعطَّل ميزة الربط فقط.
    private val bridge: Class<*>? by lazy { runCatching { Class.forName("wabridge.Wabridge") }.getOrNull() }

    /** هل مكتبة الربط مضمّنة في هذه النسخة؟ */
    fun available(): Boolean = bridge != null

    private fun call(name: String, vararg args: Any?): Any? {
        val c = bridge ?: throw IllegalStateException("مكتبة الربط غير مضمّنة في هذه النسخة")
        val m = c.methods.first { it.name == name && it.parameterTypes.size == args.size }
        try {
            return m.invoke(null, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun makeListener(): Any {
        val li = Class.forName("wabridge.Listener")
        return java.lang.reflect.Proxy.newProxyInstance(li.classLoader, arrayOf(li)) { proxy, method, a ->
            val args = a ?: emptyArray()
            when (method.name) {
                "onQR" -> { onQR(args[0] as String?, args[1] as String?); null }
                "onPairCode" -> { onPairCode(args[0] as String?, args[1] as String?); null }
                "onStatus" -> { onStatus(args[0] as String?, args[1] as String?, args[2] as String?); null }
                "onMessage" -> {
                    onMessage(
                        args[0] as String?, args[1] as String?, args[2] as String?, args[3] as String?,
                        args[4] as Boolean, (args[5] as Number).toLong(), (args[6] as Number).toLong(),
                        args[7] as String?, args[8] as String?, args[9] as String?
                    )
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                "toString" -> "WaLinkListener"
                else -> null
            }
        }
    }

    private fun onQR(accountID: String?, code: String?) {
        val id = accountID ?: return
        ui.post { upsert(id).apply { qr = code; pairCode = null; status = "qr" } }
    }

    private fun onPairCode(accountID: String?, code: String?) {
        val id = accountID ?: return
        ui.post { upsert(id).apply { pairCode = code; qr = null; status = "paircode" } }
    }

    private fun onStatus(accountID: String?, status: String?, info: String?) {
        val id = accountID ?: return
        val st = status ?: return
        val c = app ?: return
        ui.post {
            when (st) {
                "paired" -> {
                    accounts.removeAll { it.id == id }
                    val real = info.orEmpty()
                    if (real.isNotBlank()) {
                        upsert(real).apply { this.status = "connected"; qr = null; pairCode = null }
                        saveId(c, real, true)
                    }
                }
                "logged_out" -> {
                    accounts.removeAll { it.id == id }
                    saveId(c, id, false)
                }
                else -> upsert(id).apply {
                    this.status = st
                    this.info = info.orEmpty()
                    if (st == "connected") { qr = null; pairCode = null }
                    if (!pending && st != "error") saveId(c, id, true)
                }
            }
        }
    }

    private fun onMessage(
        accountID: String?, chatJID: String?, chatName: String?, sender: String?, fromMe: Boolean,
        tsMillis: Long, kind: Long, text: String?, mediaPath: String?, msgID: String?
    ) {
        val acc = accountID ?: return
        val chat = chatJID ?: return
        val c = app ?: return
        val msg = WaMsg(
            text.orEmpty(), tsMillis, fromMe, if (fromMe) "" else sender.orEmpty(),
            kind.toInt(), mediaPath?.takeIf { it.isNotBlank() }
        )
        ui.post { WaInbox.ingest(c, "link:$acc", chat, chatName.orEmpty().ifBlank { chat.substringBefore('@') }, msg) }
    }

    /** يشغّل المحرك ويعيد توصيل الأرقام المرتبطة سابقاً. آمن للاستدعاء المتكرر. */
    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        app = ctx.applicationContext
        if (!available()) return
        val dir = File(ctx.filesDir, "walink").apply { mkdirs() }
        try {
            call("start", dir.absolutePath, makeListener())
            started = true
        } catch (e: Throwable) {
            toast("تعذّر تشغيل الربط: ${e.message}")
        }
    }

    /** يبدأ ربط رقم جديد: [phone] فارغ → QR؛ وإلا رقم دولي (أرقام فقط) → رمز من 8 خانات. */
    fun add(ctx: Context, phone: String) {
        start(ctx)
        io.execute {
            try {
                val id = call("addAccount", phone) as String
                ui.post { upsert(id) }
            } catch (e: Throwable) {
                toast("تعذّر بدء الربط: ${e.message}")
            }
        }
    }

    fun remove(ctx: Context, id: String) {
        accounts.removeAll { it.id == id }
        saveId(ctx, id, false)
        io.execute { runCatching { call("remove", id) } }
    }

    /** إرسال نص إلى محادثة؛ يعمل في خيط خلفي ويعرض رسالة عند الفشل. */
    fun send(accountId: String, chatJid: String, text: String) {
        io.execute {
            try {
                call("send", accountId, chatJid, text)
            } catch (e: Throwable) {
                toast("تعذّر الإرسال: ${e.message}")
            }
        }
    }
}
