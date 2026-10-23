package com.klencod.dardasha.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.AppSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/** true داخل الشاشات الجذرية للتبويبات: لا زر رجوع ولا سحب للرجوع (مثل iOS). */
internal val LocalIsTabRoot = compositionLocalOf { false }

/** المساحة السفلية التي يغطيها الشريط العائم: تستخدمها القوائم كـ contentPadding لتمرّ تحت الزجاج. */
internal val LocalTabInset = compositionLocalOf { 0.dp }

/** ارتفاع كبسولة الشريط (قابل للتخصيص من الإعدادات > التخصيص والأحجام > شريط التبويبات). */
internal fun tabBarHeight(): Dp = AppSettings.tabHeight.coerceIn(44, 90).dp
private val BAR_PAD = 4.dp

/** المسافة بين أسفل الكبسولة وحافة الشاشة (iOS 26 يرفعها فوق مؤشر الشاشة الرئيسية). */
private fun barBottomGap(navBottom: Dp): Dp = ((navBottom - 6.dp).coerceAtLeast(12.dp) + AppSettings.tabBottomOffset.dp).coerceAtLeast(2.dp)

internal enum class IosTab(val label: String, val route: Route, val on: ImageVector, val off: ImageVector, val locked: Boolean) {
    Chats("الدردشات", Route.Home, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, true),
    WhatsApp("واتساب", Route.WhatsApp, Icons.Filled.QuestionAnswer, Icons.Outlined.QuestionAnswer, false),
    Starred("المميّزة", Route.Starred, Icons.Filled.Star, Icons.Outlined.StarBorder, false),
    Settings("الإعدادات", Route.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, true),
}

/** ترتيب كل التبويبات كما اختاره المستخدم (يشمل المخفية). */
internal fun orderedTabs(): List<IosTab> {
    val all = IosTab.entries
    val order = AppSettings.tabOrder.split(",").mapNotNull { it.trim().toIntOrNull() }
        .filter { it in all.indices }.distinct()
    return (order + all.indices.filter { it !in order }).map { all[it] }
}

internal fun isTabHidden(t: IosTab): Boolean = !t.locked && (AppSettings.tabHidden shr t.ordinal) and 1 == 1

/** التبويبات الظاهرة فعلاً في الشريط. */
internal fun visibleTabs(): List<IosTab> = orderedTabs().filter { !isTabHidden(it) }

/** التبويب الذي تنتمي إليه الشاشة، أو null إن لم تكن شاشة جذرية لتبويب ظاهر. */
internal fun tabOf(r: Route): IosTab? = visibleTabs().firstOrNull { it.route == r }

/**
 * إطار الشاشة: الشاشات الجذرية تمتد خلف الشريط العائم (ليظهر المحتوى من خلف الزجاج)،
 * وتُمرَّر لها المساحة عبر [LocalTabInset] لتضيفها كـ contentPadding في آخر القائمة.
 * عند ظهور الكيبورد يختفي الشريط ويُلغى الهامش.
 */
@Composable
internal fun TabRouteFrame(isTab: Boolean, content: @Composable () -> Unit) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inset = if (isTab && !imeOpen) tabBarHeight() + barBottomGap(navBottom) + 8.dp else 0.dp
    CompositionLocalProvider(LocalIsTabRoot provides isTab, LocalTabInset provides inset) {
        if (isTab) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (imeOpen) Modifier else Modifier.consumeWindowInsets(WindowInsets.navigationBars))
            ) { content() }
        } else content()
    }
}

// ───────────────────────── الخلفية الزجاجية (Backdrop Blur) ─────────────────────────

/**
 * يلتقط رسم المحتوى في [source] ثم يرسمه الشريط مموّهاً خلف الكبسولة (Liquid Glass حقيقي).
 * التمويه يعمل من Android 12 (API 31)؛ في الأقدم يُستخدم زجاج شبه معتم بدل التمويه.
 */
