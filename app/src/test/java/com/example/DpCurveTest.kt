package com.example

import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import com.example.data.systemaudio.DpCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The curve for other apps, checked against the exact curve the way DynamicsProcessing applies it: every
 * FFT bin takes the gain of the band that holds it (AOSP DPFrequency), with bins as wide as the block
 * makes them.
 */
class DpCurveTest {

  private val rate = 48000

  /** What the effect does with a cutoff: its last bin (DPFrequency's rounding). */
  private fun aospStop(cutoffHz: Float, block: Int) = (0.5 + cutoffHz.toDouble() * block / rate).toInt()

  /** The gain DynamicsProcessing applies at [f], for bands ending at [stops] with [gains]. */
  private fun applied(f: Double, block: Int, stops: IntArray, gains: FloatArray): Double =
    DpCurve.gainAtBin((f * block / rate).roundToInt(), stops, gains).toDouble()

  /**
   * Largest difference from the exact curve at the centre of every bin from 30 Hz to 16 kHz: what the
   * layout decides. Between bins the effect cannot follow the curve any closer than its bins allow,
   * whatever the layout (the window spreads a tone between neighbouring bins, which smooths the steps).
   */
  private fun maxErrorAtBins(s: EqSettings, block: Int, stops: IntArray, gains: FloatArray): Double {
    val binHz = rate.toDouble() / block
    var worst = 0.0
    var k = (30 / binHz).roundToInt()
    while (k * binHz <= 16000) {
      worst = max(worst, abs(DpCurve.gainAtBin(k, stops, gains) - EqDsp.responseDb(s, k * binHz, rate)))
      k++
    }
    return worst
  }

  /**
   * Largest difference at any frequency (400 log-spaced points, 30 Hz-16 kHz), taking the nearest bin's
   * gain: a pessimistic view, used to compare with what was sent before.
   */
  private fun maxError(s: EqSettings, block: Int, stops: IntArray, gains: FloatArray): Double {
    var worst = 0.0
    for (i in 0 until 400) {
      val f = 30.0 * (16000.0 / 30.0).pow(i / 399.0)
      worst = max(worst, abs(applied(f, block, stops, gains) - EqDsp.responseDb(s, f, rate)))
    }
    return worst
  }

  /** The new layout and gains for [s]. */
  private fun dense(s: EqSettings, maxBands: Int = 256): Triple<Int, IntArray, FloatArray> {
    val block = DpCurve.blockSize(rate)
    val stops = DpCurve.layout(rate, block, maxBands)
    return Triple(block, stops, DpCurve.bandGains(stops, rate, block) { EqDsp.responseDb(s, it, rate) })
  }

  /** What was sent before: 31 third-octave bands, gain at the centre, at the default 10 ms frame. */
  private fun previous(s: EqSettings): Triple<Int, IntArray, FloatArray> {
    val block = 512
    val centres = doubleArrayOf(
      20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0,
      630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0,
      10000.0, 12500.0, 16000.0, 20000.0
    )
    val stops = IntArray(centres.size) { aospStop((centres[it] * 2.0.pow(1.0 / 6.0)).toFloat(), block) }
    val gains = FloatArray(centres.size) { EqDsp.responseDb(s, centres[it], rate).toFloat() }
    return Triple(block, stops, gains)
  }

  private fun parametric(vararg bands: EqBand) =
    EqSettings(parametric = true, paramBands = bands.toList(), autoPreamp = false, limiterEnabled = false)

  @Test
  fun blockIsAbout85Milliseconds() {
    assertEquals(4096, DpCurve.blockSize(44100))
    assertEquals(4096, DpCurve.blockSize(48000))
    assertEquals(8192, DpCurve.blockSize(96000))
  }

