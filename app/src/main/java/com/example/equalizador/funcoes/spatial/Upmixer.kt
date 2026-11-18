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
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Settings of the 2.0 to 5.1 upmixer.
 *
 * Combines complex 2x2 Hermitian PCA Primary-Ambient Extraction (PAE), Dolby Pro Logic II 5:2:5
 * quadrature steering (k1 = sqrt(19/25), k2 = sqrt(6/25), separating Ls vs Rs cleanly), constant-
 * power energy conservation across (L, C, R, Ls, Rs), and Music-mode gentle surround high-shelf.
 */
data class UpmixParams(
  /** 0..1, how much of the coherent centred content goes to C. */
  val center: Double = 0.88,
  /** 0..1, how much of the out-of-phase / matrix surround content goes to Ls/Rs. */
  val surround: Double = 0.85,
  /** 0..1, how much of the diffuse isotropic ambience goes to Ls/Rs. */
  val ambience: Double = 0.35,
  /** -1..1, moves the field toward the front (-) or the back (+). */
  val dimension: Double = 0.0,
  /** Generates the LFE channel (a low-passed copy of the mid). */
  val lfe: Boolean = false,
  val lfeHz: Double = 120.0,
  /** Memory of the spectral averages (~50 ms). */
  val alpha: Double = 0.8,
  /** Enables DPL II asymmetric k1/k2 steering separating Ls vs Rs on matrix-encoded sources. */
  val dpl2Quadrature: Boolean = true,
  /** Gentle high-shelf frequency (Hz) on surround channels (Music Mode, 0 = flat). */
  val surroundShelfHz: Double = 6500.0
)

/** Per-bin constants of the upmixer: surround decorrelation phase, LFE low-pass, and surround high-shelf. */
internal class UpmixTables(sampleRate: Int, params: UpmixParams) {
  /** e^{j·phi} per bin, the rotation that decorrelates Ls from Rs (zero below ~300 Hz). */
  val rotRe = DoubleArray(BINS)
  val rotIm = DoubleArray(BINS)
  val lfeGain = DoubleArray(BINS)
  /** Music-mode gentle surround high-shelf (-2.8 dB above ~6.5 kHz to avoid rear harshness). */
  val surroundShelf = DoubleArray(BINS)

  init {
    val rng = java.util.Random(11)
    val raw = DoubleArray(BINS) { (rng.nextDouble() * 2 - 1) * PI }
    val kern = DoubleArray(9) { 0.5 - 0.5 * cos(2 * PI * it / 8) }
    val kSum = kern.sum()
    val hfTargetGain = 0.724 // -2.8 dB gentle surround shelf
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

      surroundShelf[k] = if (params.surroundShelfHz <= 0.0 || f <= 1.0) {
        1.0
      } else {
        val ratio = f / params.surroundShelfHz
        val w = (ratio * ratio) / (1.0 + ratio * ratio)
        1.0 - (1.0 - hfTargetGain) * w
      }
    }
  }
}

