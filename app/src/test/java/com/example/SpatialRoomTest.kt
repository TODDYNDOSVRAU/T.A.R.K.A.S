package com.example

import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.LATENCY
import com.example.data.audio.spatial.SpatialParams
import com.example.data.audio.spatial.SpatialRoom
import com.example.data.audio.spatial.SphericalHeadHrtf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The virtual room, measured: it has to add distance (early reflections, a tail that dies in a
 * fraction of a second) without colouring or inflating the sound, and it has to switch off exactly.
 */
class SpatialRoomTest {

  private companion object {
    const val FS = 48000
    val hrtf by lazy { SphericalHeadHrtf(FS) }
  }

  private fun db(x: Double) = 10 * log10(x + 1e-30)

  private fun energy(x: DoubleArray, from: Int, to: Int): Double {
    var s = 0.0
    for (i in from until min(to, x.size)) s += x[i] * x[i]
    return s
  }

  /** The room alone, fed with [inL]/[inR] block by block at a constant [level]. */
  private fun roomBlocks(inL: DoubleArray, inR: DoubleArray, level: Double): Pair<DoubleArray, DoubleArray> {
    val room = SpatialRoom(FS, 576)
    val outL = DoubleArray(inL.size)
    val outR = DoubleArray(inL.size)
    var i = 0
    while (i < inL.size) {
      val n = min(HOP, inL.size - i)
      val bl = DoubleArray(n); val br = DoubleArray(n)
      room.process(inL.copyOfRange(i, i + n), inR.copyOfRange(i, i + n), bl, br, n, level, level)
      System.arraycopy(bl, 0, outL, i, n)
      System.arraycopy(br, 0, outR, i, n)
      i += n
    }
    return outL to outR
  }

  private fun impulse(n: Int, at: Int) = DoubleArray(n).also { it[at] = 1.0 }

  @Test
  fun impulseResponseHasEarlyReflectionsAndAShortTail() {
    val n = FS * 2
    val (l, r) = roomBlocks(impulse(n, 100), DoubleArray(n), 1.0)
    val early = energy(l, 0, 100 + 576 + FS * 60 / 1000) + energy(r, 0, 100 + 576 + FS * 60 / 1000)
    val tail = energy(l, 100 + 576 + FS * 60 / 1000, n) + energy(r, 100 + 576 + FS * 60 / 1000, n)
    println("early ${db(early)} dB, tail ${db(tail)} dB (direct = 0 dB)")
    assertTrue("no NaN", l.all { it.isFinite() } && r.all { it.isFinite() })
    assertTrue("early reflections too weak: ${db(early)} dB", db(early) > -16)
    assertTrue("early reflections too strong: ${db(early)} dB", db(early) < -3)
    assertTrue("tail ${db(tail)} dB", db(tail) > -22 && db(tail) < -4)

    // Reverberation time from the Schroeder integral of both ears (T20 x 3).
    val total = (0 until n).map { l[it] * l[it] + r[it] * r[it] }.toDoubleArray()
    val edc = DoubleArray(n)
    var acc = 0.0
    for (i in n - 1 downTo 0) { acc += total[i]; edc[i] = acc }
    val start = 100 + 576 + FS * 60 / 1000
    fun timeAt(dbDrop: Double): Int {
      var i = start
      while (i < n && db(edc[i] / edc[start]) > -dbDrop) i++
      return i
    }
    val t = 3.0 * (timeAt(25.0) - timeAt(5.0)) / 20.0 * 20.0 / FS // seconds for 60 dB, from the 5-25 dB slope
    println("RT60 about $t s")
    assertTrue("RT60 $t s", t > 0.15 && t < 0.9)
  }

  /** Normalised echo density (Abel and Huang): about 1 for a diffuse field, near 0 for a few isolated echoes. */
  private fun echoDensity(h: DoubleArray, centre: Int, half: Int): Double {
    var sq = 0.0
    for (i in centre - half until centre + half) sq += h[i] * h[i]
    val sigma = sqrt(sq / (2 * half))
    var out = 0
    for (i in centre - half until centre + half) if (abs(h[i]) > sigma) out++
    return out.toDouble() / (2 * half) / 0.3173
  }

  @Test
  fun theResponseIsDiffuseNotAFewEchoes() {
    val n = FS
    val at = 100
    val (l, r) = roomBlocks(impulse(n, at), impulse(n, at), 1.0)
    val first = at + 576
    val half = FS * 10 / 1000
    val ned = listOf(20, 40, 60, 100).map { ms ->
      val c = first + FS * ms / 1000
      (echoDensity(l, c, half) + echoDensity(r, c, half)) / 2
    }
    println("echo density at 20/40/60/100 ms: " + ned.joinToString { "%.2f".format(it) })
    assertTrue("echo density at 40 ms is ${ned[1]}", ned[1] > 0.55)
    assertTrue("echo density at 60 ms is ${ned[2]}", ned[2] > 0.75)
  }

