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
    LazyColumn(modifier) {
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
            HorizontalDivider()
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
