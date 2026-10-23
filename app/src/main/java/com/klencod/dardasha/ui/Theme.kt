package com.klencod.dardasha.ui

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.klencod.dardasha.data.AppSettings

/** لون تمييز قابل للاختيار من الإعدادات > المظهر. */
class AccentPalette(
    val name: String,
    val dark: Color, val onDark: Color, val mineDark: Color,
    val light: Color, val mineLight: Color,
)

val ACCENTS = listOf(
    AccentPalette("أخضر مزرق", Color(0xFF19C3A0), Color(0xFF06251F), Color(0xFF0A6B58), Color(0xFF077964), Color(0xFFD2F4EA)),
    AccentPalette("أخضر", Color(0xFF25D366), Color(0xFF062B14), Color(0xFF0B6B35), Color(0xFF0F7B41), Color(0xFFDCF8C6)),
    AccentPalette("أزرق", Color(0xFF4DA3FF), Color(0xFF04182E), Color(0xFF0F4C8A), Color(0xFF1B6CC1), Color(0xFFD5E8FF)),
    AccentPalette("بنفسجي", Color(0xFFA58BFF), Color(0xFF160B3D), Color(0xFF4B3A99), Color(0xFF7550DC), Color(0xFFE5DEFF)),
    AccentPalette("برتقالي", Color(0xFFFFA040), Color(0xFF2B1600), Color(0xFF94550F), Color(0xFFA65607), Color(0xFFFFE5C8)),
    AccentPalette("وردي", Color(0xFFFF7AAE), Color(0xFF3A0A1F), Color(0xFF9B2F5B), Color(0xFFC13171), Color(0xFFFFD8E8)),
)

/** لون تمييز مخصص من درجة لون (Hue) يختارها المستخدم. */
fun customPalette(hue: Int): AccentPalette {
    val h = hue.coerceIn(0, 359).toFloat()
    return AccentPalette(
        "مخصص",
        dark = Color.hsl(h, 0.80f, 0.62f), onDark = Color.hsl(h, 0.80f, 0.10f), mineDark = Color.hsl(h, 0.60f, 0.28f),
        light = Color.hsl(h, 0.75f, 0.40f), mineLight = Color.hsl(h, 0.70f, 0.88f),
    )
}

/** عرض الشاشة بوحدات dp بعد تطبيق «حجم الواجهة» (الكثافة المعدَّلة). */
@Composable
internal fun screenWidthScaledDp(): Float =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / (AppSettings.uiScale / 100f)

/**
 * ألوان التطبيق. كل خاصية تقرأ إعدادات المظهر (حالة Compose) فتتبدّل الواجهة فوراً
 * عند تغيير الوضع (داكن/فاتح/حسب النظام) أو لون التمييز.
 */
object C {
    /** يُضبط من MainActivity عند بدء التشغيل ليعكس وضع النظام. */
    @Volatile var systemDark: Boolean = true

    val dark: Boolean get() = when (AppSettings.theme) { 0 -> systemDark; 1 -> false; else -> true }
    private val ac: AccentPalette get() =
        if (AppSettings.accent >= ACCENTS.size) customPalette(AppSettings.customHue)
        else ACCENTS[AppSettings.accent.coerceIn(0, ACCENTS.lastIndex)]

    val bg: Color get() = if (dark) Color(0xFF0A1114) else Color(0xFFE9EDF0)
    val panel: Color get() = if (dark) Color(0xFF101A20) else Color(0xFFFFFFFF)
    val line: Color get() = if (dark) Color(0xFF1C2931) else Color(0xFFDDE3E7)
    val theirs: Color get() = if (dark) Color(0xFF1B2830) else Color(0xFFF0F2F5)
    /** فقاعة الرسائل الواردة. */
    val bubbleIn: Color get() = if (dark) Color(0xFF1B2830) else Color(0xFFFFFFFF)
    val mine: Color get() = if (dark) ac.mineDark else ac.mineLight
    val accent: Color get() = if (dark) ac.dark else ac.light
    val mute: Color get() = if (dark) Color(0xFF8696A0) else Color(0xFF61717B)
    val fg: Color get() = if (dark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val err: Color get() = if (dark) Color(0xFFF15C6D) else Color(0xFFC33647)
    val tick: Color get() = if (dark) Color(0xFF53BDEB) else Color(0xFF227EAB)
    val onAccent: Color get() = if (dark) ac.onDark else Color.White
    val star: Color get() = if (dark) Color(0xFFFFC83D) else Color(0xFF986F00)
    /** خلفية الشريط العائم للتفاعلات وأزراره. */
    val popup: Color get() = if (dark) Color(0xFF1F2C34) else Color(0xFFFFFFFF)
    val popupAlt: Color get() = if (dark) Color(0xFF3B4A54) else Color(0xFFDDE3E7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DardashaTheme(content: @Composable () -> Unit) {
    val scheme = if (C.dark) darkColorScheme(
        primary = C.accent,
        onPrimary = C.onAccent,
        background = C.bg,
        onBackground = C.fg,
        surface = C.panel,
        onSurface = C.fg,
        surfaceVariant = C.theirs,
        onSurfaceVariant = C.mute,
        error = C.err,
        outline = C.line,
    ) else lightColorScheme(
        primary = C.accent,
        onPrimary = C.onAccent,
        background = C.bg,
        onBackground = C.fg,
        surface = C.panel,
        onSurface = C.fg,
        surfaceVariant = C.theirs,
        onSurfaceVariant = C.mute,
        error = C.err,
        outline = C.line,
    )
    // «حجم الواجهة» و«حجم الخط» العامّان: يكبّران/يصغّران كل شيء (أبعاد، أيقونات، نصوص)
    val base = androidx.compose.ui.platform.LocalDensity.current
    val scaled = androidx.compose.ui.unit.Density(
        base.density * AppSettings.uiScale / 100f,
        base.fontScale * AppSettings.textScale / 100f
    )
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaled) {
    MaterialTheme(colorScheme = scheme) {
        // iOS: لا موجة Ripple في أي مكان، ومقابض/تحديد النص زرقاء مثل UIKit
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = IOS.blue, backgroundColor = IOS.blue.copy(alpha = 0.3f)
            ),
            content = content
        )
    }
    }
}
