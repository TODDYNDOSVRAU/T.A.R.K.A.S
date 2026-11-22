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
  val hasTrack: Boolean = false,
  /** Target loudness (-14 Streaming, -16 Apple, -18 EBU/ReplayGain). */
  val targetLufs: Double = LoudnessNormalization.TARGET_LUFS,
  /** Fallback attenuation in dB for tracks not yet measured (0..-6 dB). */
  val fallbackGainDb: Double = 0.0
)
