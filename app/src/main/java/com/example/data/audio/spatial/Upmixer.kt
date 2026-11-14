package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Settings of the 2.0 to 5.1 upmixer. Same meaning and defaults as UpmixParams in
 * tools/spatial_proto/tarkas_spatial/upmix.py. The defaults have not been listened to yet.
 */
data class UpmixParams(
  /** 0..1, how much of the centred (in phase, in the middle) content goes to C. */
  val center: Double = 0.7,
  /** 0..1, how much of the out-of-phase content goes to Ls/Rs. */
  val surround: Double = 0.8,
  /** 0..1, how much of the incoherent content (ambience) goes to Ls/Rs. */
  val ambience: Double = 0.25,
  /** -1..1, moves the field to the front (-) or to the back (+). */
  val dimension: Double = 0.0,
  /** Generates the LFE channel (a low-passed copy of the mid). */
  val lfe: Boolean = false,
  val lfeHz: Double = 120.0,
  /** Memory of the spectral averages, as in [SpatialEngine]. */
  val alpha: Double = 0.8
)

/** The per-bin constants of the upmixer: the surround decorrelation phase and the LFE low-pass. */
internal class UpmixTables(sampleRate: Int, params: UpmixParams) {
  /** e^{j·phi} per bin, the rotation that decorrelates Ls from Rs (zero below ~300 Hz). */
  val rotRe = DoubleArray(BINS)
  val rotIm = DoubleArray(BINS)
  val lfeGain = DoubleArray(BINS)

  init {
    // Smoothed random phase, fixed seed: the same sound on every run (the prototype's NumPy generator
    // cannot be reproduced here, and nothing depends on the particular values).
    val rng = java.util.Random(11)
    val raw = DoubleArray(BINS) { (rng.nextDouble() * 2 - 1) * PI }
    val kern = DoubleArray(9) { 0.5 - 0.5 * cos(2 * PI * it / 8) }
    val kSum = kern.sum()
    for (k in 0 until BINS) {
      var s = 0.0
      for (d in -4..4) {
        val j = k + d
        if (j in 0 until BINS) s += kern[d + 4] / kSum * raw[j]
      }
      val f = k * sampleRate.toDouble() / NFFT
      val phi = s * 1.2 * (f / 300.0).coerceIn(0.0, 1.0)
      rotRe[k] = cos(phi); rotIm[k] = sin(phi)
      val x = (ln(max(f, 1.0) / (params.lfeHz * 0.8)) / ln(1.5625)).coerceIn(0.0, 1.0)
      lfeGain[k] = 1 - (0.5 - 0.5 * cos(PI * x))
    }
  }
}

/**
 * The spectral half of the upmixer, shared by [Upmixer] (6 channels out) and [UpmixVirtualizer] (straight
 * to the ears): window the last [WIN] samples of L and R, one complex FFT for both, the per-bin correlation
 * and balance, and the six channel spectra in [specRe]/[specIm] (index: L R C LFE Ls Rs).
 *
 *   rho = Re(Plr) / sqrt(Pll·Prr)        +1 in phase, -1 in antiphase
 *   b   = (|L|-|R|) / (|L|+|R|)           0 = in the middle
 *   gc  = center·max(rho,0)²·(1-|b|)²     goes to C
 *   gs  = (surround·max(-rho,0)² + ambience·(1-|rho|))·(1+dimension/2)   goes to Ls/Rs
 *
 * What goes to C, Ls and Rs is taken out of L and R, so the total energy per bin is kept.
 */
internal class UpmixSpectra(private val p: UpmixParams, private val tables: UpmixTables) {
  val specRe = Array(6) { DoubleArray(BINS) }
  val specIm = Array(6) { DoubleArray(BINS) }

  private val fft = Fft(NFFT)
  private val win = DoubleArray(WIN) { 0.5 - 0.5 * cos(2 * PI * it / WIN) }
  private val fRe = DoubleArray(NFFT)
  private val fIm = DoubleArray(NFFT)
  private val pll = DoubleArray(BINS)
  private val prr = DoubleArray(BINS)
  private val plr = DoubleArray(BINS)

  fun reset() {
    pll.fill(0.0); prr.fill(0.0); plr.fill(0.0)
  }

