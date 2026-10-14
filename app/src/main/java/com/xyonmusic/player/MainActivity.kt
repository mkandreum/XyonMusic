package com.xyonmusic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.ui.components.SongRow
import com.xyonmusic.player.ui.screens.ForYouScreen
import com.xyonmusic.player.ui.screens.LibraryScreen
import com.xyonmusic.player.ui.screens.TopScreen
import com.xyonmusic.player.ui.theme.XyonTheme

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) perm.launch(Manifest.permission.READ_MEDIA_AUDIO)
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            XyonTheme(darkTheme = dark) { XyonApp(vm, dark = dark, onToggleTheme = { dark = !dark }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XyonApp(vm: PlayerViewModel, dark: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var album: Album? by remember { mutableStateOf(null) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val current = ui.songs.getOrNull(ui.index)

    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text("XyonMusic") },
                actions = { IconButton(onToggleTheme) { Icon(if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Tema") } }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = current != null && !expanded) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(current?.artUri ?: current?.contentUri, null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current?.title ?: current?.name ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(current?.artist ?: "", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.prev() }) { Icon(Icons.Filled.SkipPrevious, null) }
                        IconButton({ vm.toggle() }) { Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
                        IconButton({ vm.next() }) { Icon(Icons.Filled.SkipNext, null) }
                    }
                }
                NavigationBar {
                    listOf(
                        "Biblioteca" to Icons.Filled.LibraryMusic,
                        "Para ti" to Icons.Filled.AutoAwesome,
                        "Top" to Icons.Filled.TrendingUp,
                        "Buscar" to Icons.Filled.Search
                    ).forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, l) }, label = { Text(l) })
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> LibraryScreen(vm, "", onAlbumClick = { album = it }, Modifier.fillMaxSize())
                1 -> ForYouScreen(vm, onAlbumClick = { album = it }, Modifier.fillMaxSize())
                2 -> TopScreen(vm, onAlbumClick = { album = it }, Modifier.fillMaxSize())
                else -> SearchUi(query, { query = it }, vm, onAlbumClick = { album = it })
            }
        }
        album?.let { a ->
            ModalBottomSheet(onDismissRequest = { album = null }) {
                Column(Modifier.padding(16.dp, 0.dp, 16.dp, 32.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(a.artUri, null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.artist ?: "${a.songs.size} canciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.height(320.dp)) {
                        items(a.songs, key = { it.contentUri.toString() }) { s ->
                            SongRow(s, false, onClick = { vm.play(a.songs, a.songs.indexOf(s)); album = null })
                        }
                    }
                }
            }
        }
        if (expanded && current != null) FullPlayer(vm, onCollapse = { expanded = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchUi(q: String, onQ: (String) -> Unit, vm: PlayerViewModel, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(query = q, onQueryChange = onQ, onSearch = {}, active = false, onActiveChange = {}, placeholder = { Text("Buscar canciones, artistas, álbumes") }) {}
        Spacer(Modifier.height(8.dp))
        LibraryScreen(vm, q, onAlbumClick, Modifier.fillMaxSize())
    }
}

@Composable
private fun FullPlayer(vm: PlayerViewModel, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.songs.getOrNull(ui.index) ?: return
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) { IconButton(onCollapse) { Icon(Icons.Filled.ExpandMore, "Cerrar") } }
            AsyncImage(s.artUri ?: s.contentUri, null, modifier = Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(20.dp))
            Text(s.title ?: s.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist ?: "Artista desconocido", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.album ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Slider(value = ui.posMs.toFloat(), onValueChange = { vm.seek(it.toLong()) }, valueRange = 0f..ui.durMs.coerceAtLeast(1).toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(ui.posMs)); Text(formatMs(ui.durMs))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton({ vm.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, null, tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton({ vm.prev() }) { Icon(Icons.Filled.SkipPrevious, null, modifier = Modifier.size(40.dp)) }
                FilledIconButton({ vm.toggle() }, modifier = Modifier.size(72.dp)) {
                    Icon(if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, modifier = Modifier.size(36.dp))
                }
                IconButton({ vm.next() }) { Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(40.dp)) }
                IconButton({ vm.cycleRepeat() }) {
                    when (ui.repeat) {
                        Player.REPEAT_MODE_ALL -> Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                        Player.REPEAT_MODE_ONE -> Icon(Icons.Filled.RepeatOneOn, null, tint = MaterialTheme.colorScheme.primary)
                        else -> Icon(Icons.Filled.Repeat, null)
                    }
                }
            }
        }
    }
}
