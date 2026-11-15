package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.audio.TrackFormat
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.ParametricEqAudioProcessor
import com.example.data.audio.loudness.LoudnessMeterProcessor
import com.example.data.model.EqSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Do the processors of the chain take more than 2 channels (a 5.1 track, 6 channels)? Step 0 of
 * docs/research/prologic-ii-integracao.md. What each one does with 6 channels is stated here; what the
 * stereo-only parts do (width, balance, loudness measurement) is the documented behaviour, not a bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultichannelProcessorsTest {

  private val rate = 48000
  private val six = AudioProcessor.AudioFormat(rate, 6, C.ENCODING_PCM_FLOAT)
  private val six16 = AudioProcessor.AudioFormat(rate, 6, C.ENCODING_PCM_16BIT)

  /** [frames] frames of 6 channels; only channel [only] carries a 440 Hz tone, the others are silent. */
  private fun tone(frames: Int, only: Int, amp: Float = 0.3f) = FloatArray(frames * 6).also {
    for (f in 0 until frames) it[f * 6 + only] = (amp * sin(2 * PI * 440 * f / rate)).toFloat()
  }

  private fun floatBuffer(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
    .apply { a.forEach { putFloat(it) }; flip() }

  private fun shortBuffer(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder())
    .apply { a.forEach { putShort((it * 32767f).toInt().toShort()) }; flip() }

  private fun readFloats(b: ByteBuffer) = FloatArray(b.remaining() / 4) { b.getFloat() }

  private fun channelPeak(a: FloatArray, ch: Int): Float {
    var p = 0f
    for (i in ch until a.size step 6) p = maxOf(p, abs(a[i]))
    return p
  }

  @Test
  fun eqDspKeepsEachOfSixChannelsApart() {
    val dsp = EqDsp().apply { configure(rate, 6) }
    for (ch in 0 until 6) {
      val buf = tone(4800, ch)
      dsp.reset()
      dsp.process(buf, 4800)
      assertTrue("channel $ch keeps its tone", channelPeak(buf, ch) > 0.25f)
      for (other in 0 until 6) if (other != ch) {
        assertEquals("channel $other stays silent when only $ch plays", 0f, channelPeak(buf, other), 0f)
      }
    }
  }

  @Test
  fun eqDspFiltersEveryChannelLikeStereoDoes() {
    val boosted = EqSettings().let { s ->
      s.copy(bands = s.bands.map { if (it.freqHz == 1000f) it.copy(gainDb = 6f) else it }, autoPreamp = false)
    }
    val frames = 4800
    val stereo = FloatArray(frames * 2) { i -> (0.2 * sin(2 * PI * 1000 * (i / 2) / rate)).toFloat() }
    val surround = FloatArray(frames * 6) { i -> (0.2 * sin(2 * PI * 1000 * (i / 6) / rate)).toFloat() }
    val a = EqDsp().apply { update(boosted); configure(rate, 2) }
    val b = EqDsp().apply { update(boosted); configure(rate, 6) }
    a.process(stereo, frames)
    b.process(surround, frames)
    // A 1 kHz tone with +6 dB at 1 kHz: every one of the 6 channels comes out like the stereo left channel.
    val last = frames - 480
    for (ch in 0 until 6) {
      assertEquals("channel $ch", stereo[last * 2], surround[last * 6 + ch], 1e-5f)
    }
    assertTrue("the boost acted", channelPeak(surround, 3) > 0.2f * 1.5f)
  }

  @Test
  fun eqProcessorPassesSixChannelsOfFloatAndOf16BitWithTheSameLength() {
    val p = ParametricEqAudioProcessor(EqDsp())
    p.configure(six)
    p.flush()
    val input = tone(2400, 4)
    p.queueInput(floatBuffer(input))
    val out = readFloats(p.output)
    assertEquals(input.size, out.size)
    assertTrue(channelPeak(out, 4) > 0.25f)
    assertEquals(0f, channelPeak(out, 0), 0f)

    val q = ParametricEqAudioProcessor(EqDsp())
    q.configure(six16)
    q.flush()
    val inBuf = shortBuffer(tone(2400, 2))
    q.queueInput(inBuf)
    assertEquals(2400 * 6 * 2, q.output.remaining())
  }

  @Test
  fun balanceAndWidthAreStereoOnlyAndSixChannelsStayUntouchedByThem() {
    val s = EqSettings(balance = 0.8f, stereoWidth = 0.2f, autoPreamp = false)
    val frames = 2400
    val buf = FloatArray(frames * 6) { i -> (0.2 * sin(2 * PI * 300 * (i / 6) / rate)).toFloat() }
    val ref = buf.copyOf()
    val dsp = EqDsp().apply { update(s); configure(rate, 6) }
    dsp.process(buf, frames)
    // Left and right of a 6-channel track are not "the stereo pair": balance and width do not touch them.
    // Compared after the limiter's 2 ms delay, with the flat bands the samples are the input delayed.
    val delay = (0.002 * rate).toInt()
    for (f in delay + 10 until frames) {
      assertEquals(ref[(f - delay) * 6 + 0], buf[f * 6 + 0], 1e-4f)
      assertEquals(ref[(f - delay) * 6 + 1], buf[f * 6 + 1], 1e-4f)
    }
  }

  @Test
  fun loudnessMeterPassesSixChannelsUntouchedButDoesNotMeasureThem() {
    val p = LoudnessMeterProcessor()
    assertEquals(six, p.configure(six))
    p.flush()
    val input = tone(48000, 2)
    p.queueInput(floatBuffer(input))
    assertArrayEquals(input, readFloats(p.output), 0f)
    val m = p.take()
    // Mono and stereo only (LoudnessMeter): with 6 channels there is no value, so no normalization.
    assertNull(m.lufs)
    assertEquals(0.0, m.seconds, 0.0)
  }

  @Test
  fun outputChannelCountIsTheLargestReportedOrUnknown() {
    assertNull("an output that reports none takes any layout", TrackFormat.maxOutputChannels(IntArray(0)))
    assertEquals(2, TrackFormat.maxOutputChannels(intArrayOf(2)))
    assertEquals(8, TrackFormat.maxOutputChannels(intArrayOf(2, 8, 6)))
    assertEquals(2, TrackFormat.maxOutputChannels(intArrayOf(0, 2)))
    assertNull(TrackFormat.maxOutputChannels(intArrayOf(0)))
  }
}
