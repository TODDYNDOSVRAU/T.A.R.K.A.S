package com.example.data.audio.dsp

import com.example.data.model.FilterType
import kotlin.math.tanh

/**
 * Zero-allocation analog coloration stage combining:
 * 1. **Tube / Tape Warmth (2x oversampled)**: asymmetric soft saturation generating subtle 2nd & 3rd
 *    harmonics with linear 2x upsampling + half-band averaging downsampler to suppress aliasing, plus
 *    a 20 Hz DC blocker.
 * 2. **Sub-Bass Harmonic Synthesizer**: extracts 30–90 Hz fundamental and synthesizes 2nd/3rd harmonics
 *    (80–200 Hz) via psychoacoustic missing-fundamental reinforcement, ideal for compact earphones.
 *
 * When both [tubeWarmth] and [subBassHarmonic] are <= 0.005f, [isProcessing] is false (strict 0.00 dB bypass).
 */
class AnalogHarmonicsDsp {
  @Volatile private var tubeWarmth: Float = 0f
  @Volatile private var subBassHarmonic: Float = 0f

  private var sampleRate: Int = 48_000
  private var channels: Int = 2

  // 2x oversampling previous sample + DC blocker state per channel
  private var prevSample = DoubleArray(2)
  private var dcPrevX = DoubleArray(2)
  private var dcPrevY = DoubleArray(2)
  private var dcR = 0.997

  // Sub-bass extractor (bandpass ~58 Hz) and harmonic shaper (highpass ~85 Hz + lowpass ~220 Hz)
  private var subBp = BiquadCoefficients.IDENTITY
  private var subHp = BiquadCoefficients.IDENTITY
  private var subLp = BiquadCoefficients.IDENTITY
  private var sSubBp = DoubleArray(4)
  private var sSubHp = DoubleArray(4)
  private var sSubLp = DoubleArray(4)

  val isProcessing: Boolean get() = tubeWarmth > 0.005f || subBassHarmonic > 0.005f

  fun configure(sampleRate: Int, channels: Int) {
    this.sampleRate = sampleRate.coerceAtLeast(8_000)
    this.channels = channels.coerceAtLeast(1)
    val sr = this.sampleRate.toDouble()
    dcR = 1.0 - (2.0 * Math.PI * 15.0 / sr).coerceIn(0.0005, 0.05)
    subBp = BiquadCoefficients.design(FilterType.BAND_PASS, 58.0, 0.0, 1.1, sr)
    subHp = BiquadCoefficients.design(FilterType.HIGH_PASS, 85.0, 0.0, 0.707, sr)
    subLp = BiquadCoefficients.design(FilterType.LOW_PASS, 220.0, 0.0, 0.707, sr)
    if (prevSample.size != this.channels) {
      prevSample = DoubleArray(this.channels)
      dcPrevX = DoubleArray(this.channels)
      dcPrevY = DoubleArray(this.channels)
      sSubBp = DoubleArray(this.channels * 2)
      sSubHp = DoubleArray(this.channels * 2)
      sSubLp = DoubleArray(this.channels * 2)
    }
    reset()
  }

  fun update(tubeWarmth: Float, subBassHarmonic: Float) {
    this.tubeWarmth = tubeWarmth.coerceIn(0f, 1f)
    this.subBassHarmonic = subBassHarmonic.coerceIn(0f, 1f)
  }

  fun reset() {
    prevSample.fill(0.0)
    dcPrevX.fill(0.0)
    dcPrevY.fill(0.0)
    sSubBp.fill(0.0)
    sSubHp.fill(0.0)
    sSubLp.fill(0.0)
  }

  /** Processes one interleaved frame at [base] in place. Zero allocations. */
  fun processFrame(buffer: FloatArray, base: Int, ch: Int) {
    val tube = tubeWarmth
    val sub = subBassHarmonic
    if (tube <= 0.005f && sub <= 0.005f) return

    val nCh = minOf(ch, channels)
    for (c in 0 until nCh) {
      var x = buffer[base + c].toDouble()

      if (sub > 0.005f) {
        val o = c * 2
        val fund = runBiquad(subBp, x, sSubBp, o)
        // Full-wave + asymmetric squared rectification generates strong 2nd & 3rd harmonics
        val rect = fund * fund * 2.2 + tanh(fund * 3.0) * 0.35
        val hp = runBiquad(subHp, rect, sSubHp, o)
        val harm = runBiquad(subLp, hp, sSubLp, o)
        x += harm * (sub * 0.65)
      }

      if (tube > 0.005f) {
        val drive = 1.0 + tube * 2.8
        val bias = 0.08 * tube
        val mid = (prevSample[c] + x) * 0.5
        prevSample[c] = x

        // 2x oversampled asymmetric tube curve, unity-normalized around 0
        val norm = 1.0 / tanh(drive)
        val y0 = (tanh((mid + bias) * drive) - tanh(bias * drive)) * norm
        val y1 = (tanh((x + bias) * drive) - tanh(bias * drive)) * norm
        val down = (y0 + y1) * 0.5

        // DC blocker removes offset introduced by even-order asymmetric bias
        val dcOut = down - dcPrevX[c] + dcR * dcPrevY[c]
        dcPrevX[c] = down
        dcPrevY[c] = dcOut

        x = x * (1.0 - tube) + dcOut * tube
      }

      buffer[base + c] = x.toFloat()
    }
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
}
