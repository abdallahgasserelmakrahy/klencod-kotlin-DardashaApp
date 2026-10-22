package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.data.SavedAccount

/** قائمة الحسابات المحفوظة (بأسلوب iOS): اضغط على حساب للتبديل إليه، أو «إضافة حساب». */
@Composable
fun AccountsPanel(vm: MainVM, onDone: () -> Unit = {}, hPad: Dp = 16.dp) {
    var confirmRemove by remember { mutableStateOf<SavedAccount?>(null) }
    val me = vm.meId

    Column(Modifier.fillMaxWidth()) {
        vm.accounts.forEach { a ->
            val active = a.id == me
            // الحساب النشط نعرض بياناته الحيّة، وغيره من المحفوظ
            val live = if (active) vm.peers[a.id] else null
            val title = live?.displayName?.takeIf { it.isNotBlank() } ?: live?.username?.takeIf { it.isNotBlank() } ?: a.title
            val handle = live?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: a.handle
            val avatar = live?.avatarPath ?: a.avatarPath
            val nameForAvatar = live?.username?.takeIf { it.isNotBlank() } ?: a.username.ifBlank { a.title }

            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .pressRow(enabled = !active) { onDone(); vm.switchTo(a.id) }
                        .padding(horizontal = hPad, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(nameForAvatar, avatar, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title, color = IOS.label, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(handle, color = IOS.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (active) {
                        Icon(Icons.Filled.Check, "الحساب الحالي", tint = IOS.blue, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.size(40.dp).tap { confirmRemove = a },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Close, "إزالة الحساب", tint = IOS.tertiary, modifier = Modifier.size(18.dp)) }
                }
                HorizontalDivider(
                    Modifier.align(Alignment.BottomStart).padding(start = hPad + 58.dp),
                    color = IOS.separator, thickness = 0.5.dp
                )
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .pressRow { onDone(); vm.startAddAccount() }
                .padding(horizontal = hPad, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(IOS.fill),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, null, tint = IOS.blue) }
            Spacer(Modifier.width(14.dp))
            Text("إضافة حساب", color = IOS.blue, fontSize = 17.sp)
        }
    }

    confirmRemove?.let { a ->
        IosAlert(
            title = "إزالة الحساب؟",
            message = "سيُسجَّل خروج ${a.handle.ifBlank { a.title }} من هذا الجهاز. تستطيع إضافته لاحقاً بكلمة المرور.",
            confirmText = "إزالة",
            onConfirm = { confirmRemove = null; onDone(); vm.removeAccount(a.id) },
            onDismiss = { confirmRemove = null },
        )
    }
}

/** ورقة سفلية للتبديل السريع بين الحسابات (من قائمة ⋮ في الشاشة الرئيسية). */
@Composable
fun AccountsSheet(vm: MainVM, onDismiss: () -> Unit) {
    IosSheet(
        onDismiss = onDismiss, title = "الحسابات",
        trailing = { close -> IosNavText("تم", bold = true) { close() } },
    ) { close ->
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).clip(RoundedCornerShape(12.dp)).background(CellBg)) {
            AccountsPanel(vm, onDone = close, hPad = 14.dp)
        }
    }
}
