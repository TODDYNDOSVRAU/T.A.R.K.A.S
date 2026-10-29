package com.example.data.audio.spatial

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.LATENCY
import java.nio.ByteBuffer

/**
 * Tarkas Spatial in ExoPlayer's audio chain, before the equalizer (stereo in, binaural stereo out).
 *
 * The output is always delayed by the same [DELAY] frames, effect on or off, so switching never jumps
 * in time: off, a plain delay line plays; on, the engine plays. The engine starts from the dry sound
 * (intensity 0, which equals the delay line sample for sample) and glides to the target, and it glides
 * back to 0 before the delay line takes over again, so neither direction clicks.
 *
 * Any failure falls back to the dry sound; the player never stops because of this effect.
 */
@OptIn(UnstableApi::class)
class SpatialAudioProcessor : BaseAudioProcessor() {

  companion object {
    /** Frames between input and output: the engine's latency plus one hop of buffering (~23 ms at 48 kHz). */
    const val DELAY = LATENCY + HOP
  }

  private var encoding = C.ENCODING_INVALID
  private var sampleRate = 0

  // Input history, interleaved stereo: the delay line, and the source of the dry sound.
  private var ring = FloatArray(0)
  private var ringPos = 0 // frames written so far, modulo the ring size

  private var input = FloatArray(0)
  private var engineOut = FloatArray(0)
  private var output = FloatArray(0)

  // Engine output waiting to be played (a FIFO, interleaved).
  private var fifo = FloatArray(0)
  private var fifoStart = 0
  private var fifoCount = 0

