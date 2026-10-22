package com.klencod.dardasha.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════
//  مكوّنات iOS إضافية (HIG): Activity Indicator · أزرار · قائمة سياقية · Sheet · Action Sheet
// ════════════════════════════════════════════════════════════════════════════

/** مؤشّر النشاط الأصلي في iOS: 12 خطاً تدور بخطوات منفصلة (بدل الدائرة الدوّارة في Material). */
@Composable
internal fun IosSpinner(modifier: Modifier = Modifier, color: Color = IOS.secondary) {
    val t = rememberInfiniteTransition(label = "spinner")
    val step by t.animateFloat(
        0f, 12f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "spinStep"
    )
    Canvas(modifier.size(20.dp)) {
        val cur = step.toInt() % 12
        val r = size.minDimension / 2f
        val inner = r * 0.52f
        val sw = r * 0.16f
        for (i in 0 until 12) {
            // الخط الحالي أقوى ما يكون، ويخفت ما خلفه تدريجياً
            val age = (cur - i + 12) % 12
            val a = (1f - age / 12f).coerceIn(0.18f, 1f)
            val ang = Math.toRadians((i * 30 - 90).toDouble())
            val dx = Math.cos(ang).toFloat()
            val dy = Math.sin(ang).toFloat()
            drawLine(
                color.copy(alpha = color.alpha * a),
                Offset(center.x + dx * inner, center.y + dy * inner),
                Offset(center.x + dx * r * 0.95f, center.y + dy * r * 0.95f),
                strokeWidth = sw, cap = StrokeCap.Round
            )
        }
    }
}

/** شريط تقدّم رفيع بأسلوب iOS (مسار رمادي + تعبئة مدوّرة). */
@Composable
internal fun IosProgress(progress: Float, modifier: Modifier = Modifier, color: Color = IOS.blue, track: Color = IOS.fill) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "iprog")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.height(4.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = p; transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
        }.background(color, CircleShape))
    }
}

/** زر أيقونة بأسلوب iOS: بلا موجة Ripple، يخفت عند الضغط (يشبه توقيع IconButton ليسهل الاستبدال). */
@Composable
internal fun IosIconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (!enabled) 0.3f else if (pressed) 0.35f else 1f, tween(if (pressed) 40 else 220), label = "ibA")
    Box(
        modifier.size(44.dp).alpha(a).clickable(src, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** زر نصّي بأسلوب iOS (بدل TextButton). */
@Composable
internal fun IosTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (!enabled) 0.3f else if (pressed) 0.35f else 1f, tween(if (pressed) 40 else 220), label = "itbA")
    Row(
        modifier.alpha(a).clickable(src, null, enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** زر دائري مملوء (مثل زر «إلى آخر رسالة» وزر الإرسال) مع انكماش بنابض عند الضغط. */
@Composable
internal fun IosFilledCircleButton(
    onClick: () -> Unit, container: Color, modifier: Modifier = Modifier, size: Dp = 36.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.6f, 700f), label = "ifcS")
    Box(
        modifier.size(size).graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape).background(container).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

// ───────────────────────── القائمة السياقية (Menu) ─────────────────────────

class IosMenuEntry(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * قائمة منبثقة بأسلوب iOS: تتمدّد من زاوية الزر بنابض، صفوف 44pt، أيقونة في النهاية، فواصل رفيعة.
 * تُوضَع داخل Box بجانب الزر المُطلِق. العناصر الفارغة (null) تُتجاهل.
 */
@Composable
internal fun IosMenu(
    expanded: Boolean, onDismiss: () -> Unit, entries: List<IosMenuEntry?>, width: Dp = 250.dp,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (state.currentState || state.targetState) {
        val items = entries.filterNotNull()
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val density = LocalDensity.current
        val pad = with(density) { 20.dp.roundToPx() }       // هامش لظل القائمة داخل نافذة الـ Popup
        val gap = with(density) { 6.dp.roundToPx() }
        val margin = with(density) { 8.dp.roundToPx() }
        val provider = remember(rtl, pad, gap, margin) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize
                ): IntOffset {
                    val w = popupContentSize.width - 2 * pad
                    val h = popupContentSize.height - 2 * pad
                    val left = (if (rtl) anchorBounds.left else anchorBounds.right - w)
                        .coerceIn(margin, (windowSize.width - w - margin).coerceAtLeast(margin))
                    val top = (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - h - margin).coerceAtLeast(margin))
                    return IntOffset(left - pad, top - pad)
                }
            }
        }
        val origin = TransformOrigin(if (rtl) 0.1f else 0.9f, 0.05f)
        val haptic = LocalHapticFeedback.current
        Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
            AnimatedVisibility(
                visibleState = state,
                enter = scaleIn(spring(0.78f, 520f), initialScale = 0.45f, transformOrigin = origin) + fadeIn(tween(140)),
                exit = scaleOut(tween(140), targetScale = 0.92f, transformOrigin = origin) + fadeOut(tween(120)),
            ) {
                Box(Modifier.padding(20.dp)) {
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.width(width).shadow(18.dp, shape, ambientColor = Color.Black.copy(0.25f), spotColor = Color.Black.copy(0.35f))
                            .clip(shape).background(IOS.menuBg)
                    ) {
                        items.forEachIndexed { i, e ->
                            val c = if (e.danger) IOS.red else IOS.label
                            Box(Modifier.fillMaxWidth().pressRow {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                e.onClick()
                            }) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(e.label, color = c, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                    Icon(e.icon, null, tint = c, modifier = Modifier.size(22.dp))
                                }
                                if (i < items.lastIndex) HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = IOS.separator, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Sheet و Action Sheet ─────────────────────────

/**
 * هيكل مشترك لكل الأوراق السفلية: نافذة بملء الشاشة + تعتيم يتلاشى + انزلاق من الأسفل بنابض.
 * [content] يستقبل دالة [close] لإغلاق بحركة (ثم يُستدعى [onDismiss]).
 */
@Composable
internal fun IosSheetHost(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismissNow by rememberUpdatedState(onDismiss)
    val close: () -> Unit = {
        if (!closing) {
            closing = true; shown = false
            scope.launch { delay(240); dismissNow() }
        }
    }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(shown, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).tap(close))
            }
            AnimatedVisibility(
                shown, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(0.88f, 380f)) { it },
                exit = slideOutVertically(tween(230)) { it },
            ) {
                Column(Modifier.fillMaxWidth()) { content(close) }
            }
        }
    }
}

