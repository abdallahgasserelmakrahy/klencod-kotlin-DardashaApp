package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.SettingsSection
import com.klencod.dardasha.data.AppSettings
import kotlin.math.roundToInt

// =====================================================================================
//  قسم «التخصيص والأحجام»: تحكّم كامل بمظهر وأحجام كل شيء + تخصيص شريط التبويبات
// =====================================================================================

private val CUSTOM_PAGES = setOf(
    SettingsSection.CustomColors, SettingsSection.CustomSizes,
    SettingsSection.CustomBubbles, SettingsSection.CustomTabBar
)

/** هل هذه صفحة فرعية داخل قسم التخصيص (للرجوع إلى القسم لا إلى الإعدادات). */
internal fun isCustomSubPage(s: SettingsSection): Boolean = s in CUSTOM_PAGES

private fun toHub(vm: MainVM) { vm.route = Route.SettingsPage(SettingsSection.Customize) }

private val THEME_NAMES = listOf("حسب النظام", "فاتح", "داكن")

// ───────────────────────── منزلق iOS ─────────────────────────

/** منزلق بأسلوب iOS: مسار رفيع، إبهام أبيض بظل، يتبع اتجاه RTL، واهتزاز خفيف عند تغيّر القيمة. */
@Composable
internal fun IosSlider(
    value: Int, min: Int, max: Int,
    onChange: (Int) -> Unit, onFinish: () -> Unit = {},
    step: Int = 1, modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val padPx = with(density) { 14.dp.toPx() }
    val curChange by rememberUpdatedState(onChange)
    val curFinish by rememberUpdatedState(onFinish)
    val frac = if (max > min) ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f) else 0f

    Box(
        modifier.fillMaxWidth().height(34.dp)
            .pointerInput(min, max, step, rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun valueAt(x: Float): Int {
                        val w = (size.width - 2 * padPx).coerceAtLeast(1f)
                        var f = ((x - padPx) / w).coerceIn(0f, 1f)
                        if (rtl) f = 1f - f
                        val raw = min + f * (max - min)
                        val snapped = min + ((raw - min) / step).roundToInt() * step
                        return snapped.coerceIn(min, max)
                    }
                    var v = valueAt(down.position.x)
                    curChange(v)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val nv = valueAt(ch.position.x)
                        if (nv != v) {
                            v = nv
                            curChange(v)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        ch.consume()
                    }
                    curFinish()
                }
            }
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(IOS.fill))
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(IOS.blue))
        }
        Box(
            Modifier.align(BiasAlignment(frac * 2f - 1f, 0f)).size(28.dp)
                .shadow(3.dp, CircleShape).background(Color.White, CircleShape)
        )
    }
}

/** صف إعداد بمنزلق: عنوان + قيمة حالية + منزلق. [deferred] = لا يُطبَّق إلا عند رفع الإصبع (للأحجام العامة). */
@Composable
internal fun SSlider(
    title: String, value: Int, min: Int, max: Int,
    unit: String = "", step: Int = 1, last: Boolean = false, deferred: Boolean = false,
    display: ((Int) -> String)? = null, onChange: (Int) -> Unit,
) {
    var tmp by remember(value) { mutableIntStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) tmp else value
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(display?.invoke(shown) ?: "$shown$unit", color = IOS.secondary, fontSize = 17.sp)
        }
        IosSlider(
            shown, min, max, step = step,
            onChange = { tmp = it; dragging = true; if (!deferred) onChange(it) },
            onFinish = { if (deferred) onChange(tmp); dragging = false }
        )
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 16.dp), color = IOS.separator, thickness = 0.5.dp)
}

/** صف Segmented داخل مجموعة (عنوان فرعي اختياري). */
@Composable
private fun SSegment(title: String?, options: List<String>, selected: Int, last: Boolean = false, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (title != null) {
            Text(title, color = IOS.label, fontSize = 17.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        IosSegmented(options, selected.coerceIn(0, options.lastIndex)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(it)
        }
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 16.dp), color = IOS.separator, thickness = 0.5.dp)
}

/** منتقي درجة اللون: منزلق 0–359 مع عيّنة اللون. */
@Composable
private fun HueRow(title: String, hue: Int, last: Boolean = false, onChange: (Int) -> Unit) {
    val sample = Color.hsl(hue.coerceIn(0, 359).toFloat(), 0.8f, 0.55f)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(22.dp).clip(CircleShape).background(sample))
    }
    IosSlider(hue, 0, 359, onChange = onChange)
    if (!last) HorizontalDivider(Modifier.padding(start = 16.dp), color = IOS.separator, thickness = 0.5.dp)
}

