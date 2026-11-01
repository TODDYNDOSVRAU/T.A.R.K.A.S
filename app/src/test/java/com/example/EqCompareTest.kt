package com.example

import com.example.data.model.EqBand
import com.example.data.model.EqCompare
import com.example.data.model.EqSettings
import com.example.ui.screens.audio.dbToY
import com.example.ui.screens.audio.yToDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a curve still counts as its preset, and the graph's dB axis read back from a touch. */
class EqCompareTest {

  @Test
  fun switchingLayoutWithFlatBandsIsNotAnEdit() {
    val flat = EqSettings()
    assertFalse(EqCompare.edited(flat.copy(parametric = true), flat))
  }

  @Test
  fun aBandThatMovesIsAnEdit() {
    val flat = EqSettings()
    assertTrue(EqCompare.edited(flat.copy(bands = flat.bands.mapIndexed { i, b -> if (i == 5) b.copy(gainDb = 2f) else b }), flat))
    assertTrue(EqCompare.edited(flat.copy(parametric = true, paramBands = listOf(EqBand(freqHz = 1000f, gainDb = 3f))), flat))
  }

  @Test
  fun toneAndPreampCountEvenWithTheSameBands() {
    val flat = EqSettings()
    assertTrue(EqCompare.edited(flat.copy(bassDb = 1f), flat))
    assertTrue(EqCompare.edited(flat.copy(limiterEnabled = false), flat))
  }

  @Test
  fun theFoneCorrectionAndTheSwitchAreNotPartOfThePreset() {
    val flat = EqSettings()
    assertFalse(EqCompare.edited(flat.copy(enabled = false), flat))
  }

  @Test
  fun aTouchHeightReadsBackTheGainDrawnThere() {
    listOf(-15f, -6f, 0f, 3.5f, 15f).forEach { db ->
      assertEquals(db, yToDb(dbToY(db, 600f), 600f), 1e-4f)
    }
  }
}
