package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN

/**
 * Stereo to five virtual speakers around the head, in one STFT: the [Upmixer]'s analysis, then each of
 * the six channel spectra straight through its pair of [SurroundFilters] to the ears. It is the upmixer
 * followed by the 5.1 virtualizer, without the time signal in between: one forward FFT and one inverse FFT
 * per hop instead of eight, and the latency of the virtualizer alone ([LATENCY], 576 samples) instead of
 * the two added (1088, which would not fit the processor's delay).
 *
 * Like the other stages it glides between the dry sound (the input itself, time-aligned) and its own, so
 * switching never clicks and intensity 0 is the plain delayed input. Allocation-free after construction.
 */
class UpmixVirtualizer(
  sampleRate: Int,
  private val filters: SurroundFilters,
  params: UpmixParams = UpmixParams()
) : SpatialStage {

  companion object {
    const val LATENCY = SurroundVirtualizer.LATENCY
    private const val DRY_RING = 2048
    private const val INTENSITY_STEP = 1.0 / 8
  }

  override val latency: Int get() = LATENCY

  @Volatile override var targetIntensity: Double = 1.0
  private var intensity = 1.0
  override val currentIntensity: Double get() = intensity

  private val spectra = UpmixSpectra(params, UpmixTables(sampleRate, params))
  private val fft = Fft(NFFT)
  private val histL = DoubleArray(WIN)
  private val histR = DoubleArray(WIN)
  private val pendL = DoubleArray(HOP)
  private val pendR = DoubleArray(HOP)
  private var pending = 0
  private val accL = DoubleArray(NFFT)
  private val accR = DoubleArray(NFFT)
  private val fRe = DoubleArray(NFFT)
  private val fIm = DoubleArray(NFFT)
  private val yLRe = DoubleArray(BINS); private val yLIm = DoubleArray(BINS)
  private val yRRe = DoubleArray(BINS); private val yRIm = DoubleArray(BINS)
  private val dry = FloatArray(DRY_RING * 2)
  private var inputFrames = 0L
  private var outputFrames = 0L

  init {
    reset()
  }

  override fun reset(startIntensity: Double) {
    histL.fill(0.0); histR.fill(0.0); pendL.fill(0.0); pendR.fill(0.0)
    pending = 0
    accL.fill(0.0); accR.fill(0.0)
    dry.fill(0f)
    inputFrames = 0; outputFrames = 0
    intensity = startIntensity
    spectra.reset()
  }

  override fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int {
    var written = 0
    for (i in 0 until frames) {
      val l = input[offset + 2 * i].let { if (it.isFinite()) it else 0f }
      val r = input[offset + 2 * i + 1].let { if (it.isFinite()) it else 0f }
      pendL[pending] = l.toDouble(); pendR[pending] = r.toDouble()
      val q = 2 * (inputFrames % DRY_RING).toInt()
      dry[q] = l; dry[q + 1] = r
      inputFrames++
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
    yLRe.fill(0.0); yLIm.fill(0.0); yRRe.fill(0.0); yRIm.fill(0.0)
    for (ch in 0 until 6) {
      val xRe = spectra.specRe[ch]; val xIm = spectra.specIm[ch]
      val lRe = filters.re[ch * 2]; val lIm = filters.im[ch * 2]
      val rRe = filters.re[ch * 2 + 1]; val rIm = filters.im[ch * 2 + 1]
      for (k in 0 until BINS) {
        val xr = xRe[k]; val xi = xIm[k]
        yLRe[k] += lRe[k] * xr - lIm[k] * xi; yLIm[k] += lRe[k] * xi + lIm[k] * xr
        yRRe[k] += rRe[k] * xr - rIm[k] * xi; yRIm[k] += rRe[k] * xi + rIm[k] * xr
      }
    }
    packTwoReal(yLRe, yLIm, yRRe, yRIm, fRe, fIm)
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
      // The dry sound for output frame j is input frame j - LATENCY (silence before 0).
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
}