  @Test
  fun layoutCoversEveryBinOnceAndFollowsTheBins() {
    val block = DpCurve.blockSize(rate)
    val stops = DpCurve.layout(rate, block, 256)
    assertTrue("${stops.size} bands", stops.size in 100..256)
    for (i in 1 until stops.size) assertTrue("ascending at $i", stops[i] > stops[i - 1])
    assertEquals("ends at the Nyquist bin", block / 2, stops.last())
    // In the bass every band is a single bin (11.7 Hz wide).
    val binHz = rate.toDouble() / block
    val bassBands = stops.indices.filter { it > 0 && stops[it] * binHz < 150 }
    assertTrue(bassBands.all { stops[it] - stops[it - 1] == 1 })
  }

  @Test
  fun cutoffsLandOnTheirBinInTheEffect() {
    for (r in listOf(44100, 48000, 96000)) {
      val block = DpCurve.blockSize(r)
      val stops = DpCurve.layout(r, block, 256)
      for (s in stops) {
        val cutoff = DpCurve.cutoffHz(s, r, block)
        assertEquals("rate $r, bin $s", s, (0.5 + cutoff.toDouble() * block / r).toInt())
      }
    }
  }

  @Test
  fun fewerBandsWhenTheDeviceTakesFewer() {
    val block = DpCurve.blockSize(rate)
    for (limit in intArrayOf(128, 64, 32, 8)) {
      val stops = DpCurve.layout(rate, block, limit)
      assertTrue("$limit: ${stops.size}", stops.size in 1..limit)
      assertEquals(block / 2, stops.last())
      for (i in 1 until stops.size) assertTrue(stops[i] > stops[i - 1])
    }
  }

  /** A narrow bass peak, the case the 31 bands at 512-sample blocks could not follow. */
  @Test
  fun narrowBassPeakFollowsTheCurve() {
    val s = parametric(EqBand(freqHz = 60f, gainDb = 6f, q = 4f))
    val (nb, ns, ng) = dense(s)
    val (ob, os, og) = previous(s)
    val atBins = maxErrorAtBins(s, nb, ns, ng)
    val now = maxError(s, nb, ns, ng)
    val before = maxError(s, ob, os, og)
    println("60 Hz Q4 +6 dB: at bins %.2f dB; anywhere now %.2f dB, before %.2f dB".format(atBins, now, before))
    assertTrue("at bins $atBins", atBins < 0.05)
    assertTrue("before $before, now $now", now < before)
    // Before, the whole 60 Hz region was one bin, 94 Hz wide: the peak came out as a broad, low hump.
    val (ob2, os2, og2) = previous(s)
    println("gain sent at 60 Hz: now %.2f dB, before %.2f dB".format(applied(60.0, nb, ns, ng), applied(60.0, ob2, os2, og2)))
    assertEquals(6.0, applied(60.0, nb, ns, ng), 1.5)
  }

  @Test
  fun shelvesPassesAndGraphicCurvesStayClose() {
    val cases = mapOf(
      "low shelf 100 Hz +6" to parametric(EqBand(100f, 6f, 0.7f, FilterType.LOW_SHELF)),
      "high-pass 40 Hz" to parametric(EqBand(40f, 0f, 0.7f, FilterType.HIGH_PASS)),
      "high shelf 8 kHz -4" to parametric(EqBand(8000f, -4f, 0.7f, FilterType.HIGH_SHELF)),
      "graphic smile" to EqSettings.withGains(List(15) { if (it < 3 || it > 11) 5f else 0f })
        .copy(autoPreamp = false, limiterEnabled = false),
      "harman-like" to parametric(
        EqBand(105f, 5f, 0.7f, FilterType.LOW_SHELF), EqBand(2800f, 3f, 1.5f), EqBand(6000f, -3f, 4f)
      )
    )
    for ((name, s) in cases) {
      val (nb, ns, ng) = dense(s)
      val (ob, os, og) = previous(s)
      val atBins = maxErrorAtBins(s, nb, ns, ng)
      val now = maxError(s, nb, ns, ng)
      val before = maxError(s, ob, os, og)
      println("$name: at bins %.2f dB; anywhere now %.2f dB, before %.2f dB".format(atBins, now, before))
      assertTrue("$name: at bins $atBins", atBins < 0.5)
      assertTrue("$name: now $now, before $before", now <= before + 0.05)
    }
  }
}