// ───────────────────────── معاينات حيّة ─────────────────────────

/** معاينة الفقاعات بنفس الأبعاد والألوان التي ستظهر في المحادثة. */
@Composable
private fun BubblePreview() {
    val r = AppSettings.bubbleRadius.dp
    val tailR = minOf(4, AppSettings.bubbleRadius).dp
    val px = AppSettings.bubblePadH.dp
    val py = AppSettings.bubblePadV.dp
    val fs = AppSettings.msgFontSp.sp
    Column(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(IOS.bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.clip(
                    RoundedCornerShape(topStart = r, topEnd = r, bottomStart = r, bottomEnd = if (AppSettings.bubbleTail) tailR else r)
                ).background(WA.received).padding(horizontal = px, vertical = py)
            ) { Text("مرحباً! كيف حالك؟", color = IOS.label, fontSize = fs) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.clip(
                    RoundedCornerShape(topStart = r, topEnd = r, bottomStart = if (AppSettings.bubbleTail) tailR else r, bottomEnd = r)
                ).background(WA.sent).padding(horizontal = px, vertical = py)
            ) { Text("بخير، شكراً لك 🌿", color = Color.White, fontSize = fs) }
        }
    }
}

/** معاينة صف قائمة المحادثات. */
@Composable
private fun ListRowPreview() {
    Row(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(IOS.bg)
            .padding(horizontal = 16.dp, vertical = AppSettings.listRowPad.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar("أحمد", null, 54.dp, online = true, ring = IOS.bg, plain = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("أحمد", color = IOS.label, fontSize = AppSettings.listNameSp.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("٩:٤١", color = IOS.secondary, fontSize = 15.sp)
            }
            Text("أراك في المساء إن شاء الله", color = IOS.secondary, fontSize = AppSettings.listPreviewSp.sp, maxLines = 1)
        }
    }
}

/** معاينة حيّة لشريط التبويبات الزجاجي فوق خلفية ملوّنة (التمويه حقيقي). */
@Composable
private fun TabBarPreview() {
    val bd = rememberGlassBackdrop()
    val tabs = visibleTabs()
    var sel by remember { mutableIntStateOf(0) }
    SideEffect { bd.active = true }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).height(180.dp).clip(RoundedCornerShape(16.dp))) {
        Box(
            Modifier.fillMaxSize().glassSource(bd).background(
                Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFF4D96FF), Color(0xFF6BCB77)))
            )
        ) {
            Box(Modifier.padding(start = 24.dp, top = 22.dp).size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
            Box(Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 40.dp).size(90.dp).clip(CircleShape).background(Color(0xFF1C1C1E).copy(alpha = 0.55f)))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 30.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(150.dp).height(10.dp).clip(CircleShape).background(Color.White))
                Box(Modifier.width(100.dp).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
            }
        }
        GlassTabBar(
            tabs = tabs, selIdx = sel.coerceIn(0, tabs.size - 1), enabled = true, badge = 3, backdrop = bd,
            onPick = { sel = it },
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = AppSettings.tabSideMargin.coerceAtMost(40).dp).padding(bottom = 14.dp)
        )
    }
}

// ───────────────────────── الإعدادات الجاهزة ─────────────────────────

private class Preset(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color, val action: () -> Unit)

private fun resetAllLooks() {
    AppSettings.resetColors(); AppSettings.resetSizes(); AppSettings.resetBubbles(); AppSettings.resetTabBar()
}

