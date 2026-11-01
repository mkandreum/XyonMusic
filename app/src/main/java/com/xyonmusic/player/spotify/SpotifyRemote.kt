package com.xyonmusic.player.spotify

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

/** Estado de lo que suena en Spotify, recibido en tiempo real desde la app de Spotify. */
data class SpRemoteState(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val positionMs: Long,
    val paused: Boolean,
    val shuffle: Boolean,
    val repeat: Int, // 0 off, 1 one, 2 all (convención del SDK)
    val at: Long = SystemClock.elapsedRealtime()
) {
    /** Posición estimada ahora mismo (el SDK solo avisa en cambios de estado). */
    fun positionNow(): Long =
        if (paused) positionMs else (positionMs + (SystemClock.elapsedRealtime() - at)).coerceAtMost(durationMs)
}

/**
 * SDK oficial de Spotify "App Remote": controla la app de Spotify instalada en el móvil
 * sin salir de XyonMusic. El audio lo reproduce Spotify (no se puede extraer), pero el
 * control y la información se muestran en el reproductor de XyonMusic.
 */
class SpotifyRemote(private val ctx: Context) {
    private var remote: SpotifyAppRemote? = null
    private var sub: Subscription<PlayerState>? = null
    private var lastImage: String? = null

    private val _state = MutableStateFlow<SpRemoteState?>(null)
    val state: StateFlow<SpRemoteState?> = _state.asStateFlow()
    private val _art = MutableStateFlow<Bitmap?>(null)
    val art: StateFlow<Bitmap?> = _art.asStateFlow()

    val connected: Boolean get() = remote?.isConnected == true
    val installed: Boolean get() = SpotifyAppRemote.isSpotifyInstalled(ctx)

    /** Conecta con la app de Spotify. Devuelve null si va bien, o el error. */
    suspend fun connect(clientId: String, showAuth: Boolean): String? {
        if (connected) return null
        if (!installed) return "La app de Spotify no está instalada"
        if (clientId.isBlank()) return "Falta el Client ID de Spotify"
        return suspendCancellableCoroutine { cont ->
            val params = ConnectionParams.Builder(clientId)
                .setRedirectUri(Spotify.REDIRECT)
                .showAuthView(showAuth)
                .build()
            SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
                override fun onConnected(r: SpotifyAppRemote) {
                    remote = r
                    subscribe(r)
                    if (cont.isActive) cont.resume(null)
                }
                override fun onFailure(t: Throwable) {
                    remote = null
                    if (cont.isActive) cont.resume(describe(t))
                }
            })
        }
    }

    private fun describe(t: Throwable): String {
        val n = t.javaClass.simpleName
        return when {
            n.contains("NotLoggedIn") -> "Inicia sesión en la app de Spotify"
            n.contains("UserNotAuthorized") || n.contains("Authentication") ->
                "Spotify no ha autorizado la app: revisa el paquete y la huella SHA-1 en tu app de Spotify for Developers"
            n.contains("CouldNotFindSpotifyApp") -> "La app de Spotify no está instalada"
            n.contains("Offline") -> "Spotify está sin conexión"
            else -> "No se pudo conectar con Spotify (${t.message ?: n})"
        }
    }

    private fun subscribe(r: SpotifyAppRemote) {
        sub?.cancel()
        sub = r.playerApi.subscribeToPlayerState().setEventCallback { ps ->
            val t = ps.track ?: return@setEventCallback
            _state.value = SpRemoteState(
                uri = t.uri ?: "",
                title = t.name ?: "",
                artist = t.artists?.joinToString(", ") { it.name ?: "" }?.ifBlank { null } ?: t.artist?.name ?: "",
                album = t.album?.name,
                durationMs = t.duration,
                positionMs = ps.playbackPosition,
                paused = ps.isPaused,
                shuffle = ps.playbackOptions?.isShuffling == true,
                repeat = ps.playbackOptions?.repeatMode ?: 0
            )
            val img = t.imageUri?.raw
            if (img != null && img != lastImage) {
                lastImage = img
                r.imagesApi.getImage(t.imageUri).setResultCallback { b -> _art.value = b }
            }
        }
    }

    /** Reproduce una canción dentro de un contexto (p. ej. "Canciones que te gustan") desde la posición dada. */
    fun playInContext(contextUri: String?, index: Int, trackUri: String, onError: (String) -> Unit) {
        val p = remote?.playerApi ?: return onError("Spotify no está conectado")
        if (contextUri != null) {
            p.skipToIndex(contextUri, index).setErrorCallback {
                // Si el contexto no admite saltar a un índice, se reproduce la canción directamente.
                p.play(trackUri).setErrorCallback { e -> onError(describe(e)) }
            }
        } else p.play(trackUri).setErrorCallback { e -> onError(describe(e)) }
    }

    fun play(uri: String, onError: (String) -> Unit = {}) {
        remote?.playerApi?.play(uri)?.setErrorCallback { e -> onError(describe(e)) }
    }

    fun toggle() { val s = _state.value ?: return; remote?.playerApi?.let { if (s.paused) it.resume() else it.pause() } }
    fun pause() { remote?.playerApi?.pause() }
    fun next() { remote?.playerApi?.skipNext() }
    fun previous() { remote?.playerApi?.skipPrevious() }
    fun seekTo(ms: Long) {
        remote?.playerApi?.seekTo(ms)
        _state.value = _state.value?.copy(positionMs = ms, at = SystemClock.elapsedRealtime())
    }
    fun toggleShuffle() { remote?.playerApi?.toggleShuffle() }
    fun toggleRepeat() { remote?.playerApi?.toggleRepeat() }

    fun disconnect() {
        sub?.cancel(); sub = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        _state.value = null; _art.value = null; lastImage = null
    }

    companion object {
        /** Huella SHA-1 de la firma de este APK (hay que registrarla en Spotify for Developers). */
        fun signingSha1(ctx: Context): String? = runCatching {
            val pm = ctx.packageManager
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert = sigs?.firstOrNull()?.toByteArray() ?: return null
            MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
        }.getOrNull()
    }
}
