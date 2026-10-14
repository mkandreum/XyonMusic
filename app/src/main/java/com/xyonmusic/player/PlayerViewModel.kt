package com.xyonmusic.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.xyonmusic.player.data.MusicRepository
import com.xyonmusic.player.data.Song
import com.xyonmusic.player.playback.MusicService
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
    val loading: Boolean = true
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    private var controller: MediaController? = null

    init {
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
                        index = c.currentMediaItemIndex,
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
        c.setMediaItems(list.map { MediaItem.fromUri(it.contentUri) }, i, 0)
        c.prepare(); c.play()
    }

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
