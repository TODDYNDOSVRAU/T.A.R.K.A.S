package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AudioProfile
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("tarkas_prefs", Context.MODE_PRIVATE)

  companion object {
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_CUSTOM_PROFILES = "custom_profiles"
    private const val KEY_THEME_MODE = "theme_mode" // "system", "dark", "light"
    private const val KEY_CURIOSITY_ENABLED = "curiosity_enabled"
    private const val KEY_CURIOSITY_TIMING = "curiosity_timing"
    private const val KEY_CURIOSITY_DISPLAY = "curiosity_display"
    private const val KEY_CURIOSITY_VOICE_PRESET = "curiosity_voice_preset"
    private const val KEY_CURIOSITY_PITCH = "curiosity_pitch"
    private const val KEY_CURIOSITY_RATE = "curiosity_rate"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_REPEAT = "repeat_mode"
    private const val KEY_EQ_SETTINGS = "eq_settings"
    private const val KEY_EQ_USER_PRESETS = "eq_user_presets"
  }

  fun getShuffle(): Boolean = prefs.getBoolean(KEY_SHUFFLE, false)

  fun saveShuffle(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply()
  }

  fun getRepeatMode(): com.example.data.model.RepeatMode {
    val name = prefs.getString(KEY_REPEAT, com.example.data.model.RepeatMode.OFF.name)
    return try {
      com.example.data.model.RepeatMode.valueOf(name ?: com.example.data.model.RepeatMode.OFF.name)
    } catch (_: Exception) {
      com.example.data.model.RepeatMode.OFF
    }
  }

  fun saveRepeatMode(mode: com.example.data.model.RepeatMode) {
    prefs.edit().putString(KEY_REPEAT, mode.name).apply()
  }

  fun getSavedProfileId(): String {
    return prefs.getString(KEY_ACTIVE_PROFILE_ID, AudioProfile.FLAT.id) ?: AudioProfile.FLAT.id
  }

  fun saveActiveProfileId(profileId: String) {
    prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
  }

  fun getThemeMode(): String {
    return prefs.getString(KEY_THEME_MODE, "system") ?: "system"
  }

  fun saveThemeMode(mode: String) {
    prefs.edit().putString(KEY_THEME_MODE, mode).apply()
  }

  /** "solid", "frosted" or "liquid" */
  fun getSurfaceStyle(): String {
    return prefs.getString("surface_style", "solid") ?: "solid"
  }

  fun saveSurfaceStyle(key: String) {
    prefs.edit().putString("surface_style", key).apply()
  }

  /** "Reduzir animações": off by default, so the app animates even when the system scale is zero. */
  fun getReduceMotion(): Boolean = prefs.getBoolean("reduce_motion", false)

  fun saveReduceMotion(reduce: Boolean) {
    prefs.edit().putBoolean("reduce_motion", reduce).apply()
  }

  /** "Reduzir transparência": glass surfaces drawn solid. */
  fun getReduceTransparency(): Boolean = prefs.getBoolean("reduce_transparency", false)

  fun saveReduceTransparency(reduce: Boolean) {
    prefs.edit().putBoolean("reduce_transparency", reduce).apply()
  }

  /** Accent colour key (see AccentColor); "ice" is the brand default. */
  fun getAccent(): String = prefs.getString("accent", "ice") ?: "ice"

  fun saveAccent(key: String) {
    prefs.edit().putString("accent", key).apply()
  }

  /** First-run setup finished, and which version of the terms the user accepted, and when. */
  fun getOnboardingDone(): Boolean = prefs.getBoolean("onboarding_done", false)

  fun saveOnboardingDone(done: Boolean) {
    prefs.edit().putBoolean("onboarding_done", done).apply()
  }

  fun getTermsVersionAccepted(): Int = prefs.getInt("terms_version_accepted", 0)

  fun getTermsAcceptedAt(): Long = prefs.getLong("terms_accepted_at", 0L)

  fun saveTermsAccepted(version: Int, atMillis: Long) {
    prefs.edit().putInt("terms_version_accepted", version).putLong("terms_accepted_at", atMillis).apply()
  }

  /** Every preference this app keeps, for "Exportar meus dados". */
  fun exportAll(): Map<String, *> = prefs.all

  /** Erases every preference (LGPD: elimination of the user's data). */
  fun clearAll() {
    prefs.edit().clear().apply()
  }

  /** "app", "adb" or "root": how far the equalizer reaches. */
  fun getEqReach(): String = prefs.getString("eq_reach", "app") ?: "app"

  fun saveEqReach(key: String) {
    prefs.edit().putString("eq_reach", key).apply()
  }

  fun getCuriosityConfig(): com.example.data.model.CuriosityConfig {
    val enabled = prefs.getBoolean(KEY_CURIOSITY_ENABLED, true)
    val timingName = prefs.getString(KEY_CURIOSITY_TIMING, com.example.data.model.CuriosityTiming.END_OF_TRACK.name)
    val timing = try {
      com.example.data.model.CuriosityTiming.valueOf(timingName ?: com.example.data.model.CuriosityTiming.END_OF_TRACK.name)
    } catch (_: Exception) {
      com.example.data.model.CuriosityTiming.END_OF_TRACK
    }

    val displayName = prefs.getString(KEY_CURIOSITY_DISPLAY, com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER.name)
    val displayMode = try {
      com.example.data.model.CuriosityDisplayMode.valueOf(displayName ?: com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER.name)
    } catch (_: Exception) {
      com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER
    }

    val presetName = prefs.getString(KEY_CURIOSITY_VOICE_PRESET, com.example.data.model.VoicePreset.NATURAL.name)
    val voicePreset = try {
      com.example.data.model.VoicePreset.valueOf(presetName ?: com.example.data.model.VoicePreset.NATURAL.name)
    } catch (_: Exception) {
      com.example.data.model.VoicePreset.NATURAL
    }

    val pitch = prefs.getFloat(KEY_CURIOSITY_PITCH, voicePreset.pitch)
    val rate = prefs.getFloat(KEY_CURIOSITY_RATE, voicePreset.speechRate)

    return com.example.data.model.CuriosityConfig(
      enabled = enabled,
      timing = timing,
      displayMode = displayMode,
      voicePreset = voicePreset,
      pitch = pitch,
      speechRate = rate
    )
  }

  fun saveCuriosityConfig(config: com.example.data.model.CuriosityConfig) {
    prefs.edit()
      .putBoolean(KEY_CURIOSITY_ENABLED, config.enabled)
      .putString(KEY_CURIOSITY_TIMING, config.timing.name)
      .putString(KEY_CURIOSITY_DISPLAY, config.displayMode.name)
      .putString(KEY_CURIOSITY_VOICE_PRESET, config.voicePreset.name)
      .putFloat(KEY_CURIOSITY_PITCH, config.pitch)
      .putFloat(KEY_CURIOSITY_RATE, config.speechRate)
      // Switches of earlier versions that never did anything.
      .remove("curiosity_duck")
      .remove("curiosity_short")
      .apply()
  }

  /** Whether the app already explained and asked for the notification permission once. */
  fun getNotificationsAsked(): Boolean = prefs.getBoolean("notifications_asked", false)

  fun saveNotificationsAsked() {
    prefs.edit().putBoolean("notifications_asked", true).apply()
  }

  // ---- Equalizer ----

  /** The last equalizer state, or null on first launch. */
  fun getEqSettings(): EqSettings? {
    val raw = prefs.getString(KEY_EQ_SETTINGS, null) ?: return null
    return try {
      EqPresets.settingsFromJson(JSONObject(raw))
    } catch (_: Exception) {
      null
    }
  }

  fun saveEqSettings(settings: EqSettings) {
    prefs.edit().putString(KEY_EQ_SETTINGS, EqPresets.settingsToJson(settings).toString()).apply()
  }

  /** Presets the user saved. On the first run after the update, old 15-band profiles are converted. */
  fun getUserPresets(): List<EqPreset> {
    val raw = prefs.getString(KEY_EQ_USER_PRESETS, null)
    if (raw != null) return EqPresets.presetsFromJson(raw)
    val migrated = getLegacyCustomProfiles().map { EqPresets.fromLegacy(it).copy(isCustom = true) }
    saveUserPresets(migrated)
    return migrated
  }

  fun saveUserPresets(presets: List<EqPreset>) {
    prefs.edit().putString(KEY_EQ_USER_PRESETS, EqPresets.presetsToJson(presets)).apply()
  }

  /** Profiles saved by the old hardware equalizer, read only to convert them. */
  private fun getLegacyCustomProfiles(): List<AudioProfile> {
    val raw = prefs.getString(KEY_CUSTOM_PROFILES, null) ?: return emptyList()
    val result = mutableListOf<AudioProfile>()
    try {
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val bandsJson = obj.getJSONArray("bands")
        val bands = mutableListOf<Short>()
        for (b in 0 until bandsJson.length()) {
          bands.add(bandsJson.getInt(b).toShort())
        }
        result.add(
          AudioProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            description = obj.optString("desc", "Perfil customizado pelo usuário"),
            bandLevelsMb = bands,
            bassBoostStrength = obj.optInt("bass", 0).toShort(),
            virtualizerStrength = obj.optInt("virt", 0).toShort(),
            isCustom = true
          )
        )
      }
    } catch (_: Exception) {}
    return result
  }
}