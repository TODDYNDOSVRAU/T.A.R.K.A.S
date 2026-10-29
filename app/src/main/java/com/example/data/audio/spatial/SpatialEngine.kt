package com.example.data.audio.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Settings of the spatial engine. Same meaning and defaults as Params in tools/spatial_proto. */
data class SpatialParams(
  /** 0 = the original stereo (delayed, untouched), 1 = only the spatial render. */
  val intensity: Double = 1.0,
  /** Azimuth factor w: how far the stereo image is opened. */
  val width: Double = 1.8,
  /** Highest elevation of the treble, in degrees. */
  val height: Double = 25.0,
  /** D = max(coherence, dominance)^gamma. */
  val gamma: Double = 2.0,
  /** Memory of the spectral averages (~50 ms). */
  val alpha: Double = 0.8,
  /** Keeps the central voice (300 Hz-4 kHz) on the ear plane. */
  val anchor: Boolean = true,
  /** How much of the ambience goes up to the "ceiling". */
  val ambienceHeight: Double = 0.5,
  /** Degrees; turns the scene (head tracking, later). */
  val headYaw: Double = 0.0,
  /** Below this the signal passes untouched. */
  val bassHz: Double = 120.0,
  /** Limiter ceiling (sample peak). */
  val ceilingDb: Double = -1.0,
  /** Keeps the loudness of the original. */
  val loudnessMatch: Boolean = true
)

/** The three listening presets of the prototype. */
enum class SpatialPreset(val key: String, val label: String, val params: SpatialParams, val virtualizerStrength: Int) {
  SUBTLE("sutil", "Sutil", SpatialParams(intensity = 0.7, width = 1.3, height = 12.0, ambienceHeight = 0.3), 450),
  STANDARD("padrao", "Padrão", SpatialParams(), 750),
  WIDE("amplo", "Amplo", SpatialParams(width = 2.4, height = 35.0, ambienceHeight = 0.8), 1000);

  companion object {
    fun fromKey(key: String?): SpatialPreset = entries.firstOrNull { it.key == key } ?: STANDARD
  }
}

/**
 * Tarkas Spatial: turns stereo into a binaural scene for headphones, deciding per frequency bin where
 * each sound goes. Kotlin port of tools/spatial_proto/tarkas_spatial/engine.py (PDF section 8):
 * STFT per bin -> coherence and pan -> direct/ambience -> destination rules -> per-bin HRTF ->
 * overlap-add -> loudness -> limiter.
 *
 * Streaming like the prototype: [process] takes blocks of any size and keeps state; the output is
 * delayed by [LATENCY] samples. Everything is allocated up front; [setParams] builds the per-bin tables
 * on the caller's thread and swaps them in, so the audio thread never allocates.
 */
