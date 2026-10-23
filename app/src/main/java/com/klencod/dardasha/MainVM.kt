package com.klencod.dardasha

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klencod.dardasha.data.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

sealed interface Route {
    data object Home : Route
    data class Conversation(val peer: String) : Route
    data object NewChat : Route
    data object Me : Route
    data object Archived : Route
    data object Starred : Route
    data class Contact(val peer: String) : Route
    data class Media(val peer: String) : Route
    /** شاشة واتساب: قائمة كل ما وصل من واتساب (رسائل، صور، رسائل صوتية…). */
    data object WhatsApp : Route
    /** ربط أرقام واتساب كأجهزة مصاحبة. */
    data object WaLink : Route
    /** محادثة من صندوق واتساب المحلي. */
    data class WaChat(val key: String) : Route
    /** شاشة الإعدادات الرئيسية. */
    data object Settings : Route
    /** صفحة فرعية من الإعدادات. */
    data class SettingsPage(val section: SettingsSection) : Route
}

enum class SettingsSection {
    Account, Privacy, Chats, Notifications, WhatsApp, Plus, Appearance, Storage, Blocked, Help,
    /** قسم التخصيص والأحجام وصفحاته الفرعية. */
    Customize, CustomColors, CustomSizes, CustomBubbles, CustomTabBar
}

