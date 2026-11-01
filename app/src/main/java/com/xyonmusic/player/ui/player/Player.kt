package com.xyonmusic.player.ui.player

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.xyonmusic.player.PlayerViewModel
import com.xyonmusic.player.data.Lyrics
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.data.formatTime
import com.xyonmusic.player.ui.components.*
import com.xyonmusic.player.ui.theme.BlobsBackground
import com.xyonmusic.player.ui.theme.LocalXyon
import com.xyonmusic.player.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


/** Lo que muestra el reproductor, venga de tu música o de Spotify. */
private class PModel(
    val key: String,
    val title: String,
    val artist: String,
    val artLocal: String?,
    val artBitmap: android.graphics.Bitmap?,
    val pos: Long,
    val dur: Long,
    val playing: Boolean,
    val shuffle: Boolean,
    val repeatOn: Boolean,
    val repeatOne: Boolean,
    val spotify: Boolean
)

@Composable
private fun ModelArt(m: PModel, modifier: Modifier) {
    if (m.spotify) {
        val c = LocalXyon.current
        Box(modifier.background(c.placeholder), contentAlignment = Alignment.Center) {
            val b = m.artBitmap
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(0.42f), tint = c.text2.copy(alpha = 0.55f))
        }
    } else Art(m.artLocal, modifier)
}

// =============================================================================
// Reproductor a pantalla completa ("Reproduciendo")
// =============================================================================
@Composable
fun FullPlayer(vm: PlayerViewModel, onClose: () -> Unit, onGoAlbum: (Song) -> Unit, onGoArtist: (Song) -> Unit) {
    val c = LocalXyon.current
    val pl by vm.player.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val rs by vm.remote.state.collectAsStateWithLifecycle()
    val rArt by vm.remote.art.collectAsStateWithLifecycle()
    // Posición de Spotify en vivo (el SDK solo avisa en cambios)
    val rPos by produceState(0L, rs) { while (true) { value = rs?.positionNow() ?: 0L; kotlinx.coroutines.delay(250) } }
    val spotify = source == PlayerViewModel.Source.SPOTIFY && rs != null
    val s = pl.current
    val r = rs
    val m: PModel = when {
        spotify && r != null -> PModel(r.uri, r.title, r.artist, null, rArt, rPos, r.durationMs, !r.paused, r.shuffle, r.repeat != 0, r.repeat == 1, true)
        s != null -> PModel(s.uri, s.title, s.artist, s.uri, null, pl.posMs, pl.durMs, pl.playing, pl.shuffle, pl.repeat != Player.REPEAT_MODE_OFF, pl.repeat == Player.REPEAT_MODE_ONE, false)
        else -> return
    }
    val h = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dragY = remember { Animatable(0f) }
    var menu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val lyrics by produceState<String?>(null, m.key) {
        value = if (m.spotify) null else withContext(Dispatchers.IO) { Lyrics.read(ctx, android.net.Uri.parse(m.key)) }
    }
    val actions = PActions(
        toggle = { if (m.spotify) vm.spotifyToggle() else vm.togglePlay() },
        next = { if (m.spotify) vm.spotifyNext() else vm.next() },
        prev = { if (m.spotify) vm.spotifyPrev() else vm.previous() },
        seek = { ms -> if (m.spotify) vm.spotifySeek(ms) else vm.seekTo(ms) },
        shuffle = { if (m.spotify) vm.spotifyShuffle() else vm.toggleShuffle() },
        repeat = { if (m.spotify) vm.spotifyRepeat() else vm.cycleRepeat() }
    )

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .clip(RoundedCornerShape(((dragY.value / screenH) * 140f).coerceIn(0f, 32f).dp))
            .background(c.bg)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .pointerInput(Unit) {
                // Deslizar hacia abajo para cerrar
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragY.value > screenH * 0.2f) { h.confirm(); onClose(); dragY.snapTo(0f) }
                            else dragY.animateTo(0f, spring(dampingRatio = 0.72f))
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } }
                ) { ch, dy -> ch.consume(); scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) } }
            }
    ) {
        BlobsBackground()
        val blur = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(80.dp) else Modifier.graphicsLayer { alpha = 0.35f }
        ModelArt(m, Modifier.fillMaxSize().then(blur).graphicsLayer { alpha = 0.9f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.35f), c.bg.copy(alpha = 0.85f)))))

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.KeyboardArrowDown, 32, onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reproduciendo", color = c.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (m.spotify) Text("desde Spotify", color = Color(0xFF1ED760), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                    IconBtn(Icons.Rounded.MoreHoriz, 28, { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        if (m.spotify) {
                            DropdownMenuItem({ Text("Abrir en Spotify") }, { menu = false; vm.spotify.openInSpotify(m.key) }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
                        } else s?.let { song ->
                            DropdownMenuItem({ Text("Ir al álbum") }, { menu = false; onGoAlbum(song) }, leadingIcon = { Icon(Icons.Rounded.Album, null) })
                            DropdownMenuItem({ Text("Ir al artista") }, { menu = false; onGoArtist(song) }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
                            DropdownMenuItem({ Text("A continuación") }, { menu = false; showQueue = true }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) })
                            if (lyrics != null) DropdownMenuItem(
                                { Text(if (showLyrics) "Ver carátula" else "Ver letra") }, { menu = false; showLyrics = !showLyrics },
                                leadingIcon = { Icon(Icons.Rounded.Lyrics, null) }
                            )
                        }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight(0.9f).aspectRatio(1f)) { ArtOrLyrics(m, showLyrics, lyrics, h, actions) }
                    Spacer(Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) { Controls(m, actions) }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f)) { ArtOrLyrics(m, showLyrics, lyrics, h, actions) }
                    Spacer(Modifier.height(16.dp))
                    Controls(m, actions)
                }
            }
        }
    }
    if (showQueue) QueueSheet(vm, h) { showQueue = false }
}

