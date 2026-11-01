package com.xyonmusic.player.data

import android.net.Uri

// FIX WebView: Song tenía art como base64 en memoria (OOM en listas).
// Nativa: artUri separado (content://) + Coil carga paginada, sin base64.
data class Song(
    val contentUri: Uri,
    val name: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artUri: Uri?
)
