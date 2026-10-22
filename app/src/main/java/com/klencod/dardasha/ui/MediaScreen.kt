package com.klencod.dardasha.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PermMedia
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Media
import com.klencod.dardasha.data.fmtDay
import com.klencod.dardasha.data.parseGeo
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MEDIA_URL_RE = Regex("""(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** معرض المحادثة بأسلوب iOS: Segmented Control + شبكة صور + قوائم Inset Grouped. */
@Composable
fun MediaScreen(vm: MainVM, peer: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<String?>(null) }

    val th by remember(peer) { derivedStateOf { vm.thread(peer).asReversed().filter { !it.isDeleted } } }
    val images = th.filter { it.imagePath != null && it.viewOnce != true }
    val files = th.filter { it.filePath != null }
    val links = th.filter { parseGeo(it.text) == null }.flatMap { m ->
        MEDIA_URL_RE.findAll(m.text).mapNotNull { r ->
            var raw = r.value
            while (raw.isNotEmpty() && raw.last() in ".,;:!?)،؛") raw = raw.dropLast(1)
            if (raw.isEmpty()) null else m.createdAt to raw
        }.toList()
    }

    IosPage(onBack = { vm.route = Route.Contact(peer) }) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(vm.name(peer), onBack = { vm.route = Route.Contact(peer) }, separatorVisible = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 10.dp)) {
                IosSegmented(listOf("صور (${images.size})", "ملفات (${files.size})", "روابط (${links.size})"), tab) { tab = it }
            }
            HorizontalDivider(color = IOS.separator, thickness = 0.5.dp)

            Crossfade(tab, animationSpec = tween(220), label = "mediaTab") { t ->
                when (t) {
                    0 -> if (images.isEmpty()) EmptyHint(Icons.Filled.PermMedia, "لا توجد صور بعد") else LazyVerticalGrid(
                        GridCells.Fixed(3), Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        gridItems(images, key = { it.id }) { m ->
                            StorageImage(
                                "chat-images", m.imagePath!!,
                                Modifier.fillMaxWidth().aspectRatio(1f).tap { viewer = m.imagePath }
                            )
                        }
                    }
                    1 -> if (files.isEmpty()) EmptyHint(Icons.Filled.Description, "لا توجد ملفات بعد") else LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)
                    ) {
                        itemsIndexed(files, key = { _, m -> m.id }) { i, m ->
                            MediaRow(
                                Icons.Filled.Description, IOS.blue, m.fileName ?: "ملف", fmtDay(m.createdAt),
                                i, files.size, maxLines = 2, titleColor = IOS.label
                            ) {
                                scope.launch {
                                    if (!Media.openFile(ctx, m.filePath!!, m.fileName, m.fileType)) vm.toast = "تعذّر فتح الملف"
                                }
                            }
                        }
                    }
                    else -> if (links.isEmpty()) EmptyHint(Icons.Filled.Link, "لا توجد روابط بعد") else LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)
                    ) {
                        itemsIndexed(links) { i, (date, raw) ->
                            val url = if (raw.startsWith("www.", true)) "https://$raw" else raw
                            MediaRow(
                                Icons.Filled.Link, IOS.indigo, raw, fmtDay(date),
                                i, links.size, maxLines = 2, titleColor = IOS.blue
                            ) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { vm.toast = "تعذّر فتح الرابط" }
                            }
                        }
                    }
                }
            }
        }
    }

    viewer?.let { path -> ImageViewer(path) { viewer = null } }
}

@Composable
private fun MediaRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String,
    index: Int, count: Int, maxLines: Int, titleColor: Color, onClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth().clip12(index, count).background(CellBg)) {
        RowBox(last = index == count - 1, inset = 64.dp, onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).background(tint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = titleColor, fontSize = 16.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = IOS.secondary, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun Modifier.clip12(i: Int, n: Int): Modifier = this.then(Modifier.clip(rowShape(i, n)))

@Composable
private fun EmptyHint(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = IOS.tertiary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = IOS.secondary, fontSize = 17.sp)
        Spacer(Modifier.height(80.dp))
    }
}

/** عارض الصور بأسلوب iOS: اسحب لأعلى/لأسفل للإغلاق مع تصغير وتعتيم تدريجي. */
@Composable
private fun ImageViewer(path: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val off = remember { Animatable(0f) }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    val progress = (abs(off.value) / (screenH * 0.5f)).coerceIn(0f, 1f)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - progress * 0.85f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(off.value) > threshold) {
                                    off.animateTo(if (off.value > 0) screenH else -screenH, tween(180)); onClose()
                                } else off.animateTo(0f, spring(0.8f, 400f))
                            }
                        },
                        onDragCancel = { scope.launch { off.animateTo(0f, spring(0.8f, 400f)) } },
                    ) { change, dy ->
                        change.consume()
                        scope.launch { off.snapTo(off.value + dy) }
                    }
                }
        ) {
            StorageImage(
                "chat-images", path,
                Modifier.fillMaxSize().graphicsLayer {
                    translationY = off.value
                    val s = 1f - progress * 0.25f
                    scaleX = s; scaleY = s
                },
                ContentScale.Fit, force = true
            )
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp).alpha(1f - progress)) {
                IosNavText("تم", bold = true, onClick = onClose)
            }
        }
    }
}
