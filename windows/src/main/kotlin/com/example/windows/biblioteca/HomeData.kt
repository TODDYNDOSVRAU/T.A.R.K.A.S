package com.example.windows.biblioteca

import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.library.LibraryGroups
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.TrackStats
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** One entry of Speed dial or Daily Mixes: a cover, two lines and what a click plays. [titleKey] is non-null for entries the UI names itself. */
data class HomeEntry(val id: String, val title: String, val subtitle: String, val cover: Track?, val tracks: List<Track>, val kind: Kind) {
  enum class Kind { FAVOURITES, PLAYLIST, TRACK, AUTO, GENRE, ALBUM }
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
      out += HomeEntry("t:" + t.id, t.title, t.artistLabel(), t, listOf(t), HomeEntry.Kind.TRACK)
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

  /** Two lists woven one by one (a, b, a, b, ...), without repeated ids, so neither catalogue crowds the other out. */
  fun interleave(a: List<Track>, b: List<Track>, limit: Int = 24): List<Track> {
    val out = ArrayList<Track>()
    for (i in 0 until maxOf(a.size, b.size)) {
      a.getOrNull(i)?.let { out += it }
      b.getOrNull(i)?.let { out += it }
    }
    return out.distinctBy { it.id }.take(limit)
  }

  /** Most carousels the page shows before the catalogue shelf. */
  const val MAX_RAILS = 4

  /**
   * Which carousels the page shows. [rails] lists each one's key with the ids it holds, in page order. A carousel with
   * nothing in it is skipped, and so is one that only repeats what the one before already shows (for example "Listen
   * again" when "Continue listening" has the same songs); after that, at most [max] remain.
   */
  fun railsToShow(rails: List<Pair<String, List<String>>>, max: Int = MAX_RAILS): List<String> {
    val shown = ArrayList<Pair<String, Set<String>>>()
    for ((key, ids) in rails) {
      if (shown.size >= max) break
      if (ids.isEmpty()) continue
      val set = ids.toSet()
      if (shown.any { (_, before) -> before.containsAll(set) }) continue
      shown += key to set
    }
    return shown.map { it.first }
  }

  /** How long a catalogue may take before its loading placeholder shows; an answer that comes sooner never flashes it. */
  const val SKELETON_DELAY_MS = 300L

  /** True when the loading placeholder is visible: still [loading] and at least [SKELETON_DELAY_MS] have passed. */
  fun skeletonShows(loading: Boolean, elapsedMs: Long): Boolean = loading && elapsedMs >= SKELETON_DELAY_MS

  /** Stiffness of the one spring every Home movement uses; with damping ratio 1 it settles in about 300 ms without overshoot. */
  const val SETTLE_STIFFNESS = 439f

  /** Natural frequency (rad/s) of that spring: sqrt(stiffness) for unit mass. */
  val SETTLE_OMEGA: Float = sqrt(SETTLE_STIFFNESS)

  /**
   * The speed a critically damped spring may start from when it travels [distance] (signed). Speed toward the target
   * above omega x distance would overshoot it, so it is cut to 90 % of that; speed away from the target is kept.
   */
  fun springVelocity(velocity: Float, distance: Float, omega: Float = SETTLE_OMEGA): Float {
    if (distance == 0f) return 0f
    val sign = if (distance > 0f) 1f else -1f
    val toward = velocity * sign
    val cap = 0.9f * omega * abs(distance)
    return sign * min(toward, cap)
  }

  /** Scale of a card: 0.96 pressed, 1.04 under the pointer, 1 otherwise. */
  fun cardScale(hovered: Boolean, pressed: Boolean): Float = when {
    pressed -> 0.96f
    hovered -> 1.04f
    else -> 1f
  }

  /** The entry the page opens with: the first mix of the library, else the first album; null when there is nothing to feature. */
  fun featured(mixes: List<HomeEntry>, albums: List<HomeEntry>): HomeEntry? =
    (mixes.firstOrNull { it.tracks.isNotEmpty() } ?: albums.firstOrNull { it.tracks.isNotEmpty() })

  /** Albums found among [tracks] (a catalogue's songs): the tracks that share an album and artist, in first-seen order. Songs without an album are left out. */
  fun albums(tracks: List<Track>, limit: Int = 12): List<HomeEntry> =
    tracks.filter { it.album.isNotBlank() }.groupBy { it.album.trim().lowercase() + "|" + it.artist.trim().lowercase() }.values
      .take(limit).map { g ->
        val first = g.first()
        HomeEntry("al:" + first.album + "|" + first.artist, first.album, first.artistLabel(), first, g, HomeEntry.Kind.ALBUM)
      }
}
