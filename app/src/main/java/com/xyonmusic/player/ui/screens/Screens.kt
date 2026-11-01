package com.xyonmusic.player.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyonmusic.player.PlayerViewModel
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Song
import androidx.compose.ui.layout.layout
import com.xyonmusic.player.data.toAlbums
import com.xyonmusic.player.spotify.SpPlaylist
import com.xyonmusic.player.spotify.SpTrack
import com.xyonmusic.player.ui.components.*

/** Acciones de navegación compartidas por todas las pantallas. */
class Nav(
    val openAlbum: (Album) -> Unit,
    val openPlaylist: (SpPlaylist) -> Unit,
    val openSettings: () -> Unit,
    val openTab: (Int) -> Unit
)

val BottomInset = 170.dp

@Composable
private fun Header(title: String, vm: PlayerViewModel, nav: Nav, device: Boolean = false) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    val name = ui.userName.ifBlank { sp.user?.name.orEmpty() }
    LargeTitleHeader(title, sp.user?.image, name, nav.openSettings, onDevice = if (device) nav.openSettings else null)
}

@Composable
private fun Loading() = Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
}

// ============================== HOME ==============================
@Composable
fun HomeScreen(vm: PlayerViewModel, nav: Nav, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    val albums = remember(ui.songs) { ui.songs.toAlbums() }
    val byUri = remember(ui.songs) { ui.songs.associateBy { it.contentUri.toString() } }
    val recentSongs = remember(ui.recent, byUri) { ui.recent.mapNotNull { byUri[it] } }
    val recentAlbums = remember(recentSongs, albums) {
        recentSongs.mapNotNull { s -> albums.firstOrNull { a -> s in a.songs } }.distinct().take(12)
    }
    val newAlbums = remember(albums) { albums.sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(12) }
    val name = ui.userName.ifBlank { sp.user?.name?.substringBefore(' ').orEmpty() }.ifBlank { "Tu" }
    val station = if (name == "Tu") "Tu Station" else "$name’s Station"

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = BottomInset)) {
        item { Header("Home", vm, nav, device = true) }
        if (ui.loading) { item { Loading() }; return@LazyColumn }

        item { SectionHeader("Top Picks for You") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    PickCard("Made for You", station, null, StationArt) {
                        if (ui.songs.isNotEmpty()) ui.songs.shuffled().let { vm.play(it, 0) }
                    }
                }
                item {
                    PickCard("Mood for You", "Love", "Música romántica para el momento perfecto.", LoveArt) {
                        val love = ui.songs.filter { s ->
                            val t = "${s.title} ${s.album}".lowercase()
                            listOf("love", "amor", "corazón", "heart", "kiss", "beso", "te quiero").any { it in t }
                        }.ifEmpty { ui.songs }.shuffled()
                        if (love.isNotEmpty()) vm.play(love, 0)
                    }
                }
                if (sp.connected && sp.saved.isNotEmpty()) item {
                    PickCard("Spotify", "Tus me gusta", "${sp.saved.size} canciones guardadas en Spotify.", SpotifyArt) {
                        vm.playSpotify(sp.saved, 0)
                    }
                }
                item {
                    PickCard("Discovery", "Nuevo para ti", "Álbumes de tu biblioteca que aún no has escuchado.", DiscoveryArt) {
                        val notHeard = albums.filter { it !in recentAlbums }.ifEmpty { albums }
                        notHeard.randomOrNull()?.let { nav.openAlbum(it) }
                    }
                }
            }
        }

        item { SectionHeader("Recently Played", onMore = { nav.openTab(2) }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (recentSongs.isNotEmpty()) item {
                    OverlayTile("General", name.takeIf { it != "Tu" }, 184.dp, onClick = { vm.play(recentSongs, 0) }) {
                        Mosaic(recentAlbums.map { it.artUri }.ifEmpty { albums.take(4).map { it.artUri } }, Modifier.matchParentSize())
                    }
                }
                val list = recentAlbums.ifEmpty { albums.shuffled().take(8) }
                items(list, key = { "r" + it.title + it.artist }) { a ->
                    OverlayTile(a.title, a.artist, 184.dp, onClick = { nav.openAlbum(a) }) { Artwork(a.artUri, Modifier.matchParentSize(), 0.dp) }
                }
                if (sp.connected) items(sp.recent.take(10), key = { "sr" + it.uri }) { t ->
                    OverlayTile(t.name, t.artist, 184.dp, onClick = { vm.playSpotify(sp.recent, sp.recent.indexOf(t)) }) {
                        Artwork(t.image, Modifier.matchParentSize(), 0.dp)
                    }
                }
            }
        }

        if (sp.connected && sp.playlists.isNotEmpty()) {
            item { SectionHeader("Tus playlists de Spotify", onMore = { nav.openTab(2) }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(sp.playlists, key = { "p" + it.id }) { p -> AlbumTile(p.name, p.owner, p.image, 150.dp) { nav.openPlaylist(p) } }
                }
            }
        }

        item { SectionHeader("New Releases for You", onMore = { nav.openTab(3) }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(newAlbums, key = { "n" + it.title + it.artist }) { a -> AlbumTile(a.title, a.artist, a.artUri, 150.dp) { nav.openAlbum(a) } }
            }
        }
        if (ui.songs.isEmpty()) item {
            Text(
                "No se encontró música en el dispositivo. Concede el permiso de audio o conecta Spotify desde tu perfil.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AlbumTile(title: String, subtitle: String?, art: Any?, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Artwork(art, Modifier.size(size).border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private typealias CardArt = @Composable BoxScope.() -> Unit

/** Tarjeta grande "Top Picks" (260x340) con arte generativo, igual que Apple Music. */
@Composable
private fun PickCard(eyebrow: String, title: String, subtitle: String?, art: CardArt, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.width(262.dp).height(345.dp).clip(shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x11FFFFFF))), shape)
            .clickable(onClick = onClick)
    ) {
        art()
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6200010))))
        Text(
            " music", Modifier.align(Alignment.TopEnd).padding(14.dp),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(eyebrow, color = Color(0xD9FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 27.sp)
            if (subtitle != null) Text(subtitle, color = Color(0xE6FFFFFF), fontSize = 15.sp, maxLines = 2, lineHeight = 19.sp)
        }
    }
}

