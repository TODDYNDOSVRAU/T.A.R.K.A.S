package com.example.data.update

import android.content.Context

/** What the updater remembers between runs: the two switches, the last check and the release waiting. */
class UpdateStore(context: Context) {
  private val prefs = context.getSharedPreferences("tarkas_update", Context.MODE_PRIVATE)

  /** Automatic checks (periodic and on opening the app). Manual checks always work. */
  var autoEnabled: Boolean
    get() = prefs.getBoolean("auto", true)
    set(v) = prefs.edit().putBoolean("auto", v).apply()

  /** Downloads wait for an unmetered network (Wi-Fi) unless the person taps to download now. */
  var wifiOnly: Boolean
    get() = prefs.getBoolean("wifi_only", true)
    set(v) = prefs.edit().putBoolean("wifi_only", v).apply()

  var lastCheckedAt: Long
    get() = prefs.getLong("last_checked", 0L)
    set(v) = prefs.edit().putLong("last_checked", v).apply()

  /** The newest release seen, as the answer text it came from, so it survives a restart. */
  var lastReleaseJson: String?
    get() = prefs.getString("release_json", null)
    set(v) = prefs.edit().putString("release_json", v).apply()

  /** Tag of the APK that finished downloading and passed every check, or null. */
  var verifiedTag: String?
    get() = prefs.getString("verified_tag", null)
    set(v) = prefs.edit().putString("verified_tag", v).apply()
}
