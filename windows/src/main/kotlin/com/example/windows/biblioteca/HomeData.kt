package com.example.windows.biblioteca

import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.library.LibraryGroups
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.TrackStats

/** One entry of Speed dial or Daily Mixes: a cover, two lines and what a click plays. [titleKey] is non-null for entries the UI names itself. */
data class HomeEntry(val id: String, val title: String, val subtitle: String, val cover: Track?, val tracks: List<Track>, val kind: Kind) {
  enum class Kind { FAVOURITES, PLAYLIST, TRACK, AUTO, GENRE }
}

/** What the Home dashboard shows, computed from real library data only. Pure, so the JVM tests cover it. */
object HomeData {
  const val SPEED_DIAL_MAX = 12
  const val MIXES_MAX = 8
  const val TOP_TRACKS = 6
  const val TOP_GENRES = 3

  fun speedDial(
    library: List<Track>, favouriteIds: Set<String>, playlists: List<Playlist>, stats: Map<String, TrackStats>,
    favouritesTitle: String, countLabel: (Int) -> String
  ): List<HomeEntry> {
    val byId = library.associateBy { it.id }
    val out = mutableListOf<HomeEntry>()
    val fav = library.filter { it.id in favouriteIds }
    if (fav.isNotEmpty()) out += HomeEntry("fav", favouritesTitle, countLabel(fav.size), fav.first(), fav, HomeEntry.Kind.FAVOURITES)
    for (p in playlists) {
      val t = p.trackIds.mapNotNull { byId[it] }
      if (t.isNotEmpty()) out += HomeEntry("p:" + p.id, p.name, countLabel(t.size), t.first(), t, HomeEntry.Kind.PLAYLIST)
    }
    for (t in AutoLists.build(AutoList.MOST_PLAYED, library, stats, TOP_TRACKS)) {
      out += HomeEntry("t:" + t.id, t.title, t.artist, t, listOf(t), HomeEntry.Kind.TRACK)
    }
    return out.take(SPEED_DIAL_MAX)
  }

  fun mixes(library: List<Track>, stats: Map<String, TrackStats>, autoName: (AutoList) -> String, countLabel: (Int) -> String): List<HomeEntry> {
    val out = mutableListOf<HomeEntry>()
    for (l in AutoList.entries) {
      val t = AutoLists.build(l, library, stats)
      if (t.isNotEmpty()) out += HomeEntry("a:" + l.name, autoName(l), countLabel(t.size), t.first(), t, HomeEntry.Kind.AUTO)
    }
    for (g in LibraryGroups.genres(library).sortedByDescending { it.tracks.size }.take(TOP_GENRES)) {
      if (g.tracks.isNotEmpty()) out += HomeEntry("g:" + g.key, g.name, countLabel(g.tracks.size), g.tracks.first(), g.tracks, HomeEntry.Kind.GENRE)
    }
    return out.take(MIXES_MAX)
  }

  /** What is playing, then the history, newest first, without repeats. */
  fun listening(current: Track?, history: List<Track>, limit: Int = 12): List<Track> =
    (listOfNotNull(current) + history.asReversed()).distinctBy { it.id }.take(limit)
}
