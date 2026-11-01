package com.xyonmusic.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.data.toAlbums
import com.xyonmusic.player.spotify.Spotify
import com.xyonmusic.player.ui.components.bounceClick
import com.xyonmusic.player.ui.components.rememberHaptics
import com.xyonmusic.player.ui.player.FullPlayer
import com.xyonmusic.player.ui.player.MiniPlayer
import com.xyonmusic.player.ui.player.SpotifyMiniPlayer
import com.xyonmusic.player.ui.screens.AlbumDetail
import com.xyonmusic.player.ui.screens.AudioPermission
import com.xyonmusic.player.ui.screens.ForYouScreen
import com.xyonmusic.player.ui.screens.LibraryScreen
import com.xyonmusic.player.ui.screens.LocalAudioPermission
import com.xyonmusic.player.ui.screens.LocalBottomInset
import com.xyonmusic.player.ui.screens.TopScreen
import com.xyonmusic.player.ui.theme.XyonTheme

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private var permGranted by mutableStateOf(true)
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permGranted = ok
        if (ok) vm.reload()
    }

    private fun checkPerm() = ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        permGranted = checkPerm()
        if (!permGranted) perm.launch(audioPerm)
        handleIntent(intent)
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            XyonTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAudioPermission provides AudioPermission(permGranted) { perm.launch(audioPerm) }) {
                    XyonApp(vm, dark = dark, onToggleTheme = { dark = !dark })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val now = checkPerm()
        if (now && !permGranted) vm.reload()
        permGranted = now
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Vuelta del login OAuth de Spotify (xyonmusic://spotify-callback?code=...)
    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.toString().startsWith(Spotify.REDIRECT)) { vm.onSpotifyRedirect(data); i.data = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XyonApp(vm: PlayerViewModel, dark: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var album: Album? by remember { mutableStateOf(null) }
    var shownAlbum: Album? by remember { mutableStateOf(null) }
    if (album != null) shownAlbum = album
    var menuSong: Song? by remember { mutableStateOf(null) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val current = ui.current
    val sp by vm.sp.collectAsStateWithLifecycle()
    var spotifySettings by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var bottomInset by remember { mutableStateOf(0.dp) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(sp.message) {
        sp.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.consumeSpotifyMessage() }
    }
    BackHandler(expanded || album != null || tab != 0) {
        when {
            expanded -> expanded = false
            album != null -> album = null
            else -> tab = 0
        }
    }
    val openAlbum: (Album) -> Unit = { album = it }
    val openMenu: (Song) -> Unit = { menuSong = it }

    CompositionLocalProvider(LocalBottomInset provides bottomInset) {
        Box(modifier.fillMaxSize()) {
            Scaffold(
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text("XyonMusic", fontWeight = FontWeight.Bold) },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton({ haptics.tap(); onToggleTheme() }) { Icon(if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Tema") }
                            // Círculo de usuario: abre Ajustes (conexión Spotify)
                            Box(Modifier.padding(end = 8.dp).size(40.dp).bounceClick(0.85f) { haptics.tap(); spotifySettings = true }, contentAlignment = Alignment.Center) {
                                val img = sp.user?.image
                                if (img != null) AsyncImage(img, "Ajustes", modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                else Icon(Icons.Filled.Person, "Ajustes", modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp))
                            }
                        }
                    )
                }
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 10 }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -dir * it / 10 })
                        },
                        label = "tabs"
                    ) { t ->
                        when (t) {
                            0 -> LibraryScreen(vm, "", onAlbumClick = openAlbum, Modifier.fillMaxSize(), onSongMenu = openMenu)
                            1 -> ForYouScreen(vm, onAlbumClick = openAlbum, Modifier.fillMaxSize(), onSongMenu = openMenu)
                            2 -> TopScreen(vm, onAlbumClick = openAlbum, Modifier.fillMaxSize(), onSongMenu = openMenu)
                            else -> SearchUi(query, { query = it }, vm, onAlbumClick = openAlbum, onSongMenu = openMenu)
                        }
                    }
                }
            }

            // Ficha de álbum (encima del contenido, debajo de la barra inferior)
            AnimatedVisibility(album != null, enter = fadeIn(tween(1)), exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.96f)) {
                shownAlbum?.let { a -> AlbumDetail(a, vm, bottomInset, onSongMenu = openMenu, onBack = { album = null }) }
            }

            // Mini reproductor + barra de navegación translúcidos (el contenido pasa por detrás)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { bottomInset = with(density) { it.height.toDp() } }
            ) {
                val showSpotify = sp.now != null && (current == null || (sp.now?.playing == true && !ui.playing))
                AnimatedVisibility(current != null && !expanded && !showSpotify, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    MiniPlayer(vm, onOpen = { expanded = true })
                }
                AnimatedVisibility(showSpotify && !expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    SpotifyMiniPlayer(vm)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.9f)) {
                    listOf<Pair<String, ImageVector>>(
                        "Biblioteca" to Icons.Filled.LibraryMusic,
                        "Para ti" to Icons.Filled.AutoAwesome,
                        "Top" to Icons.Filled.TrendingUp,
                        "Buscar" to Icons.Filled.Search
                    ).forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { if (tab != i) haptics.tap(); tab = i; album = null },
                            icon = { Icon(ic, l) }, label = { Text(l) }
                        )
                    }
                }
            }

            // Reproductor completo
            AnimatedVisibility(
                expanded && current != null,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260))
            ) { FullPlayer(vm, onCollapse = { expanded = false }) }
        }
    }

    menuSong?.let { s ->
        SongMenu(s, vm, onDismiss = { menuSong = null }, onOpenAlbum = { a -> menuSong = null; expanded = false; album = a })
    }
    if (spotifySettings) {
        ModalBottomSheet(onDismissRequest = { spotifySettings = false }) {
            SpotifySettings(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchUi(q: String, onQ: (String) -> Unit, vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, onSongMenu: (Song) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchBar(query = q, onQueryChange = onQ, onSearch = {}, active = false, onActiveChange = {}, placeholder = { Text("Buscar canciones, artistas, álbumes") }) {}
        Spacer(Modifier.height(8.dp))
        LibraryScreen(vm, q, onAlbumClick, Modifier.fillMaxSize(), onSongMenu = onSongMenu)
    }
}

/** Menú de pulsación larga sobre una canción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(song: Song, vm: PlayerViewModel, onDismiss: () -> Unit, onOpenAlbum: (Album) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(20.dp, 0.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.artUri, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title ?: song.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "Artista desconocido", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        HorizontalDivider()
        MenuItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Reproducir después") {
            haptics.confirm(); vm.playNext(song)
            Toast.makeText(ctx, "Sonará a continuación", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Añadir a la cola") {
            haptics.confirm(); vm.addToQueue(song)
            Toast.makeText(ctx, "Añadida a la cola", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        MenuItem(Icons.Filled.Album, "Ir al álbum") {
            haptics.tap()
            ui.songs.toAlbums().firstOrNull { song in it.songs }?.let(onOpenAlbum) ?: onDismiss()
        }
        MenuItem(Icons.Filled.Person, "Ir al artista") {
            haptics.tap()
            val artist = song.artist ?: "Artista desconocido"
            val songs = ui.songs.filter { (it.artist ?: "Artista desconocido") == artist }.sortedWith(compareBy({ it.album ?: "" }, { it.title ?: it.name }))
            onOpenAlbum(Album(artist, "${songs.toAlbums().size} álbumes", songs))
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SpotifySettings(vm: PlayerViewModel) {
    val sp by vm.sp.collectAsStateWithLifecycle()
    var clientId by remember(sp.clientId) { mutableStateOf(sp.clientId) }
    val green = Color(0xFF1ED760)
    Column(Modifier.padding(20.dp, 0.dp, 20.dp, 36.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Spotify", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (sp.connected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(sp.user?.image, null, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sp.user?.name ?: "Conectado", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${sp.liked.size} canciones que te gustan" + if (sp.user?.premium == true) " · Premium" else " · Free (se abren en Spotify)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.refreshSpotify() }, enabled = !sp.loading) { Text(if (sp.loading) "Cargando…" else "Actualizar") }
                OutlinedButton({ vm.disconnectSpotify() }) { Text("Desconectar") }
            }
        } else {
            Text(
                "1. Crea una app en developer.spotify.com/dashboard\n2. Añade el Redirect URI: ${Spotify.REDIRECT}\n3. Añade tu email en User Management\n4. Pega aquí el Client ID",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(clientId, { clientId = it; vm.setSpotifyClientId(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Client ID") })
            Spacer(Modifier.height(12.dp))
            Button(
                { vm.connectSpotify() }, Modifier.fillMaxWidth(), enabled = !sp.loading,
                colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color.Black)
            ) { Text(if (sp.loading) "Conectando…" else "Conectar con Spotify") }
        }
    }
}
