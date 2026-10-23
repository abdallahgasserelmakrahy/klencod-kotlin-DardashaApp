package com.klencod.dardasha.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Notifier
import com.klencod.dardasha.Route
import com.klencod.dardasha.SettingsSection
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.data.Media
import com.klencod.dardasha.data.WaApps
import com.klencod.dardasha.data.WaInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// =====================================================================================
//  عناصر iOS المشتركة لصفحات الإعدادات
// =====================================================================================

private val IosTeal: Color get() = Color(0xFF32ADE6)

/**
 * هيكل صفحة إعدادات بأسلوب iOS: شريط تنقل + عنوان كبير يتقلّص عند التمرير،
 * خلفية Grouped، سحب من الحافة للرجوع، وطبقة [overlay] للـ Action Sheets.
 */
@Composable
internal fun SettingsScaffold(
    title: String, backLabel: String, onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val collapsed by remember(thresholdPx) { derivedStateOf { scroll.value > thresholdPx } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "setTitle")

    IosPage(onBack = onBack, overlay = overlay) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(title, onBack = onBack, separatorVisible = collapsed, backLabel = backLabel, titleAlpha = titleAlpha)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp)) {
                Text(
                    title, color = IOS.label, fontSize = AppSettings.largeTitleSp.sp, fontWeight = FontWeight.Bold, lineHeight = (AppSettings.largeTitleSp + 6).sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
                )
                content()
                Spacer(Modifier.height(40.dp + LocalTabInset.current))
            }
        }
    }
}

/** مجموعة (Inset Grouped) بعنوان وتذييل اختياريين. */
@Composable
internal fun SGroup(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        IGroup(header, content)
        if (footer != null) IFooter(footer)
    }
}

