package com.example.data.audio.loudness

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * First stage of the audio chain: lets the audio through unchanged and measures its loudness on the
 * way, before the spatial and the equalizer touch it, so the value describes the track itself.
 * 16-bit and float PCM are measured; any other encoding passes untouched and is not measured.
 *
 * The meter is filled on the playback thread and read on the main one, so every access holds [lock].
 */
@OptIn(UnstableApi::class)
class LoudnessMeterProcessor : BaseAudioProcessor() {

  private val lock = Any()
  private val meter = LoudnessMeter()
  private var scratch = FloatArray(0)
  private var encoding = C.ENCODING_INVALID
  private var channels = 2
  private var sampleRate = 0
  /** Tracks measured to their end of stream, oldest first, not yet taken. */
  private val closed = ArrayDeque<Measurement>()
  /** This stream's end was already handled (see [onQueueEndOfStream]). */
  private var streamClosed = false

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val enc = inputAudioFormat.encoding
    if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val bytes = inputBuffer.remaining()
    if (bytes == 0) return
    val out = replaceOutputBuffer(bytes)
    if (encoding == C.ENCODING_PCM_16BIT) {
      val samples = bytes / 2
      ensureScratch(samples)
      for (i in 0 until samples) {
        val v = inputBuffer.getShort()
        out.putShort(v)
        scratch[i] = v / 32768f
      }
      synchronized(lock) { meter.process(scratch, samples / channels) }
    } else {
      val samples = bytes / 4
      ensureScratch(samples)
      for (i in 0 until samples) {
        val v = inputBuffer.getFloat()
        out.putFloat(v)
        scratch[i] = v
      }
      synchronized(lock) { meter.process(scratch, samples / channels) }
    }
    out.flip()
  }

  /**
   * The end of a track's audio: the sink sends it at every change of track, gapless ones included, before
   * any of the next track's audio comes in. The track's measurement is closed here, at its exact last
   * sample, rather than when the player reports the change: by then the head of the next track (buffered
   * ahead) is already through, and a loud head would gate out a quiet track's whole measurement.
   *
   * The chain calls this again on every pass while it drains, so only the first call of a stream counts.
   * The sink also drains twice at each change, the second time with no audio in between: a stream that
   * held no audio is not a track and is not queued, or every later track would get its predecessor's value.
   */
  override fun onQueueEndOfStream() {
    if (streamClosed) return
    streamClosed = true
    synchronized(lock) {
      if (meter.measuredSeconds == 0.0) return
      closed.addLast(Measurement(meter.result(), meter.measuredSeconds))
      // Only the latest tracks matter; older ones were never taken (a stop in between).
      while (closed.size > 4) closed.removeFirst()
      meter.clear()
    }
  }

  override fun onFlush() {
    streamClosed = false
    val format = inputAudioFormat
    synchronized(lock) {
      if (format != AudioProcessor.AudioFormat.NOT_SET &&
        (format.encoding != encoding || format.channelCount != channels || format.sampleRate != sampleRate)
      ) {
        // As in the equalizer: a new format applies once the previous track's audio has drained.
        encoding = format.encoding
        channels = format.channelCount
        sampleRate = format.sampleRate
        meter.configure(sampleRate, channels)
      }
      // A seek or a stop: the filters restart, what was measured is kept until take().
      meter.resetFilters()
    }
  }

  override fun onReset() {
    scratch = FloatArray(0)
    encoding = C.ENCODING_INVALID
    sampleRate = 0
  }

  /**
   * The measurement of the track that just finished or was left: the oldest track closed at its end of
   * stream if there is one (a natural end, gapless or not), otherwise what was measured so far (a skip or
   * a stop, which the caller usually discards as incomplete). Taking the partial one starts over.
   */
  fun take(): Measurement = synchronized(lock) {
    closed.removeFirstOrNull()
      ?: Measurement(meter.result(), meter.measuredSeconds).also {
        meter.clear()
        closed.clear()
      }
  }

  /** Forgets everything, for a track loaded from scratch: nothing measured before belongs to it. */
  fun clear() = synchronized(lock) {
    meter.clear()
    closed.clear()
  }

  private fun ensureScratch(samples: Int) {
    if (scratch.size < samples) scratch = FloatArray(samples)
  }

  data class Measurement(val lufs: Double?, val seconds: Double)
}
