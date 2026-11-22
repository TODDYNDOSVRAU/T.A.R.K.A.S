package com.example.data.audio.dsp

import com.example.data.autoeq.AutoEqParser
import com.example.data.model.BandChannel
import com.example.data.model.CrossfeedPreset
import com.example.data.model.CrossfeedSettings
import com.example.data.model.EqBand
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class Fase1DspTest {

  private val rate = 48000

  @Test
  fun notchFilterAttenuatesCentreAndPassesFarFrequencies() {
    val c = BiquadCoefficients.design(FilterType.NOTCH, 1000.0, 0.0, 4.0, rate.toDouble())
    assertTrue("Expected deep notch at 1 kHz, got ${c.magnitudeDb(1000.0, rate.toDouble())}", c.magnitudeDb(1000.0, rate.toDouble()) < -40.0)
    assertEquals(0.0, c.magnitudeDb(100.0, rate.toDouble()), 0.05)
    assertEquals(0.0, c.magnitudeDb(10000.0, rate.toDouble()), 0.05)
  }

  @Test
  fun bandPassFilterPassesCentreAtZeroDbAndAttenuatesEdges() {
    val c = BiquadCoefficients.design(FilterType.BAND_PASS, 1000.0, 0.0, 2.0, rate.toDouble())
    assertEquals(0.0, c.magnitudeDb(1000.0, rate.toDouble()), 0.05)
    assertTrue(c.magnitudeDb(100.0, rate.toDouble()) < -20.0)
    assertTrue(c.magnitudeDb(10000.0, rate.toDouble()) < -20.0)
  }

  @Test
  fun perBandChannelIsolatesLeftFromRight() {
    val dsp = EqDsp()
    val settings = EqSettings(
      enabled = true,
      parametric = true,
      autoPreamp = false,
      limiterEnabled = false,
      bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f, q = 1.4f, type = FilterType.PEAK, channel = BandChannel.LEFT))
    )
    dsp.update(settings)
    dsp.configure(rate, 2)

    val frames = rate / 2
    val buf = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s = (0.25 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
      buf[i * 2] = s
      buf[i * 2 + 1] = s
    }
    dsp.process(buf, frames)

    val start = frames / 2
    var rmsL = 0.0
    var rmsR = 0.0
    var rmsRef = 0.0
    for (i in start until frames) {
      val ref = 0.25 * sin(2.0 * PI * 1000.0 * i / rate)
      rmsL += buf[i * 2] * buf[i * 2]
      rmsR += buf[i * 2 + 1] * buf[i * 2 + 1]
      rmsRef += ref * ref
    }
    val dbL = 10.0 * log10(rmsL / rmsRef)
    val dbR = 10.0 * log10(rmsR / rmsRef)
    assertEquals("Left channel should get +6 dB", 6.0, dbL, 0.1)
    assertEquals("Right channel should remain 0 dB", 0.0, dbR, 0.02)
  }

  @Test
  fun crossfeedOffIsBitExactPassThrough() {
    val xf = CrossfeedDsp()
    xf.configure(rate)
    xf.update(CrossfeedSettings(CrossfeedPreset.OFF))
    val buf = floatArrayOf(0.5f, -0.25f, 0.125f, 0.75f)
    val copy = buf.copyOf()
    for (f in 0 until 2) xf.processFrame(buf, f * 2)
    for (i in buf.indices) assertEquals(copy[i], buf[i], 0f)
  }

  @Test
  fun crossfeedKeepsMonoFlatAndBlendsLowFrequenciesOnly() {
    val xf = CrossfeedDsp()
    xf.configure(rate)
    xf.update(CrossfeedSettings(CrossfeedPreset.BS2B_DEFAULT))
    xf.reset()

    // 1. Mono 200 Hz stays 0.00 dB
    val frames = rate / 2
    val mono = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s = (0.4 * sin(2.0 * PI * 200.0 * i / rate)).toFloat()
      mono[i * 2] = s
      mono[i * 2 + 1] = s
      xf.processFrame(mono, i * 2)
    }
    var maxErr = 0.0
    for (i in frames / 2 until frames) {
      val ref = 0.4 * sin(2.0 * PI * 200.0 * i / rate)
      maxErr = maxOf(maxErr, abs(mono[i * 2] - ref))
    }
    assertTrue("Mono error should be negligible, was $maxErr", maxErr < 1e-4)

    // 2. Left-only 100 Hz bleeds into Right with ~4.5 dB separation; 10 kHz stays isolated on Left.
    xf.reset()
    val low = FloatArray(frames * 2)
    for (i in 0 until frames) {
      low[i * 2] = (0.5 * sin(2.0 * PI * 100.0 * i / rate)).toFloat()
      low[i * 2 + 1] = 0f
      xf.processFrame(low, i * 2)
    }
    var sumL = 0.0; var sumR = 0.0
    for (i in frames / 2 until frames) {
      sumL += low[i * 2] * low[i * 2]
      sumR += low[i * 2 + 1] * low[i * 2 + 1]
    }
    val sepLowDb = 10.0 * log10(sumL / sumR)
    assertEquals("BS2B low-frequency separation should be ~4.5 dB", 4.5, sepLowDb, 0.5)

    xf.reset()
    val high = FloatArray(frames * 2)
    for (i in 0 until frames) {
      high[i * 2] = (0.5 * sin(2.0 * PI * 10000.0 * i / rate)).toFloat()
      high[i * 2 + 1] = 0f
      xf.processFrame(high, i * 2)
    }
    sumL = 0.0; sumR = 0.0
    for (i in frames / 2 until frames) {
      sumL += high[i * 2] * high[i * 2]
      sumR += high[i * 2 + 1] * high[i * 2 + 1]
    }
    val sepHighDb = 20.0 * log10(sqrt(sumL) / sqrt(sumR).coerceAtLeast(1e-9))
    assertTrue("High frequency should stay isolated (> 20 dB), got $sepHighDb dB", sepHighDb > 20.0)
  }

  @Test
  fun apoImportAndJsonRoundTripPreserveChannelsNotchBandPassAndCrossfeed() {
    val apo = """
      Preamp: -4.2 dB
      Channel: L
      Filter 1: ON NO Fc 3200 Hz Q 6.0
      Channel: R
      Filter 2: ON BP Fc 1200 Hz Q 1.5
      Channel: all
      Filter 3: ON PK Fc 105 Hz Gain 4.0 dB Q 0.70
    """.trimIndent()

    val imported = AutoEqParser.parseApoText(apo, "IEM Teste")
    assertNotNull(imported)
    assertEquals(3, imported!!.bands.size)
    assertEquals(FilterType.NOTCH, imported.bands[0].type)
    assertEquals(BandChannel.LEFT, imported.bands[0].channel)
    assertEquals(FilterType.BAND_PASS, imported.bands[1].type)
    assertEquals(BandChannel.RIGHT, imported.bands[1].channel)
    assertEquals(FilterType.PEAK, imported.bands[2].type)
    assertEquals(BandChannel.STEREO, imported.bands[2].channel)

    val settings = EqSettings(
      parametric = true,
      bands = imported.bands,
      preampDb = imported.preampDb,
      crossfeed = CrossfeedSettings(CrossfeedPreset.CUSTOM, cutoffHz = 850f, feedDb = 7.5f)
    )
    val exported = EqTextFormat.export(settings)
    assertTrue(exported.contains("Channel: L"))
    assertTrue(exported.contains("NO Fc 3200 Hz"))
    assertTrue(exported.contains("Channel: R"))
    assertTrue(exported.contains("BP Fc 1200 Hz"))
    val reparsed = EqTextFormat.parse(exported) as? EqTextFormat.Result.Parametric
    assertNotNull(reparsed)
    assertEquals(3, reparsed!!.bands.size)
    assertEquals(BandChannel.LEFT, reparsed.bands[0].channel)
    assertEquals(FilterType.NOTCH, reparsed.bands[0].type)
    assertEquals(BandChannel.RIGHT, reparsed.bands[1].channel)
    assertEquals(FilterType.BAND_PASS, reparsed.bands[1].type)
    assertEquals(BandChannel.STEREO, reparsed.bands[2].channel)
    assertEquals(850f, settings.crossfeed.effectiveCutoffHz, 0.01f)
    assertEquals(7.5f, settings.crossfeed.effectiveFeedDb, 0.01f)
  }
}
