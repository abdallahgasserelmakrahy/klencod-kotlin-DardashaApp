package com.klencod.dardasha.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.Route
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────── ألوان iOS لقوائم "Inset Grouped" ─────────────
internal val Grouped: Color get() = if (C.dark) IOS.bg else IOS.bar
internal val CellBg: Color get() = if (C.dark) IOS.bar else IOS.bg
internal val IosGreen: Color get() = if (C.dark) Color(0xFF30D158) else Color(0xFF34C759)

/** تظليل الضغط على الصف بأسلوب iOS (بدل الموجة Ripple). */
internal fun Modifier.pressRow(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.background(if (pressed && enabled) IOS.pressed else Color.Transparent)
        .clickable(src, null, enabled = enabled, onClick = onClick)
}

/**
 * يتيح للمضيف (MainActivity) عرض الشاشة السابقة أسفل الصفحة أثناء السحب للرجوع.
 * [resolver] يعيد الشاشة التي سيُرجَع إليها.
 */
internal class BackPeek(val resolver: () -> Route?) {
    var route by mutableStateOf<Route?>(null)
    var progress by mutableFloatStateOf(0f)
    /** true بعد إكمال السحب: يتخطى المضيف حركة الرجوع لأن الانتقال تمّ بالفعل. */
    var swiped = false
    fun begin() { route = resolver(); progress = 0f; swiped = false }
    fun end() { route = null; progress = 0f }
}

internal val LocalBackPeek = compositionLocalOf<BackPeek?> { null }

/** شكل صف داخل قائمة: زوايا مدورة للأول والأخير فقط (للقوائم الكسولة). */
internal fun rowShape(i: Int, n: Int, r: Dp = 12.dp) = RoundedCornerShape(
    topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
    bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp,
)

/**
 * صفحة بأسلوب iOS: سحب من الحافة للرجوع (يتبع اتجاه RTL). انتقال Push/Pop يتولاه المضيف في MainActivity.
 * [overlay] طبقة فوق الصفحة بملء الشاشة (للـ Action Sheets).
 */
@Composable
internal fun IosPage(
    onBack: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val screenW = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val dragA = remember { Animatable(0f) }
    val peek = LocalBackPeek.current

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationX = dir * dragA.value }
            .background(Grouped)
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            content()
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp).pointerInput(screenW, dir, peek) {
                    detectHorizontalDragGestures(
                        onDragStart = { peek?.begin() },
                        onDragEnd = {
                            scope.launch {
                                if (dragA.value > screenW * 0.33f) {
                                    dragA.animateTo(screenW, tween(200)) { peek?.progress = value / screenW }
                                    peek?.swiped = true
                                    back()
                                    peek?.end()
                                } else {
                                    dragA.animateTo(0f, spring(0.8f, 400f)) { peek?.progress = value / screenW }
                                    peek?.end()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragA.animateTo(0f, spring(0.8f, 400f)) { peek?.progress = value / screenW }
                                peek?.end()
                            }
                        },
                    ) { change, dx ->
                        change.consume()
                        scope.launch {
                            dragA.snapTo((dragA.value + dx * dir).coerceAtLeast(0f))
                            peek?.progress = dragA.value / screenW
                        }
                    }
                }
            )
        }
        overlay()
    }
}

/** شريط التنقل: رجوع (أزرق) + عنوان وسطي + عنصر اختياري في النهاية + فاصل يظهر عند التمرير. */
@Composable
internal fun IosTopBar(
    title: String, onBack: (() -> Unit)?, separatorVisible: Boolean, backLabel: String = "رجوع",
    trailing: (@Composable () -> Unit)? = null, titleAlpha: Float = 1f,
) {
    val sepAlpha by animateFloatAsState(if (separatorVisible) 1f else 0f, tween(150), label = "sep")
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Text(
            title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).alpha(titleAlpha)
        )
        if (onBack != null) {
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            Row(
                Modifier.align(Alignment.CenterStart).alpha(if (pressed) 0.35f else 1f)
                    .clickable(src, null, onClick = onBack)
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, backLabel, tint = IOS.blue, modifier = Modifier.size(30.dp))
                Text(backLabel, color = IOS.blue, fontSize = 17.sp)
            }
        }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).padding(horizontal = 16.dp)) { trailing() }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter).alpha(sepAlpha), color = IOS.separator, thickness = 0.5.dp)
    }
}