private class PActions(
    val toggle: () -> Unit, val next: () -> Unit, val prev: () -> Unit,
    val seek: (Long) -> Unit, val shuffle: () -> Unit, val repeat: () -> Unit
)

@Composable
private fun ArtOrLyrics(m: PModel, showLyrics: Boolean, lyrics: String?, h: Haptics, a: PActions) {
    val c = LocalXyon.current
    AnimatedContent(showLyrics && lyrics != null, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }, label = "al") { lyr ->
        if (lyr) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).glass(RoundedCornerShape(16.dp), c)) {
            Text(lyrics.orEmpty(), Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), color = c.text, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
        } else SwipeArt(m, h, onNext = a.next, onPrev = a.prev)
    }
}

/** Carátula con sombra; se encoge al pausar y se desliza a los lados para cambiar de canción. */
@Composable
private fun SwipeArt(m: PModel, h: Haptics, onNext: () -> Unit, onPrev: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val scale by animateFloatAsState(if (m.playing) 1f else 0.88f, spring(dampingRatio = 0.6f, stiffness = 260f), label = "artScale")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationX = dx.value; rotationZ = dx.value / w * 5f
                    scaleX = scale; scaleY = scale; alpha = 1f - (abs(dx.value) / w).coerceIn(0f, 0.6f)
                }
                .shadow(30.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val v = dx.value
                                if (abs(v) > w * 0.28f) {
                                    h.confirm()
                                    dx.animateTo(if (v < 0) -w else w, tween(150))
                                    if (v < 0) onNext() else onPrev()
                                    dx.snapTo(if (v < 0) w * 0.5f else -w * 0.5f)
                                }
                                dx.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f) } }
                    ) { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } }
                }
        ) { ModelArt(m, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Controls(m: PModel, a: PActions) {
    val c = LocalXyon.current
    val h = rememberHaptics()
    AnimatedContent(Triple(m.key, m.title, m.artist), transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "title") { (_, title, artist) ->
        Column(Modifier.fillMaxWidth()) {
            Text(title, Modifier.fillMaxWidth(), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, Modifier.fillMaxWidth(), color = c.text2, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(16.dp))
    SeekBar(m.pos, m.dur, h) { a.seek(it) }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ToggleBtn(Icons.Rounded.Shuffle, m.shuffle) { h.tap(); a.shuffle() }
        IconBtn(Icons.Rounded.SkipPrevious, 40, { h.confirm(); a.prev() })
        Box(
            Modifier.size(76.dp).shadow(14.dp, CircleShape, spotColor = c.accent, ambientColor = c.accent)
                .clip(CircleShape).background(c.accent).bounceClick(0.86f) { h.confirm(); a.toggle() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(m.playing, transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "pp") { p ->
                Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(44.dp), tint = Color.White)
            }
        }
        IconBtn(Icons.Rounded.SkipNext, 40, { h.confirm(); a.next() })
        ToggleBtn(if (m.repeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, m.repeatOn) { h.tap(); a.repeat() }
    }
}

/** Barra de progreso de la original (pista 6dp, botón blanco 16dp) con vibración al arrastrar. */
@Composable
private fun SeekBar(pos: Long, dur: Long, h: Haptics, onSeek: (Long) -> Unit) {
    val c = LocalXyon.current
    var dragging by remember { mutableStateOf(false) }
    var frac by remember { mutableFloatStateOf(0f) }
    var lastStep by remember { mutableIntStateOf(-1) }
    var atEdge by remember { mutableStateOf(false) }
    val shown = if (dragging) frac else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val thumb by animateFloatAsState(if (dragging) 1.35f else 1f, label = "thumb")
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(dur) { detectTapGestures { o -> h.tick(); onSeek(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; frac = (o.x / size.width).coerceIn(0f, 1f); lastStep = (frac * 24).toInt(); h.tap() },
                        onDragEnd = { onSeek((frac * dur).toLong()); dragging = false },
                        onDragCancel = { dragging = false }
                    ) { ch, dx ->
                        ch.consume()
                        val nf = (frac + dx / size.width).coerceIn(0f, 1f)
                        val step = (nf * 24).toInt(); val edge = nf <= 0f || nf >= 1f
                        if (edge && !atEdge) h.edge() else if (step != lastStep && !edge) h.tick()
                        lastStep = step; atEdge = edge; frac = nf
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.text.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(c.accent))
            }
            Box(
                Modifier.offset(x = w * shown - 8.dp).size(16.dp).graphicsLayer { scaleX = thumb; scaleY = thumb }
                    .shadow(3.dp, CircleShape).background(Color.White, CircleShape)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val p = if (dragging) (frac * dur).toLong() else pos
            Text(formatTime(p), color = if (dragging) c.text else c.text2, fontSize = 13.sp, fontWeight = if (dragging) FontWeight.Bold else FontWeight.Normal)
            Text(formatTime(dur), color = c.text2, fontSize = 13.sp)
        }
    }
}

// =============================================================================
// Cola "A continuación": tocar para saltar, arrastrar ≡ para reordenar
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: PlayerViewModel, h: Haptics, onDismiss: () -> Unit) {
    val c = LocalXyon.current
    val pl by vm.player.collectAsStateWithLifecycle()
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pl.index.coerceAtLeast(0))
    val target = if (dragFrom >= 0) (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, pl.queue.lastIndex) else -1
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Text("A continuación", Modifier.padding(horizontal = 20.dp), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${pl.queue.size} canciones · arrastra ≡ para reordenar", Modifier.padding(horizontal = 20.dp), color = c.text2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(pl.queue, key = { i, s -> "$i|${s.uri}" }) { i, s ->
                val dragged = i == dragFrom
                val shift = when {
                    dragFrom < 0 || dragged -> 0f
                    i in (dragFrom + 1)..target -> -rowPx
                    i in target until dragFrom -> rowPx
                    else -> 0f
                }
                val anim by animateFloatAsState(shift, spring(stiffness = 600f), label = "q")
                Row(
                    Modifier.fillMaxWidth().height(rowH).zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) dragDy else anim; if (dragged) { scaleX = 1.03f; scaleY = 1.03f } }
                        .background(if (dragged) c.placeholder else Color.Transparent)
                        .clickable { h.tap(); vm.skipTo(i) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(s.uri, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = if (i == pl.index) c.accent else c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (i == pl.index) PlayingIcon(pl.playing)
                    Icon(
                        Icons.Rounded.DragHandle, "Reordenar", tint = c.text2,
                        modifier = Modifier.size(44.dp).padding(10.dp).pointerInput(i, pl.queue.size) {
                            detectDragGestures(
                                onDragStart = { dragFrom = i; dragDy = 0f; lastTarget = i; h.long() },
                                onDragEnd = {
                                    val t = (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, pl.queue.lastIndex)
                                    if (t != dragFrom) { vm.moveInQueue(dragFrom, t); h.confirm() }
                                    dragFrom = -1; dragDy = 0f
                                },
                                onDragCancel = { dragFrom = -1; dragDy = 0f }
                            ) { ch, d ->
                                ch.consume(); dragDy += d.y
                                val t = (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, pl.queue.lastIndex)
                                if (t != lastTarget) { lastTarget = t; h.tick() }
                            }
                        }
                    )
                }
            }
        }
    }
}


