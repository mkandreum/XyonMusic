package com.xyonmusic.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xyonmusic.player.data.Song

// FIX WebView: placeholder shimmer con ::after + IntersectionObserver manual.
// Nativa: LazyColumn con key estable + Coil (caché disco/memoria), sin base64.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.fillMaxWidth()
            .fadeInOnAppear()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc -> { haptics.long(); lc() } }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artUri ?: song.contentUri,
            contentDescription = song.title ?: song.name,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title ?: song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(song.artist ?: "Artista desconocido", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (playing) {
            Spacer(Modifier.width(8.dp))
            EqualizerBars(isPlaying, MaterialTheme.colorScheme.primary)
        }
    }
}
