package com.example

import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.UpmixParams
import com.example.data.audio.spatial.Upmixer
import com.example.data.audio.spatial.Upmixer.Companion.LATENCY
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_C
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_L
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_LFE
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_LS
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_R
import com.example.data.audio.spatial.UpmixSpectra.Companion.CH_RS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The 2.0 to 5.1 upmixer against the checks of the Python prototype (tools/spatial_proto/tests/test_upmix.py):
 * independent pink-noise sources are encoded into Lt/Rt by a matrix encoder written here (centre in phase,
 * surrounds at 90 degrees and opposite polarity), and the six output channels are measured one by one.
 * Channel order: L R C LFE Ls Rs.
 */
class UpmixerTest {

  private companion object {
    const val FS = 48000
    const val N = 131072 // a power of two, so the noise and the Hilbert shift need no extra FFT size
    const val SKIP = 8192 // the spectral averages fill during the first part
  }

  // ---- signal tools ---------------------------------------------------------------------------

  private fun pink(seed: Long): DoubleArray {
    val rng = Random(seed)
    val re = DoubleArray(N) { rng.nextGaussian() }
    val im = DoubleArray(N)
    val fft = Fft(N)
    fft.forward(re, im)
    for (k in 0 until N / 2 + 1) {
      val g = if (k == 0) 0.0 else 1 / sqrt(k * FS.toDouble() / N)
      re[k] *= g; im[k] *= g
      if (k in 1 until N / 2) { re[N - k] *= g; im[N - k] *= g }
    }
    fft.inverse(re, im)
    val rms = sqrt(re.sumOf { (it / N) * (it / N) } / N)
    return DoubleArray(N) { 0.2 * (re[it] / N) / rms }
  }

  /** A -90 degree shift of every frequency: the one a matrix encoder puts on the surrounds. */
  private fun hilbert(x: DoubleArray): DoubleArray {
    val re = x.copyOf()
    val im = DoubleArray(N)
    val fft = Fft(N)
    fft.forward(re, im)
    for (k in 1 until N / 2) {
      // X[k] *= -j, and the mirror bin the opposite way, so the result stays real.
      val a = re[k]; re[k] = im[k]; im[k] = -a
      val b = re[N - k]; re[N - k] = -im[N - k]; im[N - k] = b
    }
    re[0] = 0.0; im[0] = 0.0; re[N / 2] = 0.0; im[N / 2] = 0.0
    fft.inverse(re, im)
    return DoubleArray(N) { re[it] / N }
  }

  /** The matrix encoder: C in phase on both sides, Ls and Rs at 90 degrees and opposite polarity between sides. */
  private fun encode(l: DoubleArray, r: DoubleArray, c: DoubleArray, ls: DoubleArray, rs: DoubleArray): Pair<DoubleArray, DoubleArray> {
    val k = 1 / sqrt(2.0)
    val hs = hilbert(ls); val hr = hilbert(rs)
    return DoubleArray(N) { l[it] + k * c[it] + k * hs[it] } to DoubleArray(N) { r[it] + k * c[it] - k * hr[it] }
  }

  private val zero get() = DoubleArray(N)

  private fun interleave(l: DoubleArray, r: DoubleArray) = FloatArray(l.size * 2) { (if (it % 2 == 0) l[it / 2] else r[it / 2]).toFloat() }

  /** The whole input through the upmixer in [block]-frame calls; 6 channels aligned to the input (latency removed). */
  private fun upmix(stereo: FloatArray, params: UpmixParams = UpmixParams(), block: Int = 4096): Array<FloatArray> {
    val frames = stereo.size / 2
    val up = Upmixer(FS, params)
    val padded = stereo.copyOf((frames + LATENCY + SpatialEngine.HOP * 2) * 2)
    val total = padded.size / 2
    val out = ArrayList<FloatArray>()
    val buf = FloatArray((block + SpatialEngine.HOP) * 6)
    var pos = 0
    var written = 0
    while (pos < total) {
      val n = minOf(block, total - pos)
      val w = up.process(padded, pos * 2, n, buf)
      out += buf.copyOf(w * 6)
      written += w
      pos += n
    }
    val all = FloatArray(written * 6)
    var at = 0
    for (part in out) { System.arraycopy(part, 0, all, at, part.size); at += part.size }
    val have = minOf(frames, written - LATENCY)
    return Array(6) { c -> FloatArray(have) { all[6 * (it + LATENCY) + c] } }
  }

  private fun powers(y: Array<FloatArray>) = DoubleArray(6) { c ->
    var s = 0.0
    for (i in SKIP until y[c].size) s += y[c][i].toDouble() * y[c][i]
    s / (y[c].size - SKIP)
  }

  private fun db(x: Double) = 10 * log10(x + 1e-30)

  /** Shares of the power per channel in dB (LFE left out), main channel above -1.5, the rest below [floor]. */
  private fun check(l: DoubleArray, r: DoubleArray, main: Int, floor: Double) {
    val p = powers(upmix(interleave(l, r)))
    p[CH_LFE] = 0.0
    val total = p.sum()
    val share = DoubleArray(6) { db(p[it] / total) }
    val text = share.joinToString { "%.1f".format(it) }
    assertTrue("channel $main not dominant: $text", share[main] > -1.5)
    for (ch in 0 until 6) if (ch != main && ch != CH_LFE) assertTrue("leak into $ch: $text", share[ch] < floor)
  }

