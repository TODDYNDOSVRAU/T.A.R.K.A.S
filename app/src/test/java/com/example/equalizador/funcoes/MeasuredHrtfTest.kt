package com.example

import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.MeasuredHrtf
import com.example.data.audio.spatial.RoomParams
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * The measured head (SADIE II D1, KU100) in the virtualizer: the asset reader, left and right, the final
 * filters against the prototype's (ref_filters_<rate>.bin: float32, 6 channels x 2 ears x 1025 samples,
 * room 0.35), and the fall-back to the spherical head when there is no usable file.
 */
class MeasuredHrtfTest {

  private fun asset(name: String): File = File("src/main/assets/$name")
  private fun open(name: String) = asset(name).takeIf { it.exists() }?.inputStream()
  private fun measured(rate: Int) = MeasuredHrtf.load(rate, ::open) { throw AssertionError(it) }!!

  private fun reference(rate: Int): FloatArray {
    val bytes = javaClass.getResourceAsStream("/hrtf/ref_filters_$rate.bin")!!.readBytes()
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(bytes.size / 4) { b.getFloat(4 * it) }
  }

  private fun impulseResponse(f: SurroundFilters, index: Int, fft: Fft): DoubleArray {
    val n = SpatialEngine.NFFT
    val re = DoubleArray(n); val im = DoubleArray(n)
    for (k in 0 until SpatialEngine.BINS) { re[k] = f.re[index][k]; im[k] = f.im[index][k] }
    im[0] = 0.0; im[SpatialEngine.BINS - 1] = 0.0
    for (k in 1 until SpatialEngine.BINS - 1) { re[n - k] = re[k]; im[n - k] = -im[k] }
    fft.inverse(re, im)
    return DoubleArray(n) { re[it] / n }
  }

  @Test
  fun readsTheAssets() {
    assertEquals(76, measured(48000).directions)
    assertEquals(91, measured(44100).directions)
    assertEquals(23, measured(96000).directions)
    assertEquals(94_412L, asset("hrtf/hrtf_ku100_96000.bin").length())
    assertEquals(311_924L, asset("hrtf/hrtf_ku100_48000.bin").length())
    assertEquals(373_484L, asset("hrtf/hrtf_ku100_44100.bin").length())
  }

  @Test
  fun leftAndRightEars() {
    val h = measured(48000)
    fun energy(a: FloatArray) = a.sumOf { it.toDouble() * it }
    // A source on the left (+30°) is louder in the left ear; on the right (-30°), in the right ear.
    val l = h.nearest(30.0, 0.0); val r = h.nearest(-30.0, 0.0)
    assertTrue(energy(h.impulse(l, 0)) > energy(h.impulse(l, 1)))
    assertTrue(energy(h.impulse(r, 1)) > energy(h.impulse(r, 0)))
    // The pair mirror each other.
    assertEquals(energy(h.impulse(l, 0)), energy(h.impulse(r, 1)), 0.05 * energy(h.impulse(l, 0)))
    // Frontal sound arrives at the common delay: the peak of the centre is near sample 64.
    val c = h.impulse(h.nearest(0.0, 0.0), 0)
    val peak = c.indices.maxByOrNull { abs(c[it]) }!!
    assertTrue("peak at $peak", peak in 60..72)
  }

  private fun checkAgainstPrototype(rate: Int) {
    val ref = reference(rate)
    val len = SurroundRoom.FILTER_LEN
    assertEquals(6 * 2 * len, ref.size)
    // The very function SpatialControl builds the app's filters with: the room must be in them.
    val f = SurroundFilters.production(rate, SphericalHeadHrtf(rate), ::open) { throw AssertionError(it) }
    assertEquals(RoomParams.APPROVED, f.room)
    val fft = Fft(SpatialEngine.NFFT)
    val peak = ref.maxOf { abs(it) }
    var worst = 0.0
    for (idx in 0 until 12) {
      val h = impulseResponse(f, idx, fft)
      for (n in 0 until len) worst = maxOf(worst, abs(h[n] - ref[idx * len + n]))
    }
    assertTrue("$rate Hz: worst difference ${worst / peak} of the peak", worst < 1e-4 * peak)
  }

  @Test fun filtersMatchPrototypeAt48k() = checkAgainstPrototype(48000)
  @Test fun filtersMatchPrototypeAt44k() = checkAgainstPrototype(44100)
  @Test fun filtersMatchPrototypeAt96k() = checkAgainstPrototype(96000)

  @Test
  fun noAssetFor88kFallsBackToTheSphericalHead() {
    val problems = ArrayList<String>()
    assertNull(MeasuredHrtf.load(88200, ::open) { problems += it })
    assertEquals(1, problems.size)
    assertNull(MeasuredHrtf.load(48000, { null }) { problems += it })
  }

  /** Without a usable measured file the production filters are the spherical head's, still with the room. */
  @Test
  fun fallbackKeepsTheRoom() {
    val spherical = SphericalHeadHrtf(88200)
    val problems = ArrayList<String>()
    val f = SurroundFilters.production(88200, spherical, ::open) { problems += it }
    assertEquals(1, problems.size)
    assertEquals(RoomParams.APPROVED, f.room)
    val dry = SurroundFilters(88200, spherical)
    var diff = 0.0
    for (k in 0 until SpatialEngine.BINS) diff = maxOf(diff, abs(f.re[0][k] - dry.re[0][k]))
    assertTrue("room changes the filter ($diff)", diff > 0.01)
    // No opener at all (the app has not set one): same fall-back.
    assertEquals(RoomParams.APPROVED, SurroundFilters.production(88200, spherical, null).room)
  }

  private fun load(bytes: ByteArray, rate: Int = 48000): Pair<MeasuredHrtf?, List<String>> {
    val problems = ArrayList<String>()
    return MeasuredHrtf.load(rate, { ByteArrayInputStream(bytes) }) { problems += it } to problems
  }

  @Test
  fun invalidAssetsAreRefusedAndLogged() {
    val good = asset("hrtf/hrtf_ku100_48000.bin").readBytes()
    assertNotNull(load(good).first)
    val cases = mapOf(
      "magic" to good.copyOf().also { it[0] = 'X'.code.toByte() },
      "version" to good.copyOf().also { it[4] = 2 },
      "truncated" to good.copyOf(good.size - 4),
      "extra bytes" to good.copyOf(good.size + 4),
      "empty" to ByteArray(0),
      "short header" to good.copyOf(10),
      "not a number" to good.copyOf().also { it[23] = 0x7F; it[22] = 0xC0.toByte() }
    )
    for ((name, bytes) in cases) {
      val (h, problems) = load(bytes)
      assertNull(name, h)
      assertEquals(name, 1, problems.size)
    }
    // A file for another rate is refused too.
    assertNull(load(good, rate = 44100).first)
  }
}