private fun presets() = listOf(
    Preset("الافتراضي (iOS 26)", Icons.Filled.RestartAlt, IOS.blue) { resetAllLooks() },
    Preset("مدمج — يعرض أكثر", Icons.Filled.ViewDay, IOS.orange) {
        resetAllLooks()
        AppSettings.uiScale = 92; AppSettings.listRowPad = 7; AppSettings.avatarScale = 88; AppSettings.rowHeight = 40
        AppSettings.bubblePadV = 5; AppSettings.tabHeight = 56; AppSettings.tabIconSize = 23; AppSettings.tabLabelMode = 1
    },
    Preset("مريح للقراءة — كبير", Icons.Filled.FormatSize, IosGreen) {
        resetAllLooks()
        AppSettings.uiScale = 112; AppSettings.textScale = 108; AppSettings.msgFontCustom = 20; AppSettings.rowHeight = 54
        AppSettings.listRowPad = 14; AppSettings.avatarScale = 110; AppSettings.tabHeight = 70; AppSettings.tabIconSize = 28
        AppSettings.tabLabelSize = 11; AppSettings.bubblePadV = 9; AppSettings.bubblePadH = 14
    },
    Preset("زجاج شفاف", Icons.Filled.Palette, IOS.indigo) {
        resetAllLooks()
        AppSettings.tabBlur = 38; AppSettings.tabTint = 55; AppSettings.tabSaturation = 220; AppSettings.tabHeight = 66
        AppSettings.tabSideMargin = 16; AppSettings.tabLens = 0
    },
    Preset("مربّع كلاسيكي", Icons.Filled.LinearScale, IOS.gray) {
        resetAllLooks()
        AppSettings.tabCornerPct = 38; AppSettings.tabSideMargin = 10; AppSettings.tabBottomOffset = -6; AppSettings.tabHeight = 58
        AppSettings.avatarShape = 1; AppSettings.bubbleRadius = 12; AppSettings.tabLens = 1
    },
)

// ───────────────────────── الصفحة الرئيسية للقسم ─────────────────────────

@Composable
internal fun CustomizeHubPage(vm: MainVM) {
    var confirmReset by remember { mutableStateOf(false) }
    SettingsScaffold("التخصيص والأحجام", "الإعدادات", { vm.route = Route.Settings }) {
        SGroup(
            header = "إعدادات جاهزة",
            footer = "اختر نمطاً جاهزاً ثم عدّل أي تفصيل بالصفحات أدناه. تُطبَّق التغييرات فوراً وتُحفظ على هذا الجهاز."
        ) {
            val list = presets()
            list.forEachIndexed { i, p ->
                SCell(p.name, last = i == list.lastIndex, icon = p.icon, tint = p.tint) {
                    p.action(); vm.toast = "تم تطبيق: ${p.name}"
                }
            }
        }
        SGroup(header = "تخصيص التفاصيل") {
            SCell("الألوان والمظهر", last = false, icon = Icons.Filled.Palette, tint = IOS.indigo, chevron = true) {
                vm.route = Route.SettingsPage(SettingsSection.CustomColors)
            }
            SCell("الأحجام (الواجهة والخطوط والصور)", last = false, icon = Icons.Filled.FormatSize, tint = IOS.orange, chevron = true) {
                vm.route = Route.SettingsPage(SettingsSection.CustomSizes)
            }
            SCell("الدردشات والفقاعات", last = false, icon = Icons.Filled.ChatBubble, tint = IosGreen, chevron = true) {
                vm.route = Route.SettingsPage(SettingsSection.CustomBubbles)
            }
            SCell("شريط التبويبات", last = true, icon = Icons.Filled.LinearScale, tint = IOS.blue, chevron = true) {
                vm.route = Route.SettingsPage(SettingsSection.CustomTabBar)
            }
        }
        SGroup(footer = "يعيد كل خيارات هذا القسم إلى الوضع الافتراضي. لا يمسّ حسابك ولا رسائلك.") {
            SCenter("إعادة ضبط كل التخصيصات", IOS.red) { confirmReset = true }
        }
        if (confirmReset) IosAlert(
            title = "إعادة ضبط التخصيص؟",
            message = "ستعود الألوان والأحجام والفقاعات وشريط التبويبات إلى الشكل الافتراضي.",
            confirmText = "إعادة ضبط",
            onConfirm = { confirmReset = false; resetAllLooks(); vm.toast = "تمت إعادة الضبط" },
            onDismiss = { confirmReset = false }
        )
    }
}

// ───────────────────────── الألوان والمظهر ─────────────────────────

