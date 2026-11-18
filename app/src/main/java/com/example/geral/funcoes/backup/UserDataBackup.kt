package com.example.data.backup

import android.content.SharedPreferences
import androidx.room.withTransaction
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
 * Export and restore of everything the app keeps about the person: settings, playback and
 * normalisation preferences, and the database (library, playlists, favourites, play statistics and
 * the player state). The export is readable JSON; the restore reads the whole file and checks it
 * BEFORE it changes anything, then replaces the data in one step.
 *
 * Preferences go by file name ([PREFS_SCOPES]), each value with its type, so a restore gives back
 * exactly what was saved.
 */
class UserDataBackup(
  private val db: AppDatabase,
  /** Opens the preferences file of a scope (`tarkas_prefs`, `tarkas_loudness`). */
  private val prefsOf: (String) -> SharedPreferences
) {

  suspend fun export(appVersion: String, exportedAt: String): JSONObject {
    val prefs = JSONObject()
    for (scope in PREFS_SCOPES) prefs.put(scope, prefsToJson(prefsOf(scope)))
    val playlists = JSONArray()
    val entries = db.playlists().entries().groupBy { it.playlistId }
    for (p in db.playlists().all()) {
      playlists.put(
        JSONObject().put("id", p.id).put("name", p.name).put("createdAt", p.createdAt)
          .put("tracks", JSONArray((entries[p.id] ?: emptyList()).sortedBy { it.position }.map { it.trackId }))
      )
    }
    val favourites = JSONArray()
    // Newest first, the order the app shows; the times are what keeps it on restore.
    db.favorites().entities().forEach { favourites.put(JSONObject().put("trackId", it.trackId).put("addedAt", it.addedAt)) }
    return JSONObject()
      .put("app", APP)
      .put("format", FORMAT)
      .put("version", appVersion)
      .put("exportedAt", exportedAt)
      .put("preferences", prefs)
      .put("tracks", JSONArray().also { a -> db.tracks().all().sortedBy { it.id }.forEach { a.put(trackToJson(it)) } })
      .put("favorites", favourites)
      .put("playlists", playlists)
      .put("playStats", JSONArray().also { a ->
        db.playStats().all().sortedBy { it.trackId }.forEach {
          a.put(JSONObject().put("trackId", it.trackId).put("plays", it.plays).put("skips", it.skips)
            .put("lastPlayed", it.lastPlayed).put("lastSkipped", it.lastSkipped))
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

  /**
   * Replaces the data with the file's. Throws [RestoreException] before touching anything when the
   * file is not a T.A.R.K.A.S. export or is damaged. The app has to reopen afterwards, so that every
   * screen and the player read the restored data.
   */
  suspend fun restore(text: String) {
    val plan = parse(text)
    db.withTransaction {
      db.playlists().deleteAll() // entries go with them (foreign key)
      db.favorites().deleteAll()
      db.playStats().deleteAll()
      db.playback().clear()
      db.settings().deleteAll()
      db.tracks().deleteAll()
      db.tracks().upsert(plan.tracks)
      db.favorites().addAll(plan.favorites)
      for (p in plan.playlists) {
        db.playlists().insert(p.first)
        db.playlists().insertEntries(p.second)
      }
      db.playStats().upsertAll(plan.stats)
      plan.playback?.let { db.playback().put(it) }
      db.settings().putAll(plan.settings)
    }
    // Preferences last: the database is the part that can fail halfway, and it rolls back as a whole.
    for ((scope, values) in plan.prefs) applyPrefs(prefsOf(scope), values)
  }

  private class Plan(
    val tracks: List<TrackEntity>,
    val favorites: List<FavoriteEntity>,
    val playlists: List<Pair<PlaylistEntity, List<PlaylistTrackEntity>>>,
    val stats: List<PlayStatEntity>,
    val playback: PlaybackStateEntity?,
    val settings: List<SettingEntity>,
    val prefs: Map<String, Map<String, Any>>
  )

  private fun parse(text: String): Plan {
    try {
      val o = JSONObject(text)
      if (o.optString("app") != APP) throw RestoreException("not a $APP export")
      val format = o.optInt("format", -1)
      if (format != FORMAT) throw RestoreException("export format $format is not supported")
      val prefs = LinkedHashMap<String, Map<String, Any>>()
      val prefsJson = o.getJSONObject("preferences")
      for (scope in PREFS_SCOPES) prefs[scope] = prefsFromJson(prefsJson.getJSONObject(scope))
      val tracks = o.getJSONArray("tracks").objects().map(::trackFromJson)
      val ids = tracks.map { it.id }.toHashSet()
      val favourites = o.getJSONArray("favorites").objects().map {
        FavoriteEntity(it.getString("trackId"), it.getLong("addedAt"))
      }
      val playlists = o.getJSONArray("playlists").objects().map { p ->
        val id = p.getString("id")
        val list = p.getJSONArray("tracks")
        val rows = (0 until list.length()).map { PlaylistTrackEntity(id, it, list.getString(it)) }
        PlaylistEntity(id, p.getString("name"), p.getLong("createdAt")) to rows
      }
      if (playlists.map { it.first.id }.toSet().size != playlists.size) throw RestoreException("two playlists share an id")
      if (ids.size != tracks.size) throw RestoreException("two tracks share an id")
      val stats = o.getJSONArray("playStats").objects().map {
        PlayStatEntity(it.getString("trackId"), it.getInt("plays"), it.getInt("skips"), it.getLong("lastPlayed"), it.getLong("lastSkipped"))
      }
      val playback = o.optJSONObject("playback")?.let {
        PlaybackStateEntity(
          0, it.getString("stateJson"), it.getLong("positionMs"),
          if (it.isNull("positionTrackId")) null else it.getString("positionTrackId")
        )
      }
      val settings = o.getJSONArray("settings").objects().map {
        SettingEntity(it.getString("scope"), it.getString("key"), it.getString("value"))
      }
      return Plan(tracks, favourites, playlists, stats, playback, settings, prefs)
    } catch (e: JSONException) {
      throw RestoreException("damaged file: ${e.message}")
    }
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

  private fun prefsToJson(p: SharedPreferences): JSONObject {
    val out = JSONObject()
    for ((k, v) in p.all.toSortedMap()) {
      val typed = when (v) {
        is Boolean -> "b"
        is Int -> "i"
        is Long -> "l"
        is Float -> "f"
        is String -> "s"
        is Set<*> -> "set"
        else -> continue
      }
      out.put(k, JSONObject().put("t", typed).put("v", if (v is Set<*>) JSONArray(v.map { it.toString() }.sorted()) else v))
    }
    return out
  }

  private fun prefsFromJson(o: JSONObject): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    for (k in o.keys()) {
      val e = o.getJSONObject(k)
      out[k] = when (val t = e.getString("t")) {
        "b" -> e.getBoolean("v")
        "i" -> e.getInt("v")
        "l" -> e.getLong("v")
        "f" -> e.getDouble("v").toFloat()
        "s" -> e.getString("v")
        "set" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> throw RestoreException("unknown preference type '$t'")
      }
    }
    return out
  }

  @Suppress("UNCHECKED_CAST")
  private fun applyPrefs(p: SharedPreferences, values: Map<String, Any>) {
    val e = p.edit().clear()
    for ((k, v) in values) when (v) {
      is Boolean -> e.putBoolean(k, v)
      is Int -> e.putInt(k, v)
      is Long -> e.putLong(k, v)
      is Float -> e.putFloat(k, v)
      is String -> e.putString(k, v)
      is Set<*> -> e.putStringSet(k, v as Set<String>)
    }
    // commit, not apply: the app is about to reopen and the write has to be on disk by then.
    if (!e.commit()) throw RestoreException("preferences could not be written")
  }

  companion object {
    const val APP = "T.A.R.K.A.S."
    const val FORMAT = 2
    val PREFS_SCOPES = listOf("tarkas_prefs", "tarkas_loudness")
  }
}
