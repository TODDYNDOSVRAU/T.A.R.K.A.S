package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.HRTF_DELAY
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 5.1 speakers as the listener hears them in headphones: one filter per (channel, ear), already
 * normalised.
 *
 * Each speaker sits at fixed azimuths (L +30°, R -30°, C 0°, Ls +110°, Rs -110°, LFE omnidirectional).
 * - [production] keeps exact compatibility with tools/spatial_proto and ref_filters_*.bin checks.
 * - [studio] enables full PDF physics: L/R symmetrical KU100 calibration, Gunnarsson HF phase
 *   regularization, Grathwohl multi-speaker pinna-notch stack relief, and [RoomParams.STUDIO]
 *   (ISO 9613-1 air absorption + porous wall HF absorption + fractional delays).
 * Also exposes [roomRe]/[roomIm] so [UpmixVirtualizer] and [SurroundVirtualizer] can rotate the
 * direct speaker paths dynamically under live head tracking while keeping the diffuse room anchored.
 */
class SurroundFilters(
  val sampleRate: Int, hrtf: HrtfSource, val room: RoomParams = RoomParams.OFF,
  /** 6 (5.1: surrounds at 110°) or 8 (7.1: sides at 90°, backs at 150°). */
  val channels: Int = 6
) {
  init { require(channels == 6 || channels == 8) { "channels $channels" } }

  /** Azimuth of each channel in degrees (NaN = the LFE, which has no direction). */
  val azimuths: DoubleArray = if (channels == 8) {
    doubleArrayOf(FRONT_AZ, -FRONT_AZ, 0.0, Double.NaN, SIDE_AZ, -SIDE_AZ, BACK_AZ, -BACK_AZ)
  } else {
    doubleArrayOf(FRONT_AZ, -FRONT_AZ, 0.0, Double.NaN, SURROUND_AZ, -SURROUND_AZ)
  }

  /** Combined direct + room filter, index `channel * 2 + ear` (ear 0 = left), real and imaginary parts per bin. */
  val re = Array(channels * 2) { DoubleArray(BINS) }
  val im = Array(channels * 2) { DoubleArray(BINS) }

  /** Normalized room-reflection-only component (for additive composition with head-tracked direct HRTFs). */
  internal val roomRe = Array(channels * 2) { DoubleArray(BINS) }
  internal val roomIm = Array(channels * 2) { DoubleArray(BINS) }
  internal val speakerScale = DoubleArray(channels) { 1.0 }

  init {
    val fft = Fft(NFFT)
    val first = Math.ceil(100.0 * NFFT / sampleRate).toInt()
    val last = Math.floor(10000.0 * NFFT / sampleRate).toInt().coerceAtMost(BINS - 1)

    val rawDirect = Array(channels * 2) { DoubleArray(BINS * 2) }
    val rawRefl = Array(channels * 2) { DoubleArray(BINS * 2) }

    for (ch in 0 until channels) {
      if (azimuths[ch].isNaN()) {
        // LFE: both ears, common delay only.
        for (ear in 0..1) for (k in 0 until BINS) {
          val phase = -2 * PI * k * sampleRate.toDouble() / NFFT * HRTF_DELAY / sampleRate
          re[ch * 2 + ear][k] = INV_SQRT2 * cos(phase)
          im[ch * 2 + ear][k] = INV_SQRT2 * sin(phase)
        }
        continue
      }
      val (dl, dr) = hrtf.response(azimuths[ch], 0.0)
      System.arraycopy(dl, 0, rawDirect[ch * 2], 0, BINS * 2)
      System.arraycopy(dr, 0, rawDirect[ch * 2 + 1], 0, BINS * 2)

      if (room.enabled) {
        val rl = DoubleArray(BINS * 2)
        val rr = DoubleArray(BINS * 2)
        SurroundRoom.addReflections(rl, rr, SurroundRoom.paths(azimuths[ch], 0.0, room, sampleRate), hrtf, sampleRate, room)
        System.arraycopy(rl, 0, rawRefl[ch * 2], 0, BINS * 2)
        System.arraycopy(rr, 0, rawRefl[ch * 2 + 1], 0, BINS * 2)
      }
    }

    if (room.exactPhysics) {
      // 1. L/R geometric symmetry on C and symmetric speaker pairs (eliminates asymmetric dummy-head mic jitter).
      symmetrizeChannelPair(rawDirect[CH_C * 2], rawDirect[CH_C * 2 + 1], rawDirect[CH_C * 2], rawDirect[CH_C * 2 + 1])
      symmetrizeChannelPair(rawRefl[CH_C * 2], rawRefl[CH_C * 2 + 1], rawRefl[CH_C * 2], rawRefl[CH_C * 2 + 1])
      symmetrizeSymmetricSpeakers(rawDirect, CH_L, CH_R)
      symmetrizeSymmetricSpeakers(rawRefl, CH_L, CH_R)
      symmetrizeSymmetricSpeakers(rawDirect, CH_LS, CH_RS)
      symmetrizeSymmetricSpeakers(rawRefl, CH_LS, CH_RS)
      if (channels == 8) {
        symmetrizeSymmetricSpeakers(rawDirect, CH_SBL, CH_SBR)
        symmetrizeSymmetricSpeakers(rawRefl, CH_SBL, CH_SBR)
      }

      // 2. Grathwohl multi-speaker notch stack protection & Gunnarsson HF phase smoothness across (L, C, R):
      applyMultiSpeakerSpectralAndPhaseProtection(sampleRate, rawDirect, fft)
    }

    for (ch in 0 until channels) {
      if (azimuths[ch].isNaN()) continue
      val hl = DoubleArray(BINS * 2) { rawDirect[ch * 2][it] + rawRefl[ch * 2][it] }
      val hr = DoubleArray(BINS * 2) { rawDirect[ch * 2 + 1][it] + rawRefl[ch * 2 + 1][it] }
      SurroundRoom.fit(hl, fft); SurroundRoom.fit(hr, fft)
      var power = 0.0
      for (k in first..last) {
        power += hl[2 * k] * hl[2 * k] + hl[2 * k + 1] * hl[2 * k + 1] + hr[2 * k] * hr[2 * k] + hr[2 * k + 1] * hr[2 * k + 1]
      }
      val g = 1.0 / sqrt(power / (last - first + 1))
      speakerScale[ch] = g
      for (k in 0 until BINS) {
        re[ch * 2][k] = g * hl[2 * k]; im[ch * 2][k] = g * hl[2 * k + 1]
        re[ch * 2 + 1][k] = g * hr[2 * k]; im[ch * 2 + 1][k] = g * hr[2 * k + 1]
      }
      if (room.enabled) {
        val rlFit = rawRefl[ch * 2].copyOf(); val rrFit = rawRefl[ch * 2 + 1].copyOf()
        SurroundRoom.fit(rlFit, fft); SurroundRoom.fit(rrFit, fft)
        for (k in 0 until BINS) {
          roomRe[ch * 2][k] = g * rlFit[2 * k]; roomIm[ch * 2][k] = g * rlFit[2 * k + 1]
          roomRe[ch * 2 + 1][k] = g * rrFit[2 * k]; roomIm[ch * 2 + 1][k] = g * rrFit[2 * k + 1]
        }
      }
    }
  }

  private fun symmetrizeChannelPair(lA: DoubleArray, rA: DoubleArray, lB: DoubleArray, rB: DoubleArray) {
    for (i in 0 until BINS * 2) {
      val avg = 0.5 * (lA[i] + rB[i])
      val avgOpp = 0.5 * (rA[i] + lB[i])
      lA[i] = avg; rB[i] = avg
      rA[i] = avgOpp; lB[i] = avgOpp
    }
  }

  private fun symmetrizeSymmetricSpeakers(arr: Array<DoubleArray>, leftCh: Int, rightCh: Int) {
    symmetrizeChannelPair(arr[leftCh * 2], arr[leftCh * 2 + 1], arr[rightCh * 2], arr[rightCh * 2 + 1])
  }

  /**
   * Implements PDF §3.3 (Gunnarsson 2022 + Grathwohl multi-speaker pinna notch control):
   * prevents -10 to -15 dB notch accumulation when L, C, R play correlated music simultaneously.
   */
  private fun applyMultiSpeakerSpectralAndPhaseProtection(sampleRate: Int, rawDirect: Array<DoubleArray>, fft: Fft) {
    // Compute combined frontal reference level across 200-2000 Hz.
    val f200 = Math.ceil(200.0 * NFFT / sampleRate).toInt()
    val f2k = Math.floor(2000.0 * NFFT / sampleRate).toInt().coerceAtMost(BINS - 1)
    var refSum = 0.0
    var refCount = 0
    for (k in f200..f2k) {
      for (ch in intArrayOf(CH_L, CH_R, CH_C)) {
        val h = rawDirect[ch * 2]
        refSum += h[2 * k] * h[2 * k] + h[2 * k + 1] * h[2 * k + 1]
        refCount++
      }
    }
    val refRms = sqrt(refSum / max(1, refCount)) + 1e-12

    // Detect deep multi-speaker frontal dips in 5..13 kHz and lift gently so pinna cues remain natural without hollowness.
    val relief = DoubleArray(BINS) { 1.0 }
    for (k in 0 until BINS) {
      val f = k * sampleRate.toDouble() / NFFT
      if (f in 4500.0..14000.0) {
        // Phantom sum L(+30) + R(-30) and C(0) power at left ear:
        val phRe = (rawDirect[CH_L * 2][2 * k] + rawDirect[CH_R * 2][2 * k]) * INV_SQRT2
        val phIm = (rawDirect[CH_L * 2][2 * k + 1] + rawDirect[CH_R * 2][2 * k + 1]) * INV_SQRT2
        val mag = 0.5 * sqrt(phRe * phRe + phIm * phIm) +
          0.5 * sqrt(rawDirect[CH_C * 2][2 * k] * rawDirect[CH_C * 2][2 * k] + rawDirect[CH_C * 2][2 * k + 1] * rawDirect[CH_C * 2][2 * k + 1])
        val ratio = mag / refRms
        if (ratio < 0.78) {
          // Smooth 55% partial restoration capped at +3.2 dB (1.445x) so directional notch stays audible but never collapses.
          val targetRatio = ratio + 0.55 * (0.78 - ratio)
          val boost = (targetRatio / max(ratio, 0.25)).coerceIn(1.0, 1.445)
          val bandEdge = when {
            f < 5500.0 -> (f - 4500.0) / 1000.0
            f > 12500.0 -> (14000.0 - f) / 1500.0
            else -> 1.0
          }.coerceIn(0.0, 1.0)
          relief[k] = 1.0 + (boost - 1.0) * (0.5 - 0.5 * cos(PI * bandEdge))
        }
      }
    }

    val mag = DoubleArray(BINS)
    val cRe = DoubleArray(NFFT); val cIm = DoubleArray(NFFT)
    val mpRe = DoubleArray(BINS); val mpIm = DoubleArray(BINS)
    val directional = if (channels == 8) intArrayOf(CH_L, CH_R, CH_C, CH_LS, CH_RS, CH_SBL, CH_SBR)
    else intArrayOf(CH_L, CH_R, CH_C, CH_LS, CH_RS)
    for (ch in directional) {
      for (ear in 0..1) {
        val h = rawDirect[ch * 2 + ear]
        for (k in 0 until BINS) {
          val m = sqrt(h[2 * k] * h[2 * k] + h[2 * k + 1] * h[2 * k + 1]) * relief[k]
          mag[k] = max(m, 1e-6)
          h[2 * k] *= relief[k]
          h[2 * k + 1] *= relief[k]
        }
        // Gunnarsson HF phase regularization: above 2.0 kHz (above human IPD phase limit ~1.3 kHz), blend noisy raw
        // all-pass reflections cleanly toward minimum-phase + aligned pure arrival while keeping < 1.8 kHz phase exact.
        AcousticPhysics.minimumPhaseFromMagnitude(mag, fft, cRe, cIm, mpRe, mpIm)
        // Estimate pure excess phase group delay relative to HRTF_DELAY from 200-1200 Hz correlation.
        val azSpeaker = azimuths[ch]
        val dot = AcousticPhysics.sourceEarCos(azSpeaker, 0.0, ear == 0, AcousticPhysics.HeadGeometry.DEFAULT)
        val pureDelay = AcousticPhysics.extendedWoodworthDelaySec(dot, AcousticPhysics.HeadGeometry.DEFAULT) +
          HRTF_DELAY.toDouble() / sampleRate
        for (k in 0 until BINS) {
          val f = k * sampleRate.toDouble() / NFFT
          if (f <= 1800.0) continue
          val blend = ((ln(f / 1800.0) / ln(5000.0 / 1800.0))).coerceIn(0.0, 0.85)
          val ph = -2 * PI * f * pureDelay
          val dRe = cos(ph); val dIm = sin(ph)
          val gRe = mpRe[k] * dRe - mpIm[k] * dIm
          val gIm = mpRe[k] * dIm + mpIm[k] * dRe
          // Interpolate phasor preserving target magnitude (comb-free phasor blend!).
          val r0 = h[2 * k]; val i0 = h[2 * k + 1]
          val m0 = sqrt(r0 * r0 + i0 * i0) + 1e-20
          val mg = sqrt(gRe * gRe + gIm * gIm) + 1e-20
          val pr = (1.0 - blend) * r0 / m0 + blend * gRe / mg
          val pi = (1.0 - blend) * i0 / m0 + blend * gIm / mg
          val pn = sqrt(pr * pr + pi * pi) + 1e-20
          h[2 * k] = m0 * pr / pn
          h[2 * k + 1] = m0 * pi / pn
        }
      }
    }
  }

  companion object {
    /**
     * The filters tested against tools/spatial_proto / ref_filters_*.bin.
     */
    fun production(
      sampleRate: Int, spherical: HrtfSource, open: ((String) -> java.io.InputStream?)?,
      onProblem: (String) -> Unit = {}
    ): SurroundFilters {
      val measured = if (open == null) null else MeasuredHrtf.load(sampleRate, open, onProblem)
      return SurroundFilters(sampleRate, measured ?: spherical, RoomParams.APPROVED)
    }

    /**
     * High-precision studio filters for live playback: KU100 measured HRTF (when available) or
     * exact diffraction spherical HRTF, with L/R symmetry, Gunnarsson phase regularization,
     * Grathwohl multi-speaker notch protection, and [RoomParams.STUDIO] (ISO 9613-1 + fractional delays).
     */
    fun studio(
      sampleRate: Int, accurateSpherical: HrtfSource, open: ((String) -> java.io.InputStream?)?,
      onProblem: (String) -> Unit = {},
      channels: Int = 6
    ): SurroundFilters {
      val measured = if (open == null) null else MeasuredHrtf.load(sampleRate, open, onProblem)
      return SurroundFilters(sampleRate, measured ?: accurateSpherical, RoomParams.STUDIO, channels)
    }

    const val FRONT_AZ = 30.0
    const val SURROUND_AZ = 110.0
    /** 7.1 layout (ITU): side pair at 90°, back pair at 150°. */
    const val SIDE_AZ = 90.0
    const val BACK_AZ = 150.0
    private const val CH_L = 0
    private const val CH_R = 1
    private const val CH_C = 2
    private const val CH_LS = 4
    private const val CH_RS = 5
    private const val CH_SBL = 6
    private const val CH_SBR = 7
    private val INV_SQRT2 = 1 / sqrt(2.0)
  }
}

