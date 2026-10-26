package com.example.data.repository

import android.content.Context
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.model.TrackJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites and playlists, persisted locally.
 *
 * Playlists only keep track ids, so a snapshot of every saved track is stored too.
 * That way streamed or local tracks still show up after the app restarts.
 * Local `content://` files may need to be re-added if Android revoked access to them.
 */
class LibraryRepository(context: Context) {
  private val prefs = context.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE)

  private val _favorites = MutableStateFlow(loadFavorites())
  /** Favorite track ids, newest first. */
  val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

  private val _playlists = MutableStateFlow(loadPlaylists())
  val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

  private val _savedTracks = MutableStateFlow(loadSavedTracks())
  val savedTracks: StateFlow<Map<String, Track>> = _savedTracks.asStateFlow()

  fun isFavorite(trackId: String): Boolean = trackId in _favorites.value

  fun toggleFavorite(track: Track) {
    val current = _favorites.value
    if (track.id in current) {
      _favorites.value = current - track.id
    } else {
      remember(track)
      _favorites.value = listOf(track.id) + current
    }
    persistFavorites()
    persistSavedTracks()
  }

  fun createPlaylist(name: String, firstTrack: Track? = null): Playlist {
    val playlist = Playlist(
      id = "pl_${System.currentTimeMillis()}",
      name = name.trim().ifBlank { "Nova playlist" },
      trackIds = if (firstTrack != null) listOf(firstTrack.id) else emptyList()
    )
    if (firstTrack != null) remember(firstTrack)
    _playlists.value = _playlists.value + playlist
    persistPlaylists()
    persistSavedTracks()
    return playlist
  }

  fun renamePlaylist(playlistId: String, name: String) {
    if (name.isBlank()) return
    _playlists.value = _playlists.value.map { if (it.id == playlistId) it.copy(name = name.trim()) else it }
    persistPlaylists()
  }

  fun deletePlaylist(playlistId: String) {
    _playlists.value = _playlists.value.filter { it.id != playlistId }
    persistPlaylists()
  }

  fun addToPlaylist(playlistId: String, track: Track) {
    remember(track)
    _playlists.value = _playlists.value.map {
      if (it.id == playlistId && track.id !in it.trackIds) it.copy(trackIds = it.trackIds + track.id) else it
    }
    persistPlaylists()
    persistSavedTracks()
  }

  fun removeFromPlaylist(playlistId: String, trackId: String) {
    _playlists.value = _playlists.value.map {
      if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
    }
    persistPlaylists()
  }

  private fun remember(track: Track) {
    _savedTracks.value = _savedTracks.value + (track.id to track)
  }

  // --- persistence ---

  private fun persistFavorites() {
    val array = JSONArray()
    _favorites.value.forEach { array.put(it) }
    prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
  }

  private fun persistPlaylists() {
    val array = JSONArray()
    _playlists.value.forEach { p ->
      val ids = JSONArray()
      p.trackIds.forEach { ids.put(it) }
      array.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", ids))
    }
    prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
  }

  private fun persistSavedTracks() {
    // Keep only tracks that something still points at.
    val used = _favorites.value.toSet() + _playlists.value.flatMap { it.trackIds }
    val array = TrackJson.listToJson(_savedTracks.value.values.filter { it.id in used })
    prefs.edit().putString(KEY_TRACKS, array.toString()).apply()
  }

  private fun loadFavorites(): List<String> = try {
    val array = JSONArray(prefs.getString(KEY_FAVORITES, "[]"))
    (0 until array.length()).map { array.getString(it) }
  } catch (_: Exception) {
    emptyList()
  }

  private fun loadPlaylists(): List<Playlist> = try {
    val array = JSONArray(prefs.getString(KEY_PLAYLISTS, "[]"))
    (0 until array.length()).map { i ->
      val o = array.getJSONObject(i)
      val ids = o.optJSONArray("tracks") ?: JSONArray()
      Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
    }
  } catch (_: Exception) {
    emptyList()
  }

  private fun loadSavedTracks(): Map<String, Track> = try {
    TrackJson.listFromJson(JSONArray(prefs.getString(KEY_TRACKS, "[]"))).associateBy { it.id }
  } catch (_: Exception) {
    emptyMap()
  }

  private companion object {
    const val KEY_FAVORITES = "favorites"
    const val KEY_PLAYLISTS = "playlists"
    const val KEY_TRACKS = "saved_tracks"
  }
}