@Stable
internal class GlassBackdrop(val source: GraphicsLayer, val blurred: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var active by mutableStateOf(false)
    /** موضع الكبسولة في الجذر (يُحدَّث عند التخطيط، غير مراقَب). */
    var panelPos: Offset = Offset.Zero
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val s = rememberGraphicsLayer()
    val b = rememberGraphicsLayer()
    return remember(s, b) { GlassBackdrop(s, b) }
}

/** ضعه على حاوية المحتوى (خلف الشريط) ليصبح مصدر الخلفية الزجاجية. */
internal fun Modifier.glassSource(b: GlassBackdrop): Modifier = this
    .onGloballyPositioned { b.origin = it.positionInRoot() }
    .drawWithContent {
        if (b.active && Build.VERSION.SDK_INT >= 31) {
            b.source.record(this, layoutDirection, IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                this@drawWithContent.drawContent()
            }
            drawLayer(b.source)
        } else drawContent()
    }

/** سطح الزجاج: تمويه الخلفية + تشبّع + صبغة + إضاءة حافة (Specular Rim) — كلها قابلة للتخصيص. */
private fun Modifier.glassPanel(b: GlassBackdrop, dark: Boolean): Modifier =
    this
        .onGloballyPositioned { b.panelPos = it.positionInRoot() }
        .drawWithContent {
            val canBlur = b.active && Build.VERSION.SDK_INT >= 31
            val useLayer = canBlur && AppSettings.tabBlur > 0
            val w = size.width
            val h = size.height
            if (useLayer) {
                val r = AppSettings.tabBlur.coerceIn(1, 60).dp.toPx()
                b.blurred.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                b.blurred.colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(AppSettings.tabSaturation.coerceIn(0, 300) / 100f) }
                )
                b.blurred.clip = true
                b.blurred.record(this, layoutDirection, IntSize(w.roundToInt(), h.roundToInt())) {
                    val o = b.panelPos - b.origin
                    translate(-o.x, -o.y) { drawLayer(b.source) }
                }
                drawLayer(b.blurred)
            }
            // الصبغة: Regular Glass — فاتح/داكن حسب المظهر، وأكثر عتمة عند غياب التمويه
            val k = AppSettings.tabTint.coerceIn(0, 250) / 100f
            val translucent = canBlur
            val baseA = if (dark) (if (translucent) 0.34f else 0.92f) else (if (translucent) 0.46f else 0.94f)
            val tint = (if (dark) Color(0xFF1C1C1E) else Color.White).copy(alpha = (baseA * k).coerceIn(0f, 1f))
            drawRect(tint)
            // لمعة علوية ناعمة
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = ((if (dark) 0.10f else 0.38f) * k).coerceIn(0f, 1f)), Color.Transparent),
                    startY = 0f, endY = h * 0.65f
                )
            )
            drawContent()
            if (AppSettings.tabRim) {
                // حافة زجاجية: لمعة في الزاوية العلوية اليسرى تخفت نحو السفلية اليمنى
                val sw = 1.dp.toPx()
                val rad = h * AppSettings.tabCornerPct.coerceIn(0, 100) / 200f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.42f else 0.95f),
                            Color.White.copy(alpha = if (dark) 0.06f else 0.20f),
                            Color.White.copy(alpha = if (dark) 0.22f else 0.60f),
                        ),
                        start = Offset(0f, 0f), end = Offset(w, h)
                    ),
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = androidx.compose.ui.geometry.Size(w - sw, h - sw),
                    cornerRadius = CornerRadius((rad - sw / 2f).coerceAtLeast(0f)), style = Stroke(sw)
                )
                // خط خارجي خافت لتحديد الشكل فوق الخلفيات الفاتحة
                drawRoundRect(
                    color = Color.Black.copy(alpha = if (dark) 0.35f else 0.06f),
                    cornerRadius = CornerRadius(rad), style = Stroke(0.5.dp.toPx())
                )
            }
        }

