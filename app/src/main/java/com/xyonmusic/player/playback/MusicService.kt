package com.xyonmusic.player.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xyonmusic.player.data.Song

// FIX WebView: usaba <audio> HTML + MusicService desconectado (broadcast STATE_UPDATE
// que el JS nunca escuchaba). Fondo se cortaba al bloquear, sin notificación ni audio-focus.
// Nativa: ExoPlayer real en MediaSessionService, conectado a la UI por MediaController.
class MusicService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
            .setUri(contentUri)
            .setMediaId(contentUri.toString())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title ?: name)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artUri)
                    .build()
            ).build()
    }
}