  fun analyse(histL: DoubleArray, histR: DoubleArray) {
    for (n in 0 until WIN) { fRe[n] = histL[n] * win[n]; fIm[n] = histR[n] * win[n] }
    for (n in WIN until NFFT) { fRe[n] = 0.0; fIm[n] = 0.0 }
    fft.forward(fRe, fIm)
    val a = p.alpha
    val k2 = INV_SQRT2
    for (k in 0 until BINS) {
      val m = if (k == 0) 0 else NFFT - k
      val zr = fRe[k]; val zi = fIm[k]; val cr = fRe[m]; val ci = -fIm[m]
      val lRe = 0.5 * (zr + cr); val lIm = 0.5 * (zi + ci)
      val rRe = 0.5 * (zi - ci); val rIm = -0.5 * (zr - cr)

      pll[k] = a * pll[k] + (1 - a) * (lRe * lRe + lIm * lIm)
      prr[k] = a * prr[k] + (1 - a) * (rRe * rRe + rIm * rIm)
      plr[k] = a * plr[k] + (1 - a) * (lRe * rRe + lIm * rIm)
      val gl = sqrt(pll[k]); val gr = sqrt(prr[k])
      // The floor, relative to the total power, keeps rounding noise in a silent channel (the packed FFT leaves
      // ~1e-16 of the other channel there, correlated with it) from passing as correlation. The prototype
      // does not need it: its separate FFTs give exact zeros.
      val rho = (plr[k] / (gl * gr + 1e-12 * (pll[k] + prr[k]) + 1e-20)).coerceIn(-1.0, 1.0)
      val b = (gl - gr) / (gl + gr + 1e-20)
      val side = 1 - abs(b)
      val gc = (p.center * max(rho, 0.0) * max(rho, 0.0) * side * side).coerceIn(0.0, 1.0)
      val gs = ((p.surround * max(-rho, 0.0) * max(-rho, 0.0) + p.ambience * (1 - abs(rho))) * (1 + 0.5 * p.dimension))
        .coerceIn(0.0, 1.0)

      val mRe = (lRe + rRe) * k2; val mIm = (lIm + rIm) * k2
      val sRe = (lRe - rRe) * k2; val sIm = (lIm - rIm) * k2
      val cRe = gc * mRe; val cIm = gc * mIm            // C = gc·M
      val tRe = gs * sRe * k2; val tIm = gs * sIm * k2  // gs·S/√2
      val rotRe = tables.rotRe[k]; val rotIm = tables.rotIm[k]

      specRe[CH_L][k] = lRe - cRe * k2 - tRe; specIm[CH_L][k] = lIm - cIm * k2 - tIm
      specRe[CH_R][k] = rRe - cRe * k2 + tRe; specIm[CH_R][k] = rIm - cIm * k2 + tIm
      specRe[CH_C][k] = cRe; specIm[CH_C][k] = cIm
      if (p.lfe) {
        specRe[CH_LFE][k] = mRe * tables.lfeGain[k]; specIm[CH_LFE][k] = mIm * tables.lfeGain[k]
      } else {
        specRe[CH_LFE][k] = 0.0; specIm[CH_LFE][k] = 0.0
      }
      // Ls = t·e^{-j·phi}, Rs = t·e^{+j·phi}
      specRe[CH_LS][k] = tRe * rotRe + tIm * rotIm; specIm[CH_LS][k] = tIm * rotRe - tRe * rotIm
      specRe[CH_RS][k] = tRe * rotRe - tIm * rotIm; specIm[CH_RS][k] = tIm * rotRe + tRe * rotIm
    }
  }

  companion object {
    const val CH_L = 0
    const val CH_R = 1
    const val CH_C = 2
    const val CH_LFE = 3
    const val CH_LS = 4
    const val CH_RS = 5
    val INV_SQRT2 = 1 / sqrt(2.0)
  }
}

/**
 * Two real spectra A and B (halves up to Nyquist) into the full Hermitian spectrum of z = a + i·b in
 * [fRe]/[fIm], so one inverse FFT returns both signals (real part = a, imaginary part = b). The imaginary
 * parts of the DC and Nyquist bins of a real signal are zero; dropping them is what a real inverse does.
 * Clears those bins in the inputs.
 */
