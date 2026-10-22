package com.klencod.dardasha.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.first
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.Route
import com.klencod.dardasha.timerLabel
import androidx.compose.ui.window.SecureFlagPolicy
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.io.File
import com.klencod.dardasha.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "👏")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainVM, peer: String) {
    val ctx = LocalContext.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(AppSettings.keepAwake) {
        hostView.keepScreenOn = AppSettings.keepAwake
        onDispose { hostView.keepScreenOn = false }
    }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val me = vm.meId ?: return

    val thread by remember(peer) { derivedStateOf { vm.thread(peer) } }
    var text by remember(peer) { mutableStateOf(vm.draftOf(peer)) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    val inputFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var jumpHl by remember { mutableStateOf<String?>(null) }
    var searchOn by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matchPos by remember { mutableIntStateOf(0) }
    var infoMsg by remember { mutableStateOf<Message?>(null) }
    var newBelow by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Message?>(null) }
    val selected = remember(peer) { mutableStateListOf<String>() }
    var selMenu by remember { mutableStateOf(false) }
    var ctxMsg by remember { mutableStateOf<Message?>(null) }   // القائمة السياقية (ضغط مطوّل)
    var ctxTail by remember { mutableStateOf(false) }
    var ctxDelete by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selTop by remember { mutableFloatStateOf(0f) }    // أعلى الفقاعة المختارة (إحداثيات النافذة)
    var selBottom by remember { mutableFloatStateOf(0f) } // أسفلها
    var emojiFor by remember { mutableStateOf<String?>(null) } // "react" | "input"
    var camUri by remember { mutableStateOf<Uri?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var pendingImg by remember { mutableStateOf<ByteArray?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    var topMenu by remember { mutableStateOf(false) }
    var attachSheet by remember { mutableStateOf(false) }
    var pendingDoc by remember { mutableStateOf<PickedDoc?>(null) }
    var confirmLoc by remember { mutableStateOf(false) }
    var locBusy by remember { mutableStateOf(false) }
    var forwardMsgs by remember { mutableStateOf<List<Message>>(emptyList()) }
    var timerDialog by remember { mutableStateOf(false) }
    var viewOnceNext by remember { mutableStateOf(false) }
    var onceMsg by remember { mutableStateOf<Message?>(null) }
    val recorder = remember { VoiceRecorder(ctx) }
    val isBlocked = peer in vm.blocked
    val selecting = selected.isNotEmpty()
    val selMsgs = thread.filter { it.id in selected }
    fun toggleSel(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }
    val otherUnread by remember(peer) {
        derivedStateOf { vm.conversations().filter { it.first != peer }.sumOf { vm.unread(it.first) } }
    }
    BackHandler(enabled = ctxMsg != null) { ctxMsg = null }
    BackHandler(enabled = selecting && ctxMsg == null) { selected.clear() }
    BackHandler(enabled = searchOn && !selecting) { searchOn = false; query = "" }

    // ---- مسودة: تُحفظ أثناء الكتابة (ليس أثناء التعديل) ----
    LaunchedEffect(text, editing) { if (editing == null) vm.setDraft(peer, text) }

    // ---- قائمة الرسائل: الأحدث أولاً ----
    val rev = thread.asReversed()
    val lastMineId = rev.firstOrNull { it.sender == me && !it.isDeleted }?.id
    val typingNow = vm.typingFrom[peer] == true && !isBlocked
    val typingPad by animateDpAsState(if (typingNow) 44.dp else 0.dp, spring(0.8f, 400f), label = "typPad")

    // ---- البحث داخل المحادثة ----
    val matches = remember(query, searchOn, thread) {
        if (!searchOn || query.isBlank()) emptyList<Int>()
        else rev.indices.filter { !rev[it].isDeleted && rev[it].text.contains(query.trim(), ignoreCase = true) }
    }
    val mp = if (matches.isEmpty()) 0 else matchPos.coerceIn(0, matches.size - 1)
    val curMatchId = matches.getOrNull(mp)?.let { rev.getOrNull(it)?.id }
    LaunchedEffect(query) { matchPos = 0 }
    LaunchedEffect(matches, mp) { matches.getOrNull(mp)?.let { listState.animateScrollToItem(it) } }

    // ---- الانتقال لرسالة (من الاقتباس) ----
    fun jumpTo(id: String) {
        val i = rev.indexOfFirst { it.id == id }
        if (i < 0) { vm.toast = "الرسالة الأصلية غير متاحة"; return }
        scope.launch {
            listState.animateScrollToItem(i)
            jumpHl = id
            delay(1500)
            if (jumpHl == id) jumpHl = null
        }
    }

    // ---- أول رسالة غير مقروءة عند فتح المحادثة: فاصل + انتقال إليها ----
    var firstUnreadId by remember(peer) { mutableStateOf<String?>(null) }
    var unreadAtOpen by remember(peer) { mutableIntStateOf(0) }

    // ---- زر النزول لآخر رسالة + عدّاد الجديد ----
    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    val lastMsg = rev.firstOrNull()
    LaunchedEffect(lastMsg?.id) {
        val last = lastMsg ?: return@LaunchedEffect
        if (last.sender == me) { if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0) }
        else if (listState.firstVisibleItemIndex > 1) newBelow++
    }
    LaunchedEffect(showDown) { if (!showDown) newBelow = 0 }
    LaunchedEffect(peer) {
        val th = snapshotFlow { thread }.first { it.isNotEmpty() }
        val un = th.filter { it.sender != me && vm.isUnreadIn(it) && !it.isDeleted }
        if (un.isEmpty()) return@LaunchedEffect
        firstUnreadId = un.first().id
        unreadAtOpen = un.size
        val idx = th.size - 1 - th.indexOfFirst { it.id == un.first().id }
        if (idx > 3) {
            snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val h = listState.layoutInfo.viewportSize.height
            listState.scrollToItem(idx, -(h * 0.6f).toInt())
            newBelow = un.size
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri) }
            if (b != null) { pendingImg = b; pendingDoc = null } else vm.toast = "تعذّر قراءة الصورة"
        }
    }
    val docPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Media.readDoc(ctx, uri) } }
            val d = r.getOrNull()
            if (d != null) { pendingDoc = d; pendingImg = null }
            else vm.toast = if (r.exceptionOrNull()?.message == "big") "الملف كبير (الحد الأقصى 20 ميغابايت)" else "تعذّر قراءة الملف"
        }
    }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Media.compress(ctx, u) }
            if (b != null) { pendingImg = b; pendingDoc = null } else vm.toast = "تعذّر قراءة الصورة"
        }
    }
    fun openCamera() {
        runCatching {
            val dir = File(ctx.cacheDir, "files").apply { mkdirs() }
            val f = File(dir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            camUri = uri
            camLauncher.launch(uri)
        }.onFailure { vm.toast = "تعذّر فتح الكاميرا" }
    }
    fun shareLocation() {
        scope.launch {
            locBusy = true
            val l = Loc.current(ctx)
            locBusy = false
            if (l == null) vm.toast = "تعذّر تحديد موقعك — فعّل خدمة الموقع"
            else vm.sendLocation(peer, l.latitude, l.longitude, replyTo)
            replyTo = null
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) shareLocation() else vm.toast = "يلزم إذن الموقع"
    }
    fun startLocation() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) shareLocation()
        else locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (recorder.start()) recording = true else vm.toast = "تعذّر بدء التسجيل" }
        else vm.toast = "يلزم إذن الميكروفون"
    }

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        if (recording) { recSecs = 0; while (true) { delay(1000); recSecs++ } }
    }
    LaunchedEffect(peer) { vm.fetchSeen(peer) }
    LaunchedEffect(peer, thread.size) { vm.markRead(peer) }

    fun doSend() {
        val body = text.trim()
        val ed = editing
        if (ed != null) {
            if (body.isNotEmpty() && body != ed.text) vm.editMessage(ed, body)
            editing = null; text = ""; return
        }
        val img = pendingImg
        val doc = pendingDoc
        if (body.isEmpty() && img == null && doc == null) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (doc != null) vm.sendFile(peer, doc, body, replyTo)
        else vm.send(peer, body, replyTo, img, viewOnce = viewOnceNext && img != null)
        text = ""; replyTo = null; pendingImg = null; pendingDoc = null; viewOnceNext = false; vm.typing(peer, false)
    }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recorder.start()) recording = true else vm.toast = "تعذّر بدء التسجيل"
        } else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    val blurR by animateDpAsState(if (ctxMsg != null) 18.dp else 0.dp, tween(260), label = "ctxBlur")
    Box(
        Modifier.fillMaxSize()
            .then(if (blurR > 0.dp) Modifier.blur(blurR) else Modifier)
            .background(IOS.bg).safeDrawingPadding()
    ) {
    Column(Modifier.fillMaxSize()) {
        // ---------- top bar ----------
        if (selecting) {
            Box(Modifier.fillMaxWidth().background(IOS.bg).heightIn(min = 52.dp).padding(horizontal = 6.dp)) {
                IosTextButton(onClick = { selected.clear() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("إلغاء", color = IOS.blue, fontSize = 17.sp)
                }
                Text(
                    when (selected.size) { 1 -> "رسالة واحدة"; 2 -> "رسالتان"; in 3..10 -> "${selected.size} رسائل"; else -> "${selected.size} رسالة" },
                    color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = IOS.separator, thickness = 0.5.dp)
            }
        } else if (searchOn) {
            Row(
                Modifier.fillMaxWidth().background(IOS.bg).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosIconButton(onClick = { searchOn = false; query = "" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "إغلاق البحث", tint = IOS.label)
                }
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                    singleLine = true,
                    textStyle = TextStyle(color = IOS.label, fontSize = 16.sp),
                    cursorBrush = SolidColor(IOS.blue),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("بحث في المحادثة", color = IOS.secondary, fontSize = 16.sp)
                        inner()
                    }
                )
                if (query.isNotBlank()) Text(
                    if (matches.isEmpty()) "0" else "${mp + 1}/${matches.size}",
                    color = IOS.secondary, fontSize = 13.sp
                )
                IosIconButton(enabled = mp < matches.size - 1, onClick = { matchPos = mp + 1 }) {
                    Icon(Icons.Filled.KeyboardArrowUp, "الأقدم", tint = if (mp < matches.size - 1) IOS.label else IOS.secondary)
                }
                IosIconButton(enabled = mp > 0, onClick = { matchPos = mp - 1 }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "الأحدث", tint = if (mp > 0) IOS.label else IOS.secondary)
                }
            }
            LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
        } else
        Box(Modifier.fillMaxWidth().background(IOS.bg)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // ── رجوع: كبسولة زجاجية + عدّاد غير المقروء (مثل iOS) ──
                GlassButton(
                    onClick = { vm.route = Route.Home },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, "رجوع", tint = IOS.blue,
                        modifier = Modifier.padding(start = if (otherUnread > 0) 6.dp else 0.dp).size(30.dp)
                    )
                    if (otherUnread > 0) Text(
                        if (otherUnread > 99) "99+" else "$otherUnread",
                        color = IOS.blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                // ── الوسط: صورة بجانب الاسم وآخر ظهور (يفتح معلومات جهة الاتصال) ──
                val (sub, live) = when {
                    isBlocked -> "🚫 محظور" to false
                    vm.typingFrom[peer] == true -> "يكتب الآن..." to true
                    vm.isOnline(peer) -> "متصل الآن" to true
                    vm.lastSeenOf(peer) != null -> "آخر ظهور " + fmtSeen(vm.lastSeenOf(peer)) to false
                    else -> "غير متصل" to false
                }
                Row(
                    Modifier.align(Alignment.Center).padding(start = 64.dp, end = 100.dp)
                        .tap { vm.route = Route.Contact(peer) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(vm.name(peer), vm.peers[peer]?.avatarPath, 36.dp, ring = IOS.bg, plain = true)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                vm.name(peer), color = IOS.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            sub, color = if (live) IOS.blue else IOS.secondary, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // ── النهاية: بحث + قائمة ──
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(onClick = { searchOn = true }) {
                        Icon(Icons.Filled.Search, "بحث", tint = IOS.blue, modifier = Modifier.size(22.dp))
                    }
                    Box {
                        GlassButton(onClick = { topMenu = true }) {
                            Icon(Icons.Filled.MoreHoriz, "المزيد", tint = IOS.blue, modifier = Modifier.size(24.dp))
                        }
                        IosMenu(
                            expanded = topMenu, onDismiss = { topMenu = false },
                            entries = listOf(
                                IosMenuEntry("بحث في المحادثة", Icons.Filled.Search) { topMenu = false; searchOn = true },
                                IosMenuEntry("معلومات جهة الاتصال", Icons.Filled.Info) { topMenu = false; vm.route = Route.Contact(peer) },
                                IosMenuEntry("الرسائل المؤقتة", Icons.Filled.Timer) { topMenu = false; timerDialog = true },
                                if (!vm.isArchived(peer)) IosMenuEntry(
                                    if (vm.isPinned(peer)) "إلغاء التثبيت" else "تثبيت المحادثة", Icons.Filled.PushPin
                                ) { topMenu = false; vm.togglePin(peer) } else null,
                                IosMenuEntry(
                                    if (vm.isMuted(peer)) "إلغاء الكتم" else "كتم الإشعارات",
                                    if (vm.isMuted(peer)) Icons.Filled.Notifications else Icons.Filled.NotificationsOff
                                ) { topMenu = false; vm.toggleMute(peer) },
                                IosMenuEntry(
                                    if (vm.isArchived(peer)) "إلغاء الأرشفة" else "أرشفة المحادثة",
                                    if (vm.isArchived(peer)) Icons.Filled.Unarchive else Icons.Filled.Archive
                                ) { topMenu = false; vm.toggleArchive(peer) },
                                IosMenuEntry(
                                    if (isBlocked) "إلغاء الحظر" else "حظر", Icons.Filled.Block, danger = !isBlocked
                                ) { topMenu = false; if (isBlocked) vm.unblock(peer) else vm.block(peer) },
                            )
                        )
                    }
                }
            }
            // فاصل رفيع يظهر فقط حين يمرّ المحتوى تحت الشريط
            val barSep by animateFloatAsState(if (listState.canScrollForward) 1f else 0f, tween(200), label = "barSep")
            HorizontalDivider(Modifier.align(Alignment.BottomCenter).alpha(barSep), color = IOS.separator, thickness = 0.5.dp)
        }

        if (vm.timerOf(peer) > 0) {
            Row(
                Modifier.fillMaxWidth().background(IOS.fill).clickable { timerDialog = true }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Timer, null, tint = IOS.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "الرسائل المؤقتة مفعّلة — تختفي بعد " + timerLabel(vm.timerOf(peer)),
                    color = IOS.secondary, fontSize = 12.sp
                )
            }
        }

        // ---------- messages ----------
        Box(Modifier.weight(1f).fillMaxWidth().background(WA.wall)) {
        if (AppSettings.chatPattern) ChatWallpaper(Modifier.fillMaxSize())
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + typingPad),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(rev.size, key = { rev[it].id }) { i ->
                val m = rev[i]
                // فاصل اليوم يظهر فوق أول رسالة في اليوم (أي بعدها في القائمة المعكوسة)
                val older = rev.getOrNull(i + 1)
                val newer = rev.getOrNull(i - 1)
                val tail = older == null || older.sender != m.sender || dayKey(older.createdAt) != dayKey(m.createdAt)
                val runEnd = newer == null || newer.sender != m.sender || dayKey(newer.createdAt) != dayKey(m.createdAt)
                val gapMs = (parseIso(m.createdAt) ?: 0L) - (parseIso(older?.createdAt) ?: 0L)
                val showStamp = older == null || dayKey(older.createdAt) != dayKey(m.createdAt) || gapMs >= AppSettings.stampGapMin * 60_000L
                Column(Modifier.animateItem()) {
                    if (showStamp) {
                        // طابع الوقت يظهر فوق أول رسالة بعد فجوة طويلة أو في يوم جديد (مثل iMessage)
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                fmtSeen(m.createdAt), color = IOS.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (m.id == firstUnreadId) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when (unreadAtOpen) {
                                    1 -> "رسالة واحدة غير مقروءة"
                                    2 -> "رسالتان غير مقروءتان"
                                    in 3..10 -> "$unreadAtOpen رسائل غير مقروءة"
                                    else -> "$unreadAtOpen رسالة غير مقروءة"
                                },
                                color = IOS.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Bubble(
                        vm, m, m.sender == me,
                        tail = runEnd && AppSettings.bubbleTail,
                        isLastMine = m.id == lastMineId,
                        selecting = selecting,
                        ctx = ctxMsg?.id == m.id,
                        selected = m.id in selected,
                        highlight = m.id == jumpHl || m.id == curMatchId,
                        onQuoteClick = { id -> if (selecting) toggleSel(m.id) else jumpTo(id) },
                        canSwipeReply = !selecting && !m.isDeleted && !isBlocked && editing == null,
                        onSwipeReply = {
                            replyTo = m
                            runCatching { inputFocus.requestFocus() }
                        },
                        onBounds = { t, b -> selTop = t; selBottom = b },
                        onLong = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (selecting) toggleSel(m.id) else { ctxTail = runEnd; ctxMsg = m }
                        },
                        onTap = { if (selecting) toggleSel(m.id) },
                        onImage = { if (selecting) toggleSel(m.id) else viewer = it },
                        onOnce = { if (selecting) toggleSel(m.id) else onceMsg = it }
                    )
                    if (runEnd) Spacer(Modifier.height(6.dp))
                }
            }
        }
        // فقاعة «يكتب الآن…» أسفل القائمة
        androidx.compose.animation.AnimatedVisibility(
            visible = typingNow,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp),
            enter = androidx.compose.animation.scaleIn(spring(0.6f, 500f), transformOrigin = TransformOrigin(0.5f, 1f)) +
                androidx.compose.animation.fadeIn(tween(150)),
            exit = androidx.compose.animation.scaleOut(tween(150), transformOrigin = TransformOrigin(0.5f, 1f)) +
                androidx.compose.animation.fadeOut(tween(150)),
        ) { TypingBubble() }
        if (showDown) {
            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                IosFilledCircleButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    container = WA.glass, size = 40.dp,
                ) { Icon(Icons.Filled.KeyboardArrowDown, "إلى آخر رسالة", tint = IOS.blue) }
                if (newBelow > 0) Text(
                    if (newBelow > 99) "99+" else "$newBelow",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp)
                        .background(IOS.blue, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        } // Box list

        // ---------- context bar (reply / edit / image) ----------
        val ctxMsg = editing ?: replyTo
        if (ctxMsg != null || pendingImg != null || pendingDoc != null) {
            Row(
                Modifier.fillMaxWidth().background(IOS.fill).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pendingImg?.let { b ->
                    val bmp = remember(b) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
                    if (bmp != null) {
                        Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    when {
                        editing != null -> "✏ تعديل الرسالة"
                        replyTo != null -> "↩ " + vm.preview(replyTo!!)
                        pendingDoc != null -> "📎 " + pendingDoc!!.name
                        viewOnceNext -> "① صورة لمرة واحدة جاهزة للإرسال"
                        else -> "📷 صورة جاهزة للإرسال"
                    },
                    color = IOS.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (pendingImg != null && editing == null) {
                    IosTextButton(onClick = { viewOnceNext = !viewOnceNext }) {
                        Text(
                            if (viewOnceNext) "① مرة واحدة ✓" else "① مرة واحدة",
                            color = if (viewOnceNext) IOS.blue else IOS.secondary, fontSize = 13.sp
                        )
                    }
                }
                IosIconButton(onClick = {
                    if (editing != null) text = ""
                    editing = null; replyTo = null; pendingImg = null; pendingDoc = null; viewOnceNext = false
                }) { Icon(Icons.Filled.Close, "إلغاء", tint = IOS.secondary) }
            }
        }

        // ---------- input ----------
        when {
            selecting -> {
                val live = selMsgs.filter { !it.isDeleted }
                val allStar = live.isNotEmpty() && live.all { vm.isStarred(it.id) }
                val fwd = live.filter { it.viewOnce != true }
                val texts = live.filter { it.text.isNotBlank() && it.audioPath == null }
                Box(Modifier.fillMaxWidth().background(IOS.bg)) {
                    HorizontalDivider(Modifier.align(Alignment.TopCenter), color = IOS.separator, thickness = 0.5.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosIconButton(onClick = { ctxDelete = false; confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "حذف", tint = IOS.red)
                        }
                        IosIconButton(enabled = live.isNotEmpty(), onClick = {
                            live.filter { vm.isStarred(it.id) == allStar }.forEach { vm.toggleStar(it) }
                            selected.clear()
                        }) { Icon(if (allStar) Icons.Filled.StarBorder else Icons.Filled.Star, "نجمة", tint = IOS.blue) }
                        IosIconButton(enabled = fwd.isNotEmpty(), onClick = { forwardMsgs = fwd; selected.clear() }) {
                            Icon(Icons.Filled.Share, "إعادة توجيه", tint = IOS.blue)
                        }
                        IosIconButton(enabled = texts.isNotEmpty(), onClick = {
                            clipboard.setText(AnnotatedString(texts.joinToString("\n") { it.text }))
                            selected.clear()
                        }) { Icon(Icons.Filled.ContentCopy, "نسخ", tint = IOS.blue) }
                    }
                }
            }
            isBlocked -> {
                Box(Modifier.fillMaxWidth().background(IOS.bg).padding(8.dp), contentAlignment = Alignment.Center) {
                    IosTextButton(onClick = { vm.unblock(peer) }) { Text("هذا المستخدم محظور — إلغاء الحظر", color = IOS.blue) }
                }
            }
            recording -> {
                val pulse = rememberInfiniteTransition(label = "rec")
                val dotA by pulse.animateFloat(
                    0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot"
                )
                Row(
                    Modifier.fillMaxWidth().background(IOS.bg).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(19.dp))
                            .border(0.5.dp, IOS.separator, RoundedCornerShape(19.dp)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).alpha(dotA).background(IOS.red, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("%d:%02d".format(recSecs / 60, recSecs % 60), color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        IosTextButton(onClick = { recorder.cancel(); recording = false }) {
                            Text("إلغاء", color = IOS.red, fontSize = 17.sp)
                        }
                    }
                    IosFilledCircleButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val r = recorder.stop(); recording = false
                            if (r == null) vm.toast = "التسجيل قصير جداً" else vm.sendAudio(peer, r.first, r.second)
                        },
                        container = IOS.blue, size = 34.dp,
                    ) { Icon(Icons.Filled.ArrowUpward, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            else -> {
                val hasContent = text.isNotBlank() || pendingImg != null || pendingDoc != null
                Row(
                    Modifier.fillMaxWidth().background(IOS.bg).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // زر «+» رمادي دائري (مثل iMessage)
                    if (editing == null) {
                        IosFilledCircleButton(
                            onClick = { if (!locBusy) attachSheet = true },
                            container = IOS.fill, size = 38.dp,
                        ) {
                            if (locBusy) IosSpinner(Modifier.size(20.dp), color = IOS.secondary)
                            else Icon(Icons.Filled.Add, "إرفاق", tint = WA.icon, modifier = Modifier.size(26.dp))
                        }
                    }
                    // حقل الكتابة: كبسولة بحدّ رفيع، الميكروفون/زر الإرسال داخلها
                    Row(
                        Modifier.weight(1f).heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .border(0.5.dp, IOS.separator, RoundedCornerShape(19.dp))
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it; if (editing == null) vm.typing(peer, it.isNotEmpty()) },
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp).focusRequester(inputFocus),
                            textStyle = TextStyle(color = IOS.label, fontSize = AppSettings.msgFontSp.sp),
                            cursorBrush = SolidColor(IOS.blue),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = if (AppSettings.enterSends) androidx.compose.ui.text.input.ImeAction.Send
                                    else androidx.compose.ui.text.input.ImeAction.Default
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = { if (text.isNotBlank()) doSend() }
                            ),
                            decorationBox = { inner ->
                                if (text.isEmpty()) Text("مراسلة", color = IOS.tertiary, fontSize = AppSettings.msgFontSp.sp)
                                inner()
                            }
                        )
                        if (hasContent) {
                            // زر الإرسال: سهم أبيض داخل دائرة زرقاء، يظهر بنابض
                            val sendScale = remember { Animatable(0.4f) }
                            LaunchedEffect(Unit) { sendScale.animateTo(1f, spring(0.55f, 600f)) }
                            Box(
                                Modifier.padding(bottom = 4.dp).size(30.dp)
                                    .graphicsLayer { scaleX = sendScale.value; scaleY = sendScale.value }
                                    .clip(CircleShape).background(IOS.blue).clickable { doSend() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.ArrowUpward, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp)) }
                        } else {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).clickable { startRec() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Mic, "تسجيل صوتي", tint = WA.icon, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    } // Box
    // ---------- القائمة السياقية بأسلوب iOS: فقاعة مرفوعة + تفاعلات + أوامر ----------
    ctxMsg?.let { cm ->
        val mineReact = vm.reactions.firstOrNull { it.messageId == cm.id && it.userId == me }?.emoji
        val live = !cm.isDeleted
        val canEdit = cm.sender == me && live && cm.imagePath == null && cm.audioPath == null && cm.filePath == null
        val starred = vm.isStarred(cm.id)
        val entries = listOf(
            if (live && !isBlocked) IosMenuEntry("رد", Icons.AutoMirrored.Filled.Reply) {
                replyTo = cm; editing = null; ctxMsg = null; runCatching { inputFocus.requestFocus() }
            } else null,
            if (live && cm.viewOnce != true) IosMenuEntry("إعادة توجيه", Icons.Filled.Share) {
                forwardMsgs = listOf(cm); ctxMsg = null
            } else null,
            if (live && AppSettings.waShareMenu && cm.text.isNotBlank() && cm.viewOnce != true && com.klencod.dardasha.data.WaApps.installed(ctx).isNotEmpty())
                IosMenuEntry("مشاركة عبر واتساب", Icons.Filled.Share) {
                    com.klencod.dardasha.data.WaShare.sendText(ctx, cm.text); ctxMsg = null
                } else null,
            if (live && cm.text.isNotBlank() && cm.audioPath == null) IosMenuEntry("نسخ", Icons.Filled.ContentCopy) {
                clipboard.setText(AnnotatedString(cm.text)); ctxMsg = null
            } else null,
            if (canEdit) IosMenuEntry("تعديل", Icons.Filled.Edit) {
                editing = cm; replyTo = null; text = cm.text; ctxMsg = null
            } else null,
            if (live) IosMenuEntry(
                if (starred) "إلغاء التمييز" else "تمييز بنجمة",
                if (starred) Icons.Filled.StarBorder else Icons.Filled.Star
            ) { vm.toggleStar(cm); ctxMsg = null } else null,
            if (live) IosMenuEntry("معلومات", Icons.Filled.Info) { infoMsg = cm; ctxMsg = null } else null,
            IosMenuEntry("تحديد", Icons.Filled.CheckCircle) { selected.add(cm.id); ctxMsg = null },
            IosMenuEntry("حذف", Icons.Filled.Delete, danger = true) {
                ctxMsg = null; selected.clear(); selected.add(cm.id); ctxDelete = true; confirmDelete = true
            },
        )
        MessageContextOverlay(
            vm = vm, msg = cm, mine = cm.sender == me, tail = ctxTail,
            top = selTop, bottom = selBottom, mineReact = mineReact, entries = entries,
            onReact = { e -> vm.react(cm, e); ctxMsg = null },
            onMore = { emojiFor = "react" },
            onDismiss = { ctxMsg = null },
        )
    }

    emojiFor?.let { mode ->
        EmojiSheet(onDismiss = { emojiFor = null; if (mode == "react") ctxMsg = null }) { e ->
            if (mode == "react") {
                ctxMsg?.let { vm.react(it, e) }
                ctxMsg = null; emojiFor = null
            } else text += e
        }
    }

    if (confirmDelete) {
        // نثبّت اللقطة لتبقى الخيارات كما هي أثناء حركة الإغلاق (بعد مسح التحديد)
        val snap = remember { selMsgs.toList() }
        val allMine = snap.isNotEmpty() && snap.all { it.sender == me && !it.isDeleted }
        IosActionSheet(
            onDismiss = { confirmDelete = false; if (ctxDelete) { selected.clear(); ctxDelete = false } },
            title = if (snap.size > 1) "حذف ${snap.size} رسائل؟" else "حذف الرسالة؟",
            message = "«حذف عندي» يخفيها من جهازك فقط." + if (allMine) " «حذف للجميع» يحذفها عند الطرفين." else "",
            actions = listOfNotNull(
                if (allMine) IosAction("حذف للجميع", destructive = true) {
                    snap.forEach { vm.deleteForAll(it) }; selected.clear()
                } else null,
                IosAction("حذف عندي", destructive = true) {
                    snap.forEach { vm.hideLocal(it) }; selected.clear()
                },
            ),
        )
    }

    // ---------- attach sheet ----------
    if (attachSheet) {
        IosActionSheet(
            onDismiss = { attachSheet = false },
            actions = listOf(
                IosAction("الكاميرا") { openCamera() },
                IosAction("صورة") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                IosAction("مستند") { docPick.launch(arrayOf("*/*")) },
                IosAction("الموقع") { confirmLoc = true },
            ),
        )
    }
    if (confirmLoc) {
        IosAlert(
            title = "مشاركة الموقع",
            message = "سيتم إرسال موقعك الحالي إلى ${vm.name(peer)}.",
            confirmText = "إرسال", danger = false,
            onConfirm = { confirmLoc = false; startLocation() },
            onDismiss = { confirmLoc = false },
        )
    }

    if (forwardMsgs.isNotEmpty()) ForwardDialog(vm, forwardMsgs) { forwardMsgs = emptyList() }
    if (timerDialog) TimerDialog(vm.timerOf(peer), { vm.setTimer(peer, it) }, { timerDialog = false })

    // ---------- صورة لمرة واحدة ----------
    onceMsg?.let { om ->
        val path = om.imagePath
        LaunchedEffect(om.id) { vm.markViewed(om) } // تُستهلك لحظة الفتح
        if (path != null) {
            Dialog(
                onDismissRequest = { Media.evict(path); onceMsg = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable { Media.evict(path); onceMsg = null }) {
                    StorageImage("chat-images", path, Modifier.fillMaxSize(), ContentScale.Fit, persist = false, force = true)
                    Text(
                        "① تُعرض مرة واحدة فقط", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                    )
                    IosIconButton(
                        onClick = { Media.evict(path); onceMsg = null },
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    ) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
                }
            }
        }
    }

    // ---------- معلومات الرسالة ----------
    infoMsg?.let { im ->
        val lines = buildList {
            add("الإرسال: " + fmtSeen(im.createdAt))
            if (im.sender == me) add("القراءة: " + (if (im.readAt != null) fmtSeen(im.readAt) else "لم تُقرأ بعد"))
            if (im.viewOnce == true) add("فتح الصورة: " + (if (im.viewedAt != null) fmtSeen(im.viewedAt) else "لم تُفتح بعد"))
            if (im.expiresAt != null) add("تختفي: " + fmtSeen(im.expiresAt))
            if (im.isEdited) add("هذه الرسالة معدّلة")
        }
        IosAlert(
            title = "معلومات الرسالة", message = lines.joinToString("\n"),
            confirmText = "حسناً", danger = false, cancelText = null,
            onConfirm = { infoMsg = null }, onDismiss = { infoMsg = null },
        )
    }

    // ---------- full-screen image ----------
    viewer?.let { path ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }) {
                StorageImage("chat-images", path, Modifier.fillMaxSize(), ContentScale.Fit, force = true)
                IosIconButton(onClick = { viewer = null }, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Filled.Close, "إغلاق", tint = Color.White)
                }
            }
        }
    }
}

