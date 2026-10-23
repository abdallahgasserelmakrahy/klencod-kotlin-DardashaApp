package com.klencod.dardasha.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.data.WaInbox
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Message
import com.klencod.dardasha.data.fmtList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** ألوان iOS النظامية (داكن/فاتح) تتبع وضع المظهر في التطبيق. */
internal object IOS {
    private val d: Boolean get() = C.dark
    val bg: Color get() = if (d) when (AppSettings.darkBg) { 1 -> Color(0xFF121214); 2 -> Color(0xFF0A0E1A); else -> Color(0xFF000000) } else Color(0xFFFFFFFF)
    val pressed: Color get() = if (d) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
    val label: Color get() = if (d) Color(0xFFFFFFFF) else Color(0xFF000000)
    val secondary: Color get() = if (d) Color(0x99EBEBF5) else Color(0x993C3C43)
    val tertiary: Color get() = if (d) Color(0x4DEBEBF5) else Color(0x4D3C3C43)
    val separator: Color get() = if (d) Color(0xFF38383A) else Color(0xFFC6C6C8)
    val fill: Color get() = if (d) Color(0x3D767680) else Color(0x1F767680)
    val blue: Color get() = if (AppSettings.iosUseAccent) C.accent else if (d) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val red: Color get() = if (d) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val orange: Color get() = if (d) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    val indigo: Color get() = if (d) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    val gray: Color get() = Color(0xFF8E8E93)
    val bubbleIn: Color get() = if (d) Color(0xFF262629) else Color(0xFFE9E9EB)
    val bar: Color get() = if (d) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val menuBg: Color get() = if (d) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)
    val alertBg: Color get() = if (d) Color(0xFF2A2A2C) else Color(0xFFF2F2F7)
    val alertSep: Color get() = if (d) Color(0xFF48484A) else Color(0xFFC6C6C8)
}

internal class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

