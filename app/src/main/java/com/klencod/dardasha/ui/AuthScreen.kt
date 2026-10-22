package com.klencod.dardasha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM

/** الزر الرئيسي بأسلوب iOS: مستطيل مدوّر بلون التمييز، يتقلّص قليلاً عند الضغط ويخفت عند التعطيل. */
@Composable
internal fun IosPrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(0.7f, 600f), label = "btnScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "btnAlpha")
    Box(
        Modifier.fillMaxWidth().height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed && enabled) C.accent.copy(alpha = 0.85f) else C.accent)
            .clickable(src, null, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) IosSpinner(Modifier.size(20.dp), color = C.onAccent)
        else Text(text, color = C.onAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuthField(
    value: String, onChange: (String) -> Unit, placeholder: String, icon: ImageVector, last: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false, onDone: () -> Unit = {},
) {
    var show by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = IOS.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = IOS.tertiary, fontSize = 17.sp, maxLines = 1)
                BasicTextField(
                    value, onChange, singleLine = true,
                    textStyle = TextStyle(color = IOS.label, fontSize = 17.sp),
                    cursorBrush = SolidColor(IOS.blue),
                    visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onNext = { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                        onDone = { onDone() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (password && value.isNotEmpty()) Icon(
                if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                if (show) "إخفاء" else "إظهار", tint = IOS.secondary,
                modifier = Modifier.size(22.dp).tap { show = !show }
            )
        }
        if (!last) HorizontalDivider(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp), color = IOS.separator, thickness = 0.5.dp
        )
    }
}

@Composable
fun AuthScreen(vm: MainVM) {
    var signup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val fm = LocalFocusManager.current

    val canSubmit = !vm.authBusy && email.isNotBlank() && pass.length >= 6 && (!signup || user.isNotBlank())
    fun submit() {
        if (!canSubmit) return
        fm.clearFocus()
        err = ""; info = ""
        if (signup) {
            if (!Regex("^[A-Za-z0-9_]{3,20}$").matches(user)) {
                err = "اسم المستخدم: 3 إلى 20 حرف إنجليزي أو رقم أو _"; return
            }
            vm.signUp(email, pass, user, { err = it }, {
                info = "تم التسجيل. أكّد الإيميل من الرسالة ثم ادخل."; signup = false
            })
        } else vm.signIn(email, pass) { err = it }
    }

    Box(Modifier.fillMaxSize().background(Grouped)) {
        if (vm.addingAccount) {
            Row(
                Modifier.safeDrawingPadding().padding(start = 8.dp, top = 6.dp).align(Alignment.TopStart)
                    .tap { vm.cancelAddAccount() }.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "رجوع", tint = IOS.blue, modifier = Modifier.size(30.dp))
                Text("رجوع", color = IOS.blue, fontSize = 17.sp)
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Appear(0) { AppLogo(80.dp) }
            Spacer(Modifier.height(16.dp))
            Appear(1) {
                Text("دردشة", color = IOS.label, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Crossfade(
                when {
                    vm.addingAccount && signup -> "أنشئ حساباً جديداً وأضفه إلى هذا الجهاز"
                    vm.addingAccount -> "سجّل دخول حساب آخر. حسابك الحالي يبقى محفوظاً."
                    signup -> "أنشئ حساباً جديداً"
                    else -> "أدخل الإيميل وكلمة المرور للمتابعة."
                }, animationSpec = tween(200), label = "authSub"
            ) { t ->
                Text(t, color = IOS.secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))

            Appear(2) {
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CellBg)) {
                        AnimatedVisibility(
                            signup,
                            enter = expandVertically(spring(0.85f, 400f)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                        ) {
                            AuthField(
                                user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                                "اسم المستخدم", Icons.Filled.AlternateEmail, last = false
                            )
                        }
                        AuthField(
                            email, { email = it.trim() }, "الإيميل", Icons.Filled.Email, last = false,
                            keyboardType = KeyboardType.Email
                        )
                        AuthField(
                            pass, { pass = it }, "كلمة المرور", Icons.Filled.Lock, last = true,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                            password = true, onDone = { submit() }
                        )
                    }
                    when {
                        err.isNotEmpty() -> IFooter(err, color = IOS.red)
                        info.isNotEmpty() -> IFooter(info, color = C.accent)
                        signup -> IFooter("اسم المستخدم: 3 إلى 20 حرفاً إنجليزياً أو رقماً أو _ • كلمة المرور 6 أحرف على الأقل.")
                        else -> IFooter("كلمة المرور 6 أحرف على الأقل.")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Appear(3) {
                IosPrimaryButton(if (signup) "إنشاء حساب" else "دخول", enabled = canSubmit, loading = vm.authBusy) { submit() }
            }
            Spacer(Modifier.height(8.dp))
            Appear(4) {
                Text(
                    if (signup) "عندك حساب؟ ادخل" else "ما عندك حساب؟ سجّل",
                    color = IOS.blue, fontSize = 17.sp,
                    modifier = Modifier.tap { signup = !signup; err = ""; info = "" }.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
