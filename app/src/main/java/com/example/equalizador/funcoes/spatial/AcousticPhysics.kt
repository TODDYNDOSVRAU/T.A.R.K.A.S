package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core physical & psychoacoustic formulations for non-rotating, room-anchored virtual speakers:
 * - ISO 9613-1:1993 exact atmospheric absorption (O2 and N2 vibrational relaxation).
 * - Frequency-dependent room boundary reflection absorption.
 * - Extended Woodworth (finite distance rho = r/a, posterior ear offset theta_E) blended with
 *   Kuhn (1977) low-frequency diffraction across transition f0 = 630 Hz.
 * - Real Cepstrum minimum-phase decomposition & phase alignment.
 * - Gunnarsson (2022) high-frequency inter-speaker phase regularization (prevents comb filtering).
 * - Complex 2x2 Hermitian PCA Primary-Ambient Extraction (PAE).
 * - Householder 8-channel Feedback Delay Network (FDN) diffuse room tail.
 */
object AcousticPhysics {
  const val SOUND_SPEED = 343.0
  const val KUHN_TRANSITION_HZ = 630.0
  private const val LN10_OVER_20 = 0.11512925464970229
  private const val INV_SQRT2 = 0.7071067811865476

  /** Anatomical geometry of the listener's head and nominal virtual loudspeaker ring. */
  data class HeadGeometry(
    val headRadiusM: Double = 0.0875,
    val speakerDistanceM: Double = 2.0,
    /** Posterior ear canal azimuth offset (degrees behind coronal plane, EDF/anthropometry). */
    val earAzimuthDeg: Double = 97.5,
    /** Ear canal vertical offset (degrees). */
    val earElevationDeg: Double = -5.0,
    val speedOfSound: Double = SOUND_SPEED
  ) {
    companion object {
      val DEFAULT = HeadGeometry()
    }
  }

  /** Fast lookup table on [0, 2*PI) for audio-thread fractional phase shifts without sin/cos calls. */
  class UnitCircleLut(private val size: Int = 2048) {
    private val mask = size - 1
    private val cosTable = DoubleArray(size + 1) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size + 1) { sin(2.0 * PI * it / size) }
    private val scale = size / (2.0 * PI)
    var re = 1.0; private set
    var im = 0.0; private set

    init {
      require(size and (size - 1) == 0) { "LUT size must be a power of two" }
    }

