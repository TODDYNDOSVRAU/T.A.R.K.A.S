package com.example.data.audio.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * First stage of the chain: turns 16, 24 and 32-bit integer PCM into 32-bit float, so the meter, the
 * spatial processor and the equalizer after it work on float samples from end to end. Without it each of
 * them converted to float and back to 16 bits (and 24-bit files lost their low byte before the first one):
 * every stage rounded the signal again and a boosted curve was cut at the 16-bit limit. Float goes out to
 * the sink, and Android's mixer takes it as it is.
 *
 * Scaling is by 2^(bits-1), the exact inverse of what a float-to-integer conversion does, so the
 * conversion is lossless for every input.
 */
@OptIn(UnstableApi::class)
class FloatPcmAudioProcessor : BaseAudioProcessor() {

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
    when (inputAudioFormat.encoding) {
      C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT ->
        AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
      // Already float, or not PCM at all: left alone.
      else -> AudioProcessor.AudioFormat.NOT_SET
    }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return
    val bytesPerSample = when (inputAudioFormat.encoding) {
      C.ENCODING_PCM_16BIT -> 2
      C.ENCODING_PCM_24BIT -> 3
      else -> 4
    }
    val samples = remaining / bytesPerSample
    val out = replaceOutputBuffer(samples * 4)
    when (inputAudioFormat.encoding) {
      C.ENCODING_PCM_16BIT -> repeat(samples) { out.putFloat(inputBuffer.getShort() * SCALE_16) }
      C.ENCODING_PCM_24BIT -> repeat(samples) {
        // Little-endian, sign-extended from the top byte.
        val b0 = inputBuffer.get().toInt() and 0xFF
        val b1 = inputBuffer.get().toInt() and 0xFF
        val b2 = inputBuffer.get().toInt()
        out.putFloat(((b2 shl 16) or (b1 shl 8) or b0) * SCALE_24)
      }
      else -> repeat(samples) { out.putFloat((inputBuffer.getInt() * SCALE_32).toFloat()) }
    }
    out.flip()
  }

  private companion object {
    const val SCALE_16 = 1f / 32768f
    const val SCALE_24 = 1f / 8388608f
    const val SCALE_32 = 1.0 / 2147483648.0
  }
}
