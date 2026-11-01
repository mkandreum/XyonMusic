package com.xyonmusic.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.xyonmusic.player.formatMs
import com.xyonmusic.player.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// =============================================================================
// Reproductor a pantalla completa
// =============================================================================
@Composable
fun FullPlayer(vm: PlayerViewModel, onCollapse: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.current ?: return
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val colors = rememberArtColors(s.artUri)
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // Deslizar hacia abajo para cerrar
    val dragY = remember { Animatable(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val lyrics by produceState<String?>(null, s.contentUri) {
        value = withContext(Dispatchers.IO) { Lyrics.read(ctx, s.contentUri) }
    }

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .graphicsLayer {
                val f = (dragY.value / screenH).coerceIn(0f, 1f)
                scaleX = 1f - f * 0.08f; scaleY = scaleX
            }
            .clip(RoundedCornerShape(((dragY.value / screenH) * 120f).coerceIn(0f, 32f).dp))
            .animatedArtBackground(colors)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragY.value > screenH * 0.22f) { haptics.confirm(); onCollapse(); dragY.snapTo(0f) }
                            else dragY.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } }
                ) { ch, dy ->
                    ch.consume()
                    scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                }
            }
            .pointerInput(Unit) { detectTapGestures { } } // no dejar pasar toques al contenido de detrás
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Asa + cerrar
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
                Icon(
                    Icons.Filled.KeyboardArrowDown, "Cerrar", tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart).size(34.dp).bounceClick { haptics.tap(); onCollapse() }
                )
            }
            Spacer(Modifier.weight(0.12f))

            // Carátula / letra
            AnimatedContent(
                targetState = showLyrics && lyrics != null,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                label = "art-lyrics",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            ) { lyr ->
                if (lyr) LyricsView(lyrics.orEmpty())
                else SwipeableArt(s.artUri ?: s.contentUri, ui.playing, haptics, onNext = { vm.next() }, onPrev = { vm.prev() })
            }
            Spacer(Modifier.weight(0.1f))

            // Título
            AnimatedContent(s, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "title") { song ->
                Column(Modifier.fillMaxWidth()) {
                    Text(song.title ?: song.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(song.artist ?: "Artista desconocido", song.album).joinToString(" — "),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SeekBar(ui.posMs, ui.durMs, haptics, onSeek = { vm.seek(it) })
            Spacer(Modifier.height(14.dp))

            // Controles principales
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(52.dp).bounceClick { haptics.confirm(); vm.prev() })
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(Color.White)
                        .bounceClick(0.84f) { haptics.confirm(); vm.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        ui.playing,
                        transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                        label = "pp"
                    ) { playing ->
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pausa" else "Reproducir", tint = Color.Black, modifier = Modifier.size(44.dp))
                    }
                }
                Icon(Icons.Filled.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(52.dp).bounceClick { haptics.confirm(); vm.next() })
            }
            Spacer(Modifier.height(18.dp))

            // Controles secundarios
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val on = Color.White; val off = Color.White.copy(alpha = 0.5f)
                Icon(Icons.Filled.Shuffle, "Aleatorio", tint = if (ui.shuffle) on else off, modifier = Modifier.size(26.dp).bounceClick { haptics.tap(); vm.toggleShuffle() })
                Icon(
                    Icons.Filled.Lyrics, "Letra", tint = if (showLyrics && lyrics != null) on else if (lyrics == null) off.copy(alpha = 0.25f) else off,
                    modifier = Modifier.size(26.dp).bounceClick { haptics.tap(); if (lyrics != null) showLyrics = !showLyrics }
                )
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "A continuación", tint = off, modifier = Modifier.size(28.dp).bounceClick { haptics.tap(); showQueue = true })
                Icon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repetir",
                    tint = if (ui.repeat != Player.REPEAT_MODE_OFF) on else off,
                    modifier = Modifier.size(26.dp).bounceClick { haptics.tap(); vm.cycleRepeat() }
                )
            }
            Spacer(Modifier.weight(0.14f))
        }
    }
    if (showQueue) QueueSheet(vm, haptics) { showQueue = false }
}