  // ---- steering -------------------------------------------------------------------------------

  @Test
  fun centreGoesToC() {
    val (l, r) = encode(zero, zero, pink(1), zero, zero)
    check(l, r, CH_C, -6.0)
  }

  @Test
  fun hardLeftStaysInL() {
    val (l, r) = encode(pink(2), zero, zero, zero, zero)
    check(l, r, CH_L, -15.0)
  }

  @Test
  fun hardRightStaysInR() {
    val (l, r) = encode(zero, pink(3), zero, zero, zero)
    check(l, r, CH_R, -15.0)
  }

  @Test
  fun matrixSurroundGoesToTheRear() {
    val s = pink(4)
    val (l, r) = encode(zero, zero, zero, s, s) // mono surround: Lt = +S, Rt = -S
    val p = powers(upmix(interleave(l, r)))
    p[CH_LFE] = 0.0
    val rear = db((p[CH_LS] + p[CH_RS]) / p.sum())
    assertTrue("rear at $rear dB of the total", rear > -1.5)
  }

  // ---- energy and structure ---------------------------------------------------------------------

  @Test
  fun totalEnergyIsKept() {
    val wide = pink(5) to pink(6)
    val five = encode(pink(7), pink(8), pink(9), pink(10), pink(11))
    for ((name, src) in listOf("wide stereo" to wide, "five sources" to five)) {
      val x = interleave(src.first, src.second)
      val y = upmix(x)
      var eIn = 0.0
      for (i in SKIP until y[0].size) eIn += x[2 * i].toDouble() * x[2 * i] + x[2 * i + 1].toDouble() * x[2 * i + 1]
      var eOut = 0.0
      for (c in intArrayOf(CH_L, CH_R, CH_C, CH_LS, CH_RS)) for (i in SKIP until y[c].size) eOut += y[c][i].toDouble() * y[c][i]
      val d = db(eOut / eIn)
      assertTrue("$name: $d dB", abs(d) < 1.5)
    }
  }

  @Test
  fun everythingOffIsADelayedCopy() {
    val l = pink(12); val r = pink(13)
    val x = interleave(l, r)
    val y = upmix(x, UpmixParams(center = 0.0, surround = 0.0, ambience = 0.0))
    for (i in SKIP until y[0].size) {
      assertEquals(x[2 * i], y[CH_L][i], 1e-5f)
      assertEquals(x[2 * i + 1], y[CH_R][i], 1e-5f)
    }
    for (c in intArrayOf(CH_C, CH_LS, CH_RS, CH_LFE)) for (v in y[c]) assertEquals(0f, v, 0f)
  }

  @Test
  fun blockSizeDoesNotMatter() {
    val x = interleave(pink(14), pink(15)).copyOf(FS * 2)
    val a = upmix(x, block = 100)
    val b = upmix(x, block = 4096)
    val n = minOf(a[0].size, b[0].size)
    assertTrue(n > FS / 2 - 2048)
    for (c in 0 until 6) for (i in 0 until n) assertEquals("ch $c at $i", a[c][i], b[c][i], 1e-6f)
  }

  @Test
  fun latencyIsTheDeclaredValue() {
    assertEquals(512, LATENCY)
    val x = FloatArray(FS * 2)
    x[2 * 1000] = 1f; x[2 * 1000 + 1] = 1f
    val up = Upmixer(FS, UpmixParams(center = 0.0, surround = 0.0, ambience = 0.0))
    val padded = x.copyOf((FS + 2048) * 2)
    val out = FloatArray((FS + 2048 + SpatialEngine.HOP) * 6)
    var written = 0
    var pos = 0
    val buf = FloatArray((4096 + SpatialEngine.HOP) * 6)
    while (pos < padded.size / 2) {
      val n = minOf(4096, padded.size / 2 - pos)
      val w = up.process(padded, pos * 2, n, buf)
      System.arraycopy(buf, 0, out, written * 6, w * 6)
      written += w; pos += n
    }
    var peak = 0; var best = 0f
    for (i in 0 until written) if (abs(out[6 * i]) > best) { best = abs(out[6 * i]); peak = i }
    assertEquals(1000 + LATENCY, peak)
  }

  @Test
  fun lfeHasOnlyBass() {
    val s = pink(16)
    val y = upmix(interleave(s, s), UpmixParams(lfe = true))
    val lfe = y[CH_LFE]
    val len = Integer.highestOneBit(lfe.size - SKIP)
    val re = DoubleArray(len) { lfe[SKIP + it].toDouble() }
    val im = DoubleArray(len)
    Fft(len).forward(re, im)
    var low = 0.0; var high = 0.0
    for (k in 0 until len / 2) {
      val f = k * FS.toDouble() / len
      val p = re[k] * re[k] + im[k] * im[k]
      if (f < 120) low += p
      if (f > 400) high += p
    }
    assertTrue("bass ${db(low / high)} dB above the rest", db(low / high) > 30)
  }

  @Test
  fun nanInputDoesNotPoisonTheOutput() {
    val x = interleave(pink(17), pink(18)).copyOf(FS * 2)
    for (i in 10000 until 10200) x[i] = Float.NaN
    x[20001] = Float.POSITIVE_INFINITY
    val y = upmix(x)
    for (c in 0 until 6) for (v in y[c]) assertTrue(v.isFinite())
  }
}