@Composable
internal fun IosNavText(text: String, enabled: Boolean = true, bold: Boolean = false, color: Color = IOS.blue, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Text(
        text, color = if (enabled) color else IOS.tertiary, fontSize = 17.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.alpha(if (pressed && enabled) 0.35f else 1f)
            .clickable(src, null, enabled = enabled, onClick = onClick).padding(vertical = 10.dp)
    )
}

/** true عند الوصول للشاشة بالرجوع (Pop): لا نعيد حركة الظهور المتدرّج. */
internal val LocalSkipAppear = compositionLocalOf { false }

/** ظهور متدرّج: شفافية + انزلاق خفيف بنابض. */
@Composable
internal fun Appear(index: Int, content: @Composable () -> Unit) {
    val skip = LocalSkipAppear.current
    val a = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) { if (!skip) { delay(70L + index * 50L); a.animateTo(1f, spring(0.85f, 300f)) } }
    Box(Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 26.dp.toPx() }) { content() }
}

@Composable
internal fun IGroup(header: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) Text(
            header, color = IOS.secondary, fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CellBg), content = content)
    }
}

@Composable
internal fun IFooter(text: String, color: Color = IOS.secondary, alignEnd: Boolean = false) {
    Text(
        text, color = color, fontSize = 13.sp,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/** خلية قابلة للضغط مع تظليل iOS وفاصل داخلي (Inset). */
@Composable
internal fun RowBox(last: Boolean, inset: Dp, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressRow(onClick = onClick) else Modifier)
    ) {
        content()
        if (!last) HorizontalDivider(
            Modifier.align(Alignment.BottomStart).padding(start = inset),
            color = IOS.separator, thickness = 0.5.dp
        )
    }
}

@Composable
internal fun IRow(
    icon: ImageVector, tint: Color, title: String, last: Boolean,
    value: String? = null, chevron: Boolean = false, trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    RowBox(last, inset = 58.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(30.dp).background(tint, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = IOS.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = IOS.secondary, fontSize = 17.sp)
            if (trailing != null) trailing()
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = IOS.tertiary, modifier = Modifier.size(20.dp))
        }
    }
}

/** مفتاح iOS الأصلي: 51×31، أخضر، إبهام أبيض بنابض، يتبع اتجاه RTL. */
@Composable
internal fun ISwitch(checked: Boolean) {
    val bias by animateFloatAsState(if (checked) 1f else -1f, spring(0.7f, 500f), label = "swb")
    val track by animateColorAsState(if (checked) IosGreen else IOS.fill, tween(250), label = "swc")
    Box(
        Modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(50)).background(track),
        contentAlignment = BiasAlignment(bias, 0f)
    ) {
        Box(Modifier.padding(2.dp).size(27.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Segmented Control بأسلوب iOS مع مؤشر ينزلق بنابض. */
@Composable
internal fun IosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(IOS.fill).padding(2.dp)
    ) {
        val segW = maxWidth / options.size
        val off by animateDpAsState(segW * selected, spring(0.8f, 500f), label = "seg")
        Box(
            Modifier.offset(x = off).width(segW).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .background(if (C.dark) Color(0xFF636366) else Color.White, RoundedCornerShape(7.dp))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, t ->
                Box(Modifier.weight(1f).fillMaxHeight().tap { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(
                        t, color = IOS.label, fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** إطار Action Sheet عام: خلفية معتمة + انزلاق من الأسفل بنابض + زر رجوع النظام يغلقه. */
@Composable
internal fun BoxScope.IosSheetFrame(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(enabled = visible) { onDismiss() }
    androidx.compose.animation.AnimatedVisibility(
        visible, enter = androidx.compose.animation.fadeIn(tween(200)), exit = androidx.compose.animation.fadeOut(tween(200))
    ) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).tap(onDismiss)) }
    androidx.compose.animation.AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter),
        enter = androidx.compose.animation.slideInVertically(spring(0.85f, 380f)) { it } + androidx.compose.animation.fadeIn(tween(150)),
        exit = androidx.compose.animation.slideOutVertically(tween(220)) { it } + androidx.compose.animation.fadeOut(tween(200)),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content
        )
    }
}
