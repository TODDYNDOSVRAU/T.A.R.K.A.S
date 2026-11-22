package com.example.data.audio.loudness

import android.content.Context
import android.content.SharedPreferences
import com.example.data.storage.PrefsVersion

/**
 * The normalization mode and the loudness measured for each track, in a preferences file of their own:
 * they belong to the audio engine, not to the screens.
 *
 * Keys are track ids, not paths: an Audius stream URL can change between sessions, its id does not.
 */
class LoudnessStore(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("tarkas_loudness", Context.MODE_PRIVATE)
      .also { PrefsVersion.upgrade(it, listOf({ _ -> })) }

  var mode: NormalizationMode
    get() = prefs.getString(KEY_MODE, null)
      ?.let { name -> NormalizationMode.entries.firstOrNull { it.name == name } }
      // Automatic is per track except for an album played in order, so it suits anyone who never chose.
      ?: NormalizationMode.AUTO
    set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

  var targetLufs: Double
    get() = prefs.getFloat(KEY_TARGET_LUFS, LoudnessNormalization.TARGET_LUFS.toFloat()).toDouble().coerceIn(-24.0, -9.0)
    set(value) = prefs.edit().putFloat(KEY_TARGET_LUFS, value.coerceIn(-24.0, -9.0).toFloat()).apply()

  var fallbackGainDb: Double
    get() = prefs.getFloat(KEY_FALLBACK_DB, 0f).toDouble().coerceIn(-12.0, 0.0)
    set(value) = prefs.edit().putFloat(KEY_FALLBACK_DB, value.coerceIn(-12.0, 0.0).toFloat()).apply()

  fun measured(trackId: String): Double? =
    prefs.getString(MEASURED_PREFIX + trackId, null)?.toDoubleOrNull()

  fun saveMeasured(trackId: String, lufs: Double) {
    prefs.edit().putString(MEASURED_PREFIX + trackId, "%.2f".format(java.util.Locale.ROOT, lufs)).apply()
  }

  private companion object {
    const val KEY_MODE = "mode"
    const val KEY_TARGET_LUFS = "target_lufs"
    const val KEY_FALLBACK_DB = "fallback_db"
    /** The "v1" is the measuring method; a change to it would start a new prefix instead of mixing values. */
    const val MEASURED_PREFIX = "measured_v1_"
  }
}
