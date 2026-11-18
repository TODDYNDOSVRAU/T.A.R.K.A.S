package com.example.windows.audio

import com.example.data.audio.dsp.EqDsp
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Decode → [EqDsp] → 16-bit PCM, with no audio device, so it can be tested.
 * The same [EqDsp] the Android app runs inside ExoPlayer's sink runs here on the same float frames.
 */
class PcmPipeline(private val dsp: EqDsp, private val chain: com.example.windows.equalizador.EffectChain? = null) {

  /**
   * An open file decoded to signed little-endian PCM at its own depth ([decoded]: 16, 24 or 32 bit),
   * and the 16-bit format [render] writes ([format]), both at the file's rate and channel count.
   */
  class Source(val stream: AudioInputStream, val decoded: AudioFormat, val format: AudioFormat, val totalFrames: Long) : AutoCloseable {
    override fun close() = stream.close()
  }

  /**
   * Opens [file] through the Java Sound providers on the classpath (WAV/AIFF built in, MP3 and FLAC
   * from the bundled decoders). Throws [UnsupportedFormatException] with the extension when none can.
   */
  fun open(file: File): Source {
    val raw = try {
      AudioSystem.getAudioInputStream(file)
    } catch (e: Exception) {
      throw UnsupportedFormatException(file.extension.lowercase(), e)
    }
    val base = raw.format
    val bits = base.sampleSizeInBits.takeIf { it == 16 || it == 24 || it == 32 } ?: 16
    val decoded = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, bits, base.channels, base.channels * bits / 8, base.sampleRate, false)
    val pcm = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
    val stream = try {
      if (base.matches(decoded)) raw else AudioSystem.getAudioInputStream(decoded, raw)
    } catch (e: IllegalArgumentException) {
      raw.close()
      throw UnsupportedFormatException(file.extension.lowercase(), e)
    }
    val frames = framesOf(raw, file, base)
    if (chain != null) chain.configure(pcm.sampleRate.toInt(), pcm.channels) else dsp.configure(pcm.sampleRate.toInt(), pcm.channels)
    if (chain != null) chain.reset() else dsp.reset()
    return Source(stream, decoded, pcm, frames)
  }

  private fun framesOf(raw: AudioInputStream, file: File, base: AudioFormat): Long {
    if (raw.frameLength > 0 && base.encoding.let { it == AudioFormat.Encoding.PCM_SIGNED || it == AudioFormat.Encoding.PCM_UNSIGNED || it == AudioFormat.Encoding.PCM_FLOAT }) return raw.frameLength
    // Compressed providers report length in their own frames; the file properties carry the duration.
    val us = (AudioSystem.getAudioFileFormat(file).properties()["duration"] as? Long) ?: return -1
    return us * base.sampleRate.toLong() / 1_000_000
  }

  private var bytes = ByteArray(0)
  private var floats = FloatArray(0)

  /**
   * Reads up to [frames] frames from [src], runs the EQ and writes 16-bit PCM into [out].
   * Returns the bytes written to [out], or -1 at the end of the stream.
   */
  fun render(src: Source, frames: Int, out: ByteArray): Int {
    val ch = src.format.channels
    val bps = src.decoded.sampleSizeInBits / 8
    val need = frames * ch * bps
    if (bytes.size < need) bytes = ByteArray(need)
    if (floats.size < frames * ch) floats = FloatArray(frames * ch)
    var got = 0
    while (got < need) {
      val n = src.stream.read(bytes, got, need - got)
      if (n < 0) break
      got += n
    }
    val whole = got - got % (ch * bps)
    if (whole <= 0) return -1
    val samples = whole / bps
    when (bps) {
      2 -> for (i in 0 until samples) floats[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)) / 32768f
      3 -> for (i in 0 until samples) {
        val o = 3 * i
        floats[i] = ((bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o + 2].toInt() shl 16)) / 8388608f
      }
      else -> for (i in 0 until samples) {
        val o = 4 * i
        val v = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or ((bytes[o + 2].toInt() and 0xFF) shl 16) or (bytes[o + 3].toInt() shl 24)
        floats[i] = (v / 2147483648.0).toFloat()
      }
    }
    if (chain != null) chain.process(floats, samples / ch) else dsp.process(floats, samples / ch)
    for (i in 0 until samples) {
      val v = Math.round(floats[i].coerceIn(-1f, 32767f / 32768f) * 32768f)
      out[2 * i] = v.toByte()
      out[2 * i + 1] = (v shr 8).toByte()
    }
    return samples * 2
  }
}

class UnsupportedFormatException(val extension: String, cause: Throwable) :
  Exception("O formato .$extension não pôde ser aberto: nenhum decodificador o reconhece. Formatos aceitos agora: WAV, AIFF, MP3, FLAC.", cause)
