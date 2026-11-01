package com.xyonmusic.player.ui.components

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Vibraciones: usan las del sistema (performHapticFeedback), así respetan si el
// usuario tiene la respuesta háptica desactivada en Ajustes de Android.
// ---------------------------------------------------------------------------
class Haptics(private val view: View) {
    private fun fire(c: Int) { view.performHapticFeedback(c) }

    /** Toque ligero: botones, cambio de pestaña. */
    fun tap() = fire(if (Build.VERSION.SDK_INT >= 23) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Confirmación: play/pausa, siguiente, anterior. */
    fun confirm() = fire(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** "Clic" de rueda: arrastrar la barra, letras del índice, encaje del carrusel. */
    fun tick() = fire(
        when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            Build.VERSION.SDK_INT >= 21 -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
    )

    /** Tope marcado: llegar al inicio/final de la barra. */
    fun edge() = fire(
        when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_TICK
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.GESTURE_END
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
    )

    /** Pulsación larga: menú de canción, empezar a arrastrar en la cola. */
    fun long() = fire(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val v = LocalView.current
    return remember(v) { Haptics(v) }
}

// ---------------------------------------------------------------------------
// Botón que "se hunde" al pulsarlo y rebota al soltar.
// ---------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    pressedScale: Float = 0.88f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = 700f), label = "bounce"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(src, indication = null, onClick = onClick, onLongClick = onLongClick)
}

/** Aparición suave (fundido + ligero desplazamiento) al entrar en pantalla. */
fun Modifier.fadeInOnAppear(delayMs: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, delayMillis = delayMs, easing = FastOutSlowInEasing)) }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 22f }
}

// ---------------------------------------------------------------------------
// Indicador "sonando": barras de ecualizador animadas.
// ---------------------------------------------------------------------------
@Composable
fun EqualizerBars(active: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(380, 520, 450).mapIndexed { i, d ->
        t.animateFloat(
            0.2f, 1f, infiniteRepeatable(tween(d, delayMillis = i * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "eq$i"
        )
    }
    Row(
        modifier.size(size),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { h ->
            Box(
                Modifier.weight(1f).fillMaxHeight(if (active) h.value else 0.25f)
                    .clip(RoundedCornerShape(1.dp)).background(color)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Colores dominantes de una carátula (Palette) con transición suave.
// ---------------------------------------------------------------------------
data class ArtColors(val primary: Color, val secondary: Color, val dark: Color) {
    companion object {
        val Default = ArtColors(Color(0xFF3A1E2C), Color(0xFF1E2A44), Color(0xFF0A0A10))
    }
}

@Composable
fun rememberArtColors(model: Any?): ArtColors {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(ArtColors.Default) }
    LaunchedEffect(model) {
        if (model == null) { target = ArtColors.Default; return@LaunchedEffect }
        val req = ImageRequest.Builder(ctx).data(model).allowHardware(false).size(160).build()
        val bmp = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        target = if (bmp == null) ArtColors.Default else withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val fallback = 0xFF2A2A33.toInt()
            ArtColors(
                primary = Color(p.getVibrantColor(p.getMutedColor(fallback))),
                secondary = Color(p.getDarkVibrantColor(p.getLightMutedColor(fallback))),
                dark = Color(p.getDarkMutedColor(0xFF101014.toInt()))
            )
        }
    }
    val a by animateColorAsState(target.primary, tween(900), label = "c1")
    val b by animateColorAsState(target.secondary, tween(900), label = "c2")
    val c by animateColorAsState(target.dark, tween(900), label = "c3")
    return ArtColors(a, b, c)
}

/** Fondo en degradado que se mueve lentamente con los colores de la carátula. */
fun Modifier.animatedArtBackground(colors: ArtColors, dim: Float = 0.45f): Modifier = composed {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "ph")
    drawBehind {
        drawRect(colors.dark)
        val r = size.maxDimension
        drawRect(
            Brush.radialGradient(
                listOf(colors.primary.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(size.width * (0.3f + 0.25f * cos(phase)), size.height * (0.25f + 0.12f * sin(phase))),
                radius = r * 0.75f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(colors.secondary.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(size.width * (0.75f - 0.25f * sin(phase)), size.height * (0.75f + 0.1f * cos(phase))),
                radius = r * 0.7f
            )
        )
        drawRect(Color.Black.copy(alpha = dim))
    }
}

// ---------------------------------------------------------------------------
// Origen de la portada pulsada (para la animación "vuela y crece" hacia el álbum).
// ---------------------------------------------------------------------------
object ArtOrigin {
    private var rect: Rect? = null
    fun set(r: Rect?) { rect = r }
    fun take(): Rect? = rect.also { rect = null }
}
