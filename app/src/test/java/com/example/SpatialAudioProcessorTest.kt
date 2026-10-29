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
    SpatialControl.setRoute(headphones = false, systemSpatial = false)
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
    SpatialControl.setRoute(headphones = true, systemSpatial = false)
    waitForEngine()
    val p = processorReady()
    val block = 1000
    val outputs = ArrayList<Float>()
    var pos = 0
    // On for a while, off, then on again, in blocks like the player's.
    repeat(60) { i ->
      if (i == 25) SpatialControl.setRoute(headphones = false, systemSpatial = false)
      if (i == 45) SpatialControl.setRoute(headphones = true, systemSpatial = false)
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
}
