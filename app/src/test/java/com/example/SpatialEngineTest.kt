package com.example

import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.LATENCY
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialParams
import com.example.data.audio.spatial.SphericalHeadHrtf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The objective tests of the phase 0 prototype (tools/spatial_proto/tests, PDF section 11.2), run on
 * the Kotlin port, so the app's engine is held to the same numbers.
 */
class SpatialEngineTest {

  private companion object {
    const val FS = 48000
    val hrtf by lazy { SphericalHeadHrtf(FS) }
  }

  private fun db(x: Double) = 10 * log10(x + 1e-30)

  /** Pink noise of [n] samples (a power of two), unit RMS. */
  private fun pink(n: Int, seed: Long = 0): DoubleArray {
    val rng = Random(seed)
    val re = DoubleArray(n) { rng.nextGaussian() }
    val im = DoubleArray(n)
    val fft = Fft(n)
    fft.forward(re, im)
    re[0] = 0.0; im[0] = 0.0
    for (k in 1 until n) {
      val f = min(k, n - k).toDouble() * FS / n
      val s = 1 / sqrt(f)
      re[k] *= s; im[k] *= s
    }
    fft.inverse(re, im)
    val rms = sqrt(re.sumOf { it * it } / n)
    return DoubleArray(n) { re[it] / rms }
  }

  private fun sine(f: Double, n: Int, amp: Double = 0.3) = DoubleArray(n) { amp * sin(2 * PI * f * it / FS) }

  /** Stereo interleaved, constant-power pan p in -1..1. */
  private fun pan(sig: DoubleArray, p: Double): FloatArray {
    val a = (p + 1) * PI / 4
    return FloatArray(sig.size * 2) { if (it % 2 == 0) (sig[it / 2] * cos(a)).toFloat() else (sig[it / 2] * sin(a)).toFloat() }
  }

  /** Whole signal through the engine, output aligned with the input (like render() in the prototype). */
  private fun render(x: FloatArray, params: SpatialParams = SpatialParams(), block: Int = 4096): FloatArray {
    val eng = SpatialEngine(FS, hrtf, params)
    val frames = x.size / 2
    val out = FloatArray((frames + LATENCY + 2 * HOP) * 2)
    var written = 0
    val scratch = FloatArray((block + HOP) * 2)
    var i = 0
    while (i < frames) {
      val n = min(block, frames - i)
      val w = eng.process(x, 2 * i, n, scratch)
      System.arraycopy(scratch, 0, out, written * 2, w * 2)
      written += w
      i += n
    }
    val tail = FloatArray((LATENCY + HOP) * 2)
    val tailOut = FloatArray((LATENCY + 2 * HOP) * 2)
    val w = eng.process(tail, 0, LATENCY + HOP, tailOut)
    System.arraycopy(tailOut, 0, out, written * 2, min(w * 2, out.size - written * 2))
    return out.copyOfRange(LATENCY * 2, (LATENCY + frames) * 2)
  }

  private fun channelPower(y: FloatArray, ch: Int, from: Int, to: Int): Double {
    var s = 0.0
    for (i in from until to) s += y[2 * i + ch].toDouble() * y[2 * i + ch]
    return s / (to - from)
  }

  // ---- reconstruction ----

  @Test
  fun dryPathIsTransparent() {
    val p = pink(1 shl 16)
    val x = FloatArray(p.size * 2) { if (it % 2 == 0) (0.1 * p[it / 2]).toFloat() else (0.07 * p[it / 2]).toFloat() }
    val y = render(x, SpatialParams(intensity = 0.0, loudnessMatch = false))
    var err = 0.0; var sig = 0.0
    for (i in 4096 * 2 until x.size) { err += (y[i] - x[i]).toDouble().let { it * it }; sig += x[i].toDouble() * x[i] }
    // Float output: limited by 24-bit float precision, not by the engine.
    assertTrue("error ${db(err / sig)} dB", db(err / sig) < -100)
  }

  @Test
  fun streamingBlockSizeDoesNotMatter() {
    val x = pan(pink(1 shl 15), 0.3).also { for (i in it.indices) it[i] *= 0.3f }
    val y1 = render(x, block = 100)
    val y2 = render(x, block = 4096)
    for (i in y1.indices) assertEquals(y1[i], y2[i], 1e-6f)
  }

  @Test
  fun latencyConstantMatchesImpulse() {
    val x = FloatArray(FS * 2)
    x[2 * (FS / 2)] = 0.5f; x[2 * (FS / 2) + 1] = 0.5f
    val eng = SpatialEngine(FS, hrtf, SpatialParams(intensity = 0.0, loudnessMatch = false))
    val out = FloatArray((FS + LATENCY + 2 * HOP) * 2)
    val w = eng.process(x, 0, FS, out)
    var best = 0; var peak = 0f
    for (i in 0 until w.coerceAtMost(FS)) if (abs(out[2 * i]) > peak) { peak = abs(out[2 * i]); best = i }
    assertEquals(FS / 2 + LATENCY, best)
  }

  // ---- binaural ----