/** Carátula que respira con la reproducción y se desliza a los lados para cambiar de canción. */
@Composable
private fun SwipeableArt(model: Any?, playing: Boolean, haptics: Haptics, onNext: () -> Unit, onPrev: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val scale by animateFloatAsState(if (playing) 1f else 0.82f, spring(dampingRatio = 0.55f, stiffness = 260f), label = "art")
    val elev by animateDpAsState(if (playing) 28.dp else 8.dp, label = "elev")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationX = dx.value
                    rotationZ = dx.value / w * 6f
                    scaleX = scale * (1f - abs(dx.value) / w * 0.1f); scaleY = scaleX
                    alpha = 1f - (abs(dx.value) / w).coerceIn(0f, 0.6f)
                }
                .shadow(elev, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val v = dx.value
                                if (abs(v) > w * 0.28f) {
                                    haptics.confirm()
                                    dx.animateTo(if (v < 0) -w else w, tween(160))
                                    if (v < 0) onNext() else onPrev()
                                    dx.snapTo(if (v < 0) w * 0.6f else -w * 0.6f)
                                }
                                dx.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f))
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f) } }
                    ) { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } }
                }
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.Center).size(72.dp))
            AsyncImage(model, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun LyricsView(text: String) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.18f))
    ) {
        Text(
            text,
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            color = Color.White, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

// =============================================================================
// Barra de progreso: se ensancha al tocarla, muestra el tiempo grande y vibra.
// =============================================================================
@Composable
fun SeekBar(pos: Long, dur: Long, haptics: Haptics, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var frac by remember { mutableFloatStateOf(0f) }
    var lastStep by remember { mutableIntStateOf(-1) }
    var atEdge by remember { mutableStateOf(false) }
    val shown = if (dragging) frac else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val barH by animateDpAsState(if (dragging) 12.dp else 5.dp, spring(dampingRatio = 0.6f), label = "barH")
    val timeSize by animateFloatAsState(if (dragging) 17f else 12f, label = "ts")

    fun update(nf: Float) {
        val step = (nf * 24).toInt()
        val edge = nf <= 0f || nf >= 1f
        if (edge && !atEdge) haptics.edge() else if (step != lastStep && !edge) haptics.tick()
        lastStep = step; atEdge = edge; frac = nf
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .pointerInput(dur) {
                    detectTapGestures { o -> haptics.tick(); onSeek(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()) }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; frac = (o.x / size.width).coerceIn(0f, 1f); lastStep = (frac * 24).toInt(); haptics.tap() },
                        onDragEnd = { onSeek((frac * dur).toLong()); dragging = false },
                        onDragCancel = { dragging = false }
                    ) { ch, dx -> ch.consume(); update((frac + dx / size.width).coerceIn(0f, 1f)) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(barH).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(shown).height(barH).clip(CircleShape).background(Color.White.copy(alpha = if (dragging) 1f else 0.85f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val p = if (dragging) (frac * dur).toLong() else pos
            Text(formatMs(p), color = Color.White.copy(alpha = if (dragging) 1f else 0.6f), fontSize = timeSize.sp, fontWeight = if (dragging) FontWeight.Bold else FontWeight.Normal)
            Text("-" + formatMs((dur - p).coerceAtLeast(0)), color = Color.White.copy(alpha = 0.6f), fontSize = timeSize.sp)
        }
    }
}

// =============================================================================
// Cola "A continuación": tocar para saltar, arrastrar el asa para reordenar.
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: PlayerViewModel, haptics: Haptics, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.index.coerceAtLeast(0))
    val target = if (dragFrom >= 0) (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, ui.queue.lastIndex) else -1

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("A continuación", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 4.dp))
        Text(
            "${ui.queue.size} canciones · arrastra ≡ para reordenar",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(ui.queue, key = { i, s -> "$i|${s.contentUri}" }) { i, s ->
                val isDragged = i == dragFrom
                val shift = when {
                    dragFrom < 0 || isDragged -> 0f
                    i in (dragFrom + 1)..target -> -rowPx
                    i in target until dragFrom -> rowPx
                    else -> 0f
                }
                val animShift by animateFloatAsState(shift, spring(stiffness = 600f), label = "shift")
                Row(
                    Modifier.fillMaxWidth().height(rowH)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) dragDy else animShift
                            if (isDragged) { scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 16f }
                        }
                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { haptics.tap(); vm.skipTo(i) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(s.artUri, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title ?: s.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (i == ui.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (i == ui.index) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(s.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (i == ui.index) EqualizerBars(ui.playing, MaterialTheme.colorScheme.primary, Modifier.padding(end = 12.dp))
                    Icon(
                        Icons.Filled.DragHandle, "Reordenar", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(i, ui.queue.size) {
                            detectDragGestures(
                                onDragStart = { dragFrom = i; dragDy = 0f; lastTarget = i; haptics.long() },
                                onDragEnd = {
                                    val t = (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, ui.queue.lastIndex)
                                    if (t != dragFrom) { vm.moveInQueue(dragFrom, t); haptics.confirm() }
                                    dragFrom = -1; dragDy = 0f
                                },
                                onDragCancel = { dragFrom = -1; dragDy = 0f }
                            ) { ch, d ->
                                ch.consume(); dragDy += d.y
                                val t = (dragFrom + (dragDy / rowPx).roundToInt()).coerceIn(0, ui.queue.lastIndex)
                                if (t != lastTarget) { lastTarget = t; haptics.tick() }
                            }
                        }
                    )
                }
            }
        }
    }
}

// =============================================================================
// Mini reproductores (local y Spotify)
// =============================================================================
@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.current ?: return
    val haptics = rememberHaptics()
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth()
            .graphicsLayer { translationY = drag.coerceAtMost(0f) * 0.4f }
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f))
            .pointerInput(Unit) {
                // Deslizar hacia arriba para abrir el reproductor
                detectVerticalDragGestures(onDragEnd = { if (drag < -60f) { haptics.tap(); onOpen() }; drag = 0f }, onDragCancel = { drag = 0f }) { ch, dy ->
                    ch.consume(); drag += dy
                }
            }
            .clickable { haptics.tap(); onOpen() }
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(s.artUri ?: s.contentUri, null, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title ?: s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(s.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.SkipPrevious, "Anterior", Modifier.size(40.dp).padding(6.dp).bounceClick { haptics.confirm(); vm.prev() })
            Box(Modifier.size(40.dp).bounceClick(0.8f) { haptics.confirm(); vm.toggle() }, contentAlignment = Alignment.Center) {
                AnimatedContent(ui.playing, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "mini-pp") { p ->
                    Icon(if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(30.dp))
                }
            }
            Icon(Icons.Filled.SkipNext, "Siguiente", Modifier.size(40.dp).padding(6.dp).bounceClick { haptics.confirm(); vm.next() })
        }
        val prog = if (ui.durMs > 0) (ui.posMs.toFloat() / ui.durMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun SpotifyMiniPlayer(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val sp by vm.sp.collectAsStateWithLifecycle()
    val n = sp.now ?: return
    val haptics = rememberHaptics()
    val green = Color(0xFF1ED760)
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f))
            .clickable { haptics.tap(); vm.spotify.openInSpotify(n.track.uri) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(n.track.image, null, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(16.dp).clip(CircleShape).background(green), contentAlignment = Alignment.Center) {
                EqualizerBars(n.playing, Color.Black, size = 9.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text("Spotify · ${n.track.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = green)
        }
        Box(Modifier.size(40.dp).bounceClick(0.8f) { haptics.confirm(); vm.spotifyToggle() }, contentAlignment = Alignment.Center) {
            AnimatedContent(n.playing, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "sp-pp") { p ->
                Icon(if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(30.dp))
            }
        }
        Icon(Icons.Filled.SkipNext, "Siguiente", Modifier.size(40.dp).padding(6.dp).bounceClick { haptics.confirm(); vm.spotifyNext() })
    }
}

