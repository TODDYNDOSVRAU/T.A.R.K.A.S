package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.ParametricEqAudioProcessor
import com.example.data.audio.loudness.LoudnessMeterProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor
import com.example.data.audio.spatial.SpatialAudioProcessor.Companion.DELAY
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The player's chain through several tracks in a row, the way the audio sink drives it at each gapless
 * change: the next track's format is configured while the current one still plays, then the chain is
 * drained to its end of stream and flushed. Each drain must end, or the player never moves on.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GaplessChainTest {

  private val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

  private fun chunk(frames: Int, start: Int): ByteBuffer {
    val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
    for (i in 0 until frames) {
      val v = (6000 * sin(2 * PI * 440 * (start + i) / 44100)).toInt().toShort()
      b.putShort(v).putShort(v)
    }
    b.flip()
    return b
  }

  /** Plays [tracks] tracks through [processors]; returns the frames that came out of each. */
  private fun playTracks(processors: List<AudioProcessor>, tracks: Int): List<Long> {
    val outFrames = mutableListOf<Long>()
    val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(processors))
    pipeline.configure(format)
    pipeline.flush()
    repeat(tracks) { t ->
      var out = 0L
      fun drainOutput() {
        repeat(1000) {
          val o = pipeline.output
          if (!o.hasRemaining()) return
          out += o.remaining() / 4
          o.position(o.limit())
        }
        throw AssertionError("track ${t + 1}: output never runs dry")
      }
      var start = 0
      repeat(40) { i ->
        // The sink configures the next track's pipeline while this one still plays.
        if (i == 30 && t < tracks - 1) AudioProcessingPipeline(ImmutableList.copyOf(processors)).configure(format)
        val input = chunk(1024, start)
        start += 1024
        var spins = 0
        while (input.hasRemaining()) {
          pipeline.queueInput(input)
          drainOutput()
          if (++spins > 1000) throw AssertionError("track ${t + 1}, chunk $i: the chain stopped taking input")
        }
      }
      pipeline.queueEndOfStream()
      var spins = 0
      while (!pipeline.isEnded && spins < 1000) {
        drainOutput()
        spins++
      }
      assertTrue("track ${t + 1}: the chain never reached its end of stream", pipeline.isEnded)
      outFrames += out
      pipeline.flush()
    }
    return outFrames
  }

  /** The app's chain: every track ends, with exactly its own frames plus the spatial delay's tail, once. */
  @Test
  fun fullChainEndsEveryTrack() {
    val out = playTracks(listOf(LoudnessMeterProcessor(), SpatialAudioProcessor(), ParametricEqAudioProcessor(EqDsp())), 4)
    assertEquals(List(4) { 40L * 1024 + DELAY }, out)
  }

  /** The sink always puts its own processors first, so the spatial is never the first of the chain. */
  @Test
  fun spatialBehindAnotherProcessorEndsEveryTrack() {
    val out = playTracks(listOf(ParametricEqAudioProcessor(EqDsp()), SpatialAudioProcessor()), 3)
    assertEquals(List(3) { 40L * 1024 + DELAY }, out)
  }

  @Test
  fun repeatedEndOfStreamPushesTheTailOnce() {
    val p = SpatialAudioProcessor()
    p.configure(format)
    p.flush()
    p.queueInput(chunk(1024, 0))
    p.output
    p.queueEndOfStream()
    assertEquals(DELAY * 4, p.output.remaining())
    p.queueEndOfStream()
    assertEquals("a second call adds nothing", 0, p.output.remaining())
    assertTrue(p.isEnded)
  }
}
