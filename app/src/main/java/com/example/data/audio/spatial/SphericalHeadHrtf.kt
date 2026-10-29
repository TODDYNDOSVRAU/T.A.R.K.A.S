package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A parametric spherical-head HRTF (Brown & Duda, 1998) with an outer-ear notch and a rear shadow,
 * sampled on a grid of 5° azimuth × 10° elevation (-40° to 90°) and diffuse-field normalised.
 * Port of tools/spatial_proto/tarkas_spatial/hrtf.py; a measured set (SOFA) can replace it later
 * without changing the engine.
 *
 * Coordinates as ITU-R BS.2051: x forward, y left, z up; positive azimuth to the left.
 * Building the table takes a moment (about a million complex values per ear): do it off the audio thread.
 */
class SphericalHeadHrtf(
  val sampleRate: Int,
  val nfft: Int = SpatialEngine.NFFT,
  val delaySamples: Int = SpatialEngine.HRTF_DELAY,
  private val headRadius: Double = 0.0875,
  private val speedOfSound: Double = 343.0
) {
  val bins = nfft / 2 + 1
  private val freqs = DoubleArray(bins) { it * sampleRate.toDouble() / nfft }

  /** Grid tables, per ear, real and imaginary parts: index ((az * EL_COUNT + el) * bins + k). */
  private val tableLRe: FloatArray
  private val tableLIm: FloatArray
  private val tableRRe: FloatArray
  private val tableRIm: FloatArray
  private val norm = DoubleArray(bins)

  // Per-bin pieces shared by every direction.
  private val omega = DoubleArray(bins) { 2 * PI * freqs[it] }
  private val log2f = DoubleArray(bins) { log2(max(freqs[it], 1.0)) }
  private val rearShelfCurve = DoubleArray(bins) { 1 / (1 + exp(-4 * (log2f[it] - log2(3000.0)))) }
  private val delayRe = DoubleArray(bins) { cos(-2 * PI * freqs[it] * delaySamples / sampleRate) }
  private val delayIm = DoubleArray(bins) { sin(-2 * PI * freqs[it] * delaySamples / sampleRate) }

  init {
    val cells = AZ_COUNT * EL_COUNT
    tableLRe = FloatArray(cells * bins); tableLIm = FloatArray(cells * bins)
    tableRRe = FloatArray(cells * bins); tableRIm = FloatArray(cells * bins)
    val power = DoubleArray(bins)
    var weightSum = 0.0
    val hl = DoubleArray(bins * 2)
    val hr = DoubleArray(bins * 2)
    for (ia in 0 until AZ_COUNT) {
      for (ie in 0 until EL_COUNT) {
        val az = azimuth(ia)
        val el = elevation(ie)
        evaluate(az, el, hl, hr)
        val w = cos(Math.toRadians(el))
        weightSum += w
        val base = (ia * EL_COUNT + ie) * bins
        for (k in 0 until bins) {
          val lr = hl[2 * k]; val li = hl[2 * k + 1]
          val rr = hr[2 * k]; val ri = hr[2 * k + 1]
          power[k] += w * (lr * lr + li * li + rr * rr + ri * ri) / 2
          tableLRe[base + k] = lr.toFloat(); tableLIm[base + k] = li.toFloat()
          tableRRe[base + k] = rr.toFloat(); tableRIm[base + k] = ri.toFloat()
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
      }
    }
  }

  /** Unnormalised HRTF for one direction, into interleaved (re, im) arrays of 2·bins. */
  private fun evaluate(azDeg: Double, elDeg: Double, outL: DoubleArray, outR: DoubleArray) {
    val az = Math.toRadians(azDeg)
    val el = Math.toRadians(elDeg)
    val uy = cos(el) * sin(az)
    // Left ear on +y, right ear on -y.
    val thetaL = acos(uy.coerceIn(-1.0, 1.0))
    val thetaR = acos((-uy).coerceIn(-1.0, 1.0))
    // Main pinna notch rises with elevation: ~6 kHz at -40° to ~11 kHz at +90°.
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
      ear(thetaL, k, outL, cr, ci)
      ear(thetaR, k, outR, cr, ci)
    }
  }

  /** Head shadow and arrival time for an ear at angle [theta] from the source, times the common factor. */
  private fun ear(theta: Double, k: Int, out: DoubleArray, cr: Double, ci: Double) {
    val w = omega[k]
    val w0 = speedOfSound / headRadius
    val alpha = (1 + A_MIN / 2) + (1 - A_MIN / 2) * cos(theta / THETA_MIN * PI)
    val q = w / (2 * w0)
    // (1 + iαq) / (1 + iq)
    val den = 1 + q * q
    val sr = (1 + alpha * q * q) / den
    val si = (alpha * q - q) / den
    // Frontal sound arrives at t = 0, aligned with the dry path; the minimum -a/c is covered by the delay.
    val t = if (theta < PI / 2) -headRadius / speedOfSound * cos(theta) else headRadius / speedOfSound * (theta - PI / 2)
    val pr = cos(w * t)
    val pi = -sin(w * t)
    val er = sr * pr - si * pi
    val ei = sr * pi + si * pr
    out[2 * k] = er * cr - ei * ci
    out[2 * k + 1] = er * ci + ei * cr
  }

  /** Normalised HRTF (same scale as the grid) for an exact direction: (re, im) interleaved, per ear. */
  fun response(azDeg: Double, elDeg: Double): Pair<DoubleArray, DoubleArray> {
    val hl = DoubleArray(bins * 2)
    val hr = DoubleArray(bins * 2)
    evaluate(azDeg, elDeg, hl, hr)
    for (k in 0 until bins) {
      hl[2 * k] *= norm[k]; hl[2 * k + 1] *= norm[k]
      hr[2 * k] *= norm[k]; hr[2 * k + 1] *= norm[k]
    }
    return hl to hr
  }

  /** Table offset of the grid cell nearest to a direction (nearest neighbour, as the prototype). */
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
