package com.klencod.dardasha.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import com.klencod.dardasha.data.WaKind
import com.klencod.dardasha.data.WaMsg
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.WaApps
import com.klencod.dardasha.data.WaInbox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** محادثة من صندوق واتساب المحلي: عرض الرسائل الملتقطة من الإشعارات + رد سريع. */
@Composable
fun WhatsAppChatScreen(vm: MainVM, key: String) {
    val ctx = LocalContext.current
    val back = { vm.route = Route.WhatsApp }
    val conv = WaInbox.find(key)
    var text by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var viewer by remember { mutableStateOf<String?>(null) }

    IosPage(onBack = back) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(
                conv?.title ?: "واتساب", onBack = back, separatorVisible = true, backLabel = "واتساب",
                trailing = { Text(conv?.let { WaApps.label(it.pkg) } ?: "", color = IOS.secondary, fontSize = 12.sp) }
            )
            if (conv == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("المحادثة غير موجودة", color = IOS.secondary, fontSize = 16.sp)
                }
            } else {
                val rev = conv.msgs.reversed()
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(rev) { m ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
                            Column(
                                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (m.fromMe) IOS.blue else IOS.bubbleIn)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                if (!m.fromMe && m.sender.isNotBlank() && m.sender != conv.title)
                                    Text(m.sender, color = IosGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                WaBody(m, onImage = { viewer = it })
                                Text(
                                    fmt.format(Date(m.time)), fontSize = 10.sp,
                                    color = if (m.fromMe) Color(0xB3FFFFFF) else IOS.secondary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
                if (conv.canReply) {
                    Row(
                        Modifier.fillMaxWidth().background(IOS.bar).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(IOS.fill)
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            if (text.isEmpty()) Text("رد سريع", color = IOS.secondary, fontSize = 16.sp)
                            BasicTextField(
                                value = text, onValueChange = { text = it }, maxLines = 4,
                                textStyle = TextStyle(color = IOS.label, fontSize = 16.sp),
                                cursorBrush = SolidColor(IOS.blue), modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (text.isBlank()) IOS.fill else IOS.blue)
                                .tap {
                                    if (text.isNotBlank()) {
                                        if (WaInbox.reply(ctx, conv, text)) text = ""
                                        else Toast.makeText(ctx, "تعذّر الرد: انتظر رسالة جديدة من هذه المحادثة", Toast.LENGTH_LONG).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                } else {
                    Text(
                        "الرد السريع يتوفر بعد وصول رسالة جديدة من هذه المحادثة (يحتاج إشعاراً نشطاً من واتساب).",
                        color = IOS.secondary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(IOS.bar).padding(14.dp)
                    )
                }
            }
        }
    }

    viewer?.let { path ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }, contentAlignment = Alignment.Center) {
                AsyncImage(File(path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** محتوى الرسالة: نص، أو صورة/صوت/فيديو/ملف مع أفضل عرض متاح. */
@Composable
private fun WaBody(m: WaMsg, onImage: (String) -> Unit) {
    val fg = if (m.fromMe) Color.White else IOS.label
    val sub = if (m.fromMe) Color(0xB3FFFFFF) else IOS.secondary
    val path = m.path
    when (m.kind) {
        WaKind.IMAGE -> {
            if (path != null && File(path).exists()) {
                AsyncImage(
                    File(path), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { onImage(path) }
                )
                val cap = WaKind.caption(m.text)
                if (cap.isNotBlank()) Text(cap, color = fg, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(m.text, color = fg, fontSize = 16.sp)
                Text("الصورة غير متاحة (لم يرسلها واتساب مع الإشعار)", color = sub, fontSize = 11.sp)
            }
        }
        WaKind.AUDIO -> WaAudio(m, fg, sub)
        WaKind.VIDEO, WaKind.FILE -> {
            Text(m.text, color = fg, fontSize = 16.sp)
            Text("افتح واتساب لعرض المرفق", color = sub, fontSize = 11.sp)
        }
        else -> Text(m.text, color = fg, fontSize = 16.sp)
    }
}

/** رسالة صوتية: تُشغَّل من هنا إن التقط التطبيق الملف الصوتي، وإلا يظهر نصها فقط (مثل «🎤 0:12»). */
@Composable
private fun WaAudio(m: WaMsg, fg: Color, sub: Color) {
    val path = m.path?.takeIf { File(it).exists() }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(path) { onDispose { runCatching { player?.release() }; player = null; playing = false } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (m.fromMe) Color(0x33FFFFFF) else IosGreen)
                .then(
                    if (path == null) Modifier else Modifier.tap {
                        runCatching {
                            if (playing) {
                                player?.pause(); playing = false
                            } else {
                                val p = player ?: MediaPlayer().apply {
                                    setDataSource(path); prepare()
                                    setOnCompletionListener { playing = false; runCatching { seekTo(0) } }
                                }.also { player = it }
                                p.start(); playing = true
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                tint = if (path == null) sub else Color.White, modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(WaKind.caption(m.text).ifBlank { m.text }.let { "🎤 $it" }, color = fg, fontSize = 15.sp)
            if (path == null) Text("الصوت غير متاح للتشغيل هنا", color = sub, fontSize = 11.sp)
        }
    }
}
