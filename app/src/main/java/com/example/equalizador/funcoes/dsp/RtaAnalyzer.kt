package com.example.data.audio.dsp

import com.example.data.audio.spatial.Fft
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Snapshot of the internal 64-band logarithmic Real-Time Analyzer (20 Hz..20 kHz, +3 dB/oct tilt).
 * Values are normalized in 0f..1f (-72 dB .. 0 dB). Empty arrays mean the tap is off or silent.
 */
data class RtaFrame(
  val pre: FloatArray = EMPTY,
  val post: FloatArray = EMPTY,
  /** Centre frequency in Hz of the dominant RTA peak (0f when silent). */
  val peakHz: Float = 0f
) {
  companion object {
    const val BANDS = 64
    val EMPTY = FloatArray(0)
    val FLAT = RtaFrame(EMPTY, EMPTY, 0f)
  }
}

/**
 * Lock-free internal 2048-point FFT spectrum analyzer tapped directly from [EqDsp]'s float pipeline.
 *
 * - Requires ZERO Android permissions (no RECORD_AUDIO, no Android Visualizer / AudioFlinger tap).
 * - Audio thread only copies mono-summed samples into a pre-allocated ring when [enabled] is true;
 *   when hidden (another tab, full player, or screen off) it costs a single volatile boolean check.
 * - FFT computation runs off the audio thread in [computeFrame], with Hann windowing, 64 logarithmic
 *   bands (20 Hz..20 kHz), +3.0 dB/oct perceptual tilt (pink-noise flat, Pro-Q style), and smooth
 *   fast-attack / gentle-decay ballistics.
 */
class RtaAnalyzer {
  @Volatile var enabled: Boolean = false
  @Volatile var sampleRate: Int = 48000

  private val preWrite = FloatArray(FFT_SIZE)
  private val postWrite = FloatArray(FFT_SIZE)
  private val preSnap = FloatArray(FFT_SIZE)
  private val postSnap = FloatArray(FFT_SIZE)
  private var writePos = 0
  private val frameReady = AtomicBoolean(false)