internal fun packTwoReal(aRe: DoubleArray, aIm: DoubleArray, bRe: DoubleArray, bIm: DoubleArray, fRe: DoubleArray, fIm: DoubleArray) {
  aIm[0] = 0.0; aIm[BINS - 1] = 0.0; bIm[0] = 0.0; bIm[BINS - 1] = 0.0
  for (k in 0 until BINS) {
    fRe[k] = aRe[k] - bIm[k]
    fIm[k] = aIm[k] + bRe[k]
    if (k in 1 until BINS - 1) {
      fRe[NFFT - k] = aRe[k] + bIm[k]
      fIm[NFFT - k] = -aIm[k] + bRe[k]
    }
  }
}

/**
 * 2.0 to 5.1, streaming: stereo in (any block size), whole hops of 6 interleaved channels out in Android's
 * 5POINT1 order L R C LFE Ls Rs, delayed by [LATENCY] samples. Port of
 * tools/spatial_proto/tarkas_spatial/upmix.py: an active-matrix decoder of our own, per STFT bin (Hann 1024,
 * FFT 2048, hop 512), with no HRTF and no limiter. Non-finite input counts as silence.
 *
 * The app does not play these six channels yet (real 5.1 output waits for the route's channel counts);
 * [UpmixVirtualizer] does the same analysis and goes straight to the ears.
 */
class Upmixer(sampleRate: Int, params: UpmixParams = UpmixParams()) {

  companion object {
    /** Window minus hop: 512 samples (10.7 ms at 48 kHz). */
    const val LATENCY = WIN - HOP
  }

  private val spectra = UpmixSpectra(params, UpmixTables(sampleRate, params))
  private val fft = Fft(NFFT)
  private val histL = DoubleArray(WIN)
  private val histR = DoubleArray(WIN)
  private val pendL = DoubleArray(HOP)
  private val pendR = DoubleArray(HOP)
  private var pending = 0
  private val acc = Array(6) { DoubleArray(NFFT) }
  private val fRe = DoubleArray(NFFT)
  private val fIm = DoubleArray(NFFT)

  init {
    reset()
  }

  fun reset() {
    histL.fill(0.0); histR.fill(0.0); pendL.fill(0.0); pendR.fill(0.0)
    pending = 0
    for (a in acc) a.fill(0.0)
    spectra.reset()
  }

  /** [frames] stereo frames from [input] at [offset]; returns the 6-channel frames written to [output] (a multiple of HOP). */
  fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int {
    var written = 0
    for (i in 0 until frames) {
      val l = input[offset + 2 * i]; val r = input[offset + 2 * i + 1]
      pendL[pending] = if (l.isFinite()) l.toDouble() else 0.0
      pendR[pending] = if (r.isFinite()) r.toDouble() else 0.0
      pending++
      if (pending == HOP) {
        pending = 0
        System.arraycopy(histL, HOP, histL, 0, WIN - HOP); System.arraycopy(pendL, 0, histL, WIN - HOP, HOP)
        System.arraycopy(histR, HOP, histR, 0, WIN - HOP); System.arraycopy(pendR, 0, histR, WIN - HOP, HOP)
        frame(output, written)
        written += HOP
      }
    }
    return written
  }

  private fun frame(output: FloatArray, at: Int) {
    spectra.analyse(histL, histR)
    val scale = 1.0 / NFFT
    for (pair in 0 until 3) {
      val a = 2 * pair; val b = a + 1
      packTwoReal(spectra.specRe[a], spectra.specIm[a], spectra.specRe[b], spectra.specIm[b], fRe, fIm)
      fft.inverse(fRe, fIm)
      for (n in 0 until NFFT) { acc[a][n] += fRe[n] * scale; acc[b][n] += fIm[n] * scale }
    }
    for (i in 0 until HOP) for (c in 0 until 6) output[6 * (at + i) + c] = acc[c][i].toFloat()
    for (c in 0 until 6) {
      System.arraycopy(acc[c], HOP, acc[c], 0, NFFT - HOP)
      acc[c].fill(0.0, NFFT - HOP, NFFT)
    }
  }
}
