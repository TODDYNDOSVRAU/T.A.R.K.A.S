package com.example.data.playback

import com.example.data.TextMatch
import com.example.data.model.Track

/**
 * Search inside a list of songs: by title, artist and album at once, ignoring case and accents.
 * Every word typed has to appear somewhere in those three fields, so "beatles help" finds "Help!" by
 * The Beatles. The list keeps its order, which is how the chosen sort order stays valid in the result.
 */
object LibrarySearch {

  /** The text a track is searched in: title, artist and album folded once (see [TextMatch.fold]). */
  fun haystack(track: Track): String = TextMatch.fold("${track.title} ${track.artist} ${track.album}")

  /** The words of [query], folded; empty when the query is blank. */
  fun terms(query: String): List<String> =
    TextMatch.fold(query).split(' ', '\t', '\n').filter { it.isNotEmpty() }

  /**
   * The tracks of [tracks] that match every word of [query], in the same order. A blank query returns
   * [tracks] itself. [haystackOf] lets a caller pass folded text it already keeps, since folding is the
   * costly part on a long list.
   */
  fun filter(tracks: List<Track>, query: String, haystackOf: (Track) -> String = ::haystack): List<Track> {
    val words = terms(query)
    if (words.isEmpty()) return tracks
    return tracks.filter { t ->
      val text = haystackOf(t)
      words.all { it in text }
    }
  }
}
