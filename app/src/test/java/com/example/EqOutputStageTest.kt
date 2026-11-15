package com.example

import com.example.data.audio.dsp.EqActivity
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.LimiterActivity
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** E3 (what the limiter and the clamp did) and E2 (the untouched signal plays at the average volume of the curve). */
class EqOutputStageTest {
  private val rate = 48000

  private fun sine(freq: Double, amp: Float, frames: Int, channels: Int = 2): FloatArray {
    val buf = FloatArray(frames * channels)
    for (i in 0 until frames) {
      val v = (amp * sin(2 * PI * freq * i / rate)).toFloat()
      for (c in 0 until channels) buf[i * channels + c] = v
    }
    return buf
  }

  private fun rms(buf: FloatArray, channels: Int, fromFrame: Int): Double {
    var sum = 0.0
    var n = 0
    for (i in fromFrame until buf.size / channels) {
      val v = buf[i * channels]
      sum += v * v
      n++
    }
    return sqrt(sum / n)
  }

  private fun dsp(s: EqSettings) = EqDsp().apply { configure(rate, 2); update(s) }

  // ---- E3 ----

  @Test
  fun quietMusicShowsNoLimiterAndNoClip() {
    val d = dsp(EqSettings(limiterEnabled = true))
    d.process(sine(440.0, 0.3f, 9600), 9600)
    val a = d.takeActivity()
    assertFalse(a.limiting)
    assertFalse(a.clipped)
    assertEquals(0f, a.reductionDb, 0f)
  }

  @Test
  fun aBoostPastTheCeilingShowsTheLimiterAndItsDepth() {
    val d = dsp(EqSettings(preampDb = 6f, autoPreamp = false, limiterEnabled = true))
    d.process(sine(440.0, 1f, 9600), 9600)
    val a = d.takeActivity()
    assertTrue("limiter not shown: ${a.reductionDb} dB", a.limiting)
    // A +6 dB full-scale sine needs about 6 dB of reduction to fit under 0 dBFS.
    assertEquals(6.0, a.reductionDb.toDouble(), 0.6)
    assertFalse("a working limiter must not clip", a.clipped)
  }

  @Test
  fun withoutTheLimiterTheBoostClipsAndTheClipsAreCounted() {
    val d = dsp(EqSettings(preampDb = 6f, autoPreamp = false, limiterEnabled = false))
    val buf = sine(440.0, 1f, 9600)
    d.process(buf, 9600)
    val a = d.takeActivity()
    assertTrue(a.clipped)
    assertTrue("only ${a.clippedSamples} clipped samples", a.clippedSamples > 1000)
    assertFalse(a.limiting)
    // The sink still gets only legal samples.
    assertTrue(buf.all { abs(it) <= 1f })
  }

  @Test
  fun takingTheActivityStartsOver() {
    val d = dsp(EqSettings(preampDb = 6f, autoPreamp = false, limiterEnabled = false))
    d.process(sine(440.0, 1f, 4800), 4800)
    assertTrue(d.takeActivity().clipped)
    d.process(sine(440.0, 0.1f, 4800), 4800)
    assertFalse(d.takeActivity().clipped)
  }

  @Test
  fun theDeepestReductionOfSeveralBuffersIsKept() {
    val d = dsp(EqSettings(preampDb = 6f, autoPreamp = false, limiterEnabled = true))
    d.process(sine(440.0, 1f, 2400), 2400)
    d.process(sine(440.0, 0.05f, 480), 480)
    assertTrue(d.takeActivity().reductionDb > 3f)
  }

  @Test
  fun reductionInDbFollowsTheGain() {
    assertEquals(0f, EqDsp.gainToReductionDb(1f), 0f)
    assertEquals(6.02f, EqDsp.gainToReductionDb(0.5f), 0.01f)
  }

  @Test
  fun theScreenKeepsWhatItSawForTheHoldThenLetsGo() {
    EqActivity.clear()
    EqActivity.publish(LimiterActivity(reductionDb = 3f, clippedSamples = 0), nowMs = 1000)
    EqActivity.publish(LimiterActivity(), nowMs = 1100)
    assertTrue("must still show inside the hold", EqActivity.state.value.limiting)
    EqActivity.publish(LimiterActivity(), nowMs = 1000 + EqActivity.HOLD_MS)
    assertFalse("must clear after the hold", EqActivity.state.value.limiting)
  }

