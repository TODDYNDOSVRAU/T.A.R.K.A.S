package com.example

import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundVirtualizer
import com.example.data.audio.spatial.UpmixVirtualizer
import com.example.data.audio.spatial.UpmixVirtualizer.Companion.LATENCY
import com.example.data.audio.spatial.Upmixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * The fused 2.0 to binaural stage (upmix spectra straight through the five-speaker filters): it must sound
 * like the cascade Upmixer -> SurroundVirtualizer, with half of its latency, and glide, copy and survive bad
 * input like the other stages.
 */
class UpmixVirtualizerTest {

  private companion object {
    const val FS = 48000
    const val N = FS * 2
    const val SKIP = 8192
    val filters: SurroundFilters by lazy { SurroundFilters(FS, SphericalHeadHrtf(FS)) }
  }

  private fun noise(frames: Int, seed: Long): FloatArray {
    val r = Random(seed)
    return FloatArray(frames * 2) { (r.nextGaussian() * 0.1).toFloat() }
  }

  private fun db(x: Double) = 10 * log10(x)

  /** Runs [x] through [v] and returns stereo aligned to the input (the stage's own latency removed). */
  private fun run(v: UpmixVirtualizer, x: FloatArray, block: Int = 4096, intensity: Double = 1.0): FloatArray {
    v.targetIntensity = intensity
    v.reset(intensity)
    val frames = x.size / 2
    val padded = x.copyOf((frames + LATENCY + SpatialEngine.HOP * 2) * 2)
    val total = padded.size / 2
    val buf = FloatArray((block + SpatialEngine.HOP) * 2)
    val out = ArrayList<Float>()
    var pos = 0
    while (pos < total) {
      val n = minOf(block, total - pos)
      val w = v.process(padded, pos * 2, n, buf)
      for (i in 0 until w * 2) out += buf[i]
      pos += n
    }
    val all = out.toFloatArray()
    val have = minOf(frames, all.size / 2 - LATENCY)
    return all.copyOfRange(LATENCY * 2, (LATENCY + have) * 2)
  }

  private fun earPower(y: FloatArray): DoubleArray {
    val p = DoubleArray(2)
    val n = y.size / 2 - SKIP
    for (i in SKIP until y.size / 2) { p[0] += y[2 * i].toDouble() * y[2 * i]; p[1] += y[2 * i + 1].toDouble() * y[2 * i + 1] }
    return doubleArrayOf(p[0] / n, p[1] / n)
  }

  @Test
  fun latencyIsTheVirtualizersAlone() {
    assertEquals(SurroundVirtualizer.LATENCY, LATENCY)
    assertEquals(LATENCY, UpmixVirtualizer(FS, filters).latency)
    assertTrue(LATENCY < Upmixer.LATENCY + SurroundVirtualizer.LATENCY)
  }

  @Test
  fun intensityZeroIsTheInputDelayedExactly() {
    val x = noise(N / 2, 1)
    val v = UpmixVirtualizer(FS, filters)
    v.targetIntensity = 0.0
    v.reset(0.0)
    val padded = x.copyOf(x.size + 4 * SpatialEngine.HOP * 2)
    val out = FloatArray(padded.size + 2 * SpatialEngine.HOP * 2)
    val w = v.process(padded, 0, padded.size / 2, out)
    for (i in 0 until LATENCY * 2) assertEquals(0f, out[i], 0f)
    for (i in LATENCY * 2 until minOf(w * 2, LATENCY * 2 + x.size)) assertEquals(x[i - LATENCY * 2], out[i], 0f)
  }

  @Test
  fun soundsLikeTheCascade() {
    val x = noise(N, 2)
    val fused = earPower(run(UpmixVirtualizer(FS, filters), x))

    // The cascade: upmix to six channels, then the 5.1 virtualizer. Latencies 512 + 576.
    val up = Upmixer(FS)
    val virt = SurroundVirtualizer(filters)
    virt.targetIntensity = 1.0
    virt.reset(1.0)
    val frames = N
    val padded = x.copyOf((frames + 1088 + SpatialEngine.HOP * 4) * 2)
    val six = FloatArray(padded.size / 2 * 6 + SpatialEngine.HOP * 6)
    val sixFrames = up.process(padded, 0, padded.size / 2, six)
    val ears = FloatArray((sixFrames + SpatialEngine.HOP) * 2)
    val w = virt.process(six, 0, sixFrames, ears)
    // Power is what is compared, so it is enough to drop the leading silence of the two stages.
    val lead = (Upmixer.LATENCY + SurroundVirtualizer.LATENCY) * 2
    val c = earPower(ears.copyOfRange(lead, minOf(w * 2, lead + frames * 2)))

    assertTrue("left ${db(fused[0] / c[0])} dB", abs(db(fused[0] / c[0])) < 1.5)
    assertTrue("right ${db(fused[1] / c[1])} dB", abs(db(fused[1] / c[1])) < 1.5)
  }

  @Test
  fun blockSizeDoesNotMatter() {
    val x = noise(FS, 3)
    val a = run(UpmixVirtualizer(FS, filters), x, block = 100)
    val b = run(UpmixVirtualizer(FS, filters), x, block = 4096)
    val n = minOf(a.size, b.size)
    assertTrue(n > FS)
    for (i in 0 until n) assertEquals("at $i", a[i], b[i], 1e-6f)
  }

  @Test
  fun glidingNeverJumps() {
    val x = FloatArray(FS * 4) { (0.3 * sin(2 * PI * 440 * (it / 2) / FS)).toFloat() }
    val v = UpmixVirtualizer(FS, filters)
    v.targetIntensity = 0.0
    v.reset(0.0)
    val out = FloatArray((1000 + SpatialEngine.HOP) * 2)
    val all = ArrayList<Float>()
    var pos = 0
    var i = 0
    while (pos + 1000 <= x.size / 2) {
      if (i == 20) v.targetIntensity = 1.0
      if (i == 60) v.targetIntensity = 0.0
      val w = v.process(x, pos * 2, 1000, out)
      for (k in 0 until w * 2) all += out[k]
      pos += 1000; i++
    }
    assertTrue(all.all { it.isFinite() })
    var maxStep = 0f
    for (ch in 0..1) for (k in 1 until all.size / 2) maxStep = max(maxStep, abs(all[2 * k + ch] - all[2 * (k - 1) + ch]))
    assertTrue("step $maxStep", maxStep < 0.15f)
    assertEquals(0.0, v.currentIntensity, 1e-9)
  }

  @Test
  fun nanInputDoesNotPoisonTheOutput() {
    val x = noise(FS, 4)
    for (i in 10000 until 10200) x[i] = Float.NaN
    x[20001] = Float.NEGATIVE_INFINITY
    val y = run(UpmixVirtualizer(FS, filters), x)
    assertTrue(y.all { it.isFinite() })
  }
}