private const val ACTIVE_KEY = "active_id"
private const val MAX_PINS = 3
private const val MAX_ACCOUNTS = 5
private val TIMERS_SER = kotlinx.serialization.builtins.MapSerializer(
    kotlinx.serialization.serializer<String>(),
    kotlinx.serialization.serializer<Int>()
)

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = ctx.getSharedPreferences("dardasha", 0)

    // ---------- state ----------
    var meId by mutableStateOf<String?>(null); private set
    var booting by mutableStateOf(true); private set
    /** true بعد قراءة النسخة المحلية (يُخفي شاشة النظام الافتتاحية فور جاهزية القائمة). */
    var cacheReady by mutableStateOf(false); private set
    private val cachedIds = HashSet<String>()
    private var pendingPeer: String? = null
    private var pendingWa = false
    var route by mutableStateOf<Route>(Route.Home)
    var conn by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)
    var authBusy by mutableStateOf(false); private set

    // ---------- الحسابات المتعددة ----------
    /** الحسابات المحفوظة على هذا الجهاز (النشط منها هو meId). */
    val accounts = mutableStateListOf<SavedAccount>()
    /** true أثناء عرض شاشة الدخول لإضافة حساب آخر (والحساب الحالي ما زال مسجَّلاً). */
    var addingAccount by mutableStateOf(false); private set
    /** true أثناء التبديل بين الحسابات (يُعرض مؤشّر تحميل). */
    var switching by mutableStateOf(false); private set

    val msgs = mutableStateListOf<Message>()
    val peers = mutableStateMapOf<String, Profile>()
    val nicks = mutableStateMapOf<String, String>()
    val reactions = mutableStateListOf<Reaction>()
    val typingFrom = mutableStateMapOf<String, Boolean>()
    val seenOverride = mutableStateMapOf<String, String>()
    var blocked by mutableStateOf<Set<String>>(emptySet()); private set
    var online by mutableStateOf<Set<String>>(emptySet()); private set
    var hidden by mutableStateOf<Set<String>>(emptySet()); private set
    val chatPrefs = mutableStateMapOf<String, ChatPref>()
    var starred by mutableStateOf<Set<String>>(emptySet()); private set
    /** رسائل قرأتُها أثناء إيقاف «إشعارات القراءة»: تُعلَّم محلياً فقط دون إخبار المرسِل. */
    private var localRead by mutableStateOf<Set<String>>(emptySet())
    private var timersState by mutableStateOf<Map<String, Int>>(emptyMap())
    /** يتحدّث كل بضع ثوانٍ ليُخفي الرسائل المؤقتة المنتهية دون إعادة تحميل. */
    private var tick by mutableLongStateOf(0L)
    private val purged = HashSet<String>()

    private val seen = HashSet<String>()
    private val jobs = mutableListOf<Job>()
    private val channels = mutableListOf<RealtimeChannel>()
    private var room: RealtimeChannel? = null
    private val typJobs = HashMap<String, Job>()
    private var lastTypingSent = 0L
    private val readSent = HashSet<String>()

    init {
        // 1) نعرض البيانات المحفوظة فوراً (بدون انتظار الشبكة أو Supabase)
        viewModelScope.launch {
            val boot = withContext(Dispatchers.IO) {
                val accs = Accounts.all(ctx)
                val id = prefs.getString(ACTIVE_KEY, null)?.takeIf { a -> accs.any { it.id == a } }
                Triple(accs, id, id?.let { Cache.load(ctx, it) })
            }
            val (accs, id, snap) = boot
            accounts.clear(); accounts.addAll(accs)
            if (meId == null) {
                if (id != null) beginSession(id, snap)
                else if (accs.isEmpty()) booting = false // لا حساب على الجهاز: شاشة الترحيب مباشرة
            }
            cacheReady = true
        }
        // صمّامات أمان: لا نُبقي شاشة البدء أكثر من لحظات مهما حدث
        viewModelScope.launch { delay(1200); cacheReady = true }
        viewModelScope.launch { delay(3000); if (booting) booting = false }

        // 2) نتحقّق من الجلسة بالخلفية (قد تحتاج الشبكة) ولا نعطّل الواجهة بسببها
        viewModelScope.launch(Dispatchers.Default) {
            Supa.client.auth.sessionStatus.collect { st ->
                withContext(Dispatchers.Main.immediate) {
                    when (st) {
                        is SessionStatus.Authenticated -> {
                            // نحفظ الجلسة عند كل دخول/تجديد (refresh token يتبدّل) ليبقى التبديل ممكناً لاحقاً
                            withContext(Dispatchers.IO) { runCatching { Accounts.saveSession(ctx, st.session) } }
                            refreshAccounts()
                            st.session.user?.id?.let { onLogin(it) }
                        }
                        is SessionStatus.NotAuthenticated -> onLogout()
                        else -> Unit
                    }
                }
            }
        }
    }

    // ---------- auth ----------
    fun signIn(email: String, pass: String, onErr: (String) -> Unit) = viewModelScope.launch {
        authBusy = true
        runCatching { Repo.signIn(email, pass) }
            .onSuccess { addingAccount = false }
            .onFailure { onErr(trErr(it.message)) }
        authBusy = false
    }

    fun signUp(email: String, pass: String, user: String, onErr: (String) -> Unit, onConfirm: () -> Unit) =
        viewModelScope.launch {
            authBusy = true
            runCatching { Repo.signUp(email, pass, user) }
                .onSuccess { signedIn -> if (signedIn) { addingAccount = false } else { onConfirm() } }
                .onFailure { onErr(trErr(it.message)) }
            authBusy = false
        }

    /** تسجيل خروج الحساب الحالي فقط؛ إن وُجد حساب محفوظ آخر ننتقل إليه تلقائياً. */
    fun signOut() { viewModelScope.launch { signOutCurrent(null) } }

    private suspend fun signOutCurrent(preferNext: String?) {
        val me = meId
        switching = true
        addingAccount = false
        cacheMyProfile()
        runCatching { Push.unregister(ctx) }
        runCatching { Repo.signOut() }
        me?.let { Accounts.remove(ctx, it); Cache.delete(ctx, it) }
        Media.clearAll()
        Keep.clearAll()
        refreshAccounts()
        val next = preferNext?.takeIf { it != me && Accounts.get(ctx, it) != null }
            ?: Accounts.all(ctx).firstOrNull()?.id
        if (next != null) switchInternal(next) else switching = false
    }

    // ---------- تبديل / إضافة / إزالة حساب ----------
    private fun refreshAccounts() {
        val l = Accounts.all(ctx)
        accounts.clear(); accounts.addAll(l)
    }

    /** نحفظ اسم/صورة الحساب الحالي لعرضهما في قائمة الحسابات. */
    private fun cacheMyProfile() {
        val me = meId ?: return
        peers[me]?.let { runCatching { Accounts.updateProfile(ctx, it) } }
        refreshAccounts()
    }

    fun startAddAccount() {
        if (accounts.size >= MAX_ACCOUNTS) { toast = "الحد الأقصى $MAX_ACCOUNTS حسابات على الجهاز"; return }
        addingAccount = true
    }
    fun cancelAddAccount() { addingAccount = false }

    fun switchTo(id: String) {
        if (id == meId || switching) return
        viewModelScope.launch { cacheMyProfile(); switchInternal(id) }
    }

    private suspend fun switchInternal(id: String): Boolean {
        val saved = Accounts.session(ctx, id)
        if (saved == null) {
            toast = "تعذّر فتح الحساب، سجّل الدخول من جديد"
            switching = false
            return false
        }
        switching = true
        val ok = runCatching {
            // refresh token يُستهلك مرة واحدة، لذا نجدّد الجلسة ثم نستوردها (وإن فشل التجديد نجرّب المحفوظة)
            val fresh = runCatching { Supa.client.auth.refreshSession(saved.refreshToken) }.getOrNull() ?: saved
            Supa.client.auth.importSession(fresh)
        }.isSuccess
        if (!ok) {
            toast = "انتهت جلسة هذا الحساب، سجّل الدخول من جديد"
            Accounts.remove(ctx, id); refreshAccounts()
            switching = false
            return false
        }
        // نبقي المؤشّر حتى يلتقط مُستمع الجلسة الحساب الجديد (onLogin)
        var t = 0
        while (meId != id && t < 50) { delay(100); t++ }
        switching = false
        return true
    }

    /**
     * إزالة حساب من الجهاز. لإلغاء تسجيل توكن الإشعارات من السيرفر لا بدّ أن يكون الحساب نشطاً،
     * فنبدّل إليه مؤقتاً ثم نسجّل خروجه ونعود للحساب الذي كنا عليه.
     */
    fun removeAccount(id: String) {
        if (switching) return
        val back = meId?.takeIf { it != id }
        viewModelScope.launch {
            if (id != meId && !switchInternal(id)) return@launch
            signOutCurrent(back)
        }
    }

    /** اختصار «واتساب» في الشاشة الرئيسية: يفتح شاشة واتساب (وليس صفحة الإعدادات). */
    fun openWhatsApp() {
        if (meId == null) { pendingWa = true; return }
        route = Route.WhatsApp
    }

    /** الضغط على إشعار: إن كان يخصّ حساباً آخر محفوظاً نبدّل إليه أولاً ثم نفتح المحادثة. */
    fun openChatFromNotification(accountId: String?, peer: String) {
        if (meId == null && (accountId == null || accountId == prefs.getString(ACTIVE_KEY, null))) {
            pendingPeer = peer // الجلسة ما زالت تُفتح من الكاش؛ نفتح المحادثة فور جاهزيتها
            return
        }
        if (accountId == null || accountId == meId) { openChat(peer); return }
        viewModelScope.launch {
            if (Accounts.get(ctx, accountId) != null && switchInternal(accountId)) openChat(peer)
        }
    }

    private fun trErr(m: String?): String {
        val s = m ?: return "حدث خطأ"
        return when {
            Regex("already registered", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "الإيميل مسجّل من قبل"
            Regex("Invalid login", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "الإيميل أو كلمة المرور غير صحيحة"
            Regex("duplicate|unique", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "اسم المستخدم محجوز"
            Regex("Database error", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "اسم المستخدم محجوز أو غير صالح"
            Regex("not confirmed", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "أكّد الإيميل أولاً"
            Regex("timeout|timed out|Unable to resolve host|UnknownHost|Failed to connect|ConnectException|SocketException|SSLException", RegexOption.IGNORE_CASE)
                .containsMatchIn(s) -> "تعذّر الاتصال بالخادم. تأكد من الإنترنت (أو جرّب شبكة أخرى) ثم حاول مرة أخرى."
            else -> s
        }
    }

    private fun onLogin(id: String) {
        // الجلسة فُتحت أصلاً من الكاش: لا نعيد التحميل (المزامنة تعمل بالخلفية)
        if (meId == id) { booting = false; switching = false; return }
        beginSession(id, null)
    }

    /** يفتح جلسة الحساب: يعرض الكاش المحلي فوراً ثم يزامن مع الخادم بالخلفية. */
    private fun beginSession(id: String, snap: CacheSnapshot?) {
        stopSession()
        meId = id
        AppState.activeUserId = id
        prefs.edit().putString(ACTIVE_KEY, id).apply()
        loadHidden(id)
        loadLocalRead(id)
        loadLocalTimers(id)
        loadLocalExtras(id)
        (snap ?: Cache.load(ctx, id))?.let { applySnapshot(it) }
        booting = false
        switching = false
        route = pendingPeer?.let { Route.Conversation(it) }
            ?: if (pendingWa) Route.WhatsApp else Route.Home
        pendingPeer = null
        pendingWa = false
        startPersist(id)
        jobs += viewModelScope.launch { bootstrap(id) }
        Push.sync(ctx)
    }

    private fun applySnapshot(s: CacheSnapshot) {
        val now = System.currentTimeMillis()
        val ok = s.msgs.filter { m ->
            val e = parseIso(m.expiresAt)
            (e == null || e > now) && seen.add(m.id)
        }
        msgs.addAll(ok)
        cachedIds.addAll(ok.map { it.id })
        s.peers.forEach { peers[it.id] = it }
        nicks.putAll(s.nicks)
        reactions.addAll(s.reactions)
        blocked = s.blocked.toSet()
    }

    /** يحفظ البيانات على القرص بعد كل تغيير (مع تأخير بسيط) لتكون جاهزة في الفتحة القادمة. */
    @OptIn(FlowPreview::class)
    private fun startPersist(id: String) {
        jobs += viewModelScope.launch {
            snapshotFlow {
                CacheSnapshot(
                    msgs = msgs.toList(), peers = peers.values.toList(), nicks = nicks.toMap(),
                    reactions = reactions.toList(), blocked = blocked.toList()
                )
            }.drop(1).debounce(1000).collect { snap ->
                withContext(Dispatchers.IO) {
                    Cache.save(
                        ctx, id,
                        snap.copy(msgs = snap.msgs.sortedByDescending { it.createdAt }.take(Cache.MAX_MSGS))
                    )
                }
            }
        }
    }

    private fun onLogout() {
        stopSession()
        meId = null
        AppState.activeUserId = null
        prefs.edit().remove(ACTIVE_KEY).apply()
        hidden = emptySet()
        booting = false
        route = Route.Home
    }

    private fun stopSession() {
        jobs.forEach { it.cancel() }; jobs.clear()
        typJobs.values.forEach { it.cancel() }; typJobs.clear()
        val chs = channels.toList(); channels.clear()
        val r = room; room = null
        viewModelScope.launch {
            chs.forEach { runCatching { Supa.client.realtime.removeChannel(it) } }
            r?.let { runCatching { Supa.client.realtime.removeChannel(it) } }
        }
        msgs.clear(); seen.clear(); peers.clear(); nicks.clear(); reactions.clear()
        typingFrom.clear(); seenOverride.clear(); readSent.clear(); localRead = emptySet()
        chatPrefs.clear(); starred = emptySet(); drafts.clear()
        blocked = emptySet(); online = emptySet(); timersState = emptyMap(); purged.clear(); cachedIds.clear()
        conn = ""
    }

    // ---------- bootstrap ----------
    private suspend fun bootstrap(id: String) {
        conn = "○ جارٍ الاتصال..."
        // ننتظر جاهزية الجلسة (قد تحتاج تجديد التوكن عبر الشبكة) — الواجهة تعرض الكاش في هذه الأثناء
        withContext(Dispatchers.Default) { runCatching { Supa.client.auth.awaitInitialization() } }
        while (Supa.client.auth.currentUserOrNull()?.id != id) delay(1500)

        // الاشتراكات اللحظية بالتوازي مع تحميل البيانات
        jobs += viewModelScope.launch {
            subscribeMessages(id)
            subscribeReactions()
            subscribeTimers(id)
            subscribeRoom(id)
        }
        var attempt = 0
        while (!syncFromServer(id)) {
            conn = "○ بانتظار الشبكة"
            attempt++
            delay(minOf(2000L * attempt, 15_000L))
        }
        cachedIds.clear()
        if (conn.startsWith("○ جارٍ")) conn = "● متصل"
        jobs += viewModelScope.launch {
            while (true) {
                delay(10_000)
                tick++
                purgeExpired(id)
            }
        }
        jobs += viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (AppState.foreground && AppSettings.showLastSeen) runCatching { Repo.touchLastSeen(id, Instant.now().toString()) }
            }
        }
    }

    /** يحدّث كل البيانات من الخادم دفعة واحدة بالتوازي. يرجع false إن فشل تحميل الرسائل (نعيد المحاولة). */
    private suspend fun syncFromServer(id: String): Boolean = coroutineScope {
        val m = async { runCatching { Repo.loadMessages() } }
        val c = async { runCatching { Repo.loadContacts() } }
        val b = async { runCatching { Repo.loadBlocks() } }
        val r = async { runCatching { Repo.loadReactions() } }
        val t = async { runCatching { Repo.loadTimers() } }
        val p = async { runCatching { Repo.loadPrefs() } }
        val s = async { runCatching { Repo.loadStars() } }
        val msgRes = m.await()
        c.await(); b.await(); r.await(); t.await(); p.await(); s.await()
        if (msgRes.isFailure) return@coroutineScope false
        if (meId != id) return@coroutineScope true
        mergeMessages(msgRes.getOrThrow())
        c.await().onSuccess { l -> l.forEach { nicks[it.contact] = it.nick ?: "" } }
        b.await().onSuccess { l -> blocked = l.map { it.blocked }.toSet() }
        r.await().onSuccess { l -> reactions.clear(); reactions.addAll(l) }
        t.await().onSuccess { l -> applyTimers(id, l) }
        p.await().onSuccess { l -> chatPrefs.clear(); l.forEach { chatPrefs[it.peer] = it }; saveLocalPrefs(id) }
        s.await().onSuccess { l -> starred = l.map { it.messageId }.toSet(); saveLocalStars(id) }
        loadPeers(id, onlyMissing = false)
        true
    }

    /**
     * يدمج رسائل الخادم فوق النسخة المحلية: يحدّث الموجود (قراءة/تعديل/حذف) ويضيف الجديد،
     * ويزيل فقط ما كان في الكاش وحُذف من الخادم. التحديث في خطوة واحدة فلا يظهر وميض.
     */
    private fun mergeMessages(fresh: List<Message>) {
        val now = System.currentTimeMillis()
        val freshById = HashMap<String, Message>(fresh.size * 2)
        fresh.forEach { freshById[it.id] = it }
        val full = fresh.size >= 1000
        val oldestMs = fresh.minOfOrNull { parseIso(it.createdAt) ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
        val out = ArrayList<Message>(msgs.size + fresh.size)
        for (m in msgs) {
            val f = freshById.remove(m.id)
            when {
                f != null -> out.add(protect(m, f))
                // كان في الكاش ولم يعد على الخادم ضمن النافذة المحمّلة => حُذف
                m.id in cachedIds && fresh.isNotEmpty() && (!full || (parseIso(m.createdAt) ?: 0L) >= oldestMs) ->
                    seen.remove(m.id)
                else -> out.add(m)
            }
        }
        for (f in fresh.asReversed()) {
            if (f.id !in freshById) continue // موجودة أصلاً
            val e = parseIso(f.expiresAt)
            if (e != null && e <= now) continue
            if (seen.add(f.id)) out.add(protect(null, f))
        }
        Snapshot.withMutableSnapshot { msgs.clear(); msgs.addAll(out) }
        keepMedia(out)
    }

    // ---------- الرسائل المؤقتة ----------
    fun timerOf(peer: String): Int = timersState[peer] ?: 0

    private fun loadLocalTimers(id: String) {
        runCatching {
            prefs.getString("tm_$id", null)?.let { js ->
                timersState = AppJson.decodeFromString(TIMERS_SER, js)
            }
        }
    }

    private fun saveLocalTimers(id: String) {
        runCatching {
            prefs.edit().putString("tm_$id", AppJson.encodeToString(TIMERS_SER, timersState)).apply()
        }
    }

    private fun applyTimers(id: String, l: List<ChatTimer>) {
        val m = timersState.toMutableMap()
        l.forEach { m[if (it.a == id) it.b else it.a] = it.secs ?: 0 }
        timersState = m
        saveLocalTimers(id)
    }

    private suspend fun reloadTimers(id: String) {
        runCatching { Repo.loadTimers() }.onSuccess { l -> applyTimers(id, l) }
    }

    private suspend fun subscribeTimers(me: String) {
        val ch = Supa.client.channel("tm")
        val f = ch.postgresChangeFlow<PostgresAction>("public") { table = "chat_timers" }
        channels += ch
        jobs += viewModelScope.launch { f.collect { reloadTimers(me) } }
        runCatching { ch.subscribe(blockUntilSubscribed = true) }
    }

    fun setTimer(peer: String, secs: Int) {
        val me = meId ?: return
        timersState = timersState + (peer to secs)
        saveLocalTimers(me)
        viewModelScope.launch {
            runCatching { Repo.setTimer(me, peer, secs) }
                .onSuccess { toast = if (secs > 0) "الرسائل الجديدة ستختفي بعد ${timerLabel(secs)}" else "تم إيقاف الرسائل المؤقتة" }
                .onFailure { toast = "تعذّر مزامنة المؤقّت (يعمل على جهازك فقط): ${it.message}" }
        }
    }

    /** المرسِل يحذف رسائله المنتهية فعلياً من القاعدة والتخزين (المستلم يُخفيها محلياً فوراً). */
    private suspend fun purgeExpired(me: String) {
        val now = System.currentTimeMillis()
        val old = msgs.filter {
            it.sender == me && it.id !in purged && (parseIso(it.expiresAt) ?: Long.MAX_VALUE) <= now
        }
        for (m in old) {
            purged.add(m.id)
            runCatching {
                Repo.deleteMessageRow(m.id)
                m.imagePath?.let { Repo.remove("chat-images", it) }
                m.audioPath?.let { Repo.remove("chat-audio", it) }
                m.filePath?.let { Repo.remove(Repo.FILES_BUCKET, it) }
            }
        }
        if (old.isNotEmpty()) msgs.removeAll { m -> old.any { it.id == m.id } }
    }

    // ---------- pin / archive / mute / stars (محلياً + مزامنة مع Supabase إن وُجد الجدول) ----------
    private fun loadLocalExtras(id: String) {
        runCatching {
            prefs.getString("cp_$id", null)?.let { js ->
                AppJson.decodeFromString<List<ChatPref>>(js).forEach { chatPrefs[it.peer] = it }
            }
        }
        starred = prefs.getStringSet("st_$id", emptySet())?.toSet() ?: emptySet()
    }

    private fun saveLocalPrefs(id: String) {
        runCatching { prefs.edit().putString("cp_$id", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatPref.serializer()), chatPrefs.values.toList())).apply() }
    }

    private fun saveLocalStars(id: String) {
        prefs.edit().putStringSet("st_$id", starred).apply()
    }

    fun pref(peer: String) = chatPrefs[peer] ?: ChatPref(peer)
    fun isPinned(peer: String) = pref(peer).pinned
    fun isArchived(peer: String) = pref(peer).archived
    fun isMuted(peer: String) = pref(peer).muted

    private fun updatePref(peer: String, f: (ChatPref) -> ChatPref) {
        val me = meId ?: return
        val np = f(pref(peer))
        if (np == ChatPref(peer)) chatPrefs.remove(peer) else chatPrefs[peer] = np
        saveLocalPrefs(me)
        viewModelScope.launch { runCatching { Repo.savePref(me, np) } }
    }

    fun togglePin(peer: String) {
        if (!isPinned(peer) && chatPrefs.values.count { it.pinned } >= MAX_PINS) {
            toast = "يمكن تثبيت $MAX_PINS محادثات كحد أقصى"; return
        }
        val on = !isPinned(peer)
        updatePref(peer) { it.copy(pinned = on) }
        toast = if (on) "تم تثبيت المحادثة" else "تم إلغاء التثبيت"
    }

    fun toggleArchive(peer: String) {
        val on = !isArchived(peer)
        // الأرشفة تلغي التثبيت (مثل واتساب)
        updatePref(peer) { it.copy(archived = on, pinned = if (on) false else it.pinned) }
        toast = if (on) "تمت أرشفة المحادثة" else "تم إلغاء الأرشفة"
    }

    fun toggleMute(peer: String) {
        val on = !isMuted(peer)
        updatePref(peer) { it.copy(muted = on) }
        toast = if (on) "تم كتم المحادثة" else "تم إلغاء الكتم"
    }

    fun isStarred(id: String) = id in starred

    // ---------- مسودات الكتابة (محفوظة محلياً لكل محادثة) ----------
    val drafts = mutableStateMapOf<String, String>()
    private fun draftKey(peer: String) = "dr_${meId}_$peer"
    fun draftOf(peer: String): String =
        drafts[peer] ?: (prefs.getString(draftKey(peer), "") ?: "").also { if (it.isNotEmpty()) drafts[peer] = it }
    fun setDraft(peer: String, t: String) {
        if (t.isBlank()) {
            if (drafts.remove(peer) != null || prefs.contains(draftKey(peer))) prefs.edit().remove(draftKey(peer)).apply()
        } else if (drafts[peer] != t) {
            drafts[peer] = t
            prefs.edit().putString(draftKey(peer), t).apply()
        }
    }

    fun toggleStar(m: Message) {
        val me = meId ?: return
        val on = m.id !in starred
        starred = if (on) starred + m.id else starred - m.id
        saveLocalStars(me)
        viewModelScope.launch {
            runCatching { if (on) Repo.addStar(me, m.id) else Repo.removeStar(me, m.id) }
        }
        toast = if (on) "تم تمييز الرسالة بنجمة" else "أُزيلت النجمة"
    }

    fun starredMessages(): List<Message> =
        msgs.filter { it.id in starred && visible(it) }.sortedByDescending { it.createdAt }

    private suspend fun loadPeers(id: String, onlyMissing: Boolean = true) {
        val need = (msgs.map { other(it) } + nicks.keys + id).toSet().filter { !onlyMissing || it !in peers }
        runCatching { Repo.profiles(need) }.onSuccess { l -> l.forEach { peers[it.id] = it } }
        cacheMyProfile()
    }

    // ---------- منع الحذف ----------
    private fun recoverable(m: Message): Boolean =
        !m.body.isNullOrBlank() || listOfNotNull(m.imagePath, m.audioPath, m.filePath).any { Keep.has(it) || Media.isCached(it) }

    /**
     * إن حذف الطرف الآخر رسالة للجميع (يُعلَّم deleted على الخادم وتُحذف ملفاتها) وكان «منع الحذف» مفعّلاً،
     * نُبقي النسخة المحلية كما وصلتنا ونعلّمها delSaved. لا يشمل رسائلي ولا الصور «لمرة واحدة».
     */
    private fun protect(old: Message?, incoming: Message): Message {
        if (!AppSettings.antiDelete) return incoming
        val me = meId ?: return incoming
        if (incoming.sender == me || incoming.viewOnce == true) return incoming
        if (old != null && old.delSaved == true) return old
        if (!incoming.isDeleted) return incoming
        val base = if (old != null && !old.isDeleted) old else incoming
        if (base.viewOnce == true || !recoverable(base)) return incoming
        return base.copy(deleted = false, delSaved = true)
    }

    /** ينزّل وسائط الرسائل الواردة مسبقاً لتبقى معنا إن حُذفت لاحقاً. */
    private fun keepMedia(list: List<Message>) {
        if (!AppSettings.antiDelete) return
        val me = meId ?: return
        val todo = list.filter { m ->
            m.sender != me && !m.isDeleted && m.viewOnce != true &&
                listOfNotNull(m.imagePath, m.audioPath, m.filePath).any { !Keep.has(it) }
        }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (m in todo) {
                m.imagePath?.let { Keep.fetch("chat-images", it) }
                m.audioPath?.let { Keep.fetch("chat-audio", it) }
                m.filePath?.let { Keep.fetch(Repo.FILES_BUCKET, it) }
            }
        }
    }

    /** يُستدعى عند تفعيل «منع الحذف» لحفظ وسائط الرسائل الحالية. */
    fun refreshKeep() = keepMedia(msgs.toList())

    private fun addMsg(m: Message): Boolean {
        if (!seen.add(m.id)) return false
        val exp = parseIso(m.expiresAt)
        if (exp != null && exp <= System.currentTimeMillis()) return false
        msgs.add(m)
        return true
    }

    // ---------- realtime ----------
    private suspend fun subscribeMessages(me: String) {
        val ch = Supa.client.channel("msgs")
        val ins = ch.postgresChangeFlow<PostgresAction.Insert>("public") { table = "messages" }
        val upd = ch.postgresChangeFlow<PostgresAction.Update>("public") { table = "messages" }
        val del = ch.postgresChangeFlow<PostgresAction.Delete>("public") { table = "messages" }
        channels += ch
        jobs += viewModelScope.launch {
            ins.collect { a ->
                val m = runCatching { a.decodeRecord<Message>() }.getOrNull() ?: return@collect
                if (m.sender != me && m.receiver != me) return@collect
                if (addMsg(m)) {
                    keepMedia(listOf(m))
                    if (other(m) !in peers) loadPeers(me)
                    if (m.sender != me) onIncoming(m)
                }
            }
        }
        jobs += viewModelScope.launch {
            upd.collect { a ->
                val m = runCatching { a.decodeRecord<Message>() }.getOrNull() ?: return@collect
                val i = msgs.indexOfFirst { it.id == m.id }
                if (i >= 0) msgs[i] = protect(msgs[i], m)
                // الصورة لمرة واحدة: بعد أن يفتحها المستلم يحذف المرسِل الملف من التخزين
                if (m.sender == me && m.viewOnce == true && m.viewedAt != null && m.imagePath != null) {
                    runCatching { Repo.remove("chat-images", m.imagePath) }
                    Media.evict(m.imagePath)
                }
            }
        }
        jobs += viewModelScope.launch {
            del.collect { a ->
                val id = a.oldRecord["id"]?.jsonPrimitive?.content ?: return@collect
                msgs.removeAll { it.id == id }
            }
        }
        runCatching { ch.subscribe(blockUntilSubscribed = true) }
            .onSuccess { conn = "● متصل" }
            .onFailure { conn = "○ بانتظار الشبكة" }
    }

    private suspend fun subscribeReactions() {
        val ch = Supa.client.channel("rx")
        val f = ch.postgresChangeFlow<PostgresAction>("public") { table = "message_reactions" }
        channels += ch
        jobs += viewModelScope.launch {
            f.collect { runCatching { Repo.loadReactions() }.onSuccess { l -> reactions.clear(); reactions.addAll(l) } }
        }
        runCatching { ch.subscribe(blockUntilSubscribed = true) }
    }

    private suspend fun subscribeRoom(me: String) {
        // نفس قناة "room" في نسخة الويب: presence للمتصلين + broadcast للكتابة
        val r = Supa.client.channel("room") { presence { key = me } }
        val pres = r.presenceChangeFlow()
        val typ = r.broadcastFlow<JsonObject>("typing")
        room = r
        jobs += viewModelScope.launch {
            pres.collect { a ->
                online = (online + a.joins.keys) - a.leaves.keys
                a.leaves.keys.forEach { seenOverride[it] = Instant.now().toString(); typingFrom.remove(it) }
            }
        }
        jobs += viewModelScope.launch {
            typ.collect { p ->
                if (p["to"]?.jsonPrimitive?.content != me) return@collect
                val from = p["from"]?.jsonPrimitive?.content ?: return@collect
                val on = runCatching { p["on"]?.jsonPrimitive?.boolean }.getOrNull() == true
                typJobs[from]?.cancel()
                if (on) {
                    typingFrom[from] = true
                    typJobs[from] = viewModelScope.launch { delay(4000); typingFrom.remove(from) }
                } else typingFrom.remove(from)
            }
        }
        runCatching {
            r.subscribe(blockUntilSubscribed = true)
            if (AppSettings.showLastSeen) {
                r.track(buildJsonObject { put("at", System.currentTimeMillis()) })
                Repo.touchLastSeen(me, Instant.now().toString())
            }
        }
    }

    fun onForeground() {
        val me = meId ?: return
        val r = room ?: return
        if (!AppSettings.showLastSeen) return
        viewModelScope.launch {
            runCatching {
                r.track(buildJsonObject { put("at", System.currentTimeMillis()) })
                Repo.touchLastSeen(me, Instant.now().toString())
            }
        }
    }

    /** يُستدعى عند تغيير «آخر ظهور ومتصل»: يُظهر حضوري للآخرين أو يخفيه فوراً. */
    fun applyPresencePrivacy() {
        val me = meId ?: return
        val r = room ?: return
        viewModelScope.launch {
            runCatching {
                if (AppSettings.showLastSeen) {
                    r.track(buildJsonObject { put("at", System.currentTimeMillis()) })
                    Repo.touchLastSeen(me, Instant.now().toString())
                } else r.untrack()
            }
        }
    }

    private fun onIncoming(m: Message) {
        if (m.sender in blocked) return
        // رسالة جديدة تُخرج المحادثة من الأرشيف ما لم تكن مكتومة
        if (isArchived(m.sender) && !isMuted(m.sender)) updatePref(m.sender) { it.copy(archived = false) }
        if (isMuted(m.sender)) return
        val viewing = (route as? Route.Conversation)?.peer == m.sender
        if (viewing && AppState.foreground) return
        if (!AppState.foreground) {
            Notifier.show(ctx, m.sender, name(m.sender), preview(m), meId)
        }
    }

    // ---------- helpers ----------
    fun other(m: Message) = if (m.sender == meId) m.receiver else m.sender
    fun name(id: String) = nicks[id]?.takeIf { it.isNotBlank() }
        ?: peers[id]?.displayName?.takeIf { it.isNotBlank() }
        ?: peers[id]?.username ?: "…"
    fun isOnline(id: String) = id in online

    fun preview(m: Message): String = when {
        m.isDeleted -> "🚫 تم حذف هذه الرسالة"
        m.viewOnce == true && m.imagePath != null -> if (m.viewedAt != null) "① تم فتح الصورة" else "① صورة لمرة واحدة"
        m.forwarded == true && m.audioPath == null && m.filePath == null && m.imagePath == null && parseGeo(m.text) == null ->
            "↪ " + m.text
        m.audioPath != null -> "🎤 رسالة صوتية"
        m.filePath != null -> "📎 " + (m.fileName ?: "ملف")
        parseGeo(m.text) != null -> "📍 موقع"
        m.imagePath != null && m.text.isBlank() -> "📷 صورة"
        else -> m.text
    }

    private fun visible(m: Message): Boolean {
        @Suppress("UNUSED_VARIABLE") val t = tick // يجعل القوائم تُعاد حسابها عند كل نبضة
        if (m.id in hidden) return false
        val exp = parseIso(m.expiresAt)
        return exp == null || exp > System.currentTimeMillis()
    }

    fun thread(peer: String): List<Message> =
        msgs.filter { other(it) == peer && visible(it) }.sortedBy { it.createdAt }

    /** آخر رسالة لكل محادثة، الأحدث أولاً */
    fun conversations(): List<Pair<String, Message>> {
        val by = LinkedHashMap<String, Message>()
        msgs.sortedBy { it.createdAt }.forEach { if (visible(it)) by[other(it)] = it }
        return by.entries
            .sortedWith(compareByDescending<Map.Entry<String, Message>> { isPinned(it.key) }
                .thenByDescending { it.value.createdAt })
            .map { it.key to it.value }
    }

    fun unread(peer: String): Int {
        val me = meId ?: return 0
        return msgs.count { it.sender == peer && it.receiver == me && isUnreadIn(it) && !it.isDeleted && visible(it) }
    }

    /** غير مقروءة محلياً: لم تُرسَل لها إشعار قراءة ولم نعلّمها مقروءة على هذا الجهاز. */
    fun isUnreadIn(m: Message) = m.readAt == null && m.id !in localRead

    private fun loadLocalRead(id: String) {
        localRead = prefs.getStringSet("lread_$id", emptySet())?.toSet() ?: emptySet()
    }

    private fun saveLocalRead() {
        val me = meId ?: return
        val alive = msgs.mapTo(HashSet()) { it.id }
        localRead = localRead.filterTo(HashSet()) { it in alive }
        prefs.edit().putStringSet("lread_$me", localRead).apply()
    }

    fun lastSeenOf(id: String): String? = seenOverride[id] ?: peers[id]?.lastSeen

    fun openChat(peer: String) { route = Route.Conversation(peer) }

    private fun expFor(peer: String): Long? {
        val s = timerOf(peer)
        return if (s > 0) System.currentTimeMillis() + s * 1000L else null
    }

    private fun rlsMsg(e: Throwable?): String {
        val s = e?.message ?: ""
        return if (s.contains("row-level security", true)) "لا يمكنك مراسلة هذا المستخدم الآن" else "فشل الإرسال: $s"
    }

    // ---------- actions ----------
    fun fetchSeen(id: String) = viewModelScope.launch {
        runCatching { Repo.profile(id) }.getOrNull()?.let { peers[id] = it }
    }

    fun typing(peer: String, on: Boolean) {
        if (!AppSettings.typingIndicator) return
        val r = room ?: return
        val me = meId ?: return
        val now = System.currentTimeMillis()
        if (on && now - lastTypingSent < 2000) return
        lastTypingSent = if (on) now else 0
        viewModelScope.launch {
            runCatching {
                r.broadcast("typing", buildJsonObject { put("from", me); put("to", peer); put("on", on) })
            }
        }
    }

    fun markRead(peer: String) {
        val me = meId ?: return
        val mine = msgs.filter { it.sender == peer && it.receiver == me && it.readAt == null }
        if (mine.isEmpty()) return
        // علّمها مقروءة محلياً فوراً (ومحفوظة) حتى لا يعود العدّاد إن فشل الطلب أو تأخّر الـ realtime
        val newLocal = mine.filter { it.id !in localRead }
        if (newLocal.isNotEmpty()) {
            localRead = localRead + newLocal.map { it.id }
            saveLocalRead()
        }
        if (!AppSettings.readReceipts) return
        val toSend = mine.filter { it.id !in readSent }
        if (toSend.isEmpty()) return
        val iso = Instant.now().toString()
        toSend.forEach { readSent.add(it.id) }
        viewModelScope.launch {
            // دفعات صغيرة: القائمة الطويلة تجعل رابط الطلب أطول من المسموح فيفشل بصمت
            toSend.chunked(40).forEach { chunk ->
                val ids = chunk.map { it.id }
                val ok = runCatching { Repo.markRead(ids, iso) }.isSuccess
                if (!ok) ids.forEach { readSent.remove(it) } // تُعاد المحاولة عند الدخول التالي
            }
        }
    }

    private fun baseRow(to: String, body: String, replyTo: String?) = buildJsonObject {
        put("sender", meId!!)
        put("receiver", to)
        put("body", body)
        if (replyTo != null) put("reply_to", replyTo)
        expFor(to)?.let { put("expires_at", Instant.ofEpochMilli(it).toString()) }
    }

    private fun rand() = java.lang.Long.toString((Math.random() * 1e12).toLong(), 36)

    fun send(to: String, body: String, replyTo: Message?, img: ByteArray?, viewOnce: Boolean = false) {
        val me = meId ?: return
        if (to in blocked) { toast = "هذا المستخدم محظور"; return }
        if (to == me) { toast = "لا يمكنك مراسلة نفسك"; return }
        viewModelScope.launch {
            var path: String? = null
            runCatching {
                if (img != null) {
                    path = "$me/$to/${System.currentTimeMillis()}_${rand()}.jpg"
                    Repo.upload("chat-images", path!!, img, "image/jpeg")
                }
                val row = buildJsonObject {
                    baseRow(to, body.ifEmpty { "📷 صورة" }, replyTo?.id).forEach { (k, v) -> put(k, v) }
                    if (path != null) put("image_path", path!!)
                    if (path != null && viewOnce) put("view_once", true)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                path?.let { p -> runCatching { Repo.remove("chat-images", p) } }
                toast = rlsMsg(it)
            }
        }
    }

    private fun canSendTo(to: String): Boolean {
        val me = meId ?: return false
        if (to in blocked) { toast = "هذا المستخدم محظور"; return false }
        if (to == me) { toast = "لا يمكنك مراسلة نفسك"; return false }
        return true
    }

    fun sendFile(to: String, doc: PickedDoc, caption: String, replyTo: Message?) {
        val me = meId ?: return
        if (!canSendTo(to)) return
        viewModelScope.launch {
            val path = "$me/$to/${System.currentTimeMillis()}_${rand()}.${Media.safeExt(doc.name)}"
            toast = "جارٍ رفع الملف..."
            runCatching {
                Repo.upload(Repo.FILES_BUCKET, path, doc.bytes, doc.mime)
                val row = buildJsonObject {
                    baseRow(to, caption.ifEmpty { "📎 ${doc.name}" }, replyTo?.id).forEach { (k, v) -> put(k, v) }
                    put("file_path", path); put("file_name", doc.name); put("file_type", doc.mime)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                runCatching { Repo.remove(Repo.FILES_BUCKET, path) }
                toast = if (it.message?.contains("not found", true) == true || it.message?.contains("Bucket", true) == true)
                    "bucket المستندات غير موجود — شغّل supabase/features.sql" else rlsMsg(it)
            }
        }
    }

    fun sendLocation(to: String, lat: Double, lng: Double, replyTo: Message?) {
        if (!canSendTo(to)) return
        viewModelScope.launch {
            runCatching {
                addMsg(Repo.insertMessage(baseRow(to, "📍 موقعي\n" + geoUrl(lat, lng), replyTo?.id)))
            }.onFailure { toast = rlsMsg(it) }
        }
    }

    fun sendAudio(to: String, file: File, secs: Int) {
        val me = meId ?: return
        viewModelScope.launch {
            val path = "$me/$to/${System.currentTimeMillis()}_${rand()}.m4a"
            runCatching {
                Repo.upload("chat-audio", path, file.readBytes(), "audio/mp4")
                val row = buildJsonObject {
                    baseRow(to, "🎤 رسالة صوتية", null).forEach { (k, v) -> put(k, v) }
                    put("audio_path", path)
                    put("audio_secs", secs)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                runCatching { Repo.remove("chat-audio", path) }
                toast = rlsMsg(it)
            }
            file.delete()
        }
    }

    /** إعادة توجيه رسالة إلى عدة محادثات. الوسائط تُنزَّل وتُرفع من جديد تحت مسار المستلم الجديد. */
    fun forward(m: Message, targets: Collection<String>) {
        val me = meId ?: return
        if (m.isDeleted || (m.viewOnce == true) || targets.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            var imgBytes: ByteArray? = null
            var audBytes: ByteArray? = null
            var fileBytes: ByteArray? = null
            for (to in targets) {
                if (to == me || to in blocked) { fail++; continue }
                val uploaded = mutableListOf<Pair<String, String>>()
                runCatching {
                    val row = buildJsonObject {
                        baseRow(to, m.text, null).forEach { (k, v) -> put(k, v) }
                        put("forwarded", true)
                    }
                    val fields = HashMap<String, kotlinx.serialization.json.JsonElement>()
                    val stamp = "${System.currentTimeMillis()}_${rand()}"
                    m.imagePath?.let { src ->
                        val b = imgBytes ?: Repo.download("chat-images", src).also { imgBytes = it }
                        val np = "$me/$to/$stamp.jpg"
                        Repo.upload("chat-images", np, b, "image/jpeg"); uploaded += "chat-images" to np
                        fields["image_path"] = kotlinx.serialization.json.JsonPrimitive(np)
                    }
                    m.audioPath?.let { src ->
                        val b = audBytes ?: Repo.download("chat-audio", src).also { audBytes = it }
                        val np = "$me/$to/$stamp.m4a"
                        Repo.upload("chat-audio", np, b, "audio/mp4"); uploaded += "chat-audio" to np
                        fields["audio_path"] = kotlinx.serialization.json.JsonPrimitive(np)
                        m.audioSecs?.let { fields["audio_secs"] = kotlinx.serialization.json.JsonPrimitive(it.toInt()) }
                    }
                    m.filePath?.let { src ->
                        val b = fileBytes ?: Repo.download(Repo.FILES_BUCKET, src).also { fileBytes = it }
                        val np = "$me/$to/$stamp.${Media.safeExt(m.fileName ?: "")}"
                        Repo.upload(Repo.FILES_BUCKET, np, b, m.fileType ?: "application/octet-stream")
                        uploaded += Repo.FILES_BUCKET to np
                        fields["file_path"] = kotlinx.serialization.json.JsonPrimitive(np)
                        fields["file_name"] = kotlinx.serialization.json.JsonPrimitive(m.fileName ?: "ملف")
                        fields["file_type"] = kotlinx.serialization.json.JsonPrimitive(m.fileType ?: "application/octet-stream")
                    }
                    val full = buildJsonObject {
                        row.forEach { (k, v) -> put(k, v) }
                        fields.forEach { (k, v) -> put(k, v) }
                    }
                    addMsg(Repo.insertMessage(full))
                }.onSuccess { ok++ }.onFailure {
                    fail++
                    uploaded.forEach { (bk, p) -> runCatching { Repo.remove(bk, p) } }
                }
            }
            toast = when {
                fail == 0 -> "تمت إعادة التوجيه"
                ok == 0 -> "تعذّرت إعادة التوجيه"
                else -> "أُعيد توجيهها إلى $ok وفشلت $fail"
            }
        }
    }

    /** يُستدعى عند فتح صورة «مرة واحدة» — تُعتبر مستهلَكة فوراً. */
    fun markViewed(m: Message) {
        if (m.sender == meId || m.viewedAt != null) return
        val iso = Instant.now().toString()
        val i = msgs.indexOfFirst { it.id == m.id }
        if (i >= 0) msgs[i] = msgs[i].copy(viewedAt = iso)
        viewModelScope.launch {
            runCatching { Repo.markViewed(m.id, iso) }
                .onFailure { toast = "تعذّر تسجيل المشاهدة: ${it.message}" }
        }
    }

    fun saveProfile(
        displayName: String, about: String, bio: String, city: String,
        birthday: String?, links: List<ProfileLink>, onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        val me = meId ?: return@launch
        runCatching { Repo.saveProfile(me, displayName, about, bio, city, birthday, links) }
            .onSuccess {
                peers[me]?.let { p ->
                    peers[me] = p.copy(
                        displayName = displayName.ifBlank { null }, about = about, bio = bio.ifBlank { null },
                        city = city.ifBlank { null }, birthday = birthday, links = links
                    )
                }
                toast = "تم حفظ الملف الشخصي"; onDone()
            }
            .onFailure {
                toast = if (it.message?.contains("column", true) == true)
                    "شغّل supabase/features_v2.sql في Supabase أولاً" else "فشل الحفظ: ${it.message}"
            }
    }

    fun editMessage(m: Message, body: String) = viewModelScope.launch {
        runCatching { Repo.editMessage(m.id, body) }
            .onSuccess {
                val i = msgs.indexOfFirst { it.id == m.id }
                if (i >= 0) msgs[i] = msgs[i].copy(body = body, edited = true)
            }
            .onFailure { toast = "فشل التعديل: ${it.message}" }
    }

    fun deleteForAll(m: Message) = viewModelScope.launch {
        runCatching {
            Repo.markDeleted(m.id)
            m.imagePath?.let { Repo.remove("chat-images", it); Media.evict(it) }
            m.audioPath?.let { Repo.remove("chat-audio", it) }
            m.filePath?.let { Repo.remove(Repo.FILES_BUCKET, it) }
        }.onSuccess {
            val i = msgs.indexOfFirst { it.id == m.id }
            if (i >= 0) msgs[i] = msgs[i].copy(deleted = true)
        }.onFailure { toast = "فشل الحذف: ${it.message}" }
    }

    /** حذف المحادثة من جهازي فقط (تعود الرسائل الجديدة طبيعياً). */
    private fun loadHidden(id: String) {
        val key = "hidden_$id"
        hidden = when {
            prefs.contains(key) -> prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
            // ترحيل قيمة النسخ السابقة (كانت مشتركة) إلى أول حساب يدخل بعد التحديث
            prefs.contains("hidden") -> (prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()).also {
                prefs.edit().putStringSet(key, it).remove("hidden").apply()
            }
            else -> emptySet()
        }
    }

    private fun saveHidden() {
        val me = meId ?: return
        prefs.edit().putStringSet("hidden_$me", hidden).apply()
    }

    fun hideChat(peer: String) {
        hidden = hidden + thread(peer).map { it.id }
        saveHidden()
        toast = "تم حذف المحادثة"
    }

    /** أرشفة كل المحادثات الظاهرة (من الإعدادات). */
    fun archiveAllChats() {
        val list = conversations().map { it.first }.filter { !isArchived(it) }
        list.forEach { toggleArchive(it) }
        toast = if (list.isEmpty()) "لا توجد محادثات لأرشفتها" else "تمت أرشفة ${list.size} محادثة"
    }

    /** إخراج كل المحادثات من الأرشيف. */
    fun unarchiveAllChats() {
        val list = conversations().map { it.first }.filter { isArchived(it) }
        list.forEach { toggleArchive(it) }
        toast = if (list.isEmpty()) "لا توجد محادثات مؤرشفة" else "أُعيدت ${list.size} محادثة من الأرشيف"
    }

    /** حذف كل المحادثات من هذا الجهاز فقط (لا تتأثر عند الطرف الآخر). */
    fun clearAllChats() {
        hidden = hidden + msgs.map { it.id }
        saveHidden()
        toast = "تم حذف كل المحادثات من هذا الجهاز"
    }

    fun hideLocal(m: Message) {
        hidden = hidden + m.id
        saveHidden()
    }

    fun react(m: Message, emoji: String) {
        val me = meId ?: return
        val mine = reactions.firstOrNull { it.messageId == m.id && it.userId == me }
        viewModelScope.launch {
            runCatching {
                if (mine?.emoji == emoji) {
                    Repo.removeReaction(m.id, me)
                    reactions.remove(mine)
                } else {
                    Repo.setReaction(m.id, me, emoji)
                    reactions.removeAll { it.messageId == m.id && it.userId == me }
                    reactions.add(Reaction(m.id, me, emoji))
                }
            }.onFailure { toast = "تعذّر التفاعل" }
        }
    }

    fun block(id: String) = viewModelScope.launch {
        val me = meId ?: return@launch
        runCatching { Repo.block(me, id) }.onSuccess { blocked = blocked + id }
            .onFailure { toast = "تعذّر الحظر" }
    }

    fun unblock(id: String) = viewModelScope.launch {
        runCatching { Repo.unblock(id) }.onSuccess { blocked = blocked - id }
            .onFailure { toast = "تعذّر إلغاء الحظر" }
    }

    // ---------- users / profile ----------
    suspend fun search(q: String): List<Profile> {
        val me = meId ?: return emptyList()
        val l = runCatching { Repo.searchUsers(q, me) }.getOrDefault(emptyList())
        l.forEach { peers[it.id] = it }
        return l
    }

    fun saveAbout(text: String) = viewModelScope.launch {
        val me = meId ?: return@launch
        runCatching { Repo.setAbout(me, text) }
            .onSuccess { peers[me]?.let { p -> peers[me] = p.copy(about = text) }; toast = "تم الحفظ" }
            .onFailure { toast = "فشل الحفظ: ${it.message}" }
    }

    fun setAvatar(bytes: ByteArray) = viewModelScope.launch {
        val me = meId ?: return@launch
        val old = peers[me]?.avatarPath
        val path = "$me/${System.currentTimeMillis()}.jpg"
        runCatching {
            Repo.upload("avatars", path, bytes, "image/jpeg", upsert = true)
            Repo.setAvatarPath(me, path)
            peers[me]?.let { peers[me] = it.copy(avatarPath = path) }
            old?.let { runCatching { Repo.remove("avatars", it) } }
        }.onSuccess { toast = "تم تحديث الصورة" }.onFailure { toast = "فشل رفع الصورة: ${it.message}" }
    }

    fun removeAvatar() = viewModelScope.launch {
        val me = meId ?: return@launch
        val old = peers[me]?.avatarPath ?: return@launch
        runCatching {
            Repo.setAvatarPath(me, null)
            Repo.remove("avatars", old)
        }.onSuccess { peers[me]?.let { p -> peers[me] = p.copy(avatarPath = null) } }
    }
}

fun timerLabel(secs: Int): String = when {
    secs <= 0 -> "إيقاف"
    secs % 86400 == 0 -> if (secs / 86400 == 1) "24 ساعة" else "${secs / 86400} أيام"
    secs % 3600 == 0 -> "${secs / 3600} ساعة"
    secs % 60 == 0 -> "${secs / 60} دقيقة"
    else -> "$secs ثانية"
}