/** لون التبويب المحدد حسب تفضيل المستخدم. */
@Composable
internal fun tabSelectedColor(): Color = when (AppSettings.tabSelColor) {
    1 -> C.accent
    2 -> IOS.label
    3 -> Color.hsl(AppSettings.tabCustomHue.coerceIn(0, 359).toFloat(), 0.85f, if (C.dark) 0.62f else 0.48f)
    else -> IOS.blue
}

// ───────────────────────── شريط التبويبات ─────────────────────────

/**
 * شريط التبويبات بأسلوب iOS 26 (Liquid Glass): كبسولة عائمة شفافة مموّهة،
 * عدسة اختيار تنزلق بنابض وتتمدد مع السرعة، وتكبر وتلمع عند اللمس/السحب،
 * ويمكن سحب الإصبع عبر التبويبات. يختفي (ينزلق للأسفل) عند فتح صفحة داخلية أو الكيبورد.
 */
@Composable
internal fun IosTabBar(
    selected: IosTab?,
    enabled: Boolean,
    chatsBadge: Int,
    backdrop: GlassBackdrop,
    onSelect: (IosTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gap = barBottomGap(navBottom)
    val density = LocalDensity.current
    val tabs = visibleTabs()
    val visible = selected != null && selected in tabs && !imeOpen

    val vis by animateFloatAsState(if (visible) 1f else 0f, spring(0.86f, 380f), label = "barVis")
    SideEffect { backdrop.active = vis > 0.01f }
    if (vis <= 0.001f && !visible) return

    // آخر تبويب نشط (يبقى للعدسة أثناء الانزلاق خارج الشاشة)
    val lastSel = remember { intArrayOf(0) }
    selected?.let { s -> tabs.indexOf(s).takeIf { it >= 0 }?.let { lastSel[0] = it } }
    val selIdx = lastSel[0].coerceIn(0, tabs.size - 1)

    val totalPx = with(density) { (tabBarHeight() + gap + 24.dp).toPx() }
    val dark = C.dark
    val bg = C.bg

    Box(
        modifier.fillMaxWidth()
            .graphicsLayer { translationY = (1f - vis) * totalPx; alpha = vis.coerceIn(0f, 1f) },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scroll Edge Effect: المحتوى يخفت قرب الشريط بدل شريط معتم
        Box(
            Modifier.fillMaxWidth().height(tabBarHeight() + gap + 28.dp)
                .drawWithContent {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, bg.copy(alpha = if (dark) 0.78f else 0.70f)),
                            startY = 0f, endY = size.height * 0.82f
                        )
                    )
                }
        )
        GlassTabBar(
            tabs = tabs, selIdx = selIdx, enabled = enabled, badge = chatsBadge, backdrop = backdrop,
            onPick = { onSelect(tabs[it.coerceIn(0, tabs.size - 1)]) },
            modifier = Modifier.padding(horizontal = AppSettings.tabSideMargin.dp).padding(bottom = gap)
        )
    }
}

/**
 * كبسولة الزجاج نفسها (العدسة + العناصر + إيماءات السحب). تُستخدم في الشريط الفعلي
 * وفي المعاينة الحيّة داخل الإعدادات. [onPick] يُستدعى بفهرس التبويب الجديد.
 */
