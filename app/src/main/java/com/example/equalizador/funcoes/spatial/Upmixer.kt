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
  val surroundShelfHz: Double = 6500.0,
  /**
   * 0..1, share of the wide (left minus right) content of the front stage that moves to Ls/Rs, with
   * energy kept (the front loses what the rear gains). Centred content (lead voice) has no side
   * component, so it never reaches the rears this way. 0 = off.
   */
  val rearFill: Double = 0.0,
  /**
   * 0..1, emphasis of the voice band (about 250 Hz to 3.5 kHz) on C, and the same amount of rejection of
   * it from the rear feed. A spectral weighting of what is already centred, not a stem separation.
   */
  val vocalFocus: Double = 0.0,
  /** Rear feed is high-passed at this frequency (Hz), so bass stays in front and in the LFE. 0 = off. */
  val rearLowCutHz: Double = 0.0,
  /** 0..1, level of the generated LFE channel. */
  val lfeLevel: Double = 1.0,
  /** With [lfe]: below the LFE crossover the bass leaves L/R/C and plays only in the LFE (no doubling). */
  val bassManage: Boolean = false,
  /**
   * 7.1: adds the back pair (SBL/SBR, 8 channels out). The rear feed is shared between the side pair and the
   * back pair: wide instruments mostly on the sides, diffuse ambience mostly on the backs (which also get less
   * of the voice band and their own decorrelation). Off = 5.1, 6 channels.
   */
  val backs: Boolean = false
) {
  companion object {
    /**
     * The Cinema preset: voice-focused centre, wide content moved to the rears with a low cut,
     * more ambience, and bass-managed LFE. The default [UpmixParams] stay neutral for the pinned tests.
     */
    val CINEMA = UpmixParams(
      center = 0.95, ambience = 0.5, lfe = true, lfeLevel = 0.8,
      rearFill = 0.75, vocalFocus = 0.6, rearLowCutHz = 150.0, bassManage = true, backs = true
    )
  }
}

/** Per-bin constants of the upmixer: surround decorrelation phase, LFE low-pass, and surround high-shelf. */
internal class UpmixTables(sampleRate: Int, params: UpmixParams) {
  /** e^{j·phi} per bin, the rotation that decorrelates Ls from Rs (zero below ~300 Hz). */
  val rotRe = DoubleArray(BINS)
  val rotIm = DoubleArray(BINS)
  /** Extra phase turn that decorrelates a back speaker from its side speaker. */
  val relRe = DoubleArray(BINS)
  val relIm = DoubleArray(BINS)
  val lfeGain = DoubleArray(BINS)
  /** Music-mode gentle surround high-shelf (-2.8 dB above ~6.5 kHz to avoid rear harshness). */
  val surroundShelf = DoubleArray(BINS)
  /** 1 inside the voice band (250 Hz to 3.5 kHz), falling smoothly to 0 an octave outside it. */
  val voiceWeight = DoubleArray(BINS)
  /** High-pass for the rear feed (rearLowCutHz), 1 when off. */
  val rearGain = DoubleArray(BINS)

