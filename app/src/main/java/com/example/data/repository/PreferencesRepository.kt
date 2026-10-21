package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AudioProfile
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
    private const val KEY_CURIOSITY_DUCK = "curiosity_duck"
    private const val KEY_CURIOSITY_SHORT = "curiosity_short"
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
    val duck = prefs.getBoolean(KEY_CURIOSITY_DUCK, true)
    val shortOnly = prefs.getBoolean(KEY_CURIOSITY_SHORT, true)

    return com.example.data.model.CuriosityConfig(
      enabled = enabled,
      timing = timing,
      displayMode = displayMode,
      voicePreset = voicePreset,
      pitch = pitch,
      speechRate = rate,
      duckMusic = duck,
      shortSummaryOnly = shortOnly
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
      .putBoolean(KEY_CURIOSITY_DUCK, config.duckMusic)
      .putBoolean(KEY_CURIOSITY_SHORT, config.shortSummaryOnly)
      .apply()
  }

  fun getCustomProfiles(): List<AudioProfile> {
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

  fun saveCustomProfile(profile: AudioProfile) {
    val current = getCustomProfiles().toMutableList()
    current.removeAll { it.id == profile.id }
    current.add(profile)

    val array = JSONArray()
    for (p in current) {
      val obj = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("desc", p.description)
        val bandsArr = JSONArray()
        p.bandLevelsMb.forEach { bandsArr.put(it.toInt()) }
        put("bands", bandsArr)
        put("bass", p.bassBoostStrength.toInt())
        put("virt", p.virtualizerStrength.toInt())
      }
      array.put(obj)
    }
    prefs.edit().putString(KEY_CUSTOM_PROFILES, array.toString()).apply()
  }
}
