package com.example

import com.example.data.audio.dsp.BiquadCoefficients
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class EqDspTest {

  private val rate = 48000.0

  private fun design(type: FilterType, f: Double, g: Double, q: Double) =
    BiquadCoefficients.design(type, f, g, q, rate)

  @Test
  fun peakReachesItsGainAtTheCentreAndNothingFarAway() {
    val peak = design(FilterType.PEAK, 1000.0, 6.0, 1.0)
    assertEquals(6.0, peak.magnitudeDb(1000.0, rate), 0.05)
    assertEquals(0.0, peak.magnitudeDb(20.0, rate), 0.1)
    assertEquals(0.0, peak.magnitudeDb(18000.0, rate), 0.3)
  }

  @Test
  fun higherQMakesThePeakNarrower() {
    val wide = design(FilterType.PEAK, 1000.0, 9.0, 0.7)
    val narrow = design(FilterType.PEAK, 1000.0, 9.0, 6.0)
    // One octave above the centre the narrow band has almost let go, the wide one has not.
    assertTrue(narrow.magnitudeDb(2000.0, rate) < 1.5)
    assertTrue(wide.magnitudeDb(2000.0, rate) > 4.0)
  }

  @Test
  fun shelvesLiftOnlyTheirSide() {
    val low = design(FilterType.LOW_SHELF, 150.0, 6.0, 0.707)
    assertEquals(6.0, low.magnitudeDb(20.0, rate), 0.3)
    assertEquals(0.0, low.magnitudeDb(8000.0, rate), 0.1)
    val high = design(FilterType.HIGH_SHELF, 5000.0, -6.0, 0.707)
    assertEquals(-6.0, high.magnitudeDb(18000.0, rate), 0.6)
    assertEquals(0.0, high.magnitudeDb(100.0, rate), 0.1)
  }

  @Test
  fun highPassCutsBelowAndIsMinus3dBAtItsCorner() {
    val hp = design(FilterType.HIGH_PASS, 100.0, 0.0, 0.7071)
    assertEquals(-3.0, hp.magnitudeDb(100.0, rate), 0.1)
    assertTrue(hp.magnitudeDb(25.0, rate) < -20.0)
    assertEquals(0.0, hp.magnitudeDb(2000.0, rate), 0.1)
  }

  @Test
  fun responseAddsPreampAndBandsAndSkipsFlatOnes() {
    val s = EqSettings(preampDb = -3f, autoPreamp = false)
    // All bands at 0 dB: only the preamp remains.
    assertEquals(-3.0, EqDsp.responseDb(s, 1000.0), 0.01)
    assertTrue(EqDsp.activeFilters(s, 48000).isEmpty())
    val boosted = s.copy(bands = s.bands.map { if (it.freqHz == 1000f) it.copy(gainDb = 6f) else it })
    assertEquals(1, EqDsp.activeFilters(boosted, 48000).size)
    assertEquals(3.0, EqDsp.responseDb(boosted, 1000.0), 0.2)
  }

  @Test
  fun onlyTheBandsOfTheModeInUseAreHeard() {
    val graphicBoost = EqSettings.withGains(List(15) { if (it == 8) 6f else 0f }).bands // 1 kHz
    val paramCut = listOf(EqBand(freqHz = 1000f, gainDb = -6f, q = 1f))
    val graphic = EqSettings(bands = graphicBoost, otherBands = paramCut, autoPreamp = false)
    assertTrue(EqDsp.responseDb(graphic, 1000.0) > 3.0)
    val param = graphic.withLayout(true)
    assertEquals(-6.0, EqDsp.responseDb(param, 1000.0), 0.2)
    assertEquals(1, EqDsp.activeFilters(param, 48000).size)
  }

  private fun sine(freq: Double, amp: Float, frames: Int, channels: Int = 2): FloatArray {
    val buf = FloatArray(frames * channels)
    for (i in 0 until frames) {
      val v = (amp * sin(2 * PI * freq * i / rate)).toFloat()
      for (c in 0 until channels) buf[i * channels + c] = v
    }
    return buf
  }

  private fun rms(buf: FloatArray, channel: Int, channels: Int, fromFrame: Int): Double {
    var sum = 0.0
    var n = 0
    for (i in fromFrame until buf.size / channels) {
      val v = buf[i * channels + channel]
      sum += v * v
      n++
    }
    return sqrt(sum / n)
  }

  /** Changes glide in over a few milliseconds; run silence through until they have settled. */
  private fun settle(dsp: EqDsp) = dsp.process(FloatArray(9600), 4800)

  @Test
  fun processingA1kHzToneWithA6dBPeakDoublesIt() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(
      EqSettings(
        bands = listOf(EqBand(freqHz = 1000f, gainDb = 6.02f, q = 1f)),
        limiterEnabled = false,
        autoPreamp = false
      )
    )
    val input = sine(1000.0, 0.1f, 9600)
    val before = rms(input, 0, 2, 4800)
    val out = input.copyOf()
    dsp.process(out, 9600)
    // After the filter settles (second half), the level is twice as high.
    assertEquals(2.0, rms(out, 0, 2, 4800) / before, 0.03)
  }

  @Test
  fun flatSettingsLeaveTheSignalUntouched() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings(limiterEnabled = false))
    val input = sine(440.0, 0.5f, 2000)
    val out = input.copyOf()
    dsp.process(out, 2000)
    assertArrayEquals(input, out, 1e-6f)
  }

  @Test
  fun disabledChainIsABypass() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings(enabled = false, preampDb = 12f, balance = -1f))
    settle(dsp)
    val input = sine(440.0, 0.5f, 1000)
    val out = input.copyOf()
    dsp.process(out, 1000)
    assertArrayEquals(input, out, 0f)
  }

  @Test
  fun limiterNeverLetsASamplePassTheCeiling() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings(preampDb = 12f, limiterEnabled = true, limiterCeilingDb = -1f))
    val out = sine(100.0, 0.9f, 4800)
    dsp.process(out, 4800)
    val ceiling = Math.pow(10.0, -1.0 / 20.0).toFloat()
    var peak = 0f
    out.forEach { peak = max(peak, abs(it)) }
    assertTrue("peak $peak above ceiling $ceiling", peak <= ceiling + 1e-4f)
  }

  @Test
  fun zeroWidthIsMonoAndFullLeftBalanceSilencesTheRight() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings(stereoWidth = 0f, limiterEnabled = false))
    settle(dsp)
    val stereo = FloatArray(200) { i -> if (i % 2 == 0) 0.4f else -0.2f }
    dsp.process(stereo, 100)
    for (i in 0 until 100) assertEquals(stereo[i * 2], stereo[i * 2 + 1], 1e-6f)

    dsp.update(EqSettings(balance = -1f, limiterEnabled = false))
    settle(dsp)
    val buf = sine(440.0, 0.5f, 500)
    dsp.process(buf, 500)
    for (i in 0 until 500) assertEquals(0f, buf[i * 2 + 1], 0f)
  }

  @Test
  fun autoPreampLowersTheLevelByTheLoudestBoost() {
    val boosted = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f, q = 1f)))
    assertEquals(-6.0, EqDsp.autoPreampDb(boosted).toDouble(), 0.1)
    // The curve as heard peaks at 0 dB, so the limiter has nothing to catch.
    assertEquals(0.0, EqDsp.responseDb(boosted, 1000.0), 0.1)
    // Cuts only: nothing to make room for.
    val cut = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = -6f, q = 1f)))
    assertEquals(0f, EqDsp.autoPreampDb(cut), 0f)
    assertEquals(0f, EqDsp.autoPreampDb(boosted.copy(autoPreamp = false)), 0f)
  }

  @Test
  fun movingAFaderThroughZeroDoesNotRestartTheFilters() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    val at = { g: Float -> EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = g, q = 1f)), limiterEnabled = false, autoPreamp = false) }
    dsp.update(at(0.5f))
    val tone = sine(1000.0, 0.3f, 4800 * 3)
    val first = tone.copyOfRange(0, 9600)
    dsp.process(first, 4800)
    dsp.update(at(0f))
    val second = tone.copyOfRange(9600, 19200)
    dsp.process(second, 4800)
    dsp.update(at(-0.5f))
    val third = tone.copyOfRange(19200, 28800)
    dsp.process(third, 4800)
    // No jump at the buffer edges: consecutive samples stay as close as the tone itself allows.
    val out = first + second + third
    val maxStep = (2 * PI * 1000.0 / rate * 0.3 * 1.1).toFloat()
    for (i in 1 until out.size / 2) {
      val step = abs(out[i * 2] - out[(i - 1) * 2])
      assertTrue("jump of $step at frame $i", step <= maxStep)
    }
  }

  @Test
  fun limiterDelaysBySomeMillisecondsAndCatchesThePeakInAdvance() {
    val dsp = EqDsp().apply { configure(48000, 1) }
    dsp.update(EqSettings(limiterEnabled = true, limiterCeilingDb = -6f, autoPreamp = false))
    val buf = FloatArray(4800)
    buf[2000] = 1f
    dsp.process(buf, 4800)
    val ceiling = Math.pow(10.0, -6.0 / 20.0).toFloat()
    val outPeak = buf.indices.maxByOrNull { abs(buf[it]) }!!
    assertTrue("peak came out at $outPeak", outPeak in 2001..2200)
    assertTrue(abs(buf[outPeak]) <= ceiling + 1e-4f)
  }

  @Test
  fun spatialStageLowersTheAutomaticPreampBy3point5dB() {
    val boosted = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f, q = 1f)))
    assertEquals(3.5f, EqDsp.SPATIAL_HEADROOM_DB, 0f)
    // Spatial on: what the curve already asks for, minus 3.5 dB.
    assertEquals(EqDsp.autoPreampDb(boosted) - 3.5f, EqDsp.autoPreampDb(boosted, spatialStage = true), 1e-4f)
    assertEquals(EqDsp.totalPreampDb(boosted) - 3.5f, EqDsp.totalPreampDb(boosted, spatialStage = true), 1e-4f)
    // The manual preamp and the fone's headroom stay in the sum.
    val withManual = boosted.copy(preampDb = -2f)
    assertEquals(EqDsp.totalPreampDb(withManual) - 3.5f, EqDsp.totalPreampDb(withManual, true), 1e-4f)
    // A flat equalizer: the spatial stage alone asks for the 3.5 dB.
    assertEquals(-3.5f, EqDsp.autoPreampDb(EqSettings(), true), 1e-4f)
    // Spatial off: same as before.
    assertEquals(EqDsp.autoPreampDb(boosted), EqDsp.autoPreampDb(boosted, false), 0f)
    assertEquals(EqDsp.totalPreampDb(boosted), EqDsp.totalPreampDb(boosted, false), 0f)
  }

  @Test
  fun manualPreampIsLeftAloneBySpatialStage() {
    val manual = EqSettings(preampDb = -4f, autoPreamp = false)
    assertEquals(0f, EqDsp.autoPreampDb(manual, true), 0f)
    assertEquals(-4f, EqDsp.totalPreampDb(manual, true), 0f)
    assertEquals(EqDsp.totalPreampDb(manual), EqDsp.totalPreampDb(manual, true), 0f)
  }

  @Test
  fun spatialStageChangesTheLevelByGlidingNotByAStep() {
    val dsp = EqDsp().apply { configure(48000, 1) }
    dsp.update(EqSettings(limiterEnabled = false))
    val settle = FloatArray(4800) { 0.5f }
    dsp.process(settle, settle.size)
    assertEquals(0.5f, settle.last(), 1e-3f)
    dsp.setSpatialStage(true)
    val on = FloatArray(9600) { 0.5f }
    dsp.process(on, on.size)
    val target = 0.5f * Math.pow(10.0, -3.5 / 20.0).toFloat()
    assertEquals(target, on.last(), 2e-3f)
    var step = 0f
    for (i in 1 until on.size) step = maxOf(step, abs(on[i] - on[i - 1]))
    assertTrue("largest step between samples $step", step < 2e-3f)
    dsp.setSpatialStage(false)
    val off = FloatArray(9600) { 0.5f }
    dsp.process(off, off.size)
    assertEquals(0.5f, off.last(), 2e-3f)
  }

  @Test
  fun defaultCeilingIs0dBFS() {
    assertEquals(0f, EqSettings().limiterCeilingDb, 0f)
  }

  @Test
  fun defaultSettingsLeaveAFullScaleSignalUntouched() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings())
    // Stereo, peaks at exactly 0 dBFS in both polarities, a tone and a flat-top stretch.
    val frames = 9600
    val input = FloatArray(frames * 2) { i ->
      val n = i / 2
      if (n in 4000..4400) (if ((n / 40) % 2 == 0) 1f else -1f)
      else (sin(2 * Math.PI * 997.0 * n / 48000) * (if (i % 2 == 0) 1.0 else 0.7)).toFloat()
    }
    input[2 * 100] = 1f
    val buf = input.copyOf()
    dsp.process(buf, frames)
    // The limiter delays the sound by its lookahead (a few milliseconds); the content must be the same.
    var bestDelay = -1
    var bestError = Float.MAX_VALUE
    for (delay in 0..300) {
      var worst = 0f
      for (n in 0 until frames - 400) for (c in 0..1) {
        worst = maxOf(worst, abs(buf[(n + delay) * 2 + c] - input[n * 2 + c]))
      }
      if (worst < bestError) { bestError = worst; bestDelay = delay }
    }
    assertTrue("largest difference from the input $bestError at a delay of $bestDelay frames", bestError < 1e-6f)
    assertTrue("delay $bestDelay frames is more than 5 ms", bestDelay in 1..240)
  }

  @Test
  fun limiterStillActsWhenTheEqualizerPushesAbove0dBFS() {
    val dsp = EqDsp().apply { configure(48000, 1) }
    dsp.update(EqSettings(preampDb = 6f, autoPreamp = false))
    val buf = FloatArray(9600) { (sin(2 * Math.PI * 440.0 * it / 48000)).toFloat() }
    dsp.process(buf, buf.size)
    var peak = 0f
    for (v in buf) peak = maxOf(peak, abs(v))
    assertTrue("peak $peak above 0 dBFS", peak <= 1f + 1e-4f)
    assertTrue("the limiter did nothing (peak $peak)", peak > 0.9f)
  }
}