class SpatialEngine(
  val sampleRate: Int,
  private val hrtf: SphericalHeadHrtf,
  params: SpatialParams = SpatialParams()
) {
  companion object {
    const val WIN = 1024
    const val NFFT = 2048
    const val HOP = 512
    const val BINS = NFFT / 2 + 1
    const val HRTF_DELAY = 64
    const val LIM_CHUNK = 32
    /** Delay between input and output, in samples (plus the hop buffering of a real-time stream). */
    const val LATENCY = WIN - HOP + HRTF_DELAY + LIM_CHUNK
    /** Largest change of intensity per frame: a switch glides in over about 85 ms. */
    const val INTENSITY_STEP = 1.0 / 8
    private val TAN30 = tan(PI / 6)
    private val INV_SQRT2 = 1 / sqrt(2.0)

    fun erbRate(f: Double) = 21.4 * log10(1 + 0.00437 * f)
  }

  /** Per-bin tables that depend on the settings; immutable, swapped whole. */
  private class Config(
    val p: SpatialParams,
    val bassXf: DoubleArray,
    val elev: DoubleArray,
    val ambLL: DoubleArray, val ambLR: DoubleArray, val ambRL: DoubleArray, val ambRR: DoubleArray
  )

  private val fft = Fft(NFFT)
  private val freqs = DoubleArray(BINS) { it * sampleRate.toDouble() / NFFT }
  private val win = DoubleArray(WIN) { 0.5 - 0.5 * cos(2 * PI * it / WIN) }
  private val dryRe = DoubleArray(BINS) { cos(-2 * PI * freqs[it] * HRTF_DELAY / sampleRate) }
  private val dryIm = DoubleArray(BINS) { sin(-2 * PI * freqs[it] * HRTF_DELAY / sampleRate) }
  private val band = IntArray(BINS) { floor(erbRate(freqs[it])).toInt() }
  private val nbands = band.max() + 1
  private val voiceBand = BooleanArray(BINS) { freqs[it] > 300 && freqs[it] < 4000 }

  @Volatile private var config: Config = buildConfig(params)

  fun setParams(p: SpatialParams) {
    config = buildConfig(p)
    targetIntensity = p.intensity
  }

  /**
   * The intensity the engine glides to. Changing it alone needs no new tables, so it is cheap: the
   * player sets it to 0 to switch the effect off without a click.
   */
  @Volatile var targetIntensity: Double = params.intensity

  val params: SpatialParams get() = config.p

  // ---- state ----
  private val pll = DoubleArray(BINS)
  private val prr = DoubleArray(BINS)
  private val plrRe = DoubleArray(BINS)
  private val plrIm = DoubleArray(BINS)
  private val prevMag = DoubleArray(BINS)
  private var transient = 0.0
  private var eDry = 1e-12
  private var eWet = 1e-12
  private var intensity = params.intensity
  private val histL = DoubleArray(WIN)
  private val histR = DoubleArray(WIN)
  private val pendL = DoubleArray(HOP)
  private val pendR = DoubleArray(HOP)
  private var pending = 0
  private val accL = DoubleArray(NFFT)
  private val accR = DoubleArray(NFFT)
  private var limGain = 1.0
  private var limNeed = 1.0
  private val limChunkL = DoubleArray(LIM_CHUNK)
  private val limChunkR = DoubleArray(LIM_CHUNK)
  private var resetStats = false

  // ---- scratch ----
  private val fRe = DoubleArray(NFFT)
  private val fIm = DoubleArray(NFFT)
  private val xlRe = DoubleArray(BINS); private val xlIm = DoubleArray(BINS)
  private val xrRe = DoubleArray(BINS); private val xrIm = DoubleArray(BINS)
  private val dArr = DoubleArray(BINS)
  private val eArr = DoubleArray(BINS)
  private val thetaArr = DoubleArray(BINS)
  private val bandNum = DoubleArray(nbands)
  private val bandDen = DoubleArray(nbands)
  private val wlRe = DoubleArray(BINS); private val wlIm = DoubleArray(BINS)
  private val wrRe = DoubleArray(BINS); private val wrIm = DoubleArray(BINS)
  private val frameL = DoubleArray(HOP)
  private val frameR = DoubleArray(HOP)

  init {
    reset()
  }

  /** Clears every state (a seek, a new track). [startIntensity] lets a switch glide in from the dry sound. */
  fun reset(startIntensity: Double = targetIntensity) {
    pll.fill(0.0); prr.fill(0.0); plrRe.fill(0.0); plrIm.fill(0.0); prevMag.fill(0.0)
    transient = 0.0
    eDry = 1e-12; eWet = 1e-12
    intensity = startIntensity
    histL.fill(0.0); histR.fill(0.0)
    pending = 0
    accL.fill(0.0); accR.fill(0.0)
    limGain = 1.0; limNeed = 1.0
    limChunkL.fill(0.0); limChunkR.fill(0.0)
    resetStats = false
  }

  /** The intensity heard now (it glides towards the settings' one). */
  val currentIntensity: Double get() = intensity

  /** How many output frames a call with [frames] input frames will produce at most. */
  fun outputFramesFor(frames: Int): Int = ((pending + frames) / HOP) * HOP

  /**
   * Processes [frames] interleaved stereo frames of [input] (from [offset]) and writes the output,
   * interleaved, to [output]. Returns the number of frames written (whole hops only).
   */
  fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int {
    var written = 0
    for (i in 0 until frames) {
      var l = input[offset + 2 * i].toDouble()
      var r = input[offset + 2 * i + 1].toDouble()
      // NaN/Inf: silence for that sample, and the statistics start over.
      if (!l.isFinite()) { l = 0.0; resetStats = true }
      if (!r.isFinite()) { r = 0.0; resetStats = true }
      pendL[pending] = l
      pendR[pending] = r
      pending++
      if (pending == HOP) {
        pending = 0
        System.arraycopy(histL, HOP, histL, 0, WIN - HOP)
        System.arraycopy(histR, HOP, histR, 0, WIN - HOP)
        System.arraycopy(pendL, 0, histL, WIN - HOP, HOP)
        System.arraycopy(pendR, 0, histR, WIN - HOP, HOP)
        frame()
        limit(output, written)
        written += HOP
      }
    }
    return written
  }

  private fun frame() {
    val c = config
    val p = c.p
    if (resetStats) {
      pll.fill(0.0); prr.fill(0.0); plrRe.fill(0.0); plrIm.fill(0.0)
      resetStats = false
    }
    // Both channels in one complex FFT: z = l + i·r.
    for (n in 0 until WIN) { fRe[n] = histL[n] * win[n]; fIm[n] = histR[n] * win[n] }
    for (n in WIN until NFFT) { fRe[n] = 0.0; fIm[n] = 0.0 }
    fft.forward(fRe, fIm)
    for (k in 0 until BINS) {
      val m = if (k == 0) 0 else NFFT - k
      val zr = fRe[k]; val zi = fIm[k]; val cr = fRe[m]; val ci = -fIm[m] // conj(Z[N-k])
      xlRe[k] = 0.5 * (zr + cr); xlIm[k] = 0.5 * (zi + ci)
      // (Z - conj) / 2i
      xrRe[k] = 0.5 * (zi - ci); xrIm[k] = -0.5 * (zr - cr)
    }

    val a = p.alpha
    bandNum.fill(0.0); bandDen.fill(0.0)
    var fluxPos = 0.0
    var magSum = 0.0
    for (k in 0 until BINS) {
      val lr = xlRe[k]; val li = xlIm[k]; val rr = xrRe[k]; val ri = xrIm[k]
      val pl = lr * lr + li * li
      val pr = rr * rr + ri * ri
      pll[k] = a * pll[k] + (1 - a) * pl
      prr[k] = a * prr[k] + (1 - a) * pr
      // XL · conj(XR)
      plrRe[k] = a * plrRe[k] + (1 - a) * (lr * rr + li * ri)
      plrIm[k] = a * plrIm[k] + (1 - a) * (li * rr - lr * ri)

      val tot = pll[k] + prr[k]
      val eps = 1e-9 * tot + 1e-20
      val plrAbs = sqrt(plrRe[k] * plrRe[k] + plrIm[k] * plrIm[k])
      val psi = (plrAbs / sqrt(pll[k] * prr[k] + eps)).coerceIn(0.0, 1.0)
      val gl = sqrt(pll[k]); val gr = sqrt(prr[k])
      thetaArr[k] = Math.toDegrees(atan(TAN30 * (gl - gr) / (gl + gr + 1e-20)))
      // A source on one side only: coherence is 0/0 and would count as ambience; dominance fixes it.
      val dom = abs(pll[k] - prr[k]) / (tot + 1e-20)
      val d = max(psi, dom).pow(p.gamma)
      dArr[k] = d
      eArr[k] = tot
      bandNum[band[k]] += d * tot
      bandDen[band[k]] += tot

      val mag = sqrt(pl) + sqrt(pr)
      fluxPos += max(mag - prevMag[k], 0.0)
      magSum += mag
      prevMag[k] = mag
    }
    // Smoothing over ERB bands against "musical noise".
    for (k in 0 until BINS) dArr[k] = 0.5 * dArr[k] + 0.5 * bandNum[band[k]] / (bandDen[band[k]] + 1e-20)

    val flux = fluxPos / (magSum + 1e-20)
    transient = if (flux > 0.3) 1.0 else transient * 0.6
    val tr = transient
    val w = 1 + (p.width - 1) * (1 - 0.7 * tr)

    var sumDry = 0.0
    var sumWet = 0.0
    for (k in 0 until BINS) {
      val theta = thetaArr[k]
      val az = (w * theta).coerceIn(-110.0, 110.0)
      var el = c.elev[k] * (1 - 0.7 * tr)
      if (p.anchor && voiceBand[k] && abs(theta) < 3.0) el = 0.0
      val azWorld = az - p.headYaw

      val d = dArr[k]
      val e = eArr[k]
      // Primary estimate: projection on the pan vector (with polarity).
      val tot = sqrt(e) + 1e-20
      val ul = sqrt(pll[k]) / tot
      val ur = sqrt(prr[k]) / tot * (if (plrRe[k] < 0) -1.0 else 1.0)
      val lr = xlRe[k]; val li = xlIm[k]; val rr = xrRe[k]; val ri = xrIm[k]
      val sRe = ul * lr + ur * rr
      val sIm = ul * li + ur * ri
      val cell = hrtf.cell(azWorld, el) + k
      val hlr = hrtf.lRe(cell).toDouble(); val hli = hrtf.lIm(cell).toDouble()
      val hrr = hrtf.rRe(cell).toDouble(); val hri = hrtf.rIm(cell).toDouble()
      val dg = sqrt(d) * INV_SQRT2
      val dRe = dg * sRe; val dIm = dg * sIm
      val sa = sqrt(max(0.0, 1 - d))
      val alr = sa * lr; val ali = sa * li; val arr = sa * rr; val ari = sa * ri
      val k2 = 2 * k
      // wl = hl·direct + ambLL·AL + ambRL·AR ; wr = hr·direct + ambLR·AL + ambRR·AR
      wlRe[k] = hlr * dRe - hli * dIm +
        c.ambLL[k2] * alr - c.ambLL[k2 + 1] * ali + c.ambRL[k2] * arr - c.ambRL[k2 + 1] * ari
      wlIm[k] = hlr * dIm + hli * dRe +
        c.ambLL[k2] * ali + c.ambLL[k2 + 1] * alr + c.ambRL[k2] * ari + c.ambRL[k2 + 1] * arr
      wrRe[k] = hrr * dRe - hri * dIm +
        c.ambLR[k2] * alr - c.ambLR[k2 + 1] * ali + c.ambRR[k2] * arr - c.ambRR[k2 + 1] * ari
      wrIm[k] = hrr * dIm + hri * dRe +
        c.ambLR[k2] * ali + c.ambLR[k2 + 1] * alr + c.ambRR[k2] * ari + c.ambRR[k2 + 1] * arr

      if (p.loudnessMatch) {
        val bx2 = c.bassXf[k] * c.bassXf[k]
        // |dry delay| = 1, so |DL|² = |XL|².
        sumDry += bx2 * (lr * lr + li * li + rr * rr + ri * ri)
        sumWet += bx2 * (wlRe[k] * wlRe[k] + wlIm[k] * wlIm[k] + wrRe[k] * wrRe[k] + wrIm[k] * wrIm[k])
      }
    }

    var g = 1.0
    if (p.loudnessMatch) {
      val beta = exp(-HOP / (sampleRate * 1.5))
      eDry = beta * eDry + (1 - beta) * sumDry
      eWet = beta * eWet + (1 - beta) * sumWet
      g = sqrt(eDry / (eWet + 1e-20)).coerceIn(0.25, 4.0)
    }

    // The intensity glides to the settings' value, so switching on or off never clicks.
    val target = targetIntensity
    intensity += (target - intensity).coerceIn(-INTENSITY_STEP, INTENSITY_STEP)

    // Y = blend(dry, g·wet, k) per channel, packed for one inverse FFT: W = YL + i·YR.
    for (k in 0 until BINS) {
      val kb = intensity * c.bassXf[k]
      val dlr = xlRe[k] * dryRe[k] - xlIm[k] * dryIm[k]
      val dli = xlRe[k] * dryIm[k] + xlIm[k] * dryRe[k]
      val drr = xrRe[k] * dryRe[k] - xrIm[k] * dryIm[k]
      val dri = xrRe[k] * dryIm[k] + xrIm[k] * dryRe[k]
      blend(dlr, dli, g * wlRe[k], g * wlIm[k], kb)
      val ylr = blendRe; var yli = blendIm
      blend(drr, dri, g * wrRe[k], g * wrIm[k], kb)
      val yrr = blendRe; var yri = blendIm
      if (k == 0 || k == BINS - 1) { yli = 0.0; yri = 0.0 } // irfft ignores them
      // W[k] = YL + i·YR ; W[N-k] = conj(YL) + i·conj(YR)
      fRe[k] = ylr - yri
      fIm[k] = yli + yrr
      if (k != 0 && k != BINS - 1) {
        fRe[NFFT - k] = ylr + yri
        fIm[NFFT - k] = -yli + yrr
      }
    }
    fft.inverse(fRe, fIm)
    val inv = 1.0 / NFFT
    for (n in 0 until NFFT) {
      accL[n] += fRe[n] * inv
      accR[n] += fIm[n] * inv
    }
    System.arraycopy(accL, 0, frameL, 0, HOP)
    System.arraycopy(accR, 0, frameR, 0, HOP)
    System.arraycopy(accL, HOP, accL, 0, NFFT - HOP)
    System.arraycopy(accR, HOP, accR, 0, NFFT - HOP)
    accL.fill(0.0, NFFT - HOP, NFFT)
    accR.fill(0.0, NFFT - HOP, NFFT)
  }

  private var blendRe = 0.0
  private var blendIm = 0.0

  /**
   * Dry/spatial mix that keeps the magnitude: adding the two signals would comb-filter (the HRTFs
   * delay and shift the phase) with holes down to -13 dB. Magnitude and phasor are interpolated
   * separately; k = 0 gives back the dry sound exactly.
   */
  private fun blend(dr: Double, di: Double, wr: Double, wi: Double, k: Double) {
    val md = sqrt(dr * dr + di * di)
    val mw = sqrt(wr * wr + wi * wi)
    val pr = (1 - k) * dr / (md + 1e-20) + k * wr / (mw + 1e-20)
    val pi = (1 - k) * di / (md + 1e-20) + k * wi / (mw + 1e-20)
    val pm = sqrt(pr * pr + pi * pi) + 1e-20
    val m = (1 - k) * md + k * mw
    blendRe = m * pr / pm
    blendIm = m * pi / pm
  }

  /**
   * Limiter with one 32-sample block of lookahead: instant attack, ~50 ms release. Delays the output
   * by [LIM_CHUNK] samples. Writes the frame's HOP samples to [out] at frame [at].
   */
  private fun limit(out: FloatArray, at: Int) {
    val ceil = 10.0.pow(config.p.ceilingDb / 20)
    val rel = 1 - exp(-LIM_CHUNK / (sampleRate * 0.05))
    var chunk = 0
    while (chunk < HOP) {
      var peak = 0.0
      for (i in 0 until LIM_CHUNK) peak = max(peak, max(abs(frameL[chunk + i]), abs(frameR[chunk + i])))
      val need = min(1.0, ceil / (peak + 1e-20))
      // The gain at the end of the delayed block already respects the next one (lookahead).
      val gNew = min(min(limNeed, need), limGain + (1 - limGain) * rel)
      for (i in 0 until LIM_CHUNK) {
        val gain = limGain + (gNew - limGain) * i / LIM_CHUNK
        val o = 2 * (at + chunk + i)
        out[o] = (limChunkL[i] * gain).toFloat()
        out[o + 1] = (limChunkR[i] * gain).toFloat()
      }
      limGain = gNew
      limNeed = need
      System.arraycopy(frameL, chunk, limChunkL, 0, LIM_CHUNK)
      System.arraycopy(frameR, chunk, limChunkR, 0, LIM_CHUNK)
      chunk += LIM_CHUNK
    }
  }

  // ---- settings tables (built off the audio thread) ----

  private fun buildConfig(p: SpatialParams): Config {
    val lo = p.bassHz * 0.8
    val hi = p.bassHz * 1.25
    val bassXf = DoubleArray(BINS)
    val elev = DoubleArray(BINS)
    for (k in 0 until BINS) {
      val f = max(freqs[k], 1.0)
      val x = (ln(f / lo) / ln(hi / lo)).coerceIn(0.0, 1.0)
      bassXf[k] = 0.5 - 0.5 * cos(PI * x)
      elev[k] = p.height / (1 + exp(-1.2 * ln(f / 4000.0) / ln(2.0)))
    }
    val amb = buildAmbience(p.ambienceHeight)
    return Config(p, bassXf, elev, amb[0], amb[1], amb[2], amb[3])
  }

  /**
   * The ambience directions' HRTFs summed with decorrelated phases, precomputed as one filter per
   * (side, ear): ll, lr, rl, rr, each interleaved (re, im). PDF, note of section 8.6.
   */
  private fun buildAmbience(h: Double): Array<DoubleArray> {
    val dirs = arrayOf(doubleArrayOf(90.0, 0.0, 1.0), doubleArrayOf(120.0, 0.0, 1.0), doubleArrayOf(150.0, 0.0, 1.0),
      doubleArrayOf(60.0, 40.0, h), doubleArrayOf(130.0, 40.0, h))
    val norm = sqrt(dirs.sumOf { it[2] * it[2] })
    val rng = java.util.Random(7)
    val kern = DoubleArray(9) { 0.5 - 0.5 * cos(2 * PI * it / 8) }.let { k -> val s = k.sum(); DoubleArray(9) { k[it] / s } }
    val raw = DoubleArray(BINS)
    val phi = DoubleArray(BINS)
    val result = Array(4) { DoubleArray(BINS * 2) }
    for ((side, sgn) in listOf(0 to 1.0, 1 to -1.0)) {
      val accL = result[side * 2]      // side L: ll, lr ; side R: rl, rr
      val accR = result[side * 2 + 1]
      for (dir in dirs) {
        val g = dir[2] / norm
        val (hl, hr) = hrtf.response(sgn * dir[0], dir[1])
        for (k in 0 until BINS) raw[k] = -PI + 2 * PI * rng.nextDouble()
        for (k in 0 until BINS) {
          var s = 0.0
          for (j in 0 until 9) {
            val idx = k + 4 - j
            if (idx in 0 until BINS) s += kern[j] * raw[idx]
          }
          // Bass and low mids stay coherent.
          phi[k] = s * 2.5 * (freqs[k] / 300.0).coerceIn(0.0, 1.0)
        }
        for (k in 0 until BINS) {
          val cr = cos(phi[k]) * g * INV_SQRT2
          val ci = sin(phi[k]) * g * INV_SQRT2
          accL[2 * k] += hl[2 * k] * cr - hl[2 * k + 1] * ci
          accL[2 * k + 1] += hl[2 * k] * ci + hl[2 * k + 1] * cr
          accR[2 * k] += hr[2 * k] * cr - hr[2 * k + 1] * ci
          accR[2 * k + 1] += hr[2 * k] * ci + hr[2 * k + 1] * cr
        }
      }
    }
    return result
  }
}
