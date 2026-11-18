package com.example.data.storage

import android.content.SharedPreferences

/**
 * A version for each preferences file, so a change of format is a numbered step that runs once instead
 * of a loose flag. `steps[i]` takes the file from version `i` to `i + 1`; the stored version is the number
 * of steps that ran. A file written by a newer app (version above the steps known here) is left alone.
 */
internal object PrefsVersion {
  const val KEY = "prefs_version"

  fun upgrade(prefs: SharedPreferences, steps: List<(SharedPreferences.Editor) -> Unit>): Int {
    val stored = prefs.getInt(KEY, 0)
    if (stored >= steps.size) return stored
    val edit = prefs.edit()
    for (i in stored until steps.size) steps[i](edit)
    edit.putInt(KEY, steps.size).apply()
    return steps.size
  }
}
