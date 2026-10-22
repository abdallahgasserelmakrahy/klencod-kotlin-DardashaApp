package com.klencod.dardasha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Media
import com.klencod.dardasha.data.ProfileLink
import com.klencod.dardasha.data.normalizeUrl
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** الملف الشخصي بأسلوب iOS: قوائم Inset Grouped، حقول مدمجة، زر «حفظ» في شريط التنقل. */
@Composable
fun ProfileScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = vm.meId ?: return
    val prof = vm.peers[me]
    var displayName by remember(prof?.displayName) { mutableStateOf(prof?.displayName ?: "") }
    var about by remember(prof?.about) { mutableStateOf(prof?.about ?: "") }
    var bio by remember(prof?.bio) { mutableStateOf(prof?.bio ?: "") }
    var city by remember(prof?.city) { mutableStateOf(prof?.city ?: "") }
    var birthday by remember(prof?.birthday) { mutableStateOf(prof?.birthday?.take(10) ?: "") }
    val links = remember(prof?.links) {
        mutableStateListOf<ProfileLink>().also { it.addAll(prof?.links.orEmpty()) }
    }
    var saving by remember { mutableStateOf(false) }
    var signOutAlert by remember { mutableStateOf(false) }
    var dateSheet by remember { mutableStateOf(false) }

    val cleanLinks = links.filter { it.url.isNotBlank() }
    val birthdayOk = birthday.isBlank() || runCatching {
        val d = LocalDate.parse(birthday.trim()); !d.isAfter(LocalDate.now()) && d.year >= 1900
    }.getOrDefault(false)
    val linksOk = cleanLinks.all { normalizeUrl(it.url) != null }
    val changed = displayName.trim() != (prof?.displayName ?: "") ||
        about.trim() != (prof?.about ?: "") ||
        bio.trim() != (prof?.bio ?: "") ||
        city.trim() != (prof?.city ?: "") ||
        birthday.trim() != (prof?.birthday?.take(10) ?: "") ||
        cleanLinks.map { it.title.trim() to it.url.trim() } !=
        prof?.links.orEmpty().map { it.title to it.url }
    val canSave = changed && birthdayOk && linksOk && !saving

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri, max = 512, quality = 85) }
            if (b != null) vm.setAvatar(b) else vm.toast = "تعذّر قراءة الصورة"
        }
    }
    fun pickPhoto() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun save() {
        saving = true
        vm.saveProfile(
            displayName.trim(), about.trim(), bio.trim(), city.trim(),
            birthday.trim().ifBlank { null },
            cleanLinks.map { ProfileLink(it.title.trim(), normalizeUrl(it.url)!!) },
            onDone = { saving = false }
        ).invokeOnCompletion { saving = false }
    }

    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 4 } }

    IosPage(
        onBack = { vm.route = Route.Home },
        overlay = {
            IosDateSheet(
                visible = dateSheet, title = "تاريخ الميلاد",
                initial = runCatching { LocalDate.parse(birthday.trim()) }.getOrNull(),
                onDismiss = { dateSheet = false },
                onConfirm = { birthday = it.toString(); dateSheet = false },
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar(
                "الملف الشخصي", onBack = { vm.route = Route.Home }, separatorVisible = scrolled,
                trailing = {
                    if (saving) IosSpinner(Modifier.size(18.dp), color = IOS.secondary)
                    else IosNavText("حفظ", enabled = canSave, bold = true) { save() }
                }
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // ── الصورة ──
                Appear(0) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val src = remember { MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.6f, 700f), label = "pav")
                        Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null) { pickPhoto() }) {
                            Avatar(prof?.username ?: "?", prof?.avatarPath, 104.dp, ring = Grouped)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                            IosNavText("تعديل") { pickPhoto() }
                            if (prof?.avatarPath != null) IosNavText("إزالة", color = IOS.red) { vm.removeAvatar() }
                        }
                    }
                }

                // ── اسم المستخدم (للقراءة فقط) ──
                Appear(1) {
                    IGroup {
                        RowBox(last = true, inset = 16.dp, onClick = null) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("اسم المستخدم", color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Text("@" + (prof?.username ?: ""), color = IOS.secondary, fontSize = 17.sp)
                            }
                        }
                    }
                }

                // ── الحقول الأساسية ──
                Appear(2) {
                    Column {
                        IGroup {
                            FieldRow("الاسم", displayName, { displayName = it.take(50) }, "الاسم المعروض (اختياري)", last = false)
                            FieldRow("حول", about, { about = it.take(140) }, "حالتك أو عبارة قصيرة", last = false, singleLine = false)
                            FieldRow("المدينة", city, { city = it.take(60) }, "اختياري", last = false)
                            BirthdayRow(birthday, onOpen = { dateSheet = true }, onClear = { birthday = "" })
                        }
                        IFooter("${about.length}/140", alignEnd = true)
                        if (!birthdayOk) IFooter("صيغة التاريخ غير صحيحة", IOS.red)
                    }
                }

                // ── النبذة ──
                Appear(3) {
                    Column {
                        IGroup(header = "نبذة عني") {
                            RowBox(last = true, inset = 16.dp, onClick = null) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    PlainField(bio, { bio = it.take(300) }, "اكتب نبذة عنك", singleLine = false)
                                }
                            }
                        }
                        IFooter("${bio.length}/300", alignEnd = true)
                    }
                }

                // ── الروابط ──
                Appear(4) {
                    Column {
                        IGroup(header = "الروابط (حتى 5)") {
                            links.forEachIndexed { i, l ->
                                RowBox(last = i == links.lastIndex && links.size >= 5, inset = 16.dp, onClick = null) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.size(24.dp).tap { links.removeAt(i) }, contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(22.dp).background(IOS.red, CircleShape), contentAlignment = Alignment.Center) {
                                                Box(Modifier.size(11.dp, 2.dp).background(Color.White, RoundedCornerShape(1.dp)))
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
                                                PlainField(
                                                    l.url, { links[i] = l.copy(url = it.trim().take(300)) },
                                                    "الرابط (مثل instagram.com/name)", singleLine = true,
                                                    keyboard = KeyboardType.Uri,
                                                    textColor = if (l.url.isNotBlank() && normalizeUrl(l.url) == null) IOS.red else IOS.label
                                                )
                                            }
                                            HorizontalDivider(color = IOS.separator, thickness = 0.5.dp)
                                            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
                                                PlainField(l.title, { links[i] = l.copy(title = it.take(30)) }, "العنوان (اختياري)", singleLine = true)
                                            }
                                        }
                                    }
                                }
                            }
                            if (links.size < 5) RowBox(last = true, inset = 16.dp, onClick = { links.add(ProfileLink()) }) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(22.dp).background(IosGreen, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text("إضافة رابط", color = IOS.label, fontSize = 17.sp)
                                }
                            }
                        }
                        if (!linksOk) IFooter("أحد الروابط غير صالح", IOS.red)
                    }
                }

                // ── الحسابات ──
                Appear(5) {
                    IGroup(header = "الحسابات") { AccountsPanel(vm, hPad = 16.dp) }
                }

                // ── تسجيل الخروج ──
                Appear(6) {
                    IGroup {
                        RowBox(last = true, inset = 16.dp, onClick = { signOutAlert = true }) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = IOS.red, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("تسجيل الخروج", color = IOS.red, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (signOutAlert) IosAlert(
        title = "تسجيل الخروج؟",
        message = "سيُسجَّل خروج هذا الحساب من الجهاز.",
        confirmText = "تسجيل الخروج",
        onConfirm = { signOutAlert = false; vm.signOut() },
        onDismiss = { signOutAlert = false },
    )
}

/** حقل نصي بلا إطار مع نص إرشادي (Placeholder) بأسلوب iOS. */
@Composable
private fun PlainField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    singleLine: Boolean, keyboard: KeyboardType = KeyboardType.Text, textColor: Color = IOS.label,
) {
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) Text(placeholder, color = IOS.tertiary, fontSize = 17.sp)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(),
            singleLine = singleLine, maxLines = if (singleLine) 1 else 8,
            textStyle = TextStyle(color = textColor, fontSize = 17.sp),
            cursorBrush = SolidColor(IOS.blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        )
    }
}