  // Consumer-thread buffers (only touched by computeFrame).
  private val fft = Fft(FFT_SIZE)
  private val re = DoubleArray(FFT_SIZE)
  private val im = DoubleArray(FFT_SIZE)
  private val window = DoubleArray(FFT_SIZE) { i -> 0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1))) }
  private val preSmooth = FloatArray(RtaFrame.BANDS)
  private val postSmooth = FloatArray(RtaFrame.BANDS)
  // Double-buffered output arrays so computeFrame() allocates zero FloatArrays per frame.
  private val preOutBufs = arrayOf(FloatArray(RtaFrame.BANDS), FloatArray(RtaFrame.BANDS))
  private val postOutBufs = arrayOf(FloatArray(RtaFrame.BANDS), FloatArray(RtaFrame.BANDS))
  private var outSlot = 0

  // Precomputed logarithmic bin LUT per sampleRate (eliminates 128 pow + 64 sqrt calls every frame).
  private var lutRate = -1
  private val binK0 = IntArray(RtaFrame.BANDS)
  private val binK1 = IntArray(RtaFrame.BANDS)
  private val binTiltDb = DoubleArray(RtaFrame.BANDS)
  private val binCenterHz = FloatArray(RtaFrame.BANDS)

  private fun ensureLut(srInt: Int) {
    if (lutRate == srInt) return
    lutRate = srInt
    val sr = srInt.toDouble()
    val half = FFT_SIZE / 2
    val binHz = sr / FFT_SIZE
    val ratio = MAX_FREQ / MIN_FREQ
    for (b in 0 until RtaFrame.BANDS) {
      val f0 = MIN_FREQ * ratio.pow(b.toDouble() / RtaFrame.BANDS)
      val f1 = MIN_FREQ * ratio.pow((b + 1).toDouble() / RtaFrame.BANDS)
      val fc = sqrt(f0 * f1)
      val k0 = (f0 / binHz).toInt().coerceIn(1, half - 1)
      val k1 = (f1 / binHz).toInt().coerceIn(k0, half - 1)
      binK0[b] = k0
      binK1[b] = k1
      binTiltDb[b] = TILT_DB_PER_OCT * (ln(fc / 1000.0) / LN_2)
      binCenterHz[b] = fc.toFloat()
    }
  }

  /** Called on the audio thread per sample frame (interleaved). Allocation-free and lock-free. */
  fun pushPre(monoSample: Float) {
    if (!enabled) return
    preWrite[writePos] = monoSample
  }

  /** Called on the audio thread after EQ per sample frame; advances write pointer and publishes snapshot. */
  fun pushPostAndAdvance(monoSample: Float) {
    if (!enabled) return
    postWrite[writePos] = monoSample
    writePos++
    if (writePos >= FFT_SIZE) {
      writePos = 0
      if (!frameReady.get()) {
        preWrite.copyInto(preSnap)
        postWrite.copyInto(postSnap)
        frameReady.set(true)
      }
    }
  }

  fun clear() {
    writePos = 0
    frameReady.set(false)
    preSmooth.fill(0f)
    postSmooth.fill(0f)
  }

  /**
   * Computes a fresh 64-band [RtaFrame] off the audio thread if new samples arrived (or decays
   * smoothly when idle). Returns null if disabled.
   */
  fun computeFrame(needPre: Boolean, needPost: Boolean): RtaFrame? {
    if (!enabled) return null
    val hasNew = frameReady.compareAndSet(true, false)
    val srInt = sampleRate.coerceAtLeast(8000)
    ensureLut(srInt)
    outSlot = outSlot xor 1
    val preOut = if (needPre) {
      if (hasNew) analyzeInto(preSnap, preSmooth) else decay(preSmooth)
      val dst = preOutBufs[outSlot]
      preSmooth.copyInto(dst)
      dst
    } else RtaFrame.EMPTY
    val postOut = if (needPost) {
      if (hasNew) analyzeInto(postSnap, postSmooth) else decay(postSmooth)
      val dst = postOutBufs[outSlot]
      postSmooth.copyInto(dst)
      dst
    } else RtaFrame.EMPTY
    val active = if (postOut.isNotEmpty()) postOut else preOut
    var maxIdx = -1
    var maxVal = 0.12f
    for (i in active.indices) {
      if (active[i] > maxVal) {
        maxVal = active[i]
        maxIdx = i
      }
    }
    val peakHz = if (maxIdx >= 0) binCenterHz[maxIdx] else 0f
    return RtaFrame(pre = preOut, post = postOut, peakHz = peakHz)
  }

  private fun decay(smooth: FloatArray) {
    for (i in smooth.indices) smooth[i] = (smooth[i] * 0.85f).let { if (it < 0.005f) 0f else it }
  }

  private fun analyzeInto(samples: FloatArray, smooth: FloatArray) {
    for (i in 0 until FFT_SIZE) {
      re[i] = samples[i] * window[i]
      im[i] = 0.0
    }
    fft.forward(re, im)

    val scale = 2.0 / (FFT_SIZE * 0.5) // compensate Hann coherent gain

    for (b in 0 until RtaFrame.BANDS) {
      val k0 = binK0[b]
      val k1 = binK1[b]

      var peakSq = 0.0
      for (k in k0..k1) {
        val m2 = re[k] * re[k] + im[k] * im[k]
        if (m2 > peakSq) peakSq = m2
      }
      val mag = sqrt(peakSq) * scale
      val dbRaw = 20.0 * log10(mag.coerceAtLeast(1e-6))
      val db = dbRaw + binTiltDb[b]
      val target = ((db - FLOOR_DB) / (-FLOOR_DB)).toFloat().coerceIn(0f, 1f)
      val prev = smooth[b]
      val coef = if (target > prev) ATTACK else RELEASE
      smooth[b] = prev + (target - prev) * coef
    }
  }

  private companion object {
    const val FFT_SIZE = 2048
    const val MIN_FREQ = 20.0
    const val MAX_FREQ = 20000.0
    const val FLOOR_DB = -72.0
    const val TILT_DB_PER_OCT = 3.0
    const val ATTACK = 0.65f
    const val RELEASE = 0.25f
    val LN_2 = ln(2.0)
  }
}
