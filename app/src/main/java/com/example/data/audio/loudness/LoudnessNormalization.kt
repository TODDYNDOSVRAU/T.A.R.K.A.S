package com.example.data.audio.loudness

import kotlin.math.min
import kotlin.math.pow

/** What the listener chose under Áudio > Ajustes avançados > Volume entre faixas. */
enum class NormalizationMode {
  OFF,
  /** Every track at the same loudness. */
  TRACK,
  /** The album's own value when the file has it, so quiet and loud songs of one album keep their difference. */
  ALBUM
}

/** Where the loudness of the playing track came from. */
enum class LoudnessSource {
  TAG_TRACK,
  TAG_ALBUM,
  /** Measured by the app on an earlier full play. */
  MEASURED,
  /** Nothing known yet: the track plays as it is and is measured now. */
  NONE
}

/** Loudness values a file declares, in LUFS; null when absent. */
data class TaggedLoudness(val trackLufs: Double? = null, val albumLufs: Double? = null) {
  val isEmpty: Boolean get() = trackLufs == null && albumLufs == null
}

/** The gain applied to the playing track, and why. */
data class NormalizationDecision(
  val source: LoudnessSource,
  /** The loudness the decision used; null with [LoudnessSource.NONE]. */
  val loudnessLufs: Double?,
  /** Change in dB, never above 0: it only lowers. */
  val gainDb: Double
) {
  /** Linear factor for the player's volume. */
  val linearGain: Float get() = 10.0.pow(gainDb / 20.0).toFloat()
}

/**
 * Reads loudness tags and decides the gain. ReplayGain and Opus R128 tags store a gain to a reference
 * loudness rather than the loudness itself; both are turned into LUFS so one rule decides the gain.
 */
object LoudnessNormalization {

  /** Where every track is brought to, the level the main streaming services use. */
  const val TARGET_LUFS = -14.0
  /** ReplayGain 2 gains bring a track to this loudness. */
  const val REPLAYGAIN_REFERENCE_LUFS = -18.0
  /** Opus R128 gains are relative to EBU R128's −23 LUFS, in 1/256 dB. */
  const val R128_REFERENCE_LUFS = -23.0
  /** Loudness values outside this range come from a broken tag and are ignored. */
  private val PLAUSIBLE_LUFS = -70.0..5.0

  /**
   * Parses tag pairs (any case; ID3 TXXX descriptions, Vorbis comments and MP4 iTunes atoms all
   * arrive as a key and a text). Unknown keys and unreadable values are skipped.
   */
  fun parseTags(tags: List<Pair<String, String>>): TaggedLoudness {
    var rgTrack: Double? = null
    var rgAlbum: Double? = null
    var r128Track: Double? = null
    var r128Album: Double? = null
    for ((rawKey, value) in tags) {
      when (rawKey.trim().uppercase()) {
        "REPLAYGAIN_TRACK_GAIN" -> rgTrack = rgTrack ?: parseDb(value)
        "REPLAYGAIN_ALBUM_GAIN" -> rgAlbum = rgAlbum ?: parseDb(value)
        "R128_TRACK_GAIN" -> r128Track = r128Track ?: value.trim().toIntOrNull()?.let { it / 256.0 }
        "R128_ALBUM_GAIN" -> r128Album = r128Album ?: value.trim().toIntOrNull()?.let { it / 256.0 }
      }
    }
    fun plausible(lufs: Double?) = lufs?.takeIf { it in PLAUSIBLE_LUFS }
    return TaggedLoudness(
      trackLufs = plausible(rgTrack?.let { REPLAYGAIN_REFERENCE_LUFS - it } ?: r128Track?.let { R128_REFERENCE_LUFS - it }),
      albumLufs = plausible(rgAlbum?.let { REPLAYGAIN_REFERENCE_LUFS - it } ?: r128Album?.let { R128_REFERENCE_LUFS - it })
    )
  }

  /** "−6.20 dB", "-6.2", "+1.50 dB" → dB; null when it is not a number. */
  internal fun parseDb(text: String): Double? =
    text.trim()
      .replace('−', '-')
      .removeSuffix("dB").removeSuffix("db").removeSuffix("DB")
      .trim()
      .removePrefix("+")
      .replace(',', '.')
      .toDoubleOrNull()

  /**
   * Picks the loudness to use and the gain. Tags beat a measurement (they cover the whole file,
   * measured off-line); in [NormalizationMode.ALBUM] the album value wins when there is one.
   */
  fun decide(mode: NormalizationMode, tags: TaggedLoudness, measuredLufs: Double?): NormalizationDecision {
    if (mode == NormalizationMode.OFF) return NormalizationDecision(LoudnessSource.NONE, null, 0.0)
    val (source, lufs) = when {
      mode == NormalizationMode.ALBUM && tags.albumLufs != null -> LoudnessSource.TAG_ALBUM to tags.albumLufs
      tags.trackLufs != null -> LoudnessSource.TAG_TRACK to tags.trackLufs
      measuredLufs != null -> LoudnessSource.MEASURED to measuredLufs
      else -> return NormalizationDecision(LoudnessSource.NONE, null, 0.0)
    }
    return NormalizationDecision(source, lufs, gainFor(lufs))
  }

  /** Only lowers: a quiet track stays as it is, so the output never goes past what the file holds. */
  fun gainFor(loudnessLufs: Double): Double = min(0.0, TARGET_LUFS - loudnessLufs)

  /** A measurement is kept only when it covered most of the track: a skipped track says little. */
  fun isMeasurementComplete(measuredSeconds: Double, durationMs: Long): Boolean =
    durationMs > 0 && measuredSeconds >= MIN_COVERAGE * durationMs / 1000.0

  const val MIN_COVERAGE = 0.9
}
