package com.xyonmusic.player.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xyonmusic.player.PlayerViewModel
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.ui.components.*
import kotlin.math.roundToInt

/**
 * Ficha de álbum: la portada "vuela" desde donde se tocó hasta su sitio, el fondo
 * toma el color de la carátula y hay botones de Reproducir / Aleatorio.
 */
@Composable
fun AlbumDetail(
    album: Album,
    vm: PlayerViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onSongMenu: (Song) -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val colors = rememberArtColors(album.artUri)
    val origin = remember(album) { ArtOrigin.take() }
    val prog = remember(album) { Animatable(if (origin == null) 1f else 0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(album) { prog.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
    val density = LocalDensity.current

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = (prog.value * 1.6f).coerceIn(0f, 1f) }
            .animatedArtBackground(colors, dim = 0.55f)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(240.dp)
                            .onGloballyPositioned { target = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (origin == null || prog.value >= 0.999f) 1f else 0f }
                            .shadow(24.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.Center).size(64.dp))
                        AsyncImage(album.artUri, album.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        album.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Text(
                        listOfNotNull(album.artist, "${album.songs.size} canciones").joinToString(" · "),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailButton("Reproducir", Icons.Filled.PlayArrow, Modifier.weight(1f)) {
                            haptics.confirm(); vm.play(album.songs, 0)
                        }
                        DetailButton("Aleatorio", Icons.Filled.Shuffle, Modifier.weight(1f)) {
                            haptics.confirm(); vm.play(album.songs.shuffled(), 0)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(album.songs, key = { _, s -> s.contentUri.toString() }) { i, s ->
                val isCurrent = ui.current == s
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth()
                        .fadeInOnAppear((i * 25).coerceAtMost(300))
                        .bounceClick(0.97f, onLongClick = { haptics.long(); onSongMenu(s) }) { haptics.tap(); vm.play(album.songs, i) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                        if (isCurrent) EqualizerBars(ui.playing, Color.White)
                        else Text("${i + 1}", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.title ?: s.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                        if (s.artist != null && s.artist != album.artist) Text(s.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
                    }
                    Text(com.xyonmusic.player.formatMs(s.durationMs ?: 0), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                HorizontalDivider(Modifier.padding(start = 48.dp), color = Color.White.copy(alpha = 0.08f))
            }
        }

        // Volver
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(42.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f)).bounceClick { haptics.tap(); onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
    }

    // Portada en vuelo (desde el carrusel hasta su sitio)
    val o = origin; val t = target
    if (o != null && t != null && prog.value < 0.999f) {
        val p = prog.value
        val l = o.left + (t.left - o.left) * p
        val tp = o.top + (t.top - o.top) * p
        val w = o.width + (t.width - o.width) * p
        val h = o.height + (t.height - o.height) * p
        with(density) {
            Box(
                Modifier.offset { IntOffset(l.roundToInt(), tp.roundToInt()) }
                    .size(w.toDp(), h.toDp())
                    .shadow((24 * p).dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray)
            ) { AsyncImage(album.artUri, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        }
    }
}

@Composable
private fun DetailButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).bounceClick(0.93f, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
