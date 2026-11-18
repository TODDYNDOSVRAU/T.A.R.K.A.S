package com.example

import com.example.data.audio.spatial.Fft
import com.example.data.audio.spatial.RoomParams
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundRoom
import com.example.data.audio.spatial.SurroundVirtualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The virtual room of the 5.1 virtualizer (SurroundRoom), against numbers taken from the Python prototype
 * (tools/spatial_proto, Virtualizer51 with room_level 0.35 at 48 kHz) and its exactness checks.
 * Channel order: L R C LFE Ls Rs; filter index = channel * 2 + ear.
 */
class SurroundRoomTest {

  private companion object {
    const val FS = 48000
    val BINS_CHECKED = intArrayOf(5, 40, 120, 300, 700, 1000)
    val hrtf: SphericalHeadHrtf by lazy { SphericalHeadHrtf(FS) }
    val dry: SurroundFilters by lazy { SurroundFilters(FS, hrtf) }
    val room: SurroundFilters by lazy { SurroundFilters(FS, hrtf, RoomParams.APPROVED) }
  }

  private fun assertBins(name: String, f: SurroundFilters, index: Int, re: DoubleArray, im: DoubleArray) {
    for ((i, b) in BINS_CHECKED.withIndex()) {
      assertEquals("$name re bin $b", re[i], f.re[index][b], 1e-6)
      assertEquals("$name im bin $b", im[i], f.im[index][b], 1e-6)
    }
  }

  @Test
  fun dryFiltersMatchPython() {
    assertBins("L left", dry, 0,
      doubleArrayOf(0.4978129212083762, 0.6593532982118572, -0.6912689543981053, -0.5709215111435744, 0.19320773227741814, -0.0328405934188292),
      doubleArrayOf(-0.5735302535599769, -0.5263663231017921, -0.6863803363898062, -0.10478110044123633, 1.0381168539011993, -1.0760952551253689))
    assertBins("Ls right", dry, 9,
      doubleArrayOf(0.169003878781855, -0.33360643589947175, 0.02754675268049796, -0.02313049797926252, 0.07400244363103978, -0.05624870125125693),
      doubleArrayOf(-0.5938409232237855, 0.356771987837671, 0.24517905730751494, 0.06221866156667949, -0.03237820529360667, 0.047753131196966635))
  }

  @Test
  fun roomFiltersMatchPython() {
    assertBins("L left", room, 0,
      doubleArrayOf(0.42603141237366626, 0.6878510876297274, -0.7340072281844017, 0.08917473570462198, 0.3970438871971467, 0.0947069582979964),
      doubleArrayOf(-0.500978194870155, -0.8377418643670845, -0.6440770738442849, 0.004990397618058774, 1.0922773792911988, -0.6796541576212868))
    assertBins("Ls right", room, 9,
      doubleArrayOf(0.11785100421222336, -0.4141491650475553, -0.13060189766552033, -0.0704536835029941, 0.18615648564328627, 0.013839352400056466),
      doubleArrayOf(-0.5122767452852732, 0.16530937676840414, 0.24367433875867087, 0.1582610160216499, 0.025145923221676, 0.037093910130280204))
    assertBins("C left", room, 4,
      doubleArrayOf(0.3568365618359774, 0.13006804380384168, 0.457336674974178, -0.5042149553737095, 0.21570344725242624, 0.23335826940683937),
      doubleArrayOf(-0.6713812120479589, -1.0868965416201504, 0.5014881101130979, -0.4959444003639976, 0.44785555113646447, -0.8612433468595787))
  }

  @Test
  fun reflectionPathsMatchPython() {
    val paths = SurroundRoom.paths(30.0, 0.0, RoomParams.APPROVED, FS)
    assertEquals(15, paths.size)
    assertEquals(172.01847, paths[0].azimuth, 1e-4); assertEquals(728, paths[0].delay); assertEquals(0.08132144, paths[0].gain, 1e-7)
    assertEquals(-21.022146, paths[1].elevation, 1e-4); assertEquals(656, paths[1].delay)
    for (p in paths) {
      assertTrue(p.delay in 49..SurroundRoom.MAX_DELAY)
      assertTrue(p.gain < 1.0)
    }
  }

