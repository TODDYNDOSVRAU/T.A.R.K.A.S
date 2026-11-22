package com.example.data.audio.dsp

import com.example.data.model.FilterType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Zero-allocation 3-band Linkwitz-Riley multiband compander (Low < 250 Hz, Mid 250–2500 Hz, High > 2500 Hz).
 *
 * Designed for real-time mobile listening: gently lifts quiet details and tames sharp peaks per band
 * without pumping. When [amount] <= 0f, [isProcessing] is false and the processor is strictly bypassed
 * with 0.00 dB deviation and 0% CPU overhead.
 */
class MultibandCompanderDsp {
  @Volatile private var amount: Float = 0f
  private var sampleRate: Int = 48_000
  private var channels: Int = 2

  // 2nd-order Butterworth pairs (cascaded = 4th-order Linkwitz-Riley, phase-coherent sum)
  private var lpLow = BiquadCoefficients.IDENTITY
  private var hpLow = BiquadCoefficients.IDENTITY
  private var lpHigh = BiquadCoefficients.IDENTITY
  private var hpHigh = BiquadCoefficients.IDENTITY

  // State arrays: [ch * 2] per biquad stage (max 2 channels for stereo, dynamically sized if needed)
  private var sLp1A = DoubleArray(4)
  private var sLp1B = DoubleArray(4)
  private var sHp1A = DoubleArray(4)
  private var sHp1B = DoubleArray(4)
  private var sLp2A = DoubleArray(4)
  private var sLp2B = DoubleArray(4)
  private var sHp2A = DoubleArray(4)
  private var sHp2B = DoubleArray(4)

  // Per-band envelope followers (linked across stereo channels for stable imaging)
  private val env = DoubleArray(3)
  private var atkCoef = 0.01
  private var relCoef = 0.0005

  val isProcessing: Boolean get() = amount > 0.005f

  fun configure(sampleRate: Int, channels: Int) {
    this.sampleRate = sampleRate.coerceAtLeast(8_000)
    this.channels = channels.coerceAtLeast(1)
    val sr = this.sampleRate.toDouble()
    val qBw = 0.7071067811865476
    lpLow = BiquadCoefficients.design(FilterType.LOW_PASS, LOW_XOVER_HZ, 0.0, qBw, sr)
    hpLow = BiquadCoefficients.design(FilterType.HIGH_PASS, LOW_XOVER_HZ, 0.0, qBw, sr)
    lpHigh = BiquadCoefficients.design(FilterType.LOW_PASS, HIGH_XOVER_HZ, 0.0, qBw, sr)
    hpHigh = BiquadCoefficients.design(FilterType.HIGH_PASS, HIGH_XOVER_HZ, 0.0, qBw, sr)
    atkCoef = 1.0 - exp(-1.0 / (0.010 * sr))
    relCoef = 1.0 - exp(-1.0 / (0.120 * sr))
    val sz = this.channels * 2
    if (sLp1A.size != sz) {
      sLp1A = DoubleArray(sz); sLp1B = DoubleArray(sz)
      sHp1A = DoubleArray(sz); sHp1B = DoubleArray(sz)
      sLp2A = DoubleArray(sz); sLp2B = DoubleArray(sz)
      sHp2A = DoubleArray(sz); sHp2B = DoubleArray(sz)
    }
    reset()
  }

  fun update(amount: Float) {
    this.amount = amount.coerceIn(0f, 1f)
  }

  fun reset() {
    sLp1A.fill(0.0); sLp1B.fill(0.0)
    sHp1A.fill(0.0); sHp1B.fill(0.0)
    sLp2A.fill(0.0); sLp2B.fill(0.0)
    sHp2A.fill(0.0); sHp2B.fill(0.0)
    env.fill(0.0)
  }

  /** Processes one interleaved frame at [base] in place. Zero allocations. */
  fun processFrame(buffer: FloatArray, base: Int, ch: Int) {
    val amt = amount
    if (amt <= 0.005f || ch <= 0) return

    // Fast path for mono or stereo (up to configured channels)
    val nCh = minOf(ch, channels)
    var peakL = 0.0
    var peakM = 0.0
    var peakH = 0.0

    // Store split bands in local scalars for stereo (no heap allocation)
    var low0 = 0.0; var mid0 = 0.0; var high0 = 0.0
    var low1 = 0.0; var mid1 = 0.0; var high1 = 0.0

    for (c in 0 until nCh) {
      val x = buffer[base + c].toDouble()
      val o = c * 2
      val l1 = runBiquad(lpLow, x, sLp1A, o)
      val low = runBiquad(lpLow, l1, sLp1B, o)
      val h1 = runBiquad(hpLow, x, sHp1A, o)
      val rest = runBiquad(hpLow, h1, sHp1B, o)

      val m1 = runBiquad(lpHigh, rest, sLp2A, o)
      val mid = runBiquad(lpHigh, m1, sLp2B, o)
      val hi1 = runBiquad(hpHigh, rest, sHp2A, o)
      val high = runBiquad(hpHigh, hi1, sHp2B, o)

      peakL = max(peakL, abs(low))
      peakM = max(peakM, abs(mid))
      peakH = max(peakH, abs(high))

      if (c == 0) { low0 = low; mid0 = mid; high0 = high }
      else if (c == 1) { low1 = low; mid1 = mid; high1 = high }
    }

    val gL = bandGain(0, peakL, amt)
    val gM = bandGain(1, peakM, amt)
    val gH = bandGain(2, peakH, amt)

    if (nCh >= 1) {
      val wet = (low0 * gL + mid0 * gM + high0 * gH).toFloat()
      buffer[base] = buffer[base] * (1f - amt) + wet * amt
    }
    if (nCh >= 2) {
      val wet = (low1 * gL + mid1 * gM + high1 * gH).toFloat()
      buffer[base + 1] = buffer[base + 1] * (1f - amt) + wet * amt
    }
  }

  private fun bandGain(band: Int, peak: Double, amt: Float): Double {
    val prev = env[band]
    val coef = if (peak > prev) atkCoef else relCoef
    val e = prev + (peak - prev) * coef
    env[band] = e
    if (e < 1e-5) return 1.0
    // Target RMS/peak anchor around -18 dBFS (0.125): gentle downward compression above, subtle upward lift below
    val target = 0.14
    val ratio = 1.0 + 1.5 * amt // 1:1 -> 2.5:1
    val expPow = (1.0 / ratio) - 1.0
    val gain = (e / target).pow(expPow)
    // Clamp gain excursion to [-8 dB, +5 dB] so noise floor is never pumped excessively
    return gain.coerceIn(0.40, 1.78)
  }

  private fun runBiquad(b: BiquadCoefficients, x: Double, st: DoubleArray, idx: Int): Double {
    val y = b.b0 * x + st[idx]
    if (!y.isFinite()) {
      st[idx] = 0.0
      st[idx + 1] = 0.0
      return 0.0
    }
    st[idx] = b.b1 * x - b.a1 * y + st[idx + 1]
    st[idx + 1] = b.b2 * x - b.a2 * y
    return y
  }

  companion object {
    private const val LOW_XOVER_HZ = 250.0
    private const val HIGH_XOVER_HZ = 2500.0
  }
}