@Composable
internal fun CustomColorsPage(vm: MainVM) {
    val haptic = LocalHapticFeedback.current
    val accentIdx = AppSettings.accent.coerceIn(0, ACCENTS.size)
    val customSel = accentIdx >= ACCENTS.size
    SettingsScaffold("الألوان والمظهر", "التخصيص", { toHub(vm) }) {
        SGroup(header = "معاينة") { BubblePreview() }
        SGroup(header = "الوضع") {
            SSegment(null, THEME_NAMES, AppSettings.theme, last = true) { AppSettings.theme = it }
        }
        SGroup(header = "لون التمييز", footer = "اللون المخصّص يتيح لك اختيار أي درجة لون.") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                ACCENTS.forEachIndexed { i, a ->
                    val sel = accentIdx == i
                    val col = if (C.dark) a.dark else a.light
                    Box(
                        Modifier.size(38.dp).then(if (sel) Modifier.border(2.5.dp, col, CircleShape) else Modifier)
                            .tap { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); AppSettings.accent = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(if (sel) 29.dp else 38.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                            if (sel) Icon(Icons.Filled.Check, a.name, tint = if (C.dark) a.onDark else Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                // اللون المخصص: عجلة ألوان
                val wheel = Brush.sweepGradient(List(7) { Color.hsl(it * 60f, 0.85f, 0.55f) })
                Box(
                    Modifier.size(38.dp).then(if (customSel) Modifier.border(2.5.dp, C.accent, CircleShape) else Modifier)
                        .tap { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); AppSettings.accent = ACCENTS.size },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(if (customSel) 29.dp else 38.dp).clip(CircleShape).background(wheel), contentAlignment = Alignment.Center) {
                        if (customSel) Icon(Icons.Filled.Check, "مخصص", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), color = IOS.separator, thickness = 0.5.dp)
            if (customSel) HueRow("درجة اللون", AppSettings.customHue) { AppSettings.customHue = it }
            SSwitch("تلوين عناصر النظام بلون التمييز", AppSettings.iosUseAccent, last = true) { AppSettings.iosUseAccent = it }
        }
        SGroup(header = "خلفية الوضع الداكن") {
            SSegment(null, listOf("أسود نقي", "فحمي", "أزرق ليلي"), AppSettings.darkBg, last = true) { AppSettings.darkBg = it }
        }
        SGroup(header = "لون فقاعاتك", footer = "يغيّر لون الرسائل التي ترسلها فقط.") {
            SSegment(null, listOf("افتراضي", "لون التمييز", "مخصص"), AppSettings.bubbleColorMode, last = AppSettings.bubbleColorMode != 2) {
                AppSettings.bubbleColorMode = it
            }
            if (AppSettings.bubbleColorMode == 2) HueRow("درجة اللون", AppSettings.bubbleHue, last = true) { AppSettings.bubbleHue = it }
        }
        SGroup {
            SCenter("إعادة ضبط الألوان", IOS.red) { AppSettings.resetColors(); vm.toast = "تمت إعادة ضبط الألوان" }
        }
    }
}

// ───────────────────────── الأحجام ─────────────────────────

@Composable
internal fun CustomSizesPage(vm: MainVM) {
    SettingsScaffold("الأحجام", "التخصيص", { toHub(vm) }) {
        SGroup(
            header = "حجم كل التطبيق",
            footer = "«حجم الواجهة» يكبّر أو يصغّر كل شيء (الأيقونات والمسافات والنصوص). «حجم الخط» يغيّر النصوص فقط. يُطبَّق عند رفع إصبعك."
        ) {
            SSlider("حجم الواجهة", AppSettings.uiScale, 80, 130, "%", step = 5, deferred = true) { AppSettings.uiScale = it }
            SSlider("حجم الخط العام", AppSettings.textScale, 80, 160, "%", step = 5, last = true, deferred = true) { AppSettings.textScale = it }
        }
        SGroup(header = "العناوين والرسائل") {
            SSlider("العناوين الكبيرة", AppSettings.largeTitleSp, 24, 44, " نقطة") { AppSettings.largeTitleSp = it }
            SSlider("خط الرسائل", AppSettings.msgFontSp, 12, 28, " نقطة", last = true) { AppSettings.msgFontCustom = it }
        }
        SGroup(header = "قائمة المحادثات") { ListRowPreview() }
        SGroup {
            SSlider("حجم الصور الشخصية (كل التطبيق)", AppSettings.avatarScale, 60, 150, "%", step = 5) { AppSettings.avatarScale = it }
            SSegment("شكل الصور الشخصية", listOf("دائرة", "مربع ناعم"), AppSettings.avatarShape) { AppSettings.avatarShape = it }
            SSlider("حجم اسم المحادثة", AppSettings.listNameSp, 13, 24, " نقطة") { AppSettings.listNameSp = it }
            SSlider("حجم نص المعاينة", AppSettings.listPreviewSp, 11, 22, " نقطة") { AppSettings.listPreviewSp = it }
            SSlider("ارتفاع الصف (الهامش الرأسي)", AppSettings.listRowPad, 3, 22, " dp", last = true) { AppSettings.listRowPad = it }
        }
        SGroup(header = "صفوف الإعدادات") {
            SSlider("ارتفاع الصف", AppSettings.rowHeight, 34, 70, " dp", last = true) { AppSettings.rowHeight = it }
        }
        SGroup {
            SCenter("إعادة ضبط الأحجام", IOS.red) { AppSettings.resetSizes(); vm.toast = "تمت إعادة ضبط الأحجام" }
        }
    }
}

// ───────────────────────── الدردشات والفقاعات ─────────────────────────

@Composable
internal fun CustomBubblesPage(vm: MainVM) {
    SettingsScaffold("الدردشات والفقاعات", "التخصيص", { toHub(vm) }) {
        SGroup(header = "معاينة حيّة") { BubblePreview() }
        SGroup(header = "شكل الفقاعة") {
            SSlider("استدارة الزوايا", AppSettings.bubbleRadius, 4, 28, " dp") { AppSettings.bubbleRadius = it }
            SSlider("الحشوة الأفقية", AppSettings.bubblePadH, 6, 24, " dp") { AppSettings.bubblePadH = it }
            SSlider("الحشوة الرأسية", AppSettings.bubblePadV, 3, 16, " dp") { AppSettings.bubblePadV = it }
            SSlider("أقصى عرض للفقاعة", AppSettings.bubbleMaxPct, 55, 95, "%", step = 1) { AppSettings.bubbleMaxPct = it }
            SSlider("عرض الصور داخل الرسائل", AppSettings.chatImageW, 150, 320, " dp", step = 10, last = true) { AppSettings.chatImageW = it }
        }
        SGroup(header = "خيارات المحادثة") {
            SSlider("حجم خط الرسائل", AppSettings.msgFontSp, 12, 28, " نقطة") { AppSettings.msgFontCustom = it }
            SSwitch("ذيل الفقاعة", AppSettings.bubbleTail, last = false) { AppSettings.bubbleTail = it }
            SSwitch("نمط خلفية المحادثة", AppSettings.chatPattern, last = true) { AppSettings.chatPattern = it }
        }
        SGroup {
            SCenter("إعادة ضبط الفقاعات", IOS.red) { AppSettings.resetBubbles(); vm.toast = "تمت إعادة ضبط الفقاعات" }
        }
    }
}

// ───────────────────────── شريط التبويبات ─────────────────────────

@Composable
internal fun CustomTabBarPage(vm: MainVM) {
    val haptic = LocalHapticFeedback.current
    val ordered = orderedTabs()

    fun move(from: Int, to: Int) {
        val ids = ordered.map { it.ordinal }.toMutableList()
        val x = ids.removeAt(from); ids.add(to, x)
        AppSettings.tabOrder = ids.joinToString(",")
    }

    SettingsScaffold("شريط التبويبات", "التخصيص", { toHub(vm) }) {
        SGroup(
            header = "معاينة حيّة",
            footer = "المعاينة تعمل فعلاً: اضغط أو اسحب إصبعك بين التبويبات. التمويه الحقيقي يتطلب Android 12 فأعلى."
        ) { TabBarPreview() }

        SGroup(header = "التبويبات", footer = "«الدردشات» و«الإعدادات» ثابتان. يمكنك إخفاء الباقي وإعادة ترتيبها بالأسهم.") {
            ordered.forEachIndexed { i, tab ->
                val hidden = isTabHidden(tab)
                RowBox(last = i == ordered.lastIndex, inset = 16.dp, onClick = null) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = AppSettings.rowHeight.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tab.on, null, tint = if (hidden) IOS.tertiary else IOS.blue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(tab.label, color = if (hidden) IOS.tertiary else IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.KeyboardArrowUp, "أعلى",
                            tint = if (i > 0) IOS.blue else IOS.tertiary,
                            modifier = Modifier.size(30.dp).then(
                                if (i > 0) Modifier.tap { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); move(i, i - 1) } else Modifier
                            )
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown, "أسفل",
                            tint = if (i < ordered.lastIndex) IOS.blue else IOS.tertiary,
                            modifier = Modifier.size(30.dp).then(
                                if (i < ordered.lastIndex) Modifier.tap { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); move(i, i + 1) } else Modifier
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        if (tab.locked) {
                            Icon(Icons.Filled.Lock, "ثابت", tint = IOS.tertiary, modifier = Modifier.size(20.dp).padding(end = 0.dp))
                            Spacer(Modifier.width(31.dp))
                        } else Box(Modifier.tap {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            AppSettings.tabHidden = AppSettings.tabHidden xor (1 shl tab.ordinal)
                        }) { ISwitch(!hidden) }
                    }
                }
            }
        }

        SGroup(header = "الشكل والموضع") {
            SSlider("الارتفاع", AppSettings.tabHeight, 48, 80, " dp") { AppSettings.tabHeight = it }
            SSlider("استدارة الزوايا", AppSettings.tabCornerPct, 0, 100, "%", step = 5, display = {
                if (it >= 100) "كبسولة" else "$it%"
            }) { AppSettings.tabCornerPct = it }
            SSlider("الهامش الجانبي", AppSettings.tabSideMargin, 0, 48, " dp") { AppSettings.tabSideMargin = it }
            SSlider("الارتفاع عن الحافة السفلية", AppSettings.tabBottomOffset, -8, 30, " dp", last = true) { AppSettings.tabBottomOffset = it }
        }

        SGroup(header = "الأيقونات والنص") {
            SSlider("حجم الأيقونة", AppSettings.tabIconSize, 18, 34, " dp") { AppSettings.tabIconSize = it }
            SSlider("حجم النص", AppSettings.tabLabelSize, 8, 14, " نقطة") { AppSettings.tabLabelSize = it }
            SSegment("عرض النصوص", listOf("دائماً", "للمحدد فقط", "مخفية"), AppSettings.tabLabelMode, last = true) { AppSettings.tabLabelMode = it }
        }

        SGroup(header = "الزجاج", footer = "التمويه 0 = زجاج شفاف بلا تمويه. كثافة الصبغة تتحكم بمدى وضوح ما خلف الشريط.") {
            SSlider("قوة التمويه", AppSettings.tabBlur, 0, 60, " dp") { AppSettings.tabBlur = it }
            SSlider("كثافة الصبغة", AppSettings.tabTint, 0, 200, "%", step = 5) { AppSettings.tabTint = it }
            SSlider("تشبّع الألوان", AppSettings.tabSaturation, 100, 250, "%", step = 10, last = false) { AppSettings.tabSaturation = it }
            SSwitch("حافة لامعة", AppSettings.tabRim, last = false) { AppSettings.tabRim = it }
            SSwitch("ظل الشريط", AppSettings.tabShadow, last = true) { AppSettings.tabShadow = it }
        }

        SGroup(header = "التحديد والألوان") {
            SSegment("شكل التحديد", listOf("عدسة زجاجية", "ملوّن", "بدون"), AppSettings.tabLens) { AppSettings.tabLens = it }
            SSegment("لون التبويب المحدد", listOf("أزرق", "التمييز", "أحادي", "مخصص"), AppSettings.tabSelColor, last = AppSettings.tabSelColor != 3) {
                AppSettings.tabSelColor = it
            }
            if (AppSettings.tabSelColor == 3) HueRow("درجة اللون", AppSettings.tabCustomHue, last = true) { AppSettings.tabCustomHue = it }
        }

        SGroup(header = "الحركة والاهتزاز") {
            SSwitch("تمدّد العدسة مع السرعة", AppSettings.tabStretch, last = false) { AppSettings.tabStretch = it }
            SSwitch("ارتداد الأيقونة", AppSettings.tabBounce, last = false) { AppSettings.tabBounce = it }
            SSwitch("الاهتزاز عند التبديل", AppSettings.tabHaptics, last = true) { AppSettings.tabHaptics = it }
        }

        SGroup(header = "شارة الرسائل غير المقروءة") {
            SSwitch("إظهار الشارة على «الدردشات»", AppSettings.tabBadge, last = AppSettings.tabBadge.not()) { AppSettings.tabBadge = it }
            if (AppSettings.tabBadge) SSegment(null, listOf("رقم", "نقطة فقط"), AppSettings.tabBadgeStyle, last = true) { AppSettings.tabBadgeStyle = it }
        }

        SGroup {
            SCenter("إعادة ضبط شريط التبويبات", IOS.red) { AppSettings.resetTabBar(); vm.toast = "تمت إعادة ضبط الشريط" }
        }
    }
}
