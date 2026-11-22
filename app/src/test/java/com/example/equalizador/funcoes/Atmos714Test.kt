package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.data.audio.spatial.RoomParams
import com.example.data.audio.spatial.SpatialAudioProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor.Companion.DELAY
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.audio.spatial.SphericalHeadHrtf
import com.example.data.audio.spatial.SurroundFilters
import com.example.data.audio.spatial.SurroundVirtualizer
import com.example.data.audio.spatial.UpmixParams
import com.example.data.audio.spatial.UpmixVirtualizer
import com.example.data.audio.spatial.Upmixer
import com.example.data.audio.spatial.Upmixer.Companion.LATENCY
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * Verifies the 7.1.4 overhead dome (SurroundFilters 12ch, UpmixParams.ATMOS_714, UpmixVirtualizer,
 * and native 12-channel SpatialAudioProcessor rendering).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Atmos714Test {
  private companion object {
    const val FS = 48000
    const val N = 65536
    const val SKIP = 12288
  }

  private val hrtf: SphericalHeadHrtf by lazy { SphericalHeadHrtf(FS, accurateDiffraction = true) }

  @After
  fun tearDown() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
  }

  private fun noise(seed: Long) = Random(seed).let { r -> DoubleArray(N) { 0.2 * r.nextGaussian() } }

  private fun voiceBand(seed: Long): DoubleArray {
    val r = Random(seed)
    val f = DoubleArray(60) { 300.0 + (3000.0 - 300.0) * it / 59 + r.nextDouble() * 20 }
    val ph = DoubleArray(60) { r.nextDouble() * 2 * PI }
    return DoubleArray(N) { n -> var s = 0.0; for (k in 0 until 60) s += sin(2 * PI * f[k] * n / FS + ph[k]); 0.2 * s / 7.75 }
  }

  private fun interleave(l: DoubleArray, r: DoubleArray) = FloatArray(2 * N) { (if (it % 2 == 0) l[it / 2] else r[it / 2]).toFloat() }
  private fun db(x: Double) = 10 * log10(x.coerceAtLeast(1e-30))

  private fun upmix(stereo: FloatArray, params: UpmixParams): Array<FloatArray> {
    val up = Upmixer(FS, params)
    val ch = up.channels
    val padded = stereo.copyOf(stereo.size + 2 * (LATENCY + SpatialEngine.HOP * 2))
    val total = padded.size / 2
    val out = FloatArray((total + SpatialEngine.HOP) * ch)
    var written = 0
    var pos = 0
    val buf = FloatArray((4096 + SpatialEngine.HOP) * ch)
    while (pos < total) {
      val n = minOf(4096, total - pos)
      val w = up.process(padded, pos * 2, n, buf)
      System.arraycopy(buf, 0, out, written * ch, w * ch)
      written += w
      pos += n
    }
    val have = minOf(N, written - LATENCY)
    return Array(ch) { c -> FloatArray(have) { out[ch * (it + LATENCY) + c] } }
  }

  private fun power(x: FloatArray): Double {
    var s = 0.0
    for (i in SKIP until x.size) s += x[i].toDouble() * x[i]
    return s / (x.size - SKIP)
  }

  @Test
  fun surroundFilters12HasElevatedSymmetricOverheadPairs() {
    val f = SurroundFilters.studio(FS, hrtf, open = null, channels = 12)
    assertEquals(12, f.channels)
    for (ch in 0..7) assertEquals(0.0, f.elevations[ch], 0.0)
    for (ch in 8..11) assertEquals(SurroundFilters.HEIGHT_EL, f.elevations[ch], 0.0)

    // L/R symmetry on TFL(8)/TFR(9) and TBL(10)/TBR(11): left ear of TFL == right ear of TFR.
    for (pair in listOf(SurroundFilters.CH_TFL to SurroundFilters.CH_TFR, SurroundFilters.CH_TBL to SurroundFilters.CH_TBR)) {
      val (lCh, rCh) = pair
      for (k in 0 until SpatialEngine.BINS) {
        assertEquals("re sym ch $lCh/$rCh bin $k", f.re[lCh * 2][k], f.re[rCh * 2 + 1][k], 1e-6)
        assertEquals("im sym ch $lCh/$rCh bin $k", f.im[lCh * 2][k], f.im[rCh * 2 + 1][k], 1e-6)
      }
    }

    // Elevated TFL (+35°, +45° el) differs from ear-level L (+30°, 0° el).
    var diff = 0.0
    for (k in 0 until SpatialEngine.BINS) {
      diff += abs(f.re[SurroundFilters.CH_TFL * 2][k] - f.re[0][k])
    }
    assertTrue("height filter must differ from ear-level L (diff=$diff)", diff > 1.0)
  }

  @Test
  fun atmos714UpmixLiftsDiffuseAmbienceOverheadAndKeepsVoiceAndBassBelow() {
    assertEquals(12, Upmixer(FS, UpmixParams.ATMOS_714).channels)

    // 1. Diffuse uncorrelated noise reaches all 4 overhead speakers.
    val amb = upmix(interleave(noise(41), noise(42)), UpmixParams.ATMOS_714)
    val topFront = power(amb[8]) + power(amb[9])
    val topBack = power(amb[10]) + power(amb[11])
    assertTrue("top fronts carry diffuse ambience: ${db(topFront)} dB", topFront > 1e-4)
    assertTrue("top backs carry diffuse ambience: ${db(topBack)} dB", topBack > 1e-4)
    assertTrue("top fronts carry slightly more than top backs (60/40): ${db(topFront)} vs ${db(topBack)}", topFront > topBack)

    // 2. Centred voice stays anchored in C, not in the ceiling (< -20 dB of C).
    val v = voiceBand(43)
    val voice = upmix(interleave(v, v), UpmixParams.ATMOS_714)
    val cPow = power(voice[2])
    val topVoice = power(voice[8]) + power(voice[9]) + power(voice[10]) + power(voice[11])
    assertTrue("voice stays out of heights: top ${db(topVoice)} vs C ${db(cPow)} dB", topVoice < cPow * 0.01)

    // 3. 60 Hz bass stays in LFE, not in the ceiling (< -20 dB of LFE).
    val bass = DoubleArray(N) { 0.2 * sin(2 * PI * 60.0 * it / FS) }
    val low = upmix(interleave(bass, bass), UpmixParams.ATMOS_714)
    val lfePow = power(low[3])
    val topBass = power(low[8]) + power(low[9]) + power(low[10]) + power(low[11])
    assertTrue("bass stays out of heights: top ${db(topBass)} vs LFE ${db(lfePow)} dB", topBass < lfePow * 0.01)

    // 4. Total energy conserved within ±1.5 dB across all 12 channels and zero clipping (same seeds as SpatialRolesTest).
    for (seed in 10L..12L) {
      val a = noise(seed); val b = noise(seed + 100)
      val input = if (seed == 10L) interleave(a, a) else interleave(a, b)
      val y = upmix(input, UpmixParams.ATMOS_714)
      var pout = 0.0
      for (c in y.indices) pout += power(y[c])
      var pin = 0.0
      for (j in SKIP until N) pin += input[2 * j].toDouble() * input[2 * j] + input[2 * j + 1].toDouble() * input[2 * j + 1]
      pin /= (N - SKIP)
      assertEquals("seed $seed 12ch energy conservation", 0.0, db(pout) - db(pin), 1.5)
      for (c in y.indices) for (s in y[c]) assertTrue("seed $seed ch $c sample $s finite & unclipped", s.isFinite() && abs(s) < 1.0f)
    }
  }

  @Test
  fun atmos714UpmixVirtualizerRendersWithZeroExtraLatencyAndHeadTracking() {
    val filters = SurroundFilters.studio(FS, hrtf, open = null, channels = 12)
    val virt = UpmixVirtualizer(FS, filters, params = UpmixParams.ATMOS_714, headHrtf = hrtf, studioRoomTail = RoomParams.APPROVED_LEVEL)
    assertEquals(SurroundVirtualizer.LATENCY, virt.latency)

    val input = interleave(noise(60), noise(61))
    val out0 = FloatArray((N + SpatialEngine.HOP) * 2)
    val w0 = virt.process(input, 0, 8192, out0)
    assertTrue("produced hops", w0 >= 7680)
    for (i in 0 until w0 * 2) assertTrue("finite output", out0[i].isFinite())

    // Rotate head 30° left and verify binaural output responds cleanly.
    virt.reset(startIntensity = 1.0)
    virt.liveYaw = 30.0
    val outYaw = FloatArray((N + SpatialEngine.HOP) * 2)
    val wYaw = virt.process(input, 0, 8192, outYaw)
    assertEquals(w0, wYaw)
    var diff = 0.0
    for (i in 2048 * 2 until w0 * 2) diff += abs(outYaw[i] - out0[i])
    assertTrue("head tracking rotates 7.1.4 scene (diff=$diff)", diff > 1.0)
  }

  @Test
  fun spatialAudioProcessorAccepts12Channel714Natively() {
    val format12 = AudioProcessor.AudioFormat(FS, 12, C.ENCODING_PCM_FLOAT)
    val proc = SpatialAudioProcessor()
    val outFmt = proc.configure(format12)
    assertNotEquals(AudioProcessor.AudioFormat.NOT_SET, outFmt)
    assertEquals(2, outFmt.channelCount)
    assertEquals(FS, outFmt.sampleRate)

    // 1. Off: output equals downmix12 delayed by DELAY frames.
    SpatialControl.configure(false, SpatialPreset.CINEMA, 1.0)
    proc.flush()
    val frames = 4096
    val in12 = FloatArray(frames * 12) { i ->
      val n = i / 12; val ch = i % 12
      (0.08 * sin(2 * PI * (180.0 + 130.0 * ch) * n / FS)).toFloat()
    }
    val expectedDry = FloatArray(frames * 2)
    SurroundVirtualizer.downmix12(in12, 0, frames, expectedDry)

    val buf = ByteBuffer.allocateDirect(in12.size * 4).order(ByteOrder.nativeOrder())
    in12.forEach { buf.putFloat(it) }
    buf.flip()
    proc.queueInput(buf)
    val outBuf = proc.output
    val outDry = FloatArray(outBuf.remaining() / 4) { outBuf.getFloat() }
    assertEquals(frames * 2, outDry.size)
    for (i in DELAY * 2 until outDry.size) {
      assertEquals(expectedDry[i - DELAY * 2], outDry[i], 1e-6f)
    }

    // 2. On: renders through 12-channel KU100/Spherical SurroundVirtualizer.
    SpatialControl.configure(true, SpatialPreset.CINEMA, 1.0)
    val deadline = System.currentTimeMillis() + 20_000
    while (SpatialControl.surroundFor(FS) == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
    assertTrue("12ch surround engine ready", SpatialControl.surroundFor(FS) != null)
    proc.flush()
    var wetDiff = 0.0
    repeat(4) {
      val b = ByteBuffer.allocateDirect(in12.size * 4).order(ByteOrder.nativeOrder())
      in12.forEach { b.putFloat(it) }
      b.flip()
      proc.queueInput(b)
      val ob = proc.output
      val arr = FloatArray(ob.remaining() / 4) { ob.getFloat() }
      for (i in DELAY * 2 until arr.size) {
        assertTrue(arr[i].isFinite())
        wetDiff += abs(arr[i] - expectedDry[i - DELAY * 2])
      }
    }
    assertTrue("12ch binaural renderer diverged from plain downmix (diff=$wetDiff)", wetDiff > 10.0)
  }

  @Test
  fun enablingInAlreadySelectedPresetAndMovingSlidersAppliesImmediatelyWithoutSwitchingModes() {
    val format2 = AudioProcessor.AudioFormat(FS, 2, C.ENCODING_PCM_FLOAT)
    val proc = SpatialAudioProcessor()
    proc.configure(format2)
    proc.flush()

    val block = 2048
    var pos = 0
    fun nextStereo(): FloatArray {
      val s = FloatArray(block * 2) { i ->
        val n = pos + i / 2
        val v = 0.2 * sin(2 * PI * 440 * n / FS) + 0.15 * sin(2 * PI * 2400 * n / FS)
        (if (i % 2 == 0) v else v * 0.55).toFloat()
      }
      pos += block
      return s
    }
    fun feed(input: FloatArray): FloatArray {
      val b = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
      input.forEach { b.putFloat(it) }
      b.flip()
      proc.queueInput(b)
      val ob = proc.output
      return FloatArray(ob.remaining() / 4) { ob.getFloat() }
    }
    fun meanDiffFromDry(input: FloatArray, out: FloatArray): Double {
      var d = 0.0; var c = 0
      for (i in DELAY * 2 until out.size) { d += abs(out[i] - input[i - DELAY * 2]); c++ }
      return d / c
    }

    // 1. Start OFF with CINEMA already selected, play dry audio.
    SpatialControl.configure(false, SpatialPreset.CINEMA, 1.0)
    repeat(4) { feed(nextStereo()) }

    // 2. Toggle ON in the ALREADY selected preset (CINEMA) — never switch modes!
    SpatialControl.configure(true, SpatialPreset.CINEMA, 1.0)
    val deadline = System.currentTimeMillis() + 20_000
    var diffOn = 0.0
    while (System.currentTimeMillis() < deadline && diffOn < 0.01) {
      val inp = nextStereo()
      val out = feed(inp)
      diffOn = meanDiffFromDry(inp, out)
      if (diffOn < 0.01) Thread.sleep(10)
    }
    // Let intensity glide all the way to 1.0 before comparing against 0.2.
    repeat(8) {
      val inp = nextStereo()
      val out = feed(inp)
      diffOn = meanDiffFromDry(inp, out)
    }
    assertTrue("CINEMA must activate when toggled ON without switching modes (diff=$diffOn)", diffOn >= 0.01)

    // 3. Move Intensity slider down to 0.2 without switching modes -> output must move closer to dry.
    SpatialControl.setIntensity(0.2)
    var diffLow = 0.0
    repeat(12) {
      val inp = nextStereo()
      val out = feed(inp)
      diffLow = meanDiffFromDry(inp, out)
    }
    assertTrue("Intensity 0.2 must reduce wet diff vs 1.0 ($diffLow < $diffOn)", diffLow < diffOn * 0.5)

    // 4. Restore Intensity 1.0, move atmosRoomTail from 0.0 to 2.0 on wideband noise -> output must change.
    SpatialControl.setIntensity(1.0)
    val rng = Random(99)
    val wideBlock = FloatArray(block * 2) { (0.15 * rng.nextGaussian()).toFloat() }
    SpatialControl.setAtmosRoomTail(0.0)
    var outDryRoom = FloatArray(0)
    repeat(12) { outDryRoom = feed(wideBlock) }
    SpatialControl.setAtmosRoomTail(2.0)
    var outWetRoom = FloatArray(0)
    repeat(12) { outWetRoom = feed(wideBlock) }
    var tailDelta = 0.0
    for (i in outDryRoom.indices) tailDelta += abs(outWetRoom[i] - outDryRoom[i])
    assertTrue("atmosRoomTail 2.0 must diverge from 0.0 (delta=$tailDelta)", tailDelta / outDryRoom.size > 0.005)

    // Restore defaults.
    SpatialControl.setAtmosRoomTail(RoomParams.APPROVED_LEVEL)
  }
}
