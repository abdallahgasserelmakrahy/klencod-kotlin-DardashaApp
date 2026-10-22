package com.klencod.dardasha.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.WaLinkService
import com.klencod.dardasha.data.LinkAcc
import com.klencod.dardasha.data.WaLink

/** ربط أرقام واتساب كأجهزة مصاحبة (حتى عدة أرقام) وإدارتها. */
@Composable
fun WaLinkScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val back = { vm.route = Route.WhatsApp }
    var phone by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<LinkAcc?>(null) }
    val scroll = rememberScrollState()
    val accounts = WaLink.accounts.toList()

    IosPage(onBack = back) {
        Column(Modifier.fillMaxSize()) {
            IosTopBar("ربط أرقام واتساب", onBack = back, separatorVisible = scroll.value > 10, backLabel = "واتساب")
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp)) {
                Text(
                    "ربط أرقام واتساب", color = IOS.label, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                if (!WaLink.available()) IFooter(
                    "هذه النسخة لا تتضمن مكتبة الربط (لم ينجح بناء الجسر أثناء التجميع)، فالربط غير متاح فيها. راجع سجل البناء عند خطوة wabridge.",
                    color = IOS.red
                )
                IFooter("يعمل التطبيق كجهاز مصاحب لحسابك مثل واتساب ويب، فتصل رسائل كل أرقامك إلى شاشة واتساب هنا ويمكنك الرد منها.")

                // ---- الأرقام المرتبطة / قيد الربط ----
                if (accounts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    IGroup("الأرقام") {
                        accounts.forEachIndexed { i, a ->
                            RowBox(last = i == accounts.lastIndex, inset = 16.dp, onClick = { confirmRemove = a }) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (a.pending) "رقم جديد قيد الربط" else "+${a.id}",
                                            color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            statusText(a), fontSize = 14.sp,
                                            color = when (a.status) { "connected" -> IosGreen; "error" -> IOS.red; else -> IOS.secondary }
                                        )
                                    }
                                    if (a.status == "error" && a.info.isNotBlank())
                                        Text(a.info, color = IOS.red, fontSize = 12.sp)
                                    a.pairCode?.let { code ->
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            code.chunked(4).joinToString("-"), color = IOS.label, fontSize = 34.sp,
                                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "في واتساب: الإعدادات ← الأجهزة المرتبطة ← ربط جهاز ← «الربط باستخدام رقم الهاتف بدلاً من ذلك»، ثم اكتب هذا الرمز.",
                                            color = IOS.secondary, fontSize = 13.sp
                                        )
                                    }
                                    a.qr?.let { qr ->
                                        Spacer(Modifier.height(10.dp))
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(qr, 240.dp) }
                                        Text(
                                            "امسح الرمز من واتساب على هاتف آخر: الأجهزة المرتبطة ← ربط جهاز.",
                                            color = IOS.secondary, fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IFooter("اضغط على رقم لإزالته (يُسجَّل خروجه من واتساب ويُحذف من هنا).")
                }

                // ---- ربط رقم جديد ----
                Spacer(Modifier.height(16.dp))
                IGroup("ربط رقم جديد") {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("رقم الهاتف بالصيغة الدولية بدون + (مثال: 96512345678)", color = IOS.secondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(IOS.fill).padding(horizontal = 14.dp, vertical = 11.dp)) {
                            if (phone.isEmpty()) Text("96512345678", color = IOS.tertiary, fontSize = 17.sp)
                            BasicTextField(
                                value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() }.take(18) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = TextStyle(color = IOS.label, fontSize = 17.sp), cursorBrush = SolidColor(IOS.blue),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        ActionButton("ربط برمز من 8 خانات", enabled = phone.length >= 8 && WaLink.available()) {
                            WaLinkService.ensure(ctx, force = true)
                            WaLink.add(ctx, phone); phone = ""
                        }
                        Spacer(Modifier.height(8.dp))
                        ActionButton("ربط برمز QR (من هاتف آخر)", enabled = WaLink.available(), filled = false) {
                            WaLinkService.ensure(ctx, force = true)
                            WaLink.add(ctx, "")
                        }
                    }
                }
                IFooter("إن كان واتساب على نفس هذا الهاتف فاختر «الرمز من 8 خانات»، لأنك لا تستطيع مسح QR من الشاشة نفسها.")

                // ---- استقرار الاتصال ----
                Spacer(Modifier.height(16.dp))
                IGroup("استقرار الاتصال") {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "يعمل الاتصال بخدمة بإشعار ثابت «واتساب متصل». لتجنّب أن يقتله أندرويد، استثنِ التطبيق من توفير البطارية، وفي هواتف أوبو/شاومي/هواوي فعّل «التشغيل التلقائي» وثبّت التطبيق في قائمة التطبيقات الأخيرة.",
                            color = IOS.secondary, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton("استثناء من توفير البطارية", filled = false) {
                            runCatching {
                                ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                }
                IFooter("تنبيه: ربط تطبيق غير رسمي بواتساب يخالف شروط واتساب وقد يؤدي إلى حظر الرقم. استخدمه لأرقامك وأرقام عائلتك بموافقتهم، وتجنّب الإرسال الجماعي.", color = IOS.red)
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    confirmRemove?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(if (a.pending) "إلغاء الربط؟" else "إزالة الرقم؟") },
            text = { Text("سيُفصل هذا الرقم عن التطبيق ويُحذف من أجهزة واتساب المرتبطة. محادثاته المحفوظة تبقى هنا حتى تحذفها.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { WaLink.remove(ctx, a.id); confirmRemove = null }) { Text("إزالة", color = IOS.red) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = null }) { Text("إلغاء") } }
        )
    }
}

private fun statusText(a: LinkAcc) = when (a.status) {
    "connected" -> "متصل"
    "connecting" -> "جارٍ الاتصال…"
    "qr" -> "بانتظار مسح الرمز"
    "paircode" -> "بانتظار إدخال الرمز"
    "disconnected" -> "غير متصل (يعيد المحاولة)"
    "error" -> "خطأ"
    else -> a.status
}

@Composable
private fun ActionButton(text: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (!enabled) IOS.fill else if (filled) IosGreen else IOS.fill)
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = if (!enabled) IOS.tertiary else if (filled) Color.White else IOS.blue
        )
    }
}

/** يرسم نص QR كصورة (خلفية بيضاء دائماً ليسهل مسحه). */
@Composable
private fun QrImage(text: String, size: Dp) {
    val bmp = remember(text) {
        val n = 512
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, n, n)
        val px = IntArray(n * n) { i -> if (m.get(i % n, i / n)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp))
}
