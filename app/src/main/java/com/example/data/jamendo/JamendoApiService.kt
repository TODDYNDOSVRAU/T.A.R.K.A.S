package com.example.data.jamendo

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Jamendo said no: the app's client id is missing or refused. Not a network problem. */
class JamendoNotConfigured : Exception("Jamendo client id missing or refused")

/**
 * Jamendo (https://developer.jamendo.com/v3.0/docs): independent music under Creative Commons, played
 * in full by the app's own player. Every call needs the app's client id, a public identifier that
 * Jamendo issues on its developer portal; it is read from `.env` (JAMENDO_CLIENT_ID) at build time and
 * never committed. Without it the Jamendo results say so instead of failing.
 */
object JamendoApiService {
  private const val TAG = "JamendoApiService"
  private const val BASE = "https://api.jamendo.com/v3.0"
  private const val LIMIT = 20

  private val clientId: String get() = BuildConfig.JAMENDO_CLIENT_ID.trim()

  /**
   * False when the build has no client id (".env.example" ships a placeholder): the app then does not
   * call Jamendo at all.
   */
  val isConfigured: Boolean get() = clientId.isNotEmpty() && clientId != "MY_JAMENDO_CLIENT_ID"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  /**
   * Tracks matching [query]. Throws [java.io.IOException] when Jamendo did not answer (offline), and
   * [JamendoNotConfigured] when there is no valid client id.
   */
  suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val q = query.trim()
    // The free-text search misses some titles that the title search finds: the tries alternate.
    firstNonEmpty { attempt -> fetch(if (attempt % 2 == 0) "search" to q else "namesearch" to q, "order" to "relevance") }
  }

  /** The most listened tracks of the week. Same errors as [searchTracks]. */
  suspend fun getPopularTracks(): List<Track> = withContext(Dispatchers.IO) {
    firstNonEmpty { fetch("order" to "popularity_week") }
  }

  /**
   * Jamendo answers an empty list to about a third of identical requests (measured in October 2026),
   * and the real list a moment later. An empty answer is asked again a few times before it counts as
   * "nothing found".
   */
  private suspend fun firstNonEmpty(request: (attempt: Int) -> List<Track>): List<Track> {
    repeat(ATTEMPTS - 1) { attempt ->
      request(attempt).let { if (it.isNotEmpty()) return it }
      kotlinx.coroutines.delay(RETRY_DELAY_MS)
    }
    return request(ATTEMPTS - 1)
  }

  private const val ATTEMPTS = 4
  private const val RETRY_DELAY_MS = 300L

  private fun fetch(vararg params: Pair<String, String>): List<Track> {
    if (!isConfigured) throw JamendoNotConfigured()
    val url = "$BASE/tracks/".toHttpUrl().newBuilder()
      .addQueryParameter("client_id", clientId)
      .addQueryParameter("format", "json")
      .addQueryParameter("limit", LIMIT.toString())
      .addQueryParameter("include", "musicinfo")
      .addQueryParameter("audioformat", "mp32")
      .addQueryParameter("imagesize", "300")
      .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
      .build()
    val body = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
      .execute()
      .use { response ->
        if (!response.isSuccessful) throw java.io.IOException("Jamendo answered ${response.code}")
        response.body?.string()
      } ?: throw java.io.IOException("Empty answer from Jamendo")
    return parseTracks(body)
  }

  /** Reads Jamendo's answer. Public for the tests; one malformed entry is skipped, not the whole list. */
  fun parseTracks(body: String): List<Track> {
    val json = JSONObject(body)
    val headers = json.optJSONObject("headers")
    if (headers?.optString("status") == "failed") {
      val message = headers.optString("error_message")
      Log.w(TAG, "Jamendo refused the request: $message")
      // Jamendo answers 200 with status "failed" for a missing, unknown or suspended client id.
      if (message.contains("client", ignoreCase = true)) throw JamendoNotConfigured()
      throw java.io.IOException("Jamendo failed: $message")
    }
    val results = json.optJSONArray("results") ?: return emptyList()
    val tracks = mutableListOf<Track>()
    for (i in 0 until results.length()) {
      val item = results.optJSONObject(i) ?: continue
      val id = item.optString("id")
      val audio = item.optString("audio")
      // A track without a stream (withdrawn, or region-locked) cannot play: leave it out.
      if (id.isBlank() || !audio.startsWith("https://")) continue
      val genre = item.optJSONObject("musicinfo")?.optJSONObject("tags")?.optJSONArray("genres")
        ?.optString(0)?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ?: "Jamendo"
      tracks += Track(
        id = "jamendo_$id",
        title = text(item, "name").ifBlank { "Sem título" },
        artist = text(item, "artist_name").ifBlank { "Artista Jamendo" },
        album = text(item, "album_name").ifBlank { "Jamendo" },
        durationMs = item.optLong("duration", 0L) * 1000L,
        genre = genre,
        era = item.optString("releasedate").take(4).ifBlank { "Jamendo" },
        isLocalFile = false,
        filePath = audio,
        colorHex = COLORS[Math.floorMod(id.hashCode(), COLORS.size)],
        artworkUrl = item.optString("album_image").ifBlank { item.optString("image") }.ifBlank { null }
      )
    }
    return tracks.distinctBy { it.id }
  }

  /** Jamendo sends some names HTML-escaped ("Etude &quot;The Passion&quot;"): they are shown decoded. */
  private fun text(item: JSONObject, key: String): String =
    android.text.Html.fromHtml(item.optString(key), android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

  private val COLORS = listOf(0xFFF59E0B, 0xFF10B981, 0xFF38BDF8, 0xFFA855F7, 0xFFFA243C, 0xFF00E5FF)
}
