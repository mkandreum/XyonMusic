package com.xyonmusic.player.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xyonmusic.player.PlayerViewModel
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Artist
import com.xyonmusic.player.data.Library
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.spotify.Spotify
import com.xyonmusic.player.ui.components.*
import com.xyonmusic.player.ui.theme.BlobsBackground
import com.xyonmusic.player.ui.theme.LocalXyon
import com.xyonmusic.player.ui.theme.THEMES
import com.xyonmusic.player.ui.theme.glass

/** Acciones de navegación que comparten todas las pantallas. */
class Nav(
    val openAlbum: (Album) -> Unit,
    val openArtist: (Artist) -> Unit,
    val openSettings: () -> Unit,
    val songMenu: (Song) -> Unit,
    val pickFolder: () -> Unit,
    val openSpotifyAlbum: (SpAlbum) -> Unit = {}
) {
    /** Abre un elemento del CoverFlow (álbum local o de Spotify). */
    fun openFlow(f: FlowItem) { f.album?.let(openAlbum); f.spotify?.let(openSpotifyAlbum) }
}

private val SpotifyGreen = Color(0xFF1ED760)

// =============================================================================
// LIBRARY
// =============================================================================
@Composable
fun LibraryScreen(vm: PlayerViewModel, nav: Nav, bottomInset: Dp) {
    val c = LocalXyon.current
    val lib by vm.lib.collectAsStateWithLifecycle()
    val pl by vm.player.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var likedOpen by rememberSaveable { mutableStateOf(true) }
    val h = rememberHaptics()
    val flow = remember(lib.albums, sp.liked) { flowItems(lib.albums, spotifyAlbums(sp.liked)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        BigHeader("Library") { GlassCircleBtn(Icons.Rounded.Settings, nav.openSettings) }
        if (lib.songs.isEmpty() && !sp.connected) {
            if (lib.loaded || !lib.hasFolder) Welcome(nav.pickFolder)
            return
        }
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Canciones", !showAlbums) { showAlbums = false }
            Chip("Álbumes", showAlbums) { showAlbums = true }
            Text(
                if (showAlbums) "${flow.size} álbumes" else "${lib.songs.size + sp.liked.size} canciones",
                Modifier.clip(RoundedCornerShape(15.dp)).glass(RoundedCornerShape(15.dp), c, shine = false).padding(horizontal = 12.dp, vertical = 5.dp),
                color = c.text2, fontSize = 13.sp
            )
        }
        if (showAlbums) {
            CoverFlow(flow, vertical = true, onOpen = nav::openFlow, bottomPadding = bottomInset)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset)) {
                // Lista predefinida: "Canciones que te gustan" de Spotify, en el mismo orden que Spotify.
                if (sp.connected) {
                    item(key = "liked-h") {
                        Column(Modifier.padding(bottom = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glass(RoundedCornerShape(16.dp), c)
                                    .clickable { h.tap(); likedOpen = !likedOpen }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White) }
                                Spacer(Modifier.width(15.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Canciones que te gustan", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when {
                                            sp.loading && sp.liked.isEmpty() -> "Spotify · cargando…"
                                            sp.error != null && sp.liked.isEmpty() -> "Spotify · ${sp.error}"
                                            else -> "Spotify · ${sp.liked.size} canciones"
                                        },
                                        color = SpotifyGreen, fontSize = 13.sp, maxLines = 1
                                    )
                                }
                                if (sp.error != null) IconBtn(Icons.Rounded.Refresh, 22, { vm.refreshSpotify() })
                                Icon(if (likedOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.text2)
                            }
                            if (sp.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(2.dp), color = SpotifyGreen, trackColor = Color.Transparent)
                        }
                    }
                    if (likedOpen) itemsIndexed(sp.liked, key = { i, t -> "sp$i${t.uri}" }) { i, t ->
                        val now = sp.now?.track?.uri == t.uri
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { h.tap(); vm.playSpotify(sp.liked, i) }
                                .padding(horizontal = 5.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                AsyncImage(t.image, null, Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(c.placeholder))
                                if (now) Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                    EqualizerBars(sp.now?.playing == true, SpotifyGreen)
                                }
                            }
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, color = if (now) SpotifyGreen else c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = c.text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(com.xyonmusic.player.data.formatTime(t.durationMs), Modifier.padding(start = 10.dp), color = c.text2, fontSize = 14.sp)
                        }
                    }
                    if (lib.songs.isNotEmpty()) item(key = "local-h") { SectionTitle("En tu dispositivo") }
                }
                if (lib.songs.isEmpty() && lib.loaded) item(key = "welcome") { Welcome(nav.pickFolder) }
                items(lib.songs, key = { it.uri }) { s ->
                    SongRow(s, pl.current == s, pl.playing, onLongClick = { nav.songMenu(s) }) { vm.play(lib.songs, lib.songs.indexOf(s)) }
                }
            }
        }
    }
}

