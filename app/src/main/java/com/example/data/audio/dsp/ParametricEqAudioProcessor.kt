package com.example.data.audio.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Plugs [EqDsp] into ExoPlayer's audio sink. Handles 16-bit and float PCM (what decoders produce);
 * any other encoding is passed through untouched by declining it.
 */
@OptIn(UnstableApi::class)
class ParametricEqAudioProcessor(private val dsp: EqDsp) : BaseAudioProcessor() {

  private var scratch = FloatArray(0)
  private var encoding = C.ENCODING_INVALID
  private var channels = 2

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val enc = inputAudioFormat.encoding
    // Any other encoding passes through untouched (NOT_SET makes this processor inactive) instead of
    // failing the whole audio sink.
    if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
    encoding = enc
    channels = inputAudioFormat.channelCount
    dsp.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    // Same format out as in: the chain only changes sample values.
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val bytes = inputBuffer.remaining()
    if (bytes == 0) return
    val out = replaceOutputBuffer(bytes)

    if (encoding == C.ENCODING_PCM_16BIT) {
      val samples = bytes / 2
      ensureScratch(samples)
      for (i in 0 until samples) scratch[i] = inputBuffer.getShort() / 32768f
      dsp.process(scratch, samples / channels)
      for (i in 0 until samples) {
        out.putShort((scratch[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
      }
    } else {
      val samples = bytes / 4
      ensureScratch(samples)
      for (i in 0 until samples) scratch[i] = inputBuffer.getFloat()
      dsp.process(scratch, samples / channels)
      for (i in 0 until samples) out.putFloat(scratch[i])
    }
    out.flip()
  }

  override fun onFlush() {
    dsp.reset()
  }

  override fun onReset() {
    scratch = FloatArray(0)
    encoding = C.ENCODING_INVALID
  }

  private fun ensureScratch(samples: Int) {
    if (scratch.size < samples) scratch = FloatArray(samples)
  }
}
