package com.xyonmusic.player.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class SpTrack(val uri: String, val name: String, val artist: String, val image: String?, val durationMs: Long)
data class SpPlaylist(val id: String, val uri: String, val name: String, val owner: String, val image: String?, val total: Int)
data class SpUser(val name: String, val image: String?, val premium: Boolean)

/**
 * Integración Spotify vía Web API + OAuth Authorization Code con PKCE (sin client secret).
 * Spotify no permite reproducir su audio dentro de apps de terceros: el audio sale por la app
 * oficial de Spotify (Spotify Connect) y XyonMusic la controla (requiere Premium para control remoto).
 * Sin Premium o sin dispositivo activo, se abre la canción en la app de Spotify.
 */
class Spotify(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    var clientId: String
        get() = prefs.getString("client_id", "") ?: ""
        set(v) = prefs.edit().putString("client_id", v.trim()).apply()

    val connected: Boolean get() = !prefs.getString("refresh", null).isNullOrEmpty()

    fun startLogin() {
        val verifier = randomString(64)
        val state = randomString(16)
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Devuelve null si OK, o un mensaje de error. */
    suspend fun handleRedirect(uri: Uri): String? = withContext(Dispatchers.IO) {
        uri.getQueryParameter("error")?.let { return@withContext "Spotify: $it" }
        val code = uri.getQueryParameter("code") ?: return@withContext "Sin código de autorización"
        if (uri.getQueryParameter("state") != prefs.getString("state", null)) return@withContext "Estado OAuth inválido"
        runCatching {
            tokenRequest(
                "grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT,
                "client_id" to clientId, "code_verifier" to (prefs.getString("verifier", "") ?: "")
            )
        }.exceptionOrNull()?.message
    }

    fun disconnect() = prefs.edit().remove("access").remove("refresh").remove("exp").apply()

    private fun tokenRequest(vararg form: Pair<String, String>) {
        val body = form.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
        val c = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) error("Token HTTP $code: $text")
        val j = JSONObject(text)
        prefs.edit()
            .putString("access", j.getString("access_token"))
            .putLong("exp", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000)
            .apply { j.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { putString("refresh", it) } }
            .apply()
    }

    private fun token(): String {
        if (System.currentTimeMillis() > prefs.getLong("exp", 0)) {
            tokenRequest("grant_type" to "refresh_token", "refresh_token" to (prefs.getString("refresh", "") ?: ""), "client_id" to clientId)
        }
        return prefs.getString("access", "") ?: ""
    }

    private fun api(method: String, path: String, body: String? = null): Pair<Int, String> {
        val c = URL("https://api.spotify.com/v1$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer ${token()}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        } else if (method == "PUT" || method == "POST") {
            c.setFixedLengthStreamingMode(0); c.doOutput = true
        }
        val code = c.responseCode
        val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        return code to text
    }

    private fun getJson(path: String): JSONObject {
        val (code, text) = api("GET", path)
        if (code !in 200..299) error("Spotify HTTP $code")
        return JSONObject(text)
    }

    suspend fun me(): SpUser = withContext(Dispatchers.IO) {
        val j = getJson("/me")
        SpUser(j.optString("display_name", "Spotify"), j.optJSONArray("images").firstImage(), j.optString("product") == "premium")
    }

    suspend fun playlists(): List<SpPlaylist> = withContext(Dispatchers.IO) {
        getJson("/me/playlists?limit=50").getJSONArray("items").objects().map {
            SpPlaylist(
                it.getString("id"), it.getString("uri"), it.optString("name"),
                it.optJSONObject("owner")?.optString("display_name").orEmpty(),
                it.optJSONArray("images").firstImage(),
                (it.optJSONObject("tracks") ?: it.optJSONObject("items"))?.optInt("total") ?: 0
            )
        }
    }

    suspend fun savedTracks(): List<SpTrack> = withContext(Dispatchers.IO) {
        getJson("/me/tracks?limit=50").getJSONArray("items").objects().mapNotNull { it.optJSONObject("track")?.toTrack() }
    }

    suspend fun recentlyPlayed(): List<SpTrack> = withContext(Dispatchers.IO) {
        getJson("/me/player/recently-played?limit=30").getJSONArray("items").objects()
            .mapNotNull { it.optJSONObject("track")?.toTrack() }.distinctBy { it.uri }
    }

    suspend fun playlistTracks(id: String): List<SpTrack> = withContext(Dispatchers.IO) {
        getJson("/playlists/$id/tracks?limit=100").getJSONArray("items").objects()
            .mapNotNull { (it.optJSONObject("track") ?: it.optJSONObject("item"))?.toTrack() }
    }

    /** Reproduce en el dispositivo Spotify activo; si no hay o no es Premium, abre la app de Spotify. */
    suspend fun play(trackUris: List<String>, offset: Int = 0, contextUri: String? = null): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            if (contextUri != null) {
                put("context_uri", contextUri); put("offset", JSONObject().put("position", offset))
            } else {
                put("uris", JSONArray(trackUris.take(100))); put("offset", JSONObject().put("position", offset.coerceAtMost(99)))
            }
        }.toString()
        val (code, _) = runCatching { api("PUT", "/me/player/play", body) }.getOrDefault(0 to "")
        if (code in 200..299) return@withContext null
        val target = trackUris.getOrNull(offset) ?: contextUri ?: return@withContext "No se pudo reproducir"
        openInSpotify(target)
        if (code == 404) "Abre Spotify en algún dispositivo para controlarlo desde aquí" else null
    }

    suspend fun control(action: String) = withContext(Dispatchers.IO) {
        runCatching {
            when (action) {
                "next" -> api("POST", "/me/player/next")
                "prev" -> api("POST", "/me/player/previous")
                "pause" -> api("PUT", "/me/player/pause")
                "play" -> api("PUT", "/me/player/play")
            }
        }
    }

    fun openInSpotify(spotifyUri: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
            val web = "https://open.spotify.com/" + spotifyUri.removePrefix("spotify:").replace(':', '/')
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        const val REDIRECT = "xyonmusic://spotify-callback"
        private const val SCOPES = "user-read-private user-library-read playlist-read-private playlist-read-collaborative " +
            "user-read-recently-played user-read-playback-state user-modify-playback-state"

        private fun randomString(n: Int): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val r = SecureRandom()
            return (1..n).map { chars[r.nextInt(chars.length)] }.joinToString("")
        }

        private fun JSONArray?.firstImage(): String? = this?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }
        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
        private fun JSONObject.toTrack(): SpTrack? {
            val uri = optString("uri").takeIf { it.startsWith("spotify:track:") } ?: return null
            val artists = optJSONArray("artists")?.objects()?.joinToString(", ") { it.optString("name") }.orEmpty()
            return SpTrack(uri, optString("name"), artists, optJSONObject("album")?.optJSONArray("images").firstImage(), optLong("duration_ms"))
        }
    }
}