private val LocalMine = compositionLocalOf { false }
@Composable private fun bFg(): Color = if (LocalMine.current) Color.White else IOS.label
@Composable private fun bMute(): Color = if (LocalMine.current) Color(0xB3FFFFFF) else IOS.secondary
@Composable private fun bAcc(): Color = if (LocalMine.current) Color.White else IOS.blue
@Composable private fun bOnAcc(): Color = if (LocalMine.current) IOS.blue else Color.White

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    vm: MainVM, m: Message, mine: Boolean, tail: Boolean, selected: Boolean,
    isLastMine: Boolean, selecting: Boolean, ctx: Boolean,
    canSwipeReply: Boolean, onSwipeReply: () -> Unit,
    highlight: Boolean, onQuoteClick: (String) -> Unit,
    onBounds: (Float, Float) -> Unit,
    onLong: () -> Unit, onTap: () -> Unit,
    onImage: (String) -> Unit, onOnce: (Message) -> Unit
) {
    val quoted = m.replyTo?.let { rid -> vm.msgs.firstOrNull { it.id == rid } }
    val rx = vm.reactions.filter { it.messageId == m.id }.groupingBy { it.emoji }.eachCount()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    /** true = ذيل الفقاعة على الحافة اليمنى فعلياً. */
    val right = if (mine) rtl else !rtl
    // iMessage: زوايا 18dp، والزاوية السفلية الخارجية صغيرة عند وجود الذيل
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine && tail) 4.dp else 18.dp, bottomEnd = if (!mine && tail) 4.dp else 18.dp
    )
    val bubbleColor = if (mine) WA.sent else WA.received
    val geo = parseGeo(m.text)
    val showText = !m.isDeleted && geo == null && m.text.isNotBlank() &&
        m.text != "📷 صورة" && m.text != "🎤 رسالة صوتية" && m.viewOnce != true &&
        !(m.filePath != null && m.text == "📎 " + (m.fileName ?: ""))
    // صورة وحدها: تملأ الفقاعة بلا حشوة (مثل iMessage)
    val mediaOnly = !m.isDeleted && m.imagePath != null && m.viewOnce != true &&
        quoted == null && m.forwarded != true && !showText && m.audioPath == null && m.filePath == null
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.76f).dp.coerceAtLeast(280.dp)
    var showTime by remember { mutableStateOf(false) }

    val rowBg by animateColorAsState(
        if (highlight) Color(0x55FFC857) else Color.Transparent, label = "bubbleHl"
    )

    // ---------- ظهور الرسالة الجديدة: تكبير بنابض من زاوية الذيل ----------
    val fresh = remember { (parseIso(m.createdAt) ?: 0L) > System.currentTimeMillis() - 3000L }
    val pop = remember { Animatable(if (fresh) 0.6f else 1f) }
    LaunchedEffect(Unit) { if (fresh) pop.animateTo(1f, spring(0.62f, 420f)) }

    // ---------- السحب للرد (swipe to reply) ----------
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipeScope = rememberCoroutineScope()
    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxPx = with(density) { 96.dp.toPx() }
    val swipeX = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val curReply by rememberUpdatedState(onSwipeReply)
    // الأيقونة تظهر على الجهة اليسرى فعلياً (نسحب لليمين) — في RTL هي End
    val iconAlign = if (rtl) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        Modifier.fillMaxWidth().then(
            if (!canSwipeReply) Modifier else Modifier.pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onDragStart = { armed = false },
                    onDragEnd = {
                        if (swipeX.value >= thresholdPx) curReply()
                        armed = false
                        swipeScope.launch { swipeX.animateTo(0f, spring(0.75f, 500f)) }
                    },
                    onDragCancel = {
                        armed = false
                        swipeScope.launch { swipeX.animateTo(0f, spring(0.75f, 500f)) }
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        // مقاومة خفيفة + السحب لليمين فقط
                        val next = (swipeX.value + dx * 0.7f).coerceIn(0f, maxPx)
                        swipeScope.launch { swipeX.snapTo(next) }
                        if (!armed && next >= thresholdPx) {
                            armed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (armed && next < thresholdPx) armed = false
                    }
                )
            }
        )
    ) {
        if (swipeX.value > 4f) {
            val p = (swipeX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                Modifier.align(iconAlign).padding(horizontal = 16.dp).size(32.dp)
                    .graphicsLayer { alpha = p; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p }
                    .background(if (armed) IOS.blue else IOS.fill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply, null,
                    tint = if (armed) Color.White else IOS.secondary, modifier = Modifier.size(18.dp)
                )
            }
        }
    // نُبلّغ عن موضع الفقاعة المختارة ليظهر شريط التفاعل فوقها مباشرة
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val curCtx by rememberUpdatedState(ctx)
    val curBounds by rememberUpdatedState(onBounds)
    fun report(c: LayoutCoordinates) {
        if (curCtx && c.isAttached) {
            val t = c.positionInRoot().y
            curBounds(t, t + c.size.height)
        }
    }
    LaunchedEffect(ctx) { coords?.let { report(it) } }
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { coords = it; report(it) }
            .graphicsLayer { translationX = swipeX.value; alpha = if (ctx) 0f else 1f }
            .background(rowBg)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = { if (selecting) onTap() else showTime = !showTime },
                onLongClick = {
                    coords?.takeIf { it.isAttached }?.let { c ->
                        val t = c.positionInRoot().y
                        curBounds(t, t + c.size.height)
                    }
                    onLong()
                }
            )
            .padding(horizontal = 14.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // دائرة التحديد على طريقة iOS (تظهر عند وضع التحديد)
        androidx.compose.animation.AnimatedVisibility(
            visible = selecting,
            enter = androidx.compose.animation.expandHorizontally(spring(0.8f, 500f)) + androidx.compose.animation.fadeIn(tween(150)),
            exit = androidx.compose.animation.shrinkHorizontally(tween(180)) + androidx.compose.animation.fadeOut(tween(120)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (selected) IOS.blue else Color.Transparent)
                        .border(1.5.dp, if (selected) IOS.blue else IOS.tertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End
        ) {
        Column(
            Modifier.animateContentSize(spring(0.85f, 500f)),
            horizontalAlignment = if (mine) Alignment.Start else Alignment.End
        ) {
            CompositionLocalProvider(LocalMine provides mine) {
            Box(
                Modifier
                    .padding(top = if (rx.isNotEmpty()) 12.dp else 0.dp)
                    .graphicsLayer {
                        scaleX = pop.value; scaleY = pop.value
                        alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(if (right) 1f else 0f, 1f)
                    }
            ) {
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                modifier = Modifier.widthIn(max = maxW)
                    .bubbleTail(tail, mine, bubbleColor)
                    .clip(bubbleShape)
            ) {
                Column(
                    Modifier.padding(
                        if (mediaOnly) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    )
                ) {
                    if (m.isDeleted) {
                        Text("🚫 تم حذف هذه الرسالة", color = bMute(), fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    } else {
                        if (m.forwarded == true) {
                            Text("↪ معاد توجيهها", color = bMute(), fontSize = 12.sp, fontStyle = FontStyle.Italic)
                        }
                        if (quoted != null) {
                            Row(
                                Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable { onQuoteClick(quoted.id) }
                                    .background(if (mine) Color(0x26000000) else Color(0x14000000))
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(bAcc()))
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text(
                                        if (quoted.sender == vm.meId) "أنت" else vm.name(quoted.sender),
                                        color = bAcc(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(vm.preview(quoted), color = bMute(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (m.imagePath != null) {
                            if (m.viewOnce == true) {
                                val opened = m.viewedAt != null
                                Row(
                                    Modifier.widthIn(min = 200.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (mine) Color(0x26000000) else Color(0x14000000))
                                        .clickable(enabled = !mine && !opened) { onOnce(m) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (opened) "✓" else "①", color = bAcc(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        when {
                                            opened -> if (mine) "فتحها المستلم" else "تم فتح الصورة"
                                            mine -> "صورة لمرة واحدة"
                                            else -> "اضغط لعرض الصورة (مرة واحدة فقط)"
                                        },
                                        color = if (opened) bMute() else bFg(), fontSize = 14.sp
                                    )
                                }
                            } else {
                                StorageImage(
                                    "chat-images", m.imagePath,
                                    Modifier.width(240.dp).heightIn(min = 120.dp, max = 320.dp)
                                        .then(if (mediaOnly) Modifier else Modifier.clip(RoundedCornerShape(10.dp)))
                                        .clickable { onImage(m.imagePath) }
                                )
                            }
                            if (!mediaOnly) Spacer(Modifier.height(4.dp))
                        }
                        if (m.audioPath != null) {
                            AudioBubble(m.audioPath, (m.audioSecs ?: 0.0).toInt())
                        }
                        if (m.filePath != null) FileBubble(m.filePath, m.fileName, m.fileType)
                        if (geo != null) LocationBubble(geo)
                        if (showText) {
                            LinkedText(m.text, color = bFg(), linkColor = if (mine) Color.White else IOS.blue)
                        }
                    }
                }
            }
            // تفاعل (Tapback): شارة صغيرة فوق الزاوية المقابلة للذيل
            if (rx.isNotEmpty()) {
                Row(
                    Modifier.align(if (right) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight)
                        .absoluteOffset(x = if (right) (-10).dp else 10.dp, y = (-12).dp)
                        .background(IOS.menuBg, RoundedCornerShape(14.dp))
                        .border(2.dp, IOS.bg, RoundedCornerShape(14.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    rx.forEach { (e, n) -> Text(if (n > 1) "$e$n " else "$e ", fontSize = 13.sp) }
                }
            }
            }
            // سطر الحالة تحت الفقاعة: نجمة · تعديل · الوقت (عند الضغط) · تم التسليم/تمت القراءة (آخر رسالة مُرسَلة)
            val status: String? = if (isLastMine && mine && !m.isDeleted) when {
                m.readAt != null -> "تمت القراءة " + fmtTime(m.readAt)
                vm.isOnline(m.receiver) -> "تم التسليم"
                else -> "تم الإرسال"
            } else null
            val starred = vm.isStarred(m.id) && !m.isDeleted
            val edited = m.isEdited && !m.isDeleted
            val delSaved = m.delSaved == true
            if (starred || edited || delSaved || showTime || status != null) {
                Row(
                    Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (starred) {
                        Icon(Icons.Filled.Star, "مميّزة", tint = C.star, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (edited) Text("تم التعديل  ", color = IOS.secondary, fontSize = 11.sp)
                    if (delSaved) Text("🗑 حذفها المرسِل — محفوظة عندك  ", color = IOS.red, fontSize = 11.sp)
                    if (showTime) Text(fmtTime(m.createdAt) + if (status != null) "  ·  " else "", color = IOS.secondary, fontSize = 11.sp)
                    if (status != null) Text(status, color = IOS.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }
        }
    }
    } // swipe Box
}

@Composable
private fun AudioBubble(path: String, secs: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var pos by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    fun applySpeed(p: MediaPlayer) { runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) } }

    DisposableEffect(path) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(playing) {
        while (playing) { pos = (player?.currentPosition ?: 0) / 1000; delay(300) }
    }

    Row(Modifier.width(244.dp), verticalAlignment = Alignment.CenterVertically) {
        IosIconButton(
            onClick = {
                val p = player
                if (p != null) {
                    if (playing) { p.pause(); playing = false } else { p.start(); applySpeed(p); playing = true }
                } else if (!loading) scope.launch {
                    loading = true
                    val f = Media.audioFile(ctx, path)
                    loading = false
                    if (f == null) return@launch
                    runCatching {
                        val np = MediaPlayer()
                        np.setDataSource(f.absolutePath)
                        np.prepare()
                        np.setOnCompletionListener { playing = false; pos = 0; it.seekTo(0) }
                        np.start(); applySpeed(np)
                        player = np; playing = true
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            if (loading) IosSpinner(Modifier.size(20.dp), color = bAcc())
            else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = bFg())
        }
        IosProgress(
            progress = if (secs > 0) (pos.toFloat() / secs).coerceIn(0f, 1f) else 0f,
            modifier = Modifier.weight(1f), color = bAcc(), track = if (LocalMine.current) Color(0x59FFFFFF) else Color(0x33767680)
        )
        Spacer(Modifier.width(8.dp))
        val shown = if (playing) pos else secs
        Text("%d:%02d".format(shown / 60, shown % 60), color = bMute(), fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            when (speed) { 1f -> "1x"; 1.5f -> "1.5x"; else -> "2x" },
            color = bAcc(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x33000000))
                .clickable {
                    speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    player?.let { if (playing) applySpeed(it) }
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun FileBubble(path: String, name: String?, type: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val ext = (name ?: "").substringAfterLast('.', "").uppercase().take(4)
    Row(
        Modifier.widthIn(min = 200.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x33000000))
            .clickable(enabled = !busy) {
                scope.launch {
                    busy = true
                    val ok = Media.openFile(ctx, path, name, type)
                    busy = false
                    if (!ok) android.widget.Toast.makeText(ctx, "تعذّر فتح الملف", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(bAcc(), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (busy) IosSpinner(Modifier.size(20.dp), color = bOnAcc())
            else Icon(Icons.Filled.Description, null, tint = bOnAcc())
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name ?: "ملف", color = bFg(), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ext.isNotEmpty()) Text(ext, color = bMute(), fontSize = 12.sp)
        }
    }
}

@Composable
private fun LocationBubble(g: GeoPoint) {
    val ctx = LocalContext.current
    Row(
        Modifier.widthIn(min = 200.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x33000000))
            .clickable {
                if (!Media.openMap(ctx, g))
                    android.widget.Toast.makeText(ctx, "لا يوجد تطبيق خرائط", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(IOS.red, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.LocationOn, null, tint = Color.White) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("📍 موقع", color = bFg(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("%.5f, %.5f".format(java.util.Locale.US, g.lat, g.lng), color = bMute(), fontSize = 12.sp)
            Text("اضغط للفتح في الخرائط", color = bAcc(), fontSize = 12.sp)
        }
    }
}

/**
 * القائمة السياقية بأسلوب iOS: خلفية معتمة (والمحتوى خلفها مموَّه)، شريط تفاعلات فوق الفقاعة،
 * نسخة الفقاعة «مرفوعة» في مكانها، وقائمة أوامر تحتها. كلها تتمدّد بنابض من جهة الفقاعة.
 */
@Composable
private fun MessageContextOverlay(
    vm: MainVM, msg: Message, mine: Boolean, tail: Boolean,
    top: Float, bottom: Float, mineReact: String?,
    entries: List<IosMenuEntry?>,
    onReact: (String) -> Unit, onMore: () -> Unit, onDismiss: () -> Unit,
) {
    val dens = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val right = if (mine) rtl else !rtl
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var areaH by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.78f, 420f)) }
    val rows = entries.filterNotNull()
    val showBar = !msg.isDeleted
    val barPx = with(dens) { 54.dp.toPx() }
    val gapPx = with(dens) { 10.dp.toPx() }
    val rowPx = with(dens) { 46.dp.toPx() }
    val marginPx = with(dens) { 10.dp.toPx() }
    val headPx = if (showBar) barPx + gapPx else 0f
    val totalPx = headPx + (bottom - top) + gapPx + rowPx * rows.size
    val minLocal = maxOf(WindowInsets.safeDrawing.getTop(dens) - origin.y, 0f) + marginPx
    val maxLocal = areaH - WindowInsets.safeDrawing.getBottom(dens) - totalPx - marginPx
    val y = (top - origin.y - headPx).coerceIn(minLocal, maxOf(minLocal, maxLocal))
    val s = 0.7f + 0.3f * appear.value
    val ox = if (right) 1f else 0f

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .onSizeChanged { areaH = it.height }
    ) {
        // الخلفية: تعتيم يضغط عليه المستخدم للإغلاق (التمويه يُطبَّق على المحتوى خلفها)
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(if (C.dark) Color(0x66000000) else Color(0x33000000)).tap(onDismiss)
        )
        Column(
            Modifier.offset { IntOffset(0, y.toInt()) }.fillMaxWidth(),
            horizontalAlignment = if (mine) Alignment.Start else Alignment.End
        ) {
            if (showBar) {
                Row(
                    Modifier.padding(horizontal = 14.dp).height(54.dp)
                        .graphicsLayer {
                            scaleX = s; scaleY = s; alpha = appear.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(ox, 1f)
                        }
                        .shadow(14.dp, RoundedCornerShape(30.dp))
                        .background(IOS.menuBg, RoundedCornerShape(30.dp))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (mineReact == e) IOS.blue.copy(alpha = 0.25f) else Color.Transparent)
                                .tap { onReact(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 26.sp) }
                    }
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(IOS.fill).tap(onMore),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, "المزيد", tint = IOS.label, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.height(10.dp))
            }
            // نسخة الفقاعة في مكانها الأصلي (الأصلية مخفية خلف الطبقة)
            Bubble(
                vm, msg, mine, tail = tail, selected = false, isLastMine = false, selecting = false, ctx = false,
                canSwipeReply = false, onSwipeReply = {}, highlight = false, onQuoteClick = {},
                onBounds = { _, _ -> }, onLong = {}, onTap = {}, onImage = {}, onOnce = {}
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.padding(horizontal = 14.dp).width(250.dp)
                    .graphicsLayer {
                        scaleX = s; scaleY = s; alpha = appear.value.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(ox, 0f)
                    }
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(IOS.menuBg)
            ) {
                rows.forEachIndexed { i, e ->
                    val col = if (e.danger) IOS.red else IOS.label
                    Row(
                        Modifier.fillMaxWidth().height(46.dp).pressRow(onClick = e.onClick).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(e.label, color = col, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(e.icon, null, tint = col, modifier = Modifier.size(20.dp))
                    }
                    if (i < rows.lastIndex) HorizontalDivider(color = IOS.separator, thickness = 0.5.dp)
                }
            }
        }
    }
}
