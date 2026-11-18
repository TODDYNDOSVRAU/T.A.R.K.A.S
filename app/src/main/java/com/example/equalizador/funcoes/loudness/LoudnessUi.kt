package com.example.data.audio.loudness

/** What "Volume entre faixas" shows about the playing track. */
data class LoudnessUi(
  val mode: NormalizationMode = NormalizationMode.TRACK,
  val source: LoudnessSource = LoudnessSource.NONE,
  /** Loudness of the playing track in LUFS; null when not known yet. */
  val loudnessLufs: Double? = null,
  /** Gain applied now, in dB (0 or negative). */
  val gainDb: Double = 0.0,
  /** A track is loaded; without one there is nothing to describe. */
  val hasTrack: Boolean = false
)