@Composable
internal fun GlassTabBar(
    tabs: List<IosTab>,
    selIdx: Int,
    enabled: Boolean,
    badge: Int,
    backdrop: GlassBackdrop,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dark = C.dark
    val n = tabs.size.coerceAtLeast(1)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val sel = selIdx.coerceIn(0, n - 1)
    val cornerPct = AppSettings.tabCornerPct.coerceIn(0, 100)
    val shape = RoundedCornerShape(percent = cornerPct / 2)
    val lensStyle = AppSettings.tabLens
    val selTint = tabSelectedColor()

    val anim = remember { Animatable(sel.toFloat()) }
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var downSlot by remember { mutableIntStateOf(-1) }
    var settle by remember { mutableStateOf<Settle?>(null) }
    val lensSpec = spring<Float>(dampingRatio = 0.68f, stiffness = 320f)

    LaunchedEffect(sel, n) { if (dragPos == null) anim.animateTo(sel.toFloat(), lensSpec) }
    LaunchedEffect(settle) { settle?.let { anim.snapTo(it.from); anim.animateTo(it.to.toFloat(), lensSpec) } }

    val pos = (dragPos ?: anim.value).coerceIn(0f, (n - 1).toFloat())
    val grow by animateFloatAsState(
        if (lensStyle == 0 && downSlot >= 0 && (dragPos != null || downSlot == sel)) 1f else 0f,
        spring(0.6f, 520f), label = "lensGrow"
    )

    fun pick(i: Int) {
        val idx = i.coerceIn(0, n - 1)
        if (idx != sel && enabled) {
            if (AppSettings.tabHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPick(idx)
        }
    }
    val pickNow by rememberUpdatedState { i: Int -> pick(i) }

    Box(
        modifier.widthIn(max = 520.dp).fillMaxWidth().height(tabBarHeight())
            .then(
                if (AppSettings.tabShadow) Modifier.shadow(
                    20.dp, shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.50f else 0.22f)
                ) else Modifier
            )
            .clip(shape)
            .glassPanel(backdrop, dark)
            .pointerInput(enabled, n, rtl) {
                awaitEachGesture {
                    val barW = size.width.toFloat()
                    val pad = BAR_PAD.toPx()
                    val itemW = (barW - 2 * pad) / n
                    // الموضع الكسري (0 = مركز أول عنصر) من إحداثية X
                    fun slotCenter(x: Float): Float {
                        val s = if (rtl) (barW - pad - x) / itemW else (x - pad) / itemW
                        return s - 0.5f
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startSlot = slotCenter(down.position.x).roundToInt().coerceIn(0, n - 1)
                    downSlot = startSlot
                    val startX = down.position.x
                    var dragging = false
                    var last = down.position.x
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) { last = ch.position.x; break }
                        last = ch.position.x
                        if (!dragging && abs(last - startX) > touchSlop && enabled) {
                            dragging = true
                            dragPos = anim.value
                        }
                        if (dragging) {
                            dragPos = slotCenter(last).coerceIn(0f, (n - 1).toFloat())
                            ch.consume()
                        }
                    }
                    if (dragging) {
                        val from = dragPos ?: anim.value
                        val target = from.roundToInt().coerceIn(0, n - 1)
                        settle = Settle(from, target)
                        dragPos = null
                        pickNow(target)
                    } else {
                        pickNow(slotCenter(last).roundToInt().coerceIn(0, n - 1))
                    }
                    downSlot = -1
                }
            }
    ) {
        // ── عدسة الاختيار (Liquid Lens) ──
        if (lensStyle != 2) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithBarSize { barW, barH ->
                val pad = with(density) { BAR_PAD.toPx() }
                val itemW = (barW - 2 * pad) / n
                val lensH = barH - 2 * pad
                val cx = if (rtl) barW - pad - (pos + 0.5f) * itemW else pad + (pos + 0.5f) * itemW
                val speed = if (dragPos == null && AppSettings.tabStretch) abs(anim.velocity) else 0f
                val stretch = (speed * 0.035f).coerceAtMost(0.28f)
                val idleFill = when {
                    lensStyle == 1 -> selTint.copy(alpha = 0.16f)
                    dark -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Black.copy(alpha = 0.075f)
                }
                val liveFill = if (dark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.62f)
                Box(
                    Modifier
                        .size(with(density) { itemW.toDp() }, with(density) { lensH.toDp() })
                        .offset { IntOffset((cx - itemW / 2f).roundToInt(), pad.roundToInt()) }
                        .graphicsLayer {
                            val s = 1f + 0.14f * grow
                            scaleX = s * (1f + stretch)
                            scaleY = (1f + 0.12f * grow) * (1f - stretch * 0.35f)
                            shadowElevation = 10.dp.toPx() * grow
                            this.shape = shape
                            clip = false
                            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
                            spotShadowColor = Color.Black.copy(alpha = 0.30f)
                        }
                        .clip(shape)
                        .drawWithContent {
                            drawRect(lerp(idleFill, liveFill, grow))
                            if (grow > 0.01f) {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.30f * grow), Color.Transparent)
                                    )
                                )
                            }
                            if (lensStyle == 0) {
                                val sw = 1.dp.toPx()
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = (if (dark) 0.35f else 0.9f) * (0.35f + 0.65f * grow)),
                                            Color.White.copy(alpha = 0.05f),
                                            Color.White.copy(alpha = (if (dark) 0.18f else 0.55f) * (0.35f + 0.65f * grow)),
                                        ),
                                        start = Offset.Zero, end = Offset(size.width, size.height)
                                    ),
                                    topLeft = Offset(sw / 2f, sw / 2f),
                                    size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                                    cornerRadius = CornerRadius(
                                        (size.height * cornerPct / 200f - sw / 2f).coerceAtLeast(0f)
                                    ), style = Stroke(sw)
                                )
                            }
                        }
                )
            }
        }

        // ── العناصر ──
        Row(Modifier.fillMaxSize().padding(horizontal = BAR_PAD)) {
            tabs.forEachIndexed { i, tab ->
                val closeness = (1f - abs(pos - i)).coerceIn(0f, 1f)
                TabItem(
                    tab = tab, closeness = closeness, dark = dark, selTint = selTint,
                    pressed = downSlot == i && dragPos == null && i != sel,
                    selectedNow = i == sel && dragPos == null,
                    badge = if (tab == IosTab.Chats && AppSettings.tabBadge) badge else 0,
                    modifier = Modifier.weight(1f),
                    onClick = { pickNow(i) }
                )
            }
        }
    }
}

