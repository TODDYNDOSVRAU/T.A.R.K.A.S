package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.audio.loudness.LoudnessMeter
import com.example.data.audio.loudness.LoudnessMeterProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The meter against the EBU Tech 3341 test signals (stereo 1 kHz sines), whose expected integrated
 * loudness is given by the standard with a tolerance of ±0.1 LU.
 */
class LoudnessMeterTest {

  /** Feeds [seconds] of a stereo 1 kHz sine at [dbfs] peak, in chunks like the player's buffers. */
  private fun LoudnessMeter.sine(dbfs: Double, seconds: Double, rate: Int, phaseStart: Long = 0L): Long {
    val amp = 10.0.pow(dbfs / 20.0)
    val frames = (seconds * rate).toLong()
    val chunk = 1024
    val buf = FloatArray(chunk * 2)
    var done = 0L
    while (done < frames) {
      val n = minOf(chunk.toLong(), frames - done).toInt()
      for (i in 0 until n) {
        val v = (amp * sin(2 * PI * 1000.0 * (phaseStart + done + i) / rate)).toFloat()
        buf[2 * i] = v
        buf[2 * i + 1] = v
      }
      process(buf, n)
      done += n
    }
    return phaseStart + frames
  }

  @Test
  fun sineAtMinus23IsMinus23Lufs() {
    for (rate in listOf(44100, 48000, 96000)) {
      val m = LoudnessMeter().apply { configure(rate, 2) }
      m.sine(-23.0, 20.0, rate)
      assertEquals("at $rate Hz", -23.0, m.result()!!, 0.1)
    }
  }

  @Test
  fun sineAtMinus33IsMinus33Lufs() {
    val m = LoudnessMeter().apply { configure(48000, 2) }
    m.sine(-33.0, 20.0, 48000)
    assertEquals(-33.0, m.result()!!, 0.1)
  }

  /** Tech 3341 case 3: the quiet parts fall under the relative gate and do not pull the value down. */
  @Test
  fun quietPartsAreGatedOut() {
    val rate = 48000
    val m = LoudnessMeter().apply { configure(rate, 2) }
    var t = m.sine(-36.0, 10.0, rate)
    t = m.sine(-23.0, 60.0, rate, t)
    m.sine(-36.0, 10.0, rate, t)
    assertEquals(-23.0, m.result()!!, 0.1)
  }

  @Test
  fun silenceHasNoLoudness() {
    val m = LoudnessMeter().apply { configure(48000, 2) }
    m.process(FloatArray(48000 * 2 * 5), 48000 * 5)
    assertNull(m.result())
    assertEquals(5.0, m.measuredSeconds, 1e-9)
  }

  @Test
  fun clearStartsANewTrack() {
    val m = LoudnessMeter().apply { configure(48000, 2) }
    m.sine(-10.0, 10.0, 48000)
    m.clear()
    m.sine(-23.0, 10.0, 48000)
    assertEquals(-23.0, m.result()!!, 0.1)
    assertEquals(10.0, m.measuredSeconds, 1e-6)
  }

  @Test
  fun surroundIsNotMeasured() {
    val m = LoudnessMeter().apply { configure(48000, 6) }
    m.process(FloatArray(6 * 48000) { 0.1f }, 48000)
    assertNull(m.result())
  }

  /** Interleaved 16-bit stereo 1 kHz sine at [dbfs], as the sink delivers it. */
  private fun pcm16(dbfs: Double, seconds: Int): ByteBuffer {
    val frames = 48000 * seconds
    val amp = 32767 * 10.0.pow(dbfs / 20.0)
    val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
    for (i in 0 until frames) {
      val v = (amp * sin(2 * PI * 1000.0 * i / 48000)).toInt().toShort()
      b.putShort(v).putShort(v)
    }
    b.flip()
    return b
  }

  /**
   * A quiet track then, gapless, a loud one. The sink ends the quiet track's stream before the loud one
   * comes in, and the player reports the change later: the quiet track must keep its own value.
   */
  @Test
  fun endOfStreamClosesEachTrackAtItsLastSample() {
    val p = LoudnessMeterProcessor()
    val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
    p.configure(format)
    p.flush()
    p.queueInput(pcm16(-33.0, 10)); p.output
    p.queueEndOfStream()
    p.queueEndOfStream() // the chain repeats it while it drains
    p.flush()
    p.queueEndOfStream() // and the sink drains a second time, with no audio in between
    p.flush()
    // The next track's head is already through when the player reports the change.
    p.queueInput(pcm16(-8.0, 1)); p.output

    val quiet = p.take()
    assertEquals(-33.0, quiet.lufs!!, 0.15)
    assertEquals(10.0, quiet.seconds, 1e-6)

    p.queueInput(pcm16(-8.0, 9)); p.output
    p.queueEndOfStream()
    val loud = p.take()
    assertEquals(-8.0, loud.lufs!!, 0.15)
    assertEquals("its head was kept", 10.0, loud.seconds, 1e-6)
    assertNull("nothing left", p.take().lufs)
  }

  @Test
  fun clearDropsWhatWasNotTaken() {
    val p = LoudnessMeterProcessor()
    p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
    p.flush()
    p.queueInput(pcm16(-20.0, 2)); p.output
    p.queueEndOfStream()
    p.clear()
    assertNull(p.take().lufs)
  }

  @Test
  fun processorPassesAudioUnchangedAndMeasuresIt() {
    val p = LoudnessMeterProcessor()
    p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
    p.flush()
    val frames = 48000 * 5
    val amp = 32767 * 10.0.pow(-23.0 / 20.0)
    val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
    for (i in 0 until frames) {
      val v = (amp * sin(2 * PI * 1000.0 * i / 48000)).toInt().toShort()
      input.putShort(v).putShort(v)
    }
    input.flip()
    val expected = ByteArray(input.remaining()).also { input.duplicate().order(ByteOrder.nativeOrder()).get(it) }

    p.queueInput(input)
    val out = p.output
    val actual = ByteArray(out.remaining()).also { out.get(it) }
    assertTrue("audio is not changed", expected.contentEquals(actual))

    val m = p.take()
    assertEquals(5.0, m.seconds, 1e-6)
    assertEquals(-23.0, m.lufs!!, 0.15)
    assertNull("take() starts over", p.take().lufs)
  }
}
