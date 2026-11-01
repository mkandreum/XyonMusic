package com.xyonmusic.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyonmusic.player.PlayerViewModel
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.data.toAlbums
import com.xyonmusic.player.ui.components.AlbumCoverFlow
import com.xyonmusic.player.ui.components.SongRow

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xyonmusic.player.ui.components.EqualizerBars
import com.xyonmusic.player.ui.components.bounceClick
import com.xyonmusic.player.ui.components.fadeInOnAppear
import com.xyonmusic.player.ui.components.rememberHaptics
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** Espacio inferior ocupado por el mini reproductor + barra de navegación translúcidos. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Estado del permiso de audio (para la pantalla vacía "sin permisos"). */
data class AudioPermission(val granted: Boolean, val request: () -> Unit)
val LocalAudioPermission = compositionLocalOf { AudioPermission(true) {} }

private fun letterOf(s: Song): String {
    val t = Normalizer.normalize((s.title ?: s.name).trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase()
    val c = t.firstOrNull() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

// Library = page-library del index.html: CoverFlow horizontal + lista virtualizada.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    query: String,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    onSongMenu: (Song) -> Unit = {}
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val bottom = LocalBottomInset.current
    val albums = remember(ui.songs) { ui.songs.toAlbums() }
    val filtered = remember(ui.songs, query) {
        if (query.isBlank()) ui.songs else ui.songs.filter {
            (it.title ?: it.name).contains(query, true) ||
                (it.artist ?: "").contains(query, true) ||
                (it.album ?: "").contains(query, true)
        }
    }
    if (ui.loading && ui.songs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var likedOpen by rememberSaveable { mutableStateOf(true) }
    val liked = remember(sp.liked, query) {
        if (query.isBlank()) sp.liked else sp.liked.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
    }
    val listState = rememberLazyListState()
    val showLiked = sp.connected && (liked.isNotEmpty() || (query.isBlank() && (sp.loading || sp.error != null)))
    // Índice de la primera canción de la lista (para el índice A–Z)
    val headerCount = (if (showLiked) 1 + (if (likedOpen) liked.size else 0) + (if (sp.error != null && liked.isEmpty()) 1 else 0) else 0) +
        (if (query.isBlank()) 1 else 0) + 1
    val letters = remember(filtered) {
        val m = LinkedHashMap<String, Int>()
        filtered.forEachIndexed { i, s -> m.putIfAbsent(letterOf(s), i) }
        m
    }

    PullToRefreshBox(
        isRefreshing = (sp.loading && sp.connected) || (ui.loading && ui.songs.isNotEmpty()),
        onRefresh = { haptics.tap(); vm.reload(); if (sp.connected) vm.refreshSpotify() },
        modifier = modifier
    ) {
        if (ui.songs.isEmpty() && !showLiked) {
            EmptyLibrary(vm)
            return@PullToRefreshBox
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
            // Lista predefinida: "Canciones que te gustan" de Spotify, mismo orden que en Spotify.
            if (showLiked) {
                item(key = "liked-header") {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { haptics.tap(); likedOpen = !likedOpen }.padding(16.dp, 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Favorite, null, tint = Color.White) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Canciones que te gustan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (sp.loading && liked.isEmpty()) "Spotify · cargando…" else "Spotify · ${liked.size} canciones",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(if (likedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        if (sp.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp), color = Color(0xFF1ED760))
                    }
                }
                if (sp.error != null && liked.isEmpty()) item(key = "liked-error") {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("${sp.error}. Desliza hacia abajo para reintentar.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (likedOpen) items(liked, key = { "liked-" + it.uri }) { t ->
                    val nowUri = sp.now?.track?.uri
                    Row(
                        Modifier.fillMaxWidth().fadeInOnAppear()
                            .clickable { haptics.tap(); vm.playSpotify(sp.liked, sp.liked.indexOf(t)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(t.image, t.name, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = if (nowUri == t.uri) Color(0xFF1ED760) else MaterialTheme.colorScheme.onSurface)
                            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (nowUri == t.uri) EqualizerBars(sp.now?.playing == true, Color(0xFF1ED760))
                    }
                    HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
            if (query.isBlank()) {
                item(key = "coverflow") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Álbumes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp))
                        AlbumCoverFlow(albums, onAlbumClick)
                    }
                }
            }
            item(key = "count") {
                Text("${filtered.size} canciones", modifier = Modifier.padding(16.dp, 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filtered, key = { it.contentUri.toString() }) { s: Song ->
                SongRow(
                    s, playing = ui.current == s, isPlaying = ui.playing,
                    modifier = Modifier.padding(end = if (query.isBlank() && filtered.size > 30) 18.dp else 0.dp),
                    onLongClick = { onSongMenu(s) },
                    onClick = { haptics.tap(); vm.play(filtered, filtered.indexOf(s)) }
                )
                HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
        if (query.isBlank() && filtered.size > 30) {
            AlphabetIndex(
                letters, listState, headerCount,
                Modifier.align(Alignment.CenterEnd).padding(bottom = bottom).fillMaxHeight(0.72f)
            )
        }
    }
}

/** Índice A–Z lateral: tocar o arrastrar salta a la letra, con vibración y burbuja. */
@Composable
private fun AlphabetIndex(letters: Map<String, Int>, listState: LazyListState, headerCount: Int, modifier: Modifier) {
    val all = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<String?>(null) }
    var activeY by remember { mutableStateOf(0f) }
    fun jump(y: Float, h: Float) {
        val i = ((y / h) * all.size).toInt().coerceIn(0, all.lastIndex)
        val l = all[i]
        activeY = y.coerceIn(0f, h)
        if (l != active) {
            active = l
            haptics.tick()
            // primera letra disponible desde la tocada
            val target = all.drop(i).firstNotNullOfOrNull { letters[it] } ?: letters.values.lastOrNull() ?: return
            scope.launch { listState.scrollToItem(headerCount + target) }
        }
    }
    BoxWithConstraints(modifier.width(26.dp)) {
        val h = constraints.maxHeight.toFloat()
        Column(
            Modifier.fillMaxSize()
                .pointerInput(letters, headerCount) {
                    detectTapGestures(onPress = { o -> jump(o.y, h); tryAwaitRelease(); active = null })
                }
                .pointerInput(letters, headerCount) {
                    detectVerticalDragGestures(onDragEnd = { active = null }, onDragCancel = { active = null }) { ch, _ ->
                        ch.consume(); jump(ch.position.y, h)
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            all.forEach { l ->
                Text(
                    l, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (l == active) MaterialTheme.colorScheme.primary
                    else if (letters.containsKey(l)) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }
        active?.let { l ->
            Box(
                Modifier.zIndex(2f).offset { IntOffset(-90.dp.roundToPx(), (activeY - 28.dp.toPx()).roundToInt()) }
                    .size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { Text(l, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------------------
// Pantallas vacías
// ---------------------------------------------------------------------------
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).fadeInOnAppear(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyLibrary(vm: PlayerViewModel) {
    val perm = LocalAudioPermission.current
    if (!perm.granted) EmptyState(
        Icons.Filled.FolderOff, "Sin acceso a tu música",
        "XyonMusic necesita permiso para leer los archivos de audio del dispositivo.",
        "Conceder permiso", perm.request
    ) else EmptyState(
        Icons.Filled.MusicOff, "No hay canciones",
        "No se encontraron archivos de música en el dispositivo. Copia música al móvil o conecta Spotify desde tu perfil.",
        "Volver a buscar"
    ) { vm.reload() }
}

@Composable
fun ForYouScreen(vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier, onSongMenu: (Song) -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val picks = remember(ui.songs) { ui.songs.shuffled().take(20) }
    val albums = remember(ui.songs) { ui.songs.toAlbums().shuffled().take(10) }
    if (!ui.loading && ui.songs.isEmpty()) { Box(modifier) { EmptyLibrary(vm) }; return }
    LazyColumn(modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp)) {
        item { Text("Para ti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp).fadeInOnAppear()) }
        item { Text("Generado localmente, sin red.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 0.dp).fadeInOnAppear(60)) }
        item(key = "4u-flow") { AlbumCoverFlow(albums, onAlbumClick, Modifier.padding(top = 8.dp)) }
        items(picks, key = { it.contentUri.toString() }) { s ->
            SongRow(s, ui.current == s, modifier = Modifier.padding(horizontal = 4.dp), isPlaying = ui.playing, onLongClick = { onSongMenu(s) }, onClick = { haptics.tap(); vm.play(picks, picks.indexOf(s)) })
        }
    }
}

@Composable
fun TopScreen(vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier, onSongMenu: (Song) -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val top = remember(ui.songs) { ui.songs.sortedByDescending { it.durationMs ?: 0 }.take(50) }
    val albums = remember(ui.songs) { ui.songs.toAlbums().take(10) }
    if (!ui.loading && ui.songs.isEmpty()) { Box(modifier) { EmptyLibrary(vm) }; return }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp)) {
        item { Text("Top", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp).fadeInOnAppear()) }
        item(key = "top-flow") { AlbumCoverFlow(albums, onAlbumClick) }
        items(top, key = { it.contentUri.toString() }) { s ->
            SongRow(s, ui.current == s, modifier = Modifier.padding(horizontal = 4.dp), isPlaying = ui.playing, onLongClick = { onSongMenu(s) }, onClick = { haptics.tap(); vm.play(top, top.indexOf(s)) })
        }
    }
}
