package com.example.data.audio.dsp

import com.example.data.model.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalised biquad coefficients (a0 = 1), from Robert Bristow-Johnson's "Audio EQ Cookbook".
 * Immutable, so the audio thread can swap a whole set at once.
 */
class BiquadCoefficients(
  val b0: Double,
  val b1: Double,
  val b2: Double,
  val a1: Double,
  val a2: Double
) {
  /** Gain of this filter at [freqHz], in dB. Used to draw the real response curve. */
  fun magnitudeDb(freqHz: Double, sampleRate: Double): Double {
    val w = 2.0 * PI * freqHz / sampleRate
    val cos1 = cos(w)
    val sin1 = sin(w)
    val cos2 = cos(2 * w)
    val sin2 = sin(2 * w)
    val numRe = b0 + b1 * cos1 + b2 * cos2
    val numIm = -(b1 * sin1 + b2 * sin2)
    val denRe = 1.0 + a1 * cos1 + a2 * cos2
    val denIm = -(a1 * sin1 + a2 * sin2)
    val num = numRe * numRe + numIm * numIm
    val den = denRe * denRe + denIm * denIm
    return 10.0 * log10((num / den).coerceAtLeast(1e-20))
  }

  companion object {
    val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)

    /**
     * Designs one filter. [q] is the quality factor: higher is narrower for peaks, and steeper
     * (more resonant) for passes and shelves.
     */
    fun design(type: FilterType, freqHz: Double, gainDb: Double, q: Double, sampleRate: Double): BiquadCoefficients {
      val f = freqHz.coerceIn(10.0, sampleRate * 0.49)
      val safeQ = q.coerceIn(0.05, 30.0)
      val a = 10.0.pow(gainDb / 40.0)
      val w0 = 2.0 * PI * f / sampleRate
      val cosW = cos(w0)
      val sinW = sin(w0)
      val alpha = sinW / (2.0 * safeQ)

      val b0: Double
      val b1: Double
      val b2: Double
      val a0: Double
      val a1: Double
      val a2: Double
      when (type) {
        FilterType.PEAK -> {
          b0 = 1 + alpha * a
          b1 = -2 * cosW
          b2 = 1 - alpha * a
          a0 = 1 + alpha / a
          a1 = -2 * cosW
          a2 = 1 - alpha / a
        }
        FilterType.LOW_SHELF -> {
          val s = 2 * sqrt(a) * alpha
          b0 = a * ((a + 1) - (a - 1) * cosW + s)
          b1 = 2 * a * ((a - 1) - (a + 1) * cosW)
          b2 = a * ((a + 1) - (a - 1) * cosW - s)
          a0 = (a + 1) + (a - 1) * cosW + s
          a1 = -2 * ((a - 1) + (a + 1) * cosW)
          a2 = (a + 1) + (a - 1) * cosW - s
        }
        FilterType.HIGH_SHELF -> {
          val s = 2 * sqrt(a) * alpha
          b0 = a * ((a + 1) + (a - 1) * cosW + s)
          b1 = -2 * a * ((a - 1) + (a + 1) * cosW)
          b2 = a * ((a + 1) + (a - 1) * cosW - s)
          a0 = (a + 1) - (a - 1) * cosW + s
          a1 = 2 * ((a - 1) - (a + 1) * cosW)
          a2 = (a + 1) - (a - 1) * cosW - s
        }
        FilterType.LOW_PASS -> {
          b0 = (1 - cosW) / 2
          b1 = 1 - cosW
          b2 = (1 - cosW) / 2
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
        FilterType.HIGH_PASS -> {
          b0 = (1 + cosW) / 2
          b1 = -(1 + cosW)
          b2 = (1 + cosW) / 2
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
      }
      return BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
  }
}