  @Test
  fun theHoldKeepsTheDeepestDipSeen() {
    EqActivity.clear()
    EqActivity.publish(LimiterActivity(6f, 0), nowMs = 0)
    EqActivity.publish(LimiterActivity(2f, 0), nowMs = 100)
    assertEquals(6f, EqActivity.state.value.reductionDb, 0f)
    EqActivity.clear()
    assertFalse(EqActivity.state.value.limiting)
  }

  // ---- E2 ----

  @Test
  fun aFlatCurveNeedsNoVolumeCorrection() {
    assertEquals(0f, EqDsp.loudnessMatchDb(EqSettings()), 0.01f)
  }

  @Test
  fun aWholeCurveLoweredBy4dBIsMatchedBy4dB() {
    // Flat bands with only the manual preamp: the average level is exactly the preamp.
    val s = EqSettings(preampDb = -4f, autoPreamp = false)
    assertEquals(-4f, EqDsp.loudnessMatchDb(s), 0.05f)
  }

  @Test
  fun aNarrowBoostCountsLessThanAWideBoost() {
    val narrow = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 12f, q = 8f)), autoPreamp = false)
    val wide = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 12f, q = 0.4f)), autoPreamp = false)
    val n = EqDsp.loudnessMatchDb(narrow)
    val w = EqDsp.loudnessMatchDb(wide)
    assertTrue("narrow $n dB should be small", n in 0f..3f)
    assertTrue("wide $w dB should be larger than narrow $n", w > n + 1f)
  }

  @Test
  fun theAutomaticPreampMakesTheMatchNegativeForABoostingCurve() {
    val s = EqSettings(bands = listOf(EqBand(freqHz = 100f, gainDb = 9f, q = 1f)), autoPreamp = true)
    assertTrue(EqDsp.loudnessMatchDb(s) < 0f)
  }

  @Test
  fun theCorrectionIsLimitedSoAWildCurveCannotBlastTheUntouchedSignal() {
    val s = EqSettings(preampDb = 30f, autoPreamp = false)
    assertEquals(EqDsp.MATCH_LIMIT_DB, EqDsp.loudnessMatchDb(s), 0f)
  }

  @Test
  fun comparingPlaysTheUntouchedSignalAtTheAverageVolumeOfTheCurve() {
    val on = EqSettings(preampDb = -6f, autoPreamp = false, limiterEnabled = false)
    val d = EqDsp().apply { configure(rate, 2); update(on) }
    d.setCompareReference(on)
    d.update(on.copy(enabled = false))
    d.process(FloatArray(19200), 9600) // let mix and gain glide
    val input = sine(1000.0, 0.4f, 9600)
    val out = input.copyOf()
    d.process(out, 9600)
    val db = 20 * log10(rms(out, 2, 4800) / rms(input, 2, 4800))
    assertEquals(-6.0, db, 0.1)
  }

  @Test
  fun endingTheComparisonReturnsTheUntouchedSignalToUnity() {
    val on = EqSettings(preampDb = -6f, autoPreamp = false, limiterEnabled = false)
    val d = EqDsp().apply { configure(rate, 2); update(on) }
    d.setCompareReference(on)
    d.update(on.copy(enabled = false))
    d.process(FloatArray(19200), 9600)
    d.setCompareReference(null)
    d.update(on.copy(enabled = false))
    d.process(FloatArray(19200), 9600)
    val input = sine(1000.0, 0.4f, 2000)
    val out = input.copyOf()
    d.process(out, 2000)
    assertEquals(0.0, out.indices.maxOf { abs((out[it] - input[it]).toDouble()) }, 1e-4)
  }

  @Test
  fun theGainGlidesSoStartingTheComparisonDoesNotStepTheLevel() {
    val on = EqSettings(preampDb = -12f, autoPreamp = false, limiterEnabled = false)
    val d = EqDsp().apply { configure(rate, 1); update(on.copy(enabled = false)) }
    d.process(FloatArray(4800), 4800)
    d.setCompareReference(on)
    d.update(on.copy(enabled = false))
    val buf = FloatArray(4800) { 0.5f }
    d.process(buf, 4800)
    var biggestStep = 0f
    for (i in 1 until buf.size) biggestStep = maxOf(biggestStep, abs(buf[i] - buf[i - 1]))
    assertTrue("level stepped by $biggestStep", biggestStep < 0.01f)
    assertTrue("did not arrive", abs(buf.last() - 0.5f * 0.2512f) < 0.01f)
  }
}