private class Settle(val from: Float, val to: Int)

/** يعطي أبعاد الحاوية بالبكسل دون التأثير على التخطيط. */
@Composable
private fun BoxWithBarSize(content: @Composable (barW: Float, barH: Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        with(LocalDensity.current) { content(maxWidth.toPx(), maxHeight.toPx()) }
    }
}

@Composable
private fun TabItem(
    tab: IosTab, closeness: Float, dark: Boolean, selTint: Color, pressed: Boolean, selectedNow: Boolean,
    badge: Int, modifier: Modifier, onClick: () -> Unit,
) {
    val idle = if (dark) Color(0xFFEBEBF5).copy(alpha = 0.62f) else Color(0xFF3C3C43).copy(alpha = 0.62f)
    val tint = lerp(idle, selTint, closeness)
    val press by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.7f, 600f), label = "tabPress")
    val labelMode = AppSettings.tabLabelMode

    // ارتداد نابضي للأيقونة عند الوصول للتبويب (ليس عند أول ظهور)
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selectedNow) {
        if (first) { first = false; return@LaunchedEffect }
        if (selectedNow && AppSettings.tabBounce) {
            bounce.snapTo(0.80f)
            bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 380f))
        }
    }

    Column(
        modifier.fillMaxHeight().semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selectedNow
            onClick(label = tab.label) { onClick(); true }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { val s = bounce.value * press; scaleX = s; scaleY = s }) {
            Icon(
                if (closeness > 0.5f) tab.on else tab.off, tab.label, tint = tint,
                modifier = Modifier.size(AppSettings.tabIconSize.coerceIn(16, 40).dp)
            )
            if (badge > 0) {
                if (AppSettings.tabBadgeStyle == 1) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
                            .size(10.dp).background(IOS.red, CircleShape)
                    )
                } else Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(IOS.red, RoundedCornerShape(9.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }
        if (labelMode != 2) Text(
            tab.label, color = tint, fontSize = AppSettings.tabLabelSize.coerceIn(7, 16).sp,
            fontWeight = if (closeness > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).graphicsLayer { if (labelMode == 1) alpha = closeness }
        )
    }
}