/** Pantalla de bienvenida: elegir carpeta de música. */
@Composable
fun Welcome(onPick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = c.accent)
        Spacer(Modifier.height(16.dp))
        Text("Tu música vive aquí", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Selecciona la carpeta donde guardas tu música para empezar a disfrutar.", Modifier.width(300.dp), color = c.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Row(
            Modifier.clip(RoundedCornerShape(30.dp)).glass(RoundedCornerShape(30.dp), c).press(h, 0.95f, onClick = onPick).padding(horizontal = 30.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CreateNewFolder, null, tint = c.text)
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar carpeta", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// =============================================================================
// 4U
// =============================================================================
@Composable
fun ForYouScreen(vm: PlayerViewModel, nav: Nav, bottomInset: Dp) {
    val c = LocalXyon.current
    val fy by vm.forYou.collectAsStateWithLifecycle()
    val pl by vm.player.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val h = rememberHaptics()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = bottomInset)) {
        item { BigHeader("4U") { if (lib.songs.isNotEmpty()) GlassCircleBtn(Icons.Rounded.Refresh) { vm.refreshForYou(true) } } }
        val f = fy
        if (f == null) {
            item { EmptyState(Icons.Rounded.AutoAwesome, "Añade música a tu librería para recibir recomendaciones.") }
            return@LazyColumn
        }
        item {
            SectionTitle("Descubrimiento de álbum")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).glass(RoundedCornerShape(18.dp), c).press(h, 0.98f) { nav.openAlbum(f.album) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(f.album.artUri, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.album.name, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(f.album.artist ?: "", color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(c.accent).press(h, 0.94f) { vm.play(f.album.songs, 0) }.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White)
                        Text(" ${f.album.songs.size} canciones", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            SectionTitle("Canciones para ti")
        }
        items(f.songs, key = { "4u" + it.uri }) { s ->
            SongRow(s, pl.current == s, pl.playing, onLongClick = { nav.songMenu(s) }) { vm.play(f.songs, f.songs.indexOf(s)) }
        }
    }
}

// =============================================================================
// TOP
// =============================================================================
@Composable
fun TopScreen(vm: PlayerViewModel, nav: Nav, bottomInset: Dp) {
    val counts by vm.counts.collectAsStateWithLifecycle()
    val lib by vm.lib.collectAsStateWithLifecycle()
    val pl by vm.player.collectAsStateWithLifecycle()
    val top = remember(counts, lib.songs) {
        lib.songs.filter { (counts[it.uri] ?: 0) > 0 }.sortedByDescending { counts[it.uri] ?: 0 }.take(50)
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = bottomInset)) {
        item { BigHeader("Top") }
        if (top.isEmpty()) { item { EmptyState(Icons.Rounded.BarChart, "Escucha canciones para ver tus Tops.") }; return@LazyColumn }
        item { SectionTitle("Tus canciones más escuchadas") }
        itemsIndexed(top, key = { _, s -> "top" + s.uri }) { i, s ->
            SongRow(s, pl.current == s, pl.playing, number = i + 1, trailing = "${counts[s.uri] ?: 0}×", onLongClick = { nav.songMenu(s) }) { vm.play(top, i) }
        }
    }
}

