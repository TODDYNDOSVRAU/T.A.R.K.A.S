package com.example.windows.audio

import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class PcmPipelineTest {
  private val rate = 48000

  private fun sineWav(freq: Double, seconds: Double, amp: Double = 0.25): File {
    val frames = (rate * seconds).toInt()
    val bytes = ByteArray(frames * 4)
    for (i in 0 until frames) {
      val v = Math.round(amp * 32767 * sin(2 * PI * freq * i / rate)).toInt()
      for (c in 0 until 2) {
        bytes[i * 4 + c * 2] = v.toByte(); bytes[i * 4 + c * 2 + 1] = (v shr 8).toByte()
      }
    }
    val fmt = AudioFormat(rate.toFloat(), 16, 2, true, false)
    val f = File.createTempFile("tarkas", ".wav").apply { deleteOnExit() }
    AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), fmt, frames.toLong()), AudioFileFormat.Type.WAVE, f)
    return f
  }

  private fun renderAll(file: File, settings: EqSettings): Pair<ShortArray, ShortArray> {
    val dsp = EqDsp()
    val p = PcmPipeline(dsp)
    val src = p.open(file)
    dsp.update(settings)
    val out = ByteArray(512 * 4)
    val all = java.io.ByteArrayOutputStream()
    while (true) { val n = p.render(src, 512, out); if (n < 0) break; all.write(out, 0, n) }
    src.close()
    val b = all.toByteArray()
    val rendered = ShortArray(b.size / 2) { ((b[2 * it].toInt() and 0xFF) or (b[2 * it + 1].toInt() shl 8)).toShort() }
    val inStream = AudioSystem.getAudioInputStream(file)
    val ib = inStream.readAllBytes()
    val input = ShortArray(ib.size / 2) { ((ib[2 * it].toInt() and 0xFF) or (ib[2 * it + 1].toInt() shl 8)).toShort() }
    return input to rendered
  }

  private fun rmsDb(x: ShortArray, from: Int): Double {
    var s = 0.0
    for (i in from until x.size) s += x[i].toDouble() * x[i]
    return 20 * log10(sqrt(s / (x.size - from)) / 32768)
  }

  /** The limiter's lookahead delays the output; find it as the shift where input and output match best. */
  private fun alignedMaxDiff(input: ShortArray, out: ShortArray): Pair<Int, Int> {
    var best = Int.MAX_VALUE to -1
    for (lag in 0..960) {
      var m = 0
      for (i in 0 until input.size - 2 * lag) m = maxOf(m, kotlin.math.abs(input[i] - out[i + 2 * lag]))
      if (m < best.first) best = m to lag
    }
    return best
  }

  @Test fun flatEqIsBitExactThroughThePipeline() {
    val (input, out) = renderAll(sineWav(1000.0, 0.5), EqSettings(bands = EqSettings().bands.map { it.copy(gainDb = 0f) }))
    assertEquals(input.size, out.size)
    val (diff, lag) = alignedMaxDiff(input, out)
    assertEquals("flat EQ must not change a single sample (after the lookahead)", 0, diff)
    assertTrue("lookahead $lag frames must stay within 5 ms", lag * 1000.0 / rate <= 5.0)
  }

  @Test fun peakBandRaisesItsFrequencyBySetGainRelativeToTheRest() {
    val settings = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 6f)))
    val flat = EqSettings(bands = listOf(EqBand(freqHz = 1000f, gainDb = 0f)))
    val skip = rate // second half: past the 8 ms smoothing
    fun gainAt(freq: Double, s: EqSettings): Double { val (i, o) = renderAll(sineWav(freq, 1.0), s); return rmsDb(o, skip) - rmsDb(i, skip) }
    // The automatic preamp lowers everything by the boost; what the band adds is the 1 kHz gain over a far frequency.
    val boost = (gainAt(1000.0, settings) - gainAt(100.0, settings)) - (gainAt(1000.0, flat) - gainAt(100.0, flat))
    assertEquals("+6 dB peak at its centre, ±0.05 dB", 6.0, boost, 0.05)
  }

  @Test fun unknownFormatSaysWhatHappened() {
    val f = File.createTempFile("tarkas", ".xyz").apply { writeText("not audio"); deleteOnExit() }
    val e = runCatching { PcmPipeline(EqDsp()).open(f) }.exceptionOrNull()
    assertTrue(e is UnsupportedFormatException && e.message!!.contains(".xyz"))
  }
}
