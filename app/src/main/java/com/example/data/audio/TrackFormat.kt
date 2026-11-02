package com.example.data.audio

/**
 * The playing track's format as the file states it, before Android's mixer, which plays everything at
 * the output's own rate. Shown in the engineering details instead of a fixed text.
 */
data class TrackFormat(
  val codec: String,
  val sampleRateHz: Int,
  val channels: Int,
  /** Bits per sample when the file states them (lossless formats); null for lossy ones. */
  val bits: Int? = null
) {
  /** "FLAC · 96 kHz · 24 bits · estéreo". */
  fun describe(): String = listOfNotNull(
    codec,
    if (sampleRateHz > 0) khz(sampleRateHz) else null,
    bits?.let { "$it bits" },
    when (channels) {
      1 -> "mono"
      2 -> "estéreo"
      in 3..Int.MAX_VALUE -> "$channels canais"
      else -> null
    }
  ).joinToString(" · ")

  companion object {
    fun khz(hz: Int): String =
      if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(java.util.Locale.ROOT, "%.1f kHz", hz / 1000.0)

    /** A short codec name from a MIME type ("audio/flac" → "FLAC"). */
    fun codecName(mime: String?): String = when (mime?.lowercase()) {
      null -> "Áudio"
      "audio/flac" -> "FLAC"
      "audio/mpeg", "audio/mpeg-l2", "audio/mpeg-l1" -> "MP3"
      "audio/mp4a-latm" -> "AAC"
      "audio/opus" -> "Opus"
      "audio/vorbis" -> "Vorbis"
      "audio/alac" -> "ALAC"
      "audio/raw" -> "PCM"
      "audio/ac3" -> "AC-3"
      "audio/eac3" -> "E-AC-3"
      else -> mime.substringAfter('/').uppercase()
    }

    /**
     * Whether the output cannot play [trackRateHz] as it is, given the rates it reports. An output that
     * reports none takes any rate (Android says so), so that is never a warning.
     */
    fun outputBelowTrack(trackRateHz: Int, outputRates: IntArray): Boolean =
      trackRateHz > 0 && outputRates.isNotEmpty() && outputRates.max() < trackRateHz
  }
}
