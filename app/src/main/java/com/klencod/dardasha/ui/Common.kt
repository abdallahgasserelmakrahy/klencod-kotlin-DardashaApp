package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.klencod.dardasha.data.AppSettings
import com.klencod.dardasha.data.Media
import com.klencod.dardasha.data.Repo

fun avGradient(name: String): Brush {
    var h = 0
    for (c in name) h = (h * 31 + c.code) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(h.toFloat(), 0.55f, 0.42f),
            Color.hsl(((h + 40) % 360).toFloat(), 0.6f, 0.32f)
        )
    )
}

@Composable
fun Avatar(name: String, path: String?, size: Dp = 46.dp, online: Boolean = false, ring: Color = C.panel, plain: Boolean = false) {
    val sz = size * (AppSettings.avatarScale / 100f)
    val avShape = if (AppSettings.avatarShape == 1) androidx.compose.foundation.shape.RoundedCornerShape(sz * 0.28f) else CircleShape
    Box(Modifier.size(sz)) {
        Box(
            Modifier.size(sz).clip(avShape).background(
                if (plain) Brush.verticalGradient(listOf(Color(0xFFA5ABB8), Color(0xFF858B98))) else avGradient(name)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (sz.value * 0.36f).sp
            )
            if (path != null) {
                AsyncImage(
                    model = Repo.avatarUrl(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(13.dp)
                    .background(Color(0xFF25D366), CircleShape)
                    .border(2.dp, ring, CircleShape)
            )
        }
    }
}

/**
 * صورة من bucket خاص (تُنزَّل بالصلاحيات الحالية وتُخزَّن مؤقتاً).
 * يحترم إعداد «التنزيل التلقائي»: إن كان ممنوعاً تظهر أيقونة تنزيل وتُحمَّل الصورة عند الضغط عليها.
 * [force] = true للعرض بملء الشاشة (المستخدم طلب الصورة صراحةً).
 */
@Composable
fun StorageImage(
    bucket: String,
    path: String,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    persist: Boolean = true,
    force: Boolean = false
) {
    val ctx = LocalContext.current
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(Media.cached(path)) }
    var allowed by remember(path) {
        mutableStateOf(force || bmp != null || Media.isCached(path) || Media.autoDownloadAllowed(ctx))
    }
    LaunchedEffect(path, allowed) { if (bmp == null && allowed) bmp = Media.image(bucket, path, persist) }
    Box(
        modifier.background(Color(0x33000000)).clickable(enabled = !allowed && bmp == null) { allowed = true },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = scale)
        else if (!allowed) Icon(Icons.Filled.Download, "تنزيل الصورة", tint = Color.White, modifier = Modifier.size(34.dp))
    }
}
