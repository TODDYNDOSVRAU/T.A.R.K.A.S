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
    val s = EqSettings(preampDb = -3f)
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
    val graphic = EqSettings(bands = graphicBoost, paramBands = paramCut)
    assertTrue(EqDsp.responseDb(graphic, 1000.0) > 3.0)
    val param = graphic.copy(parametric = true)
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

  @Test
  fun processingA1kHzToneWithA6dBPeakDoublesIt() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(
      EqSettings(
        bands = listOf(EqBand(freqHz = 1000f, gainDb = 6.02f, q = 1f)),
        limiterEnabled = false
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
    dsp.update(EqSettings())
    val input = sine(440.0, 0.5f, 2000)
    val out = input.copyOf()
    dsp.process(out, 2000)
    assertArrayEquals(input, out, 1e-6f)
  }

  @Test
  fun disabledChainIsABypass() {
    val dsp = EqDsp().apply { configure(48000, 2) }
    dsp.update(EqSettings(enabled = false, preampDb = 12f, balance = -1f))
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
    val stereo = FloatArray(200) { i -> if (i % 2 == 0) 0.4f else -0.2f }
    dsp.process(stereo, 100)
    for (i in 0 until 100) assertEquals(stereo[i * 2], stereo[i * 2 + 1], 1e-6f)

    dsp.update(EqSettings(balance = -1f, limiterEnabled = false))
    val buf = sine(440.0, 0.5f, 500)
    dsp.process(buf, 500)
    for (i in 0 until 500) assertEquals(0f, buf[i * 2 + 1], 0f)
  }
}