/**
 * 5.1 to binaural, streaming: [process] takes 6-channel interleaved frames in blocks of any size and
 * returns whole hops of stereo, delayed by [LATENCY] samples (576 samples, 12 ms at 48 kHz).
 * Supports live room-anchored head tracking ([liveYaw]) when [headHrtf] is supplied.
 */
class SurroundVirtualizer(
  private val filters: SurroundFilters,
  private val headHrtf: SphericalHeadHrtf? = null
) : SpatialStage {

  companion object {
    /** Window minus hop, plus the HRTF's common delay: 576 samples (12 ms at 48 kHz). */
    const val LATENCY = WIN - HOP + HRTF_DELAY
    private const val DRY_RING = 2048
    private const val INTENSITY_STEP = 1.0 / 8
    private const val DOWNMIX_GAIN = 0.7071067811865476f
    internal val SPEAKER_AZ = doubleArrayOf(
      SurroundFilters.FRONT_AZ, -SurroundFilters.FRONT_AZ, 0.0, Double.NaN,
      SurroundFilters.SURROUND_AZ, -SurroundFilters.SURROUND_AZ
    )

    fun downmix(input: FloatArray, offset: Int, frames: Int, out: FloatArray, outOffset: Int = 0) {
      for (i in 0 until frames) {
        val p = offset + 6 * i
        val l = finite(input[p]); val r = finite(input[p + 1]); val c = finite(input[p + 2])
        val ls = finite(input[p + 4]); val rs = finite(input[p + 5])
        out[outOffset + 2 * i] = DOWNMIX_GAIN * (l + DOWNMIX_GAIN * c + DOWNMIX_GAIN * ls)
        out[outOffset + 2 * i + 1] = DOWNMIX_GAIN * (r + DOWNMIX_GAIN * c + DOWNMIX_GAIN * rs)
      }
    }

    /** The same fold-down for a 7.1 frame (L R C LFE Ls Rs SBL SBR): the back pair joins the sides. */
    fun downmix8(input: FloatArray, offset: Int, frames: Int, out: FloatArray, outOffset: Int = 0) {
      for (i in 0 until frames) {
        val p = offset + 8 * i
        val l = finite(input[p]); val r = finite(input[p + 1]); val c = finite(input[p + 2])
        val ls = finite(input[p + 4]) + finite(input[p + 6]); val rs = finite(input[p + 5]) + finite(input[p + 7])
        out[outOffset + 2 * i] = DOWNMIX_GAIN * (l + DOWNMIX_GAIN * c + DOWNMIX_GAIN * ls)
        out[outOffset + 2 * i + 1] = DOWNMIX_GAIN * (r + DOWNMIX_GAIN * c + DOWNMIX_GAIN * rs)
      }
    }

    /**
     * Puts [frames] frames of a 5.1 file (L R C LFE Ls Rs) on the 7.1 stage the app draws: the surrounds are
     * shared between the side pair (90°) and the back pair (150°), half each, so they sit between the two,
     * where a 5.1 mix puts them, and the fold-down back to stereo is unchanged.
     */
    fun expand51To71(input: FloatArray, frames: Int, out: FloatArray) {
      for (i in 0 until frames) {
        val a = 6 * i; val b = 8 * i
        out[b] = input[a]; out[b + 1] = input[a + 1]; out[b + 2] = input[a + 2]; out[b + 3] = input[a + 3]
        out[b + 4] = 0.5f * input[a + 4]; out[b + 5] = 0.5f * input[a + 5]
        out[b + 6] = 0.5f * input[a + 4]; out[b + 7] = 0.5f * input[a + 5]
      }
    }

    private fun finite(v: Float) = if (v.isFinite()) v else 0f
  }

  override val latency: Int get() = LATENCY

  /** Interleaved channels per input frame: 6, or 8 for a 7.1 filter set. */
  private val n = filters.channels

  @Volatile override var targetIntensity: Double = 1.0
  private var intensity = 1.0
  override val currentIntensity: Double get() = intensity
  @Volatile override var liveYaw: Double = 0.0

  private val fft = Fft(NFFT)
  private val win = DoubleArray(WIN) { 0.5 - 0.5 * cos(2 * PI * it / WIN) }
  private val hist = Array(n) { DoubleArray(WIN) }
  private val pend = Array(n) { DoubleArray(HOP) }
  private var pending = 0
  private val accL = DoubleArray(NFFT)
  private val accR = DoubleArray(NFFT)
  private val fRe = DoubleArray(NFFT)
  private val fIm = DoubleArray(NFFT)
  private val yLRe = DoubleArray(BINS); private val yLIm = DoubleArray(BINS)
  private val yRRe = DoubleArray(BINS); private val yRIm = DoubleArray(BINS)
  private val xaRe = DoubleArray(BINS); private val xaIm = DoubleArray(BINS)
  private val xbRe = DoubleArray(BINS); private val xbIm = DoubleArray(BINS)
  private val dry = FloatArray(DRY_RING * 2)
  private var inputFrames = 0L
  private var outputFrames = 0L
  private val dmTmp = FloatArray(2)

  // Dynamic per-speaker direct HRTFs for head tracking (preallocated; computed only when liveYaw != cachedYaw).
  private var cachedYaw = 0.0
  private val dynLRe = Array(n) { DoubleArray(BINS) }
  private val dynLIm = Array(n) { DoubleArray(BINS) }
  private val dynRRe = Array(n) { DoubleArray(BINS) }
  private val dynRIm = Array(n) { DoubleArray(BINS) }

  init {
    reset()
  }

  /** Same fold-down as [downmix] for one frame; a 7.1 frame also folds the back pair into the sides. */
  private fun dryFold(input: FloatArray, p: Int) {
    if (n == 6) { downmix(input, p, 1, dmTmp); return }
    val l = finite(input[p]); val r = finite(input[p + 1]); val c = finite(input[p + 2])
    val ls = finite(input[p + 4]) + finite(input[p + 6]); val rs = finite(input[p + 5]) + finite(input[p + 7])
    dmTmp[0] = DOWNMIX_GAIN * (l + DOWNMIX_GAIN * c + DOWNMIX_GAIN * ls)
    dmTmp[1] = DOWNMIX_GAIN * (r + DOWNMIX_GAIN * c + DOWNMIX_GAIN * rs)
  }

  override fun reset(startIntensity: Double) {
    for (h in hist) h.fill(0.0)
    for (p in pend) p.fill(0.0)
    pending = 0
    accL.fill(0.0); accR.fill(0.0)
    dry.fill(0f)
    inputFrames = 0; outputFrames = 0
    intensity = startIntensity
    cachedYaw = Double.NaN
  }

  override fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int {
    var written = 0
    for (i in 0 until frames) {
      val p = offset + n * i
      for (c in 0 until n) {
        val v = input[p + c]
        pend[c][pending] = if (v.isFinite()) v.toDouble() else 0.0
      }
      dryFold(input, p)
      val q = 2 * (inputFrames % DRY_RING).toInt()
      dry[q] = dmTmp[0]; dry[q + 1] = dmTmp[1]
      inputFrames++
      pending++
      if (pending == HOP) {
        pending = 0
        for (c in 0 until n) {
          System.arraycopy(hist[c], HOP, hist[c], 0, WIN - HOP)
          System.arraycopy(pend[c], 0, hist[c], WIN - HOP, HOP)
        }
        frame(output, written)
        written += HOP
      }
    }
    return written
  }

  private fun frame(output: FloatArray, at: Int) {
    val yaw = liveYaw
    val dynamic = headHrtf != null && abs(yaw) >= 0.05
    if (dynamic && yaw != cachedYaw) {
      cachedYaw = yaw
      updateRotatedSpeakerFilters(filters, headHrtf!!, yaw, dynLRe, dynLIm, dynRRe, dynRIm)
    }

    yLRe.fill(0.0); yLIm.fill(0.0); yRRe.fill(0.0); yRIm.fill(0.0)
    for (pair in 0 until n / 2) {
      val a = hist[2 * pair]; val b = hist[2 * pair + 1]
      for (n in 0 until WIN) { fRe[n] = a[n] * win[n]; fIm[n] = b[n] * win[n] }
      for (n in WIN until NFFT) { fRe[n] = 0.0; fIm[n] = 0.0 }
      fft.forward(fRe, fIm)
      for (k in 0 until BINS) {
        val m = if (k == 0) 0 else NFFT - k
        val zr = fRe[k]; val zi = fIm[k]; val cr = fRe[m]; val ci = -fIm[m]
        xaRe[k] = 0.5 * (zr + cr); xaIm[k] = 0.5 * (zi + ci)
        xbRe[k] = 0.5 * (zi - ci); xbIm[k] = -0.5 * (zr - cr)
      }
      accumulate(2 * pair, xaRe, xaIm, dynamic)
      accumulate(2 * pair + 1, xbRe, xbIm, dynamic)
    }
    yLIm[0] = 0.0; yLIm[BINS - 1] = 0.0; yRIm[0] = 0.0; yRIm[BINS - 1] = 0.0
    for (k in 0 until BINS) {
      fRe[k] = yLRe[k] - yRIm[k]
      fIm[k] = yLIm[k] + yRRe[k]
      if (k in 1 until BINS - 1) {
        fRe[NFFT - k] = yLRe[k] + yRIm[k]
        fIm[NFFT - k] = -yLIm[k] + yRRe[k]
      }
    }
    fft.inverse(fRe, fIm)
    val scale = 1.0 / NFFT
    for (n in 0 until NFFT) { accL[n] += fRe[n] * scale; accR[n] += fIm[n] * scale }

    val target = targetIntensity
    val from = intensity
    intensity = when {
      target > from -> minOf(target, from + INTENSITY_STEP)
      target < from -> maxOf(target, from - INTENSITY_STEP)
      else -> from
    }
    for (i in 0 until HOP) {
      val g = (from + (intensity - from) * (i + 1) / HOP).toFloat()
      val src = outputFrames + i - LATENCY
      var dl = 0f; var dr = 0f
      if (src >= 0) {
        val q = 2 * (src % DRY_RING).toInt()
        dl = dry[q]; dr = dry[q + 1]
      }
      output[2 * (at + i)] = dl + g * (accL[i].toFloat() - dl)
      output[2 * (at + i) + 1] = dr + g * (accR[i].toFloat() - dr)
    }
    outputFrames += HOP
    System.arraycopy(accL, HOP, accL, 0, NFFT - HOP); accL.fill(0.0, NFFT - HOP, NFFT)
    System.arraycopy(accR, HOP, accR, 0, NFFT - HOP); accR.fill(0.0, NFFT - HOP, NFFT)
  }

  private fun accumulate(channel: Int, xRe: DoubleArray, xIm: DoubleArray, dynamic: Boolean) {
    val lRe: DoubleArray; val lIm: DoubleArray; val rRe: DoubleArray; val rIm: DoubleArray
    if (dynamic && channel != 3) {
      lRe = dynLRe[channel]; lIm = dynLIm[channel]
      rRe = dynRRe[channel]; rIm = dynRIm[channel]
    } else {
      lRe = filters.re[channel * 2]; lIm = filters.im[channel * 2]
      rRe = filters.re[channel * 2 + 1]; rIm = filters.im[channel * 2 + 1]
    }
    for (k in 0 until BINS) {
      val xr = xRe[k]; val xi = xIm[k]
      yLRe[k] += lRe[k] * xr - lIm[k] * xi; yLIm[k] += lRe[k] * xi + lIm[k] * xr
      yRRe[k] += rRe[k] * xr - rIm[k] * xi; yRIm[k] += rRe[k] * xi + rIm[k] * xr
    }
  }
}

