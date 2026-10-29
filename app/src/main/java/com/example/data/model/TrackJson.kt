package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/** How a track is stored: in the library (favorites, playlists) and in the saved queue. */
object TrackJson {

  fun toJson(t: Track): JSONObject = JSONObject()
    .put("id", t.id).put("title", t.title).put("artist", t.artist).put("album", t.album)
    .put("dur", t.durationMs).put("genre", t.genre).put("era", t.era)
    .put("local", t.isLocalFile).put("path", t.filePath ?: JSONObject.NULL)
    .put("color", t.colorHex)
    .put("art", t.artworkUrl ?: JSONObject.NULL)
    .put("demo", t.isDemo)

  /** Null for an entry that can no longer play (from an integration that was removed). */
  fun fromJson(o: JSONObject): Track? {
    // YouTube Music and Spotify songs ("yt" before 1.9, "web" until 1.13) played in web players the
    // app no longer has: drop them.
    if (o.has("yt") && !o.isNull("yt")) return null
    if (o.has("web") && !o.isNull("web")) return null
    return Track(
      id = o.getString("id"),
      title = o.getString("title"),
      artist = o.getString("artist"),
      album = o.getString("album"),
      durationMs = o.optLong("dur", 0L),
      genre = o.optString("genre", ""),
      era = o.optString("era", ""),
      isLocalFile = o.optBoolean("local", false),
      filePath = if (o.isNull("path")) null else o.getString("path"),
      colorHex = o.optLong("color", 0xFF46C9D3),
      artworkUrl = if (o.isNull("art")) null else o.optString("art").ifBlank { null },
      isDemo = o.optBoolean("demo", false)
    )
  }

  fun listToJson(tracks: List<Track>): JSONArray = JSONArray().apply { tracks.forEach { put(toJson(it)) } }

  fun listFromJson(array: JSONArray?): List<Track> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i -> runCatching { fromJson(array.getJSONObject(i)) }.getOrNull() }
  }
}
