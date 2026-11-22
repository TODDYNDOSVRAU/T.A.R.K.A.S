package com.example.data.audio.dsp

import com.example.data.model.BandChannel
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterSlope
import com.example.data.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class PeqEntrega1Test {

  private val rate = 48000

  @Test
  fun tiltShelfPivotsAtZeroDbAndTiltsSymmetrically() {
    val c = BiquadCoefficients.design(FilterType.TILT_SHELF, 1000.0, 6.0, 0.707, rate.toDouble())
    assertEquals("Pivot at 1 kHz should be 0 dB", 0.0, c.magnitudeDb(1000.0, rate.toDouble()), 0.05)
    assertEquals("Low end should be -3 dB", -3.0, c.magnitudeDb(30.0, rate.toDouble()), 0.1)
    assertEquals("High end should be +3 dB", 3.0, c.magnitudeDb(15000.0, rate.toDouble()), 0.2)
  }

  @Test
  fun butterworthSlopesHaveExactCutoffAndIncreasingSteepness() {
    val hp24 = BiquadCoefficients.designSlope(FilterType.HIGH_PASS, 1000.0, 0.707, FilterSlope.DB_24, rate.toDouble())
    val hp48 = BiquadCoefficients.designSlope(FilterType.HIGH_PASS, 1000.0, 0.707, FilterSlope.DB_48, rate.toDouble())
    val hp6 = BiquadCoefficients.designSlope(FilterType.HIGH_PASS, 1000.0, 0.707, FilterSlope.DB_6, rate.toDouble())

    fun totalDb(list: List<BiquadCoefficients>, f: Double): Double = list.sumOf { it.magnitudeDb(f, rate.toDouble()) }

    assertEquals(-3.01, totalDb(hp6, 1000.0), 0.1)
    assertEquals(-3.01, totalDb(hp24, 1000.0), 0.1)
    assertEquals(-3.01, totalDb(hp48, 1000.0), 0.1)

    val db24OctBelow = totalDb(hp24, 500.0)
    val db48OctBelow = totalDb(hp48, 500.0)
    assertTrue("24 dB/oct at 500 Hz should be around -24 dB, got $db24OctBelow", db24OctBelow in -26.0..-22.0)
    assertTrue("48 dB/oct at 500 Hz should be around -48 dB, got $db48OctBelow", db48OctBelow in -51.0..-45.0)
  }

  @Test
  fun midSideBandRoutesOnlyMidOrOnlySide() {
    val dsp = EqDsp()
    val settings = EqSettings(
      enabled = true,
      parametric = true,
      autoPreamp = false,
      limiterEnabled = false,
      bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f, q = 1.4f, type = FilterType.PEAK, channel = BandChannel.MID))
    )
    dsp.update(settings)
    dsp.configure(rate, 2)

    val frames = rate / 2
    // Pure Mid (L == R): should get +6 dB
    val mono = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s = (0.2 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
      mono[i * 2] = s; mono[i * 2 + 1] = s
    }
    dsp.process(mono, frames)
    var pOut = 0.0; var pRef = 0.0
    for (i in frames / 2 until frames) {
      val ref = 0.2 * sin(2.0 * PI * 1000.0 * i / rate)
      pOut += mono[i * 2] * mono[i * 2]; pRef += ref * ref
    }
    assertEquals("Mid band should boost mono by +6 dB", 6.0, 10.0 * log10(pOut / pRef), 0.15)

    // Pure Side (L == -R): should stay 0 dB
    dsp.reset()
    val side = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s = (0.2 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
      side[i * 2] = s; side[i * 2 + 1] = -s
    }
    dsp.process(side, frames)
    pOut = 0.0; pRef = 0.0
    for (i in frames / 2 until frames) {
      val ref = 0.2 * sin(2.0 * PI * 1000.0 * i / rate)
      pOut += side[i * 2] * side[i * 2]; pRef += ref * ref
    }
    assertEquals("Mid band must leave pure Side untouched", 0.0, 10.0 * log10(pOut / pRef), 0.02)
  }

  @Test
  fun soloBandIsolatesTargetFrequency() {
    val dsp = EqDsp()
    dsp.update(EqSettings(enabled = true, autoPreamp = false, limiterEnabled = false))
    dsp.configure(rate, 2)
    dsp.setSoloBand(EqBand(freqHz = 1000f, q = 3.0f))

    val frames = rate / 2
    val buf1k = FloatArray(frames * 2)
    val buf150 = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val s1 = (0.3 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
      val s2 = (0.3 * sin(2.0 * PI * 150.0 * i / rate)).toFloat()
      buf1k[i * 2] = s1; buf1k[i * 2 + 1] = s1
      buf150[i * 2] = s2; buf150[i * 2 + 1] = s2
    }
    dsp.process(buf1k, frames)
    dsp.process(buf150, frames)
    var p1k = 0.0; var p150 = 0.0
    for (i in frames / 2 until frames) {
      p1k += buf1k[i * 2] * buf1k[i * 2]
      p150 += buf150[i * 2] * buf150[i * 2]
    }
    val sepDb = 10.0 * log10(p1k / p150.coerceAtLeast(1e-12))
    assertTrue("Solo at 1 kHz should strongly reject 150 Hz (> 15 dB), got $sepDb dB", sepDb > 15.0)
  }

  @Test
  fun internalRtaDetectsPeakAt1kHzWithoutAndroidVisualizer() {
    val rta = RtaAnalyzer()
    rta.sampleRate = rate
    rta.enabled = true
    for (i in 0 until 2048) {
      val s = (0.5 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
      rta.pushPre(s)
      rta.pushPostAndAdvance(s)
    }
    val frame = rta.computeFrame(needPre = true, needPost = true)
    assertNotNull(frame)
    assertEquals(RtaFrame.BANDS, frame!!.post.size)
    val peakBand = frame.post.indices.maxBy { frame.post[it] }
    // log(1000/20) / log(20000/20) * 64 = 0.566 * 64 ≈ band 36
    assertTrue("Expected 1 kHz peak around band 36, got band $peakBand", peakBand in 34..38)
    assertTrue("Peak band energy should be strong, got ${frame.post[peakBand]}", frame.post[peakBand] > 0.45f)
    assertTrue("Dominant peakHz should be near 1000 Hz, got ${frame.peakHz}", frame.peakHz in 900f..1120f)
  }

  @Test
  fun loudnessNormalizationSupportsCustomTargetLufsAndFallbackGain() {
    val tags = com.example.data.audio.loudness.TaggedLoudness(trackLufs = -10.0, albumLufs = -12.0)
    val d16 = com.example.data.audio.loudness.LoudnessNormalization.decide(
      mode = com.example.data.audio.loudness.NormalizationMode.TRACK,
      tags = tags,
      measuredLufs = null,
      targetLufs = -16.0
    )
    assertEquals(-6.0, d16.gainDb, 0.01)

    val fallback = com.example.data.audio.loudness.LoudnessNormalization.decide(
      mode = com.example.data.audio.loudness.NormalizationMode.TRACK,
      tags = com.example.data.audio.loudness.TaggedLoudness(),
      measuredLufs = null,
      targetLufs = -14.0,
      fallbackGainDb = -3.0
    )
    assertEquals(-3.0, fallback.gainDb, 0.01)
  }
}
