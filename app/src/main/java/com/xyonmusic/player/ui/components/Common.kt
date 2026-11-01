package com.xyonmusic.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyonmusic.player.data.ArtCache
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.data.formatTime
import com.xyonmusic.player.ui.theme.LocalXyon
import com.xyonmusic.player.ui.theme.glass

/** Carátula incrustada en el archivo, con fundido y nota musical de reserva. */
@Composable
fun Art(uri: String?, modifier: Modifier = Modifier) {
    val c = LocalXyon.current
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(uri?.let { ArtCache.peek(it) }) }
    LaunchedEffect(uri) { if (uri != null && bmp == null) bmp = ArtCache.load(ctx, uri) }
    Box(modifier.background(c.placeholder), contentAlignment = Alignment.Center) {
        Crossfade(bmp, label = "art") { b ->
            if (b == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(0.42f), tint = c.text2.copy(alpha = 0.55f))
            } else Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Click con "hundimiento" y vibración (como en la original). */
fun Modifier.press(haptics: Haptics? = null, scale: Float = 0.96f, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    this.bounceClick(scale, onLongClick = onLongClick?.let { l -> { haptics?.long(); l() } }) { haptics?.tap(); onClick() }

/** Botón redondo de icono (tamaño de icono + 14dp). */
@Composable
fun IconBtn(icon: ImageVector, size: Int, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Box(modifier.size((size + 14).dp).clip(CircleShape).press(h, 0.85f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size.dp), tint = tint ?: c.text)
    }
}

/** Botón de 54dp que se ilumina con el acento cuando está activo (aleatorio / repetir). */
@Composable
fun ToggleBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Box(
        Modifier.size(54.dp).clip(CircleShape)
            .then(if (active) Modifier.background(c.accent.copy(alpha = 0.22f), CircleShape) else Modifier.glass(CircleShape, c, shine = false))
            .press(h, 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(28.dp), tint = if (active) c.accent else c.text) }
}

/** Botón redondo de cristal de 44dp. */
@Composable
fun GlassCircleBtn(icon: ImageVector, onClick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Box(Modifier.size(44.dp).clip(CircleShape).glass(CircleShape, c).press(h, 0.88f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.text)
    }
}

/** Chip de segmento (Canciones / Álbumes). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(c.accent.copy(alpha = 0.22f), shape).border(1.5.dp, c.accent.copy(alpha = 0.4f), shape) else Modifier.glass(shape, c, shine = false))
            .press(h, 0.94f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) { Text(text, color = if (selected) c.text else c.text2, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp) }
}

/** Cabecera grande de pestaña (35sp, extra negrita) con acciones a la derecha. */
@Composable
fun BigHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalXyon.current
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = c.text, fontSize = 35.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 24.dp, bottom = 12.dp), color = LocalXyon.current.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = LocalXyon.current
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = c.text2.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Text(text, color = c.text2, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

/** Icono "sonando" que late. */
@Composable
fun PlayingIcon(playing: Boolean) {
    val c = LocalXyon.current
    if (!playing) { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(22.dp), tint = c.accent); return }
    val t = rememberInfiniteTransition(label = "eq")
    val s by t.animateFloat(0.6f, 1.15f, infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eqs")
    Icon(Icons.Rounded.GraphicEq, null, Modifier.size(22.dp).graphicsLayer { scaleY = s }, tint = c.accent)
}

/**
 * Fila de canción de la app original: nº opcional, carátula 50dp (con icono "sonando"
 * si es la actual), título 16sp y artista 14sp, texto final opcional (duración / veces).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    playing: Boolean,
    number: Int? = null,
    trailing: String? = formatTime(song.durationMs),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { h.tap(); onClick() }, onLongClick = onLongClick?.let { l -> { h.long(); l() } })
            .padding(horizontal = 5.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text("$number", Modifier.width(28.dp), color = if (isCurrent) c.accent else c.text2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
        }
        Box {
            Art(song.uri, Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
            if (isCurrent) Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) { PlayingIcon(playing) }
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (isCurrent) c.accent else c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = c.text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, Modifier.padding(start = 10.dp), color = c.text2, fontSize = 14.sp)
    }
}

/** Botón píldora de cristal con icono en acento (Reproducir / Aleatorio). */
@Composable
fun PillAction(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    val shape = RoundedCornerShape(23.dp)
    Row(
        modifier.height(46.dp).clip(shape).glass(shape, c).press(h, 0.95f, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = c.accent)
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.text, fontWeight = FontWeight.SemiBold)
    }
}
