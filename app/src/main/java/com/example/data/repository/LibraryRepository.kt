package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.diagnostics.Diagnostics
import com.example.data.library.LibraryMigration
import com.example.data.library.SerialWorker
import com.example.data.library.TrackIds
import com.example.data.library.toEntity
import com.example.data.library.toTrack
import com.example.data.model.Playlist
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favorites and playlists, stored in Room (`favorites`, `playlists`, `playlist_tracks`, `tracks`).
 *
 * Every database access runs on one background worker, in order; the state the screens read is
 * published from there once the row is written. On the first start the old SharedPreferences JSON is
 * moved in ([LibraryMigration]). Playlists only keep track ids, so a snapshot of every saved track is
 * kept too; local `content://` files may need to be re-added if Android revoked access to them.
 */
class LibraryRepository(
  context: Context,
  private val db: AppDatabase,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
  private val appContext = context.applicationContext
  private val worker = SerialWorker(scope) { Diagnostics.record("LibraryRepository", "a library write failed", it) }

  private val _favorites = MutableStateFlow<List<String>>(emptyList())
  /** Favorite track ids, newest first. */
  val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

  private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
  val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

  private val _savedTracks = MutableStateFlow<Map<String, Track>>(emptyMap())
  val savedTracks: StateFlow<Map<String, Track>> = _savedTracks.asStateFlow()

  private var lastStamp = 0L

  init {
    worker.submit {
      LibraryMigration.ensure(appContext, db)
      load()
    }
  }

  /** Returns once everything asked so far is written and published. */
  suspend fun idle() = worker.idle()

  fun isFavorite(trackId: String): Boolean = trackId in _favorites.value

  fun toggleFavorite(track: Track) = worker.submit {
    val current = _favorites.value
    if (track.id in current) {
      db.favorites().remove(track.id)
      _favorites.value = current - track.id
      prune()
    } else {
      remember(track)
      db.favorites().add(FavoriteEntity(track.id, nextStamp()))
      _favorites.value = listOf(track.id) + current
    }
  }

  fun createPlaylist(name: String, firstTrack: Track? = null): Playlist {
    val playlist = Playlist(
      id = TrackIds.newPlaylist(),
      name = name.trim().ifBlank { appContext.getString(R.string.library_new_playlist_title) },
      trackIds = if (firstTrack != null) listOf(firstTrack.id) else emptyList()
    )
    worker.submit {
      db.withTransaction {
        if (firstTrack != null) remember(firstTrack)
        db.playlists().insert(PlaylistEntity(playlist.id, playlist.name, nextStamp()))
        if (firstTrack != null) db.playlists().setTracks(playlist.id, playlist.trackIds)
      }
      _playlists.value = _playlists.value + playlist
    }
    return playlist
  }

  fun renamePlaylist(playlistId: String, name: String) {
    if (name.isBlank()) return
    worker.submit {
      db.playlists().rename(playlistId, name.trim())
      _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(name = name.trim()) else it }
    }
  }

  fun deletePlaylist(playlistId: String) = worker.submit {
    db.playlists().delete(playlistId)
    _playlists.value = _playlists.value.filter { it.id != playlistId }
    prune()
  }

  fun addToPlaylist(playlistId: String, track: Track) = worker.submit {
    val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return@submit
    if (track.id in playlist.trackIds) return@submit
    db.withTransaction {
      remember(track)
      db.playlists().insertEntries(listOf(PlaylistTrackEntity(playlistId, playlist.trackIds.size, track.id)))
    }
    _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds + track.id) else it }
  }

  fun removeFromPlaylist(playlistId: String, trackId: String) = worker.submit {
    val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return@submit
    val ids = playlist.trackIds - trackId // the first occurrence only
    db.playlists().setTracks(playlistId, ids)
    _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(trackIds = ids) else it }
    prune()
  }

  // --- inside the worker ---

  private suspend fun load() {
    val entries = db.playlists().entries().groupBy({ it.playlistId }, { it.trackId })
    _favorites.value = db.favorites().all()
    _playlists.value = db.playlists().all().map { Playlist(it.id, it.name, entries[it.id].orEmpty()) }
    lastStamp = maxOf(lastStamp, db.playlists().all().maxOfOrNull { it.createdAt } ?: 0L)
    val used = (_favorites.value + entries.values.flatten()).distinct()
    _savedTracks.value = used.chunked(500).flatMap { db.tracks().byIds(it) }.map { it.toTrack() }.associateBy { it.id }
  }

  /** Keeps the snapshot of a track; a picked file's own row is left as it is. */
  private suspend fun remember(track: Track) {
    val existing = db.tracks().byIds(listOf(track.id)).firstOrNull()
    if (existing == null || !existing.picked) db.tracks().upsert(listOf(track.toEntity()))
    _savedTracks.value = _savedTracks.value + (track.id to track)
  }

  /** Drops the snapshots nothing points at any more. */
  private suspend fun prune() {
    db.tracks().deleteUnreferenced()
    val used = _favorites.value.toSet() + _playlists.value.flatMap { it.trackIds }
    _savedTracks.value = _savedTracks.value.filterKeys { it in used }
  }

  /** A time that never repeats, so the order of two quick actions is kept. */
  private fun nextStamp(): Long {
    lastStamp = maxOf(System.currentTimeMillis(), lastStamp + 1)
    return lastStamp
  }
}
