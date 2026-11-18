package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.ParametricEqAudioProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor.Companion.DELAY
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialPreset
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
import kotlin.math.sin

/**
 * What happens to the processors when one track gives way to the next: the audio of the old track must
 * keep its own format until the flush, and the new track must come out with sound, not silence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProcessorTrackChangeTest {

  private val pcm16 = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
  private val float = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)

  private fun shorts(frames: Int) = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
    for (i in 0 until frames * 2) putShort((8000 * sin(2 * PI * 440 * (i / 2) / 48000)).toInt().toShort())
    flip()
  }

  private fun floats(frames: Int) = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder()).apply {
    for (i in 0 until frames * 2) putFloat((0.3 * sin(2 * PI * 440 * (i / 2) / 44100)).toFloat())
    flip()
  }

  private fun drain(p: AudioProcessor): ByteBuffer = p.output

  @Test
  fun eqKeepsTheOldFormatUntilTheFlush() {
    val p = ParametricEqAudioProcessor(EqDsp())
    p.configure(pcm16)
    p.flush()
    assertEquals(4000, drain(p.also { it.queueInput(shorts(1000)) }).remaining())

    // The next track is float, configured while 16-bit audio is still coming in.
    p.configure(float)
    val old = shorts(1000)
    p.queueInput(old)
    assertEquals("the old buffer is consumed in its own format", 0, old.remaining())
    val out = drain(p)
    assertEquals(4000, out.remaining())
    // Still valid 16-bit sound (a flat EQ leaves it about the same), not floats read as shorts.
    var peak = 0
    while (out.hasRemaining()) peak = maxOf(peak, abs(out.getShort().toInt()))
    assertTrue("peak $peak", peak in 6000..10000)

    p.flush()
    assertEquals(8000, drain(p.also { it.queueInput(floats(1000)) }).remaining())
  }

  @Test
  fun spatialPlaysTheNextTrackAfterAReset() {
    SpatialControl.configure(false, SpatialPreset.STANDARD, 1.0)
    val p = SpatialAudioProcessor()
    p.configure(pcm16)
    p.flush()
    p.queueInput(shorts(4000))
    drain(p)

    // Stop, then the next track (ExoPlayer resets the sink between them).
    p.reset()
    p.configure(float)
    p.flush()
    p.queueInput(floats(4000))
    val out = drain(p)
    assertEquals(4000 * 8, out.remaining())
    var energy = 0.0
    var i = 0
    while (out.hasRemaining()) {
      val v = out.getFloat()
      if (i++ >= DELAY * 2) energy += abs(v)
    }
    assertTrue("the next track came out silent", energy > 100.0)
  }
}
