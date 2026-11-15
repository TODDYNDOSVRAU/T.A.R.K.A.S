package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.HRTF_DELAY
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 5.1 speakers as the listener hears them in headphones: one filter per (channel, ear), already
 * normalised. Port of tools/spatial_proto/tarkas_spatial/virtualize.py.
 *
 * Each speaker is a virtual source at a fixed azimuth, filtered by the same spherical-head HRTF as
 * Tarkas Spatial (positive azimuth to the left): L +30°, R -30°, C 0°, Ls +110°, Rs -110°. The LFE has no
 * direction: it goes to both ears at 1/√2 with only the common delay. Each speaker is scaled so that the
 * power summed over both ears, from 100 Hz to 10 kHz, equals the speaker's own: uncorrelated channels
 * keep the loudness they had.
 *
 * With [room] enabled each speaker also gets the first reflections of a rectangular room, inside the same
 * filter ([SurroundRoom]); the LFE gets none. The constructor's default is no room (what the tests of the
 * dry path use); the app always builds its filters with [production], which has the approved room.
 *
 * Immutable and shared; built once per sample rate from the HRTF (no tables of its own).
 * Channel order of the input is Android's 5POINT1: L, R, C, LFE, Ls, Rs.
 */
class SurroundFilters(val sampleRate: Int, hrtf: HrtfSource, val room: RoomParams = RoomParams.OFF) {
  /** Index `channel * 2 + ear` (ear 0 = left), real and imaginary parts per bin. */
  val re = Array(12) { DoubleArray(BINS) }
  val im = Array(12) { DoubleArray(BINS) }

  init {
    val azimuths = doubleArrayOf(FRONT_AZ, -FRONT_AZ, 0.0, Double.NaN, SURROUND_AZ, -SURROUND_AZ)
    val fft = Fft(NFFT)
    val first = Math.ceil(100.0 * NFFT / sampleRate).toInt()
    val last = Math.floor(10000.0 * NFFT / sampleRate).toInt().coerceAtMost(BINS - 1)
    for (ch in 0 until 6) {
      if (azimuths[ch].isNaN()) {
        // LFE: both ears, common delay only.
        for (ear in 0..1) for (k in 0 until BINS) {
          val phase = -2 * PI * k * sampleRate.toDouble() / NFFT * HRTF_DELAY / sampleRate
          re[ch * 2 + ear][k] = INV_SQRT2 * cos(phase)
          im[ch * 2 + ear][k] = INV_SQRT2 * sin(phase)
        }
        continue
      }
      val (hl, hr) = hrtf.response(azimuths[ch], 0.0)
      if (room.enabled) SurroundRoom.addReflections(hl, hr, SurroundRoom.paths(azimuths[ch], 0.0, room, sampleRate), hrtf, sampleRate)
      // Every speaker's filter is cut to the length the STFT convolves exactly (also without the room).
      SurroundRoom.fit(hl, fft); SurroundRoom.fit(hr, fft)
      var power = 0.0
      for (k in first..last) {
        power += hl[2 * k] * hl[2 * k] + hl[2 * k + 1] * hl[2 * k + 1] + hr[2 * k] * hr[2 * k] + hr[2 * k + 1] * hr[2 * k + 1]
      }
      val g = 1 / sqrt(power / (last - first + 1))
      for (k in 0 until BINS) {
        re[ch * 2][k] = g * hl[2 * k]; im[ch * 2][k] = g * hl[2 * k + 1]
        re[ch * 2 + 1][k] = g * hr[2 * k]; im[ch * 2 + 1][k] = g * hr[2 * k + 1]
      }
    }
  }

  companion object {
    /**
     * The filters the app plays: the measured head for [sampleRate] when [open] gives a usable file, otherwise
     * the [spherical] one, in both cases with the owner-approved room ([RoomParams.APPROVED], level 0.35).
     * [onProblem] gets why the measured head was not used.
     */
    fun production(
      sampleRate: Int, spherical: HrtfSource, open: ((String) -> java.io.InputStream?)?,
      onProblem: (String) -> Unit = {}
    ): SurroundFilters {
      val measured = if (open == null) null else MeasuredHrtf.load(sampleRate, open, onProblem)
      return SurroundFilters(sampleRate, measured ?: spherical, RoomParams.APPROVED)
    }

    const val FRONT_AZ = 30.0
    const val SURROUND_AZ = 110.0
    private val INV_SQRT2 = 1 / sqrt(2.0)
  }
}

/**
 * 5.1 to binaural, streaming: [process] takes 6-channel interleaved frames in blocks of any size and
 * returns whole hops of stereo, delayed by [LATENCY] samples. Same STFT as [SpatialEngine]
 * (Hann 1024, FFT 2048, hop 512). Linear and time-invariant, so the block size never changes the result.
 *
 * Like the engine it glides between the dry sound and its own: the dry sound here is the stereo downmix
 * ([downmix]), time-aligned to the output, so switching never clicks and intensity 0 is the plain downmix.
 * Allocation-free after construction.
 */
class SurroundVirtualizer(private val filters: SurroundFilters) : SpatialStage {

