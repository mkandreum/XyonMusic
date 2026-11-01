package com.xyonmusic.player

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.xyonmusic.player.data.Album
import com.xyonmusic.player.data.Artist
import com.xyonmusic.player.data.ForYou
import com.xyonmusic.player.data.Library
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.playback.MusicService
import com.xyonmusic.player.playback.MusicService.Companion.toMediaItem
import com.xyonmusic.player.spotify.SpNow
import com.xyonmusic.player.spotify.SpTrack
import com.xyonmusic.player.spotify.SpUser
import com.xyonmusic.player.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false,
    val scanning: Boolean = false,
    val progress: Float = 0f,
    val folderLabel: String? = null,
    val hasFolder: Boolean = false
)

data class PlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val posMs: Long = 0L,
    val durMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
) {
    val current: Song? get() = queue.getOrNull(index)
}

data class SpotifyUiState(
    val connected: Boolean = false,
    val clientId: String = "",
    val user: SpUser? = null,
    val liked: List<SpTrack> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val now: SpNow? = null
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("xyon", Context.MODE_PRIVATE)
    private var controller: MediaController? = null
    private var restored = false

    private val _lib = MutableStateFlow(LibraryState())
    val lib: StateFlow<LibraryState> = _lib.asStateFlow()
    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()
    private val _theme = MutableStateFlow(prefs.getString("theme", "default") ?: "default")
    val theme: StateFlow<String> = _theme.asStateFlow()
    private val _counts = MutableStateFlow(loadCounts())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
    private val _forYou = MutableStateFlow<ForYou?>(null)
    val forYou: StateFlow<ForYou?> = _forYou.asStateFlow()

    val spotify = Spotify(app)
    private val _sp = MutableStateFlow(SpotifyUiState())
    val sp: StateFlow<SpotifyUiState> = _sp.asStateFlow()

    init {
        _lib.value = _lib.value.copy(
            folderLabel = prefs.getString("folder_label", null),
            hasFolder = prefs.getString("folder", null) != null,
            loaded = prefs.getString("folder", null) == null
        )
        _sp.value = SpotifyUiState(connected = spotify.connected, clientId = spotify.clientId, liked = if (spotify.connected) loadLikedCache() else emptyList())
        if (spotify.connected) refreshSpotify()
        connect()
        prefs.getString("folder", null)?.let { scan(Uri.parse(it)) }

        viewModelScope.launch {
            var lastId: String? = null
            while (isActive) {
                controller?.let { c ->
                    val id = c.currentMediaItem?.mediaId
                    if (id != null && id != lastId) { lastId = id; countPlay(id) }
                    _player.value = _player.value.copy(
                        playing = c.isPlaying,
                        posMs = c.currentPosition.coerceAtLeast(0),
                        durMs = c.duration.takeIf { it > 0 } ?: _player.value.durMs,
                        index = if (_player.value.queue.isEmpty()) -1 else c.currentMediaItemIndex,
                        shuffle = c.shuffleModeEnabled,
                        repeat = c.repeatMode
                    )
                }
                delay(250)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (spotify.connected) _sp.value = _sp.value.copy(now = runCatching { spotify.nowPlaying() }.getOrNull())
                delay(if (_sp.value.now?.playing == true) 4000 else 10000)
            }
        }
        viewModelScope.launch { while (isActive) { delay(5000); savePlayer() } }
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, android.content.ComponentName(app, MusicService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({ controller = f.get(); restorePlayer() }, MoreExecutors.directExecutor())
    }

    // ------------------------------------------------------------------ Biblioteca
    fun setFolder(uri: Uri, label: String?) {
        prefs.edit().putString("folder", uri.toString()).putString("folder_label", label).apply()
        _lib.value = _lib.value.copy(folderLabel = label, hasFolder = true)
        scan(uri)
    }

    fun rescan() { prefs.getString("folder", null)?.let { scan(Uri.parse(it)) } }

    private fun scan(uri: Uri) = viewModelScope.launch {
        _lib.value = _lib.value.copy(scanning = true, progress = 0f)
        val songs = runCatching {
            Library.scan(getApplication(), uri) { p -> _lib.value = _lib.value.copy(progress = p) }
        }.getOrDefault(_lib.value.songs)
        val albums = withContext(Dispatchers.Default) { Library.albums(songs) }
        val artists = withContext(Dispatchers.Default) { Library.artists(songs) }
        _lib.value = _lib.value.copy(songs = songs, albums = albums, artists = artists, scanning = false, loaded = true, progress = 1f)
        refreshForYou(false)
        restorePlayer()
    }

    // ------------------------------------------------------------------ Tema
    fun setTheme(t: String) { prefs.edit().putString("theme", t).apply(); _theme.value = t }

    // ------------------------------------------------------------------ Top (veces escuchada)
    private fun loadCounts(): Map<String, Int> = runCatching {
        val j = JSONObject(prefs.getString("counts", "{}") ?: "{}")
        j.keys().asSequence().associateWith { j.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun countPlay(uri: String) {
        val m = _counts.value.toMutableMap(); m[uri] = (m[uri] ?: 0) + 1
        _counts.value = m
        prefs.edit().putString("counts", JSONObject(m as Map<*, *>).toString()).apply()
    }

    // ------------------------------------------------------------------ 4U: descubrimiento diario
    fun refreshForYou(force: Boolean) {
        val songs = _lib.value.songs; val albums = _lib.value.albums
        if (songs.isEmpty() || albums.isEmpty()) { _forYou.value = null; return }
        val today = LocalDate.now().toString()
        if (!force && prefs.getString("4u_date", null) == today) {
            val album = albums.firstOrNull { it.name == prefs.getString("4u_album", null) }
            val byUri = songs.associateBy { it.uri }
            val picks = prefs.getString("4u_songs", "").orEmpty().split('\n').filter { it.isNotEmpty() }.mapNotNull { byUri[it] }
            if (album != null && picks.isNotEmpty()) { _forYou.value = ForYou(album, picks); return }
        }
        val album = albums.random()
        val pool = songs.filter { it.album != album.name }.ifEmpty { songs }
        val picks = pool.shuffled().take(8)
        prefs.edit().putString("4u_date", today).putString("4u_album", album.name).putString("4u_songs", picks.joinToString("\n") { it.uri }).apply()
        _forYou.value = ForYou(album, picks)
    }

    // ------------------------------------------------------------------ Reproducción
    fun play(list: List<Song>, i: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, i.coerceIn(0, list.lastIndex), 0)
        c.shuffleModeEnabled = false
        c.prepare(); c.play()
        _player.value = _player.value.copy(queue = list, index = i, playing = true)
        if (_sp.value.now?.playing == true) viewModelScope.launch { spotify.control("pause") }
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        val s = list.shuffled(); play(s, 0)
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(ms: Long) { controller?.seekTo(ms); _player.value = _player.value.copy(posMs = ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (_player.value.queue.isEmpty()) { play(listOf(song), 0); return }
        val at = (c.currentMediaItemIndex + 1).coerceAtMost(_player.value.queue.size)
        c.addMediaItem(at, song.toMediaItem())
        _player.value = _player.value.copy(queue = _player.value.queue.toMutableList().apply { add(at, song) })
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (_player.value.queue.isEmpty()) { play(listOf(song), 0); return }
        c.addMediaItem(song.toMediaItem())
        _player.value = _player.value.copy(queue = _player.value.queue + song)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val q = _player.value.queue
        if (from == to || from !in q.indices || to !in q.indices) return
        c.moveMediaItem(from, to)
        _player.value = _player.value.copy(queue = q.toMutableList().apply { add(to, removeAt(from)) }, index = c.currentMediaItemIndex)
    }

    fun skipTo(i: Int) { controller?.let { it.seekTo(i, 0); it.play() } }

    // Guardar / restaurar la última cola (como la app original)
    private fun savePlayer() {
        val c = controller ?: return
        val q = _player.value.queue
        if (q.isEmpty()) return
        prefs.edit().putString(
            "player",
            JSONObject().put("uris", JSONArray(q.map { it.uri })).put("i", c.currentMediaItemIndex)
                .put("pos", c.currentPosition).put("sh", c.shuffleModeEnabled).put("rp", c.repeatMode).toString()
        ).apply()
    }

    private fun restorePlayer() {
        val c = controller ?: return
        if (restored || _lib.value.songs.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) {
            // El servicio sigue vivo: reconstruir la cola desde él.
            val byUri = _lib.value.songs.associateBy { it.uri }
            val q = (0 until c.mediaItemCount).mapNotNull { byUri[c.getMediaItemAt(it).mediaId] }
            if (q.size == c.mediaItemCount) _player.value = _player.value.copy(queue = q, index = c.currentMediaItemIndex)
            return
        }
        val j = runCatching { JSONObject(prefs.getString("player", null) ?: return) }.getOrNull() ?: return
        val byUri = _lib.value.songs.associateBy { it.uri }
        val arr = j.optJSONArray("uris") ?: return
        val q = (0 until arr.length()).mapNotNull { byUri[arr.optString(it)] }
        if (q.isEmpty()) return
        val i = j.optInt("i").coerceIn(0, q.lastIndex)
        c.setMediaItems(q.map { it.toMediaItem() }, i, j.optLong("pos"))
        c.shuffleModeEnabled = j.optBoolean("sh"); c.repeatMode = j.optInt("rp")
        c.prepare()
        _player.value = _player.value.copy(queue = q, index = i, posMs = j.optLong("pos"))
    }

    // ------------------------------------------------------------------ Spotify
    fun setSpotifyClientId(id: String) { spotify.clientId = id; _sp.value = _sp.value.copy(clientId = spotify.clientId) }
    fun connectSpotify() {
        if (spotify.clientId.isBlank()) { _sp.value = _sp.value.copy(message = "Introduce tu Client ID de Spotify"); return }
        runCatching { spotify.startLogin() }.onFailure { _sp.value = _sp.value.copy(message = it.message) }
    }
    fun onSpotifyRedirect(uri: Uri) = viewModelScope.launch {
        _sp.value = _sp.value.copy(loading = true)
        val err = spotify.handleRedirect(uri)
        _sp.value = _sp.value.copy(connected = spotify.connected, loading = false, message = err ?: "Spotify conectado")
        if (err == null) refreshSpotify()
    }
    // Caché local de "Canciones que te gustan" (aparece al instante y sin conexión)
    private fun saveLikedCache(l: List<SpTrack>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("u", it.uri).put("n", it.name).put("a", it.artist).put("i", it.image ?: "").put("d", it.durationMs)) }
        prefs.edit().putString("sp_liked", a.toString()).apply()
    }
    private fun loadLikedCache(): List<SpTrack> = runCatching {
        val a = JSONArray(prefs.getString("sp_liked", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { SpTrack(it.getString("u"), it.getString("n"), it.getString("a"), it.getString("i").ifEmpty { null }, it.getLong("d")) }
    }.getOrDefault(emptyList())

    fun disconnectSpotify() { prefs.edit().remove("sp_liked").apply(); spotify.disconnect(); _sp.value = SpotifyUiState(clientId = spotify.clientId, message = "Spotify desconectado") }
    fun refreshSpotify() = viewModelScope.launch {
        _sp.value = _sp.value.copy(loading = true, error = null)
        val user = runCatching { spotify.me() }
        val liked = runCatching { spotify.savedTracks() }
        liked.getOrNull()?.let { saveLikedCache(it) }
        _sp.value = _sp.value.copy(
            user = user.getOrNull() ?: _sp.value.user,
            liked = liked.getOrDefault(_sp.value.liked),
            loading = false,
            error = (user.exceptionOrNull() ?: liked.exceptionOrNull())?.let {
                if (it is java.io.IOException) "Sin conexión con Spotify" else "Error de Spotify: ${it.message}"
            }
        )
    }
    fun playSpotify(tracks: List<SpTrack>, i: Int) = viewModelScope.launch {
        controller?.pause()
        // Contexto = "Canciones que te gustan": sigue el orden original de Spotify.
        val ctx = _sp.value.user?.id?.let { "spotify:user:$it:collection" }
        spotify.play(tracks.map { it.uri }, i, ctx)?.let { _sp.value = _sp.value.copy(message = it) }
        delay(800)
        _sp.value = _sp.value.copy(now = runCatching { spotify.nowPlaying() }.getOrNull())
    }
    fun spotifyToggle() = viewModelScope.launch {
        val playing = _sp.value.now?.playing == true
        spotify.control(if (playing) "pause" else "play")
        _sp.value = _sp.value.copy(now = _sp.value.now?.copy(playing = !playing))
    }
    fun spotifyNext() = viewModelScope.launch {
        spotify.control("next"); delay(600)
        _sp.value = _sp.value.copy(now = runCatching { spotify.nowPlaying() }.getOrNull())
    }
    fun consumeSpotifyMessage() { _sp.value = _sp.value.copy(message = null) }

    override fun onCleared() { savePlayer(); controller?.release(); super.onCleared() }
}

@Suppress("unused")
private fun MediaItem.idOrNull() = mediaId.takeIf { it.isNotEmpty() }