internal fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MainVM, archivedView: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    var q by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val selected = remember(archivedView) { mutableStateListOf<String>() }
    var editing by remember(archivedView) { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<String>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var accountsSheet by remember { mutableStateOf(false) }
    var openPeer by remember { mutableStateOf<String?>(null) }
    val searching = q.isNotBlank()
    val homeCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { WaInbox.load(homeCtx) }
    BackHandler(enabled = editing) { editing = false; selected.clear() }

    val all by remember { derivedStateOf { vm.conversations() } }
    val archivedCount = all.count { vm.isArchived(it.first) }
    val convs = all.filter {
        // أثناء البحث نبحث في الكل؛ وإلا نعرض المؤرشفة أو العادية حسب الشاشة
        (searching || vm.isArchived(it.first) == archivedView) &&
            (!searching || vm.name(it.first).contains(q.trim(), ignoreCase = true) ||
                vm.preview(it.second).contains(q.trim(), ignoreCase = true) ||
                // البحث في كل رسائل المحادثة وليس آخر رسالة فقط
                vm.thread(it.first).any { m -> !m.isDeleted && m.text.contains(q.trim(), ignoreCase = true) })
    }

    val listState = rememberLazyListState()
    val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val collapsed by remember(thresholdPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx }
    }
    val connText = if (!archivedView && vm.conn.startsWith("○")) vm.conn.removePrefix("○").trim() else null
    val title = if (archivedView) "المؤرشفة" else "دردشة"

    Column(Modifier.fillMaxSize().background(IOS.bg).safeDrawingPadding()) {
        IosNavBar(
            editing = editing, archivedView = archivedView, selectedCount = selected.size,
            allSelected = convs.isNotEmpty() && convs.all { it.first in selected },
            collapsed = collapsed, connText = connText, title = title,
            onEdit = { editing = true },
            onDone = { editing = false; selected.clear() },
            onSelectAll = {
                if (convs.all { it.first in selected }) selected.clear()
                else convs.forEach { if (it.first !in selected) selected.add(it.first) }
            },
            onBack = { vm.route = Route.Home },
            onCompose = { vm.route = Route.NewChat },
            trailingMenu = {
                Box {
                    CircleIconButton(Icons.Filled.MoreHoriz, "المزيد") { menu = true }
                    IosMenu(
                        expanded = menu, onDismiss = { menu = false },
                        entries = listOf(
                            IosMenuEntry("حسابي", Icons.Outlined.Person) { menu = false; vm.route = Route.Me },
                            IosMenuEntry("الحسابات", Icons.Outlined.SwitchAccount) { menu = false; accountsSheet = true },
                            IosMenuEntry("واتساب", Icons.Outlined.QuestionAnswer) { menu = false; vm.openWhatsApp() },
                            IosMenuEntry("الرسائل المميّزة بنجمة", Icons.Outlined.StarBorder) { menu = false; vm.route = Route.Starred },
                            IosMenuEntry("الإعدادات", Icons.Outlined.Settings) { menu = false; vm.route = Route.Settings },
                        )
                    )
                }
            },
        )
        if (accountsSheet) AccountsSheet(vm) { accountsSheet = false }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = LocalTabInset.current)) {
            item(key = "large-title") {
                Text(
                    title, color = IOS.label, fontSize = AppSettings.largeTitleSp.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            item(key = "search") {
                IosSearchField(q, { q = it }, searchFocused) { searchFocused = it }
            }
            if (!archivedView && !searching) {
                item(key = "whatsapp-row") {
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val latest = WaInbox.convs.maxByOrNull { it.lastTime }
                    val sub = when {
                        !AppSettings.waInbox -> "اضغط للإعداد وتفعيل الصندوق"
                        latest == null -> "لا توجد رسائل بعد"
                        else -> latest.title + ": " + latest.msgs.lastOrNull()?.text.orEmpty()
                    }
                    Column(
                        Modifier.fillMaxWidth().background(if (pressed) IOS.pressed else IOS.bg)
                            .clickable(interactionSource = src, indication = null) { vm.openWhatsApp() }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(IosGreen),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.QuestionAnswer, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("واتساب", color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(sub, color = IOS.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (AppSettings.waInbox && WaInbox.convs.isNotEmpty())
                                Text("${WaInbox.convs.size}", color = IOS.secondary, fontSize = 17.sp)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(22.dp))
                        }
                        Box(Modifier.padding(start = 76.dp).fillMaxWidth().height(0.5.dp).background(IOS.separator))
                    }
                }
            }
            if (!archivedView && !searching && archivedCount > 0) {
                item(key = "archived-row") {
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    Column(
                        Modifier.fillMaxWidth().background(if (pressed) IOS.pressed else IOS.bg)
                            .clickable(interactionSource = src, indication = null) { vm.route = Route.Archived }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Archive, null, tint = IOS.blue, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("المؤرشفة", color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text("$archivedCount", color = IOS.secondary, fontSize = 17.sp)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(22.dp))
                        }
                        Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(IOS.separator))
                    }
                }
            }
            if (convs.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (searching) Icons.Filled.Search else Icons.Outlined.ChatBubbleOutline, null,
                            tint = IOS.tertiary, modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                searching -> "لا توجد نتائج"
                                archivedView -> "لا توجد محادثات مؤرشفة"
                                else -> "لا توجد محادثات"
                            },
                            color = IOS.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                searching -> "لا نتائج لـ «${q.trim()}»"
                                archivedView -> "المحادثات التي تؤرشفها تظهر هنا."
                                else -> "اضغط على أيقونة القلم في الأعلى لبدء محادثة."
                            },
                            color = IOS.secondary, fontSize = 15.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(convs, key = { it.first }) { (peer, last) ->
                    val pinned = vm.isPinned(peer)
                    val muted = vm.isMuted(peer)
                    val archived = vm.isArchived(peer)
                    val leading = if (archived) emptyList() else listOf(
                        SwipeAction(if (pinned) "إلغاء التثبيت" else "تثبيت", Icons.Filled.PushPin, IOS.orange) { vm.togglePin(peer) }
                    )
                    val trailing = listOf(
                        SwipeAction(if (muted) "إلغاء الكتم" else "كتم", if (muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, IOS.indigo) { vm.toggleMute(peer) },
                        SwipeAction(if (archived) "إلغاء الأرشفة" else "أرشفة", if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, IOS.blue) { vm.toggleArchive(peer) },
                        SwipeAction("حذف", Icons.Outlined.Delete, IOS.red) { pendingDelete = listOf(peer) },
                    )
                    Box(Modifier.animateItem()) {
                        SwipeRow(
                            enabled = !editing, closeSignal = openPeer != peer, onOpen = { openPeer = peer },
                            leading = leading, trailing = trailing
                        ) { isOpen, close ->
                            ConvRow(
                                vm, peer, last,
                                selected = peer in selected, editing = editing, isOpen = isOpen, close = close,
                                onTap = {
                                    if (editing) { if (peer in selected) selected.remove(peer) else selected.add(peer) }
                                    else vm.openChat(peer)
                                },
                                onLong = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editing = true
                                    if (peer !in selected) selected.add(peer)
                                }
                            )
                        }
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
        }

        AnimatedVisibility(
            visible = editing,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150))
        ) {
            val sel = selected.toList()
            val has = sel.isNotEmpty()
            val allPinned = sel.isNotEmpty() && sel.all { vm.isPinned(it) }
            val allMuted = sel.isNotEmpty() && sel.all { vm.isMuted(it) }
            val allArch = sel.isNotEmpty() && sel.all { vm.isArchived(it) }
            val canPin = has && sel.none { vm.isArchived(it) }
            Column(Modifier.fillMaxWidth().background(IOS.bar)) {
                HorizontalDivider(color = IOS.separator, thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToolbarButton(Icons.Outlined.PushPin, if (allPinned) "إلغاء التثبيت" else "تثبيت", canPin, IOS.blue) {
                        sel.filter { vm.isPinned(it) == allPinned }.forEach { vm.togglePin(it) }
                        selected.clear(); editing = false
                    }
                    ToolbarButton(if (allMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, if (allMuted) "إلغاء الكتم" else "كتم", has, IOS.blue) {
                        sel.filter { vm.isMuted(it) == allMuted }.forEach { vm.toggleMute(it) }
                        selected.clear(); editing = false
                    }
                    ToolbarButton(if (allArch) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (allArch) "إلغاء الأرشفة" else "أرشفة", has, IOS.blue) {
                        sel.filter { vm.isArchived(it) == allArch }.forEach { vm.toggleArchive(it) }
                        selected.clear(); editing = false
                    }
                    ToolbarButton(Icons.Outlined.Delete, "حذف", has, IOS.red) { pendingDelete = sel }
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        IosAlert(
            title = if (pendingDelete.size > 1) "حذف ${pendingDelete.size} محادثات؟" else "حذف المحادثة؟",
            message = "ستُحذف الرسائل من جهازك فقط، ولن تتأثر عند الطرف الآخر.",
            confirmText = "حذف",
            onConfirm = {
                pendingDelete.forEach { vm.hideChat(it) }
                selected.clear(); editing = false; pendingDelete = emptyList()
            },
            onDismiss = { pendingDelete = emptyList() }
        )
    }
}

// ───────────────────────── الشريط العلوي ─────────────────────────

@Composable
private fun IosNavBar(
    editing: Boolean, archivedView: Boolean, selectedCount: Int, allSelected: Boolean,
    collapsed: Boolean, connText: String?, title: String,
    onEdit: () -> Unit, onDone: () -> Unit, onSelectAll: () -> Unit,
    onBack: () -> Unit, onCompose: () -> Unit, trailingMenu: @Composable () -> Unit,
) {
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "titleAlpha")
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Box(Modifier.align(Alignment.Center)) {
            when {
                editing -> Text(
                    if (selectedCount > 0) "تم تحديد $selectedCount" else "تحديد المحادثات",
                    color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
                connText != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IosSpinner(Modifier.size(14.dp), color = IOS.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(connText, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                else -> Text(
                    title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alpha(titleAlpha)
                )
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                editing -> NavText("تم", bold = true, onClick = onDone)
                archivedView -> Row(Modifier.tap(onBack), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "رجوع", tint = IOS.blue, modifier = Modifier.size(30.dp))
                    Text("دردشة", color = IOS.blue, fontSize = 17.sp)
                }
                else -> NavText("تعديل", onClick = onEdit)
            }
            Spacer(Modifier.weight(1f))
            when {
                editing -> NavText(if (allSelected) "إلغاء التحديد" else "تحديد الكل", onClick = onSelectAll)
                archivedView -> {}
                else -> {
                    PlainIconButton(Icons.Outlined.Edit, "محادثة جديدة", onCompose)
                    Spacer(Modifier.width(2.dp))
                    trailingMenu()
                }
            }
        }
        HorizontalDivider(
            Modifier.align(Alignment.BottomCenter).alpha(if (editing) 1f else titleAlpha),
            color = IOS.separator, thickness = 0.5.dp
        )
    }
}

@Composable
private fun NavText(text: String, bold: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Text(
        text, color = IOS.blue, fontSize = 17.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.alpha(if (pressed) 0.35f else 1f)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun CircleIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.size(44.dp).alpha(if (pressed) 0.4f else 1f)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(IOS.fill), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = IOS.blue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlainIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.size(44.dp).alpha(if (pressed) 0.4f else 1f)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = IOS.blue, modifier = Modifier.size(24.dp)) }
}

// ───────────────────────── حقل البحث ─────────────────────────

@Composable
private fun IosSearchField(q: String, onQ: (String) -> Unit, focused: Boolean, onFocus: (Boolean) -> Unit) {
    val fm = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(IOS.fill).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = IOS.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text("بحث", color = IOS.secondary, fontSize = 17.sp)
                BasicTextField(
                    q, onQ, singleLine = true,
                    textStyle = TextStyle(color = IOS.label, fontSize = 17.sp),
                    cursorBrush = SolidColor(IOS.blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }
                )
            }
            if (q.isNotEmpty()) {
                Icon(
                    Icons.Filled.Cancel, "مسح", tint = IOS.secondary,
                    modifier = Modifier.size(18.dp).tap { onQ("") }
                )
            }
        }
        AnimatedVisibility(
            visible = focused || q.isNotEmpty(),
            enter = expandHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Text(
                "إلغاء", color = IOS.blue, fontSize = 17.sp,
                modifier = Modifier.padding(start = 12.dp).tap { onQ(""); fm.clearFocus() }
            )
        }
    }
}

