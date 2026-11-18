package com.example.windows.biblioteca

import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlayStatEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.library.SerialWorker
import com.example.data.library.TrackIds
import com.example.data.library.toEntity
import com.example.data.library.toTrack
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.TrackStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favourites, playlists, files the user imported one by one and the play counters, in the Room database of the
 * app. Desktop twin of the Android `LibraryRepository`: the same rules (a snapshot of every saved track, the
 * ones nothing points at any more are dropped), no Android. Every write runs on one worker, in order, and the
 * state the screens read is published from there once the row is written.
 */
class LibraryStore(
  private val db: AppDatabase,
  scope: CoroutineScope,
  private val newPlaylistName: String = "New playlist",
  private val onError: (Throwable) -> Unit = {}
) {
  private val worker = SerialWorker(scope) { onError(it) }

  private val _favorites = MutableStateFlow<List<String>>(emptyList())
  /** Favourite track ids, newest first. */
  val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

  private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
  val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

  private val _savedTracks = MutableStateFlow<Map<String, Track>>(emptyMap())
  /** The saved copy of every track a favourite or a playlist points at, so they list even when the file is gone. */
  val savedTracks: StateFlow<Map<String, Track>> = _savedTracks.asStateFlow()

  private val _picked = MutableStateFlow<List<Track>>(emptyList())
  /** Files the user imported one by one, newest first. */
  val picked: StateFlow<List<Track>> = _picked.asStateFlow()

  private val _stats = MutableStateFlow<Map<String, TrackStats>>(emptyMap())
  val stats: StateFlow<Map<String, TrackStats>> = _stats.asStateFlow()

  private var lastStamp = 0L

  init {
    worker.submit { load() }
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

  fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
    val playlist = Playlist(TrackIds.newPlaylist(), name.trim().ifBlank { newPlaylistName }, tracks.map { it.id })
    worker.submit {
      tracks.distinctBy { it.id }.forEach { remember(it) }
      db.playlists().insert(PlaylistEntity(playlist.id, playlist.name, nextStamp()))
      db.playlists().setTracks(playlist.id, playlist.trackIds)
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
    remember(track)
    db.playlists().insertEntries(listOf(PlaylistTrackEntity(playlistId, playlist.trackIds.size, track.id)))
    _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds + track.id) else it }
  }

  fun removeFromPlaylist(playlistId: String, trackId: String) = worker.submit {
    val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return@submit
    val ids = playlist.trackIds - trackId // the first occurrence only
    db.playlists().setTracks(playlistId, ids)
    _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(trackIds = ids) else it }
    prune()
  }

  /** Adds a file the user picked. The same file picked again is the same track: nothing is added twice. */
  fun addPicked(track: Track) = worker.submit {
    val dated = if (track.dateAddedSec > 0) track else track.copy(dateAddedSec = System.currentTimeMillis() / 1000L)
    db.tracks().upsert(listOf(dated.toEntity(picked = true)))
    _picked.value = listOf(dated) + _picked.value.filter { it.id != dated.id }
  }

  fun removePicked(trackId: String) = worker.submit {
    val used = trackId in _favorites.value || _playlists.value.any { trackId in it.trackIds }
    if (used) db.tracks().upsert(listOf((_picked.value.first { it.id == trackId }).toEntity(picked = false)))
    else db.tracks().delete(trackId)
    _picked.value = _picked.value.filter { it.id != trackId }
    prune()
  }

  /** Counts one play of [trackId] now. */
  fun recordPlay(trackId: String, nowMs: Long = System.currentTimeMillis()) = worker.submit {
    val old = _stats.value[trackId] ?: TrackStats()
    val new = old.copy(plays = old.plays + 1, lastPlayedMs = nowMs)
    db.playStats().upsert(PlayStatEntity(trackId, new.plays, new.skips, new.lastPlayedMs, new.lastSkippedMs))
    _stats.value = _stats.value + (trackId to new)
  }

  // --- inside the worker ---

  private suspend fun load() {
    val entries = db.playlists().entries().groupBy({ it.playlistId }, { it.trackId })
    val lists = db.playlists().all()
    _favorites.value = db.favorites().all()
    _playlists.value = lists.map { Playlist(it.id, it.name, entries[it.id].orEmpty()) }
    lastStamp = maxOf(lastStamp, lists.maxOfOrNull { it.createdAt } ?: 0L)
    val used = (_favorites.value + entries.values.flatten()).distinct()
    _savedTracks.value = used.chunked(500).flatMap { db.tracks().byIds(it) }.map { it.toTrack() }.associateBy { it.id }
    _picked.value = db.tracks().picked().map { it.toTrack() }.reversed()
    _stats.value = db.playStats().all().associate { it.trackId to TrackStats(it.plays, it.skips, it.lastPlayed, it.lastSkipped) }
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
