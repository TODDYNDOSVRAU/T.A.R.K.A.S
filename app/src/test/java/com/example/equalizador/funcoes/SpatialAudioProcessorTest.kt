package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.data.audio.spatial.SpatialAudioProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor.Companion.DELAY
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Tarkas Spatial inside the player's chain: the same delay on and off, an exact copy of the input when
 * off, and switching on or off without a jump in the sound.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpatialAudioProcessorTest {

  private val fs = 48000
  private val format = AudioProcessor.AudioFormat(fs, 2, C.ENCODING_PCM_FLOAT)

  @After
  fun off() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
  }

  /** A slow two-tone signal, stereo interleaved, panned a little to the left. */
  private fun signal(frames: Int, start: Int = 0) = FloatArray(frames * 2) {
    val n = start + it / 2
    val v = 0.2 * sin(2 * PI * 440 * n / fs) + 0.1 * sin(2 * PI * 1300 * n / fs)
    (if (it % 2 == 0) v * 1.0 else v * 0.6).toFloat()
  }

  private fun run(p: SpatialAudioProcessor, input: FloatArray): FloatArray {
    val buf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
    input.forEach { buf.putFloat(it) }
    buf.flip()
    p.queueInput(buf)
    val out = p.output
    val result = FloatArray(out.remaining() / 4)
    for (i in result.indices) result[i] = out.getFloat()
    return result
  }

  private fun processorReady(): SpatialAudioProcessor {
    val p = SpatialAudioProcessor()
    p.configure(format)
    p.flush()
    return p
  }

  private fun waitForEngine() {
    val deadline = System.currentTimeMillis() + 20_000
    while (SpatialControl.engineFor(fs) == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
    assertTrue("engine not ready", SpatialControl.engineFor(fs) != null)
  }

  @Test
  fun offIsTheInputDelayedExactly() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    val p = processorReady()
    val input = signal(8192)
    val out = run(p, input)
    assertEquals(input.size, out.size)
    for (i in 0 until DELAY * 2) assertEquals(0f, out[i], 0f)
    for (i in DELAY * 2 until out.size) assertEquals(input[i - DELAY * 2], out[i], 0f)
  }

  @Test
  fun switchingOnAndOffNeverJumps() {
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    waitForEngine()
    val p = processorReady()
    val block = 1000
    val outputs = ArrayList<Float>()
    var pos = 0
    // On for a while, off, then on again, in blocks like the player's.
    repeat(60) { i ->
      if (i == 25) SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
      if (i == 45) SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
      run(p, signal(block, pos)).forEach { outputs += it }
      pos += block
    }
    val out = outputs.toFloatArray()
    assertTrue(out.all { it.isFinite() })
    // The largest step between two samples of a channel stays on the scale of the music itself
    // (the 0.3-amplitude tones move at most ~0.06 per sample); a switch click would be far larger.
    var maxStep = 0f
    for (ch in 0..1) for (i in 1 until out.size / 2) maxStep = max(maxStep, abs(out[2 * i + ch] - out[2 * (i - 1) + ch]))
    assertTrue("step $maxStep", maxStep < 0.15f)
    val ref = signal(60 * block)
    // While on, the engine really renders: the output is no longer the delayed input.
    var diff = 0.0
    for (i in 15 * block until 22 * block) diff += abs(out[2 * i] - ref[2 * (i - DELAY)])
    assertTrue("engine never rendered (diff $diff)", diff / (7 * block) > 0.01)
    // Well after the switch off, the output is the input again, delayed and untouched.
    val from = 40 * block
    for (i in from until 44 * block) {
      assertEquals(ref[2 * (i - DELAY)], out[2 * i], 1e-6f)
    }
  }

  @Test
  fun switchingOnAgainAfterTheEnginesWereDroppedStillWorks() {
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    val p = processorReady()
    waitForEngine()
    var pos = 0
    fun chunk(): Pair<FloatArray, FloatArray> {
      val input = signal(4096, pos)
      pos += 4096
      return input to run(p, input)
    }
    repeat(40) { chunk() }
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    repeat(10) { chunk() }
    // The control drops its engines 1.5 s after the switch-off.
    Thread.sleep(2000)
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    var differs = 0.0
    val deadline = System.currentTimeMillis() + 20_000
    while (System.currentTimeMillis() < deadline && differs < 0.01) {
      val (input, out) = chunk()
      // The dry sound is the input DELAY frames earlier; the effect moves away from it.
      var d = 0.0
      var count = 0
      for (i in DELAY * 2 until out.size) { d += abs(out[i] - input[i - DELAY * 2]); count++ }
      differs = d / count
      Thread.sleep(5)
    }
    assertTrue("the effect never came back, mean difference $differs", differs >= 0.01)
  }

  // ---- 5.1 source: comes out as stereo ----

  private val format51 = AudioProcessor.AudioFormat(fs, 6, C.ENCODING_PCM_FLOAT)

  private fun signal51(frames: Int, start: Int = 0) = FloatArray(frames * 6) {
    val n = start + it / 6
    val ch = it % 6
    (0.1 * sin(2 * PI * (200.0 + 170 * ch) * n / fs)).toFloat()
  }

  private fun ready51(): SpatialAudioProcessor {
    val p = SpatialAudioProcessor()
    val out = p.configure(format51)
    assertEquals(AudioProcessor.AudioFormat(fs, 2, C.ENCODING_PCM_FLOAT), out)
    p.flush()
    return p
  }

  @Test
  fun surroundSourceIsAcceptedAndComesOutAsStereo() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    val p = ready51()
    val out = run(p, signal51(4096))
    assertEquals(4096 * 2, out.size)
    assertTrue(SpatialControl.status.value != com.example.data.audio.spatial.SpatialStatus.NOT_STEREO)
  }

  @Test
  fun surroundOffIsTheDownmixDelayedExactly() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    val p = ready51()
    val input = signal51(8192)
    val dm = FloatArray(8192 * 2)
    com.example.data.audio.spatial.SurroundVirtualizer.downmix(input, 0, 8192, dm)
    val out = run(p, input)
    for (i in 0 until DELAY * 2) assertEquals(0f, out[i], 0f)
    for (i in DELAY * 2 until out.size) assertEquals(dm[i - DELAY * 2], out[i], 0f)
  }

  @Test
  fun surroundOnRendersBinauralAndOffReturnsToTheDownmix() {
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    val p = ready51()
    val deadline = System.currentTimeMillis() + 20_000
    var pos = 0
    val block = 2048
    val outs = ArrayList<Float>()
    val dms = ArrayList<Float>()
    fun chunk() {
      val input = signal51(block, pos)
      val dm = FloatArray(block * 2)
      com.example.data.audio.spatial.SurroundVirtualizer.downmix(input, 0, block, dm)
      dm.forEach { dms += it }
      run(p, input).forEach { outs += it }
      pos += block
    }
    // Until the filters are built the processor plays the downmix; then it must leave it.
    while (System.currentTimeMillis() < deadline && SpatialControl.surroundFor(fs) == null) { chunk(); Thread.sleep(5) }
    assertTrue("virtualizer not ready", SpatialControl.surroundFor(fs) != null)
    repeat(40) { chunk() }
    var diff = 0.0
    val from = (outs.size / 2) - 10 * block
    for (i in from until outs.size / 2) diff += abs(outs[2 * i] - dms[2 * (i - DELAY)])
    assertTrue("never rendered (diff $diff)", diff / (10 * block) > 0.005)
    assertTrue(outs.all { it.isFinite() })
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    repeat(40) { chunk() }
    for (i in outs.size / 2 - 4 * block until outs.size / 2) assertEquals(dms[2 * (i - DELAY)], outs[2 * i], 1e-6f)
  }

  // ---- Cinema preset: stereo spread over five virtual speakers ----

  @Test
  fun cinemaRendersAndSwitchingPathsNeverClicks() {
    assertTrue(SpatialPreset.CINEMA.virtual51)
    assertTrue(SpatialPreset.entries.count { it.virtual51 } == 1)
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    waitForEngine()
    val deadline = System.currentTimeMillis() + 20_000
    while (SpatialControl.cinemaFor(fs) == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
    assertTrue("cinema stage not ready", SpatialControl.cinemaFor(fs) != null)
    val p = processorReady()
    val block = 1000
    val outputs = ArrayList<Float>()
    var pos = 0
    // Standard, then Cinema, then Standard again, then off: every change glides through the dry sound.
    repeat(160) { i ->
      when (i) {
        30 -> SpatialControl.configure(true, SpatialPreset.CINEMA, 1.0)
        90 -> SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
        130 -> SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
      }
      run(p, signal(block, pos)).forEach { outputs += it }
      pos += block
    }
    val out = outputs.toFloatArray()
    assertTrue(out.all { it.isFinite() })
    var maxStep = 0f
    for (ch in 0..1) for (i in 1 until out.size / 2) maxStep = max(maxStep, abs(out[2 * i + ch] - out[2 * (i - 1) + ch]))
    assertTrue("step $maxStep", maxStep < 0.15f)
    val ref = signal(160 * block)
    // Well inside the Cinema stretch (blocks 30..90) the output is no longer the delayed input.
    var diff = 0.0
    for (i in 60 * block until 85 * block) diff += abs(out[2 * i] - ref[2 * (i - DELAY)]) + abs(out[2 * i + 1] - ref[2 * (i - DELAY) + 1])
    assertTrue("cinema never rendered (diff $diff)", diff / (25 * block) > 0.01)
    // After the switch-off it is the delayed input again.
    for (i in 150 * block until 158 * block) assertEquals(ref[2 * (i - DELAY)], out[2 * i], 1e-6f)
  }

  @Test
  fun aFailureKeepsThePlayingPublishesFailedAndRecordsOnce() {
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    val records = mutableListOf<String>()
    val boom = object : com.example.data.audio.spatial.SpatialStage {
      override val latency = 0
      override var targetIntensity = 1.0
      override val currentIntensity = 0.0
      override fun reset(startIntensity: Double) {}
      override fun process(input: FloatArray, offset: Int, frames: Int, output: FloatArray): Int =
        throw IllegalStateException("forced")
    }
    val p = SpatialAudioProcessor(stageFor = { _, _ -> boom }, recordFailure = { tag, _, _ -> records += tag })
    p.configure(format)
    p.flush()
    val block = 1024
    var heard = 0.0
    for (b in 0 until 6) {
      val input = signal(block, b * block)
      val out = run(p, input)
      assertEquals("every block still comes out", input.size, out.size)
      assertTrue("no NaN", out.none { it.isNaN() })
      if (b == 0) for (i in input.indices) assertEquals("the failing block plays the input", input[i], out[i], 1e-6f)
      if (b >= 3) heard += out.sumOf { abs(it.toDouble()) }
    }
    assertTrue("the music is still there after the failure", heard > 1.0)
    assertEquals(listOf("spatial.bypass"), records)
    assertEquals(com.example.data.audio.spatial.SpatialStatus.FAILED, SpatialControl.status.value)

    // A new stream (next track, seek) gives the effect another try: the state is back to working.
    p.flush()
    assertTrue(SpatialControl.status.value != com.example.data.audio.spatial.SpatialStatus.FAILED)
    run(p, signal(block))
    assertEquals("the same fault again is a new occurrence", 2, records.size)

    // Switching it off and on again also clears it.
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    SpatialControl.configure(true, SpatialPreset.STANDARD, 1.0)
    assertTrue(SpatialControl.status.value != com.example.data.audio.spatial.SpatialStatus.FAILED)
  }
}
