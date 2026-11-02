package com.example.data.audio.loudness

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Integrated loudness of a track, in LUFS, as ITU-R BS.1770-4 defines it (the measure EBU R128 and the
 * streaming services use):
 * - each channel goes through the K-weighting (a high shelf, then a high-pass);
 * - the mean square is taken over 400 ms blocks that overlap by 75% (one every 100 ms);
 * - blocks below −70 LUFS are dropped (silence), then blocks more than 10 LU below the mean of the rest
 *   (quiet intros, fades), and the remaining ones are averaged.
 *
 * Mono and stereo only: the weights of surround channels need their layout, which a PCM buffer does not
 * carry, so other channel counts are not measured ([result] stays null).
 *
 * Not thread-safe: [LoudnessMeterProcessor] feeds it on the playback thread and reads it under a lock.
 */
class LoudnessMeter {

  private var sampleRate = 0
  private var channels = 0
  private var supported = false

  // K-weighting state, per channel: stage 1 (shelf) and stage 2 (high-pass), direct form II transposed.
  private var shelf = KFilter.IDENTITY
  private var highPass = KFilter.IDENTITY
  private var z = DoubleArray(0)

  /** Frames in one 100 ms step; a gating block is four steps. */
  private var stepFrames = 0
  private var stepFill = 0
  private var stepSum = DoubleArray(0)
  /** The last four steps' summed weighted energy (over channels), to build each 400 ms block. */
  private val lastSteps = DoubleArray(4)
  private var stepsSeen = 0

  /** Mean square of every 400 ms block measured so far. */
  private var blocks = DoubleArray(256)
  private var blockCount = 0

  /** Frames fed since [clear], in the formats they came in. */
  var measuredSeconds = 0.0
    private set

  /**
   * Prepares for audio of this format. Filter state restarts, but what was measured so far is kept:
   * a format change mid-track (rare) should not throw the track's measurement away.
   */
  fun configure(sampleRate: Int, channels: Int) {
    this.sampleRate = sampleRate
    this.channels = channels
    supported = sampleRate > 0 && (channels == 1 || channels == 2)
    if (!supported) return
    shelf = KFilter.shelf(sampleRate.toDouble())
    highPass = KFilter.highPass(sampleRate.toDouble())
    z = DoubleArray(channels * 4)
    stepFrames = (sampleRate / 10).coerceAtLeast(1)
    stepFill = 0
    stepSum = DoubleArray(channels)
  }

  /** Clears the filter memory after a seek or a stop, keeping the blocks measured so far. */
  fun resetFilters() {
    z.fill(0.0)
  }

  /** Forgets everything measured: the start of a new track. */
  fun clear() {
    z.fill(0.0)
    stepFill = 0
    stepSum.fill(0.0)
    lastSteps.fill(0.0)
    stepsSeen = 0
    blockCount = 0
    measuredSeconds = 0.0
  }

  /** Feeds [frames] interleaved frames from [samples] (values in −1..1). */
  fun process(samples: FloatArray, frames: Int) {
    if (!supported) return
    val ch = channels
    val s = shelf
    val h = highPass
    for (f in 0 until frames) {
      for (c in 0 until ch) {
        val x = samples[f * ch + c].toDouble()
        val zi = c * 4
        // Stage 1: high shelf (+4 dB above ~1.7 kHz, the head's effect).
        val y1 = s.b0 * x + z[zi]
        z[zi] = s.b1 * x - s.a1 * y1 + z[zi + 1]
        z[zi + 1] = s.b2 * x - s.a2 * y1
        // Stage 2: high-pass at ~38 Hz (the ear's low-frequency roll-off).
        val y2 = h.b0 * y1 + z[zi + 2]
        z[zi + 2] = h.b1 * y1 - h.a1 * y2 + z[zi + 3]
        z[zi + 3] = h.b2 * y1 - h.a2 * y2
        stepSum[c] += y2 * y2
      }
      if (++stepFill == stepFrames) closeStep()
    }
    measuredSeconds += frames.toDouble() / sampleRate
  }

  private fun closeStep() {
    // Left and right weigh 1.0 each (the standard's weights for the front channels).
    var energy = 0.0
    for (c in 0 until channels) {
      energy += stepSum[c] / stepFrames
      stepSum[c] = 0.0
    }
    stepFill = 0
    lastSteps[stepsSeen % 4] = energy
    stepsSeen++
    if (stepsSeen >= 4) addBlock((lastSteps[0] + lastSteps[1] + lastSteps[2] + lastSteps[3]) / 4.0)
  }

  private fun addBlock(meanSquare: Double) {
    if (blockCount == blocks.size) blocks = blocks.copyOf(blocks.size * 2)
    blocks[blockCount++] = meanSquare
  }

  /** The gated integrated loudness in LUFS; null with no block above the absolute gate (silence, too short). */
  fun result(): Double? {
    if (!supported || blockCount == 0) return null
    val absoluteGate = energyOf(ABSOLUTE_GATE_LUFS)
    var sum = 0.0
    var n = 0
    for (i in 0 until blockCount) {
      if (blocks[i] > absoluteGate) {
        sum += blocks[i]
        n++
      }
    }
    if (n == 0) return null
    val relativeGate = energyOf(lufsOf(sum / n) + RELATIVE_GATE_LU)
    sum = 0.0
    n = 0
    for (i in 0 until blockCount) {
      if (blocks[i] > absoluteGate && blocks[i] > relativeGate) {
        sum += blocks[i]
        n++
      }
    }
    return if (n == 0) null else lufsOf(sum / n)
  }

  /** One biquad's normalised coefficients (a0 = 1). */
  private class KFilter(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    companion object {
      val IDENTITY = KFilter(1.0, 0.0, 0.0, 0.0, 0.0)

      /**
       * The standard gives the K-weighting coefficients for 48 kHz only. These are the analogue
       * prototypes it was derived from, through the bilinear transform, so every sample rate gets
       * the same curve; at 48 kHz they reproduce the standard's table (LoudnessMeterTest checks it
       * against the EBU Tech 3341 test signals).
       */
      fun shelf(fs: Double): KFilter {
        val f0 = 1681.974450955533
        val gainDb = 3.999843853973347
        val q = 0.7071752369554196
        val k = tan(PI * f0 / fs)
        val vh = 10.0.pow(gainDb / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a0 = 1.0 + k / q + k * k
        return KFilter(
          b0 = (vh + vb * k / q + k * k) / a0,
          b1 = 2.0 * (k * k - vh) / a0,
          b2 = (vh - vb * k / q + k * k) / a0,
          a1 = 2.0 * (k * k - 1.0) / a0,
          a2 = (1.0 - k / q + k * k) / a0
        )
      }

      fun highPass(fs: Double): KFilter {
        val f0 = 38.13547087602444
        val q = 0.5003270373238773
        val k = tan(PI * f0 / fs)
        val a0 = 1.0 + k / q + k * k
        return KFilter(
          b0 = 1.0,
          b1 = -2.0,
          b2 = 1.0,
          a1 = 2.0 * (k * k - 1.0) / a0,
          a2 = (1.0 - k / q + k * k) / a0
        )
      }
    }
  }

  companion object {
    const val ABSOLUTE_GATE_LUFS = -70.0
    const val RELATIVE_GATE_LU = -10.0

    fun lufsOf(meanSquare: Double): Double = -0.691 + 10.0 * log10(meanSquare)
    private fun energyOf(lufs: Double): Double = 10.0.pow((lufs + 0.691) / 10.0)
  }
}
