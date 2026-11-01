package com.xyonmusic.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.xyonmusic.player.data.MusicRepository
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

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val posMs: Long = 0L,
    val durMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val loading: Boolean = true,
    /** Cola real de reproducción (la lista desde la que se reprodujo + añadidos). */
    val queue: List<Song> = emptyList()
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
    private val repo = MusicRepository(app)
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    private var controller: MediaController? = null
    val spotify = Spotify(app)
    private val _sp = MutableStateFlow(SpotifyUiState())
    val sp: StateFlow<SpotifyUiState> = _sp.asStateFlow()

    init {
        _sp.value = SpotifyUiState(connected = spotify.connected, clientId = spotify.clientId)
        if (spotify.connected) refreshSpotify()
        reload()
        // Lo que suena en Spotify (para el mini reproductor de Spotify).
        viewModelScope.launch {
            while (isActive) {
                if (spotify.connected) {
                    val n = runCatching { spotify.nowPlaying() }.getOrNull()
                    _sp.value = _sp.value.copy(now = n)
                }
                delay(if (_sp.value.now?.playing == true) 4000 else 10000)
            }
        }
        connect()
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    _ui.value = _ui.value.copy(
                        playing = c.isPlaying,
                        posMs = c.currentPosition.coerceAtLeast(0),
                        durMs = c.duration.takeIf { it > 0 } ?: _ui.value.durMs,
                        index = if (_ui.value.queue.isEmpty()) -1 else c.currentMediaItemIndex,
                        shuffle = c.shuffleModeEnabled,
                        repeat = c.repeatMode
                    )
                }
                delay(500)
            }
        }
    }

    private fun connect() {
        val app = getApplication<Application>()
        val token = SessionToken(app, android.content.ComponentName(app, MusicService::class.java))
        val f = MediaController.Builder(getApplication(), token).buildAsync()
        f.addListener({ controller = f.get() }, MoreExecutors.directExecutor())
    }

    fun reload() = viewModelScope.launch(Dispatchers.IO) {
        _ui.value = _ui.value.copy(loading = true)
        val songs = runCatching { repo.loadAll() }.getOrDefault(emptyList())
        _ui.value = _ui.value.copy(songs = songs, loading = false)
    }

    fun play(list: List<Song>, i: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, i.coerceIn(0, list.lastIndex), 0)
        c.prepare(); c.play()
        _ui.value = _ui.value.copy(queue = list, index = i, playing = true)
        if (_sp.value.now?.playing == true) viewModelScope.launch { spotify.control("pause") }
    }

    /** Reproducir justo después de la actual. */
    fun playNext(song: Song) {
        val c = controller ?: return
        if (_ui.value.queue.isEmpty()) { play(listOf(song), 0); return }
        val at = (c.currentMediaItemIndex + 1).coerceAtMost(_ui.value.queue.size)
        c.addMediaItem(at, song.toMediaItem())
        _ui.value = _ui.value.copy(queue = _ui.value.queue.toMutableList().apply { add(at, song) })
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (_ui.value.queue.isEmpty()) { play(listOf(song), 0); return }
        c.addMediaItem(song.toMediaItem())
        _ui.value = _ui.value.copy(queue = _ui.value.queue + song)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val q = _ui.value.queue
        if (from == to || from !in q.indices || to !in q.indices) return
        c.moveMediaItem(from, to)
        _ui.value = _ui.value.copy(queue = q.toMutableList().apply { add(to, removeAt(from)) }, index = c.currentMediaItemIndex)
    }

    fun skipTo(i: Int) { controller?.let { it.seekTo(i, 0); it.play() } }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------- Spotify ----------
    fun setSpotifyClientId(id: String) { spotify.clientId = id; _sp.value = _sp.value.copy(clientId = spotify.clientId) }
    fun connectSpotify() {
        if (spotify.clientId.isBlank()) { _sp.value = _sp.value.copy(message = "Introduce tu Client ID de Spotify"); return }
        runCatching { spotify.startLogin() }.onFailure { _sp.value = _sp.value.copy(message = it.message) }
    }
    fun onSpotifyRedirect(uri: android.net.Uri) = viewModelScope.launch {
        _sp.value = _sp.value.copy(loading = true)
        val err = spotify.handleRedirect(uri)
        _sp.value = _sp.value.copy(connected = spotify.connected, loading = false, message = err ?: "Spotify conectado")
        if (err == null) refreshSpotify()
    }
    fun disconnectSpotify() { spotify.disconnect(); _sp.value = SpotifyUiState(clientId = spotify.clientId, message = "Spotify desconectado") }
    fun refreshSpotify() = viewModelScope.launch {
        _sp.value = _sp.value.copy(loading = true)
        val user = runCatching { spotify.me() }
        val liked = runCatching { spotify.savedTracks() }
        _sp.value = _sp.value.copy(
            user = user.getOrNull(),
            liked = liked.getOrDefault(_sp.value.liked),
            loading = false,
            error = (user.exceptionOrNull() ?: liked.exceptionOrNull())?.let {
                if (it is java.io.IOException) "Sin conexión con Spotify" else "Error de Spotify: ${it.message}"
            }
        )
    }
    fun playSpotify(tracks: List<SpTrack>, i: Int) = viewModelScope.launch {
        controller?.pause()
        // Contexto = "Canciones que te gustan" del usuario: sigue el orden original de Spotify.
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

    override fun onCleared() { controller?.release(); super.onCleared() }
}

fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
