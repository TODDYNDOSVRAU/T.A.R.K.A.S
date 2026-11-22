package com.example.data.audio.dsp

import com.example.data.model.FilterSlope
import com.example.data.model.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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
    return magnitudeDbAtTrig(cos(w), sin(w), cos(2.0 * w), sin(2.0 * w))
  }

  /**
   * Exact 64-bit IEEE 754 gain in dB using precomputed `(cos(w), sin(w), cos(2w), sin(2w))` from a
   * frequency look-up table — eliminates 4 transcendental calls per point per filter during 120 Hz drag.
   */
  fun magnitudeDbAtTrig(cos1: Double, sin1: Double, cos2: Double, sin2: Double): Double {
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
        FilterType.NOTCH -> {
          b0 = 1.0
          b1 = -2 * cosW
          b2 = 1.0
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
        FilterType.BAND_PASS -> {
          // Constant 0 dB peak gain at centre frequency (RBJ Audio EQ Cookbook).
          b0 = alpha
          b1 = 0.0
          b2 = -alpha
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
        FilterType.TILT_SHELF -> {
          // Symmetric tilt shelf: +gainDb/2 at Nyquist, -gainDb/2 at DC, 0.00 dB at pivot freqHz.
          val s = 2 * sqrt(a) * alpha
          val scale = 1.0 / a // a = 10^(gainDb/40) -> multiplies overall gain by -gainDb/2 dB
          b0 = scale * (a * ((a + 1) + (a - 1) * cosW + s))
          b1 = scale * (-2 * a * ((a - 1) + (a + 1) * cosW))
          b2 = scale * (a * ((a + 1) + (a - 1) * cosW - s))
          a0 = (a + 1) - (a - 1) * cosW + s
          a1 = 2 * ((a - 1) - (a + 1) * cosW)
          a2 = (a + 1) - (a - 1) * cosW - s
        }
      }
      return BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /**
     * Zero-allocation variant of [design] for the real-time audio thread (Dynamic EQ): writes the 5
     * normalised coefficients `(b0, b1, b2, a1, a2)` directly into [out] starting at [offset].
     */
    fun designInto(
      out: DoubleArray,
      offset: Int,
      type: FilterType,
      freqHz: Double,
      gainDb: Double,
      q: Double,
      sampleRate: Double
    ) {
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
        FilterType.NOTCH -> {
          b0 = 1.0
          b1 = -2 * cosW
          b2 = 1.0
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
        FilterType.BAND_PASS -> {
          b0 = alpha
          b1 = 0.0
          b2 = -alpha
          a0 = 1 + alpha
          a1 = -2 * cosW
          a2 = 1 - alpha
        }
        FilterType.TILT_SHELF -> {
          val s = 2 * sqrt(a) * alpha
          val scale = 1.0 / a
          b0 = scale * (a * ((a + 1) + (a - 1) * cosW + s))
          b1 = scale * (-2 * a * ((a - 1) + (a + 1) * cosW))
          b2 = scale * (a * ((a + 1) + (a - 1) * cosW - s))
          a0 = (a + 1) - (a - 1) * cosW + s
          a1 = 2 * ((a - 1) - (a + 1) * cosW)
          a2 = (a + 1) - (a - 1) * cosW - s
        }
      }
      val invA0 = 1.0 / a0
      out[offset] = b0 * invA0
      out[offset + 1] = b1 * invA0
      out[offset + 2] = b2 * invA0
      out[offset + 3] = a1 * invA0
      out[offset + 4] = a2 * invA0
    }

    /**
     * Designs a 1st-order (6 dB/oct) or cascaded Butterworth (18, 24, 36, 48 dB/oct) High-Pass or
     * Low-Pass filter. For 12 dB/oct, uses the standard 2nd-order biquad with the user's [q].
     */
    fun designSlope(
      type: FilterType,
      freqHz: Double,
      q: Double,
      slope: FilterSlope,
      sampleRate: Double
    ): List<BiquadCoefficients> {
      if (!type.usesSlope || slope == FilterSlope.DB_12) {
        return listOf(design(type, freqHz, 0.0, q, sampleRate))
      }
      val f = freqHz.coerceIn(10.0, sampleRate * 0.49)
      val n = slope.order
      val sections = ArrayList<BiquadCoefficients>((n + 1) / 2)
      if (n % 2 == 1) {
        // Exact bilinear 1st-order section (-3.01 dB at f, 6 dB/oct).
        val k = tan(PI * f / sampleRate)
        val a0 = 1.0 + k
        val a1 = (k - 1.0) / a0
        if (type == FilterType.LOW_PASS) {
          val b = k / a0
          sections += BiquadCoefficients(b, b, 0.0, a1, 0.0)
        } else {
          val b = 1.0 / a0
          sections += BiquadCoefficients(b, -b, 0.0, a1, 0.0)
        }
      }
      val pairs = n / 2
      for (k in 0 until pairs) {
        // Butterworth pole angles on the left half-plane.
        val angle = (2 * k + 1) * PI / (2 * n)
        val qSection = 1.0 / (2.0 * sin(angle))
        sections += design(type, f, 0.0, qSection, sampleRate)
      }
      return sections
    }
  }
}
