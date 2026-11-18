package com.example.windows.geral

import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlayStatEntity
import com.example.data.db.PlaybackStateEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.db.SettingEntity
import com.example.data.db.TrackEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The file cannot be restored: [reason] says why, and nothing was changed. */
class RestoreException(val reason: String) : Exception(reason)

/**
 * Export and restore of everything the app keeps about the person. On Windows every preference lives in the
 * Room `settings` table, so one database holds it all; the file keeps the Android export format (`format` 2, the two
 * preference scopes present and empty) so both apps read each other's files. The restore reads and checks the
 * whole file BEFORE it changes anything, then replaces the data.
 */
class UserDataBackup(private val db: AppDatabase) {

  suspend fun export(appVersion: String, exportedAt: String): JSONObject {
    val playlists = JSONArray()
    val entries = db.playlists().entries().groupBy { it.playlistId }
    for (p in db.playlists().all()) {
      playlists.put(
        JSONObject().put("id", p.id).put("name", p.name).put("createdAt", p.createdAt)
          .put("tracks", JSONArray((entries[p.id] ?: emptyList()).sortedBy { it.position }.map { it.trackId }))
      )
    }
    val prefs = JSONObject().also { for (s in PREFS_SCOPES) it.put(s, JSONObject()) }
    return JSONObject()
      .put("app", APP).put("format", FORMAT).put("version", appVersion).put("exportedAt", exportedAt)
      .put("platform", "windows")
      .put("preferences", prefs)
      .put("tracks", JSONArray().also { a -> db.tracks().all().sortedBy { it.id }.forEach { a.put(trackToJson(it)) } })
      .put("favorites", JSONArray().also { a -> db.favorites().entities().forEach { a.put(JSONObject().put("trackId", it.trackId).put("addedAt", it.addedAt)) } })
      .put("playlists", playlists)
      .put("playStats", JSONArray().also { a ->
        db.playStats().all().sortedBy { it.trackId }.forEach {
          a.put(JSONObject().put("trackId", it.trackId).put("plays", it.plays).put("skips", it.skips).put("lastPlayed", it.lastPlayed).put("lastSkipped", it.lastSkipped))
        }
      })
      .put("playback", db.playback().get()?.let {
        JSONObject().put("stateJson", it.stateJson).put("positionMs", it.positionMs).put("positionTrackId", it.positionTrackId ?: JSONObject.NULL)
      } ?: JSONObject.NULL)
      .put("settings", JSONArray().also { a ->
        db.settings().all().sortedWith(compareBy({ it.scope }, { it.key })).forEach {
          a.put(JSONObject().put("scope", it.scope).put("key", it.key).put("value", it.value))
        }
      })
  }

  /** Throws [RestoreException] before touching anything when the file is not a T.A.R.K.A.S. export or is damaged. The app reopens afterwards. */
  suspend fun restore(text: String) {
    val plan = parse(text)
    db.playlists().deleteAll()
    db.favorites().deleteAll()
    db.playStats().deleteAll()
    db.playback().clear()
    db.settings().deleteAll()
    db.tracks().deleteAll()
    db.tracks().upsert(plan.tracks)
    db.favorites().addAll(plan.favorites)
    for (p in plan.playlists) { db.playlists().insert(p.first); db.playlists().insertEntries(p.second) }
    db.playStats().upsertAll(plan.stats)
    plan.playback?.let { db.playback().put(it) }
    db.settings().putAll(plan.settings)
  }

  private class Plan(
    val tracks: List<TrackEntity>,
    val favorites: List<FavoriteEntity>,
    val playlists: List<Pair<PlaylistEntity, List<PlaylistTrackEntity>>>,
    val stats: List<PlayStatEntity>,
    val playback: PlaybackStateEntity?,
    val settings: List<SettingEntity>
  )

  private fun parse(text: String): Plan = try {
    val o = JSONObject(text)
    if (o.optString("app") != APP) throw RestoreException("not a $APP export")
    val format = o.optInt("format", -1)
    if (format != FORMAT) throw RestoreException("export format $format is not supported")
    val tracks = o.getJSONArray("tracks").objects().map(::trackFromJson)
    if (tracks.map { it.id }.toSet().size != tracks.size) throw RestoreException("two tracks share an id")
    val playlists = o.getJSONArray("playlists").objects().map { p ->
      val id = p.getString("id")
      val list = p.getJSONArray("tracks")
      PlaylistEntity(id, p.getString("name"), p.getLong("createdAt")) to (0 until list.length()).map { PlaylistTrackEntity(id, it, list.getString(it)) }
    }
    if (playlists.map { it.first.id }.toSet().size != playlists.size) throw RestoreException("two playlists share an id")
    Plan(
      tracks,
      o.getJSONArray("favorites").objects().map { FavoriteEntity(it.getString("trackId"), it.getLong("addedAt")) },
      playlists,
      o.getJSONArray("playStats").objects().map {
        PlayStatEntity(it.getString("trackId"), it.getInt("plays"), it.getInt("skips"), it.getLong("lastPlayed"), it.getLong("lastSkipped"))
      },
      o.optJSONObject("playback")?.let {
        PlaybackStateEntity(0, it.getString("stateJson"), it.getLong("positionMs"), if (it.isNull("positionTrackId")) null else it.getString("positionTrackId"))
      },
      o.getJSONArray("settings").objects().map { SettingEntity(it.getString("scope"), it.getString("key"), it.getString("value")) }
    )
  } catch (e: JSONException) {
    throw RestoreException("the file is damaged: ${e.message}")
  }

  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

  private fun trackToJson(t: TrackEntity) = JSONObject()
    .put("id", t.id).put("title", t.title).put("artist", t.artist).put("album", t.album)
    .put("durationMs", t.durationMs).put("genre", t.genre).put("era", t.era).put("isLocalFile", t.isLocalFile)
    .put("filePath", t.filePath ?: JSONObject.NULL).put("colorHex", t.colorHex ?: JSONObject.NULL)
    .put("artworkUrl", t.artworkUrl ?: JSONObject.NULL).put("isDemo", t.isDemo).put("albumId", t.albumId)
    .put("discNumber", t.discNumber).put("trackNumber", t.trackNumber).put("dateAddedSec", t.dateAddedSec)
    .put("picked", t.picked)

  private fun trackFromJson(o: JSONObject) = TrackEntity(
    id = o.getString("id"), title = o.getString("title"), artist = o.getString("artist"), album = o.getString("album"),
    durationMs = o.getLong("durationMs"), genre = o.getString("genre"), era = o.getString("era"),
    isLocalFile = o.getBoolean("isLocalFile"), filePath = o.optStringOrNull("filePath"),
    colorHex = o.optStringOrNull("colorHex"), artworkUrl = o.optStringOrNull("artworkUrl"),
    isDemo = o.getBoolean("isDemo"), albumId = o.getLong("albumId"), discNumber = o.getInt("discNumber"),
    trackNumber = o.getInt("trackNumber"), dateAddedSec = o.getLong("dateAddedSec"), picked = o.getBoolean("picked")
  )

  private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

  companion object {
    const val APP = "T.A.R.K.A.S."
    const val FORMAT = 2
    val PREFS_SCOPES = listOf("tarkas_prefs", "tarkas_loudness")
  }
}
