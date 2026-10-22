package com.klencod.dardasha.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

/**
 * إعدادات التطبيق المحلية (على هذا الجهاز). كل قيمة محفوظة في SharedPreferences
 * وتُعرض كحالة Compose فتتحدّث الواجهة فور تغييرها.
 */
interface SettingLoader { fun load(sp: SharedPreferences) }

class BoolSetting(reg: MutableList<SettingLoader>, private val key: String, private val def: Boolean) : SettingLoader {
    private var st by mutableStateOf(def)
    init { reg += this }
    operator fun getValue(t: Any?, p: KProperty<*>): Boolean = st
    operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { st = v; AppSettings.write { putBoolean(key, v) } }
    override fun load(sp: SharedPreferences) { st = sp.getBoolean(key, def) }
}

class IntSetting(reg: MutableList<SettingLoader>, private val key: String, private val def: Int) : SettingLoader {
    private var st by mutableStateOf(def)
    init { reg += this }
    operator fun getValue(t: Any?, p: KProperty<*>): Int = st
    operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { st = v; AppSettings.write { putInt(key, v) } }
    override fun load(sp: SharedPreferences) { st = sp.getInt(key, def) }
}

object AppSettings {
    private val reg = ArrayList<SettingLoader>()
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("dardasha_settings", Context.MODE_PRIVATE)
        sp = p
        reg.forEach { it.load(p) }
    }

    internal fun write(f: SharedPreferences.Editor.() -> Unit) {
        sp?.edit()?.apply(f)?.apply()
    }

    // ---------- المظهر ----------
    /** 0 = حسب النظام، 1 = فاتح، 2 = داكن (الافتراضي كما كان التطبيق). */
    var theme by IntSetting(reg, "theme", 2)
    /** رقم لون التمييز في قائمة ACCENTS. */
    var accent by IntSetting(reg, "accent", 0)

    // ---------- الدردشات ----------
    /** 0 = صغير، 1 = متوسط، 2 = كبير، 3 = كبير جداً. */
    var fontSize by IntSetting(reg, "font_size", 1)
    var enterSends by BoolSetting(reg, "enter_sends", false)
    var chatPattern by BoolSetting(reg, "chat_pattern2", false)

    // ---------- الإشعارات ----------
    var notifications by BoolSetting(reg, "notif", true)
    var notifPreview by BoolSetting(reg, "notif_preview", true)
    var notifSound by BoolSetting(reg, "notif_sound", true)
    var notifVibrate by BoolSetting(reg, "notif_vibrate", true)

    // ---------- واتساب ----------
    /** حفظ رسائل واتساب الواردة (من الإشعارات) محلياً على هذا الجهاز للرد السريع. */
    var waInbox by BoolSetting(reg, "wa_inbox", false)

    // ---------- Plus ----------
    /** إبقاء الشاشة مضيئة أثناء فتح دردشة. */
    var keepAwake by BoolSetting(reg, "plus_keep_awake", false)
    /** إظهار ذيل الفقاعة (الزاوية المدبّبة) في آخر رسالة من كل مجموعة. */
    var bubbleTail by BoolSetting(reg, "plus_bubble_tail", true)
    /** الفجوة التي تُظهر طابع الوقت بين الرسائل: 0 = 45 دقيقة، 1 = 15 دقيقة، 2 = 5 دقائق. */
    var stampGap by IntSetting(reg, "plus_stamp_gap", 0)
    /** إظهار «مشاركة عبر واتساب» في قائمة الرسالة. */
    var waShareMenu by BoolSetting(reg, "plus_wa_share_menu", true)
    /** منع حذف الرسائل: تبقى رسالة الطرف الآخر ظاهرة عندك حتى لو حذفها للجميع. */
    var antiDelete by BoolSetting(reg, "plus_anti_delete", false)

    // ---------- الخصوصية ----------
    /** إظهار «متصل الآن» و«آخر ظهور» للآخرين. */
    var showLastSeen by BoolSetting(reg, "show_last_seen", true)
    /** إرسال إشعارات القراءة (علامتا ✓✓ الزرقاوان) للمرسِل. */
    var readReceipts by BoolSetting(reg, "read_receipts", true)
    /** إظهار «يكتب...» للطرف الآخر أثناء الكتابة. */
    var typingIndicator by BoolSetting(reg, "typing_indicator", true)
    /** منع لقطات الشاشة وإخفاء التطبيق في قائمة التطبيقات الأخيرة. */
    var screenSecurity by BoolSetting(reg, "screen_security", false)

    // ---------- التخزين والبيانات ----------
    /** تنزيل صور المحادثات تلقائياً: 0 = دائماً، 1 = عبر Wi-Fi فقط، 2 = أبداً (بالضغط). */
    var autoDownload by IntSetting(reg, "auto_download", 0)
    /** جودة الصور المرسلة: 0 = توفير بيانات، 1 = عادية، 2 = عالية. */
    var imageQuality by IntSetting(reg, "image_quality", 1)

    // ---------- قيم مشتقة ----------
    val msgFontSp: Int get() = when (fontSize) { 0 -> 15; 2 -> 19; 3 -> 22; else -> 17 }
    val stampGapMin: Int get() = when (stampGap) { 1 -> 15; 2 -> 5; else -> 45 }
    val imageMaxPx: Int get() = when (imageQuality) { 0 -> 1024; 2 -> 1920; else -> 1280 }
    val imageJpegQuality: Int get() = when (imageQuality) { 0 -> 65; 2 -> 90; else -> 80 }

    /** يعيد كل الإعدادات إلى قيمها الافتراضية. */
    fun resetAll() {
        theme = 2; accent = 0; fontSize = 1; enterSends = false; chatPattern = false
        notifications = true; notifPreview = true; notifSound = true; notifVibrate = true
        showLastSeen = true; readReceipts = true; typingIndicator = true; screenSecurity = false
        autoDownload = 0; imageQuality = 1
        keepAwake = false; bubbleTail = true; stampGap = 0; waShareMenu = true; antiDelete = false
    }
}