/**
 * ورقة (Sheet) بأسلوب iOS: زوايا علوية 20pt، مقبض سحب، عنوان وسطي اختياري مع زرّين في الطرفين،
 * وسحب لأسفل من الرأس لإغلاقها.
 */
@Composable
internal fun IosSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    leading: (@Composable (close: () -> Unit) -> Unit)? = null,
    trailing: (@Composable (close: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    IosSheetHost(onDismiss) { close ->
        val scope = rememberCoroutineScope()
        val off = remember { Animatable(0f) }
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(0, off.value.roundToInt()) }
                .clip(shape).background(Grouped)
        ) {
            Column(
                Modifier.fillMaxWidth().draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d -> scope.launch { off.snapTo((off.value + d).coerceAtLeast(0f)) } },
                    onDragStopped = { v ->
                        if (off.value > 140f || v > 1400f) close()
                        else scope.launch { off.animateTo(0f, spring(0.8f, 420f)) }
                    }
                )
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(IOS.tertiary))
                }
                if (title != null || leading != null || trailing != null) {
                    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
                        if (title != null) Text(
                            title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        if (leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading(close) }
                        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing(close) }
                    }
                } else Box(Modifier.height(8.dp))
            }
            Column(Modifier.fillMaxWidth().heightIn(max = maxH).navigationBarsPadding()) { content(close) }
        }
    }
}

class IosAction(val label: String, val destructive: Boolean = false, val checked: Boolean = false, val onClick: () -> Unit)

/** Action Sheet الأصلي: مجموعة إجراءات في بطاقة مدوّرة + زر «إلغاء» منفصل. */
@Composable
internal fun IosActionSheet(
    onDismiss: () -> Unit, actions: List<IosAction>,
    title: String? = null, message: String? = null, cancelText: String = "إلغاء",
) {
    val haptic = LocalHapticFeedback.current
    IosSheetHost(onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IOS.alertBg)) {
                val hasHeader = title != null || message != null
                if (hasHeader) Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (title != null) Text(title, color = IOS.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    if (message != null) Text(
                        message, color = IOS.secondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = if (title != null) 2.dp else 0.dp)
                    )
                }
                actions.forEachIndexed { i, a ->
                    if (i > 0 || hasHeader) HorizontalDivider(color = IOS.alertSep, thickness = 0.5.dp)
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).pressRow {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            a.onClick(); close()
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            a.label, color = if (a.destructive) IOS.red else IOS.blue, fontSize = 20.sp,
                            fontWeight = if (a.checked) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (a.checked) Icon(
                            Icons.Filled.Check, null, tint = IOS.blue,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(22.dp)
                        )
                    }
                }
            }
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (pressed) IOS.pressed else IOS.alertBg).clickable(src, null) { close() },
                contentAlignment = Alignment.Center
            ) { Text(cancelText, color = IOS.blue, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
