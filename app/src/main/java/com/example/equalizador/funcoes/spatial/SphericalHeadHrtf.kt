package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Head-Related Transfer Function over a sphere of 5° azimuth × 10° elevation (-40° to 90°),
 * diffuse-field normalised.
 *
 * Two acoustic modes share the same grid & interface (`HrtfSource`):
 * - Legacy classic (`accurateDiffraction = false`): direct Brown & Duda (1998) spherical-head
 *   port of tools/spatial_proto, preserved so prototype snapshot tests stay deterministic.
 * - Full psychoacoustic physics (`accurateDiffraction = true`, used in production):
 *   Real Cepstrum minimum-phase decomposition + finite speaker distance (r = 2 m, proximal ILD &
 *   Extended Woodworth horizon) + posterior ear offset (theta_E = 97.5°, EDF) + Kuhn (1977)
 *   low-frequency diffraction blend (f0 = 630 Hz) + concha resonance (3.4 kHz) + dual elevation-
 *   tracking pinna notches (6.2 / 9.5 kHz) + continuous bilinear sphere interpolation without
 *   5° step quantization jumps.
 *
 * Coordinates follow ITU-R BS.2051: x forward, y left, z up; positive azimuth to the left.
 */
class SphericalHeadHrtf(
  override val sampleRate: Int,
  val nfft: Int = SpatialEngine.NFFT,
  val delaySamples: Int = SpatialEngine.HRTF_DELAY,
  private val headRadius: Double = 0.0875,
  private val speedOfSound: Double = 343.0,
  val accurateDiffraction: Boolean = false,
  val geometry: AcousticPhysics.HeadGeometry = AcousticPhysics.HeadGeometry(
    headRadiusM = headRadius,
    speedOfSound = speedOfSound
  )
) : HrtfSource {
  val bins = nfft / 2 + 1
  private val freqs = DoubleArray(bins) { it * sampleRate.toDouble() / nfft }

  /** Grid tables, per ear, real and imaginary parts: index ((az * EL_COUNT + el) * bins + k). */
  private val tableLRe: FloatArray
  private val tableLIm: FloatArray
  private val tableRRe: FloatArray
  private val tableRIm: FloatArray

  /** Time-aligned pure minimum-phase tables (excess ITD removed), for smooth bilinear interpolation. */
  private val minLRe: FloatArray
  private val minLIm: FloatArray
  private val minRRe: FloatArray
  private val minRIm: FloatArray

  /** Precomputed continuous delay parameters per cell (for fast bilinear audio-thread lookup). */
  private val cellTauHfL: DoubleArray
  private val cellTauLfExL: DoubleArray
  private val cellTauHfR: DoubleArray
  private val cellTauLfExR: DoubleArray

  var binLRe = 0.0; private set
  var binLIm = 0.0; private set
  var binRRe = 0.0; private set
  var binRIm = 0.0; private set

  private val norm = DoubleArray(bins)
  private val fft = if (accurateDiffraction) Fft(nfft) else null

  // Per-bin pieces shared by every direction.
  private val omega = DoubleArray(bins) { 2 * PI * freqs[it] }
  private val log2f = DoubleArray(bins) { log2(max(freqs[it], 1.0)) }
  private val rearShelfCurve = DoubleArray(bins) {
    val steepness = if (accurateDiffraction) 3.5 else 4.0
    val knee = if (accurateDiffraction) 3200.0 else 3000.0
    1 / (1 + exp(-steepness * (log2f[it] - log2(knee))))
  }
  private val conchaBell = DoubleArray(bins) {
    val d = (log2f[it] - log2(3400.0)) / 0.42
    exp(-0.5 * d * d)
  }
  private val delayRe = DoubleArray(bins) { cos(-omega[it] * delaySamples / sampleRate) }
  private val delayIm = DoubleArray(bins) { sin(-omega[it] * delaySamples / sampleRate) }
  private val kuhnWeight = DoubleArray(bins) {
    val f = freqs[it]
    AcousticPhysics.KUHN_TRANSITION_HZ / (AcousticPhysics.KUHN_TRANSITION_HZ + f)
  }
  private val delaySec = delaySamples.toDouble() / sampleRate
  private val lut = AcousticPhysics.UnitCircleLut(2048)

  init {
    val cells = AZ_COUNT * EL_COUNT
    tableLRe = FloatArray(cells * bins); tableLIm = FloatArray(cells * bins)
    tableRRe = FloatArray(cells * bins); tableRIm = FloatArray(cells * bins)
    minLRe = FloatArray(if (accurateDiffraction) cells * bins else 0)
    minLIm = FloatArray(if (accurateDiffraction) cells * bins else 0)
    minRRe = FloatArray(if (accurateDiffraction) cells * bins else 0)
    minRIm = FloatArray(if (accurateDiffraction) cells * bins else 0)
    cellTauHfL = DoubleArray(if (accurateDiffraction) cells else 0)
    cellTauLfExL = DoubleArray(if (accurateDiffraction) cells else 0)
    cellTauHfR = DoubleArray(if (accurateDiffraction) cells else 0)
    cellTauLfExR = DoubleArray(if (accurateDiffraction) cells else 0)

    val power = DoubleArray(bins)
    var weightSum = 0.0
    val hl = DoubleArray(bins * 2)
    val hr = DoubleArray(bins * 2)
    val mRe = DoubleArray(bins); val mIm = DoubleArray(bins)
    val scratchRe = DoubleArray(nfft); val scratchIm = DoubleArray(nfft)
    val magScratch = DoubleArray(bins)

    for (ia in 0 until AZ_COUNT) {
      for (ie in 0 until EL_COUNT) {
        val az = azimuth(ia)
        val el = elevation(ie)
        val base = (ia * EL_COUNT + ie) * bins
        if (!accurateDiffraction) {
          evaluateClassic(az, el, hl, hr)
        } else {
          val cellIdx = ia * EL_COUNT + ie
          val dotL = AcousticPhysics.sourceEarCos(az, el, true, geometry)
          val dotR = AcousticPhysics.sourceEarCos(az, el, false, geometry)
          cellTauHfL[cellIdx] = AcousticPhysics.extendedWoodworthDelaySec(dotL, geometry)
          cellTauLfExL[cellIdx] = AcousticPhysics.kuhnLowFreqDelaySec(dotL, geometry) -
            AcousticPhysics.brownDudaShadowGroupDelaySec(dotL, geometry)
          cellTauHfR[cellIdx] = AcousticPhysics.extendedWoodworthDelaySec(dotR, geometry)
          cellTauLfExR[cellIdx] = AcousticPhysics.kuhnLowFreqDelaySec(dotR, geometry) -
            AcousticPhysics.brownDudaShadowGroupDelaySec(dotR, geometry)
          evaluateAccurate(
            az, el, hl, hr,
            minOutLRe = minLRe, minOutLIm = minLIm,
            minOutRRe = minRRe, minOutRIm = minRIm,
            cellBase = base,
            magScratch = magScratch,
            cepRe = scratchRe, cepIm = scratchIm,
            tmpRe = mRe, tmpIm = mIm
          )
        }
        val w = cos(Math.toRadians(el))
        weightSum += w
        for (k in 0 until bins) {
          val lr = hl[2 * k]; val li = hl[2 * k + 1]
          val rr = hr[2 * k]; val ri = hr[2 * k + 1]
          power[k] += w * (lr * lr + li * li + rr * rr + ri * ri) / 2
          tableLRe[base + k] = lr.toFloat(); tableLIm[base + k] = li.toFloat()
          tableRRe[base + k] = rr.toFloat(); tableRIm[base + k + 0] = ri.toFloat()
        }
      }
    }
    // Diffuse-field normalisation: mean power over the sphere = 1 per bin, so the HRTF adds no tone
    // colour of its own on top of the headphone correction.
    for (k in 0 until bins) norm[k] = 1 / sqrt(power[k] / weightSum + 1e-20)
    for (cell in 0 until cells) {
      val base = cell * bins
      for (k in 0 until bins) {
        val n = norm[k].toFloat()
        tableLRe[base + k] *= n; tableLIm[base + k] *= n
        tableRRe[base + k] *= n; tableRIm[base + k] *= n
        if (accurateDiffraction) {
          minLRe[base + k] *= n; minLIm[base + k] *= n
          minRRe[base + k] *= n; minRIm[base + k] *= n
        }
      }
    }
  }

  /** Classic Brown & Duda prototype evaluation. */
  private fun evaluateClassic(azDeg: Double, elDeg: Double, outL: DoubleArray, outR: DoubleArray) {
    val az = Math.toRadians(azDeg)
    val el = Math.toRadians(elDeg)
    val uy = cos(el) * sin(az)
    val thetaL = acos(uy.coerceIn(-1.0, 1.0))
    val thetaR = acos((-uy).coerceIn(-1.0, 1.0))
    val log2Notch = log2(6000.0 * (11.0 / 6.0).pow((elDeg + 40.0) / 130.0))
    val depth = 1 - 10.0.pow(-12.0 / 20)
    val rear = max(0.0, -cos(az))
    for (k in 0 until bins) {
      val x = (log2f[k] - log2Notch) / 0.25
      val notch = 1 - depth * exp(-0.5 * x * x)
      val shelf = 1 - 0.35 * rear * rearShelfCurve[k]
      val pinna = notch * shelf
      val cr = pinna * delayRe[k]
      val ci = pinna * delayIm[k]
      earClassic(thetaL, k, outL, cr, ci)
      earClassic(thetaR, k, outR, cr, ci)
    }
  }

  private fun earClassic(theta: Double, k: Int, out: DoubleArray, cr: Double, ci: Double) {
    val w = omega[k]
    val w0 = speedOfSound / headRadius
    val alpha = (1 + A_MIN / 2) + (1 - A_MIN / 2) * cos(theta / THETA_MIN * PI)
    val q = w / (2 * w0)
    val den = 1 + q * q
    val sr = (1 + alpha * q * q) / den
    val si = (alpha * q - q) / den
    val t = if (theta < PI / 2) -headRadius / speedOfSound * cos(theta) else headRadius / speedOfSound * (theta - PI / 2)
    val pr = cos(w * t)
    val pi = -sin(w * t)
    val er = sr * pr - si * pi
    val ei = sr * pi + si * pr
    out[2 * k] = er * cr - ei * ci
    out[2 * k + 1] = er * ci + ei * cr
  }

  /**
   * Full physics evaluation: Extended Woodworth-Kuhn + Real Cepstrum Minimum Phase + Concha
   * resonance (3.4 kHz) + Dual elevation-tracking Pinna Notches + Rear pinna flange shadow.
   */
  private fun evaluateAccurate(
    azDeg: Double, elDeg: Double,
    outL: DoubleArray, outR: DoubleArray,
    minOutLRe: FloatArray? = null, minOutLIm: FloatArray? = null,
    minOutRRe: FloatArray? = null, minOutRIm: FloatArray? = null,
    cellBase: Int = 0,
    magScratch: DoubleArray,
    cepRe: DoubleArray, cepIm: DoubleArray,
    tmpRe: DoubleArray, tmpIm: DoubleArray
  ) {
    val az = Math.toRadians(azDeg)
    val el = Math.toRadians(elDeg)
    val front = max(0.0, cos(az)) * cos(el)
    val rear = max(0.0, -cos(az)) * cos(el)
    val fNotch1 = 5600.0 * (9200.0 / 5600.0).pow((elDeg + 40.0) / 130.0)
    val fNotch2 = 8800.0 * (12800.0 / 8800.0).pow((elDeg + 40.0) / 130.0)
    val log2N1 = log2(fNotch1)
    val log2N2 = log2(fNotch2)
    val d1 = 1.0 - 10.0.pow(-6.5 / 20.0)
    val d2 = 1.0 - 10.0.pow(-7.5 / 20.0)
    for (k in 0 until bins) {
      val concha = 1.0 + 0.35 * front * conchaBell[k]
      val x1 = (log2f[k] - log2N1) / 0.22
      val x2 = (log2f[k] - log2N2) / 0.20
      val n1 = 1.0 - d1 * exp(-0.5 * x1 * x1)
      val n2 = 1.0 - d2 * exp(-0.5 * x2 * x2)
      val shelf = 1.0 - 0.45 * rear * rearShelfCurve[k]
      magScratch[k] = concha * n1 * n2 * shelf
    }
    AcousticPhysics.minimumPhaseFromMagnitude(magScratch, fft!!, cepRe, cepIm, tmpRe, tmpIm)

    val dotL = AcousticPhysics.sourceEarCos(azDeg, elDeg, true, geometry)
    val dotR = AcousticPhysics.sourceEarCos(azDeg, elDeg, false, geometry)
    applyEarAccurate(dotL, tmpRe, tmpIm, outL, minOutLRe, minOutLIm, cellBase)
    applyEarAccurate(dotR, tmpRe, tmpIm, outR, minOutRRe, minOutRIm, cellBase)
  }

  private fun applyEarAccurate(
    dot: Double,
    pinnaRe: DoubleArray, pinnaIm: DoubleArray,
    out: DoubleArray,
    minOutRe: FloatArray?, minOutIm: FloatArray?, cellBase: Int
  ) {
    val rho = max(geometry.speakerDistanceM / geometry.headRadiusM, 1.0001)
    val proximal = 1.0 / sqrt(max(0.25, 1.0 + 1.0 / (rho * rho) - 2.0 * dot / rho))
    val theta = acos(dot.coerceIn(-1.0, 1.0))
    val alpha = (1.0 + A_MIN / 2.0) + (1.0 - A_MIN / 2.0) * cos(theta / THETA_MIN * PI)
    val w0 = geometry.speedOfSound / geometry.headRadiusM
    val tauHf = AcousticPhysics.extendedWoodworthDelaySec(dot, geometry)
    val tauLfExcess = AcousticPhysics.kuhnLowFreqDelaySec(dot, geometry) -
      AcousticPhysics.brownDudaShadowGroupDelaySec(dot, geometry)

    for (k in 0 until bins) {
      val q = omega[k] / (2.0 * w0)
      val den = 1.0 + q * q
      val sr = (1.0 + alpha * q * q) / den * proximal
      val si = (alpha * q - q) / den * proximal
      val pr = pinnaRe[k]; val pi = pinnaIm[k]
      // Combined minimum-phase response before excess ITD delay.
      val minR = sr * pr - si * pi
      val minI = sr * pi + si * pr
      if (minOutRe != null && minOutIm != null) {
        minOutRe[cellBase + k] = minR.toFloat()
        minOutIm[cellBase + k] = minI.toFloat()
      }
      val kw = kuhnWeight[k]
      val tau = kw * tauLfExcess + (1.0 - kw) * tauHf + delaySec
      val phase = -omega[k] * tau
      val cr = cos(phase); val ci = sin(phase)
      out[2 * k] = minR * cr - minI * ci
      out[2 * k + 1] = minR * ci + minI * cr
    }
  }

  /** Normalised HRTF (same scale as the grid) for an exact direction: (re, im) interleaved, per ear. */
  override fun response(azDeg: Double, elDeg: Double): Pair<DoubleArray, DoubleArray> {
    val hl = DoubleArray(bins * 2)
    val hr = DoubleArray(bins * 2)
    if (!accurateDiffraction) {
      evaluateClassic(azDeg, elDeg, hl, hr)
    } else {
      val mRe = DoubleArray(bins); val mIm = DoubleArray(bins)
      val sRe = DoubleArray(nfft); val sIm = DoubleArray(nfft)
      val mag = DoubleArray(bins)
      evaluateAccurate(azDeg, elDeg, hl, hr, magScratch = mag, cepRe = sRe, cepIm = sIm, tmpRe = mRe, tmpIm = mIm)
    }
    for (k in 0 until bins) {
      hl[2 * k] *= norm[k]; hl[2 * k + 1] *= norm[k]
      hr[2 * k] *= norm[k]; hr[2 * k + 1] *= norm[k]
    }
    return hl to hr
  }

  /**
   * Continuous zero-allocation binaural lookup for ([azDeg], [elDeg]) used on the audio thread:
   * bilinearly interpolates the time-aligned minimum-phase sphere spectra and synthesizes the
   * continuous analytic Kuhn-Woodworth Finite-Distance ITD phase + HRTF_DELAY via unit-circle LUT.
   */
  fun evaluateContinuous(
    azDeg: Double, elDeg: Double,
    outLRe: DoubleArray, outLIm: DoubleArray,
    outRRe: DoubleArray, outRIm: DoubleArray
  ) {
    if (!accurateDiffraction) {
      val base = cell(azDeg, elDeg)
      for (k in 0 until bins) {
        outLRe[k] = tableLRe[base + k].toDouble(); outLIm[k] = tableLIm[base + k].toDouble()
        outRRe[k] = tableRRe[base + k].toDouble(); outRIm[k] = tableRIm[base + k].toDouble()
      }
      return
    }
    val azWrapped = ((azDeg + 180.0) % 360.0 + 360.0) % 360.0
    val azPos = azWrapped / AZ_STEP
    val ia0 = floor(azPos).toInt() % AZ_COUNT
    val ia1 = (ia0 + 1) % AZ_COUNT
    val fa = azPos - floor(azPos)

    val elPos = ((elDeg - EL_MIN) / EL_STEP).coerceIn(0.0, (EL_COUNT - 1).toDouble())
    val ie0 = floor(elPos).toInt().coerceIn(0, EL_COUNT - 1)
    val ie1 = (ie0 + 1).coerceAtMost(EL_COUNT - 1)
    val fe = elPos - ie0

    val w00 = (1.0 - fa) * (1.0 - fe)
    val w10 = fa * (1.0 - fe)
    val w01 = (1.0 - fa) * fe
    val w11 = fa * fe

    val c00 = ia0 * EL_COUNT + ie0
    val c10 = ia1 * EL_COUNT + ie0
    val c01 = ia0 * EL_COUNT + ie1
    val c11 = ia1 * EL_COUNT + ie1

    val b00 = c00 * bins
    val b10 = c10 * bins
    val b01 = c01 * bins
    val b11 = c11 * bins

    val tauHfL = w00 * cellTauHfL[c00] + w10 * cellTauHfL[c10] + w01 * cellTauHfL[c01] + w11 * cellTauHfL[c11]
    val tauLfExL = w00 * cellTauLfExL[c00] + w10 * cellTauLfExL[c10] + w01 * cellTauLfExL[c01] + w11 * cellTauLfExL[c11]
    val tauHfR = w00 * cellTauHfR[c00] + w10 * cellTauHfR[c10] + w01 * cellTauHfR[c01] + w11 * cellTauHfR[c11]
    val tauLfExR = w00 * cellTauLfExR[c00] + w10 * cellTauLfExR[c10] + w01 * cellTauLfExR[c01] + w11 * cellTauLfExR[c11]

    for (k in 0 until bins) {
      val mLRe = w00 * minLRe[b00 + k] + w10 * minLRe[b10 + k] + w01 * minLRe[b01 + k] + w11 * minLRe[b11 + k]
      val mLIm = w00 * minLIm[b00 + k] + w10 * minLIm[b10 + k] + w01 * minLIm[b01 + k] + w11 * minLIm[b11 + k]
      val mRRe = w00 * minRRe[b00 + k] + w10 * minRRe[b10 + k] + w01 * minRRe[b01 + k] + w11 * minRRe[b11 + k]
      val mRIm = w00 * minRIm[b00 + k] + w10 * minRIm[b10 + k] + w01 * minRIm[b01 + k] + w11 * minRIm[b11 + k]

      val kw = kuhnWeight[k]
      val w = omega[k]
      val tauL = kw * tauLfExL + (1.0 - kw) * tauHfL + delaySec
      lut.eval(-w * tauL)
      outLRe[k] = mLRe * lut.re - mLIm * lut.im
      outLIm[k] = mLRe * lut.im + mLIm * lut.re

      val tauR = kw * tauLfExR + (1.0 - kw) * tauHfR + delaySec
      lut.eval(-w * tauR)
      outRRe[k] = mRRe * lut.re - mRIm * lut.im
      outRIm[k] = mRRe * lut.im + mRIm * lut.re
    }
  }

  /**
   * Per-bin continuous zero-allocation lookup for arbitrary ([azDeg], [elDeg]) at bin [k], writing
   * results into [binLRe], [binLIm], [binRRe], [binRIm]. Used by [SpatialEngine] to avoid 5° stair-steps.
   */
  fun evalBinContinuous(azDeg: Double, elDeg: Double, k: Int) {
    if (!accurateDiffraction) {
      val idx = cell(azDeg, elDeg) + k
      binLRe = tableLRe[idx].toDouble(); binLIm = tableLIm[idx].toDouble()
      binRRe = tableRRe[idx].toDouble(); binRIm = tableRIm[idx].toDouble()
      return
    }
    val azWrapped = ((azDeg + 180.0) % 360.0 + 360.0) % 360.0
    val azPos = azWrapped / AZ_STEP
    val ia0 = floor(azPos).toInt() % AZ_COUNT
    val ia1 = (ia0 + 1) % AZ_COUNT
    val fa = azPos - floor(azPos)

    val elPos = ((elDeg - EL_MIN) / EL_STEP).coerceIn(0.0, (EL_COUNT - 1).toDouble())
    val ie0 = floor(elPos).toInt().coerceIn(0, EL_COUNT - 1)
    val ie1 = (ie0 + 1).coerceAtMost(EL_COUNT - 1)
    val fe = elPos - ie0

    val w00 = (1.0 - fa) * (1.0 - fe)
    val w10 = fa * (1.0 - fe)
    val w01 = (1.0 - fa) * fe
    val w11 = fa * fe

    val c00 = ia0 * EL_COUNT + ie0
    val c10 = ia1 * EL_COUNT + ie0
    val c01 = ia0 * EL_COUNT + ie1
    val c11 = ia1 * EL_COUNT + ie1

    val b00 = c00 * bins + k
    val b10 = c10 * bins + k
    val b01 = c01 * bins + k
    val b11 = c11 * bins + k

    val mLRe = w00 * minLRe[b00] + w10 * minLRe[b10] + w01 * minLRe[b01] + w11 * minLRe[b11]
    val mLIm = w00 * minLIm[b00] + w10 * minLIm[b10] + w01 * minLIm[b01] + w11 * minLIm[b11]
    val mRRe = w00 * minRRe[b00] + w10 * minRRe[b10] + w01 * minRRe[b01] + w11 * minRRe[b11]
    val mRIm = w00 * minRIm[b00] + w10 * minRIm[b10] + w01 * minRIm[b01] + w11 * minRIm[b11]

    val tauHfL = w00 * cellTauHfL[c00] + w10 * cellTauHfL[c10] + w01 * cellTauHfL[c01] + w11 * cellTauHfL[c11]
    val tauLfL = w00 * cellTauLfExL[c00] + w10 * cellTauLfExL[c10] + w01 * cellTauLfExL[c01] + w11 * cellTauLfExL[c11]
    val tauHfR = w00 * cellTauHfR[c00] + w10 * cellTauHfR[c10] + w01 * cellTauHfR[c01] + w11 * cellTauHfR[c11]
    val tauLfR = w00 * cellTauLfExR[c00] + w10 * cellTauLfExR[c10] + w01 * cellTauLfExR[c01] + w11 * cellTauLfExR[c11]

    val kw = kuhnWeight[k]
    val w = omega[k]
    val tauL = kw * tauLfL + (1.0 - kw) * tauHfL + delaySec
    lut.eval(-w * tauL)
    binLRe = mLRe * lut.re - mLIm * lut.im
    binLIm = mLRe * lut.im + mLIm * lut.re

    val tauR = kw * tauLfR + (1.0 - kw) * tauHfR + delaySec
    lut.eval(-w * tauR)
    binRRe = mRRe * lut.re - mRIm * lut.im
    binRIm = mRRe * lut.im + mRIm * lut.re
  }

  /** Table offset of the grid cell nearest to a direction (used by fast static lookup). */
  fun cell(azDeg: Double, elDeg: Double): Int {
    var ia = Math.rint((azDeg + 180.0) / AZ_STEP).toInt() % AZ_COUNT
    if (ia < 0) ia += AZ_COUNT
    val ie = Math.rint((elDeg - EL_MIN) / EL_STEP).toInt().coerceIn(0, EL_COUNT - 1)
    return (ia * EL_COUNT + ie) * bins
  }

  fun lRe(index: Int) = tableLRe[index]
  fun lIm(index: Int) = tableLIm[index]
  fun rRe(index: Int) = tableRRe[index]
  fun rIm(index: Int) = tableRIm[index]

  companion object {
    const val AZ_STEP = 5.0
    const val AZ_COUNT = 72
    const val EL_MIN = -40.0
    const val EL_STEP = 10.0
    const val EL_COUNT = 14
    private const val A_MIN = 0.1
    private val THETA_MIN = Math.toRadians(150.0)

    fun azimuth(index: Int) = -180.0 + index * AZ_STEP
    fun elevation(index: Int) = EL_MIN + index * EL_STEP

    private fun log2(x: Double) = ln(x) / ln(2.0)
  }
}
