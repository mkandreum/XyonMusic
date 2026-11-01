package com.xyonmusic.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.ui.theme.LocalXyon
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * CoverFlow de la app original.
 *  - horizontal (apaisado): idéntico a la original (giro Y 65°, escala 0.88, sombreado).
 *  - vertical (retrato): mejorado: portadas separadas sin solaparse los textos, inclinación
 *    más suave y un único título fijo bajo la portada central.
 */
@Composable
fun CoverFlow(
    albums: List<Album>,
    vertical: Boolean,
    onOpen: (Album) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "No se encontraron álbumes.", modifier)
        return
    }
    val c = LocalXyon.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pos = remember(albums.size) { Animatable(0f) }
    val density = LocalDensity.current
    val centered by remember { derivedStateOf { pos.value.roundToInt().coerceIn(0, albums.lastIndex) } }
    LaunchedEffect(centered) { if (pos.isRunning || pos.value != 0f) haptics.tick() }

    BoxWithConstraints(modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        val cover: Dp = if (vertical) minOf(300.dp, maxWidth * 0.68f, maxHeight * 0.42f) else minOf(280.dp, maxHeight * 0.62f, maxWidth * 0.4f)
        val coverPx = with(density) { cover.toPx() }
        // Vertical: hueco fijo de 64dp entre la portada central y la siguiente para el título.
        val spacing = if (vertical) coverPx * 0.5f + coverPx * 0.8f * 0.5f + with(density) { 64.dp.toPx() } else coverPx * 0.43f
        val tracker = remember { VelocityTracker() }

        Box(
            Modifier.fillMaxSize().pointerInput(albums.size, vertical, spacing) {
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        val vel = if (vertical) v.y else v.x
                        val target = (pos.value - vel / spacing * 0.22f).roundToInt().coerceIn(0, albums.lastIndex).toFloat()
                        scope.launch { pos.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 140f), -vel / spacing) }
                    },
                    onDragCancel = { scope.launch { pos.animateTo(pos.value.roundToInt().toFloat().coerceIn(0f, albums.lastIndex.toFloat())) } }
                ) { ch, d ->
                    ch.consume()
                    tracker.addPosition(ch.uptimeMillis, ch.position)
                    val delta = if (vertical) d.y else d.x
                    scope.launch { pos.snapTo((pos.value - delta / spacing).coerceIn(-0.35f, albums.lastIndex + 0.35f)) }
                }
            },
            contentAlignment = if (vertical) Alignment.TopCenter else Alignment.Center
        ) {
            val p = pos.value
            val first = floor(p - 4).toInt().coerceAtLeast(0)
            val last = (p + 4).toInt().coerceAtMost(albums.lastIndex)
            val centerY = if (vertical) (maxHeight * 0.40f) else 0.dp
            for (i in first..last) {
                val a = albums[i]
                val d = i - p
                val ad = abs(d)
                if (vertical && ad > 2.3f) continue
                key(a.name + i) {
                    Column(
                        Modifier
                            .zIndex(-ad)
                            .then(if (vertical) Modifier.offset(y = centerY - cover / 2) else Modifier)
                            .graphicsLayer {
                                cameraDistance = 14f * density.density
                                if (!vertical) {
                                    // Original
                                    val t = minOf(ad / 0.5f, 1f)
                                    translationX = sign(d) * coverPx * 0.25f * t + spacing * d
                                    rotationY = -sign(d) * 65f * t
                                    val s = 1f - t * 0.12f; scaleX = s; scaleY = s
                                    alpha = 1f - minOf(ad, 1f) * 0.3f
                                } else {
                                    // Vertical mejorado: sin solaparse, inclinación suave
                                    val t = minOf(ad, 1f)
                                    translationY = spacing * d
                                    rotationX = sign(d) * 38f * t
                                    val s = 1f - t * 0.2f; scaleX = s; scaleY = s
                                    alpha = (1f - minOf(ad, 2f) * 0.38f).coerceIn(0f, 1f)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(cover)
                                .shadow(18.dp, RoundedCornerShape(if (vertical) 16.dp else 6.dp), clip = false)
                                .clip(RoundedCornerShape(if (vertical) 16.dp else 6.dp))
                                .press(haptics, 0.97f) {
                                    if (i == centered) onOpen(a) else scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = 0.85f, stiffness = 200f)) }
                                }
                        ) {
                            Art(a.artUri, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = minOf(ad, 1f) * (if (vertical) 0.45f else 0.35f))))
                        }
                        if (!vertical) {
                            // Título por portada como en la original (se desvanece rápido)
                            Spacer(Modifier.height(14.dp))
                            val ta = (1f - ad * 2f).coerceIn(0f, 1f)
                            Text(a.name, Modifier.width(cover).graphicsLayer { alpha = ta }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.artist ?: "", Modifier.width(cover).graphicsLayer { alpha = ta }, color = c.text2, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (vertical) {
                // Un único título, fijo bajo la portada central
                val a = albums[centered]
                AnimatedContent(
                    a,
                    transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 }) },
                    label = "cf-title",
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = centerY + cover / 2 + 10.dp).fillMaxWidth()
                ) { al ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(al.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(al.artist, "${al.songs.size} canciones").joinToString(" · "), color = c.text2, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