/** Updates head-tracked per-speaker binaural filters cleanly (reusing static diffuse reflections + rotated direct). */
internal fun updateRotatedSpeakerFilters(
  filters: SurroundFilters,
  headHrtf: SphericalHeadHrtf,
  yawDeg: Double,
  dynLRe: Array<DoubleArray>, dynLIm: Array<DoubleArray>,
  dynRRe: Array<DoubleArray>, dynRIm: Array<DoubleArray>
) {
  for (ch in 0 until filters.channels) {
    val az = filters.azimuths[ch]
    if (az.isNaN()) continue
    headHrtf.evaluateContinuous(az - yawDeg, 0.0, dynLRe[ch], dynLIm[ch], dynRRe[ch], dynRIm[ch])
    val scale = filters.speakerScale[ch]
    if (filters.room.enabled) {
      val rlRe = filters.roomRe[ch * 2]; val rlIm = filters.roomIm[ch * 2]
      val rrRe = filters.roomRe[ch * 2 + 1]; val rrIm = filters.roomIm[ch * 2 + 1]
      for (k in 0 until BINS) {
        dynLRe[ch][k] = scale * dynLRe[ch][k] + rlRe[k]; dynLIm[ch][k] = scale * dynLIm[ch][k] + rlIm[k]
        dynRRe[ch][k] = scale * dynRRe[ch][k] + rrRe[k]; dynRIm[ch][k] = scale * dynRIm[ch][k] + rrIm[k]
      }
    } else if (scale != 1.0) {
      for (k in 0 until BINS) {
        dynLRe[ch][k] *= scale; dynLIm[ch][k] *= scale
        dynRRe[ch][k] *= scale; dynRIm[ch][k] *= scale
      }
    }
  }
}
