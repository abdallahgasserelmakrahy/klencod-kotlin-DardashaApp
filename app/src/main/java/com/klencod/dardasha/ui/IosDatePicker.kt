package com.klencod.dardasha.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs

private val ITEM_H = 40.dp
private const val MIN_YEAR = 1900

/**
 * منتقي تاريخ بأسلوب iOS: ثلاث عجلات (يوم / شهر / سنة) تتوقف على العنصر الأوسط بحركة Snap،
 * مع اهتزاز خفيف عند كل خطوة. لا يسمح بتاريخ بعد اليوم. يُستدعى داخل [IosPage] overlay.
 */
@Composable
internal fun BoxScope.IosDateSheet(
    visible: Boolean, title: String, initial: LocalDate?,
    onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit,
) {
    IosSheetFrame(visible, onDismiss) {
        DateSheetBody(title, initial, onDismiss, onConfirm)
    }
}

@Composable
private fun DateSheetBody(title: String, initial: LocalDate?, onCancel: () -> Unit, onDone: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val start = remember { (initial ?: today.minusYears(20)).let { if (it.isAfter(today)) today else it } }
    var year by remember { mutableIntStateOf(start.year) }
    var month by remember { mutableIntStateOf(start.monthValue) }
    var day by remember { mutableIntStateOf(start.dayOfMonth) }

    // القيم الفعلية بعد تقييدها (شهر/يوم غير موجود أو بعد اليوم)
    val maxMonth = if (year == today.year) today.monthValue else 12
    val m = month.coerceAtMost(maxMonth)
    val maxDay = if (year == today.year && m == today.monthValue) today.dayOfMonth else YearMonth.of(year, m).lengthOfMonth()
    val d = day.coerceAtMost(maxDay)

    val ar = remember { Locale("ar") }
    val years = remember(today) { (MIN_YEAR..today.year).map { it.toString() } }
    val months = remember(maxMonth) { (1..maxMonth).map { Month.of(it).getDisplayName(JTextStyle.FULL, ar) } }
    val days = remember(maxDay) { (1..maxDay).map { it.toString() } }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IOS.alertBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IosNavText("إلغاء", onClick = onCancel)
            Text(
                title, color = IOS.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            IosNavText("تم", bold = true) { onDone(LocalDate.of(year, m, d)) }
        }
        HorizontalDivider(color = IOS.alertSep, thickness = 0.5.dp)

        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            // شريط التحديد خلف العجلات
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().height(ITEM_H)
                    .background(IOS.fill, RoundedCornerShape(10.dp))
            )
            Row(Modifier.fillMaxWidth()) {
                key(maxDay) { Wheel(days, d - 1, { day = it + 1 }, Modifier.weight(0.8f)) }
                key(maxMonth) { Wheel(months, m - 1, { month = it + 1 }, Modifier.weight(1.4f)) }
                Wheel(years, year - MIN_YEAR, { year = MIN_YEAR + it }, Modifier.weight(1f))
            }
            // تلاشي الحواف العلوية والسفلية
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(ITEM_H * 1.6f)
                    .background(Brush.verticalGradient(listOf(IOS.alertBg, IOS.alertBg.copy(alpha = 0f))))
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(ITEM_H * 1.6f)
                    .background(Brush.verticalGradient(listOf(IOS.alertBg.copy(alpha = 0f), IOS.alertBg)))
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** عجلة واحدة: خمسة عناصر ظاهرة، العنصر الأوسط هو المحدّد. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(items: List<String>, initial: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { ITEM_H.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val selected by remember(items.size) {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0)
                .coerceIn(0, items.lastIndex)
        }
    }
    val onSel by rememberUpdatedState(onSelect)
    LaunchedEffect(items.size) {
        snapshotFlow { selected }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSel(it)
        }
    }
    LazyColumn(
        state = state, flingBehavior = fling, modifier = modifier.height(ITEM_H * 5),
        contentPadding = PaddingValues(vertical = ITEM_H * 2)
    ) {
        itemsIndexed(items) { i, text ->
            val dist = abs(i - selected)
            Box(
                Modifier.fillMaxWidth().height(ITEM_H).graphicsLayer {
                    alpha = when (dist) { 0 -> 1f; 1 -> 0.55f; 2 -> 0.25f; else -> 0.1f }
                    val sc = 1f - dist.coerceAtMost(2) * 0.07f
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text, color = IOS.label, fontSize = 22.sp, maxLines = 1,
                    fontWeight = if (dist == 0) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