  @Test
  fun roomIsOffByDefaultAndTheLfeGetsNone() {
    assertFalse(RoomParams().enabled)
    assertFalse(SurroundFilters(FS, hrtf).room.enabled)
    for (ear in 0..1) for (k in 0 until SpatialEngine.BINS) {
      assertEquals(dry.re[6 + ear][k], room.re[6 + ear][k], 0.0)
      assertEquals(dry.im[6 + ear][k], room.im[6 + ear][k], 0.0)
    }
  }

  @Test
  fun everyFilterFitsInTheExactLength() {
    val fft = Fft(SpatialEngine.NFFT)
    for (f in listOf(dry, room)) for (idx in 0 until 12) {
      if (idx in 6..7) continue // the LFE is a pure delay and is not cut
      val h = impulseResponse(f, idx, fft)
      var tail = 0.0
      for (n in SurroundRoom.FILTER_LEN until SpatialEngine.NFFT) tail = maxOf(tail, abs(h[n]))
      assertTrue("filter $idx tail $tail", tail < 1e-12)
    }
  }

  /** Impulse response (NFFT samples) of filter [index]: inverse FFT of its spectrum, Hermitian completed. */
  private fun impulseResponse(f: SurroundFilters, index: Int, fft: Fft): DoubleArray {
    val n = SpatialEngine.NFFT
    val re = DoubleArray(n); val im = DoubleArray(n)
    for (k in 0 until SpatialEngine.BINS) { re[k] = f.re[index][k]; im[k] = f.im[index][k] }
    im[0] = 0.0; im[SpatialEngine.BINS - 1] = 0.0
    for (k in 1 until SpatialEngine.BINS - 1) { re[n - k] = re[k]; im[n - k] = -im[k] }
    fft.inverse(re, im)
    return DoubleArray(n) { re[it] / n }
  }

  /**
   * An impulse in one speaker comes out as that speaker's filter, exactly, WIN - HOP samples after the
   * stream's latency is accounted for: the filter itself carries the HRTF delay.
   */
  @Test
  fun impulseThroughTheStreamEqualsTheFilter() {
    val frames = 8192
    val fft = Fft(SpatialEngine.NFFT)
    for ((name, f) in listOf("dry" to dry, "room" to room)) for (ch in intArrayOf(0, 2, 5)) {
      val x = FloatArray(frames * 6)
      x[ch] = 1f
      val v = SurroundVirtualizer(f)
      v.targetIntensity = 1.0; v.reset(1.0)
      val y = ArrayList<Float>()
      val buf = FloatArray((1024 + SpatialEngine.HOP) * 2)
      var pos = 0
      while (pos < frames) {
        val n = minOf(1024, frames - pos)
        val w = v.process(x, pos * 6, n, buf)
        for (i in 0 until w * 2) y += buf[i]
        pos += n
      }
      val shift = SurroundVirtualizer.LATENCY - SpatialEngine.HRTF_DELAY
      for (ear in 0..1) {
        val h = impulseResponse(f, ch * 2 + ear, fft)
        var worst = 0.0
        for (n in 0 until SurroundRoom.FILTER_LEN) {
          val got = y[2 * (n + shift) + ear].toDouble()
          worst = maxOf(worst, abs(got - h[n]))
        }
        assertTrue("$name ch$ch ear$ear: worst error $worst", worst < 1e-5)
      }
    }
  }

  @Test
  fun latencyIsUnchanged() {
    assertEquals(576, SurroundVirtualizer.LATENCY)
    assertEquals(576, SurroundVirtualizer(room).latency)
  }
}