/**
 * Spectral half of the upmixer, shared by [Upmixer] (6 discrete channels) and [UpmixVirtualizer]
 * (straight to binaural headphones). Zero heap allocation during [analyse].
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
  private val plrRe = DoubleArray(BINS)
  private val plrIm = DoubleArray(BINS)
  private val pca = AcousticPhysics.PcaResult()

  fun reset() {
    pll.fill(0.0); prr.fill(0.0); plrRe.fill(0.0); plrIm.fill(0.0)
  }

  fun analyse(histL: DoubleArray, histR: DoubleArray) {
    for (n in 0 until WIN) { fRe[n] = histL[n] * win[n]; fIm[n] = histR[n] * win[n] }
    for (n in WIN until NFFT) { fRe[n] = 0.0; fIm[n] = 0.0 }
    fft.forward(fRe, fIm)

    val a = p.alpha
    val k2 = INV_SQRT2
    val bypass = p.center == 0.0 && p.surround == 0.0 && p.ambience == 0.0

    for (k in 0 until BINS) {
      val m = if (k == 0) 0 else NFFT - k
      val zr = fRe[k]; val zi = fIm[k]; val cr = fRe[m]; val ci = -fIm[m]
      val lRe = 0.5 * (zr + cr); val lIm = 0.5 * (zi + ci)
      val rRe = 0.5 * (zi - ci); val rIm = -0.5 * (zr - cr)

      pll[k] = a * pll[k] + (1 - a) * (lRe * lRe + lIm * lIm)
      prr[k] = a * prr[k] + (1 - a) * (rRe * rRe + rIm * rIm)
      plrRe[k] = a * plrRe[k] + (1 - a) * (lRe * rRe + lIm * rIm)
      plrIm[k] = a * plrIm[k] + (1 - a) * (lIm * rRe - lRe * rIm)

      if (bypass) {
        specRe[CH_L][k] = lRe; specIm[CH_L][k] = lIm
        specRe[CH_R][k] = rRe; specIm[CH_R][k] = rIm
        specRe[CH_C][k] = 0.0; specIm[CH_C][k] = 0.0
        specRe[CH_LS][k] = 0.0; specIm[CH_LS][k] = 0.0
        specRe[CH_RS][k] = 0.0; specIm[CH_RS][k] = 0.0
        if (p.lfe) {
          specRe[CH_LFE][k] = (lRe + rRe) * k2 * tables.lfeGain[k]
          specIm[CH_LFE][k] = (lIm + rIm) * k2 * tables.lfeGain[k]
        } else {
          specRe[CH_LFE][k] = 0.0; specIm[CH_LFE][k] = 0.0
        }
        continue
      }

      AcousticPhysics.decomposePrimaryAmbient(lRe, lIm, rRe, rIm, pll[k], prr[k], plrRe[k], plrIm[k], pca)
      val gl = sqrt(pll[k]); val gr = sqrt(prr[k])
      val b = (gl - gr) / (gl + gr + 1e-20)
      val rho = pca.rhoSigned
      val rotRe = tables.rotRe[k]; val rotIm = tables.rotIm[k]
      val shelf = tables.surroundShelf[k]

      // 1. Primary directional signal (rank-1 coherent component):
      var flRe = 0.0; var flIm = 0.0
      var frRe = 0.0; var frIm = 0.0
      var cRe = 0.0; var cIm = 0.0
      var pLsRe = 0.0; var pLsIm = 0.0
      var pRsRe = 0.0; var pRsIm = 0.0

      if (rho >= 0.0) {
        // Front stage: extract Center C with constant-power preservation across (L, C, R).
        val side = 1.0 - abs(b)
        val gc = (p.center * rho * rho * side * side).coerceIn(0.0, 1.0)
        val mRe = (pca.pReL + pca.pReR) * k2; val mIm = (pca.pImL + pca.pImR) * k2
        val sRe = (pca.pReL - pca.pReR) * k2; val sIm = (pca.pImL - pca.pImR) * k2
        cRe = gc * mRe; cIm = gc * mIm
        val remMid = sqrt(max(0.0, 1.0 - gc * gc))
        flRe = (remMid * mRe + sRe) * k2; flIm = (remMid * mIm + sIm) * k2
        frRe = (remMid * mRe - sRe) * k2; frIm = (remMid * mIm - sIm) * k2
      } else {
        // Out-of-phase / matrix surround (including DPL II k1/k2 +/-90° quadrature):
        // Separate Ls vs Rs via normalized DPL II level asymmetry ratio.
        val surrFrac = (p.surround * (-rho) * (-rho) * (1.0 + 0.5 * p.dimension)).coerceIn(0.0, 0.96)
        val frontKeep = sqrt(max(0.0, 1.0 - surrFrac))
        flRe = frontKeep * pca.pReL; flIm = frontKeep * pca.pImL
        frRe = frontKeep * pca.pReR; frIm = frontKeep * pca.pImR

        val dRe = pca.pReL - pca.pReR; val dIm = pca.pImL - pca.pImR
        val diffPow = dRe * dRe + dIm * dIm
        val pPow = pca.pReL * pca.pReL + pca.pImL * pca.pImL + pca.pReR * pca.pReR + pca.pImR * pca.pImR
        val normRatio = if (diffPow > 1e-24) min(2.0, sqrt(pPow / diffPow)) else k2
        val surrGain = sqrt(surrFrac) * normRatio * shelf
        // Apply -j quadrature compensation cleanly so matrix surround returns in phase.
        val qRe = dIm * surrGain
        val qIm = -dRe * surrGain

        val uSurr = if (p.dpl2Quadrature) (b / DPL2_ASYM_RATIO).coerceIn(-1.0, 1.0) else 0.0
        val panAngle = (uSurr + 1.0) * (PI / 4.0)
        val panLs = sin(panAngle)
        val panRs = cos(panAngle)
        pLsRe = panLs * (qRe * rotRe + qIm * rotIm); pLsIm = panLs * (qIm * rotRe - qRe * rotIm)
        pRsRe = panRs * (qRe * rotRe - qIm * rotIm); pRsIm = panRs * (qIm * rotRe + qRe * rotIm)
      }

      // 2. Isotropic diffuse ambience (lambda2 subspace): stays out of C, splits across L/R and decorrelated Ls/Rs.
      val ambFrac = (p.ambience * (1.0 - 0.5 * pca.ppr) * (1.0 + 0.5 * p.dimension)).coerceIn(0.0, 1.0)
      val ambFront = sqrt(max(0.0, 1.0 - ambFrac))
      val ambSurr = sqrt(ambFrac) * shelf
      val alRe = pca.aReL; val alIm = pca.aImL
      val arRe = pca.aReR; val arIm = pca.aImR

      specRe[CH_L][k] = flRe + ambFront * alRe
      specIm[CH_L][k] = flIm + ambFront * alIm
      specRe[CH_R][k] = frRe + ambFront * arRe
      specIm[CH_R][k] = frIm + ambFront * arIm
      specRe[CH_C][k] = cRe
      specIm[CH_C][k] = cIm

      if (p.lfe) {
        val mTotRe = (lRe + rRe) * k2; val mTotIm = (lIm + rIm) * k2
        specRe[CH_LFE][k] = mTotRe * tables.lfeGain[k]
        specIm[CH_LFE][k] = mTotIm * tables.lfeGain[k]
      } else {
        specRe[CH_LFE][k] = 0.0; specIm[CH_LFE][k] = 0.0
      }

      specRe[CH_LS][k] = pLsRe + ambSurr * (alRe * rotRe + alIm * rotIm)
      specIm[CH_LS][k] = pLsIm + ambSurr * (alIm * rotRe - alRe * rotIm)
      specRe[CH_RS][k] = pRsRe + ambSurr * (arRe * rotRe - arIm * rotIm)
      specIm[CH_RS][k] = pRsIm + ambSurr * (arIm * rotRe + arRe * rotIm)
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
    /** Exact DPL II surround encode level asymmetry ratio: (sqrt(19) - sqrt(6)) / (sqrt(19) + sqrt(6)) ~= 0.28162. */
    private val DPL2_ASYM_RATIO = (sqrt(19.0) - sqrt(6.0)) / (sqrt(19.0) + sqrt(6.0))
  }
}

/**
 * Two real spectra A and B (halves up to Nyquist) into the full Hermitian spectrum of z = a + i·b in
 * [fRe]/[fIm], so one inverse FFT returns both signals (real part = a, imaginary part = b).
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
 * 5POINT1 order L R C LFE Ls Rs, delayed by [LATENCY] samples (512 samples, 10.7 ms at 48 kHz).
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
