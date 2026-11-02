package com.example.data.systemaudio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How the equalizer curve is laid onto Android's DynamicsProcessing for other apps.
 *
 * DynamicsProcessing (the AOSP implementation) equalizes in the frequency domain: each block of audio
 * goes through an FFT, and every FFT bin takes the gain of the band whose range holds it, with no
 * interpolation between bands. Two things follow:
 * - the block size sets the finest detail the curve can have. The default frame (10 ms) gives
 *   512-sample blocks at 48 kHz, bins 94 Hz wide: everything under ~140 Hz becomes one or two steps,
 *   however many bands are sent. [FRAME_MS] asks for 4096-sample blocks (bins of 11.7 Hz at 48 kHz);
 * - bands narrower than a bin do nothing, and a bin should not be split. So bands follow the bins: one
 *   band per bin in the bass, where a bin is wider than a fraction of an octave, then fractions of an
 *   octave above that, as fine as the device's band limit allows.
 *
 * Pure functions, so the result can be checked against the exact curve without a device.
 */
object DpCurve {

  /**
   * The frame asked of DynamicsProcessing. The effect rounds the block up to a power of two: 4096 samples
   * at 44.1 and 48 kHz (85-93 ms), 8192 at 88.2/96 kHz. It is also the delay the effect adds to other
   * apps' sound.
   */
  const val FRAME_MS = 85.34f

  /** Bands per octave tried above the bass, finest first, until the layout fits the device's limit. */
  private val RESOLUTIONS = intArrayOf(24, 12, 6, 3, 2, 1)
  /** The first band ends here: below, bins carry rumble, not music. */
  private const val FIRST_EDGE_HZ = 20.0

  /** The block the effect will use for [sampleRate], as it computes it (rounded up to a power of two). */
  fun blockSize(sampleRate: Int): Int {
    val desired = (FRAME_MS * sampleRate / 1000f).toInt().coerceAtLeast(8)
    return Integer.highestOneBit(desired - 1) shl 1
  }

  /**
   * The last FFT bin of each band, ascending; the last band ends at the Nyquist bin. Uses the finest
   * resolution whose band count is at most [maxBands].
   */
  fun layout(sampleRate: Int, block: Int, maxBands: Int): IntArray {
    for (r in RESOLUTIONS) {
      val stops = layoutAt(sampleRate, block, r)
      if (stops.size <= maxBands) return stops
    }
    // Even an octave per band is too many (a very low limit): spread the bins evenly on a log scale.
    return evenLog(sampleRate, block, maxBands.coerceAtLeast(1))
  }

  private fun layoutAt(sampleRate: Int, block: Int, perOctave: Int): IntArray {
    val binHz = sampleRate.toDouble() / block
    val nyquistBin = block / 2
    val step = 2.0.pow(1.0 / perOctave)
    val stops = ArrayList<Int>()
    var stop = max(0, (FIRST_EDGE_HZ / binHz).toInt())
    stops += stop
    var edge = max(FIRST_EDGE_HZ, (stop + 0.5) * binHz)
    while (stop < nyquistBin) {
      edge *= step
      // Never narrower than one bin; never past the Nyquist bin.
      stop = min(nyquistBin, max(stop + 1, (edge / binHz).toInt()))
      edge = max(edge, (stop + 0.5) * binHz)
      stops += stop
    }
    return stops.toIntArray()
  }

  private fun evenLog(sampleRate: Int, block: Int, bands: Int): IntArray {
    val binHz = sampleRate.toDouble() / block
    val nyquistBin = block / 2
    val lo = FIRST_EDGE_HZ
    val hi = nyquistBin * binHz
    val stops = ArrayList<Int>()
    var prev = -1
    for (i in 1..bands) {
      val f = lo * (hi / lo).pow(i.toDouble() / bands)
      val s = if (i == bands) nyquistBin else min(nyquistBin, max(prev + 1, (f / binHz).toInt()))
      if (s > prev) stops += s
      prev = s
      if (s == nyquistBin) break
    }
    return stops.toIntArray()
  }

  /**
   * The cutoff to send for a band ending at bin [stop]. The effect turns a cutoff back into its last bin
   * by rounding (cutoff × block / rate + 0.5); a quarter of a bin past the bin lands on it exactly,
   * away from the rounding edge.
   */
  fun cutoffHz(stop: Int, sampleRate: Int, block: Int): Float =
    ((stop + 0.25) * sampleRate / block).toFloat()

  /**
   * The gain of each band: the average of the curve (in dB) over the bins it covers, which is the
   * single value closest to the curve across the band. [responseDb] gives the curve at a frequency.
   */
  fun bandGains(stops: IntArray, sampleRate: Int, block: Int, responseDb: (Double) -> Double): FloatArray {
    val binHz = sampleRate.toDouble() / block
    var start = 0
    return FloatArray(stops.size) { i ->
      var sum = 0.0
      for (k in start..stops[i]) sum += responseDb(max(k * binHz, FIRST_EDGE_HZ / 2))
      val gain = sum / (stops[i] - start + 1)
      start = stops[i] + 1
      gain.toFloat()
    }
  }

  /** The gain the effect applies at bin [bin], for checking a layout against the exact curve. */
  fun gainAtBin(bin: Int, stops: IntArray, gains: FloatArray): Float {
    for (i in stops.indices) if (bin <= stops[i]) return gains[i]
    return gains.last()
  }
}
