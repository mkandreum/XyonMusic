package com.xyonmusic.player.data

import android.net.Uri

data class Album(
    val title: String,
    val artist: String?,
    val songs: List<Song>,
    val artUri: Uri? = songs.firstOrNull { it.artUri != null }?.artUri
)

fun List<Song>.toAlbums(): List<Album> =
    groupBy { (it.album ?: "Desconocido") + "||" + (it.artist ?: "") }
        .map { (k, v) ->
            val title = k.substringBefore("||").ifBlank { "Desconocido" }
            val artist = k.substringAfter("||").ifBlank { null }
            Album(title, artist, v.sortedBy { it.title ?: it.name })
        }.sortedBy { it.title.lowercase() }
