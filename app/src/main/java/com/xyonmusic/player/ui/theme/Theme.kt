package com.xyonmusic.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens extraídos de assets/index.html (versión WebView)
// FIX: sin CDN. Inter -> FontFamily.Default (sistema), offline-first.
object XyonTokens {
    val AppBgDark = Color(0xFF0A0A10)
    val Accent = Color(0xFFF83F47)
    val TextSecondaryDark = Color(0xFFA7A7A7)
    val PlaceholderDark = Color(0xFF2A2A2A)
    val AppBgLight = Color(0xFFF0F2F5)
    val TextPrimaryLight = Color(0xFF1C1E21)
    val TextSecondaryLight = Color(0xFF65676B)
    val BottomNavHeight = 70.dp
    val MiniPlayerHeight = 64.dp
    val CoverWidth = 280.dp
}

private val DarkScheme = darkColorScheme(
    primary = XyonTokens.Accent,
    background = XyonTokens.AppBgDark,
    surface = XyonTokens.AppBgDark,
    onBackground = Color.White,
    onSurface = Color.White,
    secondaryContainer = XyonTokens.PlaceholderDark
)

private val LightScheme = lightColorScheme(
    primary = XyonTokens.Accent,
    background = XyonTokens.AppBgLight,
    surface = XyonTokens.AppBgLight,
    onBackground = XyonTokens.TextPrimaryLight,
    onSurface = XyonTokens.TextPrimaryLight
)

// FIX WebView: shimmer/blur(120px)+backdrop-filter:url() causaban jank.
// Nativa: sin blur gigante, glass = surface con alpha, sin SVG filter.
@Composable
fun XyonTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(
            titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
