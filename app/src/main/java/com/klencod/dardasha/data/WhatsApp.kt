package com.klencod.dardasha.data

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * ربط آمن مع واتساب (العادي وواتساب للأعمال) بدون أي مكتبات غير رسمية:
 *  1) مشاركة نص إلى واتساب عبر نظام المشاركة في أندرويد.
 *  2) صندوق محلي يلتقط إشعارات واتساب على هذا الجهاز ويسمح بالرد السريع عليها.
 * لا يُرسَل شيء إلى أي سيرفر؛ كل البيانات تبقى على الجهاز ويمكن مسحها.
 */
object WaApps {
    const val WA = "com.whatsapp"
    const val BIZ = "com.whatsapp.w4b"
    val packages = listOf(WA, BIZ)

    fun label(pkg: String) = when {
        pkg == BIZ -> "واتساب للأعمال"
        pkg.startsWith("link:") -> "+" + pkg.removePrefix("link:")
        else -> "واتساب"
    }

    fun installed(ctx: Context): List<String> = packages.filter {
        runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess
    }
}

object WaShare {
    /** يفتح واتساب (أو واتساب للأعمال، أو الاثنين للاختيار) لإرسال [text]. المستخدم يضغط «إرسال» بنفسه. */
    fun sendText(ctx: Context, text: String): Boolean {
        val apps = WaApps.installed(ctx)
        if (apps.isEmpty() || text.isBlank()) return false
        fun intentFor(pkg: String) = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg)
        return runCatching {
            if (apps.size == 1) {
                ctx.startActivity(intentFor(apps[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                val chooser = Intent.createChooser(intentFor(apps[0]), "إرسال عبر")
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(intentFor(apps[1])))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(chooser)
            }
        }.isSuccess
    }
}

/** نوع الرسالة القادمة من واتساب. */
object WaKind {
    const val TEXT = 0
    const val IMAGE = 1
    const val AUDIO = 2
    const val VIDEO = 3
    const val FILE = 4

    private val IMG = Regex("^\\s*(📷|🖼️?|🌄)|^\\s*(photo|image|sticker|gif|صورة|ملصق)(\\s.*)?$", RegexOption.IGNORE_CASE)
    private val AUD = Regex("^\\s*(🎤|🎙️?|🎵|🎧)|^\\s*(voice message|audio|رسالة صوتية|مقطع صوتي|ملاحظة صوتية)(\\s.*)?$", RegexOption.IGNORE_CASE)
    private val VID = Regex("^\\s*(🎥|📹|🎬)|^\\s*(video|فيديو|مقطع فيديو)(\\s.*)?$", RegexOption.IGNORE_CASE)
    private val DOC = Regex("^\\s*(📄|📎|📁)|^\\s*(document|مستند|ملف)(\\s.*)?$", RegexOption.IGNORE_CASE)
    private val MARK = Regex("^\\s*(📷|🖼️?|🌄|🎤|🎙️?|🎵|🎧|🎥|📹|🎬|📄|📎|📁)\\s*(photo|image|sticker|gif|voice message|audio|video|document|صورة|ملصق|رسالة صوتية|مقطع صوتي|ملاحظة صوتية|فيديو|مقطع فيديو|مستند|ملف)?\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE)

    /** يحدّد النوع من نص الإشعار («📷 صورة»، «🎤 رسالة صوتية»…) أو من نوع الملف المرفق إن وُجد. */
    fun of(text: String, mime: String?): Int {
        val mm = mime?.lowercase().orEmpty()
        return when {
            mm.startsWith("image/") -> IMAGE
            mm.startsWith("audio/") -> AUDIO
            mm.startsWith("video/") -> VIDEO
            text.length > 80 -> TEXT
            IMG.containsMatchIn(text) -> IMAGE
            AUD.containsMatchIn(text) -> AUDIO
            VID.containsMatchIn(text) -> VIDEO
            DOC.containsMatchIn(text) -> FILE
            else -> TEXT
        }
    }

    /** نص الرسالة بعد إزالة علامة النوع (تعليق الصورة مثلاً). */
    fun caption(text: String): String = text.replaceFirst(MARK, "").trim()
}

class WaMsg(
    val text: String, val time: Long, val fromMe: Boolean, val sender: String = "",
    val kind: Int = WaKind.TEXT, path: String? = null,
) {
    /** مسار الملف المحفوظ محلياً (صورة/صوت/فيديو) إن تمكّن التطبيق من التقاطه من الإشعار. */
    var path by mutableStateOf(path)
}

class WaConv(val key: String, val pkg: String, title: String) {
    /** معرّف المحادثة في واتساب (JID) إن وُجد؛ به نجمع رسائل الشخص وردودك في محادثة واحدة. */
    var chatId: String? = null
    var title by mutableStateOf(title)
    var lastTime by mutableLongStateOf(0L)
    var canReply by mutableStateOf(false)
    val msgs = mutableStateListOf<WaMsg>()
}

object WaInbox {
    private const val PREFS = "dardasha_wa"
    private const val KEY = "convs_v1"
    private const val MAX_CONVS = 60
    private const val MAX_MSGS = 120