/** خلية عامة: أيقونة ملوّنة اختيارية + عنوان + قيمة + عنصر نهاية + سهم. */
@Composable
internal fun SCell(
    title: String, last: Boolean,
    icon: ImageVector? = null, tint: Color = IOS.blue,
    value: String? = null, chevron: Boolean = false,
    titleColor: Color = IOS.label,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    RowBox(last, inset = if (icon != null) 58.dp else 16.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AppSettings.rowHeight.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(Modifier.size(30.dp).background(tint, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(title, color = titleColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    value, color = IOS.secondary, fontSize = 17.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp)
                )
            }
            if (trailing != null) trailing()
            if (chevron) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** صف بمفتاح iOS الأخضر. */
@Composable
internal fun SSwitch(
    title: String, checked: Boolean, last: Boolean, icon: ImageVector? = null, tint: Color = IOS.blue,
    enabled: Boolean = true, onChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SCell(
        title, last, icon, tint,
        titleColor = if (enabled) IOS.label else IOS.tertiary,
        trailing = { Box(Modifier.alpha(if (enabled) 1f else 0.4f)) { ISwitch(checked) } },
        onClick = if (enabled) ({
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(!checked)
        }) else null
    )
}

/** صف اختيار بعلامة ✓ زرقاء. */
@Composable
internal fun SCheck(
    title: String, selected: Boolean, last: Boolean, icon: ImageVector? = null, tint: Color = IOS.blue,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SCell(
        title, last, icon, tint,
        trailing = { if (selected) Icon(Icons.Filled.Check, null, tint = IOS.blue, modifier = Modifier.size(22.dp)) },
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
    )
}

/** صف نصّي في المنتصف (للإجراءات المدمّرة مثل تسجيل الخروج). */
@Composable
internal fun SCenter(title: String, color: Color, last: Boolean = true, onClick: () -> Unit) {
    RowBox(last, inset = 16.dp, onClick = onClick) {
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(title, color = color, fontSize = 17.sp)
        }
    }
}

/** Action Sheet لاختيار قيمة (خيارات + إلغاء) بنفس أسلوب iOS. */
@Composable
private fun BoxScope.ChoiceSheet(
    visible: Boolean, title: String, options: List<String>, selected: Int,
    onPick: (Int) -> Unit, onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    IosSheetFrame(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IOS.alertBg)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text(title, color = IOS.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            options.forEachIndexed { i, label ->
                HorizontalDivider(color = IOS.alertSep, thickness = 0.5.dp)
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(if (pressed) IOS.pressed else Color.Transparent)
                        .clickable(src, null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(i); onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = IOS.blue, fontSize = 20.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (i == selected) Icon(
                        Icons.Filled.Check, null, tint = IOS.blue,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(22.dp)
                    )
                }
            }
        }
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                .background(if (pressed) IOS.pressed else IOS.alertBg).clickable(src, null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) { Text("إلغاء", color = IOS.blue, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
    }
}

private fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b بايت"
    b < 1024 * 1024 -> String.format(Locale.US, "%.0f ك.ب", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f م.ب", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f ج.ب", b / 1073741824.0)
}

private val FONT_LABELS = listOf("صغير", "متوسط", "كبير", "كبير جداً")
private val DOWNLOAD_LABELS = listOf("دائماً", "عبر Wi-Fi فقط", "أبداً (بالضغط على الصورة)")
private val QUALITY_LABELS = listOf("توفير البيانات", "عادية", "عالية")
private val THEME_LABELS = listOf("حسب النظام", "فاتح", "داكن")

// =====================================================================================
//  الشاشة الرئيسية للإعدادات
// =====================================================================================

@Composable
fun SettingsScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val me = vm.meId
    val prof = me?.let { vm.peers[it] }
    val name = prof?.displayName?.takeIf { it.isNotBlank() } ?: prof?.username ?: ""
    val version = remember { appVersion(ctx) }
    val goBack = { vm.route = Route.Home }
    fun open(sec: SettingsSection) { vm.route = Route.SettingsPage(sec) }

    val scroll = rememberScrollState()
    val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val collapsed by remember(thresholdPx) { derivedStateOf { scroll.value > thresholdPx } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "setTitle")

    IosPage(onBack = goBack) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar("الإعدادات", onBack = goBack, separatorVisible = collapsed, backLabel = "دردشة", titleAlpha = titleAlpha)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp)) {
                Text(
                    "الإعدادات", color = IOS.label, fontSize = AppSettings.largeTitleSp.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)
                )

                // بطاقة الملف الشخصي
                Appear(0) {
                    IGroup {
                        RowBox(last = true, inset = 0.dp, onClick = { vm.route = Route.Me }) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(prof?.username ?: "?", prof?.avatarPath, 60.dp, ring = CellBg, plain = true)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name, color = IOS.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    val about = prof?.about?.takeIf { it.isNotBlank() }
                                    Text(
                                        about ?: ("@" + (prof?.username ?: "")), color = IOS.secondary, fontSize = 15.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                    tint = IOS.tertiary, modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Appear(1) {
                    IGroup {
                        IRow(Icons.Filled.Person, IOS.blue, "الحساب", last = false, chevron = true) { open(SettingsSection.Account) }
                        IRow(Icons.Filled.Lock, IOS.gray, "الخصوصية", last = false, chevron = true) { open(SettingsSection.Privacy) }
                        IRow(Icons.Filled.Chat, IosGreen, "الدردشات", last = false, chevron = true) { open(SettingsSection.Chats) }
                        IRow(Icons.Filled.Notifications, IOS.red, "الإشعارات", last = false, chevron = true) { open(SettingsSection.Notifications) }
                        IRow(Icons.Filled.QuestionAnswer, IosGreen, "واتساب", last = true, chevron = true) { open(SettingsSection.WhatsApp) }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Appear(2) {
                    IGroup {
                        IRow(Icons.Filled.Star, IOS.orange, "Plus", last = true, chevron = true) { open(SettingsSection.Plus) }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Appear(3) {
                    IGroup {
                        IRow(Icons.Filled.Tune, IOS.blue, "التخصيص والأحجام", last = false, chevron = true) { open(SettingsSection.Customize) }
                        IRow(Icons.Filled.Palette, IOS.indigo, "المظهر", last = false, chevron = true) { open(SettingsSection.Appearance) }
                        IRow(Icons.Filled.Storage, IOS.orange, "التخزين والبيانات", last = false, chevron = true) { open(SettingsSection.Storage) }
                        IRow(Icons.Filled.Info, IOS.blue, "المساعدة وحول التطبيق", last = true, chevron = true) { open(SettingsSection.Help) }
                    }
                }

                Spacer(Modifier.height(30.dp))
                Appear(3) {
                    IGroup {
                        IRow(Icons.Filled.Share, IosTeal, "ادعُ صديقاً", last = true) { shareApp(ctx) }
                    }
                }

                Text(
                    "دردشة  •  الإصدار $version", color = IOS.secondary, fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp + LocalTabInset.current)
                )
            }
        }
    }
}

@Composable
fun SettingsPageScreen(vm: MainVM, section: SettingsSection) {
    when (section) {
        SettingsSection.Account -> AccountPage(vm)
        SettingsSection.Privacy -> PrivacyPage(vm)
        SettingsSection.Chats -> ChatsPage(vm)
        SettingsSection.Notifications -> NotificationsPage(vm)
        SettingsSection.WhatsApp -> WhatsAppPage(vm)
        SettingsSection.Plus -> PlusPage(vm)
        SettingsSection.Appearance -> AppearancePage(vm)
        SettingsSection.Storage -> StoragePage(vm)
        SettingsSection.Blocked -> BlockedPage(vm)
        SettingsSection.Help -> HelpPage(vm)
        SettingsSection.Customize -> CustomizeHubPage(vm)
        SettingsSection.CustomColors -> CustomColorsPage(vm)
        SettingsSection.CustomSizes -> CustomSizesPage(vm)
        SettingsSection.CustomBubbles -> CustomBubblesPage(vm)
        SettingsSection.CustomTabBar -> CustomTabBarPage(vm)
    }
}

private fun toSettings(vm: MainVM) { vm.route = Route.Settings }

// =====================================================================================
//  الحساب
// =====================================================================================

@Composable
private fun AccountPage(vm: MainVM) {
    var confirmLogout by remember { mutableStateOf(false) }
    SettingsScaffold("الحساب", "الإعدادات", { toSettings(vm) }) {
        SGroup {
            SCell("تعديل الملف الشخصي", last = false, icon = Icons.Filled.Edit, tint = IOS.blue, chevron = true) { vm.route = Route.Me }
            SCell("الرسائل المميّزة بنجمة", last = true, icon = Icons.Filled.Star, tint = IOS.orange, chevron = true) { vm.route = Route.Starred }
        }
        SGroup(header = "الحسابات على هذا الجهاز") { AccountsPanel(vm) }
        SGroup { SCenter("تسجيل الخروج", IOS.red) { confirmLogout = true } }

        if (confirmLogout) IosAlert(
            title = "تسجيل الخروج؟",
            message = "ستخرج من الحساب الحالي على هذا الجهاز. تستطيع الدخول مجدداً بكلمة المرور.",
            confirmText = "تسجيل الخروج",
            onConfirm = { confirmLogout = false; vm.signOut() },
            onDismiss = { confirmLogout = false }
        )
    }
}

// =====================================================================================
//  الخصوصية
// =====================================================================================

@Composable
private fun PrivacyPage(vm: MainVM) {
    SettingsScaffold("الخصوصية", "الإعدادات", { toSettings(vm) }) {
        SGroup(
            header = "من يرى معلوماتي",
            footer = if (AppSettings.showLastSeen) "الآخرون يرون متى كنت متصلاً."
            else "مخفي: لن يرى أحد أنك متصل ولا وقت آخر ظهورك."
        ) {
            SSwitch("آخر ظهور ومتصل الآن", AppSettings.showLastSeen, last = true, icon = Icons.Filled.Visibility, tint = IOS.blue) {
                AppSettings.showLastSeen = it; vm.applyPresencePrivacy()
            }
        }
        SGroup(
            footer = if (AppSettings.readReceipts) "يرى المرسِل علامتَي ✓✓ الزرقاوين عند قراءتك رسالته."
            else "لن يعرف المرسِل أنك قرأت رسالته (والعكس لا ينطبق على رسائلك)."
        ) {
            SSwitch("إشعارات القراءة", AppSettings.readReceipts, last = true, icon = Icons.Filled.DoneAll, tint = IosGreen) {
                AppSettings.readReceipts = it
            }
        }
        SGroup(
            footer = if (AppSettings.typingIndicator) "يرى الطرف الآخر أنك تكتب له." else "لن يظهر للطرف الآخر أنك تكتب."
        ) {
            SSwitch("مؤشّر «يكتب...»", AppSettings.typingIndicator, last = true, icon = Icons.Filled.Edit, tint = IOS.orange) {
                AppSettings.typingIndicator = it
            }
        }
        SGroup(header = "الأمان", footer = "يمنع تصوير التطبيق ويُخفي محتواه في قائمة التطبيقات الأخيرة.") {
            SSwitch("منع لقطات الشاشة", AppSettings.screenSecurity, last = true, icon = Icons.Filled.Security, tint = IOS.red) {
                AppSettings.screenSecurity = it
            }
        }
        SGroup(
            header = "جهات الاتصال",
            footer = "تنطبق هذه الإعدادات على حسابك الحالي من هذا الجهاز. إخفاء الحضور قد يُبقي علامة التسليم على ✓ واحدة عند من يراسلك إلى أن تقرأ الرسالة."
        ) {
            SCell(
                "المحظورون", last = true, icon = Icons.Filled.Block, tint = IOS.gray, chevron = true,
                value = if (vm.blocked.isEmpty()) "لا أحد" else "${vm.blocked.size}"
            ) { vm.route = Route.SettingsPage(SettingsSection.Blocked) }
        }
    }
}

@Composable
private fun BlockedPage(vm: MainVM) {
    val ids = vm.blocked.toList()
    LaunchedEffect(ids) { ids.filter { it !in vm.peers }.forEach { vm.fetchSeen(it) } }
    val back = { vm.route = Route.SettingsPage(SettingsSection.Privacy) }
    SettingsScaffold("المحظورون", "الخصوصية", back) {
        if (ids.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 120.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Block, null, tint = IOS.tertiary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("لا يوجد مستخدمون محظورون", color = IOS.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "المستخدمون الذين تحظرهم يظهرون هنا.",
                    color = IOS.secondary, fontSize = 15.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            SGroup(footer = "المحظور لا تصلك إشعاراته ولا تظهر رسائله. اضغط «إلغاء الحظر» لاستعادة التواصل.") {
                ids.forEachIndexed { i, id ->
                    val p = vm.peers[id]
                    RowBox(last = i == ids.lastIndex, inset = 72.dp, onClick = null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(vm.name(id), p?.avatarPath, 44.dp, ring = CellBg, plain = true)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(vm.name(id), color = IOS.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p != null) Text("@" + p.username, color = IOS.secondary, fontSize = 13.sp)
                            }
                            IosNavText("إلغاء الحظر") { vm.unblock(id) }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  الدردشات
// =====================================================================================

@Composable
private fun ChatsPage(vm: MainVM) {
    var confirmClear by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    SettingsScaffold("الدردشات", "الإعدادات", { toSettings(vm) }) {
        SGroup(header = "حجم الخط") {
            // معاينة حيّة لحجم الخط
            Box(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .clip(RoundedCornerShape(10.dp)).background(IOS.bg).padding(14.dp)
            ) {
                Column {
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(IOS.bubbleIn).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("مرحباً! كيف حالك؟", color = IOS.label, fontSize = AppSettings.msgFontSp.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(IOS.blue).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("بخير، شكراً لك 🌿", color = Color.White, fontSize = AppSettings.msgFontSp.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                IosSegmented(FONT_LABELS, AppSettings.fontSize.coerceIn(0, 3)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    AppSettings.msgFontCustom = 0
                    AppSettings.fontSize = it
                }
            }
        }
        SGroup(footer = "رسومات خفيفة خلف الرسائل.") {
            SSwitch("نمط خلفية المحادثة", AppSettings.chatPattern, last = true, icon = Icons.Filled.Wallpaper, tint = IosTeal) {
                AppSettings.chatPattern = it
            }
        }
        SGroup(
            header = "الكتابة",
            footer = if (AppSettings.enterSends) "زر الإدخال في لوحة المفاتيح يُرسل الرسالة."
            else "زر الإدخال يبدأ سطراً جديداً، والإرسال بزر السهم."
        ) {
            SSwitch("مفتاح Enter يُرسل", AppSettings.enterSends, last = true, icon = Icons.AutoMirrored.Filled.Send, tint = IOS.blue) {
                AppSettings.enterSends = it
            }
        }
        SGroup(header = "سجل الدردشات") {
            SCell("أرشفة كل المحادثات", last = false, icon = Icons.Filled.Archive, tint = IOS.blue) { vm.archiveAllChats() }
            SCell("إخراج الكل من الأرشيف", last = true, icon = Icons.Filled.Unarchive, tint = IosGreen) { vm.unarchiveAllChats() }
        }
        SGroup(footer = "من هذا الجهاز فقط.") { SCenter("حذف كل المحادثات", IOS.red) { confirmClear = true } }

        if (confirmClear) IosAlert(
            title = "حذف كل المحادثات؟",
            message = "ستُحذف كل الرسائل من جهازك فقط، ولن تتأثر عند الطرف الآخر.",
            confirmText = "حذف الكل",
            onConfirm = { confirmClear = false; vm.clearAllChats() },
            onDismiss = { confirmClear = false }
        )
    }
}

// =====================================================================================
//  الإشعارات
// =====================================================================================

@Composable
private fun NotificationsPage(vm: MainVM) {
    val ctx = LocalContext.current
    // نعيد الفحص عند كل إعادة تركيب (المستخدم قد يعود من إعدادات النظام)
    val systemOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val on = AppSettings.notifications

    SettingsScaffold("الإشعارات", "الإعدادات", { toSettings(vm) }) {
        if (!systemOn) SGroup(footer = "اضغط لفتح إعدادات النظام وتفعيلها.") {
            SCell(
                "الإشعارات مغلقة من إعدادات النظام", last = true, icon = Icons.Filled.Notifications, tint = IOS.red,
                chevron = true
            ) { openAppNotificationSettings(ctx) }
        }

        SGroup(
            header = "الرسائل",
            footer = if (on) "تصلك إشعارات الرسائل الجديدة. تصل الإشعارات أيضاً والتطبيق مغلق."
            else "لن تصلك أي إشعارات للرسائل."
        ) {
            SSwitch("إشعارات الرسائل", on, last = false, icon = Icons.Filled.Notifications, tint = IOS.red) {
                AppSettings.notifications = it
            }
            SSwitch("معاينة الرسالة", AppSettings.notifPreview, last = false, icon = Icons.Filled.Visibility, tint = IOS.blue, enabled = on) {
                AppSettings.notifPreview = it
            }
            SSwitch("الصوت", AppSettings.notifSound, last = false, icon = Icons.Filled.VolumeUp, tint = IOS.orange, enabled = on) {
                AppSettings.notifSound = it
            }
            SSwitch("الاهتزاز", AppSettings.notifVibrate, last = true, icon = Icons.Filled.Vibration, tint = IOS.indigo, enabled = on) {
                AppSettings.notifVibrate = it
            }
        }

        SGroup(
            header = "متقدّم",
            footer = "لكتم محادثة معيّنة: مرّر الصف في قائمة المحادثات واضغط «كتم»."
        ) {
            SCell("إعدادات إشعارات النظام", last = false, icon = Icons.Filled.Settings, tint = IOS.gray, chevron = true) {
                openAppNotificationSettings(ctx)
            }
            SCell("إرسال إشعار تجريبي", last = true) { Notifier.test(ctx) }
        }
    }
}

// =====================================================================================
//  واتساب (العادي وواتساب للأعمال) — خيارات آمنة بدون مكتبات غير رسمية
// =====================================================================================

@Composable
private fun WhatsAppPage(vm: MainVM) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { WaInbox.load(ctx) }
    // نعيد فحص إذن الوصول للإشعارات عند العودة من إعدادات النظام
    var tick by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val access = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val installed = remember(tick) { WaApps.installed(ctx) }
    val on = AppSettings.waInbox
    var confirmClear by remember { mutableStateOf(false) }
    val waAlias = remember { android.content.ComponentName(ctx.packageName, "com.klencod.dardasha.WaAlias") }
    var aliasOn by remember {
        mutableStateOf(
            runCatching { ctx.packageManager.getComponentEnabledSetting(waAlias) }
                .getOrNull() == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
    }

    SettingsScaffold("واتساب", "الإعدادات", { toSettings(vm) }) {
        SGroup(
            header = "التطبيقات المثبّتة",
            footer = "المشاركة: اضغط مطولاً على أي رسالة نصية في دردشة واختر «مشاركة عبر واتساب». يعمل مع الحساب العادي وحساب الأعمال، والإرسال يتم بضغطتك أنت داخل واتساب."
        ) {
            SCell("واتساب", last = false, icon = Icons.Filled.Chat, tint = IosGreen,
                value = if (WaApps.WA in installed) "مثبّت" else "غير مثبّت")
            SCell("واتساب للأعمال", last = true, icon = Icons.Filled.Chat, tint = IosTeal,
                value = if (WaApps.BIZ in installed) "مثبّت" else "غير مثبّت")
        }

        SGroup(
            header = "صندوق واتساب على هذا الجهاز",
            footer = "يقرأ التطبيق إشعارات واتساب الواردة على هذا الجهاز فقط ويحفظها محلياً (الرسائل، ومعها الصور والرسائل الصوتية متى أتاح واتساب مرفقاتها في الإشعار) لتتمكن من الرد السريع. لا يُرسَل أي شيء لأي سيرفر، ولا يرى المحادثات القديمة، ولا يعمل مع المحادثات المكتومة أو الإشعارات المغلقة."
        ) {
            SSwitch("حفظ رسائل واتساب الواردة", on, last = false, icon = Icons.Filled.Chat, tint = IosGreen) {
                AppSettings.waInbox = it
            }
            SCell(
                "الوصول إلى الإشعارات", last = true, icon = Icons.Filled.Notifications, tint = IOS.red,
                value = if (access) "مفعّل" else "غير مفعّل", chevron = true
            ) {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }

        SGroup(
            header = "شاشة واتساب",
            footer = "شاشة واتساب تعرض كل ما يصل من واتساب: الرسائل والصور والرسائل الصوتية. لا تظهر أيقونة واتساب في الشاشة الرئيسية إلا إذا فعّلتها هنا؛ وعند الضغط عليها تفتح هذه الشاشة مباشرة."
        ) {
            SCell("فتح شاشة واتساب", last = false, icon = Icons.Filled.QuestionAnswer, tint = IosGreen, chevron = true) {
                vm.route = Route.WhatsApp
            }
            SCell("ربط أرقام واتساب (جهاز مصاحب)", last = false, icon = Icons.Filled.Share, tint = IosGreen, chevron = true) {
                vm.route = Route.WaLink
            }
            SSwitch("أيقونة «واتساب» في الشاشة الرئيسية", aliasOn, last = false, icon = Icons.Filled.Chat, tint = IosTeal) {
                val ok = runCatching {
                    ctx.packageManager.setComponentEnabledSetting(
                        waAlias,
                        if (it) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                }.isSuccess
                if (ok) aliasOn = it else vm.toast = "تعذّر تغيير الأيقونة"
            }
            SCell("إضافة اختصار «واتساب» للشاشة الرئيسية", last = true, icon = Icons.Filled.Share, tint = IosGreen) {
                val ok = runCatching {
                    if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) false
                    else {
                        val i = Intent(ctx, com.klencod.dardasha.MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(com.klencod.dardasha.MainActivity.EXTRA_OPEN_WA, true)
                        val sc = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "wa_inbox")
                            .setShortLabel("واتساب").setLongLabel("واتساب")
                            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, com.klencod.dardasha.R.mipmap.ic_launcher_wa))
                            .setIntent(i).build()
                        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, sc, null)
                    }
                }.getOrDefault(false)
                if (!ok) vm.toast = "لوحة التشغيل لا تدعم تثبيت الاختصارات. فعّل «أيقونة واتساب» بدلاً منه."
            }
        }

        if (on && !access) IFooter("الميزة مفعّلة لكن إذن الوصول للإشعارات غير ممنوح. اضغط «الوصول إلى الإشعارات» ثم فعّل التطبيق.", color = IOS.red)

        if (WaInbox.convs.isNotEmpty()) {
            SGroup {
                SCell("مسح كل رسائل واتساب المحفوظة", last = true, icon = Icons.Filled.Delete, tint = IOS.red, titleColor = IOS.red) {
                    confirmClear = true
                }
            }
        }
    }

    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("مسح رسائل واتساب؟") },
        text = { Text("ستُحذف الرسائل المحفوظة على هذا الجهاز فقط. لن يتأثر واتساب نفسه.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { WaInbox.clearAll(ctx); confirmClear = false }) { Text("مسح", color = IOS.red) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
    )
}

// =====================================================================================
//  Plus — خيارات إضافية للدردشة
// =====================================================================================

@Composable
private fun PlusPage(vm: MainVM) {
    val gapLabels = listOf("45 دقيقة", "15 دقيقة", "5 دقائق")
    SettingsScaffold("Plus", "الإعدادات", { toSettings(vm) }) {
        SGroup(
            header = "الخصوصية",
            footer = "عند تفعيله: إن حذف الطرف الآخر رسالة «للجميع» تبقى ظاهرة عندك مع علامة «حذفها المرسِل». تُحفظ الرسائل والصور والصوت والملفات الواردة على جهازك فور وصولها (الملفات حتى 25 م.ب)، ولا تشمل الصور «لمرة واحدة». لا يمكن استرجاع ما حُذف قبل التفعيل أو قبل أن يصلك. لا يؤثر هذا على رسائلك أنت."
        ) {
            SSwitch("منع حذف الرسائل", AppSettings.antiDelete, last = true, icon = Icons.Filled.Lock, tint = IOS.red) {
                AppSettings.antiDelete = it
                if (it) vm.refreshKeep()
            }
        }

        SGroup(
            header = "الدردشة",
            footer = "«إبقاء الشاشة مضيئة» يمنع انطفاء الشاشة أثناء فتح أي دردشة فقط. «ذيل الفقاعة» هو الزاوية المدبّبة في آخر رسالة من كل مجموعة."
        ) {
            SSwitch("إبقاء الشاشة مضيئة في الدردشة", AppSettings.keepAwake, last = false, icon = Icons.Filled.Visibility, tint = IOS.orange) {
                AppSettings.keepAwake = it
            }
            SSwitch("ذيل الفقاعة", AppSettings.bubbleTail, last = true, icon = Icons.Filled.Chat, tint = IosGreen) {
                AppSettings.bubbleTail = it
            }
        }

        SGroup(
            header = "طابع الوقت بين الرسائل",
            footer = "يظهر الوقت فوق الرسالة إذا مرّت هذه المدة منذ الرسالة السابقة (ودائماً عند بداية يوم جديد)."
        ) {
            gapLabels.forEachIndexed { i, label ->
                SCheck(label, AppSettings.stampGap == i, last = i == gapLabels.lastIndex, icon = Icons.Filled.Info, tint = IOS.indigo) {
                    AppSettings.stampGap = i
                }
            }
        }

        SGroup(header = "واتساب", footer = "يتحكم في ظهور «مشاركة عبر واتساب» عند الضغط مطولاً على رسالة.") {
            SSwitch("«مشاركة عبر واتساب» في قائمة الرسالة", AppSettings.waShareMenu, last = true, icon = Icons.Filled.Share, tint = IosTeal) {
                AppSettings.waShareMenu = it
            }
        }
    }
}

// =====================================================================================
//  المظهر
// =====================================================================================

@Composable
private fun AppearancePage(vm: MainVM) {
    val haptic = LocalHapticFeedback.current
    val themeIcons = listOf(Icons.Filled.BrightnessAuto, Icons.Filled.LightMode, Icons.Filled.DarkMode)
    val themeTints = listOf(IOS.gray, IOS.orange, IOS.indigo)
    val accentIdx = AppSettings.accent.coerceIn(0, ACCENTS.lastIndex)

    SettingsScaffold("المظهر", "الإعدادات", { toSettings(vm) }) {
        SGroup(header = "الوضع") {
            THEME_LABELS.forEachIndexed { i, label ->
                SCheck(label, AppSettings.theme == i, last = i == THEME_LABELS.lastIndex, icon = themeIcons[i], tint = themeTints[i]) {
                    AppSettings.theme = i
                }
            }
        }
        SGroup(header = "لون التمييز", footer = "يُطبَّق المظهر فوراً على كل التطبيق ويُحفظ على هذا الجهاز.") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                ACCENTS.forEachIndexed { i, a ->
                    val sel = accentIdx == i
                    val scale by animateFloatAsState(if (sel) 1f else 0.86f, spring(0.6f, 500f), label = "acc$i")
                    val col = if (C.dark) a.dark else a.light
                    Box(
                        Modifier.size(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .then(if (sel) Modifier.border(2.5.dp, col, CircleShape) else Modifier)
                            .tap { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); AppSettings.accent = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(if (sel) 36.dp else 46.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                            if (sel) Icon(Icons.Filled.Check, a.name, tint = if (C.dark) a.onDark else Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), color = IOS.separator, thickness = 0.5.dp)
            SCell("اللون", last = true, value = ACCENTS[accentIdx].name)
        }
    }
}

// =====================================================================================
//  التخزين والبيانات
// =====================================================================================

@Composable
private fun StoragePage(vm: MainVM) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val cache by produceState(0L, refresh) { value = withContext(Dispatchers.IO) { Media.cacheSize(ctx) } }
    val local by produceState(0L, refresh) { value = withContext(Dispatchers.IO) { Media.localDataSize(ctx) } }
    var sheet by remember { mutableIntStateOf(0) } // 0 لا شيء، 1 التنزيل التلقائي، 2 جودة الصور
    var confirmClear by remember { mutableStateOf(false) }
    val msgs = vm.msgs

    SettingsScaffold(
        "التخزين والبيانات", "الإعدادات", { toSettings(vm) },
        overlay = {
            ChoiceSheet(sheet == 1, "تنزيل الصور تلقائياً", DOWNLOAD_LABELS, AppSettings.autoDownload, { AppSettings.autoDownload = it }) { sheet = 0 }
            ChoiceSheet(sheet == 2, "جودة الصور المرسلة", QUALITY_LABELS, AppSettings.imageQuality, { AppSettings.imageQuality = it }) { sheet = 0 }
        }
    ) {
        SGroup(header = "استخدام التخزين على الجهاز", footer = "الملفات المؤقتة: صور وصوتيات وملفات حُمّلت من المحادثات. نسخة الرسائل المحلية تُعرض بها المحادثات فوراً عند الفتح.") {
            SCell("الملفات المؤقتة", last = false, icon = Icons.Filled.Storage, tint = IOS.gray, value = fmtBytes(cache))
            SCell("نسخة الرسائل المحلية", last = true, icon = Icons.Filled.Chat, tint = IOS.blue, value = fmtBytes(local))
        }
        SGroup(footer = "الرسائل تبقى محفوظة ويُعاد تنزيل الصور عند الحاجة.") {
            SCenter("مسح الملفات المؤقتة", IOS.red) { confirmClear = true }
        }
        SGroup(
            header = "الوسائط",
            footer = "«عبر Wi-Fi فقط» يمنع تنزيل الصور على بيانات الجوال. الصور التي حُمّلت سابقاً تظهر دائماً."
        ) {
            SCell(
                "تنزيل الصور تلقائياً", last = false, icon = Icons.Filled.CloudDownload, tint = IosGreen, chevron = true,
                value = DOWNLOAD_LABELS[AppSettings.autoDownload.coerceIn(0, 2)]
            ) { sheet = 1 }
            SCell(
                "جودة الصور المرسلة", last = true, icon = Icons.Filled.HighQuality, tint = IOS.blue, chevron = true,
                value = QUALITY_LABELS[AppSettings.imageQuality.coerceIn(0, 2)]
            ) { sheet = 2 }
        }
        SGroup(header = "إحصاءات الحساب") {
            SCell("المحادثات", last = false, value = "${vm.conversations().size}")
            SCell("الرسائل", last = false, value = "${msgs.size}")
            SCell("الصور", last = false, value = "${msgs.count { it.imagePath != null }}")
            SCell("الرسائل الصوتية", last = false, value = "${msgs.count { it.audioPath != null }}")
            SCell("الملفات", last = true, value = "${msgs.count { it.filePath != null }}")
        }

        if (confirmClear) IosAlert(
            title = "مسح الملفات المؤقتة؟",
            message = "ستُحذف الصور والملفات المخزّنة مؤقتاً (${fmtBytes(cache)}). لن تُحذف أي رسالة.",
            confirmText = "مسح", danger = false,
            onConfirm = { confirmClear = false; Media.clearCache(ctx); refresh++; vm.toast = "تم مسح الملفات المؤقتة" },
            onDismiss = { confirmClear = false }
        )
    }
}

// =====================================================================================
//  المساعدة وحول التطبيق
// =====================================================================================

@Composable
private fun HelpPage(vm: MainVM) {
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val version = remember { appVersion(ctx) }

    SettingsScaffold("المساعدة وحول التطبيق", "الإعدادات", { toSettings(vm) }) {
        Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // أيقونة التطبيق بشكل iOS (مربّع بزوايا مدوّرة)
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(19.dp)).background(C.accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Chat, null, tint = C.onAccent, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("دردشة", color = IOS.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("الإصدار $version", color = IOS.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SGroup {
            SCell("مشاركة التطبيق", last = false, icon = Icons.Filled.Share, tint = IosTeal) { shareApp(ctx) }
            SCell(
                "معلومات التطبيق في النظام", last = false, icon = Icons.Filled.Settings, tint = IOS.gray, chevron = true
            ) {
                runCatching {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            SCell("الحسابات", last = true, icon = Icons.Filled.SwitchAccount, tint = IOS.indigo, chevron = true) {
                vm.route = Route.SettingsPage(SettingsSection.Account)
            }
        }
        SGroup(footer = "إعادة الضبط لا تحذف رسائلك ولا حسابك؛ فقط خيارات المظهر والإشعارات والخصوصية والدردشات.") {
            SCenter("إعادة ضبط الإعدادات", IOS.red) { confirmReset = true }
        }

        if (confirmReset) IosAlert(
            title = "إعادة ضبط الإعدادات؟",
            message = "ستعود كل الخيارات إلى قيمها الافتراضية.",
            confirmText = "إعادة ضبط",
            onConfirm = {
                confirmReset = false
                AppSettings.resetAll(); vm.applyPresencePrivacy(); vm.toast = "تمت إعادة الضبط"
            },
            onDismiss = { confirmReset = false }
        )
    }
}

// =====================================================================================
//  أدوات
// =====================================================================================

private fun appVersion(ctx: android.content.Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
}.getOrDefault("")

private fun shareApp(ctx: android.content.Context) {
    runCatching {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "جرّب تطبيق «دردشة» للمحادثات الخاصة بيني وبينك 💬")
        ctx.startActivity(Intent.createChooser(i, "مشاركة التطبيق").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openAppNotificationSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
