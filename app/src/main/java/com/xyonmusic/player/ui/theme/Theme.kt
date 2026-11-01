package com.xyonmusic.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens estilo iOS 26 / Apple Music (fondo negro puro, acento rosa-rojo, tab activo azul).
object XyonTokens {
    val AppBgDark = Color(0xFF000000)
    val Accent = Color(0xFFFA2D48)
    val TabActive = Color(0xFF6F9BFF)
    val TextSecondaryDark = Color(0xFF98989F)
    val GroupedDark = Color(0xFF1C1C1E)
    val AppBgLight = Color(0xFFF2F2F7)
    val TextPrimaryLight = Color(0xFF000000)
    val TextSecondaryLight = Color(0xFF6C6C70)
}

private val DarkScheme = darkColorScheme(
    primary = XyonTokens.Accent,
    secondary = XyonTokens.TabActive,
    background = XyonTokens.AppBgDark,
    surface = XyonTokens.AppBgDark,
    surfaceVariant = XyonTokens.GroupedDark,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = XyonTokens.TextSecondaryDark,
    secondaryContainer = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF38383A)
)

private val LightScheme = lightColorScheme(
    primary = XyonTokens.Accent,
    secondary = Color(0xFF007AFF),
    background = XyonTokens.AppBgLight,
    surface = XyonTokens.AppBgLight,
    surfaceVariant = Color.White,
    onBackground = XyonTokens.TextPrimaryLight,
    onSurface = XyonTokens.TextPrimaryLight,
    onSurfaceVariant = XyonTokens.TextSecondaryLight,
    outlineVariant = Color(0xFFD1D1D6)
)

@Composable
fun XyonTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val f = FontFamily.Default
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(
            displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = 36.sp),
            titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            bodyLarge = TextStyle(fontFamily = f, fontSize = 17.sp),
            bodyMedium = TextStyle(fontFamily = f, fontSize = 15.sp),
            bodySmall = TextStyle(fontFamily = f, fontSize = 13.sp),
            labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
