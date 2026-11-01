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
import com.xyonmusic.player.spotify.SpPlaylist
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
    val queue: List<Song> = emptyList(),
    val recent: List<String> = emptyList(),
    val userName: String = "",
    val dark: Boolean = true
)

data class SpotifyUiState(
    val connected: Boolean = false,
    val clientId: String = "",
    val user: SpUser? = null,
    val playlists: List<SpPlaylist> = emptyList(),
    val saved: List<SpTrack> = emptyList(),
    val recent: List<SpTrack> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    private var controller: MediaController? = null
    private val prefs = app.getSharedPreferences("xyon", android.content.Context.MODE_PRIVATE)
    val spotify = Spotify(app)
    private val _sp = MutableStateFlow(SpotifyUiState())
    val sp: StateFlow<SpotifyUiState> = _sp.asStateFlow()

    init {
        _ui.value = _ui.value.copy(
            recent = prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() },
            userName = prefs.getString("user_name", "").orEmpty(),
            dark = prefs.getBoolean("dark", true)
        )
        _sp.value = SpotifyUiState(connected = spotify.connected, clientId = spotify.clientId)
        if (spotify.connected) refreshSpotify()
        viewModelScope.launch(Dispatchers.IO) {
            val songs = repo.loadAll()
            _ui.value = _ui.value.copy(songs = songs, loading = false)
        }
        connect()
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    _ui.value = _ui.value.copy(
                        playing = c.isPlaying,
                        posMs = c.currentPosition.coerceAtLeast(0),
                        durMs = c.duration.takeIf { it > 0 } ?: _ui.value.durMs,
                        index = c.currentMediaItemIndex.takeIf { _ui.value.queue.isNotEmpty() } ?: -1,
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

    fun play(list: List<Song>, i: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, i, 0)
        c.prepare(); c.play()
        _ui.value = _ui.value.copy(queue = list, index = i)
        list.getOrNull(i)?.let { remember(it) }
    }

    private fun remember(s: Song) {
        val key = s.contentUri.toString()
        val r = (listOf(key) + _ui.value.recent.filter { it != key }).take(40)
        prefs.edit().putString("recent", r.joinToString("\n")).apply()
        _ui.value = _ui.value.copy(recent = r)
    }

    fun setUserName(n: String) { prefs.edit().putString("user_name", n).apply(); _ui.value = _ui.value.copy(userName = n) }
    fun setDark(d: Boolean) { prefs.edit().putBoolean("dark", d).apply(); _ui.value = _ui.value.copy(dark = d) }

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
        _sp.value = _sp.value.copy(
            user = user.getOrNull(),
            playlists = runCatching { spotify.playlists() }.getOrDefault(_sp.value.playlists),
            saved = runCatching { spotify.savedTracks() }.getOrDefault(_sp.value.saved),
            recent = runCatching { spotify.recentlyPlayed() }.getOrDefault(_sp.value.recent),
            loading = false,
            message = user.exceptionOrNull()?.let { "Error Spotify: ${it.message}" }
        )
    }
    suspend fun spotifyPlaylistTracks(id: String): List<SpTrack> = runCatching { spotify.playlistTracks(id) }.getOrDefault(emptyList())
    fun playSpotify(tracks: List<SpTrack>, i: Int, contextUri: String? = null) = viewModelScope.launch {
        controller?.pause()
        spotify.play(tracks.map { it.uri }, i, contextUri)?.let { _sp.value = _sp.value.copy(message = it) }
    }
    fun consumeSpotifyMessage() { _sp.value = _sp.value.copy(message = null) }

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

    override fun onCleared() { controller?.release(); super.onCleared() }
}

fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
