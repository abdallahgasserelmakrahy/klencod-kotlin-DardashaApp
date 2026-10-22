package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.data.Message
import com.klencod.dardasha.timerLabel

private val URL_RE = Regex("""(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)

// تنسيق خفيف مثل واتساب: *غامق*  _مائل_  ~مشطوب~  (العلامة عند بداية كلمة وتُغلق عند نهايتها)
private val FMT_RE = Regex("""(?<![\p{L}\p{N}])([*_~])(?=\S)(.+?)(?<=\S)\1(?![\p{L}\p{N}])""")

private fun AnnotatedString.Builder.appendFmt(s: String) {
    var last = 0
    for (m in FMT_RE.findAll(s)) {
        append(s.substring(last, m.range.first))
        val style = when (m.groupValues[1]) {
            "*" -> SpanStyle(fontWeight = FontWeight.Bold)
            "_" -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }
        withStyle(style) { appendFmt(m.groupValues[2]) }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

/** معاينة صورة شخصية بملء الشاشة (اضغط في أي مكان للإغلاق). */
@Composable
fun AvatarPreviewDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(
                model = com.klencod.dardasha.data.Repo.avatarUrl(path), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** نص الرسالة مع روابط قابلة للضغط (تُفتح في المتصفح). */
@Composable
fun LinkedText(
    text: String, modifier: Modifier = Modifier, fontSize: Int = com.klencod.dardasha.data.AppSettings.msgFontSp,
    color: androidx.compose.ui.graphics.Color = C.fg, linkColor: androidx.compose.ui.graphics.Color = C.tick
) {
    val annotated: AnnotatedString = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in URL_RE.findAll(text)) {
                // نحذف علامات الترقيم الملتصقة بنهاية الرابط
                var raw = m.value
                while (raw.isNotEmpty() && raw.last() in ".,;:!?)،؛") raw = raw.dropLast(1)
                if (raw.isEmpty()) continue
                val start = m.range.first
                appendFmt(text.substring(last, start))
                val url = if (raw.startsWith("www.", true)) "https://$raw" else raw
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) { append(raw) }
                last = start + raw.length
            }
            appendFmt(text.substring(last))
        }
    }
    Text(annotated, color = color, fontSize = fontSize.sp, modifier = modifier)
}

/** اختيار محادثات لإعادة توجيه رسالة إليها (ورقة iOS بقائمة محدِّدات دائرية). */
@Composable
fun ForwardDialog(vm: MainVM, msgs: List<Message>, onDismiss: () -> Unit) {
    val me = vm.meId
    val targets = remember {
        vm.conversations().map { it.first }.filter { it != me && it !in vm.blocked }
    }
    val chosen: SnapshotStateList<String> = remember { mutableStateListOf() }
    IosSheet(
        onDismiss = onDismiss, title = "إعادة توجيه",
        leading = { close -> IosNavText("إلغاء") { close() } },
        trailing = { close ->
            IosNavText(if (chosen.isEmpty()) "إرسال" else "إرسال (${chosen.size})", enabled = chosen.isNotEmpty(), bold = true) {
                msgs.forEach { vm.forward(it, chosen.toList()) }; close()
            }
        },
    ) { _ ->
        if (targets.isEmpty()) {
            Text(
                "لا توجد محادثات أخرى. ابدأ محادثة جديدة أولاً.", color = IOS.secondary, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                itemsIndexed(targets, key = { _, id -> id }) { i, id ->
                    val on = id in chosen
                    Box(Modifier.fillMaxWidth().clip(rowShape(i, targets.size)).background(CellBg)) {
                        Row(
                            Modifier.fillMaxWidth().pressRow { if (on) chosen.remove(id) else chosen.add(id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(vm.name(id), vm.peers[id]?.avatarPath, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(vm.name(id), color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            SelectCircle(on)
                        }
                        if (i < targets.lastIndex) HorizontalDivider(
                            Modifier.align(Alignment.BottomStart).padding(start = 66.dp), color = IOS.separator, thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

private val TIMER_OPTIONS = listOf(0, 24 * 3600, 7 * 24 * 3600, 90 * 24 * 3600)

/** اختيار مدة اختفاء الرسائل الجديدة في المحادثة (Action Sheet). */
@Composable
fun TimerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = if (current !in TIMER_OPTIONS) TIMER_OPTIONS + current else TIMER_OPTIONS
    IosActionSheet(
        onDismiss = onDismiss,
        title = "الرسائل المؤقتة",
        message = "الرسائل الجديدة في هذه المحادثة ستختفي لدى الطرفين بعد المدة المحددة. لا يؤثر على الرسائل السابقة.",
        actions = options.map { s ->
            IosAction(if (s == 0) "إيقاف" else timerLabel(s), checked = s == current) { onPick(s) }
        },
    )
}

private val EMOJI_ALL = (
    "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 " +
    "😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 " +
    "😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 " +
    "👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐 🖖 👋 🤝 👏 🙌 👐 🤲 🙏 ✍️ 💪 🦾 " +
    "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 🔥 ⭐ 🌟 ✨ ⚡ 🎉 🎊 🎁 🏆 " +
    "🌹 🌸 🌺 🌻 🌙 ☀️ ⛅ 🌈 ☕ 🍕 🍔 🍟 🍰 🎂 🍫 🍉 🍎 ⚽ 🏀 🎮 🎵 🎶 🚗 ✈️ 🏠 📱 💻 📷 ✅ ❌ ❓ ❗ 💡 🔔"
).split(" ").filter { it.isNotBlank() }

/** لوحة إيموجي كاملة (للإدراج في الرسالة أو كتفاعل). */
@Composable
fun EmojiSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    IosSheet(onDismiss = onDismiss) { _ ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp),
            modifier = Modifier.heightIn(max = 380.dp).padding(horizontal = 8.dp)
        ) {
            gridItems(EMOJI_ALL) { e ->
                Box(
                    Modifier.size(46.dp).tap { onPick(e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 26.sp) }
            }
        }
    }
}

/** خلفية منقوشة خفيفة شبيهة بخلفية واتساب (أشكال بسيطة بشفافية منخفضة). */
@Composable
fun ChatPattern(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 60.dp.toPx()
        val col = if (C.dark) Color(0x0FFFFFFF) else Color(0x14000000)
        val sw = 1.5.dp.toPx()
        val stroke = Stroke(width = sw)
        val u = 6.dp.toPx()
        var r = 0
        var y = 0f
        while (y < size.height + step) {
            var x = if (r % 2 == 0) 0f else step / 2
            var c = 0
            while (x < size.width + step) {
                val h = ((r * 31 + c * 17 + r * c) and 0x7fffffff) % 6
                val cx = x + ((r * 13 + c * 7) % 22 - 11)
                val cy = y + ((r * 5 + c * 11) % 22 - 11)
                when (h) {
                    0 -> drawCircle(col, u, Offset(cx, cy), style = stroke)
                    1 -> {
                        drawLine(col, Offset(cx - u, cy), Offset(cx + u, cy), strokeWidth = sw)
                        drawLine(col, Offset(cx, cy - u), Offset(cx, cy + u), strokeWidth = sw)
                    }
                    2 -> drawRect(col, Offset(cx - u, cy - u), Size(2 * u, 2 * u), style = stroke)
                    3 -> {
                        val path = Path().apply {
                            moveTo(cx, cy - u); lineTo(cx + u, cy + u); lineTo(cx - u, cy + u); close()
                        }
                        drawPath(path, col, style = stroke)
                    }
                    4 -> drawCircle(col, 2.dp.toPx(), Offset(cx, cy))
                    else -> drawArc(
                        col, 20f, 220f, false, Offset(cx - u, cy - u), Size(2 * u, 2 * u), style = stroke
                    )
                }
                x += step; c++
            }
            y += step; r++
        }
    }
}
