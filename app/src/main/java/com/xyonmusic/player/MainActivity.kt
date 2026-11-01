package com.xyonmusic.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Artist
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.spotify.Spotify
import com.xyonmusic.player.ui.components.Art
import com.xyonmusic.player.ui.components.CoverFlow
import com.xyonmusic.player.ui.components.press
import com.xyonmusic.player.ui.components.rememberHaptics
import com.xyonmusic.player.ui.player.FullPlayer
import com.xyonmusic.player.ui.player.MiniPlayer
import com.xyonmusic.player.ui.player.SpotifyMiniPlayer
import com.xyonmusic.player.ui.screens.*
import com.xyonmusic.player.ui.theme.BlobsBackground
import com.xyonmusic.player.ui.theme.LocalXyon
import com.xyonmusic.player.ui.theme.XyonTheme
import com.xyonmusic.player.ui.theme.chromaticEdge
import com.xyonmusic.player.ui.theme.glass

enum class Tab(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Rounded.Home),
    FORYOU("4U", Icons.Rounded.AutoAwesome),
    TOP("Top", Icons.AutoMirrored.Rounded.TrendingUp),
    SEARCH("Buscar", Icons.Rounded.Search)
}

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val label = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "Almacenamiento" } }.getOrNull()
            vm.setFolder(uri, label)
        }
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleIntent(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            XyonTheme(theme) { XyonApp(vm, onPickFolder = { pickFolder.launch(null) }) }
        }
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

@Composable
fun XyonApp(vm: PlayerViewModel, onPickFolder: () -> Unit) {
    val c = LocalXyon.current
    val ctx = LocalContext.current
    val lib by vm.lib.collectAsStateWithLifecycle()
    val pl by vm.player.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var album by remember { mutableStateOf<Album?>(null) }
    var artist by remember { mutableStateOf<Artist?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var player by rememberSaveable { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(sp.message) { sp.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.consumeSpotifyMessage() } }
    LaunchedEffect(sp.connected) { if (sp.connected) settings = false }

    val nav = remember {
        Nav(
            openAlbum = { album = it },
            openArtist = { artist = it; album = null; player = false },
            openSettings = { settings = true },
            songMenu = { menuSong = it },
            pickFolder = onPickFolder
        )
    }
    BackHandler(player || settings || album != null || artist != null || tab != Tab.LIBRARY) {
        when {
            player -> player = false
            settings -> settings = false
            album != null -> album = null
            artist != null -> artist = null
            else -> tab = Tab.LIBRARY
        }
    }

    val current = pl.current
    val showSpotifyMini = sp.now != null && (current == null || (sp.now?.playing == true && !pl.playing))
    val hasMini = (current != null || showSpotifyMini)
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navInset + 20.dp + 70.dp + (if (hasMini) 68.dp else 0.dp) + 16.dp

    Box(Modifier.fillMaxSize()) {
        BlobsBackground()

        // Apaisado en Library: CoverFlow horizontal a pantalla completa (como la original)
        val landscapeFlow = landscape && tab == Tab.LIBRARY && artist == null && !player && lib.albums.isNotEmpty()
        if (landscapeFlow) {
            CoverFlow(lib.albums, vertical = false, onOpen = nav.openAlbum, modifier = Modifier.fillMaxSize().systemBarsPadding().padding(vertical = 12.dp))
        } else {
            AnimatedContent(
                tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(400)) + slideInHorizontally(tween(400)) { dir * it / 12 }) togetherWith
                        (fadeOut(tween(350)) + slideOutHorizontally(tween(350)) { -dir * it / 12 })
                },
                label = "tabs"
            ) { t ->
                when (t) {
                    Tab.LIBRARY -> LibraryScreen(vm, nav, bottomInset)
                    Tab.FORYOU -> ForYouScreen(vm, nav, bottomInset)
                    Tab.TOP -> TopScreen(vm, nav, bottomInset)
                    Tab.SEARCH -> SearchScreen(vm, nav, bottomInset)
                }
            }
        }

        // Página de artista
        var shownArtist by remember { mutableStateOf<Artist?>(null) }
        if (artist != null) shownArtist = artist
        AnimatedVisibility(artist != null, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            shownArtist?.let { ArtistScreen(it, vm, nav, bottomInset) { artist = null } }
        }

        // Mini reproductor + barra de navegación
        if (!landscapeFlow) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(current != null && !showSpotifyMini, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                MiniPlayer(vm) { player = true }
            }
            AnimatedVisibility(showSpotifyMini, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { SpotifyMiniPlayer(vm) }
            BottomNav(tab) { tab = it; artist = null }
        }

        // Ficha de álbum
        var shownAlbum by remember { mutableStateOf<Album?>(null) }
        if (album != null) shownAlbum = album
        AnimatedVisibility(album != null, enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f)) {
            shownAlbum?.let { AlbumDialog(it, vm, nav) { album = null } }
        }
        AnimatedVisibility(settings, enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f)) {
            SettingsDialog(vm, onChangeFolder = { settings = false; onPickFolder() }) { settings = false }
        }
        AnimatedVisibility(
            player && current != null,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
        ) {
            FullPlayer(
                vm, onClose = { player = false },
                onGoAlbum = { s -> player = false; lib.albums.firstOrNull { it.name == s.album }?.let { album = it } },
                onGoArtist = { s -> player = false; lib.artists.firstOrNull { it.name == s.artist }?.let { artist = it } }
            )
        }
        if (lib.scanning) ScanningDialog(lib.progress)
    }

    menuSong?.let { s ->
        SongMenu(s, vm, onDismiss = { menuSong = null },
            onAlbum = { lib.albums.firstOrNull { it.name == s.album }?.let { a -> album = a } },
            onArtist = { lib.artists.firstOrNull { it.name == s.artist }?.let { a -> nav.openArtist(a) } })
    }
}