  companion object {
    /** Window minus hop, plus the HRTF's common delay: 576 samples (12 ms at 48 kHz). */
    const val LATENCY = WIN - HOP + HRTF_DELAY
    private const val DRY_RING = 2048
    private const val INTENSITY_STEP = 1.0 / 8
    private const val DOWNMIX_GAIN = 0.7071067811865476f

    /**
     * The stereo downmix of [frames] 5.1 frames (ITU-R BS.775 weights for C and the surrounds, scaled by
     * 1/√2; the LFE is left out), into [out]. A sample that is not finite counts as silence.
     */
    fun downmix(input: FloatArray, offset: Int, frames: Int, out: FloatArray, outOffset: Int = 0) {
      for (i in 0 until frames) {
        val p = offset + 6 * i
        val l = finite(input[p]); val r = finite(input[p + 1]); val c = finite(input[p + 2])
        val ls = finite(input[p + 4]); val rs = finite(input[p + 5])
        out[outOffset + 2 * i] = DOWNMIX_GAIN * (l + DOWNMIX_GAIN * c + DOWNMIX_GAIN * ls)
        out[outOffset + 2 * i + 1] = DOWNMIX_GAIN * (r + DOWNMIX_GAIN * c + DOWNMIX_GAIN * rs)
      }
    }

    private fun finite(v: Float) = if (v.isFinite()) v else 0f
  }

  override val latency: Int get() = LATENCY

  @Volatile override var targetIntensity: Double = 1.0
  private var intensity = 1.0
  override val currentIntensity: Double get() = intensity

  private val fft = Fft(NFFT)
  private val win = DoubleArray(WIN) { 0.5 - 0.5 * cos(2 * PI * it / WIN) }
  private val hist = Array(6) { DoubleArray(WIN) }
  private val pend = Array(6) { DoubleArray(HOP) }
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
  private var inputFrames = 0L // frames taken in since the last reset
  private var outputFrames = 0L // frames given out since the last reset
  private val dmTmp = FloatArray(2)

  init {
    reset()
  }

  override fun reset(startIntensity: Double) {
    for (h in hist) h.fill(0.0)
    for (p in pend) p.fill(0.0)
    pending = 0
    accL.fill(0.0); accR.fill(0.0)
    dry.fill(0f)
    inputFrames = 0; outputFrames = 0
    intensity = startIntensity
  }

  override fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int {
    var written = 0
    for (i in 0 until frames) {
      val p = offset + 6 * i
      for (c in 0 until 6) {
        val v = input[p + c]
        pend[c][pending] = if (v.isFinite()) v.toDouble() else 0.0
      }
      downmix(input, p, 1, dmTmp)
      val q = 2 * (inputFrames % DRY_RING).toInt()
      dry[q] = dmTmp[0]; dry[q + 1] = dmTmp[1]
      inputFrames++
      pending++
      if (pending == HOP) {
        pending = 0
        for (c in 0 until 6) {
          System.arraycopy(hist[c], HOP, hist[c], 0, WIN - HOP)
          System.arraycopy(pend[c], 0, hist[c], WIN - HOP, HOP)
        }
        frame(output, written)
        written += HOP
      }
    }
    return written
  }

  /** One hop: filter the last window, overlap-add, and write [HOP] mixed frames at [at] in [output]. */
  private fun frame(output: FloatArray, at: Int) {
    yLRe.fill(0.0); yLIm.fill(0.0); yRRe.fill(0.0); yRIm.fill(0.0)
    // Two real channels per complex FFT: z = a + i·b.
    for (pair in 0 until 3) {
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
      accumulate(2 * pair, xaRe, xaIm)
      accumulate(2 * pair + 1, xbRe, xbIm)
    }
    // Both ears in one inverse FFT: Z = Y_L + i·Y_R with Hermitian halves. The DC and Nyquist bins of a
    // real signal are real; dropping their imaginary part is what a real inverse transform does.
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

    // The glide: one step per hop, spread linearly over the hop's samples.
    val target = targetIntensity
    val from = intensity
    intensity = when {
      target > from -> minOf(target, from + INTENSITY_STEP)
      target < from -> maxOf(target, from - INTENSITY_STEP)
      else -> from
    }
    for (i in 0 until HOP) {
      val g = (from + (intensity - from) * (i + 1) / HOP).toFloat()
      // The dry sound for output frame j is the downmix of input frame j - LATENCY (silence before 0).
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

  private fun accumulate(channel: Int, xRe: DoubleArray, xIm: DoubleArray) {
    val lRe = filters.re[channel * 2]; val lIm = filters.im[channel * 2]
    val rRe = filters.re[channel * 2 + 1]; val rIm = filters.im[channel * 2 + 1]
    for (k in 0 until BINS) {
      val xr = xRe[k]; val xi = xIm[k]
      yLRe[k] += lRe[k] * xr - lIm[k] * xi; yLIm[k] += lRe[k] * xi + lIm[k] * xr
      yRRe[k] += rRe[k] * xr - rIm[k] * xi; yRIm[k] += rRe[k] * xi + rIm[k] * xr
    }
  }
}
