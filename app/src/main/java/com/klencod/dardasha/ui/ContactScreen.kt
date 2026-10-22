package com.klencod.dardasha.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.fmtSeen
import com.klencod.dardasha.data.linkLabel
import com.klencod.dardasha.data.normalizeUrl
import com.klencod.dardasha.timerLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

private val TIMER_CHOICES = listOf(0, 24 * 3600, 7 * 24 * 3600, 90 * 24 * 3600)

/** معلومات جهة الاتصال بأسلوب iOS: Inset Grouped + حركات نابضة + سحب للرجوع من الحافة. */
@Composable
fun ContactScreen(vm: MainVM, peer: String) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val p = vm.peers[peer]
    val blocked = peer in vm.blocked
    val muted = vm.isMuted(peer)

    var timerSheet by remember { mutableStateOf(false) }
    var blockAlert by remember { mutableStateOf(false) }
    var bigPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(peer) { vm.fetchSeen(peer) }
    fun goChat() { vm.route = Route.Conversation(peer) }
    BackHandler(enabled = timerSheet) { timerSheet = false }

    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 4 } }

    IosPage(onBack = ::goChat, overlay = {
        // ── Action Sheet للرسائل المؤقتة ──
        AnimatedVisibility(timerSheet, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).tap { timerSheet = false })
        }
        AnimatedVisibility(
            timerSheet, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.85f, 380f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            TimerSheet(
                current = vm.timerOf(peer),
                onPick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.setTimer(peer, it); timerSheet = false },
                onDismiss = { timerSheet = false },
            )
        }
    }) {
            Column(Modifier.fillMaxSize()) {
                IosTopBar("معلومات جهة الاتصال", onBack = ::goChat, separatorVisible = scrolled)

                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // ── الرأس: صورة + اسم + حالة ──
                    Appear(0) {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val src = remember { MutableInteractionSource() }
                            val pressed by src.collectIsPressedAsState()
                            val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.6f, 700f), label = "avs")
                            Box(
                                Modifier.graphicsLayer { scaleX = s; scaleY = s }
                                    .clickable(src, null, enabled = p?.avatarPath != null) { bigPhoto = true }
                            ) { Avatar(vm.name(peer), p?.avatarPath, 112.dp, vm.isOnline(peer), ring = Grouped) }
                            Spacer(Modifier.height(14.dp))
                            Text(vm.name(peer), color = IOS.label, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            if (p != null) Text("@" + p.username, color = IOS.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                            Text(
                                when {
                                    vm.isOnline(peer) -> "متصل الآن"
                                    vm.lastSeenOf(peer) != null -> "آخر ظهور " + fmtSeen(vm.lastSeenOf(peer))
                                    else -> "غير متصل"
                                },
                                color = if (vm.isOnline(peer)) IosGreen else IOS.secondary, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // ── أزرار سريعة ──
                    Appear(1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAction(Modifier.weight(1f), Icons.Filled.Chat, "رسالة", false) { goChat() }
                            QuickAction(
                                Modifier.weight(1f),
                                if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                if (muted) "إلغاء الكتم" else "كتم", muted
                            ) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleMute(peer) }
                            QuickAction(Modifier.weight(1f), Icons.Filled.PermMedia, "الوسائط", false) { vm.route = Route.Media(peer) }
                        }
                    }

                    // ── حول / نبذة / المدينة / الميلاد ──
                    val info = buildList<Pair<String, String>> {
                        if (!p?.about.isNullOrBlank()) add("حول" to p!!.about!!)
                        if (!p?.bio.isNullOrBlank()) add("نبذة" to p!!.bio!!)
                        if (!p?.city.isNullOrBlank()) add("المدينة" to "📍 " + p!!.city!!)
                        p?.birthday?.takeIf { it.isNotBlank() }?.let { add("تاريخ الميلاد" to birthdayText(it)) }
                    }
                    Appear(2) {
                        IGroup {
                            if (info.isEmpty()) {
                                RowBox(last = true, inset = 16.dp, onClick = null) {
                                    Text(
                                        "لم يضف هذا المستخدم معلومات بعد.", color = IOS.secondary, fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                    )
                                }
                            } else info.forEachIndexed { i, (label, value) ->
                                RowBox(last = i == info.lastIndex, inset = 16.dp, onClick = null) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Text(label, color = IOS.secondary, fontSize = 13.sp)
                                        Text(value, color = IOS.label, fontSize = 17.sp, modifier = Modifier.padding(top = 1.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── الروابط ──
                    val links = p?.links.orEmpty().mapNotNull { l -> normalizeUrl(l.url)?.let { u -> l.title to u } }
                    if (links.isNotEmpty()) Appear(3) {
                        IGroup(header = "الروابط") {
                            links.forEachIndexed { i, (title, url) ->
                                RowBox(last = i == links.lastIndex, inset = 16.dp, onClick = {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }.onFailure { vm.toast = "تعذّر فتح الرابط" }
                                }) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(title.ifBlank { linkLabel(url) }, color = IOS.blue, fontSize = 17.sp)
                                            Text(url, color = IOS.secondary, fontSize = 13.sp, maxLines = 1)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── إعدادات المحادثة ──
                    Appear(4) {
                        val mediaCount = vm.thread(peer).count {
                            !it.isDeleted && ((it.imagePath != null && it.viewOnce != true) || it.filePath != null)
                        }
                        IGroup {
                            IRow(Icons.Filled.PermMedia, IOS.blue, "الوسائط والملفات والروابط",
                                value = if (mediaCount > 0) "$mediaCount" else null, chevron = true, last = false
                            ) { vm.route = Route.Media(peer) }
                            IRow(Icons.Filled.Timer, IOS.orange, "الرسائل المؤقتة",
                                value = if (vm.timerOf(peer) > 0) timerLabel(vm.timerOf(peer)) else "إيقاف", chevron = true, last = false
                            ) { timerSheet = true }
                            IRow(Icons.Filled.NotificationsOff, IOS.red, "كتم الإشعارات", last = true,
                                trailing = { ISwitch(muted) }
                            ) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleMute(peer) }
                        }
                    }

                    // ── حظر ──
                    Appear(5) {
                        IGroup {
                            RowBox(last = true, inset = 16.dp, onClick = {
                                if (blocked) vm.unblock(peer) else blockAlert = true
                            }) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                    Text(if (blocked) "إلغاء الحظر" else "حظر", color = if (blocked) IOS.blue else IOS.red, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                }
            }

    }

    if (blockAlert) IosAlert(
        title = "حظر " + vm.name(peer),
        message = "هل تريد حظر جهة الاتصال هذه؟",
        confirmText = "حظر",
        onConfirm = { blockAlert = false; vm.block(peer) },
        onDismiss = { blockAlert = false },
    )

    if (bigPhoto && p?.avatarPath != null) PhotoViewer(p.avatarPath!!) { bigPhoto = false }
}

// ───────────────────────── مكوّنات ─────────────────────────

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.6f, 800f), label = "qa")
    val bg by animateColorAsState(if (active) IOS.blue else CellBg, tween(200), label = "qabg")
    val fg = if (active) Color.White else IOS.blue
    Column(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(src, null, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun TimerSheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = if (current in TIMER_CHOICES) TIMER_CHOICES else TIMER_CHOICES + current
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IOS.alertBg)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الرسائل المؤقتة", color = IOS.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "الرسائل الجديدة في هذه المحادثة ستختفي لدى الطرفين بعد المدة المحددة. لا يؤثر على الرسائل السابقة.",
                    color = IOS.secondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)
                )
            }
            options.forEach { s ->
                HorizontalDivider(color = IOS.alertSep, thickness = 0.5.dp)
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(if (pressed) IOS.pressed else Color.Transparent)
                        .clickable(src, null) { onPick(s) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (s == 0) "إيقاف" else timerLabel(s), color = IOS.blue, fontSize = 20.sp,
                        fontWeight = if (s == current) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (s == current) Icon(
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

@Composable
private fun PhotoViewer(path: String, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.85f, spring(0.8f, 300f), label = "pv")
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "pva")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = a)).tap(onClose), contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(
                model = com.klencod.dardasha.data.Repo.avatarUrl(path), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s; alpha = a }
            )
        }
    }
}

/** "2000-05-14" → "14 مايو 2000 (25 سنة)" */
private fun birthdayText(iso: String): String = runCatching {
    val d = LocalDate.parse(iso.take(10))
    val age = Period.between(d, LocalDate.now()).years
    val f = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
    f.format(d) + " ($age سنة)"
}.getOrDefault(iso)
