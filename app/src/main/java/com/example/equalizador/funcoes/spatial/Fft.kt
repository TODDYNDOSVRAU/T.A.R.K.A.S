package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place radix-2 complex FFT of a fixed size, with the twiddles and the bit-reversal table built
 * once. Same sign convention as NumPy: forward = sum x[n]·e^(-2πi·kn/N), inverse without the 1/N.
 * Allocation-free after construction, so it can run on the audio thread.
 */
class Fft(val size: Int) {
  private val cosTable: DoubleArray
  private val sinTable: DoubleArray
  private val reversed: IntArray

  init {
    require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    val bits = Integer.numberOfTrailingZeros(size)
    reversed = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }
  }

  /** Forward transform of (re, im) in place. */
  fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

  /** Inverse transform of (re, im) in place, not divided by the size. */
  fun inverse(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = true)

  private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
    val n = size
    for (i in 0 until n) {
      val j = reversed[i]
      if (j > i) {
        var t = re[i]; re[i] = re[j]; re[j] = t
        t = im[i]; im[i] = im[j]; im[j] = t
      }
    }
    val sign = if (inverse) 1.0 else -1.0
    var len = 2
    while (len <= n) {
      val half = len / 2
      val step = n / len
      var start = 0
      while (start < n) {
        var k = 0
        for (j in start until start + half) {
          val wr = cosTable[k]
          val wi = sign * sinTable[k]
          val l = j + half
          val xr = re[l] * wr - im[l] * wi
          val xi = re[l] * wi + im[l] * wr
          re[l] = re[j] - xr
          im[l] = im[j] - xi
          re[j] += xr
          im[j] += xi
          k += step
        }
        start += len
      }
      len *= 2
    }
  }
}
