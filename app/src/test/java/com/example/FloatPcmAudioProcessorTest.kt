package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.data.audio.dsp.FloatPcmAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FloatPcmAudioProcessorTest {

  private fun run(encoding: Int, bytes: ByteBuffer): FloatArray {
    val p = FloatPcmAudioProcessor()
    p.configure(AudioProcessor.AudioFormat(48000, 2, encoding))
    p.flush()
    bytes.flip()
    p.queueInput(bytes)
    val out = p.output.order(ByteOrder.nativeOrder())
    return FloatArray(out.remaining() / 4) { out.getFloat() }
  }

  @Test fun sixteenBitIsExact() {
    val b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
    b.putShort(-32768).putShort(0).putShort(16384).putShort(32767)
    val f = run(C.ENCODING_PCM_16BIT, b)
    assertEquals(-1f, f[0], 0f)
    assertEquals(0f, f[1], 0f)
    assertEquals(0.5f, f[2], 0f)
    assertEquals(32767f / 32768f, f[3], 0f)
  }

  @Test fun twentyFourBitKeepsTheLowByte() {
    // 0x000001 is one step of a 24-bit file: it would be 0 after a cut to 16 bits.
    val b = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    b.put(1).put(0).put(0)
    b.put(0xFF.toByte()).put(0xFF.toByte()).put(0xFF.toByte())
    val f = run(C.ENCODING_PCM_24BIT, b)
    assertEquals(1f / 8388608f, f[0], 0f)
    assertEquals(-1f / 8388608f, f[1], 0f)
  }

  @Test fun thirtyTwoBitFullScale() {
    val b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
    b.putInt(Int.MIN_VALUE).putInt(1 shl 30)
    val f = run(C.ENCODING_PCM_32BIT, b)
    assertEquals(-1f, f[0], 0f)
    assertEquals(0.5f, f[1], 0f)
  }

  @Test fun floatInputIsLeftAlone() {
    val p = FloatPcmAudioProcessor()
    val out = p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
    assertSame(AudioProcessor.AudioFormat.NOT_SET, out)
  }
}