// =============================================================================
// BUSCAR
// =============================================================================
@Composable
fun SearchScreen(vm: PlayerViewModel, nav: Nav, bottomInset: Dp) {
    val c = LocalXyon.current
    val lib by vm.lib.collectAsStateWithLifecycle()
    val pl by vm.player.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val ql = q.trim().lowercase()
    val songs = remember(ql, lib.songs) { if (ql.isEmpty()) emptyList() else lib.songs.filter { it.title.lowercase().contains(ql) || it.artist.lowercase().contains(ql) || (it.album ?: "").lowercase().contains(ql) } }
    val albums = remember(ql, lib.albums) { if (ql.isEmpty()) emptyList() else lib.albums.filter { it.name.lowercase().contains(ql) || (it.artist ?: "").lowercase().contains(ql) } }
    val artists = remember(ql, lib.artists) { if (ql.isEmpty()) emptyList() else lib.artists.filter { it.name.lowercase().contains(ql) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = bottomInset)) {
        item {
            BigHeader("Buscar")
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).glass(RoundedCornerShape(26.dp), c).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.text2)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Busca canciones, álbumes, artistas...", color = c.text2, maxLines = 1)
                    BasicTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent))
                }
                if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", Modifier.clip(CircleShape).clickable { q = "" }.padding(4.dp), tint = c.text2)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (ql.isNotEmpty() && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item { EmptyState(Icons.Rounded.SearchOff, "No se encontraron resultados.") }
        }
        if (albums.isNotEmpty()) item { SectionTitle("Álbumes"); AlbumTiles(albums, nav.openAlbum) }
        if (artists.isNotEmpty()) {
            item { SectionTitle("Artistas") }
            items(artists, key = { "ar" + it.name }) { a -> ArtistRow(a) { nav.openArtist(a) } }
        }
        if (songs.isNotEmpty()) {
            item { SectionTitle("Canciones") }
            items(songs, key = { "sr" + it.uri }) { s -> SongRow(s, pl.current == s, pl.playing, onLongClick = { nav.songMenu(s) }) { vm.play(songs, songs.indexOf(s)) } }
        }
    }
}

@Composable
fun AlbumTiles(albums: List<Album>, onOpen: (Album) -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { "at" + it.name }) { a ->
            Column(Modifier.width(140.dp).press(h, 0.96f) { onOpen(a) }) {
                Art(a.artUri, Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
                Text(a.name, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist ?: "", color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistRow(a: Artist, onClick: () -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press(h, 0.98f, onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(a.songs.firstOrNull()?.uri, Modifier.size(56.dp).clip(CircleShape))
        Spacer(Modifier.width(15.dp))
        Column {
            Text(a.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.songs.size} canciones", color = c.text2, fontSize = 13.sp)
        }
    }
}

/** Fila Reproducir / Aleatorio. */
@Composable
fun PlayShuffleRow(vm: PlayerViewModel, songs: List<Song>) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillAction(Icons.Rounded.PlayArrow, "Reproducir", Modifier.weight(1f)) { vm.play(songs, 0) }
        PillAction(Icons.Rounded.Shuffle, "Aleatorio", Modifier.weight(1f)) { vm.shuffleAll(songs) }
    }
}

// =============================================================================
// ARTISTA (pantalla completa)
// =============================================================================
@Composable
fun ArtistScreen(artist: Artist, vm: PlayerViewModel, nav: Nav, bottomInset: Dp, onBack: () -> Unit) {
    val c = LocalXyon.current
    val pl by vm.player.collectAsStateWithLifecycle()
    val albums = remember(artist) { Library.albums(artist.songs) }
    Box(Modifier.fillMaxSize().background(c.bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        BlobsBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = bottomInset)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircleBtn(Icons.Rounded.KeyboardArrowLeft, onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(artist.name, Modifier.weight(1f), color = c.text, fontSize = 35.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayShuffleRow(vm, artist.songs)
                if (albums.size > 1 || (albums.size == 1 && albums[0].songs.size > 1)) { SectionTitle("Álbumes"); AlbumTiles(albums, nav.openAlbum) }
                SectionTitle("Canciones")
            }
            items(artist.songs, key = { "as" + it.uri }) { s -> SongRow(s, pl.current == s, pl.playing, onLongClick = { nav.songMenu(s) }) { vm.play(artist.songs, artist.songs.indexOf(s)) } }
        }
    }
}

// =============================================================================
// ÁLBUM (tarjeta emergente)
// =============================================================================
@Composable
fun AlbumDialog(album: Album, vm: PlayerViewModel, nav: Nav, onClose: () -> Unit) {
    val c = LocalXyon.current
    val pl by vm.player.collectAsStateWithLifecycle()
    val h = rememberHaptics()
    Scrim(onClose) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp).fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(22.dp)).background(c.surface).glass(RoundedCornerShape(22.dp), c)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(album.artUri, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.name, color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(album.artist ?: "", color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { album.artist?.let { a -> vm.lib.value.artists.firstOrNull { it.name == a }?.let { onClose(); nav.openArtist(it) } } })
                    Text("${album.songs.size} canciones", color = c.text2, fontSize = 13.sp)
                }
                Icon(Icons.Rounded.Close, "Cerrar", Modifier.size(40.dp).clip(CircleShape).press(h, 0.85f, onClick = onClose).padding(8.dp), tint = c.text)
            }
            Spacer(Modifier.height(14.dp))
            PlayShuffleRow(vm, album.songs)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(album.songs, key = { _, s -> "ad" + s.uri }) { i, s ->
                    SongRow(s, pl.current == s, pl.playing, number = if (s.track > 0) s.track else i + 1, onLongClick = { nav.songMenu(s) }) { vm.play(album.songs, i) }
                }
            }
        }
    }
}

