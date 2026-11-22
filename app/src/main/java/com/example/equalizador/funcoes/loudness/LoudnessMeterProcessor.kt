package com.example.data.audio.loudness

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * First stage of the audio chain: lets the audio through unchanged and measures its loudness on the
 * way, before the spatial and the equalizer touch it, so the value describes the track itself.
 * 16-bit and float PCM are measured; any other encoding passes untouched and is not measured.
 *
 * Lock-free on the audio thread: [meter] is owned exclusively by the playback thread, while the main
 * thread reads finished tracks from [closed] or the latest [partial] snapshot and signals [clearRequested].
 */
@OptIn(UnstableApi::class)
class LoudnessMeterProcessor : BaseAudioProcessor() {

  private val meter = LoudnessMeter()
  private var scratch = FloatArray(0)
  private var encoding = C.ENCODING_INVALID
  private var channels = 2
  private var sampleRate = 0
  private var lastMeasuredSeconds = 0.0
  /** Tracks measured to their end of stream, oldest first, not yet taken. */
  private val closed = ConcurrentLinkedDeque<Measurement>()
  /** Latest partial snapshot published on the audio thread whenever a 100 ms block completes. */
  @Volatile private var partial = Measurement(null, 0.0)
  /** Set by the main thread to ask the audio thread to clear [meter] before its next buffer. */
  private val clearRequested = AtomicBoolean(false)
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
    applyPendingClear()
    val out = replaceOutputBuffer(bytes)
    if (encoding == C.ENCODING_PCM_16BIT) {
      val samples = bytes / 2
      ensureScratch(samples)
      for (i in 0 until samples) {
        val v = inputBuffer.getShort()
        out.putShort(v)
        scratch[i] = v / 32768f
      }
      meter.process(scratch, samples / channels)
    } else {
      val samples = bytes / 4
      ensureScratch(samples)
      for (i in 0 until samples) {
        val v = inputBuffer.getFloat()
        out.putFloat(v)
        scratch[i] = v
      }
      meter.process(scratch, samples / channels)
    }
    val secs = meter.measuredSeconds
    if (secs != lastMeasuredSeconds) {
      lastMeasuredSeconds = secs
      partial = Measurement(meter.result(), secs)
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
    applyPendingClear()
    if (meter.measuredSeconds == 0.0) return
    closed.addLast(Measurement(meter.result(), meter.measuredSeconds))
    // Only the latest tracks matter; older ones were never taken (a stop in between).
    while (closed.size > 4) closed.pollFirst()
    meter.clear()
    lastMeasuredSeconds = 0.0
    partial = Measurement(null, 0.0)
  }

  override fun onFlush() {
    streamClosed = false
    applyPendingClear()
    val format = inputAudioFormat
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

  override fun onReset() {
    scratch = FloatArray(0)
    encoding = C.ENCODING_INVALID
    sampleRate = 0
  }

  private fun applyPendingClear() {
    if (clearRequested.compareAndSet(true, false)) {
      meter.clear()
      lastMeasuredSeconds = 0.0
      partial = Measurement(null, 0.0)
    }
  }

  /**
   * The measurement of the track that just finished or was left: the oldest track closed at its end of
   * stream if there is one (a natural end, gapless or not), otherwise what was measured so far (a skip or
   * a stop, which the caller usually discards as incomplete). Taking the partial one starts over.
   */
  fun take(): Measurement {
    closed.pollFirst()?.let { return it }
    val snap = partial
    partial = Measurement(null, 0.0)
    clearRequested.set(true)
    closed.clear()
    return snap
  }

  /** Forgets everything, for a track loaded from scratch: nothing measured before belongs to it. */
  fun clear() {
    clearRequested.set(true)
    partial = Measurement(null, 0.0)
    closed.clear()
  }

  private fun ensureScratch(samples: Int) {
    if (scratch.size < samples) scratch = FloatArray(samples)
  }

  data class Measurement(val lufs: Double?, val seconds: Double)
}