  init {
    val rng = java.util.Random(11)
    val raw = DoubleArray(BINS) { (rng.nextDouble() * 2 - 1) * PI }
    val kern = DoubleArray(9) { 0.5 - 0.5 * cos(2 * PI * it / 8) }
    val kSum = kern.sum()
    val rng2 = java.util.Random(31)
    val raw2 = DoubleArray(BINS) { (rng2.nextDouble() * 2 - 1) * PI }
    val hfTargetGain = 0.724 // -2.8 dB gentle surround shelf
    for (k in 0 until BINS) {
      var s = 0.0; var s2 = 0.0
      for (d in -4..4) {
        val j = k + d
        if (j in 0 until BINS) { s += kern[d + 4] / kSum * raw[j]; s2 += kern[d + 4] / kSum * raw2[j] }
      }
      val f = k * sampleRate.toDouble() / NFFT
      val phi = s * 1.2 * (f / 300.0).coerceIn(0.0, 1.0)
      rotRe[k] = cos(phi); rotIm[k] = sin(phi)
      val phi2 = s2 * 1.2 * (f / 300.0).coerceIn(0.0, 1.0)
      relRe[k] = cos(phi2); relIm[k] = sin(phi2)
      val x = (ln(max(f, 1.0) / (params.lfeHz * 0.8)) / ln(1.5625)).coerceIn(0.0, 1.0)
      lfeGain[k] = 1 - (0.5 - 0.5 * cos(PI * x))
      voiceWeight[k] = when {
        f < 125.0 || f > 7000.0 -> 0.0
        f < 250.0 -> 0.5 - 0.5 * cos(PI * (ln(f / 125.0) / ln(2.0)))
        f <= 3500.0 -> 1.0
        else -> 0.5 + 0.5 * cos(PI * (ln(f / 3500.0) / ln(2.0)))
      }
      rearGain[k] = if (params.rearLowCutHz <= 0.0) 1.0 else {
        val r = f / params.rearLowCutHz
        r * r / (1.0 + r * r)
      }

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
  /** Channels produced: 6 (L R C LFE Ls Rs) or 8 with [UpmixParams.backs] (…SBL SBR). */
  val channels = if (p.backs) 8 else 6
  val specRe = Array(channels) { DoubleArray(BINS) }
  val specIm = Array(channels) { DoubleArray(BINS) }
  // Shares of the rear feed that stay on the side pair (power-preserving with the back share).
  private val sideFill = if (p.backs) cos(BACK_FILL_ANGLE) else 1.0
  private val backFill = if (p.backs) sin(BACK_FILL_ANGLE) else 0.0
  private val sideAmb = if (p.backs) cos(BACK_AMB_ANGLE) else 1.0
  private val backAmb = if (p.backs) sin(BACK_AMB_ANGLE) else 0.0

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
        if (p.backs) {
          specRe[CH_SBL][k] = 0.0; specIm[CH_SBL][k] = 0.0
          specRe[CH_SBR][k] = 0.0; specIm[CH_SBR][k] = 0.0
        }
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
        var gc = p.center * rho * rho * side * side
        if (p.vocalFocus > 0.0) {
          // Voice band toward C, the rest of the spectrum slightly less (energy is returned to L/R below).
          val vw = tables.voiceWeight[k]
          gc = gc * (1.0 + p.vocalFocus * vw) * (1.0 - 0.4 * p.vocalFocus * (1.0 - vw))
        }
        gc = gc.coerceIn(0.0, if (p.vocalFocus > 0.0) 0.97 else 1.0)
        val mRe = (pca.pReL + pca.pReR) * k2; val mIm = (pca.pImL + pca.pImR) * k2
        val sRe = (pca.pReL - pca.pReR) * k2; val sIm = (pca.pImL - pca.pImR) * k2
        cRe = gc * mRe; cIm = gc * mIm
        val remMid = sqrt(max(0.0, 1.0 - gc * gc))
        // Rear fill: part of the wide (L minus R) content moves to Ls/Rs, energy kept. Centred content
        // has no side component, and the voice band is fed less, so the lead voice stays out of the rears.
        val rf = if (p.rearFill > 0.0) {
          (p.rearFill * tables.rearGain[k] * (1.0 - 0.5 * p.vocalFocus * tables.voiceWeight[k])).coerceIn(0.0, 0.8)
        } else 0.0
        val sf = if (rf > 0.0) sqrt(1.0 - rf * rf) else 1.0
        flRe = (remMid * mRe + sf * sRe) * k2; flIm = (remMid * mIm + sf * sIm) * k2
        frRe = (remMid * mRe - sf * sRe) * k2; frIm = (remMid * mIm - sf * sIm) * k2
        if (rf > 0.0) {
          // The rear pair follows the side of the source (b: +1 left), power-preserving, equal at b = 0.
          val pan = (b + 1.0) * (PI / 4.0)
          val gLs = rf * k2 * shelf * sin(pan) * SQRT2
          val gRs = rf * k2 * shelf * cos(pan) * SQRT2
          pLsRe = gLs * (sRe * rotRe + sIm * rotIm); pLsIm = gLs * (sIm * rotRe - sRe * rotIm)
          pRsRe = -gRs * (sRe * rotRe - sIm * rotIm); pRsIm = -gRs * (sIm * rotRe + sRe * rotIm)
        }
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

      // Bass management: below the LFE crossover the bass leaves the mains and plays only in the LFE.
      val hp = if (p.lfe && p.bassManage) 1.0 - tables.lfeGain[k] else 1.0
      specRe[CH_L][k] = hp * (flRe + ambFront * alRe)
      specIm[CH_L][k] = hp * (flIm + ambFront * alIm)
      specRe[CH_R][k] = hp * (frRe + ambFront * arRe)
      specIm[CH_R][k] = hp * (frIm + ambFront * arIm)
      specRe[CH_C][k] = hp * cRe
      specIm[CH_C][k] = hp * cIm

      if (p.lfe) {
        val mTotRe = (lRe + rRe) * k2; val mTotIm = (lIm + rIm) * k2
        specRe[CH_LFE][k] = mTotRe * tables.lfeGain[k] * p.lfeLevel
        specIm[CH_LFE][k] = mTotIm * tables.lfeGain[k] * p.lfeLevel
      } else {
        specRe[CH_LFE][k] = 0.0; specIm[CH_LFE][k] = 0.0
      }

      val aLsRe = ambSurr * (alRe * rotRe + alIm * rotIm); val aLsIm = ambSurr * (alIm * rotRe - alRe * rotIm)
      val aRsRe = ambSurr * (arRe * rotRe - arIm * rotIm); val aRsIm = ambSurr * (arIm * rotRe + arRe * rotIm)
      specRe[CH_LS][k] = sideFill * pLsRe + sideAmb * aLsRe
      specIm[CH_LS][k] = sideFill * pLsIm + sideAmb * aLsIm
      specRe[CH_RS][k] = sideFill * pRsRe + sideAmb * aRsRe
      specIm[CH_RS][k] = sideFill * pRsIm + sideAmb * aRsIm
      if (p.backs) {
        // Back pair: its share of the rear feed, turned by an extra decorrelating phase. The voice band
        // is thinned on the backs (they carry instruments, effects and ambience, not the lead voice).
        val bv = 1.0 - 0.7 * p.vocalFocus * tables.voiceWeight[k]
        val bLRe = backFill * bv * pLsRe + backAmb * bv * aLsRe; val bLIm = backFill * bv * pLsIm + backAmb * bv * aLsIm
        val bRRe = backFill * bv * pRsRe + backAmb * bv * aRsRe; val bRIm = backFill * bv * pRsIm + backAmb * bv * aRsIm
        val qRe = tables.relRe[k]; val qIm = tables.relIm[k]
        specRe[CH_SBL][k] = bLRe * qRe - bLIm * qIm; specIm[CH_SBL][k] = bLRe * qIm + bLIm * qRe
        specRe[CH_SBR][k] = bRRe * qRe + bRIm * qIm; specIm[CH_SBR][k] = bRIm * qRe - bRRe * qIm
      }
    }
  }

