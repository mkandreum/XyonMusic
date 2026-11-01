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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.spotify.SpPlaylist
import com.xyonmusic.player.spotify.SpTrack
import com.xyonmusic.player.spotify.Spotify
import com.xyonmusic.player.ui.components.*
import com.xyonmusic.player.ui.screens.*
import com.xyonmusic.player.ui.theme.XyonTheme
import com.xyonmusic.player.ui.theme.XyonTokens

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, audioPerm) != PackageManager.PERMISSION_GRANTED) perm.launch(audioPerm)
        handleIntent(intent)
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            XyonTheme(darkTheme = ui.dark) { XyonApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.toString().startsWith(Spotify.REDIRECT)) { vm.onSpotifyRedirect(data); i.data = null }
    }
}

private data class TabDef(val label: String, val icon: ImageVector)
private val tabs = listOf(
    TabDef("Home", Icons.Filled.Home), TabDef("Albums", Icons.Outlined.Album),
    TabDef("Library", Icons.Outlined.LibraryMusic), TabDef("New", Icons.Filled.GridView)
)

@Composable
fun XyonApp(vm: PlayerViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var album: Album? by remember { mutableStateOf(null) }
    var playlist: SpPlaylist? by remember { mutableStateOf(null) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val current = ui.queue.getOrNull(ui.index)

    LaunchedEffect(sp.message) {
        sp.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.consumeSpotifyMessage() }
    }
    LaunchedEffect(sp.connected) { if (sp.connected) settings = false }

    val nav = remember { Nav({ album = it }, { playlist = it }, { settings = true }, { tab = it }) }

    BackHandler(expanded || settings || album != null || playlist != null || tab != 0) {
        when {
            expanded -> expanded = false
            settings -> settings = false
            playlist != null -> playlist = null
            album != null -> album = null
            else -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val m = Modifier.fillMaxSize()
        when (tab) {
            0 -> HomeScreen(vm, nav, m)
            1 -> AlbumsScreen(vm, nav, m)
            2 -> LibraryScreen(vm, nav, m)
            3 -> NewScreen(vm, nav, m)
            else -> SearchScreen(vm, nav, m)
        }

        AnimatedVisibility(album != null, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            album?.let { AlbumDetail(it, vm, onBack = { album = null }) }
        }
        AnimatedVisibility(playlist != null, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            playlist?.let { PlaylistDetail(it, vm, onBack = { playlist = null }) }
        }

        // Fade inferior para que el contenido "se hunda" bajo la barra de cristal.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.92f))))
        )
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)) {
            AnimatedVisibility(current != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
                MiniPlayer(vm, ui.dark) { expanded = true }
            }
            Spacer(Modifier.height(10.dp))
            TabBar(tab, ui.dark) { tab = it; album = null; playlist = null }
        }

        AnimatedVisibility(settings, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            SettingsScreen(vm, onClose = { settings = false })
        }
        AnimatedVisibility(expanded && current != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            FullPlayer(vm, onCollapse = { expanded = false })
        }
    }
}

