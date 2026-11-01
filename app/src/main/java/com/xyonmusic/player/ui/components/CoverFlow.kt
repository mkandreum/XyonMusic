package com.xyonmusic.player.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Album
import kotlin.math.abs

// Réplica nativa del CoverFlow web (initHCoverFlow/updateHCoverFlowLayout + drag/snap manual).
// Horizontal (landscape): se mantiene tal cual.
// Vertical (portrait): CoverFlow 3D centrado con snap, portadas grandes y título del álbum central.
@Composable
fun AlbumCoverFlow(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) return
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        PortraitCoverFlow(albums, onAlbumClick, modifier)
        return
    }
    val listState = rememberLazyListState()
    LazyRow(state = listState, modifier = modifier, contentPadding = PaddingValues(horizontal = 32.dp)) {
        items(albums, key = { it.title + "|" + (it.artist ?: "") }) { album ->
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.key == album.title + "|" + (album.artist ?: "") }
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val dist = if (item == null) 400f else abs((item.offset + item.size / 2) - viewportCenter).toFloat()
            val norm = (dist / 450f).coerceIn(0f, 1f)
            val scale = 1f - 0.22f * norm
            val alpha = 1f - 0.45f * norm
            var rect by remember { mutableStateOf<Rect?>(null) }
            Card(
                onClick = { ArtOrigin.set(rect); onAlbumClick(album) },
                modifier = Modifier
                    .onGloballyPositioned { rect = it.boundsInRoot() }
                    .width(160.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    AsyncImage(
                        model = album.artUri,
                        contentDescription = album.title,
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp, 6.dp, 8.dp, 0.dp)
                    )
                    Text(
                        "${album.songs.size} canciones", maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitCoverFlow(albums: List<Album>, onAlbumClick: (Album) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(listState)
    // Álbum más cercano al centro (solo recompone cuando cambia, no en cada frame).
    val centered by remember {
        derivedStateOf {
            val l = listState.layoutInfo
            val c = (l.viewportStartOffset + l.viewportEndOffset) / 2
            l.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - c) }?.index ?: 0
        }
    }
    val haptics = rememberHaptics()
    LaunchedEffect(listState) {
        snapshotFlow { centered }.distinctUntilChanged().drop(1).collect { haptics.tick() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cover = (maxWidth * 0.56f).coerceAtMost(260.dp)
        val side = (maxWidth - cover) / 2
        Column(Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = side, vertical = 18.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((-cover * 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(albums, key = { _, a -> a.title + "|" + (a.artist ?: "") }) { i, album ->
                    var rect by remember { mutableStateOf<Rect?>(null) }
                    Box(
                        Modifier
                            .zIndex(-abs(i - centered).toFloat())
                            .size(cover)
                            .graphicsLayer {
                                // Cálculo en fase de dibujo: sin recomposición por frame.
                                val l = listState.layoutInfo
                                val info = l.visibleItemsInfo.firstOrNull { it.index == i }
                                val c = (l.viewportStartOffset + l.viewportEndOffset) / 2f
                                val d = if (info == null) 1f else ((info.offset + info.size / 2f - c) / info.size).coerceIn(-1.5f, 1.5f)
                                val n = abs(d).coerceAtMost(1f)
                                rotationY = -d.coerceIn(-1f, 1f) * 50f
                                scaleX = 1f - 0.2f * n; scaleY = scaleX
                                alpha = 1f - 0.35f * n
                                cameraDistance = 14f * density
                            }
                            .shadow(18.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .onGloballyPositioned { rect = it.boundsInRoot() }
                            .clickable { haptics.tap(); ArtOrigin.set(rect); onAlbumClick(album) }
                    ) {
                        AsyncImage(album.artUri, album.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        // Brillo sutil superior, como el reflejo del CoverFlow original.
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color.Transparent, Color(0x33000000)))))
                    }
                }
            }
            val a = albums.getOrNull(centered)
            Text(
                a?.title ?: "", Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(a?.artist, a?.let { "${it.songs.size} canciones" }).joinToString(" · "),
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
