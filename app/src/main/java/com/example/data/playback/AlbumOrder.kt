package com.example.data.playback

import com.example.data.model.Track

/** Album rules: track numbers from the media library, and when two tracks follow each other on an album. */
object AlbumOrder {

  /**
   * The media library's TRACK column holds disc × 1000 + track ("2005" is disc 2, track 5); a file
   * without a disc number gives the track alone. Returns (disc, track), 0 for what is unknown.
   */
  fun discAndTrack(raw: Int): Pair<Int, Int> = when {
    raw <= 0 -> 0 to 0
    raw >= 1000 -> raw / 1000 to raw % 1000
    else -> 0 to raw
  }

  /**
   * [b] comes right after [a] on the same album: same album, same disc, next track number. A disc
   * number missing from both counts as the same disc. Unknown albums or track numbers never follow.
   */
  fun follows(a: Track?, b: Track?): Boolean {
    if (a == null || b == null || a.id == b.id) return false
    val album = a.albumId
    if (album == null || album != b.albumId) return false
    if (a.trackNumber <= 0 || b.trackNumber <= 0) return false
    return a.discNumber == b.discNumber && b.trackNumber == a.trackNumber + 1
  }

  /** The album is being played in order around [current]: the one before leads to it, or it leads to the next. */
  fun inAlbumSequence(previous: Track?, current: Track, next: Track?): Boolean =
    follows(previous, current) || follows(current, next)
}
