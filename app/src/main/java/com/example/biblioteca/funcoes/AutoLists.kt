package com.example.data.library

import com.example.data.model.Track
import com.example.data.playback.TrackStats

/** The lists the library builds by itself from what it knows about each song. */
enum class AutoList {
  RECENTLY_ADDED,
  RECENTLY_PLAYED,
  MOST_PLAYED
}

/**
 * Builds the automatic lists. Pure: the songs and their counters go in, the ordered lists come out, so
 * the rules are tested on the JVM. Only songs that are in [tracks] appear (a counter of a song that left
 * the library is kept in the database but not listed), and a song with nothing to say for a list is not
 * in it: never played is not in "recently played" or "most played".
 */
object AutoLists {
  /** A list shows this many songs at most: it is a shortcut, not a second library. */
  const val LIMIT = 50

  fun build(list: AutoList, tracks: List<Track>, stats: Map<String, TrackStats>, limit: Int = LIMIT): List<Track> {
    val ordered = when (list) {
      AutoList.RECENTLY_ADDED -> tracks.filter { it.dateAddedSec > 0 }
        .sortedWith(compareByDescending<Track> { it.dateAddedSec }.thenBy { it.title.lowercase() }.thenBy { it.id })

      AutoList.RECENTLY_PLAYED -> tracks.filter { (stats[it.id]?.lastPlayedMs ?: 0L) > 0 }
        .sortedWith(compareByDescending<Track> { stats.getValue(it.id).lastPlayedMs }.thenBy { it.id })

      AutoList.MOST_PLAYED -> tracks.filter { (stats[it.id]?.plays ?: 0) > 0 }
        .sortedWith(
          compareByDescending<Track> { stats.getValue(it.id).plays }
            .thenByDescending { stats.getValue(it.id).lastPlayedMs }
            .thenBy { it.id }
        )
    }
    return ordered.take(limit)
  }

  /** The three lists at once, for the screen that shows them side by side. */
  fun all(tracks: List<Track>, stats: Map<String, TrackStats>): Map<AutoList, List<Track>> =
    AutoList.entries.associateWith { build(it, tracks, stats) }
}
