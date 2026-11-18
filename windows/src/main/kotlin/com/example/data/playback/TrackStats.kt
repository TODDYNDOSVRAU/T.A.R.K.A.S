package com.example.data.playback

/** A track's counters. Desktop twin of the class in `player/funcoes/PlayStats.kt` (that file is Android-bound). */
data class TrackStats(
  val plays: Int = 0,
  val skips: Int = 0,
  /** When it last counted as played / skipped (ms since 1970), 0 for never. */
  val lastPlayedMs: Long = 0L,
  val lastSkippedMs: Long = 0L
)
