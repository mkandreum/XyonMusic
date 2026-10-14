package com.xyonmusic.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Album
import kotlin.math.abs

// Réplica nativa del CoverFlow web (initHCoverFlow/updateHCoverFlowLayout + drag/snap manual).
// FIX web: velocityTracker casero + animate() por frame + base64 en memoria.
// Nativa: LazyRow + graphicsLayer en fase layout (sin recomponer por frame), Coil con caché, keys estables.
@Composable
fun AlbumCoverFlow(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) return
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
            Card(
                onClick = { onAlbumClick(album) },
                modifier = Modifier
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
