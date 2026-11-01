package com.xyonmusic.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// FIX WebView processDirectoryForUris():
// - solo listaba 1 nivel (buildChildDocumentsUriUsingTree sin recursión)
// - filtraba mal mime (lógica invertida z flag)
// - hacía clearAll()+insertAll sin metadata, y mandaba JSON gigante por evaluateJavascript
// Nativa: MediaStore recursivo real, sin SAF obligatorio, con paginación.
class MusicRepository(private val context: Context) {
    suspend fun loadAll(): List<Song> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(collection, proj, sel, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(id))
                val aId = c.getLong(albId)
                val art: Uri? = if (aId > 0)
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aId)
                else null
                out += Song(uri, c.getString(name) ?: "?", c.getString(title), c.getString(artist), c.getString(album), c.getLong(dur).takeIf { it > 0 }, art, c.getLong(added))
            }
        }
        out
    }
}