    /** Writes cos(rad) and sin(rad) into [re] and [im]. */
    fun eval(rad: Double) {
      val turns = rad * scale
      val idx0 = Math.floor(turns).toInt()
      val frac = turns - idx0
      val i = idx0 and mask
      val c0 = cosTable[i]; val c1 = cosTable[i + 1]
      val s0 = sinTable[i]; val s1 = sinTable[i + 1]
      re = c0 + frac * (c1 - c0)
      im = s0 + frac * (s1 - s0)
    }
  }

  /**
   * Exact ISO 9613-1:1993 pure-tone atmospheric attenuation coefficient alpha(f) in dB/m
   * (default studio room conditions: 20 C, 50% RH, 101.325 kPa).
   */
  fun iso9613AlphaDbPerM(
    freqHz: Double,
    tempC: Double = 20.0,
    relativeHumidityPct: Double = 50.0,
    pressureKpa: Double = 101.325
  ): Double {
    if (freqHz <= 0.0) return 0.0
    val t = tempC + 273.15
    val t0 = 293.15
    val t01 = 273.16
    val paPr = pressureKpa / 101.325
    val cSat = -6.8346 * (t01 / t).pow(1.261) + 4.6151
    val psatPr = 10.0.pow(cSat)
    val h = relativeHumidityPct * psatPr / paPr
    val frO = paPr * (24.0 + 4.04e4 * h * (0.02 + h) / (0.391 + h))
    val frN = paPr * (t / t0).pow(-0.5) * (9.0 + 280.0 * h * exp(-4.170 * ((t / t0).pow(-1.0 / 3.0) - 1.0)))
    val f2 = freqHz * freqHz
    val tRel = t / t0
    val cr = 1.84e-11 / paPr * sqrt(tRel)
    val o2 = 0.01275 * exp(-2239.1 / t) / (frO + f2 / frO)
    val n2 = 0.1068 * exp(-3352.0 / t) / (frN + f2 / frN)
    return 8.686 * f2 * (cr + tRel.pow(-2.5) * (o2 + n2))
  }

  /** Pressure amplitude transmission factor after traveling [extraDistanceM] in air (ISO 9613-1). */
  fun airTransmissionGain(freqHz: Double, extraDistanceM: Double): Double {
    if (extraDistanceM <= 0.0 || freqHz <= 0.0) return 1.0
    val alpha = iso9613AlphaDbPerM(freqHz)
    return exp(-LN10_OVER_20 * alpha * extraDistanceM)
  }

  /**
   * Frequency-dependent wall reflection pressure ratio for [bounces] on treated studio walls
   * (porous absorption grows gently toward high frequencies, avoiding harsh metallic reflections).
   */
  fun wallReflectionGain(freqHz: Double, bounces: Int, baseEnergyAbsorption: Double): Double {
    val rMid = sqrt((1.0 - baseEnergyAbsorption).coerceIn(0.05, 0.99))
    // Smooth absorption increase above ~1.8 kHz (typical gypsum + acoustic absorption): ~0.82 at 10 kHz.
    val hf = 1.0 / (1.0 + (freqHz / 4500.0).pow(1.4))
    val rFreq = rMid * (0.78 + 0.22 * hf)
    return rFreq.pow(bounces)
  }

  /** Dot product between source unit direction (azDeg, elDeg) and ear unit vector. */
  fun sourceEarCos(azDeg: Double, elDeg: Double, isLeftEar: Boolean, geo: HeadGeometry): Double {
    val az = Math.toRadians(azDeg); val el = Math.toRadians(elDeg)
    val ux = cos(el) * cos(az); val uy = cos(el) * sin(az); val uz = sin(el)
    val eAz = Math.toRadians(if (isLeftEar) geo.earAzimuthDeg else -geo.earAzimuthDeg)
    val eEl = Math.toRadians(geo.earElevationDeg)
    val ex = cos(eEl) * cos(eAz); val ey = cos(eEl) * sin(eAz); val ez = sin(eEl)
    return (ux * ex + uy * ey + uz * ez).coerceIn(-1.0, 1.0)
  }

  /**
   * Extended Woodworth high-frequency arrival time offset (seconds, relative to sphere center) for a
   * source at finite distance [HeadGeometry.speakerDistanceM]. Continuous C1 across the tangent horizon.
   */
  fun extendedWoodworthDelaySec(dot: Double, geo: HeadGeometry): Double {
    val a = geo.headRadiusM
    val c = geo.speedOfSound
    val rho = max(geo.speakerDistanceM / a, 1.0001)
    val psi = acos(dot.coerceIn(-1.0, 1.0))
    val gamma = acos(1.0 / rho)
    val path = if (psi <= gamma) {
      a * sqrt(max(0.0, 1.0 + rho * rho - 2.0 * rho * dot))
    } else {
      a * (sqrt(max(0.0, rho * rho - 1.0)) + (psi - gamma))
    }
    return (path - rho * a) / c
  }

  /** Kuhn (1977) low-frequency diffractive arrival offset (seconds): yields (3a/c)*sin(theta) ITD as f -> 0. */
  fun kuhnLowFreqDelaySec(dot: Double, geo: HeadGeometry): Double =
    -1.5 * (geo.headRadiusM / geo.speedOfSound) * dot.coerceIn(-1.0, 1.0)

  /** Low-frequency group delay already contributed by Brown-Duda's 1st-order head-shadow filter. */
  fun brownDudaShadowGroupDelaySec(dot: Double, geo: HeadGeometry, aMin: Double = 0.1, thetaMinRad: Double = Math.toRadians(150.0)): Double {
    val theta = acos(dot.coerceIn(-1.0, 1.0))
    val alpha = (1.0 + aMin / 2.0) + (1.0 - aMin / 2.0) * cos(theta / thetaMinRad * PI)
    val w0 = geo.speedOfSound / geo.headRadiusM
    return (1.0 - alpha) / (2.0 * w0)
  }

  /**
   * Net excess phase delay offset tau(f) added onto minimum-phase (head shadow + pinna) so total
   * binaural ITD transitions cleanly from Kuhn LF (630 Hz) to Extended Woodworth HF at distance r.
   */
  fun kuhnWoodworthExcessDelaySec(freqHz: Double, dot: Double, geo: HeadGeometry): Double {
    val tauHf = extendedWoodworthDelaySec(dot, geo)
    val tauLfExcess = kuhnLowFreqDelaySec(dot, geo) - brownDudaShadowGroupDelaySec(dot, geo)
    return (KUHN_TRANSITION_HZ * tauLfExcess + freqHz * tauHf) / (KUHN_TRANSITION_HZ + freqHz)
  }

  /**
   * Reconstructs the strictly causal minimum-phase spectrum in-place via Real Cepstrum folding
   * (PDF §2.2.E): keeps [magHalf] magnitude identical and computes its minimum-phase argument.
   */
  fun minimumPhaseFromMagnitude(
    magHalf: DoubleArray,
    fft: Fft,
    re: DoubleArray,
    im: DoubleArray,
    outRe: DoubleArray,
    outIm: DoubleArray
  ) {
    for (k in 0 until BINS) {
      val m = ln(max(magHalf[k], 1e-6))
      re[k] = m; im[k] = 0.0
      if (k in 1 until BINS - 1) { re[NFFT - k] = m; im[NFFT - k] = 0.0 }
    }
    fft.inverse(re, im)
    val inv = 1.0 / NFFT
    re[0] *= inv; im[0] = 0.0
    re[NFFT / 2] *= inv; im[NFFT / 2] = 0.0
    for (n in 1 until NFFT / 2) {
      re[n] *= 2.0 * inv; im[n] = 0.0
      re[NFFT - n] = 0.0; im[NFFT - n] = 0.0
    }
    fft.forward(re, im)
    for (k in 0 until BINS) {
      val e = exp(re[k].coerceIn(-18.0, 18.0))
      outRe[k] = e * cos(im[k])
      outIm[k] = e * sin(im[k])
    }
  }

  /**
   * 2x2 Hermitian PCA Primary-Ambient Decomposition result (reused per bin with zero allocations).
   */
  class PcaResult {
    var pReL = 0.0; var pImL = 0.0
    var pReR = 0.0; var pImR = 0.0
    var aReL = 0.0; var aImL = 0.0
    var aReR = 0.0; var aImR = 0.0
    /** Primary power ratio (lambda1 - lambda2) / (lambda1 + lambda2) in [0, 1]. */
    var ppr = 0.0
    /** Unit dominant eigenvector [vL, vR]. */
    var vReL = INV_SQRT2; var vImL = 0.0
    var vReR = INV_SQRT2; var vImR = 0.0
    /** Signed in-phase / out-of-phase correlation in [-1, 1]. */
    var rhoSigned = 0.0
    /** Magnitude of complex coherence in [0, 1]. */
    var coherence = 0.0
  }

  /**
   * Exact analytical 2x2 Hermitian covariance PCA (PDF §3.2) separating rank-1 coherent directional
   * source P from strictly isotropic uncorrelated ambient residual A = X - P.
   */
  fun decomposePrimaryAmbient(
    xReL: Double, xImL: Double, xReR: Double, xImR: Double,
    pll: Double, prr: Double, plrRe: Double, plrIm: Double,
    out: PcaResult
  ) {
    val tot = pll + prr
    val cAbs = sqrt(plrRe * plrRe + plrIm * plrIm)
    val gl = sqrt(pll); val gr = sqrt(prr)
    val denomCorr = gl * gr + 1e-12 * tot + 1e-20
    out.rhoSigned = (plrRe / denomCorr).coerceIn(-1.0, 1.0)
    out.coherence = (cAbs / denomCorr).coerceIn(0.0, 1.0)

    val delta = pll - prr
    val d = sqrt(delta * delta + 4.0 * cAbs * cAbs)
    val l1 = 0.5 * (tot + d)
    val l2 = max(0.0, 0.5 * (tot - d))
    out.ppr = (d / (tot + 1e-20)).coerceIn(0.0, 1.0)

    // Dominant normalized eigenvector v1 of R = [[pll, plr], [conj(plr), prr]].
    val vLRe: Double; val vLIm: Double; val vRRe: Double; val vRIm: Double
    if (cAbs < 1e-14 * tot + 1e-24) {
      if (delta >= 0.0) {
        vLRe = 1.0; vLIm = 0.0; vRRe = 0.0; vRIm = 0.0
      } else {
        vLRe = 0.0; vLIm = 0.0; vRRe = 1.0; vRIm = 0.0
      }
    } else if (delta >= 0.0) {
      val a = delta + d
      val bRe = 2.0 * plrRe
      val bIm = -2.0 * plrIm
      val nrm = sqrt(a * a + bRe * bRe + bIm * bIm) + 1e-20
      vLRe = a / nrm; vLIm = 0.0
      vRRe = bRe / nrm; vRIm = bIm / nrm
    } else {
      val aRe = 2.0 * plrRe
      val aIm = 2.0 * plrIm
      val b = -delta + d
      val nrm = sqrt(aRe * aRe + aIm * aIm + b * b) + 1e-20
      vLRe = aRe / nrm; vLIm = aIm / nrm
      vRRe = b / nrm; vRIm = 0.0
    }
    out.vReL = vLRe; out.vImL = vLIm
    out.vReR = vRRe; out.vImR = vRIm

    // Wiener/PCA isotropic-residual weight w = 1 - sqrt(lambda2 / lambda1) so E[A A^H] -> lambda2 * I.
    val w = if (l1 > 1e-24) (1.0 - sqrt(l2 / l1)).coerceIn(0.0, 1.0) else 0.0
    // Scalar projection s = v1^H X = conj(vL)*XL + conj(vR)*XR
    val sRe = vLRe * xReL + vLIm * xImL + vRRe * xReR + vRIm * xImR
    val sIm = vLRe * xImL - vLIm * xReL + vRRe * xImR - vRIm * xReR
    val wsRe = w * sRe; val wsIm = w * sIm
    out.pReL = vLRe * wsRe - vLIm * wsIm
    out.pImL = vLRe * wsIm + vLIm * wsRe
    out.pReR = vRRe * wsRe - vRIm * wsIm
    out.pImR = vRRe * wsIm + vRIm * wsRe
    out.aReL = xReL - out.pReL; out.aImL = xImL - out.pImL
    out.aReR = xReR - out.pReR; out.aImR = xImR - out.pImR
  }

  /**
   * Series of Schroeder all-passes that smear sparse echoes into a smooth diffuse field.
   * Allocation-free after construction.
   */
  class Diffuser(sampleRate: Int, delaysMs: DoubleArray, var g: Double) {
    private val len = IntArray(delaysMs.size) { (delaysMs[it] * sampleRate / 1000.0).roundToInt().coerceAtLeast(2) }
    private val buf = Array(len.size) { DoubleArray(len[it]) }
    private val pos = IntArray(len.size)

    fun process(x: Double): Double {
      var v = x
      for (s in len.indices) {
        val b = buf[s]
        val d = b[pos[s]]
        val w = v + g * d
        b[pos[s]] = w
        pos[s] = if (pos[s] + 1 == len[s]) 0 else pos[s] + 1
        v = d - g * w
      }
      return v
    }

    fun clear() {
      buf.forEach { it.fill(0.0) }
      pos.fill(0)
    }
  }

  /**
   * Shared 8-channel Householder Feedback Delay Network (FDN) diffuse reverberation tail with
   * ISO 9613-1 high-frequency damping, configurable RT60, and stereo output decorrelation.
   */
  class HouseholderTail(
    private val sampleRate: Int,
    rt60Sec: Double = 0.38,
    diffusion: Double = 0.6,
    private val dampHz: Double = 4500.0,
    private val outLpHz: Double = 6500.0,
    private val tailGain: Double = 2.7
  ) {
    private val fdnCount = 8
    private val fdnLen = IntArray(fdnCount) { (FDN_MS[it] * sampleRate / 1000.0).roundToInt() }
    private val fdnMask = IntArray(fdnCount) { nextPow2(fdnLen[it] + 1) - 1 }
    private val fdnBuf = Array(fdnCount) { DoubleArray(fdnMask[it] + 1) }
    private val fdnGain = DoubleArray(fdnCount) { gainFor(it, rt60Sec) }
    private val fdnDamp = 1.0 - exp(-2.0 * PI * dampHz / sampleRate)
    private val fdnLp = DoubleArray(fdnCount)
    private val fdnV = DoubleArray(fdnCount)
    private val fdnIn = doubleArrayOf(1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0)
    private val fdnOutL = doubleArrayOf(1.0, 1.0, 1.0, 1.0, -1.0, -1.0, -1.0, -1.0)
    private val fdnOutR = doubleArrayOf(1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0)
    private var fdnPos = 0
    private val tailLpCoef = 1.0 - exp(-2.0 * PI * outLpHz / sampleRate)
    private var tailLpL = 0.0
    private var tailLpR = 0.0
    private val tailDiffuser = Diffuser(sampleRate, doubleArrayOf(5.3, 7.9, 11.3, 13.7), (diffusion + 0.02).coerceAtMost(0.9))
    private var lastDecay = rt60Sec
    private var lastDiffusion = diffusion

    var outLeft = 0.0; private set
    var outRight = 0.0; private set

    private fun gainFor(j: Int, decayS: Double): Double {
      val rt = decayS.coerceAtLeast(0.05)
      // Combine RT60 geometric decay with ISO 9613-1 medium absorption per round-trip (~343 m/s).
      val roundTripM = fdnLen[j].toDouble() / sampleRate * SOUND_SPEED
      val isoMid = airTransmissionGain(4000.0, roundTripM)
      return 10.0.pow(-3.0 * fdnLen[j] / sampleRate / rt) * isoMid
    }

    fun setParams(decayS: Double, diffusion: Double) {
      if (decayS != lastDecay) {
        lastDecay = decayS
        for (j in 0 until fdnCount) fdnGain[j] = gainFor(j, decayS)
      }
      if (diffusion != lastDiffusion) {
        lastDiffusion = diffusion
        tailDiffuser.g = (diffusion.coerceIn(0.0, 0.9) + 0.02).coerceAtMost(0.9)
      }
    }

    fun step(inputMid: Double) {
      val mid = tailDiffuser.process(inputMid)
      val norm = 1.0 / sqrt(fdnCount.toDouble())
      val inv = 1.0 / fdnCount
      var sum = 0.0
      for (j in 0 until fdnCount) {
        val raw = fdnBuf[j][(fdnPos - fdnLen[j]) and fdnMask[j]]
        fdnLp[j] += fdnDamp * (raw - fdnLp[j])
        val v = fdnLp[j] * fdnGain[j]
        fdnV[j] = v
        sum += v
      }
      val feedback = 2.0 * sum * inv
      var tl = 0.0; var tr = 0.0
      for (j in 0 until fdnCount) {
        fdnBuf[j][fdnPos and fdnMask[j]] = fdnV[j] - feedback + mid * fdnIn[j] * norm
        tl += fdnV[j] * fdnOutL[j]
        tr += fdnV[j] * fdnOutR[j]
      }
      fdnPos++
      tailLpL += tailLpCoef * (tl * norm * tailGain - tailLpL)
      tailLpR += tailLpCoef * (tr * norm * tailGain - tailLpR)
      outLeft = tailLpL
      outRight = tailLpR
    }

    fun clear() {
      fdnBuf.forEach { it.fill(0.0) }
      fdnLp.fill(0.0)
      tailDiffuser.clear()
      tailLpL = 0.0; tailLpR = 0.0
      outLeft = 0.0; outRight = 0.0
      fdnPos = 0
    }

    companion object {
      private val FDN_MS = doubleArrayOf(13.1, 17.3, 21.7, 26.9, 31.3, 37.9, 43.1, 49.9)
      fun nextPow2(n: Int): Int { var p = 1; while (p < n) p = p shl 1; return p }
    }
  }
}
