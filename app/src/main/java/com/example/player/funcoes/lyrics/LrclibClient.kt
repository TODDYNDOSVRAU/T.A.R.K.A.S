package com.example.data.lyrics

import com.example.data.model.Track
import com.example.data.net.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches synced (`.lrc`) or plain lyrics from LRCLIB.net (free, open, no API key required).
 * Parsing is pure so it can be unit-tested on the JVM without network access.
 */
object LrclibClient {
  private const val BASE_URL = "https://lrclib.net/api/get"

  fun fetch(track: Track): String? {
    if (track.isDemo || track.title.isBlank()) return null
    val url = BASE_URL.toHttpUrl().newBuilder()
      .addQueryParameter("track_name", track.title)
      .apply {
        if (track.artist.isNotBlank()) addQueryParameter("artist_name", track.artist)
        if (track.album.isNotBlank()) addQueryParameter("album_name", track.album)
        if (track.durationMs > 0L) addQueryParameter("duration", (track.durationMs / 1000L).toString())
      }
      .build()
    val req = Request.Builder()
      .url(url)
      .header("User-Agent", "T.A.R.K.A.S./2.2 (https://totallyaccuratelabs.com)")
      .get()
      .build()
    return runCatching {
      Http.client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        resp.body?.string()
      }
    }.getOrNull()
  }

  /**
   * Extracts synced `.lrc` text first, or plain lyrics text as fallback. Returns Pair(rawText, lyrics)
   * so the caller can cache the raw `.lrc`/plain text on disk.
   */
  fun parseResponse(jsonBody: String): Pair<String, Lyrics>? {
    val obj = runCatching { JSONObject(jsonBody) }.getOrNull() ?: return null
    val synced = obj.optString("syncedLyrics", "").trim()
    if (synced.isNotEmpty()) {
      val parsed = LrcParser.parse(synced)
      if (parsed != null) return synced to parsed
    }
    val plain = obj.optString("plainLyrics", "").trim()
    if (plain.isNotEmpty()) {
      val lines = plain.lineSequence().map { LyricLine(timeMs = null, text = it.trim()) }.toList()
      if (lines.any { it.text.isNotEmpty() }) return plain to Lyrics(lines = lines, source = LyricsSource.SIDECAR_LRC)
    }
    return null
  }
}