  @Test
  fun leftSourceReachesTheLeftEarFirstAndLouder() {
    val n = FS
    val (l, r) = roomBlocks(impulse(n, 100), DoubleArray(n), 1.0)
    val first = 100 + 576 - 60
    val window = FS * 30 / 1000
    val el = energy(l, first, first + window)
    val er = energy(r, first, first + window)
    println("left ${db(el)} dB right ${db(er)} dB in the first 30 ms of reflections")
    assertTrue("left should be louder: ${db(el)} vs ${db(er)}", el > er)
  }

  @Test
  fun levelZeroAddsNothing() {
    val rng = Random(3)
    val n = FS / 2
    val x = DoubleArray(n) { rng.nextGaussian() * 0.1 }
    val (l, r) = roomBlocks(x, x, 0.0)
    assertEquals(0.0, energy(l, 0, n) + energy(r, 0, n), 0.0)
  }

  @Test
  fun roomAddsAboutAThirdOfTheEnergyOnMusicLikeNoise() {
    val rng = Random(5)
    val n = FS * 4
    val a = DoubleArray(n) { rng.nextGaussian() * 0.1 }
    val b = DoubleArray(n) { 0.7 * a[it] + 0.3 * rng.nextGaussian() * 0.1 }
    val (l, r) = roomBlocks(a, b, 1.0)
    val input = energy(a, FS, n) + energy(b, FS, n)
    val room = energy(l, FS, n) + energy(r, FS, n)
    println("room / input = ${db(room / input)} dB")
    assertTrue("room level ${db(room / input)} dB", db(room / input) > -12 && db(room / input) < -3)
  }

  @Test
  fun roomCostsLittleCpu() {
    val rng = Random(9)
    val n = FS * 10
    val a = DoubleArray(n) { rng.nextGaussian() * 0.1 }
    roomBlocks(a.copyOf(FS), a.copyOf(FS), 1.0) // warm-up
    val t0 = System.nanoTime()
    roomBlocks(a, a, 1.0)
    val ms = (System.nanoTime() - t0) / 1e6
    println("room: 10 s of audio in $ms ms")
    assertTrue("10 s of audio took $ms ms", ms < 1500)
  }

  // ---- through the engine ----

  /** Largest normalised cross-correlation of the two ears within +-1 ms (IACC). */
  private fun iacc(y: FloatArray, from: Int, to: Int): Double {
    val max = FS / 1000
    var best = 0.0
    var el = 0.0; var er = 0.0
    for (i in from until to) { el += y[2 * i].toDouble() * y[2 * i]; er += y[2 * i + 1].toDouble() * y[2 * i + 1] }
    val norm = sqrt(el * er) + 1e-30
    for (lag in -max..max) {
      var s = 0.0
      for (i in from until to) {
        val j = i + lag
        if (j in from until to) s += y[2 * i].toDouble() * y[2 * j + 1]
      }
      best = maxOf(best, abs(s) / norm)
    }
    return best
  }

  private fun render(x: FloatArray, params: SpatialParams): FloatArray {
    val eng = SpatialEngine(FS, hrtf, params)
    val frames = x.size / 2
    val out = FloatArray((frames + LATENCY + 2 * HOP) * 2)
    var written = 0
    val scratch = FloatArray((4096 + HOP) * 2)
    var i = 0
    while (i < frames) {
      val n = min(4096, frames - i)
      val w = eng.process(x, 2 * i, n, scratch)
      System.arraycopy(scratch, 0, out, written * 2, w * 2)
      written += w
      i += n
    }
    return out
  }

  @Test
  fun theRoomMakesTheEarsLessAlikeAndKeepsTheLevel() {
    val rng = Random(11)
    val frames = FS * 4
    val mid = DoubleArray(frames) { rng.nextGaussian() * 0.1 }
    val x = FloatArray(frames * 2) { (mid[it / 2] * (if (it % 2 == 0) 1.0 else 0.9)).toFloat() }
    val dry = render(x, SpatialParams(room = 0.0))
    val wet = render(x, SpatialParams(room = 1.0))
    val from = FS; val to = FS * 3
    val iDry = iacc(dry, from, to)
    val iWet = iacc(wet, from, to)
    var pd = 0.0; var pw = 0.0
    for (i in 2 * from until 2 * to) { pd += dry[i].toDouble() * dry[i]; pw += wet[i].toDouble() * wet[i] }
    println("IACC without room $iDry, with room $iWet; level change ${db(pw / pd)} dB")
    assertTrue("IACC $iDry -> $iWet", iWet < iDry - 0.03)
    assertTrue("level change ${db(pw / pd)} dB", abs(db(pw / pd)) < 3.0)
    assertTrue(wet.all { it.isFinite() })
  }

