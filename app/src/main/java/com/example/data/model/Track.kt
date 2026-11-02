package com.example.data.model

data class Track(
  val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val genre: String,
  val era: String,
  val isLocalFile: Boolean = false,
  val filePath: String? = null,
  val synthesisPreset: String? = null,
  val colorHex: Long = 0xFF00E5FF,
  /** Cover art or thumbnail; the color gradient shows when it is missing. */
  val artworkUrl: String? = null,
  /** One of the short synthesized tracks that ship with the app, kept apart from the user's music. */
  val isDemo: Boolean = false,
  /** The media library's album id (device music); null when unknown. */
  val albumId: Long? = null,
  /** Disc and track number from the file's tags; 0 when the file does not say. */
  val discNumber: Int = 0,
  val trackNumber: Int = 0,
  /** When the file reached the device (seconds since 1970); 0 when unknown. */
  val dateAddedSec: Long = 0L
) {
  /** The online catalogue the track streams from ("Audius", "Jamendo"), or null for the device's own. */
  val onlineSource: String?
    get() = when {
      id.startsWith("audius_") -> "Audius"
      id.startsWith("jamendo_") -> "Jamendo"
      else -> null
    }

  fun formattedDuration(): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
  }
}