@Composable
private fun FieldRow(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String,
    last: Boolean, singleLine: Boolean = true,
) {
    RowBox(last, inset = 16.dp, onClick = null) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = IOS.label, fontSize = 17.sp, modifier = Modifier.width(96.dp))
            Box(Modifier.weight(1f)) { PlainField(value, onChange, placeholder, singleLine) }
        }
    }
}

/** تاريخ الميلاد: صف يفتح عجلات التاريخ (Action Sheet)، مع زر مسح. */
@Composable
private fun BirthdayRow(value: String, onOpen: () -> Unit, onClear: () -> Unit) {
    val shown = remember(value) {
        runCatching {
            if (value.isBlank()) "" else {
                val f = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
                f.format(LocalDate.parse(value.trim()))
            }
        }.getOrDefault(value)
    }
    RowBox(last = true, inset = 16.dp, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الميلاد", color = IOS.label, fontSize = 17.sp, modifier = Modifier.width(96.dp))
            if (shown.isEmpty()) Text("اختر التاريخ", color = IOS.tertiary, fontSize = 17.sp, modifier = Modifier.weight(1f))
            else Text(shown, color = IOS.blue, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) Icon(
                Icons.Filled.Cancel, "مسح التاريخ", tint = IOS.tertiary,
                modifier = Modifier.size(22.dp).tap { onClear() }
            )
        }
    }
}