  companion object {
    const val CH_L = 0
    const val CH_R = 1
    const val CH_C = 2
    const val CH_LFE = 3
    const val CH_LS = 4
    const val CH_RS = 5
    const val CH_SBL = 6
    const val CH_SBR = 7
    /** Angles of the side/back split: wide fill 68% / 32%, ambience 45% / 55% (side / back power). */
    private const val BACK_FILL_ANGLE = 0.6
    private const val BACK_AMB_ANGLE = 0.83
    val INV_SQRT2 = 1 / sqrt(2.0)
    private val SQRT2 = sqrt(2.0)
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

  /** Channels per output frame: 6, or 8 with [UpmixParams.backs] (order L R C LFE Ls Rs SBL SBR). */
  val channels = if (params.backs) 8 else 6

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
  private val acc = Array(channels) { DoubleArray(NFFT) }
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
    for (pair in 0 until channels / 2) {
      val a = 2 * pair; val b = a + 1
      packTwoReal(spectra.specRe[a], spectra.specIm[a], spectra.specRe[b], spectra.specIm[b], fRe, fIm)
      fft.inverse(fRe, fIm)
      for (n in 0 until NFFT) { acc[a][n] += fRe[n] * scale; acc[b][n] += fIm[n] * scale }
    }
    for (i in 0 until HOP) for (c in 0 until channels) output[channels * (at + i) + c] = acc[c][i].toFloat()
    for (c in 0 until channels) {
      System.arraycopy(acc[c], HOP, acc[c], 0, NFFT - HOP)
      acc[c].fill(0.0, NFFT - HOP, NFFT)
    }
  }
}
