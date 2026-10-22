package com.klencod.dardasha.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/** ألوان وأدوات شاشة المحادثة (iMessage: أسود/أبيض نقي، أزرق النظام، رمادي النظام). */
internal object WA {
    private val d: Boolean get() = C.dark
    /** خلفية الرسائل: أسود/أبيض نقي مثل iMessage (بلا رسومات). */
    val wall: Color get() = IOS.bg
    /** شريط العنوان وشريط الكتابة. */
    val bar: Color get() = IOS.bg
    /** حقل الكتابة. */
    val field: Color get() = IOS.bg
    /** أيقونات شريط الكتابة (رمادي النظام). */
    val icon: Color get() = if (d) Color(0xFFAEAEB2) else Color(0xFF8E8E93)
    /** كبسولة التاريخ / فاصل الرسائل غير المقروءة. */
    val chip: Color get() = if (d) Color(0xE61C1C1E) else Color(0xE6F2F2F7)
    val chipText: Color get() = if (d) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    /** مادة «Liquid Glass» للأزرار العائمة: رمادي شبه شفاف + حدّ رفيع. */
    val glass: Color get() = if (d) Color(0xCC2C2C2E) else Color(0xCCF2F2F7)
    val glassStroke: Color get() = if (d) Color(0x1FFFFFFF) else Color(0x14000000)
    /** فقاعة المُرسِل: أزرق iMessage، والمستلم: رمادي النظام. */
    val sent: Color get() = IOS.blue
    val received: Color get() = IOS.bubbleIn
}

/** زر زجاجي (Liquid Glass) بأسلوب iOS 26: كبسولة شبه شفافة، حدّ رفيع، انكماش بنابض عند الضغط. */
@Composable
internal fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.6f, 700f), label = "glassS")
    Row(
        modifier.height(40.dp).widthIn(min = 40.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .shadow(if (C.dark) 0.dp else 2.dp, CircleShape, clip = false)
            .clip(CircleShape).background(WA.glass).border(0.5.dp, WA.glassStroke, CircleShape)
            .clickable(src, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/** فقاعة «يكتب الآن…»: ثلاث نقاط تنبض بالتتابع داخل فقاعة رمادية (مثل iMessage). */
@Composable
internal fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    Row(
        Modifier.padding(horizontal = 14.dp).bubbleTail(true, false, WA.received)
            .clip(shape).background(WA.received).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val a by t.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(tween(520), RepeatMode.Reverse, StartOffset(i * 170)), label = "dot$i"
            )
            Box(Modifier.size(8.dp).alpha(a).background(IOS.gray, CircleShape))
        }
    }
}

/**
 * خلفية رسومات خفيفة (Doodles) مرسومة بالكود: أشكال بسيطة موزّعة بشكل شبه عشوائي
 * بشفافية منخفضة جداً كي لا تؤثر على قراءة الرسائل.
 */
@Composable
internal fun ChatWallpaper(modifier: Modifier = Modifier) {
    val ink = if (C.dark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.055f)
    Canvas(modifier) {
        val cell = 60.dp.toPx()
        val u = 1.1.dp.toPx()
        val stroke = Stroke(width = 1.3.dp.toPx())
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (r in 0 until rows) for (c in 0 until cols) {
            var h = r * 73856093 xor c * 19349663 xor 0x5bd1e995
            h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
            val hh = h and 0x7fffffff
            val cx = c * cell + (if (r % 2 == 0) 0f else cell / 2) + ((hh % 21) - 10) * 1.2f
            val cy = r * cell + (((hh / 21) % 21) - 10) * 1.2f
            val center = Offset(cx, cy)
            rotate((hh % 360).toFloat(), center) { doodle((hh / 441) % 8, center, u, ink, stroke) }
        }
    }
}

