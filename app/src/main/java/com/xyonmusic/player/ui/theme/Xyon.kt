package com.xyonmusic.player.ui.theme

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Paleta de la app (igual que XyonColors de la versión original). */
data class XyonColors(
    val bg: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val glass: Color,
    val border: Color,
    val placeholder: Color,
    val surface: Color,
    val blob1: Color,
    val blob2: Color,
    val isLight: Boolean
)

data class ThemeOption(val id: String, val c1: Color, val c2: Color)

val ACCENT = Color(0xFFF83F47)

val THEMES = listOf(
    ThemeOption("default", Color(0xFFF83F47), Color(0xFFFF00FF)),
    ThemeOption("purple", Color(0xFF21D4FD), Color(0xFFB721FF)),
    ThemeOption("green", Color(0xFF00FF87), Color(0xFF60EFFF)),
    ThemeOption("light", Color(0xFFF0F2F5), Color(0xFFD0D2D5))
)

fun xyonColors(theme: String): XyonColors {
    if (theme == "light") return XyonColors(
        bg = Color(0xFFF0F2F5), text = Color(0xFF1C1E21), text2 = Color(0xFF65676B), accent = ACCENT,
        glass = Color.White.copy(alpha = 0.55f), border = Color.Black.copy(alpha = 0.08f),
        placeholder = Color(0xFFE0E0E0), surface = Color(0xF2F7F8FA),
        blob1 = Color(0xFFFF8A80), blob2 = Color(0xFF8C9EFF), isLight = true
    )
    val t = THEMES.firstOrNull { it.id == theme } ?: THEMES[0]
    return XyonColors(
        bg = Color(0xFF0A0A10), text = Color.White, text2 = Color(0xFFA7A7A7),
        accent = if (t.id == "default") ACCENT else t.c1,
        glass = Color(0xFFBBBBBC).copy(alpha = 0.07f), border = Color.White.copy(alpha = 0.1f),
        placeholder = Color(0xFF2A2A2A), surface = Color(0xF0141419),
        blob1 = t.c1, blob2 = t.c2, isLight = false
    )
}

val LocalXyon = staticCompositionLocalOf { xyonColors("default") }

@Composable
fun XyonTheme(theme: String, content: @Composable () -> Unit) {
    val c = xyonColors(theme)
    val scheme = if (c.isLight) lightColorScheme(primary = c.accent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text, surfaceVariant = c.placeholder)
    else darkColorScheme(primary = c.accent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text, surfaceVariant = c.placeholder)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalXyon provides c, content = content)
    }
}

/** Fondo con los dos "blobs" de color animados y difuminados. */
@Composable
fun BlobsBackground(modifier: Modifier = Modifier) {
    val c = LocalXyon.current
    val tr = rememberInfiniteTransition(label = "blobs")
    val t1 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b1")
    val t2 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b2")
    Box(modifier.fillMaxSize().background(c.bg)) {
        val blurMod = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(100.dp) else Modifier
        Canvas(Modifier.fillMaxSize().then(blurMod)) {
            val w = size.width; val h = size.height
            val r1 = 200.dp.toPx(); val r2 = 175.dp.toPx()
            val d100 = 100.dp.toPx(); val d150 = 150.dp.toPx(); val d50 = 50.dp.toPx()
            val p1 = Offset((w / 2 - d100) * t1 + (-d150 + r1), (h / 2 - d150) * t1 + (-d100 + r1))
            val p2 = Offset((d100 + w - r2) - (w / 2 - d100) * t2, (d50 + h - r2) - (h / 2 - d150) * t2)
            val a = if (Build.VERSION.SDK_INT >= 31) 1f else 0.55f
            drawCircle(Brush.radialGradient(0f to c.blob1.copy(alpha = a), 0.6f to Color.Transparent, center = p1, radius = r1), r1, p1)
            drawCircle(Brush.radialGradient(0f to c.blob2.copy(alpha = a), 0.7f to Color.Transparent, center = p2, radius = r2), r2, p2)
        }
    }
}

/** Cristal de la app original: relleno translúcido + brillo superior + borde fino. */
fun Modifier.glass(shape: Shape, c: XyonColors, shine: Boolean = true): Modifier = this
    .background(c.glass, shape)
    .then(
        if (shine) Modifier.background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isLight) 0.35f else 0.06f), Color.Transparent)), shape
        ) else Modifier
    )
    .border(1.dp, c.border, shape)

/**
 * Borde con aberración cromática muy suave (estilo Liquid Glass de iOS 26): el contorno se
 * separa ligeramente en rojo/cian y lleva un brillo especular arriba, como el cristal refractando.
 */
fun Modifier.chromaticEdge(shape: Shape, c: XyonColors, strength: Float = 1f): Modifier = drawWithContent {
    drawContent()
    val o = shape.createOutline(size, layoutDirection, this)
    val w = 1.4.dp.toPx()
    val d = 0.9.dp.toPx() * strength
    val a = (if (c.isLight) 0.22f else 0.32f) * strength
    translate(-d, -d * 0.5f) { drawOutline(o, Color(0xFFFF3B6B).copy(alpha = a), style = Stroke(w)) }
    translate(d, d * 0.5f) { drawOutline(o, Color(0xFF3BE6FF).copy(alpha = a), style = Stroke(w)) }
    drawOutline(
        o,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isLight) 0.9f else 0.38f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f))),
        style = Stroke(0.8.dp.toPx())
    )
}
