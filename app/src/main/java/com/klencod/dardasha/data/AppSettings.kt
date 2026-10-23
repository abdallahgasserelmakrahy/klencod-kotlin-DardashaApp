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

class StringSetting(reg: MutableList<SettingLoader>, private val key: String, private val def: String) : SettingLoader {
    private var st by mutableStateOf(def)
    init { reg += this }
    operator fun getValue(t: Any?, p: KProperty<*>): String = st
    operator fun setValue(t: Any?, p: KProperty<*>, v: String) { st = v; AppSettings.write { putString(key, v) } }
    override fun load(sp: SharedPreferences) { st = sp.getString(key, def) ?: def }
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

    // ---------- التخصيص: الألوان ----------
    /** خلفية الوضع الداكن: 0 = أسود نقي، 1 = فحمي، 2 = أزرق ليلي. */
    var darkBg by IntSetting(reg, "c_dark_bg", 0)
    /** تلوين عناصر النظام (الأزرق) بلون التمييز المختار. */
    var iosUseAccent by BoolSetting(reg, "c_ios_use_accent", false)
    /** درجة اللون (0–359) عند اختيار لون تمييز «مخصص» (رقم = عدد الألوان الجاهزة). */
    var customHue by IntSetting(reg, "c_custom_hue", 210)
    /** لون فقاعتي: 0 = افتراضي، 1 = لون التمييز، 2 = مخصص. */
    var bubbleColorMode by IntSetting(reg, "c_bubble_mode", 0)
    var bubbleHue by IntSetting(reg, "c_bubble_hue", 145)

    // ---------- التخصيص: الأحجام ----------
    /** حجم الواجهة كاملة % (يكبّر/يصغّر كل شيء). */
    var uiScale by IntSetting(reg, "s_ui_scale", 100)
    /** حجم الخط العام % فوق حجم الواجهة. */
    var textScale by IntSetting(reg, "s_text_scale", 100)
    /** حجم العناوين الكبيرة (sp). */
    var largeTitleSp by IntSetting(reg, "s_large_title", 34)
    /** حجم خط الرسائل المخصص (sp)؛ 0 = حسب المستويات الأربعة. */
    var msgFontCustom by IntSetting(reg, "s_msg_font", 0)
    /** حجم الصور الشخصية % في كل التطبيق. */
    var avatarScale by IntSetting(reg, "s_avatar_scale", 100)
    /** شكل الصور الشخصية: 0 = دائرة، 1 = مربع بزوايا ناعمة. */
    var avatarShape by IntSetting(reg, "s_avatar_shape", 0)
    /** ارتفاع صفوف الإعدادات (dp). */
    var rowHeight by IntSetting(reg, "s_row_height", 46)
    var listNameSp by IntSetting(reg, "s_list_name", 17)
    var listPreviewSp by IntSetting(reg, "s_list_preview", 15)
    var listRowPad by IntSetting(reg, "s_list_pad", 10)

    // ---------- التخصيص: فقاعات الدردشة ----------
    var bubbleRadius by IntSetting(reg, "b_radius", 18)
    var bubblePadH by IntSetting(reg, "b_pad_h", 12)
    var bubblePadV by IntSetting(reg, "b_pad_v", 7)
    /** أقصى عرض للفقاعة % من عرض الشاشة. */
    var bubbleMaxPct by IntSetting(reg, "b_max_pct", 76)
    /** عرض الصور داخل الفقاعة (dp). */
    var chatImageW by IntSetting(reg, "b_image_w", 240)

