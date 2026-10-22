package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** شعار التطبيق بشكل أيقونة iOS (مربّع بزوايا مدوّرة) ولون التمييز وفقاعة محادثة. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.225f)).background(C.accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Chat, null, tint = C.onAccent, modifier = Modifier.size(size * 0.52f))
    }
}

/** شاشة انتظار ثابتة بلا حركة (تظهر فقط عند وجود حساب قديم بلا كاش وقبل التحقق من الجلسة). */
@Composable
fun SplashScreen(loading: Boolean) {
    Box(Modifier.fillMaxSize().background(IOS.bg).safeDrawingPadding()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(110.dp)
            Spacer(Modifier.height(18.dp))
            Text("دردشة", color = IOS.label, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** شاشة الترحيب بأسلوب Apple: شعار + عنوان كبير + قائمة مزايا + زر رئيسي عريض. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(IOS.bg).safeDrawingPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Appear(0) { AppLogo(96.dp) }
            Spacer(Modifier.height(24.dp))
            Appear(1) {
                Text(
                    "مرحباً بك في دردشة", color = IOS.label, fontSize = 32.sp, lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(36.dp))
            Appear(2) {
                WelcomePoint(Icons.Filled.Mic, IOS.red, "رسائل صوتية وصور وملفات", "أرسل كل ما تريد في محادثة واحدة.")
            }
            Appear(3) {
                WelcomePoint(Icons.Filled.Timer, IOS.orange, "رسائل مؤقتة", "تختفي تلقائياً بعد المدة التي تحدّدها.")
            }
            Appear(4) {
                WelcomePoint(Icons.Filled.SwitchAccount, IOS.indigo, "أكثر من حساب", "بدّل بين حساباتك على نفس الجهاز.")
            }
        }
        Appear(5) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "تواصل مع أصدقائك برسائل فورية بسهولة وأمان.",
                    color = IOS.secondary, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                IosPrimaryButton("متابعة", onClick = onContinue)
            }
        }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp).padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = IOS.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = IOS.secondary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