private val StationArt: CardArt = {
    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFFFF8A3D), Color(0xFFF72F5C), Color(0xFFE8195A)))))
    Canvas(Modifier.matchParentSize()) {
        val w = size.width; val h = size.height
        val shades = listOf(0x33FFD27A, 0x40FF6E7F, 0x4DFF4F8A, 0x33FFFFFF, 0x26FFB3C6)
        shades.forEachIndexed { i, c ->
            val inset = i * w * 0.13f
            val p = Path().apply {
                moveTo(inset - w * 0.2f, 0f); lineTo(w, h * 0.47f); lineTo(inset - w * 0.2f, h); close()
            }
            drawPath(p, Color(c))
        }
    }
}

private val LoveArt: CardArt = {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFFFF5FB0), Color(0xFFF13C8E), Color(0xFF7A1048)))))
    Canvas(Modifier.matchParentSize()) {
        val w = size.width; val cx = w * 0.5f; val top = size.height * 0.22f; val s = w * 0.62f
        val heart = Path().apply {
            moveTo(cx, top + s * 0.95f)
            cubicTo(cx - s * 1.05f, top + s * 0.25f, cx - s * 0.55f, top - s * 0.35f, cx, top + s * 0.12f)
            cubicTo(cx + s * 0.55f, top - s * 0.35f, cx + s * 1.05f, top + s * 0.25f, cx, top + s * 0.95f)
            close()
        }
        drawPath(heart, Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x33FFFFFF))), style = Stroke(width = 4.dp.toPx()))
    }
}

private val SpotifyArt: CardArt = {
    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFF1ED760), Color(0xFF0F7A3A), Color(0xFF0B2E1A)))))
    Icon(Icons.Filled.Favorite, null, tint = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp).size(120.dp))
}

