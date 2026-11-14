package com.example

import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundVirtualizer
import com.example.data.audio.spatial.SurroundVirtualizer.Companion.LATENCY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The 5.1 to binaural virtualizer, against the same checks as the Python prototype
 * (tools/spatial_proto/tests/test_virtualize.py): where each speaker lands, the interaural delay
 * against Woodworth's formula, symmetry, loudness, block independence, latency and bad input.
 * Channel order: L R C LFE Ls Rs.
 */
class SurroundVirtualizerTest {

  private companion object {
    const val FS = 48000
    const val N = FS * 2
    const val SKIP = 4096
    val filters: SurroundFilters by lazy { SurroundFilters(FS, SphericalHeadHrtf(FS)) }
  }

  private fun noise(frames: Int, seed: Long): FloatArray {
    val r = Random(seed)
    return FloatArray(frames) { (r.nextGaussian() * 0.1).toFloat() }
  }

  /** [channels] as 6 interleaved channels; a channel not given stays silent. */
  private fun sixFrom(frames: Int, vararg channels: Pair<Int, FloatArray>): FloatArray {
    val x = FloatArray(frames * 6)
    for ((ch, sig) in channels) for (i in 0 until frames) x[6 * i + ch] = sig[i]
    return x
  }

  /** Runs the whole signal through a virtualizer at full intensity and returns stereo aligned to the input. */
  private fun virtualize(x: FloatArray, block: Int = 4096): FloatArray {
    val v = SurroundVirtualizer(filters)
    v.targetIntensity = 1.0
    v.reset(1.0)
    return runAligned(v, x, block)
  }

  private fun runAligned(v: SurroundVirtualizer, x: FloatArray, block: Int): FloatArray {
    val frames = x.size / 6
    val padded = x.copyOf((frames + LATENCY + SpatialEngine.HOP * 2) * 6)
    val out = ArrayList<Float>()
    val buf = FloatArray((block + SpatialEngine.HOP) * 2)
    var pos = 0
    while (pos < padded.size / 6) {
      val n = minOf(block, padded.size / 6 - pos)
      val w = v.process(padded, pos * 6, n, buf)
      for (i in 0 until w * 2) out += buf[i]
      pos += n
    }
    val all = out.toFloatArray()
    return all.copyOfRange(LATENCY * 2, (LATENCY + frames) * 2)
  }

  private fun earPower(y: FloatArray, from: Int = SKIP): DoubleArray {
    val p = DoubleArray(2)
    val n = y.size / 2 - from
    for (i in from until y.size / 2) { p[0] += y[2 * i].toDouble() * y[2 * i]; p[1] += y[2 * i + 1].toDouble() * y[2 * i + 1] }
    return doubleArrayOf(p[0] / n, p[1] / n)
  }

  private fun db(ratio: Double) = 10 * log10(ratio)

  @Test
  fun centerIsSymmetric() {
    val p = earPower(virtualize(sixFrom(N, 2 to noise(N, 1))))
    assertTrue(abs(db(p[0] / p[1])) < 0.5)
  }

  private fun levelDiff4k(ch: Int): Double {
    val s = FloatArray(N) { (0.3 * sin(2 * PI * 4000 * it / FS)).toFloat() }
    val p = earPower(virtualize(sixFrom(N, ch to s)))
    return db(p[0] / p[1])
  }

  @Test
  fun levelDifferenceAt4kPointsToTheSpeakerSide() {
    // The head shades the far ear above a few kHz. The prototype measured +30° = 10.3 dB, +110° = 15.3 dB.
    for (ch in intArrayOf(0, 4)) assertTrue("ch $ch ${levelDiff4k(ch)}", levelDiff4k(ch) > 6)
    for (ch in intArrayOf(1, 5)) assertTrue("ch $ch ${levelDiff4k(ch)}", levelDiff4k(ch) < -6)
  }

  @Test
  fun interauralDelayMatchesWoodworth() {
    // (a/c)(θ + sin θ), a = 8.75 cm, c = 343 m/s, θ the lateral angle: 30° for L, 70° for Ls. ±2 samples.
    for ((ch, theta) in listOf(0 to 30.0, 4 to 70.0)) {
      val t = Math.toRadians(theta)
      val expected = 0.0875 / 343.0 * (t + sin(t)) * FS
      val imp = FloatArray(FS / 2).also { it[2000] = 1f }
      val y = virtualize(sixFrom(FS / 2, ch to imp))
      val itd = peak(y, 1) - peak(y, 0)
      assertEquals("ch $ch", expected, itd.toDouble(), 2.0)
    }
  }

  private fun peak(y: FloatArray, ear: Int): Int {
    var best = 0
    for (i in 0 until y.size / 2) if (abs(y[2 * i + ear]) > abs(y[2 * best + ear])) best = i
    return best
  }