// =============================================================================
// Mini reproductor (58dp, cristal): tu música o lo que suena en Spotify
// =============================================================================
@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val c = LocalXyon.current
    val pl by vm.player.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val rs by vm.remote.state.collectAsStateWithLifecycle()
    val rArt by vm.remote.art.collectAsStateWithLifecycle()
    val rPos by produceState(0L, rs) { while (true) { value = rs?.positionNow() ?: 0L; kotlinx.coroutines.delay(500) } }
    val r = rs
    val s = pl.current
    val m: PModel = when {
        source == PlayerViewModel.Source.SPOTIFY && r != null -> PModel(r.uri, r.title, r.artist, null, rArt, rPos, r.durationMs, !r.paused, r.shuffle, r.repeat != 0, r.repeat == 1, true)
        s != null -> PModel(s.uri, s.title, s.artist, s.uri, null, pl.posMs, pl.durMs, pl.playing, pl.shuffle, false, false, false)
        else -> return
    }
    val h = rememberHaptics()
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(20.dp)
    val green = Color(0xFF1ED760)
    Box(
        Modifier.fillMaxWidth().height(58.dp)
            .graphicsLayer { translationY = drag.coerceAtMost(0f) * 0.4f }
            .clip(shape).background(c.surface.copy(alpha = if (c.isLight) 0.55f else 0.45f)).glass(shape, c)
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { if (drag < -50f) { h.tap(); onOpen() }; drag = 0f }, onDragCancel = { drag = 0f }) { ch, dy -> ch.consume(); drag += dy }
            }
            .bounceClick(0.98f) { h.tap(); onOpen() }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ModelArt(m, Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)))
                if (m.spotify) Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(16.dp).clip(CircleShape).background(green), contentAlignment = Alignment.Center) {
                    EqualizerBars(m.playing, Color.Black, size = 9.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m.spotify) "Spotify · ${m.artist}" else m.artist, color = if (m.spotify) green else c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 30, { h.confirm(); if (m.spotify) vm.spotifyToggle() else vm.togglePlay() })
            IconBtn(Icons.Rounded.SkipNext, 30, { h.confirm(); if (m.spotify) vm.spotifyNext() else vm.next() })
        }
        val prog = if (m.dur > 0) (m.pos.toFloat() / m.dur).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.text.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(if (m.spotify) green else c.accent))
        }
    }
}

/** Respaldo sin la app de Spotify instalada: lo que suena en Spotify (vía Web API); al tocar abre Spotify. */
@Composable
fun SpotifyMiniPlayer(vm: PlayerViewModel) {
    val c = LocalXyon.current
    val sp by vm.sp.collectAsStateWithLifecycle()
    val n = sp.now ?: return
    val h = rememberHaptics()
    val green = Color(0xFF1ED760)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(shape).background(c.surface.copy(alpha = if (c.isLight) 0.55f else 0.45f)).glass(shape, c)
            .bounceClick(0.98f) { h.tap(); vm.spotify.openInSpotify(n.track.uri) }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(n.track.image, null, Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(c.placeholder))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.track.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Spotify · ${n.track.artist}", color = green, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconBtn(if (n.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 30, { h.confirm(); vm.spotifyToggle() })
        IconBtn(Icons.Rounded.SkipNext, 30, { h.confirm(); vm.spotifyNext() })
    }
}
