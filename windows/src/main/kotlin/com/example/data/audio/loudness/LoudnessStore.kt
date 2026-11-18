package com.example.data.audio.loudness

/**
 * The normalization mode and the loudness measured for each track (desktop twin of the app's store).
 * Persistence is injected as a getter and a setter, so the Áudio state can back it with the Room `settings`
 * table and tests with a map. Keys are track ids, not paths.
 */
class LoudnessStore(private val get: (String) -> String?, private val put: (String, String) -> Unit) {

  var mode: NormalizationMode
    get() = get(KEY_MODE)?.let { name -> NormalizationMode.entries.firstOrNull { it.name == name } }
      ?: NormalizationMode.AUTO
    set(value) = put(KEY_MODE, value.name)

  fun measured(trackId: String): Double? = get(MEASURED_PREFIX + trackId)?.toDoubleOrNull()

  fun saveMeasured(trackId: String, lufs: Double) {
    put(MEASURED_PREFIX + trackId, "%.2f".format(java.util.Locale.ROOT, lufs))
  }

  private companion object {
    const val KEY_MODE = "loudness_mode"
    const val MEASURED_PREFIX = "measured_v1_"
  }
}