@Composable
private fun TabBar(selected: Int, dark: Boolean, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(66.dp).liquidGlass(RoundedCornerShape(33.dp), dark).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = selected == i
                val tint = if (sel) (if (dark) XyonTokens.TabActive else Color(0xFF007AFF)) else MaterialTheme.colorScheme.onBackground
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                        .background(if (sel) (if (dark) Color(0x33FFFFFF) else Color(0x14000000)) else Color.Transparent)
                        .noRippleClick { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(25.dp))
                    Text(t.label, color = tint, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val sel = selected == 4
        Box(
            Modifier.size(66.dp).liquidGlass(CircleShape, dark).noRippleClick { onSelect(4) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Search, "Buscar", Modifier.size(30.dp),
                tint = if (sel) XyonTokens.TabActive else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, dark: Boolean, onOpen: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.queue.getOrNull(ui.index) ?: return
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(30.dp), dark).clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(s.artUri, Modifier.size(42.dp), 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title ?: s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                Text(s.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ vm.toggle() }) {
                Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton({ vm.next() }) { Icon(Icons.Filled.SkipNext, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onBackground) }
        }
        val prog = if (ui.durMs > 0) ui.posMs.toFloat() / ui.durMs else 0f
        Box(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

// ----------------------------- Detalles -----------------------------
@Composable
private fun DetailScaffold(
    title: String, subtitle: String?, art: Any?, onBack: () -> Unit,
    onPlay: () -> Unit, onShuffle: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomInset)) {
            item {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(art, Modifier.size(250.dp), 12.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
                    if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Reproducir", Icons.Filled.PlayArrow, Modifier.weight(1f), onPlay)
                        PillButton("Aleatorio", Icons.Filled.Shuffle, Modifier.weight(1f), onShuffle)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            content()
        }
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(44.dp).liquidGlass(CircleShape).noRippleClick(onBack),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
    }
}

@Composable
private fun AlbumDetail(a: Album, vm: PlayerViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    DetailScaffold(a.title, a.artist, a.artUri, onBack, { vm.play(a.songs, 0) }, { vm.play(a.songs.shuffled(), 0) }) {
        items(a.songs, key = { it.contentUri.toString() }) { s ->
            SongRow(s, ui.queue.getOrNull(ui.index) == s) { vm.play(a.songs, a.songs.indexOf(s)) }
        }
    }
}

@Composable
private fun PlaylistDetail(p: SpPlaylist, vm: PlayerViewModel, onBack: () -> Unit) {
    var tracks by remember(p.id) { mutableStateOf<List<SpTrack>?>(null) }
    LaunchedEffect(p.id) { tracks = vm.spotifyPlaylistTracks(p.id) }
    val list = tracks.orEmpty()
    DetailScaffold(p.name, p.owner, p.image, onBack, { vm.playSpotify(list, 0, p.uri) }, { if (list.isNotEmpty()) vm.playSpotify(list.shuffled(), 0) }) {
        if (tracks == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        items(list, key = { "t" + it.uri + list.indexOf(it) }) { t -> SpotifyRow(t) { vm.playSpotify(list, list.indexOf(t), p.uri) } }
    }
}

// ----------------------------- Ajustes -----------------------------
@Composable
private fun SettingsScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp by vm.sp.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(ui.userName) }
    var clientId by remember(sp.clientId) { mutableStateOf(sp.clientId) }
    val clip = LocalClipboardManager.current
    val green = Color(0xFF1ED760)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(18.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cuenta", Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = { vm.setUserName(name.trim()); onClose() }) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(sp.user?.image, name.ifBlank { sp.user?.name.orEmpty() }, 92.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(name.ifBlank { sp.user?.name ?: "Tu perfil" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item { GroupLabel("PERFIL") }
            item {
                Group {
                    OutlinedTextField(
                        name, { name = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true,
                        label = { Text("Tu nombre (para “… Station”)") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo oscuro", Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
                        Switch(ui.dark, { vm.setDark(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759)))
                    }
                }
            }

            item { GroupLabel("SPOTIFY") }
            item {
                Group {
                    if (sp.connected) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(sp.user?.image, sp.user?.name.orEmpty(), 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sp.user?.name ?: "Conectado", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
                                Text(
                                    if (sp.user?.premium == true) "Spotify Premium · control remoto activo" else "Cuenta Free · las canciones se abren en Spotify",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                                )
                            }
                            Box(Modifier.size(10.dp).clip(CircleShape).background(green))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction("Actualizar biblioteca de Spotify", MaterialTheme.colorScheme.secondary) { vm.refreshSpotify() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction("Desconectar Spotify", Color(0xFFFF453A)) { vm.disconnectSpotify() }
                    } else {
                        Column(Modifier.padding(16.dp)) {
                            Text("Conecta tu cuenta para ver tus playlists, tus me gusta y lo escuchado recientemente, y reproducirlos desde XyonMusic.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                clientId, { clientId = it; vm.setSpotifyClientId(it) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Client ID de Spotify") }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("1. Entra en developer.spotify.com/dashboard y crea una app.\n2. Añade este Redirect URI (toca para copiar):",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                Spotify.REDIRECT, Modifier.padding(vertical = 6.dp).clickable { clip.setText(AnnotatedString(Spotify.REDIRECT)) },
                                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                            Text("3. Marca “Android” y “Web API”, añade tu email en User Management y pega aquí el Client ID.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Spacer(Modifier.height(14.dp))
                            Button(
                                { vm.connectSpotify() }, Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color.Black),
                                shape = RoundedCornerShape(25.dp), enabled = !sp.loading
                            ) { Text(if (sp.loading) "Conectando…" else "Conectar con Spotify", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Spotify no permite que otras apps reproduzcan su audio directamente: XyonMusic manda la reproducción a la app de Spotify (Spotify Connect, requiere Premium) o la abre en Spotify si no hay Premium.",
                    Modifier.padding(horizontal = 32.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(t: String) = Text(t, Modifier.padding(start = 34.dp, top = 24.dp, bottom = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), content = content)

@Composable
private fun SettingsAction(t: String, color: Color, onClick: () -> Unit) =
    Text(t, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), color = color, fontSize = 17.sp)

// ----------------------------- Reproductor -----------------------------
@Composable
private fun FullPlayer(vm: PlayerViewModel, onCollapse: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.queue.getOrNull(ui.index) ?: return
    val artScale by animateFloatAsState(if (ui.playing) 1f else 0.82f, label = "art")
    Box(Modifier.fillMaxSize().background(Color(0xFF151515))) {
        AsyncImage(
            s.artUri, null, Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.75f; scaleX = 1.4f; scaleY = 1.4f },
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xB3000000)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 10.dp).width(40.dp).height(5.dp).clip(CircleShape).background(Color(0x80FFFFFF)).noRippleClick(onCollapse))
            IconButton(onCollapse, Modifier.align(Alignment.Start)) { Icon(Icons.Filled.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.weight(0.4f))
            Artwork(s.artUri, Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale }, 14.dp)
            Spacer(Modifier.weight(0.5f))
            Text(s.title ?: s.name, Modifier.fillMaxWidth(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist ?: "Artista desconocido", Modifier.fillMaxWidth(), color = Color(0xB3FFFFFF), fontSize = 19.sp, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Slider(
                ui.posMs.toFloat(), { vm.seek(it.toLong()) }, valueRange = 0f..ui.durMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xE6FFFFFF), inactiveTrackColor = Color(0x40FFFFFF))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(ui.posMs), color = Color(0x99FFFFFF), fontSize = 12.sp)
                Text("-" + formatMs((ui.durMs - ui.posMs).coerceAtLeast(0)), color = Color(0x99FFFFFF), fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ vm.prev() }, Modifier.size(64.dp)) { Icon(Icons.Filled.FastRewind, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
                IconButton({ vm.toggle() }, Modifier.size(84.dp)) {
                    Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                IconButton({ vm.next() }, Modifier.size(64.dp)) { Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ vm.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, null, tint = if (ui.shuffle) Color.White else Color(0x80FFFFFF)) }
                IconButton({ vm.cycleRepeat() }) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null,
                        tint = if (ui.repeat != Player.REPEAT_MODE_OFF) Color.White else Color(0x80FFFFFF)
                    )
                }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }
}
