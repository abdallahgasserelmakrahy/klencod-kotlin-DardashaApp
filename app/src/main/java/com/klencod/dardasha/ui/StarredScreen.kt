package com.klencod.dardasha.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Message
import com.klencod.dardasha.data.fmtList

/** أصفر النجمة في iOS (System Yellow). */
private val IosYellow: Color get() = if (C.dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)

/**
 * الرسائل المميّزة بنجمة — بأسلوب iOS:
 * عنوان كبير يتقلّص إلى شريط التنقل عند التمرير، حقل بحث، قائمة مجمّعة (Inset Grouped)،
 * سحب الصف لإزالة النجمة، وسحب من الحافة للرجوع.
 */
@Composable
fun StarredScreen(vm: MainVM) {
    val haptic = LocalHapticFeedback.current
    val goBack = { vm.route = Route.Home }
    var q by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }

    val all = vm.starredMessages()
    val needle = q.trim()
    val list = if (needle.isEmpty()) all else all.filter {
        vm.preview(it).contains(needle, ignoreCase = true) || vm.name(vm.other(it)).contains(needle, ignoreCase = true)
    }

    val listState = rememberLazyListState()
    val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val collapsed by remember(thresholdPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx }
    }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "starTitle")

    IosPage(onBack = goBack) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(
                title = "الرسائل المميّزة", onBack = goBack, separatorVisible = collapsed,
                backLabel = "دردشة", titleAlpha = titleAlpha
            )
            if (all.isEmpty()) {
                StarredEmpty()
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                item(key = "large-title") {
                    Text(
                        "الرسائل المميّزة", color = IOS.label, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                    )
                }
                item(key = "search") { StarSearch(q, { q = it }, searchFocused) { searchFocused = it } }
                if (list.isEmpty()) {
                    item(key = "no-results") {
                        Column(
                            Modifier.fillParentMaxHeight(0.5f).fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text("لا توجد نتائج", color = IOS.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("لا نتائج لـ «$needle»", color = IOS.secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                        val peer = vm.other(m)
                        val unstar = SwipeAction("إلغاء التمييز", Icons.Outlined.StarBorder, IOS.orange) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.toggleStar(m)
                        }
                        Box(
                            Modifier.animateItem().padding(horizontal = 16.dp)
                                .clip(rowShape(i, list.size))
                        ) {
                            SwipeRow(
                                enabled = true, closeSignal = openId != m.id, onOpen = { openId = m.id },
                                leading = emptyList(), trailing = listOf(unstar)
                            ) { isOpen, close ->
                                StarredRow(
                                    vm, m, last = i == list.size - 1, isOpen = isOpen,
                                    onTap = { if (isOpen) close() else vm.openChat(peer) }
                                )
                            }
                        }
                    }
                    item(key = "footer") {
                        IFooter("${list.size} ${if (list.size == 1) "رسالة" else "رسائل"} مميّزة • اسحب الصف لإلغاء التمييز")
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StarredRow(vm: MainVM, m: Message, last: Boolean, isOpen: Boolean, onTap: () -> Unit) {
    val peer = vm.other(m)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) IOS.pressed else CellBg, tween(if (pressed) 0 else 250), label = "starRowBg")
    val mine = m.sender == vm.meId

    Box(
        Modifier.fillMaxWidth().background(bg)
            .combinedClickable(interactionSource = src, indication = null, onClick = onTap)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(vm.name(peer), vm.peers[peer]?.avatarPath, 44.dp, ring = CellBg, plain = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vm.name(peer), color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(fmtList(m.createdAt), color = IOS.secondary, fontSize = 15.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        (if (mine) "أنت: " else "") + vm.preview(m),
                        color = IOS.secondary, fontSize = 15.sp, lineHeight = 20.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Star, "رسالة مميّزة", tint = IosYellow,
                        modifier = Modifier.padding(top = 2.dp).size(15.dp)
                    )
                }
            }
        }
        if (!last) Box(
            Modifier.align(Alignment.BottomStart).padding(start = 72.dp).fillMaxWidth().height(0.5.dp).background(IOS.separator)
        )
    }
}

/** الحالة الفارغة بأسلوب Apple: أيقونة كبيرة رمادية + عنوان + شرح. */
@Composable
private fun StarredEmpty() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Star, null, tint = IOS.tertiary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            "لا توجد رسائل مميّزة", color = IOS.label, fontSize = 22.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "اضغط مطوّلاً على أي رسالة ثم «تمييز بنجمة».",
            color = IOS.secondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StarSearch(q: String, onQ: (String) -> Unit, focused: Boolean, onFocus: (Boolean) -> Unit) {
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
        if (focused || q.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(
                "إلغاء", color = IOS.blue, fontSize = 17.sp,
                modifier = Modifier.tap { onQ(""); fm.clearFocus() }
            )
        }
    }
}