private val DiscoveryArt: CardArt = {
    Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFF5B6CFF), Color(0xFF9B3DFF), Color(0xFF2A0F5C)))))
    Canvas(Modifier.matchParentSize()) {
        for (i in 0 until 6) drawCircle(
            Color.White.copy(alpha = 0.10f + i * 0.03f), radius = size.width * (0.12f + i * 0.09f),
            center = Offset(size.width * 0.5f, size.height * 0.35f), style = Stroke(2.dp.toPx())
        )
    }
}

// ============================== ALBUMS ==============================
/** Vertical: rejilla de 2 columnas estilo Apple Music. Horizontal: CoverFlow (se mantiene). */
@Composable
fun AlbumsScreen(vm: PlayerViewModel, nav: Nav, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val albums = remember(ui.songs) { ui.songs.toAlbums() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Column(modifier.fillMaxSize()) {
            Header("Albums", vm, nav)
            if (ui.loading) Loading()
            else AlbumCoverFlow(albums, nav.openAlbum, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(2), modifier,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = BottomInset),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(2) }) { Box(Modifier.bleed(18.dp)) { Header("Albums", vm, nav) } }
        if (ui.loading) item(span = { GridItemSpan(2) }) { Loading() }
        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Reproducir", Icons.Filled.Favorite, Modifier.weight(1f)) {
                    albums.flatMap { it.songs }.takeIf { it.isNotEmpty() }?.let { vm.play(it, 0) }
                }
                PillButton("Aleatorio", Icons.Filled.Shuffle, Modifier.weight(1f)) {
                    ui.songs.shuffled().takeIf { it.isNotEmpty() }?.let { vm.play(it, 0) }
                }
            }
        }
        items(albums, key = { it.title + "|" + it.artist }) { a ->
            Column(Modifier.clickable { nav.openAlbum(a) }) {
                Artwork(a.artUri, Modifier.fillMaxWidth().aspectRatio(1f).border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)), 12.dp)
                Spacer(Modifier.height(7.dp))
                Text(a.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                Text(a.artist ?: "${a.songs.size} canciones", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

/** Ignora el padding horizontal del contenedor (para cabeceras a sangre). */
private fun Modifier.bleed(h: androidx.compose.ui.unit.Dp) = layout { m, c ->
    val extra = (h * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
}

// ============================== LIBRARY ==============================
@Composable
fun LibraryScreen(vm: PlayerViewModel, nav: Nav, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val current = ui.queue.getOrNull(ui.index)
    val segments = listOf("Canciones", "Artistas") + if (sp.connected) listOf("Spotify") else emptyList()
    val artists = remember(ui.songs) { ui.songs.groupBy { it.artist ?: "Artista desconocido" }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = BottomInset)) {
        item { Header("Library", vm, nav) }
        item { Segmented(segments, seg.coerceAtMost(segments.lastIndex)) { seg = it } }
        if (ui.loading) { item { Loading() }; return@LazyColumn }
        when (segments.getOrNull(seg)) {
            "Artistas" -> items(artists.entries.toList(), key = { "a" + it.key }) { (artist, songs) ->
                val albumsOf = remember(songs) { songs.toAlbums() }
                ListRow(songs.firstOrNull { it.artUri != null }?.artUri, artist, "${songs.size} canciones · ${albumsOf.size} álbumes", circle = true) {
                    if (albumsOf.size == 1) nav.openAlbum(albumsOf[0]) else nav.openAlbum(Album(artist, artist, songs.sortedBy { it.album }))
                }
            }
            "Spotify" -> {
                item { SectionHeader("Playlists") }
                items(sp.playlists, key = { "sp" + it.id }) { p -> ListRow(p.image, p.name, "Playlist · ${p.owner}") { nav.openPlaylist(p) } }
                item { SectionHeader("Canciones que te gustan") }
                items(sp.saved, key = { "ss" + it.uri }) { t -> SpotifyRow(t) { vm.playSpotify(sp.saved, sp.saved.indexOf(t)) } }
            }
            else -> {
                item { Text("${ui.songs.size} canciones", Modifier.padding(18.dp, 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(ui.songs, key = { it.contentUri.toString() }) { s ->
                    SongRow(s, playing = current == s, onClick = { vm.play(ui.songs, ui.songs.indexOf(s)) })
                }
            }
        }
    }
}

@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(18.dp, 8.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)
    ) {
        items.forEachIndexed { i, s ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) Color(0xFF636366) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Text(s, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

@Composable
fun ListRow(art: Any?, title: String, subtitle: String, circle: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(18.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(art, Modifier.size(54.dp), if (circle) 27.dp else 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SpotifyRow(t: SpTrack, onClick: () -> Unit) = ListRow(t.image, t.name, t.artist, onClick = onClick)

// ============================== NEW ==============================
@Composable
fun NewScreen(vm: PlayerViewModel, nav: Nav, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val newest = remember(ui.songs) { ui.songs.sortedByDescending { it.dateAdded }.take(60) }
    val newAlbums = remember(ui.songs) { ui.songs.toAlbums().sortedByDescending { a -> a.songs.maxOf { it.dateAdded } }.take(16) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = BottomInset)) {
        item { Header("New", vm, nav) }
        if (ui.loading) { item { Loading() }; return@LazyColumn }
        item { SectionHeader("Añadidos recientemente") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(newAlbums, key = { "na" + it.title + it.artist }) { a ->
                    OverlayTile(a.title, a.artist, 220.dp, onClick = { nav.openAlbum(a) }) { Artwork(a.artUri, Modifier.matchParentSize(), 0.dp) }
                }
            }
        }
        item { SectionHeader("Canciones nuevas") }
        items(newest, key = { "ns" + it.contentUri }) { s -> SongRow(s, ui.queue.getOrNull(ui.index) == s) { vm.play(newest, newest.indexOf(s)) } }
    }
}

// ============================== SEARCH ==============================
@Composable
fun SearchScreen(vm: PlayerViewModel, nav: Nav, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val albums = remember(ui.songs) { ui.songs.toAlbums() }
    val songs = remember(ui.songs, q) {
        if (q.isBlank()) emptyList() else ui.songs.filter {
            (it.title ?: it.name).contains(q, true) || (it.artist ?: "").contains(q, true) || (it.album ?: "").contains(q, true)
        }
    }
    val foundAlbums = remember(albums, q) { if (q.isBlank()) emptyList() else albums.filter { it.title.contains(q, true) || (it.artist ?: "").contains(q, true) } }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = BottomInset)) {
        item { Header("Search", vm, nav) }
        item {
            Row(
                Modifier.padding(18.dp, 6.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Artistas, canciones, álbumes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (q.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", Modifier.noRippleClick { q = "" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (q.isBlank()) {
            item { SectionHeader("Explorar álbumes") }
            items(albums.chunked(2), key = { "g" + it.first().title + it.first().artist }) { row ->
                Row(Modifier.padding(horizontal = 18.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { a ->
                        Box(Modifier.weight(1f).aspectRatio(1.5f)) {
                            OverlayTile(a.title, a.artist, 400.dp, Modifier.fillMaxSize(), onClick = { nav.openAlbum(a) }) {
                                Artwork(a.artUri, Modifier.matchParentSize(), 0.dp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            if (foundAlbums.isNotEmpty()) {
                item { SectionHeader("Álbumes") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(foundAlbums, key = { "fa" + it.title + it.artist }) { a -> AlbumTile(a.title, a.artist, a.artUri, 130.dp) { nav.openAlbum(a) } }
                    }
                }
            }
            item { SectionHeader("Canciones") }
            items(songs, key = { "fs" + it.contentUri }) { s -> SongRow(s, false) { vm.play(songs, songs.indexOf(s)) } }
            if (songs.isEmpty() && foundAlbums.isEmpty()) item { Text("Sin resultados", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