/**
 * Barra de navegación de la original (cápsula de cristal de 70dp con indicador que se
 * desliza + círculo de búsqueda), ahora con borde de aberración cromática suave estilo iOS 26.
 */
@Composable
private fun BottomNav(tab: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    val main = listOf(Tab.LIBRARY, Tab.FORYOU, Tab.TOP)
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val pill = RoundedCornerShape(35.dp)
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clip(pill).glass(pill, c).chromaticEdge(pill, c)) {
            val w = maxWidth / main.size
            val idx = main.indexOf(tab)
            val x by animateDpAsState(w * idx.coerceAtLeast(0), spring(dampingRatio = 0.72f, stiffness = 320f), label = "ind")
            val indAlpha by animateFloatAsState(if (idx >= 0) 1f else 0f, tween(300), label = "inda")
            Box(
                Modifier.offset(x = x).width(w).fillMaxHeight().padding(5.dp).graphicsLayer { alpha = indAlpha }
                    .clip(pill).background(Brush.verticalGradient(listOf(c.text.copy(alpha = 0.12f), c.text.copy(alpha = 0.05f))))
            )
            Row(Modifier.fillMaxSize()) {
                main.forEach { t ->
                    val sel = t == tab
                    val s by animateFloatAsState(if (sel) 1.1f else 1f, spring(dampingRatio = 0.5f), label = "s")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().press(h, 0.9f) { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        val tint = if (sel) c.accent else c.text2
                        Icon(t.icon, t.label, Modifier.size(26.dp).graphicsLayer { scaleX = s; scaleY = s }, tint = tint)
                        Text(t.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        val sel = tab == Tab.SEARCH
        Box(
            Modifier.size(70.dp).clip(CircleShape).glass(CircleShape, c).chromaticEdge(CircleShape, c).press(h, 0.9f) { onSelect(Tab.SEARCH) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Search, "Buscar", Modifier.size(32.dp), tint = if (sel) c.accent else c.text) }
    }
}

/** Menú de pulsación larga sobre una canción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(song: Song, vm: PlayerViewModel, onDismiss: () -> Unit, onAlbum: () -> Unit, onArtist: () -> Unit) {
    val c = LocalXyon.current
    val ctx = LocalContext.current
    val h = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Row(Modifier.padding(20.dp, 0.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(song.uri, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = c.text2, maxLines = 1)
            }
        }
        HorizontalDivider(color = c.border)
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Reproducir después") {
            h.confirm(); vm.playNext(song); Toast.makeText(ctx, "Sonará a continuación", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a la cola") {
            h.confirm(); vm.addToQueue(song); Toast.makeText(ctx, "Añadida a la cola", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        MenuItem(Icons.Rounded.Album, "Ir al álbum") { onDismiss(); onAlbum() }
        MenuItem(Icons.Rounded.Person, "Ir al artista") { onDismiss(); onArtist() }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalXyon.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent)
        Spacer(Modifier.width(20.dp))
        Text(label, color = c.text, fontSize = 16.sp)
    }
}
