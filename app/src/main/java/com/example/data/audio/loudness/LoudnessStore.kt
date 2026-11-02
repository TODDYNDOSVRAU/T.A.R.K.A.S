package com.example.data.audio.loudness

import android.content.Context
import android.content.SharedPreferences

/**
 * The normalization mode and the loudness measured for each track, in a preferences file of their own:
 * they belong to the audio engine, not to the screens.
 *
 * Keys are track ids, not paths: an Audius stream URL can change between sessions, its id does not.
 */
class LoudnessStore(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("tarkas_loudness", Context.MODE_PRIVATE)

  var mode: NormalizationMode
    get() = prefs.getString(KEY_MODE, null)
      ?.let { name -> NormalizationMode.entries.firstOrNull { it.name == name } }
      ?: NormalizationMode.TRACK
    set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

  fun measured(trackId: String): Double? =
    prefs.getString(MEASURED_PREFIX + trackId, null)?.toDoubleOrNull()

  fun saveMeasured(trackId: String, lufs: Double) {
    prefs.edit().putString(MEASURED_PREFIX + trackId, "%.2f".format(java.util.Locale.ROOT, lufs)).apply()
  }

  private companion object {
    const val KEY_MODE = "mode"
    /** The "v1" is the measuring method; a change to it would start a new prefix instead of mixing values. */
    const val MEASURED_PREFIX = "measured_v1_"
  }
}
