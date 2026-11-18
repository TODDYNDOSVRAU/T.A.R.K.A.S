package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Stereo to five real-sounding virtual speakers around the head, in one STFT:
 * uses [Upmixer]'s DPL II quadrature + PCA Primary-Ambient analysis, renders all 5 anchored
 * speakers through [SurroundFilters] (and [headHrtf] under live head tracking), optionally adds
 * a calibrated Householder FDN late diffuse listening-room tail ([studioRoomTail] > 0) with
 * center-voice isolation, and glides seamlessly from and to the delayed dry input.
 *
 * Allocation-free after construction; keeps total latency at 576 samples (12 ms at 48 kHz).
 */
class UpmixVirtualizer(
  private val sampleRate: Int,
  private val filters: SurroundFilters,
  params: UpmixParams = UpmixParams(),
  private val headHrtf: SphericalHeadHrtf? = null,
  private val studioRoomTail: Double = 0.0
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
  @Volatile override var liveYaw: Double = 0.0

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

  // Head-tracking per-speaker dynamic filter cache (reused without allocations).
  private var cachedYaw = Double.NaN
  private val dynLRe = Array(6) { DoubleArray(BINS) }
  private val dynLIm = Array(6) { DoubleArray(BINS) }
  private val dynRRe = Array(6) { DoubleArray(BINS) }
  private val dynRIm = Array(6) { DoubleArray(BINS) }

  // Late diffuse room tail + voice-band isolation (keeps Center dialogue crisp & dry in front).
  private val tailFdn = if (studioRoomTail > 0.0) {
    AcousticPhysics.HouseholderTail(
      sampleRate = sampleRate,
      rt60Sec = 0.30,
      diffusion = 0.62,
      dampHz = 4200.0,
      outLpHz = 6000.0,
      tailGain = 1.4
    )
  } else null
  private val hpCoef = 1.0 - exp(-2.0 * PI * 180.0 / sampleRate)
  private var hpState = 0.0
  private var voicePresence = 0.0
  private val voiceBinFirst = Math.ceil(300.0 * NFFT / sampleRate).toInt().coerceIn(0, BINS - 1)
  private val voiceBinLast = Math.floor(4500.0 * NFFT / sampleRate).toInt().coerceIn(voiceBinFirst, BINS - 1)

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
    cachedYaw = Double.NaN
    hpState = 0.0
    voicePresence = 0.0
    tailFdn?.clear()
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
    val yaw = liveYaw
    val dynamic = headHrtf != null && abs(yaw) >= 0.05
    if (dynamic && yaw != cachedYaw) {
      cachedYaw = yaw
      updateRotatedSpeakerFilters(filters, headHrtf!!, yaw, dynLRe, dynLIm, dynRRe, dynRIm)
    }

    yLRe.fill(0.0); yLIm.fill(0.0); yRRe.fill(0.0); yRIm.fill(0.0)
    for (ch in 0 until 6) {
      val xRe = spectra.specRe[ch]; val xIm = spectra.specIm[ch]
      val lRe: DoubleArray; val lIm: DoubleArray; val rRe: DoubleArray; val rIm: DoubleArray
      if (dynamic && ch != 3) {
        lRe = dynLRe[ch]; lIm = dynLIm[ch]
        rRe = dynRRe[ch]; rIm = dynRIm[ch]
      } else {
        lRe = filters.re[ch * 2]; lIm = filters.im[ch * 2]
        rRe = filters.re[ch * 2 + 1]; rIm = filters.im[ch * 2 + 1]
      }
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

    if (tailFdn != null) {
      var centerPow = 0.0
      var frontPow = 1e-20
      val cRe = spectra.specRe[UpmixSpectra.CH_C]; val cIm = spectra.specIm[UpmixSpectra.CH_C]
      val lRe = spectra.specRe[UpmixSpectra.CH_L]; val lIm = spectra.specIm[UpmixSpectra.CH_L]
      val rRe = spectra.specRe[UpmixSpectra.CH_R]; val rIm = spectra.specIm[UpmixSpectra.CH_R]
      for (k in voiceBinFirst..voiceBinLast) {
        val pc = cRe[k] * cRe[k] + cIm[k] * cIm[k]
        val pl = lRe[k] * lRe[k] + lIm[k] * lIm[k]
        val pr = rRe[k] * rRe[k] + rIm[k] * rIm[k]
        centerPow += pc
        frontPow += pc + pl + pr
      }
      val nowVoice = (centerPow / frontPow).coerceIn(0.0, 1.0)
      voicePresence += (nowVoice - voicePresence) * (if (nowVoice > voicePresence) 0.5 else 0.15)
      val feedWeight = 1.0 - 0.70 * voicePresence
      val tailScale = studioRoomTail * 0.25
      for (i in 0 until HOP) {
        val dryMid = 0.5 * (accL[i] + accR[i]) * feedWeight
        hpState += hpCoef * (dryMid - hpState)
        tailFdn.step(dryMid - hpState)
        accL[i] += tailScale * tailFdn.outLeft
        accR[i] += tailScale * tailFdn.outRight
      }
    }

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
}
