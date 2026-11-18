package com.example.data.waveform

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns decoded audio into the loudness shape of a track: [buckets] values from 0 to 255, each the RMS of
 * its slice of the track relative to the loudest slice. The decoder feeds it 16-bit interleaved PCM as it
 * comes; nothing but the running sums is kept, so a long track costs the same memory as a short one.
 *
 * [estimatedFrames] is duration × sample rate from the file's header, which is only an estimate: slices are
 * counted in finer steps and laid onto the real length in [result], so a track that ends earlier or later
 * than the header said still fills the whole bar.
 */
class PeakAccumulator(private val buckets: Int = BUCKETS, private val estimatedFrames: Long) {
  private val fine = buckets * FINE_PER_BUCKET
  private val sumSq = DoubleArray(fine)
  private val counts = LongArray(fine)
  private val estimate = estimatedFrames.coerceAtLeast(1L)

  /** Frames read so far (one frame = one sample of every channel). */
  var frames = 0L
    private set

  /** [length] samples of [samples], interleaved over [channels]; a trailing partial frame is ignored. */
  fun add(samples: ShortArray, length: Int, channels: Int) {
    if (channels <= 0) return
    var i = 0
    while (i + channels <= length) {
      var energy = 0.0
      for (c in 0 until channels) {
        val v = samples[i + c] / 32768.0
        energy += v * v
      }
      val bin = min(fine - 1L, frames * fine / estimate).toInt()
      sumSq[bin] += energy / channels
      counts[bin]++
      frames++
      i += channels
    }
  }

  /** The [buckets] values; all zero for silence or an empty track. */
  fun result(): ByteArray {
    val out = ByteArray(buckets)
    if (frames == 0L) return out
    val span = min(frames, estimate).toDouble()
    val bSum = DoubleArray(buckets)
    val bCount = LongArray(buckets)
    for (i in 0 until fine) {
      if (counts[i] == 0L) continue
      val centre = (i + 0.5) * estimate / fine
      val b = min(buckets - 1.0, centre * buckets / span).toInt()
      bSum[b] += sumSq[i]
      bCount[b] += counts[i]
    }
    val rms = DoubleArray(buckets) { if (bCount[it] > 0) sqrt(bSum[it] / bCount[it]) else 0.0 }
    val peak = rms.max()
    if (peak <= 0.0) return out
    for (b in 0 until buckets) out[b] = (255 * rms[b] / peak).roundToInt().coerceIn(0, 255).toByte()
    return out
  }

  companion object {
    const val BUCKETS = 100
    private const val FINE_PER_BUCKET = 8
  }
}