  @Test
  fun theHandSettingsMoveTheRoomAndNeverRebuildTheTables() {
    val rng = Random(21)
    val frames = FS * 4
    val x = FloatArray(frames * 2) { (rng.nextGaussian() * 0.1).toFloat() }
    fun roomPart(p: SpatialParams): Double {
      val wet = render(x, p)
      val dry = render(x, SpatialParams(room = 0.0))
      var e = 0.0
      for (i in 2 * FS * 2 until 2 * FS * 4) { val d = (wet[i] - dry[i]).toDouble(); e += d * d }
      return e
    }
    val base = roomPart(SpatialParams())
    val noTail = roomPart(SpatialParams(roomTail = 0.0))
    val noEarly = roomPart(SpatialParams(roomEarly = 0.0))
    val doubled = roomPart(SpatialParams(roomEarly = 2.0, roomTail = 2.0))
    println("room energy: base ${db(base)}, no tail ${db(noTail)}, no early ${db(noEarly)}, x2 ${db(doubled)} dB")
    assertTrue("tail 0 must lower the room", noTail < base)
    assertTrue("early 0 must lower the room", noEarly < base)
    assertEquals(6.0, db(doubled / base), 0.5)

    // A live change goes through setParams without new tables unless bass, height or ambience moved.
    val eng = SpatialEngine(FS, hrtf, SpatialParams())
    val out = FloatArray((4096 + HOP) * 2)
    eng.process(x, 0, 4096, out)
    eng.setParams(SpatialParams(roomDecay = 0.8, roomPreDelayMs = 40.0, roomDiffusion = 0.3))
    eng.process(x, 8192, 4096, out)
    assertTrue(out.all { it.isFinite() })
    assertEquals(0.8, eng.params.roomDecay, 0.0)
  }

  /** Noise limited to [lo]-[hi] Hz, unit RMS, [n] samples (a power of two). */
  private fun bandNoise(n: Int, lo: Double, hi: Double, seed: Long): DoubleArray {
    val rng = Random(seed)
    val re = DoubleArray(n) { rng.nextGaussian() }
    val im = DoubleArray(n)
    val fft = Fft(n)
    fft.forward(re, im)
    for (k in 0 until n) {
      val f = min(k, n - k).toDouble() * FS / n
      if (f < lo || f > hi) { re[k] = 0.0; im[k] = 0.0 }
    }
    fft.inverse(re, im)
    val rms = sqrt(re.sumOf { it * it } / n)
    return DoubleArray(n) { re[it] / rms }
  }

  private fun roomEnergy(x: FloatArray, focus: Double): Double {
    val wet = render(x, SpatialParams(room = 1.0, voiceFocus = focus))
    val dry = render(x, SpatialParams(room = 0.0))
    var e = 0.0
    for (i in 2 * FS * 2 until 2 * FS * 4) { val d = (wet[i] - dry[i]).toDouble(); e += d * d }
    return e
  }

  @Test
  fun aCentredVoiceStaysOutOfTheRoomWhileWideInstrumentsGetIt() {
    val n = 1 shl 18
    val voice = bandNoise(n, 400.0, 3000.0, 1)
    val centred = FloatArray(n * 2) { (0.1 * voice[it / 2]).toFloat() }
    val a = bandNoise(n, 400.0, 3000.0, 2)
    val b = bandNoise(n, 400.0, 3000.0, 3)
    val wide = FloatArray(n * 2) { (0.1 * (if (it % 2 == 0) a[it / 2] else b[it / 2])).toFloat() }
    val voiceKept = db(roomEnergy(centred, 0.85) / roomEnergy(centred, 0.0))
    val wideKept = db(roomEnergy(wide, 0.85) / roomEnergy(wide, 0.0))
    println("room feed with voice focus: centred voice $voiceKept dB, wide instruments $wideKept dB")
    assertTrue("the centred voice should lose at least 8 dB of room: $voiceKept dB", voiceKept < -8)
    assertTrue("wide instruments should keep the room: $wideKept dB", wideKept > -1.5)
  }
}
