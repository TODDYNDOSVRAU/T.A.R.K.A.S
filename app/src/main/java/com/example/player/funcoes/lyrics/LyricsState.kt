package com.example.data.lyrics

/** What the lyrics screen shows for one song. */
data class LyricsState(
  val trackId: String? = null,
  val loading: Boolean = false,
  val lyrics: Lyrics? = null,
  /** The song is not a file on this device (a stream or a demo), so there is nowhere to look. */
  val notOnDevice: Boolean = false
) {
  /** Looked up, and nothing was found. */
  val none: Boolean get() = trackId != null && !loading && lyrics == null && !notOnDevice
}
