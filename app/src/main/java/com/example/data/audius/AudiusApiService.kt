package com.example.data.audius

import android.util.Log
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AudiusApiService {
  private const val TAG = "AudiusApiService"
  private const val APP_NAME = "Tarkas"
  private const val API_KEY = "0xb37c3c789148f33197d9710d4b021a313c20902b"

  // Discovery provider nodes with fallbacks
  private val DISCOVERY_NODES = listOf(
    "https://discoveryprovider.audius.co/v1",
    "https://audius-dp.amsterdam.creatorseed.com/v1",
    "https://audius-discovery-1.cultur3stake.com/v1",
    "https://dn1.nodeoperator.io/v1"
  )

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")

    for (node in DISCOVERY_NODES) {
      val url = "$node/tracks/search?query=$encoded&app_name=$APP_NAME&api_key=$API_KEY"
      val tracks = fetchTracksFromUrl(url, node)
      if (tracks.isNotEmpty()) {
        return@withContext tracks
      }
    }
    emptyList()
  }

  suspend fun getTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
    for (node in DISCOVERY_NODES) {
      val url = "$node/tracks/trending?app_name=$APP_NAME&api_key=$API_KEY"
      val tracks = fetchTracksFromUrl(url, node)
      if (tracks.isNotEmpty()) {
        return@withContext tracks
      }
    }
    emptyList()
  }

  private fun fetchTracksFromUrl(url: String, baseNode: String): List<Track> {
    try {
      val request = Request.Builder()
        .url(url)
        .addHeader("Accept", "application/json")
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        Log.w(TAG, "Audius node returned code ${response.code} for $url")
        return emptyList()
      }

      val body = response.body?.string() ?: return emptyList()
      val json = JSONObject(body)
      val data = json.optJSONArray("data") ?: return emptyList()

      val result = mutableListOf<Track>()
      for (i in 0 until data.length()) {
        val item = data.getJSONObject(i)
        val id = item.optString("id").ifBlank { item.optInt("track_id", 0).toString() }
        if (id.isBlank() || id == "0") continue

        val title = item.optString("title", "Sem título")
        val durationSec = item.optLong("duration", 180L)
        val genre = item.optString("genre", "Audius Music")
        val userObj = item.optJSONObject("user")
        val artist = userObj?.optString("name", "Artista Audius") ?: "Artista Audius"

        // Stream URL from direct node stream endpoint
        val streamUrl = "$baseNode/tracks/$id/stream?app_name=$APP_NAME&api_key=$API_KEY"

        val colorOptions = listOf(
          0xFFFA243C, // Apple Music Pink
          0xFF00E5FF, // Cyan
          0xFFA855F7, // Purple
          0xFF38BDF8, // Sky
          0xFFF59E0B, // Amber
          0xFF10B981  // Emerald
        )
        val colorHex = colorOptions[Math.abs(id.hashCode()) % colorOptions.size]

        result.add(
          Track(
            id = "audius_$id",
            title = title,
            artist = artist,
            album = "Audius • $genre",
            durationMs = durationSec * 1000L,
            genre = genre,
            era = "Audius Streaming",
            isLocalFile = false,
            filePath = streamUrl,
            colorHex = colorHex
          )
        )
      }
      return result
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch from node: $baseNode", e)
      return emptyList()
    }
  }
}
