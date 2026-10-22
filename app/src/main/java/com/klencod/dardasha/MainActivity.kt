package com.klencod.dardasha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.klencod.dardasha.data.AppSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.klencod.dardasha.ui.*

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_WA = "open_wa" }

    private val vm: MainVM by viewModels()

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // شاشة النظام الافتتاحية تبقى فقط حتى تجهز القائمة من الكاش المحلي (عادةً أجزاء من الثانية) ثم تختفي فوراً
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !vm.cacheReady }
        splash.setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        C.systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (C.dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = if (C.dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            // شريط الحالة/التنقل وخلفية النافذة يتبعان وضع المظهر، وFLAG_SECURE يتبع إعداد الخصوصية
            val dark = C.dark
            val bgColor = C.bg
            val secure = AppSettings.screenSecurity
            LaunchedEffect(dark, bgColor) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                )
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor.toArgb()))
            }
            LaunchedEffect(secure) {
                if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            DardashaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(vm)
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        // اختصار «واتساب»: أيقونة الـ alias في الشاشة الرئيسية، أو اختصار مثبَّت يحمل open_wa
        val viaAlias = i?.component?.className?.endsWith("WaAlias") == true
        if (i != null && (viaAlias || i.getBooleanExtra(EXTRA_OPEN_WA, false))) {
            i.removeExtra(EXTRA_OPEN_WA)
            vm.openWhatsApp()
        }
        val peer = i?.getStringExtra(Notifier.EXTRA_PEER) ?: return
        val account = i.getStringExtra(Notifier.EXTRA_ACCOUNT)
        i.removeExtra(Notifier.EXTRA_PEER)
        i.removeExtra(Notifier.EXTRA_ACCOUNT)
        vm.openChatFromNotification(account, peer)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
        vm.onForeground()
        WaLinkService.ensure(this)
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
    }
}

@Composable
private fun Root(vm: MainVM) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast = null }
    }
    // شاشة الترحيب (لا يوجد حساب). لا توجد شاشة افتتاحية مصطنعة: القائمة تظهر مباشرة من الكاش.
    var welcomed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.meId) { if (vm.meId != null) welcomed = false }
    val showSplash = vm.booting && vm.meId == null
    BackHandler(enabled = vm.meId == null && !vm.booting && welcomed) { welcomed = false }
    BackHandler(enabled = vm.meId != null && vm.addingAccount) { vm.cancelAddAccount() }
    BackHandler(enabled = vm.meId != null && !vm.addingAccount && vm.route != Route.Home) {
        vm.route = backTargetOf(vm.route)
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when {
            vm.switching -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.accent)
            showSplash -> SplashScreen(loading = vm.booting)
            vm.meId == null && !welcomed -> WelcomeScreen { welcomed = true }
            vm.meId == null || vm.addingAccount -> AuthScreen(vm)
            else -> {
                val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                val screenW = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                val popFlag = remember { booleanArrayOf(false) }
                val peek = remember { BackPeek { backTargetOf(vm.route) } }

                // الشاشة السابقة تظهر أسفل الصفحة أثناء السحب للرجوع (Parallax + تعتيم)
                peek.route?.let { pr ->
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = dir * (-0.3f * screenW * (1f - peek.progress)) }) {
                        CompositionLocalProvider(LocalSkipAppear provides true, LocalBackPeek provides null) {
                            RouteContent(vm, pr)
                        }
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f * (1f - peek.progress))))
                    }
                }

                CompositionLocalProvider(LocalBackPeek provides peek) {
                    AnimatedContent(
                        targetState = vm.route,
                        transitionSpec = {
                            if (peek.swiped) {
                                // الرجوع تمّ بالسحب: لا حركة إضافية
                                peek.swiped = false
                                popFlag[0] = true
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val from = routeDepth(initialState)
                                val to = routeDepth(targetState)
                                popFlag[0] = to < from
                                val ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                                when {
                                    // Push: الشاشة الجديدة تدخل من الحافة، والقديمة تتراجع 30% (Parallax)
                                    to > from -> (slideInHorizontally(tween(380, easing = ease)) { it * dir } togetherWith
                                        slideOutHorizontally(tween(380, easing = ease)) { (-(it * 0.3f) * dir).toInt() })
                                        .apply { targetContentZIndex = 1f }
                                    // Pop: الحالية تخرج للحافة، والسابقة تعود من الـ Parallax
                                    to < from -> (slideInHorizontally(tween(380, easing = ease)) { (-(it * 0.3f) * dir).toInt() } togetherWith
                                        slideOutHorizontally(tween(380, easing = ease)) { it * dir })
                                        .apply { targetContentZIndex = -1f }
                                    else -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                }
                            }
                        },
                        label = "nav"
                    ) { r ->
                        val skipAppear = remember { popFlag[0] }
                        CompositionLocalProvider(LocalSkipAppear provides skipAppear) { RouteContent(vm, r) }
                    }
                }
            }
        }
    }
}

/** عمق الشاشة في مسار التنقل: الأعمق يدخل Push والأقل يعود Pop. */
private fun routeDepth(r: Route): Int = when (r) {
    Route.Home -> 0
    is Route.Conversation, Route.NewChat, Route.Me, Route.Archived, Route.Starred, Route.Settings, Route.WhatsApp -> 1
    is Route.Contact, is Route.SettingsPage, is Route.WaChat, Route.WaLink -> 2
    is Route.Media -> 3
}

/** الشاشة التي يُرجَع إليها من [r] (نفس منطق زر الرجوع). */
private fun backTargetOf(r: Route): Route = when (r) {
    is Route.Contact -> Route.Conversation(r.peer)
    is Route.Media -> Route.Contact(r.peer)
    is Route.SettingsPage -> Route.Settings
    is Route.WaChat, Route.WaLink -> Route.WhatsApp
    else -> Route.Home
}

@Composable
private fun RouteContent(vm: MainVM, r: Route) {
    when (r) {
        Route.Home -> HomeScreen(vm)
        is Route.Conversation -> ChatScreen(vm, r.peer)
        Route.NewChat -> NewChatScreen(vm)
        Route.Me -> ProfileScreen(vm)
        Route.Archived -> HomeScreen(vm, archivedView = true)
        Route.Starred -> StarredScreen(vm)
        is Route.Contact -> ContactScreen(vm, r.peer)
        is Route.Media -> MediaScreen(vm, r.peer)
        Route.Settings -> SettingsScreen(vm)
        is Route.SettingsPage -> SettingsPageScreen(vm, r.section)
        Route.WhatsApp -> WhatsAppScreen(vm)
        Route.WaLink -> WaLinkScreen(vm)
        is Route.WaChat -> WhatsAppChatScreen(vm, r.key)
    }
}