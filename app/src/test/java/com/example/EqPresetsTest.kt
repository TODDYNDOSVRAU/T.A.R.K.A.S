package com.example

import com.example.data.model.AudioProfile
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqPresetsTest {

  @Test
  fun settingsSurviveAJsonRoundTrip() {
    val s = EqSettings(
      enabled = false,
      bands = listOf(
        EqBand(freqHz = 80f, gainDb = 0f, q = 0.7f, type = FilterType.HIGH_PASS),
        EqBand(freqHz = 3150f, gainDb = -4.5f, q = 5.5f, enabled = false)
      ),
      preampDb = -2.5f,
      bassDb = 4f, bassFreqHz = 120f,
      trebleDb = -3f, trebleFreqHz = 9000f,
      balance = 0.25f, stereoWidth = 1.4f,
      limiterEnabled = false, limiterCeilingDb = -3f,
      virtualizerStrength = 400
    )
    val back = EqPresets.settingsFromJson(JSONObject(EqPresets.settingsToJson(s).toString()))
    assertEquals(s, back)
  }

  @Test
  fun userPresetsRoundTripAndComeBackAsCustom() {
    val presets = listOf(EqPreset("custom_1", "Fone de casa", EqSettings(bassDb = 3f)))
    val back = EqPresets.presetsFromJson(EqPresets.presetsToJson(presets))
    assertEquals(1, back.size)
    assertEquals("Fone de casa", back[0].name)
    assertEquals(3f, back[0].settings.bassDb, 0f)
    assertTrue(back[0].isCustom)
  }

  @Test
  fun brokenJsonGivesNoPresetsInsteadOfCrashing() {
    assertTrue(EqPresets.presetsFromJson("{not json").isEmpty())
    assertTrue(EqPresets.presetsFromJson(null).isEmpty())
  }

  @Test
  fun outOfRangeValuesAreClampedWhenRead() {
    val o = JSONObject().put("preamp", 99.0).put("width", -5.0).put("balance", 3.0)
    val s = EqPresets.settingsFromJson(o)
    assertEquals(EqSettings.PREAMP_RANGE_DB, s.preampDb, 0f)
    assertEquals(0f, s.stereoWidth, 0f)
    assertEquals(1f, s.balance, 0f)
  }

  @Test
  fun oldFifteenBandProfilesConvertToDecibelGains() {
    val p = EqPresets.fromLegacy(AudioProfile.PROG_ROCK)
    assertEquals(15, p.settings.bands.size)
    assertEquals(2.0f, p.settings.bands[0].gainDb, 0.001f) // 200 mB
    assertEquals(4.5f, p.settings.bands[2].gainDb, 0.001f) // 450 mB
    assertEquals(63f, p.settings.bands[2].freqHz, 0f)
    assertEquals(220, p.settings.virtualizerStrength)
  }

  @Test
  fun flatPresetIsZeroEverywhereAndBuiltInIdsAreUnique() {
    val flat = EqPresets.FLAT.settings
    assertTrue(flat.bands.all { it.gainDb == 0f })
    assertEquals(0f, flat.preampDb, 0f)
    val ids = EqPresets.BUILT_IN.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
  }
}