  @Test
  fun hrtfIldAndItdForLeftSource() {
    val (hl, hr) = hrtf.response(90.0, 0.0)
    val k = Math.round(4000.0 / (FS.toDouble() / NFFT)).toInt()
    val ild = 20 * log10(sqrt(hl[2 * k] * hl[2 * k] + hl[2 * k + 1] * hl[2 * k + 1]) /
      sqrt(hr[2 * k] * hr[2 * k] + hr[2 * k + 1] * hr[2 * k + 1]))
    assertTrue("ILD $ild dB", ild > 10)
    // Group delay difference over 200-800 Hz.
    fun groupDelay(h: DoubleArray): Double {
      val ks = (1 until hrtf.bins).filter { val f = it * FS.toDouble() / NFFT; f > 200 && f < 800 }
      var prev = atan2(h[2 * ks.first() + 1], h[2 * ks.first()])
      var unwrapped = prev
      val phases = mutableListOf(unwrapped)
      for (kk in ks.drop(1)) {
        val ph = atan2(h[2 * kk + 1], h[2 * kk])
        var d = ph - prev
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        unwrapped += d
        phases += unwrapped
        prev = ph
      }
      val dw = 2 * PI * FS / NFFT
      return -(phases.last() - phases.first()) / (dw * (ks.size - 1))
    }
    val itdMs = (groupDelay(hr) - groupDelay(hl)) * 1e3
    assertTrue("ITD $itdMs ms", itdMs > 0.62 && itdMs < 0.85)
  }

  @Test
  fun leftSourceIsLouderInLeftEar() {
    val x = pan(pink(1 shl 17), -0.8).also { for (i in it.indices) it[i] *= 0.2f }
    val y = render(x)
    val frames = y.size / 2
    assertTrue(db(channelPower(y, 0, FS, frames) / channelPower(y, 1, FS, frames)) > 3)
  }

  @Test
  fun monoCompatibility() {
    val n = 1 shl 17
    val x = pan(pink(n), 0.0).also { for (i in it.indices) it[i] *= 0.2f }
    val y = render(x)
    val seg = 1 shl 16
    val from = n - seg
    val fft = Fft(seg)
    fun monoSpectrum(s: FloatArray): DoubleArray {
      val re = DoubleArray(seg) { (s[2 * (from + it)] + s[2 * (from + it) + 1]).toDouble() }
      val im = DoubleArray(seg)
      fft.forward(re, im)
      return DoubleArray(seg / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
    }
    val so = monoSpectrum(y)
    val si = monoSpectrum(x)
    val nb = 50
    val eo = DoubleArray(nb); val ei = DoubleArray(nb); val valid = BooleanArray(nb)
    for (k in so.indices) {
      val f = k * FS.toDouble() / seg
      val b = floor(SpatialEngine.erbRate(f)).toInt()
      eo[b] += so[k]; ei[b] += si[k]
      if (f > 150 && f < 16000) valid[b] = true
    }
    var worst = Double.MAX_VALUE
    for (b in 0 until nb) if (valid[b]) worst = min(worst, db(eo[b] / ei[b]))
    assertTrue("worst band $worst dB", worst > -6.0)
  }

  // ---- safety ----

  @Test
  fun silenceInSilenceOut() {
    val y = render(FloatArray(FS * 2))
    assertEquals(0f, y.maxOf { abs(it) }, 0f)
  }

  @Test
  fun nanInputRecovers() {
    val x = pan(pink(1 shl 17), 0.2).also { for (i in it.indices) it[i] *= 0.2f }
    for (i in 2 * (FS / 2) until 2 * (FS / 2 + 100)) x[i] = Float.NaN
    val y = render(x)
    assertTrue(y.all { it.isFinite() })
    var late = 0f
    for (i in 2 * (FS + FS / 2) until y.size) late = max(late, abs(y[i]))
    assertTrue(late > 0.01f)
  }

  @Test
  fun limiterCeilingOnFullScaleNoise() {
    val rng = Random(1)
    val x = FloatArray(FS * 4) { rng.nextGaussian().coerceIn(-1.0, 1.0).toFloat() }
    val y = render(x)
    val ceil = Math.pow(10.0, -1.0 / 20)
    assertTrue(y.maxOf { abs(it) } <= ceil + 1e-6)
  }

  @Test
  fun loudnessIsPreserved() {
    val rng = Random(5)
    val n = 1 shl 18
    val common = pink(n, 1)
    val x = FloatArray(n * 2) { (0.1 * (common[it / 2] * 0.7 + rng.nextGaussian() * 0.3)).toFloat() }
    val y = render(x)
    val from = n / 2
    val py = channelPower(y, 0, from, n) + channelPower(y, 1, from, n)
    val px = channelPower(x, 0, from, n) + channelPower(x, 1, from, n)
    assertTrue("loudness ${db(py / px)} dB", abs(db(py / px)) < 1.5)
  }

  @Test
  fun intensityGlidesInsteadOfJumping() {
    val eng = SpatialEngine(FS, hrtf, SpatialParams())
    eng.reset(startIntensity = 0.0)
    val x = pan(pink(1 shl 14), 0.3)
    eng.process(x, 0, HOP * 2, FloatArray(HOP * 4))
    assertEquals(2 * SpatialEngine.INTENSITY_STEP, eng.currentIntensity, 1e-9)
  }

  // ---- performance ----

  @Test
  fun frameTimeOnTheJvm() {
    val eng = SpatialEngine(FS, hrtf, SpatialParams())
    val x = pan(pink(1 shl 17), 0.3).also { for (i in it.indices) it[i] *= 0.2f }
    val out = FloatArray(HOP * 4)
    for (i in 0 until 200) eng.process(x, 2 * HOP * (i % 200), HOP, out) // warm-up (JIT)
    val times = LongArray(200)
    for (i in 0 until 200) {
      val t0 = System.nanoTime()
      eng.process(x, 2 * HOP * (i % 200), HOP, out)
      times[i] = System.nanoTime() - t0
    }
    times.sort()
    val medianMs = times[100] / 1e6
    println("Spatial engine: median ${"%.3f".format(medianMs)} ms per frame of ${"%.1f".format(HOP * 1000.0 / FS)} ms")
    assertTrue(medianMs < HOP * 1000.0 / FS)
  }
}
