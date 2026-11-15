package com.example.data.library

import android.content.Context
import androidx.room.withTransaction
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.SettingEntity
import com.example.data.diagnostics.Diagnostics
import com.example.data.model.Track
import com.example.data.model.TrackJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Moves the library from SharedPreferences JSON (`tarkas_library`: favorites, playlists, saved_tracks;
 * `tarkas_library_files`: the picked files) to Room, once. Everything is written in one transaction
 * together with the marker, so a failure leaves the marker out and the next start tries again. The old
 * preferences are never changed or deleted. What cannot be read is skipped and counted (no title or
 * path goes to the diagnostics); what can be read is always kept.
 */
object LibraryMigration {
  const val MARKER_SCOPE = "migration"
  const val MARKER_KEY = "library_json"

  /** settings scope holding one row per renamed picked file: key = old id, value = new id. */
  const val ID_MAP_SCOPE = "picked_id_map"

  private val lock = Mutex()

  class Playlist(val id: String, val name: String, val trackIds: List<String>)

  class Parsed(
    /** Newest first. */
    val favorites: List<String>,
    /** In creation order. */
    val playlists: List<Playlist>,
    val saved: List<Track>,
    /** Newest first. */
    val picked: List<Track>,
    /** Entries that were cut or had no usable data. */
    val skipped: Int,
    /** Old picked-file id (`local_<ms>`) to the new one, for everything else that stored the old id. */
    val idMap: Map<String, String> = emptyMap()
  )

  /** Safe to call from several places: the first does the work, the others wait and find the marker. */
  suspend fun ensure(context: Context, db: AppDatabase) = lock.withLock {
    if (db.settings().scope(MARKER_SCOPE).any { it.key == MARKER_KEY }) return@withLock
    val app = context.applicationContext
    try {
      val library = app.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE)
      val files = app.getSharedPreferences("tarkas_library_files", Context.MODE_PRIVATE)
      val parsed = parse(
        library.getString("favorites", null), library.getString("playlists", null),
        library.getString("saved_tracks", null), files.getString("picked", null),
        app.getString(R.string.library_new_playlist_title)
      )
      write(db, parsed, System.currentTimeMillis())
      if (parsed.skipped > 0) {
        Diagnostics.record("LibraryMigration", "library moved to the database; ${parsed.skipped} unreadable entries skipped")
      }
    } catch (e: Exception) {
      Diagnostics.record("LibraryMigration", "library not moved to the database; the old data is kept and it will be tried again", e)
    }
  }

  private suspend fun write(db: AppDatabase, p: Parsed, nowMs: Long) {
    val nowSec = nowMs / 1000
    val tracks = LinkedHashMap<String, com.example.data.db.TrackEntity>()
    p.saved.forEach { tracks[it.id] = it.toEntity(picked = false) }
    // Picked files keep their order through the date: the newest has the biggest.
    p.picked.forEachIndexed { i, t -> tracks[t.id] = t.toEntity(picked = true, dateAddedSec = nowSec - i) }
    db.withTransaction {
      db.tracks().upsert(tracks.values.toList())
      db.favorites().addAll(p.favorites.mapIndexed { i, id -> FavoriteEntity(id, nowMs - i) })
      p.playlists.forEachIndexed { i, pl ->
        db.playlists().insert(PlaylistEntity(pl.id, pl.name, nowMs + i))
        db.playlists().setTracks(pl.id, pl.trackIds)
      }
      p.idMap.forEach { (old, new) -> db.settings().put(SettingEntity(ID_MAP_SCOPE, old, new)) }
      db.settings().put(SettingEntity(MARKER_SCOPE, MARKER_KEY, "1"))
    }
  }

  /**
   * Old picked-file id to new id, as written by the migration (empty when nothing was renamed).
   * Whoever stored the old id elsewhere (play statistics, the saved queue) rewrites it with this.
   */
  suspend fun idMap(db: AppDatabase): Map<String, String> =
    db.settings().scope(ID_MAP_SCOPE).associate { it.key to it.value }

  /** The pure part: reads the four stored texts (any may be null or damaged) into what goes to Room. */
  fun parse(
    favoritesJson: String?, playlistsJson: String?, savedJson: String?, pickedJson: String?, defaultName: String
  ): Parsed {
    var skipped = 0

    val saved = ArrayList<Track>()
    val picked = ArrayList<Track>()
    for ((raw, into) in listOf(savedJson to saved, pickedJson to picked)) {
      for (element in JsonSalvage.elements(raw)) {
        val obj = runCatching { JSONObject(element) }.getOrNull()
        if (obj == null || obj.optString("id").isBlank()) { skipped++; continue }
        // null: a song of an integration that no longer exists, dropped on purpose.
        val track = runCatching { TrackJson.fromJson(obj) }.getOrElse { skipped++; null }
        if (track != null) into += track
      }
    }

    // Files picked before 1.18 had the time of the pick as id; the new id comes from the address.
    val remap = HashMap<String, String>()
    (saved + picked).forEach { t ->
      val path = t.filePath
      if (t.isLocalFile && path != null && t.id.startsWith(TrackIds.PICKED_PREFIX)) remap[t.id] = TrackIds.picked(path)
    }
    fun id(old: String) = remap[old] ?: old

    val favorites = LinkedHashSet<String>()
    for (element in JsonSalvage.elements(favoritesJson)) {
      val value = runCatching { JSONTokener(element).nextValue() }.getOrNull()
      if (value is String && value.isNotBlank()) favorites += id(value) else skipped++
    }

    val playlists = ArrayList<Playlist>()
    val usedIds = HashSet<String>()
    for (element in JsonSalvage.elements(playlistsJson)) {
      val obj = runCatching { JSONObject(element) }.getOrNull()
      if (obj == null) { skipped++; continue }
      // A playlist without an id (or with one already taken) still has its tracks: it gets a new id.
      val given = obj.optString("id").trim()
      val playlistId = if (given.isNotEmpty() && usedIds.add(given)) given else TrackIds.newPlaylist().also { usedIds += it }
      val ids = obj.optJSONArray("tracks")
      val trackIds = (0 until (ids?.length() ?: 0)).mapNotNull { i ->
        (ids!!.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let(::id) ?: run { skipped++; null }
      }
      playlists += Playlist(playlistId, obj.optString("name").trim().ifBlank { defaultName }, trackIds)
    }

    fun remapped(list: List<Track>) = list.map { if (it.id in remap) it.copy(id = id(it.id)) else it }
      .distinctBy { it.id }
    return Parsed(favorites.toList(), playlists, remapped(saved), remapped(picked), skipped, remap.filter { it.key != it.value })
  }
}