  @Test
  fun mirroredSpeakersMirrorTheEars() {
    val s = noise(N, 4)
    for ((a, b) in listOf(0 to 1, 4 to 5)) {
      val pa = earPower(virtualize(sixFrom(N, a to s)))
      val pb = earPower(virtualize(sixFrom(N, b to s)))
      assertTrue(abs(db(pa[0] / pb[1])) < 0.2)
      assertTrue(abs(db(pa[1] / pb[0])) < 0.2)
    }
  }

  @Test
  fun uncorrelatedChannelsKeepTheirTotalPower() {
    val chans = listOf(0, 1, 2, 4, 5).mapIndexed { i, ch -> ch to noise(N, 10L + i) }
    val x = sixFrom(N, *chans.toTypedArray())
    val y = virtualize(x)
    var eIn = 0.0; var eOut = 0.0
    for (i in SKIP until N) { for (c in 0 until 6) eIn += x[6 * i + c].toDouble() * x[6 * i + c]; eOut += y[2 * i].toDouble() * y[2 * i] + y[2 * i + 1].toDouble() * y[2 * i + 1] }
    assertTrue("${db(eOut / eIn)} dB", abs(db(eOut / eIn)) < 2.0)
  }

  @Test
  fun blockSizeDoesNotChangeTheResult() {
    val x = sixFrom(FS, *Array(6) { it to noise(FS, 20L + it) })
    val a = virtualize(x, block = 100)
    val b = virtualize(x, block = 4096)
    var worst = 0f
    for (i in a.indices) worst = maxOf(worst, abs(a[i] - b[i]))
    assertTrue("worst $worst", worst < 1e-5f)
  }

  @Test
  fun latencyIsTheDeclaredValue() {
    val imp = FloatArray(FS / 2).also { it[1000] = 1f }
    val v = SurroundVirtualizer(filters).apply { targetIntensity = 1.0; reset(1.0) }
    val x = sixFrom(FS / 2, 2 to imp)
    val buf = FloatArray((FS / 2 + SpatialEngine.HOP) * 2)
    val w = v.process(x, 0, FS / 2, buf)
    val y = buf.copyOf(w * 2)
    assertEquals(1000 + LATENCY, peak(y, 0))
    assertEquals(1000 + LATENCY, peak(y, 1))
  }

  @Test
  fun lfeReachesBothEarsEqually() {
    val p = earPower(virtualize(sixFrom(N, 3 to noise(N, 30))))
    assertTrue(abs(db(p[0] / p[1])) < 0.1)
    assertTrue(p[0] > 0)
  }

  @Test
  fun nonFiniteInputDoesNotPoisonTheOutput() {
    val x = sixFrom(FS, *Array(6) { it to noise(FS, 40L + it) })
    for (i in 5000 until 5100) for (c in 0 until 6) x[6 * i + c] = if (c % 2 == 0) Float.NaN else Float.POSITIVE_INFINITY
    assertTrue(virtualize(x).all { it.isFinite() })
  }

  @Test
  fun intensityZeroIsThePlainDownmixDelayed() {
    val x = sixFrom(FS / 2, *Array(6) { it to noise(FS / 2, 50L + it) })
    val v = SurroundVirtualizer(filters).apply { targetIntensity = 0.0; reset(0.0) }
    val y = runAligned(v, x, 1000)
    val dm = FloatArray(FS) // stereo
    SurroundVirtualizer.downmix(x, 0, FS / 2, dm)
    for (i in 0 until FS) assertEquals("sample $i", dm[i], y[i], 0f)
  }

  @Test
  fun glidingIntoTheEffectNeverJumps() {
    val x = sixFrom(FS, *Array(6) { it to FloatArray(FS) { n -> (0.1 * sin(2 * PI * 300 * n / FS)).toFloat() } })
    val v = SurroundVirtualizer(filters).apply { targetIntensity = 0.0; reset(0.0) }
    val out = ArrayList<Float>()
    val buf = FloatArray((1000 + SpatialEngine.HOP) * 2)
    var pos = 0
    while (pos < FS) {
      if (pos >= 20000) v.targetIntensity = 1.0
      val n = minOf(1000, FS - pos)
      val w = v.process(x, pos * 6, n, buf)
      for (i in 0 until w * 2) out += buf[i]
      pos += n
    }
    var step = 0f
    for (ch in 0..1) for (i in 1 until out.size / 2) step = maxOf(step, abs(out[2 * i + ch] - out[2 * (i - 1) + ch]))
    // A 300 Hz tone of amplitude ~0.3 moves at most ~0.012 per sample; a click would be much larger.
    assertTrue("step $step", step < 0.05f)
    assertEquals(1.0, v.currentIntensity, 1e-9)
    assertTrue(sqrt(out.takeLast(2000).sumOf { it.toDouble() * it } / 2000) > 0.01)
  }
}