private fun DrawScope.doodle(kind: Int, c: Offset, u: Float, ink: Color, stroke: Stroke) {
    when (kind) {
        0 -> drawCircle(ink, 6.5f * u, c, style = stroke)
        1 -> { // علامة +
            drawLine(ink, Offset(c.x - 6 * u, c.y), Offset(c.x + 6 * u, c.y), stroke.width)
            drawLine(ink, Offset(c.x, c.y - 6 * u), Offset(c.x, c.y + 6 * u), stroke.width)
        }
        2 -> { // نجمة رباعية
            val p = Path().apply {
                moveTo(c.x, c.y - 8 * u); quadraticTo(c.x, c.y, c.x + 8 * u, c.y)
                quadraticTo(c.x, c.y, c.x, c.y + 8 * u); quadraticTo(c.x, c.y, c.x - 8 * u, c.y)
                quadraticTo(c.x, c.y, c.x, c.y - 8 * u); close()
            }
            drawPath(p, ink, style = stroke)
        }
        3 -> { // موجة
            val p = Path().apply {
                moveTo(c.x - 9 * u, c.y)
                cubicTo(c.x - 5 * u, c.y - 7 * u, c.x - 2 * u, c.y - 7 * u, c.x, c.y)
                cubicTo(c.x + 2 * u, c.y + 7 * u, c.x + 5 * u, c.y + 7 * u, c.x + 9 * u, c.y)
            }
            drawPath(p, ink, style = stroke)
        }
        4 -> { // قلب
            val p = Path().apply {
                moveTo(c.x, c.y + 6 * u)
                cubicTo(c.x - 9 * u, c.y - 1 * u, c.x - 5 * u, c.y - 8 * u, c.x, c.y - 3 * u)
                cubicTo(c.x + 5 * u, c.y - 8 * u, c.x + 9 * u, c.y - 1 * u, c.x, c.y + 6 * u)
                close()
            }
            drawPath(p, ink, style = stroke)
        }
        5 -> { // مثلث
            val p = Path().apply {
                moveTo(c.x, c.y - 7 * u); lineTo(c.x + 7 * u, c.y + 5 * u); lineTo(c.x - 7 * u, c.y + 5 * u); close()
            }
            drawPath(p, ink, style = stroke)
        }
        6 -> drawRoundRect(
            ink, Offset(c.x - 6 * u, c.y - 6 * u), Size(12 * u, 12 * u),
            androidx.compose.ui.geometry.CornerRadius(3 * u), style = stroke
        )
        else -> { // حلقتان
            drawCircle(ink, 4 * u, Offset(c.x - 4 * u, c.y), style = stroke)
            drawCircle(ink, 4 * u, Offset(c.x + 4 * u, c.y), style = stroke)
        }
    }
}

/**
 * ذيل الفقاعة بأسلوب iMessage: ينحني للخارج عند الزاوية السفلية لآخر رسالة في المجموعة.
 * الزاوية السفلية الخارجية للفقاعة تكون صغيرة (4dp) ويكمّلها هذا الانحناء.
 */
internal fun Modifier.bubbleTail(show: Boolean, mine: Boolean, color: Color): Modifier =
    if (!show) this else drawBehind {
        val rtl = layoutDirection == LayoutDirection.Rtl
        // «البداية» للرسائل المرسلة، «النهاية» للواردة
        val right = if (mine) rtl else !rtl
        val d = density
        val w = size.width
        val h = size.height
        val tip = 7f * d
        val p = Path()
        if (right) {
            p.moveTo(w - 10f * d, h - 12f * d)
            p.lineTo(w, h - 12f * d)
            p.lineTo(w, h - 8f * d)
            p.cubicTo(w, h - 2.5f * d, w + 2.5f * d, h - 0.8f * d, w + tip, h)
            p.lineTo(w - 10f * d, h)
        } else {
            p.moveTo(10f * d, h - 12f * d)
            p.lineTo(0f, h - 12f * d)
            p.lineTo(0f, h - 8f * d)
            p.cubicTo(0f, h - 2.5f * d, -2.5f * d, h - 0.8f * d, -tip, h)
            p.lineTo(10f * d, h)
        }
        p.close()
        drawPath(p, color)
    }