    val convs = mutableStateListOf<WaConv>()
    private var loaded = false

    /** إجراءات الرد (PendingIntent) تُحفظ في الذاكرة فقط ولا تُخزَّن على القرص. */
    private val replies = HashMap<String, Pair<Notification.Action, RemoteInput>>()

    fun find(key: String) = convs.firstOrNull { it.key == key }

    /** أسماء تعني «أنت» في إشعارات واتساب (ردودك)، وأسماء التطبيق نفسه، ونصوص «جارٍ التحقق من رسائل جديدة». */
    private val SELF = setOf("أنت", "انت", "أنا", "you", "me")
    private val APP_TITLES = setOf("whatsapp", "whatsapp business", "واتساب", "واتساب للأعمال", "واتساب بيزنس", "واتساب بزنس")
    private val JUNK_TEXT = Regex(
        "التحقق من (ال)?رسائل|جارٍ التحقق|جار التحقق|checking for new messages|\\d+ (new )?messages? (from|in)|\\d+ رسائل? (جديدة )?من",
        RegexOption.IGNORE_CASE
    )
    private fun norm(x: String?) = x?.trim()?.lowercase().orEmpty()
    private fun isSelfName(x: String?) = norm(x) in SELF
    private fun isJunk(title: String, text: String?) =
        norm(title) in APP_TITLES || norm(title) in SELF || (text != null && JUNK_TEXT.containsMatchIn(text))

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = WaConv(o.getString("key"), o.getString("pkg"), o.getString("title"))
                c.chatId = o.optString("cid").takeIf { it.isNotBlank() }
                c.lastTime = o.optLong("last")
                if (c.pkg.startsWith("link:")) c.canReply = true
                val ms = o.getJSONArray("msgs")
                for (j in 0 until ms.length()) {
                    val m = ms.getJSONObject(j)
                    val p = m.optString("p").takeIf { it.isNotBlank() && File(it).exists() }
                    c.msgs.add(WaMsg(m.getString("t"), m.getLong("ts"), m.getBoolean("me"), m.optString("s"), m.optInt("k"), p))
                }
                if (!isJunk(c.title, c.msgs.lastOrNull()?.text)) convs.add(c)
            }
        }
    }

    private fun save(ctx: Context) {
        runCatching {
            val arr = JSONArray()
            convs.forEach { c ->
                val ms = JSONArray()
                c.msgs.forEach { m ->
                    ms.put(JSONObject().put("t", m.text).put("ts", m.time).put("me", m.fromMe).put("s", m.sender).put("k", m.kind).put("p", m.path ?: ""))
                }
                arr.put(JSONObject().put("key", c.key).put("pkg", c.pkg).put("title", c.title).put("cid", c.chatId ?: "").put("last", c.lastTime).put("msgs", ms))
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        }
    }

    private fun mediaDir(ctx: Context) = File(ctx.filesDir, "wa_media")

    fun clearAll(ctx: Context) {
        convs.clear(); replies.clear()
        runCatching { mediaDir(ctx).deleteRecursively() }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun delete(ctx: Context, key: String) {
        convs.firstOrNull { it.key == key }?.msgs?.forEach { m -> m.path?.let { runCatching { File(it).delete() } } }
        convs.removeAll { it.key == key }; replies.remove(key); save(ctx)
    }

    /** معرّف المحادثة (JID) من وسم الإشعار أو اختصار المحادثة، إن توفّر. */
    private fun chatIdOf(sbn: StatusBarNotification, n: Notification): String? =
        listOfNotNull(sbn.tag, runCatching { n.shortcutId }.getOrNull()).firstOrNull { it.contains("@") }

    /** يُستدعى من [com.klencod.dardasha.WaListener] لكل إشعار من واتساب. */
    fun handle(ctx: Context, sbn: StatusBarNotification) {
        if (!AppSettings.waInbox) return
        if (WaLink.isLinked(ctx)) return // الأرقام المرتبطة تصل مباشرة؛ نتجنب التكرار مع الإشعارات
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val pkg = sbn.packageName
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val plainText = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (norm(title) in APP_TITLES) return // «واتساب: جارٍ التحقق من رسائل جديدة» وما شابه
        load(ctx)

        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        // إشعار بلا نمط محادثة يُقبل فقط إن كان من فئة الرسائل وليس إشعاراً نظامياً
        if (style == null && (n.category != Notification.CATEGORY_MESSAGE || JUNK_TEXT.containsMatchIn(plainText))) return

        val chatId = chatIdOf(sbn, n)
        val convTitle = style?.conversationTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val incomingName = style?.messages?.lastOrNull { m ->
            m.person != null && !isSelfName(m.person?.name?.toString())
        }?.person?.name?.toString()?.trim()
        // اسم المحادثة: عنوان المجموعة، أو عنوان الإشعار ما لم يكن «أنت»، أو اسم آخر مُرسِل وارد
        val name = convTitle ?: title.takeIf { it.isNotBlank() && !isSelfName(it) } ?: incomingName

        val incoming = ArrayList<WaMsg>()
        val uris = ArrayList<Uri?>()
        if (style != null && style.messages.isNotEmpty()) {
            val userKey = style.user.key
            style.messages.forEach { m ->
                val t = m.text?.toString()?.trim().orEmpty()
                if (t.isEmpty() || JUNK_TEXT.containsMatchIn(t) && m.person == null && style.messages.size == 1) return@forEach
                val pn = m.person?.name?.toString()
                val mine = m.person == null || isSelfName(pn) || (userKey != null && m.person?.key == userKey)
                incoming.add(WaMsg(t, m.timestamp, mine, if (mine) "" else pn.orEmpty(), WaKind.of(t, m.dataMimeType)))
                uris.add(if (mine) null else m.dataUri)
            }
        } else if (plainText.isNotEmpty() && !isSelfName(title)) {
            incoming.add(WaMsg(plainText, sbn.postTime, false, "", WaKind.of(plainText, null)))
            uris.add(null)
        }
        if (incoming.isEmpty()) return
        // صورة كبيرة مرفقة بالإشعار (BigPicture) تُنسب لآخر رسالة صورة
        val bigPic = runCatching { androidx.core.os.BundleCompat.getParcelable(ex, Notification.EXTRA_PICTURE, Bitmap::class.java) }.getOrNull()

        // إيجاد المحادثة: بمعرّف واتساب أولاً، ثم بالاسم. رد «أنت» بلا اسم يُنسب لآخر محادثة نشطة حديثاً.
        val now = System.currentTimeMillis()
        var conv = chatId?.let { id -> convs.firstOrNull { it.pkg == pkg && it.chatId == id } }
            ?: name?.let { nm -> convs.firstOrNull { it.pkg == pkg && norm(it.title) == norm(nm) } }
        if (conv == null) {
            if (name == null) {
                conv = convs.filter { it.pkg == pkg && now - it.lastTime < 5 * 60_000L }.maxByOrNull { it.lastTime } ?: return
            } else {
                conv = WaConv("$pkg|${chatId ?: name}", pkg, name).also { convs.add(it) }
            }
        }
        val cv: WaConv = conv!!
        if (chatId != null && cv.chatId == null) cv.chatId = chatId
        if (name != null && chatId != null && cv.title != name) cv.title = name

        val lastImg = incoming.indexOfLast { it.kind == WaKind.IMAGE && !it.fromMe }
        incoming.forEachIndexed { idx, m ->
            val existing = cv.msgs.firstOrNull {
                it.text == m.text && (it.time == m.time || (it.fromMe == m.fromMe && m.fromMe && kotlin.math.abs(it.time - m.time) < 120_000))
            }
            val target = existing ?: m.also { cv.msgs.add(it) }
            // التقاط المرفق (صورة/صوت/فيديو) في خيط خلفي؛ يُحاول أيضاً عند تحديث إشعار قديم لم يكن فيه مرفق
            if (target.path == null && !target.fromMe && target.kind != WaKind.TEXT) {
                val u = uris.getOrNull(idx)
                val bmp = if (idx == lastImg) bigPic else null
                if (u != null || bmp != null) captureMedia(ctx, target, u, bmp, m.kind)
            }
        }
        cv.msgs.sortBy { it.time }
        while (cv.msgs.size > MAX_MSGS) cv.msgs.removeAt(0)
        cv.lastTime = cv.msgs.lastOrNull()?.time ?: sbn.postTime

        // زر الرد السريع في إشعار واتساب
        val withInput = n.actions?.filter { a -> a.remoteInputs?.isNotEmpty() == true }.orEmpty()
        val act = (if (android.os.Build.VERSION.SDK_INT >= 28)
            withInput.firstOrNull { a -> a.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY } else null)
            ?: withInput.firstOrNull()
        val ri = act?.remoteInputs?.firstOrNull()
        if (act != null && ri != null) { replies[cv.key] = act to ri; cv.canReply = true }

        convs.sortByDescending { it.lastTime }
        while (convs.size > MAX_CONVS) convs.removeAt(convs.lastIndex)
        save(ctx)
    }

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private const val MAX_MEDIA_BYTES = 25L * 1024 * 1024

    /** ينسخ المرفق من الإشعار إلى مجلد التطبيق الخاص (على هذا الجهاز فقط). قد يفشل إن لم يمنح واتساب صلاحية القراءة. */
    private fun captureMedia(ctx: Context, m: WaMsg, uri: Uri?, bmp: Bitmap?, kind: Int) {
        val app = ctx.applicationContext
        io.execute {
            val file = runCatching {
                val dir = mediaDir(app).apply { mkdirs() }
                val stamp = "${System.currentTimeMillis()}_${(uri?.hashCode() ?: m.time.hashCode()).toUInt()}"
                if (uri != null) {
                    val mime = runCatching { app.contentResolver.getType(uri) }.getOrNull()
                    val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                        ?: when (kind) { WaKind.IMAGE -> "jpg"; WaKind.AUDIO -> "ogg"; WaKind.VIDEO -> "mp4"; else -> "bin" }
                    val f = File(dir, "$stamp.$ext")
                    var total = 0L
                    app.contentResolver.openInputStream(uri)?.use { ins ->
                        f.outputStream().use { out ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_MEDIA_BYTES) throw java.io.IOException("too big")
                                out.write(buf, 0, n)
                            }
                        }
                    } ?: throw java.io.IOException("no stream")
                    if (total == 0L) { f.delete(); null } else f
                } else if (bmp != null) {
                    val f = File(dir, "$stamp.jpg")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    f
                } else null
            }.getOrNull()
            if (file != null) ui.post {
                m.path = file.absolutePath
                save(app)
            }
        }
    }

    /** رسالة من رقم مرتبط كجهاز مصاحب ([WaLink]): تُضاف للمحادثة بمعرّف واتساب. يُستدعى من الخيط الرئيسي. */
    fun ingest(ctx: Context, pkg: String, chatId: String, title: String, msg: WaMsg) {
        load(ctx)
        val key = "$pkg|$chatId"
        val cv = convs.firstOrNull { it.key == key }
            ?: WaConv(key, pkg, title).also { it.chatId = chatId; convs.add(it) }
        if (title.isNotBlank() && cv.title != title) cv.title = title
        cv.canReply = true
        if (cv.msgs.none { it.text == msg.text && it.time == msg.time }) cv.msgs.add(msg)
        cv.msgs.sortBy { it.time }
        while (cv.msgs.size > MAX_MSGS) cv.msgs.removeAt(0).path?.let { runCatching { File(it).delete() } }
        cv.lastTime = cv.msgs.lastOrNull()?.time ?: msg.time
        convs.sortByDescending { it.lastTime }
        while (convs.size > MAX_CONVS) convs.removeAt(convs.lastIndex)
        save(ctx)
    }

    /** الرد عبر إجراء الرد في إشعار واتساب. يعمل ما دام الإشعار الأخير للمحادثة ما زال موجوداً. */
    fun reply(ctx: Context, conv: WaConv, text: String): Boolean {
        val body = text.trim()
        if (body.isEmpty()) return false
        if (conv.pkg.startsWith("link:")) {
            val chat = conv.chatId ?: return false
            WaLink.send(conv.pkg.removePrefix("link:"), chat, body)
            val now = System.currentTimeMillis()
            conv.msgs.add(WaMsg(body, now, true))
            conv.lastTime = now
            convs.sortByDescending { it.lastTime }
            save(ctx)
            return true
        }
        val (act, ri) = replies[conv.key] ?: return false
        return try {
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(ri.resultKey, body) }
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, results)
            act.actionIntent.send(ctx, 0, intent)
            conv.msgs.add(WaMsg(body, System.currentTimeMillis(), true))
            conv.lastTime = System.currentTimeMillis()
            convs.sortByDescending { it.lastTime }
            save(ctx)
            true
        } catch (_: PendingIntent.CanceledException) {
            replies.remove(conv.key); conv.canReply = false
            false
        }
    }
}
