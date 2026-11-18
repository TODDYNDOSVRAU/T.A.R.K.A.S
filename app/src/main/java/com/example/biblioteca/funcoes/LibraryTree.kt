package com.example.data.playback

import com.example.data.model.Playlist
import com.example.data.model.Track

/**
 * What a car (Android Auto), a watch or any media browser sees when it opens the library: a short tree
 * with three folders, Favourites, Playlists and the whole library, each leading to tracks. Pure: the
 * service gives it the data and turns the nodes into media items.
 */
object LibraryTree {
  const val ROOT = "tarkas:root"
  const val FAVORITES = "tarkas:favorites"
  const val PLAYLISTS = "tarkas:playlists"
  const val LIBRARY = "tarkas:library"
  private const val PLAYLIST_PREFIX = "tarkas:playlist:"

  /** A cars's list is read while driving: longer than this is not browsable anyway. */
  const val MAX_CHILDREN = 500

  enum class Folder { FAVORITES, PLAYLISTS, LIBRARY, PLAYLIST }

  /** One entry: a folder ([folder] set, with [name] only for a playlist) or a track ([track] set). */
  data class Node(val id: String, val folder: Folder? = null, val name: String? = null, val track: Track? = null)

  fun playlistId(id: String) = PLAYLIST_PREFIX + id

  fun isFolder(id: String) = id == ROOT || id == FAVORITES || id == PLAYLISTS || id == LIBRARY || id.startsWith(PLAYLIST_PREFIX)

  fun root(): Node = Node(ROOT)

  /** The children of [parentId], or null when it is not a folder of this tree. Missing tracks are skipped. */
  fun children(
    parentId: String,
    library: List<Track>,
    favorites: List<String>,
    playlists: List<Playlist>,
    saved: Map<String, Track>
  ): List<Node>? {
    val byId = HashMap<String, Track>(library.size + saved.size).apply {
      putAll(saved)
      library.forEach { put(it.id, it) }
    }
    fun tracksOf(ids: List<String>) = ids.mapNotNull { byId[it] }
    return when {
      parentId == ROOT -> listOf(
        Node(FAVORITES, Folder.FAVORITES),
        Node(PLAYLISTS, Folder.PLAYLISTS),
        Node(LIBRARY, Folder.LIBRARY)
      )
      parentId == FAVORITES -> tracksOf(favorites).map(::trackNode)
      parentId == PLAYLISTS -> playlists.map { Node(playlistId(it.id), Folder.PLAYLIST, it.name) }
      parentId == LIBRARY -> library.map(::trackNode)
      parentId.startsWith(PLAYLIST_PREFIX) -> {
        val playlist = playlists.firstOrNull { it.id == parentId.removePrefix(PLAYLIST_PREFIX) } ?: return null
        tracksOf(playlist.trackIds).map(::trackNode)
      }
      else -> null
    }?.take(MAX_CHILDREN)
  }

  /**
   * The list a track chosen from [parentId] plays in (so Next and Previous stay inside that folder), or
   * the whole library when [parentId] is not a folder with tracks or the track is not in it.
   */
  fun contextFor(
    parentId: String?,
    track: Track,
    library: List<Track>,
    favorites: List<String>,
    playlists: List<Playlist>,
    saved: Map<String, Track>
  ): List<Track> {
    val tracks = parentId
      ?.takeIf { it != ROOT && it != PLAYLISTS }
      ?.let { children(it, library, favorites, playlists, saved) }
      ?.mapNotNull { it.track }
    return if (tracks != null && tracks.any { it.id == track.id }) tracks else library
  }

  /**
   * The track a voice search ("play X") means: the first whose title, then artist, then album contains
   * every word of [query] (case and accents ignored); null when the query is blank or nothing matches.
   */
  fun search(query: String, library: List<Track>): Track? {
    val words = fold(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    fun matches(text: String): Boolean { val f = fold(text); return words.all { it in f } }
    return library.firstOrNull { matches(it.title) }
      ?: library.firstOrNull { matches(it.artist) }
      ?: library.firstOrNull { matches(it.album) }
  }

  private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

  private fun trackNode(track: Track) = Node(track.id, track = track)
}