  private var engine: SpatialEngine? = null
  private var running = false
  /** Engine output frames to drop after a start (they come from before it saw any input). */
  private var skipEngine = 0
  /** Output frames still taken from the delay line after a start. */
  private var fromRing = 0

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val supported = inputAudioFormat.channelCount == 2 &&
      (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
    SpatialControl.setStereoInput(supported)
    if (!supported) return AudioProcessor.AudioFormat.NOT_SET // inactive: passed through untouched
    encoding = inputAudioFormat.encoding
    if (sampleRate != inputAudioFormat.sampleRate) {
      // Another rate needs its own engine (the HRTF tables depend on it).
      engine = null
      running = false
    }
    sampleRate = inputAudioFormat.sampleRate
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val bytes = inputBuffer.remaining()
    if (bytes == 0) return
    val frames = if (encoding == C.ENCODING_PCM_16BIT) bytes / 4 else bytes / 8
    ensureCapacity(frames)
    if (encoding == C.ENCODING_PCM_16BIT) {
      for (i in 0 until frames * 2) input[i] = inputBuffer.getShort() / 32768f
    } else {
      for (i in 0 until frames * 2) input[i] = inputBuffer.getFloat()
    }
    render(frames)
    val out = replaceOutputBuffer(bytes)
    if (encoding == C.ENCODING_PCM_16BIT) {
      for (i in 0 until frames * 2) out.putShort((output[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
    } else {
      for (i in 0 until frames * 2) out.putFloat(output[i])
    }
    out.flip()
  }

  /** Turns [frames] frames of [input] into [frames] frames of [output], [DELAY] frames later. */
  private fun render(frames: Int) {
    try {
      val e = engine ?: SpatialControl.engineFor(sampleRate)?.also { engine = it }
      if (e != null && !running && SpatialControl.active) {
        // Start from the dry sound and glide in.
        e.reset(startIntensity = 0.0)
        running = true
        skipEngine = LATENCY
        fromRing = DELAY
        fifoStart = 0; fifoCount = 0
      } else if (e != null && running && !SpatialControl.active && e.currentIntensity <= 0.0) {
        // Back at the dry sound: the delay line takes over, sample-identical.
        running = false
      }

      // Keep the history (always, it is the delay line).
      for (i in 0 until frames) {
        val p = 2 * ((ringPos + i) % ringFrames())
        ring[p] = input[2 * i]
        ring[p + 1] = input[2 * i + 1]
      }

      if (running && e != null) {
        val w = e.process(input, 0, frames, engineOut)
        var from = 0
        if (skipEngine > 0) {
          from = minOf(skipEngine, w)
          skipEngine -= from
        }
        pushFifo(engineOut, from, w - from)
      }

      for (i in 0 until frames) {
        if (!running || fromRing > 0 || fifoCount == 0) {
          // The input from DELAY frames ago.
          val p = 2 * (((ringPos + i - DELAY) % ringFrames() + ringFrames()) % ringFrames())
          output[2 * i] = ring[p]
          output[2 * i + 1] = ring[p + 1]
          if (fromRing > 0) fromRing--
        } else {
          val p = 2 * fifoStart
          output[2 * i] = fifo[p]
          output[2 * i + 1] = fifo[p + 1]
          fifoStart = (fifoStart + 1) % fifoFrames()
          fifoCount--
        }
      }
      ringPos = (ringPos + frames) % ringFrames()
    } catch (t: Throwable) {
      // Never let the effect stop the music: play the input as it is.
      running = false
      System.arraycopy(input, 0, output, 0, frames * 2)
    }
  }

  private fun ringFrames() = ring.size / 2
  private fun fifoFrames() = fifo.size / 2

  private fun pushFifo(src: FloatArray, from: Int, count: Int) {
    if (count <= 0) return
    if (fifoCount + count > fifoFrames()) growFifo(fifoCount + count)
    for (i in 0 until count) {
      val p = 2 * ((fifoStart + fifoCount + i) % fifoFrames())
      fifo[p] = src[2 * (from + i)]
      fifo[p + 1] = src[2 * (from + i) + 1]
    }
    fifoCount += count
  }

  private fun growFifo(frames: Int) {
    val bigger = FloatArray(Integer.highestOneBit(frames) * 4)
    for (i in 0 until fifoCount) {
      val p = 2 * ((fifoStart + i) % fifoFrames())
      bigger[2 * i] = fifo[p]
      bigger[2 * i + 1] = fifo[p + 1]
    }
    fifo = bigger
    fifoStart = 0
  }

  /** Buffers sized for [frames]; grows only when a bigger buffer arrives (rare), never shrinks. */
  private fun ensureCapacity(frames: Int) {
    if (input.size < frames * 2) {
      input = FloatArray(frames * 2)
      output = FloatArray(frames * 2)
      engineOut = FloatArray((frames + HOP) * 2)
    }
    val needRing = DELAY + frames
    if (ringFrames() < needRing) {
      // Keep the frames already in the ring, in order, at the start of the bigger one.
      val size = Integer.highestOneBit(needRing) * 4
      val bigger = FloatArray(size * 2)
      val old = ringFrames()
      if (old > 0) {
        for (i in 0 until old) {
          val src = 2 * ((ringPos + i) % old)
          val dst = 2 * ((size - old + i) % size)
          bigger[dst] = ring[src]
          bigger[dst + 1] = ring[src + 1]
        }
      }
      ring = bigger
      ringPos = 0
    }
    if (fifoFrames() == 0) fifo = FloatArray(4096 * 2)
  }

  override fun onQueueEndOfStream() {
    // The last DELAY frames are still inside: push them out with silence behind them.
    val frames = DELAY
    ensureCapacity(frames)
    input.fill(0f, 0, frames * 2)
    render(frames)
    val bytesPerFrame = if (encoding == C.ENCODING_PCM_16BIT) 4 else 8
    val out = replaceOutputBuffer(frames * bytesPerFrame)
    if (encoding == C.ENCODING_PCM_16BIT) {
      for (i in 0 until frames * 2) out.putShort((output[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
    } else {
      for (i in 0 until frames * 2) out.putFloat(output[i])
    }
    out.flip()
  }

  override fun onFlush() {
    // A seek or a new track: the history no longer belongs to what plays next.
    ring.fill(0f)
    ringPos = 0
    fifoStart = 0; fifoCount = 0
    running = false
    engine?.reset()
  }

  override fun onReset() {
    onFlush()
    engine = null
    encoding = C.ENCODING_INVALID
    ring = FloatArray(0); input = FloatArray(0); output = FloatArray(0); engineOut = FloatArray(0); fifo = FloatArray(0)
  }
}
