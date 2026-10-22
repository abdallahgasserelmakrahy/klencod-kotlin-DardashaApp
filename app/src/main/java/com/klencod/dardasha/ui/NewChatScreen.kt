package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Profile
import kotlinx.coroutines.delay

/**
 * محادثة جديدة بأسلوب iOS: شريط تنقل بزر «إلغاء»، حقل بحث رمادي مدوّر مع زر مسح،
 * نتائج داخل قائمة مجمّعة (Inset Grouped)، وحالات فارغة/بحث/لا نتائج بأسلوب Apple.
 */
@Composable
fun NewChatScreen(vm: MainVM) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val goBack = { vm.route = Route.Home }
    val term = q.trim()

    LaunchedEffect(q) {
        val v = q.trim()
        if (v.length < 2) { results = emptyList(); searched = false; loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        results = vm.search(v); searched = true; loading = false
    }
    // نفتح لوحة المفاتيح بعد انتهاء حركة الانتقال
    LaunchedEffect(Unit) { delay(380); runCatching { focus.requestFocus() } }

    IosPage(onBack = goBack) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(
                title = "محادثة جديدة", onBack = null, separatorVisible = results.isNotEmpty(),
                trailing = { IosNavText("إلغاء", onClick = goBack) }
            )
            // حقل البحث
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
                        if (q.isEmpty()) Text("ابحث باسم المستخدم", color = IOS.secondary, fontSize = 17.sp)
                        BasicTextField(
                            q, { q = it }, singleLine = true,
                            textStyle = TextStyle(color = IOS.label, fontSize = 17.sp),
                            cursorBrush = SolidColor(IOS.blue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                        )
                    }
                    if (loading) IosSpinner(Modifier.size(16.dp), color = IOS.secondary)
                    else if (q.isNotEmpty()) Icon(
                        Icons.Filled.Cancel, "مسح", tint = IOS.secondary,
                        modifier = Modifier.size(18.dp).tap { q = "" }
                    )
                }
            }

            when {
                term.length < 2 -> NewChatHint(
                    Icons.Filled.PersonSearch, "ابحث عن مستخدم",
                    "اكتب اسم المستخدم (حرفان على الأقل) لبدء محادثة جديدة."
                )
                searched && results.isEmpty() -> NewChatHint(
                    Icons.Filled.SearchOff, "لا توجد نتائج", "لا يوجد مستخدم بالاسم «$term»."
                )
                results.isNotEmpty() -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item(key = "header") {
                        Text(
                            "المستخدمون", color = IOS.secondary, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 32.dp, top = 10.dp, bottom = 6.dp)
                        )
                    }
                    itemsIndexed(results, key = { _, p -> p.id }) { i, p ->
                        Box(
                            Modifier.animateItem().padding(horizontal = 16.dp).clip(rowShape(i, results.size))
                                .background(CellBg)
                        ) {
                            RowBox(last = i == results.lastIndex, inset = 72.dp, onClick = { fm.clearFocus(); vm.openChat(p.id) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(p.username, p.avatarPath, 44.dp, ring = CellBg, plain = true)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            p.username, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (!p.about.isNullOrBlank()) Text(
                                            p.about, color = IOS.secondary, fontSize = 15.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                        tint = IOS.tertiary, modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
                else -> Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColumnScope.NewChatHint(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = IOS.tertiary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = IOS.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = IOS.secondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
    }
}
