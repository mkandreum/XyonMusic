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

// Library = page-library del index.html: CoverFlow horizontal + lista virtualizada.
@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    query: String,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val albums = remember(ui.songs) { ui.songs.toAlbums() }
    val filtered = remember(ui.songs, query) {
        if (query.isBlank()) ui.songs else ui.songs.filter {
            (it.title ?: it.name).contains(query, true) ||
                (it.artist ?: "").contains(query, true) ||
                (it.album ?: "").contains(query, true)
        }
    }
    if (ui.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val sp by vm.sp.collectAsStateWithLifecycle()
    var likedOpen by rememberSaveable { mutableStateOf(true) }
    val liked = remember(sp.liked, query) {
        if (query.isBlank()) sp.liked else sp.liked.filter { it.name.contains(query, true) || it.artist.contains(query, true) }
    }
    LazyColumn(modifier) {
        // Lista predefinida: "Canciones que te gustan" de Spotify, mismo orden que en Spotify.
        if (sp.connected && liked.isNotEmpty()) {
            item(key = "liked-header") {
                Row(
                    Modifier.fillMaxWidth().clickable { likedOpen = !likedOpen }.padding(16.dp, 12.dp),
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
                        Text("Spotify · ${liked.size} canciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (likedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (likedOpen) items(liked, key = { "liked-" + it.uri }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.playSpotify(sp.liked, sp.liked.indexOf(t)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(t.image, t.name, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            SongRow(s, playing = ui.songs.getOrNull(ui.index) == s, onClick = { vm.play(filtered, filtered.indexOf(s)) })
            HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
    }
}

@Composable
fun ForYouScreen(vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val picks = remember(ui.songs) { ui.songs.shuffled().take(20) }
    val albums = remember(ui.songs) { ui.songs.toAlbums().shuffled().take(10) }
    LazyColumn(modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("Para ti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
        item { Text("Generado localmente, sin red.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 0.dp)) }
        item(key = "4u-flow") { AlbumCoverFlow(albums, onAlbumClick, Modifier.padding(top = 8.dp)) }
        items(picks, key = { it.contentUri.toString() }) { s ->
            SongRow(s, false, modifier = Modifier.padding(horizontal = 4.dp), onClick = { vm.play(picks, picks.indexOf(s)) })
        }
    }
}

@Composable
fun TopScreen(vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val top = remember(ui.songs) { ui.songs.sortedByDescending { it.durationMs ?: 0 }.take(50) }
    val albums = remember(ui.songs) { ui.songs.toAlbums().take(10) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("Top", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
        item(key = "top-flow") { AlbumCoverFlow(albums, onAlbumClick) }
        items(top, key = { it.contentUri.toString() }) { s ->
            SongRow(s, false, modifier = Modifier.padding(horizontal = 4.dp), onClick = { vm.play(top, top.indexOf(s)) })
        }
    }
}