// ───────────────────────── صف المحادثة ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvRow(
    vm: MainVM, peer: String, last: Message, selected: Boolean, editing: Boolean,
    isOpen: Boolean, close: () -> Unit, onTap: () -> Unit, onLong: () -> Unit
) {
    val name = vm.name(peer)
    val un = vm.unread(peer)
    val typing = vm.typingFrom[peer] == true
    val mine = last.sender == vm.meId
    val muted = vm.isMuted(peer)
    val pinned = vm.isPinned(peer)
    var bigPhoto by remember { mutableStateOf(false) }
    val avatarPath = vm.peers[peer]?.avatarPath
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) IOS.pressed else IOS.bg, tween(if (pressed) 0 else 250), label = "rowBg")
    val inset by animateDpAsState(if (editing) 118.dp else 82.dp, spring(dampingRatio = 0.85f, stiffness = 400f), label = "inset")

    Column(
        Modifier.fillMaxWidth().background(bg).combinedClickable(
            interactionSource = src, indication = null,
            onClick = { if (isOpen) close() else onTap() }, onLongClick = onLong
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = AppSettings.listRowPad.dp, bottom = AppSettings.listRowPad.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = editing,
                enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 400f)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectCircle(selected)
                    Spacer(Modifier.width(12.dp))
                }
            }
            Box(
                Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { if (!editing && !isOpen && avatarPath != null) bigPhoto = true else if (isOpen) close() else onTap() },
                    onLongClick = onLong
                )
            ) {
                Avatar(name, avatarPath, 54.dp, vm.isOnline(peer), ring = IOS.bg, plain = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, color = IOS.label, fontSize = AppSettings.listNameSp.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (muted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.NotificationsOff, "مكتومة", tint = IOS.secondary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        fmtList(last.createdAt), fontSize = 15.sp,
                        color = if (un > 0 && !muted) IOS.blue else IOS.secondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (typing) {
                            Text("يكتب...", color = IOS.blue, fontSize = 15.sp)
                        } else {
                            if (mine && !last.isDeleted) {
                                val rd = last.readAt != null
                                Icon(
                                    if (rd || vm.isOnline(peer)) Icons.Filled.DoneAll else Icons.Filled.Done,
                                    if (rd) "تمت القراءة" else if (vm.isOnline(peer)) "تم التسليم" else "تم الإرسال",
                                    tint = if (rd) IOS.blue else IOS.secondary, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            val draft = vm.drafts[peer]
                            if (!draft.isNullOrBlank()) {
                                Text("مسودة: ", color = IOS.red, fontSize = 15.sp)
                                Text(draft, color = IOS.secondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else Text(
                                vm.preview(last), color = IOS.secondary, fontSize = AppSettings.listPreviewSp.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.PushPin, "مثبّتة", tint = IOS.secondary, modifier = Modifier.size(15.dp).rotate(45f))
                    }
                    if (un > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .background(if (muted) IOS.gray else IOS.blue, CircleShape).padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("$un", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(IOS.separator))
    }
    if (bigPhoto && avatarPath != null) AvatarPreviewDialog(avatarPath) { bigPhoto = false }
}

@Composable
internal fun SelectCircle(on: Boolean) {
    val fill by animateColorAsState(if (on) IOS.blue else Color.Transparent, tween(150), label = "selFill")
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(fill)
            .then(if (on) Modifier else Modifier.border(1.5.dp, IOS.tertiary, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

// ───────────────────────── السحب لإظهار الأزرار (مثل iOS) ─────────────────────────

@Composable
internal fun SwipeRow(
    enabled: Boolean, closeSignal: Boolean, onOpen: () -> Unit,
    leading: List<SwipeAction>, trailing: List<SwipeAction>,
    content: @Composable (isOpen: Boolean, close: () -> Unit) -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1f else 1f
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val actionW = with(density) { 80.dp.toPx() }
    val leadingS by rememberUpdatedState(leading)
    val trailingS by rememberUpdatedState(trailing)
    // x منطقي: موجب = الصف يتحرك نحو النهاية فتظهر أزرار البداية (leading)، سالب = تظهر أزرار النهاية (trailing)
    val x = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val spr = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val isOpen by remember { derivedStateOf { abs(x.value) > 1f } }

    LaunchedEffect(closeSignal) { if (closeSignal && x.value != 0f) x.animateTo(0f, spr) }
    LaunchedEffect(enabled) { if (!enabled && x.value != 0f) x.animateTo(0f, spr) }
    val close: () -> Unit = { scope.launch { x.animateTo(0f, spr) } }

    Box(Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }.clipToBounds()) {
        val v = x.value
        Box(Modifier.matchParentSize()) {
            if (v > 0f && leading.isNotEmpty()) ActionStrip(
                leading, v, actionW, outerFirst = true,
                onFire = { it.onClick(); close() },
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (v < 0f && trailing.isNotEmpty()) ActionStrip(
                trailing, -v, actionW, outerFirst = false,
                onFire = { it.onClick(); close() },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Box(
            Modifier
                .offset { IntOffset((x.value * sign).roundToInt(), 0) }
                .pointerInput(enabled, widthPx, sign) {
                    if (!enabled) return@pointerInput
                    fun settle() {
                        val cur = x.value
                        val lw = actionW * leadingS.size
                        val tw = actionW * trailingS.size
                        scope.launch {
                            when {
                                cur > widthPx * 0.55f && leadingS.isNotEmpty() -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    leadingS.first().onClick(); x.animateTo(0f, spr)
                                }
                                cur < -widthPx * 0.55f && trailingS.isNotEmpty() -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    trailingS.last().onClick(); x.animateTo(0f, spr)
                                }
                                cur > lw * 0.5f && leadingS.isNotEmpty() -> x.animateTo(lw, spr)
                                cur < -tw * 0.5f && trailingS.isNotEmpty() -> x.animateTo(-tw, spr)
                                else -> x.animateTo(0f, spr)
                            }
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { onOpen() },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            var n = x.value + dx * sign
                            if (n > 0f && leadingS.isEmpty()) n = 0f
                            if (n < 0f && trailingS.isEmpty()) n = 0f
                            n = n.coerceIn(-widthPx * 0.95f, widthPx * 0.95f)
                            scope.launch { x.snapTo(n) }
                        }
                    )
                }
        ) { content(isOpen, close) }
    }
}

@Composable
private fun ActionStrip(
    actions: List<SwipeAction>, reveal: Float, actionW: Float, outerFirst: Boolean,
    onFire: (SwipeAction) -> Unit, modifier: Modifier
) {
    val density = LocalDensity.current
    val n = actions.size
    val total = actionW * n
    val outer = if (outerFirst) 0 else n - 1
    fun widthOf(i: Int): Float =
        if (reveal <= total) reveal / n else if (i == outer) reveal - (n - 1) * actionW else actionW
    Row(modifier.width(with(density) { reveal.toDp() }).fillMaxHeight()) {
        actions.forEachIndexed { i, a ->
            Box(
                Modifier.width(with(density) { widthOf(i).toDp() }).fillMaxHeight()
                    .background(a.color).clipToBounds().tap { onFire(a) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.requiredWidth(with(density) { actionW.toDp() }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(a.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// ───────────────────────── الشريط السفلي + التنبيه ─────────────────────────

@Composable
private fun RowScope.ToolbarButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(
        Modifier.weight(1f)
            .alpha(if (!enabled) 0.35f else if (pressed) 0.5f else 1f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

/** تنبيه iOS: يظهر بتكبير نابض (1.1→1) مع تلاشي، أزرار بتظليل عند الضغط، وزر «إلغاء» اختياري. */
@Composable
internal fun IosAlert(
    title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = true, cancelText: String? = "إلغاء"
) {
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.spring(0.72f, 420f)) }
    val haptic = LocalHapticFeedback.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(270.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 1.12f - 0.12f * p; scaleY = 1.12f - 0.12f * p
                }
                .clip(RoundedCornerShape(14.dp)).background(IOS.alertBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = IOS.label, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            HorizontalDivider(color = IOS.alertSep, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                if (cancelText != null) {
                    Box(Modifier.weight(1f).fillMaxHeight().pressRow(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Text(cancelText, color = IOS.blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    VerticalDivider(color = IOS.alertSep, thickness = 0.5.dp)
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressRow {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onConfirm()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        confirmText, color = if (danger) IOS.red else IOS.blue, fontSize = 17.sp,
                        fontWeight = if (danger && cancelText != null) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
