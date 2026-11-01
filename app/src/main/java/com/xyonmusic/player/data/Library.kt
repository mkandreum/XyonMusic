package com.xyonmusic.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class Song(
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val track: Int
)

data class Album(val name: String, val artist: String?, val songs: List<Song>) {
    val artUri: String? get() = songs.firstOrNull()?.uri
}

data class Artist(val name: String, val songs: List<Song>)

data class ForYou(val album: Album, val songs: List<Song>)

const val UNKNOWN_ARTIST = "Artista desconocido"
const val UNKNOWN_ALBUM = "Álbum desconocido"

object Library {
    val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "mka", "wma", "alac", "aiff")

    /** Escanea recursivamente la carpeta elegida con el selector de documentos (SAF). */
    suspend fun scan(ctx: Context, tree: Uri, onProgress: (Float) -> Unit): List<Song> = withContext(Dispatchers.IO) {
        val files = mutableListOf<Pair<Uri, String>>()
        val stack = ArrayDeque<String>().apply { addLast(DocumentsContract.getTreeDocumentId(tree)) }
        while (stack.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, stack.removeLast())
            ctx.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) stack.addLast(id)
                    else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXT)
                        files += DocumentsContract.buildDocumentUriUsingTree(tree, id) to name
                }
            }
        }
        if (files.isEmpty()) onProgress(1f)
        files.mapIndexed { i, (uri, name) ->
            val base = name.substringBeforeLast('.')
            val parts = base.split(" - ").map { it.trim() }
            val fnArtist = if (parts.size > 1) parts[0] else UNKNOWN_ARTIST
            val fnTitle = if (parts.size > 1) parts[1] else base
            val r = MediaMetadataRetriever()
            val song = try {
                r.setDataSource(ctx, uri)
                fun m(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
                Song(
                    uri = uri.toString(), fileName = name,
                    title = m(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fnTitle,
                    artist = m(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: m(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: fnArtist,
                    album = m(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: UNKNOWN_ALBUM,
                    durationMs = m(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    track = m(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0
                )
            } catch (_: Exception) {
                Song(uri.toString(), name, fnTitle, fnArtist, UNKNOWN_ALBUM, 0, 0)
            } finally {
                runCatching { r.release() }
            }
            onProgress((i + 1f) / files.size)
            song
        }.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    fun albums(songs: List<Song>): List<Album> =
        songs.groupBy { it.album ?: UNKNOWN_ALBUM }
            .map { (name, s) ->
                val artist = s.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
                Album(name, artist, s.sortedWith(compareBy({ it.track.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })))
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    fun artists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }.map { (n, s) -> Artist(n, s) }.sortedBy { it.name.lowercase(Locale.ROOT) }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

/** Carátulas incrustadas en los archivos (como en la app original), con caché en memoria. */
object ArtCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun peek(uri: String): Bitmap? = cache.get(uri)
    fun isMissing(uri: String) = synchronized(missing) { uri in missing }

    suspend fun load(ctx: Context, uri: String, maxPx: Int = 640): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(uri)?.let { return@withContext it }
        if (isMissing(uri)) return@withContext null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(uri))
            val bytes = r.embeddedPicture
            if (bytes == null) { synchronized(missing) { missing += uri }; return@withContext null }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp != null) cache.put(uri, bmp) else synchronized(missing) { missing += uri }
            bmp
        } catch (_: Exception) {
            synchronized(missing) { missing += uri }; null
        } finally {
            runCatching { r.release() }
        }
    }
}
