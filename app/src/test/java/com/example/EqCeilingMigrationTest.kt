package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.data.repository.PreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The default limiter ceiling went from -0.5 to 0 dB; a saved -0.5 moves once, nothing else does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqCeilingMigrationTest {

  private lateinit var context: Context

  private val prefs get() = context.getSharedPreferences("tarkas_prefs", Context.MODE_PRIVATE)

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    prefs.edit().clear().commit()
  }

  private fun fresh() = PreferencesRepository(context)

  /** What the app before the update left on the device (no migration mark). */
  private fun seed(ceiling: Float, presetCeiling: Float? = null) {
    val edit = prefs.edit().clear()
      .putString("eq_settings", EqPresets.settingsToJson(EqSettings(limiterCeilingDb = ceiling)).toString())
    if (presetCeiling != null) {
      val presets = listOf(EqPreset("p1", "Mine", EqSettings(limiterCeilingDb = presetCeiling), isCustom = true))
      edit.putString("eq_user_presets", EqPresets.presetsToJson(presets))
    }
    edit.commit()
  }

  @Test
  fun savedMinusHalfBecomesZeroOnTheFirstLoad() {
    seed(-0.5f)
    assertEquals(0f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
  }

  @Test
  fun theSecondLoadLeavesItAlone() {
    seed(-0.5f)
    fresh().getEqSettings()
    assertEquals(0f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
    assertEquals(0f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
  }

  @Test
  fun minusHalfChosenAfterTheMigrationStays() {
    seed(-0.5f)
    fresh().getEqSettings()
    fresh().saveEqSettings(EqSettings(limiterCeilingDb = -0.5f))
    assertEquals(-0.5f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
    assertEquals(-0.5f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
  }

  @Test
  fun minusHalfSavedBeforeAnyLoadIsAlsoKeptWhenSavingFirst() {
    // First call after the update is a save: the migration runs before it, so the new value is not touched.
    seed(-0.5f)
    fresh().saveEqSettings(EqSettings(limiterCeilingDb = -0.5f))
    assertEquals(-0.5f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
  }

  @Test
  fun otherCeilingsStay() {
    seed(-1f)
    assertEquals(-1f, fresh().getEqSettings()!!.limiterCeilingDb, 0f)
  }

  @Test
  fun nothingSavedGivesZero() {
    assertNull(fresh().getEqSettings())
    assertEquals(0f, EqSettings().limiterCeilingDb, 0f)
    assertEquals(0f, EqPresets.settingsFromJson(org.json.JSONObject("{}")).limiterCeilingDb, 0f)
  }

  @Test
  fun userPresetsAreMigratedToo() {
    seed(-0.5f, presetCeiling = -0.5f)
    assertEquals(0f, fresh().getUserPresets().single().settings.limiterCeilingDb, 0f)
  }

  @Test
  fun aPresetWithAnotherCeilingStays() {
    seed(-0.5f, presetCeiling = -3f)
    val p = fresh()
    assertEquals(0f, p.getEqSettings()!!.limiterCeilingDb, 0f)
    assertEquals(-3f, p.getUserPresets().single().settings.limiterCeilingDb, 0f)
  }

  @Test
  fun aBrokenSettingsStringDoesNotStopTheAppNorGetErased() {
    prefs.edit().putString("eq_settings", "{not json").putString("eq_user_presets", "[]").commit()
    val p = fresh()
    assertNull(p.getEqSettings())
    assertEquals(emptyList<EqPreset>(), p.getUserPresets())
    assertEquals("{not json", prefs.getString("eq_settings", null))
  }
}