    // ---------- التخصيص: شريط التبويبات ----------
    var tabHeight by IntSetting(reg, "t_height", 62)
    var tabSideMargin by IntSetting(reg, "t_side", 21)
    /** إزاحة إضافية للشريط عن الحافة السفلية (dp، قد تكون سالبة). */
    var tabBottomOffset by IntSetting(reg, "t_bottom", 0)
    /** استدارة الزوايا %: 100 = كبسولة كاملة، 0 = مستطيل. */
    var tabCornerPct by IntSetting(reg, "t_corner", 100)
    var tabIconSize by IntSetting(reg, "t_icon", 25)
    var tabLabelSize by IntSetting(reg, "t_label", 10)
    /** 0 = دائماً، 1 = للتبويب المحدد فقط، 2 = مخفية. */
    var tabLabelMode by IntSetting(reg, "t_label_mode", 0)
    /** قوة تمويه الزجاج (dp)؛ 0 = بلا تمويه. */
    var tabBlur by IntSetting(reg, "t_blur", 24)
    /** كثافة صبغة الزجاج % (100 = القياسي). */
    var tabTint by IntSetting(reg, "t_tint", 100)
    /** تشبّع ألوان الخلفية المموّهة %. */
    var tabSaturation by IntSetting(reg, "t_sat", 170)
    var tabShadow by BoolSetting(reg, "t_shadow", true)
    var tabRim by BoolSetting(reg, "t_rim", true)
    /** شكل عدسة التحديد: 0 = زجاجية، 1 = ملوّنة، 2 = بدون. */
    var tabLens by IntSetting(reg, "t_lens", 0)
    var tabStretch by BoolSetting(reg, "t_stretch", true)
    var tabBounce by BoolSetting(reg, "t_bounce", true)
    var tabHaptics by BoolSetting(reg, "t_haptics", true)
    /** لون التحديد: 0 = أزرق النظام، 1 = لون التمييز، 2 = أحادي، 3 = مخصص. */
    var tabSelColor by IntSetting(reg, "t_sel_color", 0)
    var tabCustomHue by IntSetting(reg, "t_hue", 210)
    var tabBadge by BoolSetting(reg, "t_badge", true)
    /** 0 = رقم، 1 = نقطة فقط. */
    var tabBadgeStyle by IntSetting(reg, "t_badge_style", 0)
    /** ترتيب التبويبات (فهارس مفصولة بفاصلة). */
    var tabOrder by StringSetting(reg, "t_order", "0,1,2,3")
    /** قناع بتّات للتبويبات المخفية (بتّ = فهرس التبويب). */
    var tabHidden by IntSetting(reg, "t_hidden", 0)

    // ---------- قيم مشتقة ----------
    val msgFontSp: Int get() = if (msgFontCustom > 0) msgFontCustom else when (fontSize) { 0 -> 15; 2 -> 19; 3 -> 22; else -> 17 }
    val stampGapMin: Int get() = when (stampGap) { 1 -> 15; 2 -> 5; else -> 45 }
    val imageMaxPx: Int get() = when (imageQuality) { 0 -> 1024; 2 -> 1920; else -> 1280 }
    val imageJpegQuality: Int get() = when (imageQuality) { 0 -> 65; 2 -> 90; else -> 80 }

    fun resetColors() { theme = 2; accent = 0; darkBg = 0; iosUseAccent = false; customHue = 210; bubbleColorMode = 0; bubbleHue = 145 }

    fun resetSizes() {
        uiScale = 100; textScale = 100; largeTitleSp = 34; msgFontCustom = 0; fontSize = 1
        avatarScale = 100; avatarShape = 0; rowHeight = 46; listNameSp = 17; listPreviewSp = 15; listRowPad = 10
    }

    fun resetBubbles() { bubbleRadius = 18; bubblePadH = 12; bubblePadV = 7; bubbleMaxPct = 76; chatImageW = 240; bubbleTail = true }

    fun resetTabBar() {
        tabHeight = 62; tabSideMargin = 21; tabBottomOffset = 0; tabCornerPct = 100
        tabIconSize = 25; tabLabelSize = 10; tabLabelMode = 0
        tabBlur = 24; tabTint = 100; tabSaturation = 170; tabShadow = true; tabRim = true
        tabLens = 0; tabStretch = true; tabBounce = true; tabHaptics = true
        tabSelColor = 0; tabCustomHue = 210; tabBadge = true; tabBadgeStyle = 0
        tabOrder = "0,1,2,3"; tabHidden = 0
    }

    /** يعيد كل الإعدادات إلى قيمها الافتراضية. */
    fun resetAll() {
        theme = 2; accent = 0; fontSize = 1; enterSends = false; chatPattern = false
        notifications = true; notifPreview = true; notifSound = true; notifVibrate = true
        showLastSeen = true; readReceipts = true; typingIndicator = true; screenSecurity = false
        autoDownload = 0; imageQuality = 1
        keepAwake = false; bubbleTail = true; stampGap = 0; waShareMenu = true; antiDelete = false
        resetColors(); resetSizes(); resetBubbles(); resetTabBar()
    }
}