// =============================================================================
// ÁLBUM DE SPOTIFY (de tus "Canciones que te gustan")
// =============================================================================
@Composable
fun SpotifyAlbumDialog(album: SpAlbum, vm: PlayerViewModel, onClose: () -> Unit) {
    val c = LocalXyon.current
    val sp by vm.sp.collectAsStateWithLifecycle()
    val rs by vm.remote.state.collectAsStateWithLifecycle()
    val h = rememberHaptics()
    Scrim(onClose) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp).fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(22.dp)).background(c.surface).glass(RoundedCornerShape(22.dp), c)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(album.image, null, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(c.placeholder))
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.name, color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.tracks.size} que te gustan · Spotify", color = SpotifyGreen, fontSize = 13.sp)
                }
                Icon(Icons.Rounded.Close, "Cerrar", Modifier.size(40.dp).clip(CircleShape).press(h, 0.85f, onClick = onClose).padding(8.dp), tint = c.text)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillAction(Icons.Rounded.PlayArrow, "Álbum completo", Modifier.weight(1f)) {
                    val u = album.uri
                    if (u != null) vm.playSpotifyUri(u, album.tracks) else vm.playSpotify(sp.liked, sp.liked.indexOf(album.tracks.first()))
                    onClose()
                }
                PillAction(Icons.Rounded.Favorite, "Mis me gusta", Modifier.weight(1f)) {
                    vm.playSpotify(sp.liked, sp.liked.indexOf(album.tracks.first())); onClose()
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(album.tracks, key = { i, t -> "sa$i${t.uri}" }) { i, t ->
                    val now = rs?.uri == t.uri || sp.now?.track?.uri == t.uri
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { h.tap(); vm.playSpotify(sp.liked, sp.liked.indexOf(t)) }
                            .padding(horizontal = 5.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            if (now) EqualizerBars(rs?.paused == false || sp.now?.playing == true, SpotifyGreen)
                            else Text("${i + 1}", color = c.text2, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = if (now) SpotifyGreen else c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = c.text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(com.xyonmusic.player.data.formatTime(t.durationMs), color = c.text2, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Fondo oscurecido que cierra al tocar fuera. */
@Composable
fun Scrim(onDismiss: () -> Unit, alpha: Float = 0.55f, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center, content = content
    )
}

// =============================================================================
// AJUSTES (carpeta, tema y Spotify)
// =============================================================================
@Composable
fun SettingsDialog(vm: PlayerViewModel, onChangeFolder: () -> Unit, onClose: () -> Unit) {
    val c = LocalXyon.current
    val lib by vm.lib.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    val h = rememberHaptics()
    var clientId by remember(sp.clientId) { mutableStateOf(sp.clientId) }
    Scrim(onClose) {
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 400.dp).heightIn(max = 640.dp)
                .clip(RoundedCornerShape(22.dp)).background(c.surface).glass(RoundedCornerShape(22.dp), c)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Text("Ajustes", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Carpeta de música", color = c.text, fontWeight = FontWeight.SemiBold)
                    Text(lib.folderLabel ?: "No seleccionada", color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (lib.hasFolder) Icon(Icons.Rounded.Refresh, "Reescanear", Modifier.size(40.dp).clip(CircleShape).press(h, 0.85f) { vm.rescan(); onClose() }.padding(8.dp), tint = c.text)
                Text(
                    "Cambiar",
                    Modifier.clip(RoundedCornerShape(20.dp)).glass(RoundedCornerShape(20.dp), c, shine = false).press(h, 0.94f, onClick = onChangeFolder).padding(horizontal = 16.dp, vertical = 8.dp),
                    color = c.text, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Tema", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                THEMES.forEach { t ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.c1, t.c2)), CircleShape)
                            .then(if (t.id == theme) Modifier.border(3.dp, if (t.id == "light") c.accent else c.text, CircleShape) else Modifier)
                            .press(h, 0.85f) { vm.setTheme(t.id) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (sp.connected) SpotifyGreen else c.text2.copy(alpha = 0.4f)))
                Spacer(Modifier.width(8.dp))
                Text("Spotify", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (sp.connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(sp.user?.image, null, Modifier.size(44.dp).clip(CircleShape).background(c.placeholder))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sp.user?.name ?: "Conectado", color = c.text, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${sp.liked.size} canciones que te gustan · " + when (sp.user?.premium) { true -> "Premium"; false -> "Free (se abren en Spotify)"; null -> "control con Premium" },
                            color = c.text2, fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SpotifyAppStatus(vm)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillAction(Icons.Rounded.Refresh, if (sp.loading) "Cargando…" else "Actualizar", Modifier.weight(1f)) { vm.refreshSpotify() }
                    PillAction(Icons.Rounded.LinkOff, "Desconectar", Modifier.weight(1f)) { vm.disconnectSpotify() }
                }
            } else {
                Text(
                    "Escucha tus \"Canciones que te gustan\" de Spotify desde XyonMusic, en el mismo orden que en Spotify.\n\n" +
                        "1. Crea una app en developer.spotify.com/dashboard (tu cuenta debe ser Premium, requisito de Spotify desde 2026)\n2. Añade el Redirect URI: ${Spotify.REDIRECT}\n3. Añade tu email en User Management\n4. Pega aquí el Client ID",
                    color = c.text2, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                SpotifyAppStatus(vm)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).glass(RoundedCornerShape(24.dp), c).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (clientId.isEmpty()) Text("Client ID de Spotify", color = c.text2)
                    BasicTextField(clientId, { clientId = it; vm.setSpotifyClientId(it) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent))
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(SpotifyGreen).press(h, 0.96f) { vm.connectSpotify() },
                    contentAlignment = Alignment.Center
                ) { Text(if (sp.loading) "Conectando…" else "Conectar con Spotify", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Spotify no deja que otras apps reproduzcan su audio: XyonMusic lo envía a la app de Spotify (Premium) o la abre en Spotify.",
                    color = c.text2.copy(alpha = 0.8f), fontSize = 11.sp
                )
            }
        }
    }
}

/** Estado de la app de Spotify (SDK) + huella SHA-1 a registrar en Spotify for Developers. */
@Composable
private fun SpotifyAppStatus(vm: PlayerViewModel) {
    val c = LocalXyon.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val rs by vm.remote.state.collectAsStateWithLifecycle()
    val sha1 = remember { com.xyonmusic.player.spotify.SpotifyRemote.signingSha1(ctx) }
    val installed = remember { vm.remote.installed }
    val linked = vm.remote.connected || rs != null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp), c, shine = false).padding(12.dp)) {
        Text(
            when {
                !installed -> "Instala la app de Spotify para reproducir sin salir de XyonMusic."
                linked -> "Conectado con la app de Spotify: se reproduce dentro de XyonMusic."
                else -> "Al reproducir algo de Spotify se conectará con su app (puede pedirte permiso la primera vez)."
            },
            color = if (linked) SpotifyGreen else c.text2, fontSize = 13.sp
        )
        if (sha1 != null) {
            Spacer(Modifier.height(8.dp))
            Text("En tu app de Spotify for Developers → Android packages añade:", color = c.text2, fontSize = 12.sp)
            Text("Paquete: ${ctx.packageName}", color = c.text, fontSize = 12.sp, modifier = Modifier.clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(ctx.packageName)) })
            Text("SHA-1: $sha1", color = c.text, fontSize = 12.sp, modifier = Modifier.clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(sha1)) })
            Text("(toca para copiar)", color = c.text2.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

// =============================================================================
// ESCANEANDO
// =============================================================================
@Composable
fun ScanningDialog(progress: Float) {
    val c = LocalXyon.current
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    Scrim({}, 0.5f) {
        Column(
            Modifier.fillMaxWidth(0.85f).widthIn(max = 320.dp).clip(RoundedCornerShape(22.dp)).background(c.surface).glass(RoundedCornerShape(22.dp), c).padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(48.dp).graphicsLayer { rotationZ = r }, tint = c.accent)
            Spacer(Modifier.height(14.dp))
            Text("Analizando tu música...", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.text.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
            }
            Spacer(Modifier.height(10.dp))
            Text("${(progress * 100).toInt()}%", color = c.text2, fontWeight = FontWeight.SemiBold)
        }
    }
}
