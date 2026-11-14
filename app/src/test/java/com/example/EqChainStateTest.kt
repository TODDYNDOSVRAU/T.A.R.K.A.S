package com.example

import com.example.data.audio.EqChainState
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** E10: pins what the audio engine hands to the equalizer, as it was before the rules left the manager. */
class EqChainStateTest {
  private val curve = EqSettings(
    bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f, q = 1f)),
    preampDb = -3f, bassDb = 2f, trebleDb = 1f, autoPreamp = true,
    balance = 0.3f, limiterEnabled = true, virtualizerStrength = 400
  )

  @Test fun normallyTheSettingsAreAppliedAsTheyAre() {
    assertSame(curve, EqChainState.processed(curve, bypass = false, coveredByOutputMix = false))
  }

  @Test fun theComparisonTurnsTheEqualizerOffAndKeepsTheRest() {
    val p = EqChainState.processed(curve, bypass = true, coveredByOutputMix = false)
    assertFalse(p.enabled)
    assertEquals(curve.copy(enabled = false), p)
  }

  @Test fun theComparisonReferenceIgnoresTheBypass() {
    assertSame(curve, EqChainState.processed(curve, bypass = true, coveredByOutputMix = false, ignoreBypass = true))
  }

  @Test fun theOutputMixEqualizerFlattensTheCurveButKeepsBalanceAndLimiter() {
    val p = EqChainState.processed(curve, bypass = false, coveredByOutputMix = true)
    assertTrue(p.bands.all { it.gainDb == 0f })
    assertEquals(curve.bands.map { it.freqHz }, p.bands.map { it.freqHz })
    assertEquals(0f, p.preampDb, 0f)
    assertEquals(0f, p.bassDb, 0f)
    assertEquals(0f, p.trebleDb, 0f)
    assertFalse(p.autoPreamp)
    assertEquals(null, p.device)
    assertEquals(0.3f, p.balance, 0f)
    assertTrue(p.limiterEnabled)
    assertTrue(p.enabled)
  }

  @Test fun theOutputMixDropsTheFoneCorrectionToo() {
    val s = curve.copy(device = DeviceCorrection(name = "x", source = "x", bands = listOf(EqBand(freqHz = 100f, gainDb = 3f, q = 1f))))
    assertEquals(null, EqChainState.processed(s, bypass = false, coveredByOutputMix = true).device)
  }

  @Test fun anEqualizerThatIsOffIsLeftAloneEvenWhenCovered() {
    val off = curve.copy(enabled = false)
    assertSame(off, EqChainState.processed(off, bypass = false, coveredByOutputMix = true))
  }

  @Test fun theComparisonWinsOverTheOutputMix() {
    val p = EqChainState.processed(curve, bypass = true, coveredByOutputMix = true)
    assertEquals(curve.copy(enabled = false), p)
    val ref = EqChainState.processed(curve, bypass = true, coveredByOutputMix = true, ignoreBypass = true)
    assertTrue(ref.bands.all { it.gainDb == 0f })
  }

  @Test fun theVirtualizerFollowsTheSwitchTheComparisonAndTheStrength() {
    assertTrue(EqChainState.virtualizerOn(curve, bypass = false))
    assertFalse(EqChainState.virtualizerOn(curve, bypass = true))
    assertFalse(EqChainState.virtualizerOn(curve.copy(enabled = false), bypass = false))
    assertFalse(EqChainState.virtualizerOn(curve.copy(virtualizerStrength = 0), bypass = false))
  }
}
