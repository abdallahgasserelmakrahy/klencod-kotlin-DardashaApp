package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.SettingsSection
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.data.WaApps
import com.klencod.dardasha.data.WaConv
import com.klencod.dardasha.data.WaInbox
import com.klencod.dardasha.data.WaKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * شاشة واتساب: كل ما يصل من واتساب (رسائل، صور، رسائل صوتية…) مجمّعاً في قائمة محادثات.
 * تُفتح من أيقونة/اختصار «واتساب» ومن صف واتساب في القائمة الرئيسية، وليست صفحة إعدادات.
 */
@Composable
fun WhatsAppScreen(vm: MainVM) {
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
    val ready = AppSettings.waInbox && access

    val back = { vm.route = Route.Home }
    val openSettings = { vm.route = Route.SettingsPage(SettingsSection.WhatsApp) }
    val list = WaInbox.convs.toList()
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }
    var deleteKey by remember { mutableStateOf<String?>(null) }

    IosPage(onBack = back) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(
                "واتساب", onBack = back, separatorVisible = collapsed, backLabel = "رجوع",
                trailing = {
                    Icon(
                        Icons.Filled.Settings, "إعدادات واتساب", tint = IOS.blue,
                        modifier = Modifier.size(24.dp).tap { openSettings() }
                    )
                }
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = LocalTabInset.current)) {
                item(key = "title") {
                    Text(
                        "واتساب", color = IOS.label, fontSize = com.klencod.dardasha.data.AppSettings.largeTitleSp.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                    )
                }
                item(key = "link") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)).background(CellBg)
                            .tap { vm.route = Route.WaLink }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ربط أرقام واتساب (جهاز مصاحب)", color = IOS.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${com.klencod.dardasha.data.WaLink.accounts.count { !it.pending }}", color = IOS.secondary, fontSize = 16.sp)
                    }
                }
                if (!ready && !com.klencod.dardasha.data.WaLink.isLinked(ctx)) item(key = "setup") {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)).background(CellBg).padding(16.dp)
                    ) {
                        Text("فعّل صندوق واتساب", color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (!AppSettings.waInbox) "لتظهر هنا الرسائل والصور والرسائل الصوتية القادمة من واتساب، فعّل «حفظ رسائل واتساب الواردة» وامنح التطبيق الوصول إلى الإشعارات."
                            else "الميزة مفعّلة لكن إذن الوصول إلى الإشعارات غير ممنوح. فعّل التطبيق في قائمة الوصول إلى الإشعارات.",
                            color = IOS.secondary, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(IosGreen)
                                .tap { openSettings() }.padding(horizontal = 18.dp, vertical = 9.dp)
                        ) { Text("فتح الإعدادات", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (list.isEmpty()) item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(IosGreen), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.QuestionAnswer, null, tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("لا توجد رسائل بعد", color = IOS.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "ستظهر هنا رسائل واتساب والصور والرسائل الصوتية فور وصولها إلى هذا الجهاز.",
                            color = IOS.secondary, fontSize = 15.sp, textAlign = TextAlign.Center
                        )
                    }
                }
                items(list, key = { it.key }) { c ->
                    WaRow(c, onClick = { vm.route = Route.WaChat(c.key) }, onLongClick = { deleteKey = c.key })
                }
                item(key = "end") { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    deleteKey?.let { k ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteKey = null },
            title = { Text("حذف المحادثة؟") },
            text = { Text("ستُحذف رسائل هذه المحادثة المحفوظة على هذا الجهاز فقط. لن يتأثر واتساب نفسه.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { WaInbox.delete(ctx, k); deleteKey = null }) { Text("حذف", color = IOS.red) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteKey = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun WaRow(c: WaConv, onClick: () -> Unit, onLongClick: () -> Unit) {
    val last = c.msgs.lastOrNull()
    var pressed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(if (pressed) IOS.pressed else IOS.bg)
            .pointerInput(c.key) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.title, null, size = 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(waTime(c.lastTime), color = IOS.secondary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val kindIcon = when (last?.kind) {
                        WaKind.IMAGE -> Icons.Filled.Image
                        WaKind.AUDIO -> Icons.Filled.Mic
                        WaKind.VIDEO -> Icons.Filled.Videocam
                        WaKind.FILE -> Icons.Filled.Description
                        else -> null
                    }
                    if (kindIcon != null) {
                        Icon(kindIcon, null, tint = IOS.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    val preview = last?.let {
                        val t = it.text
                        if (it.fromMe) "أنت: $t" else if (it.sender.isNotBlank() && it.sender != c.title) "${it.sender}: $t" else t
                    }.orEmpty()
                    Text(
                        preview, color = IOS.secondary, fontSize = 15.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(WaApps.label(c.pkg), color = IOS.tertiary, fontSize = 11.sp)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 78.dp), color = IOS.separator, thickness = 0.5.dp)
    }
}

private fun waTime(ts: Long): String {
    if (ts <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.let {
        it.get(Calendar.YEAR) == then.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }
    return when {
        sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        yesterday -> "أمس"
        else -> SimpleDateFormat("d/M/yyyy", Locale.getDefault()).format(Date(ts))
    }
}
