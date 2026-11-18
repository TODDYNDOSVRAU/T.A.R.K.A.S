package com.example.windows.equalizador

import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.loudness.LoudnessMeter
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialEngine.Companion.HOP
import com.example.data.audio.spatial.SpatialEngine.Companion.LATENCY
import com.example.data.audio.spatial.SpatialStage
import kotlin.math.abs

/**
 * What the player runs on every decoded block: the equalizer ([EqDsp], in place), Tarkas Spatial (stereo only,
 * through the same engines the app uses) and the "Volume entre faixas" gain, in that order. Everything is
 * in place and keeps the frame count, so the pipeline needs no resizing.
 *
 * Spatial keeps the app's rule: the output is always delayed by [DELAY] frames while the track is stereo,
 * effect on or off, so switching never jumps in time; the engine starts from the dry sound and glides in.
 * A failure falls back to the dry sound and never stops the music.
 */
class AudioChain(
  val dsp: EqDsp = EqDsp(),
  private val stageFor: (sampleRate: Int) -> SpatialStage? = { rate ->
    if (SpatialControl.preset.virtual51) SpatialControl.cinemaFor(rate) else SpatialControl.engineFor(rate)
  },
) : EffectChain {

  companion object {
    /** Frames between input and output of the spatial stage: the engine's latency plus one hop (~23 ms at 48 kHz). */
    const val DELAY = LATENCY + HOP
    private const val GAIN_STEP = 1f / 2048f
    /** Both pipelines of a crossfade share one engine: only the chain that processed lately may hold it. */
    private val owner = java.util.concurrent.atomic.AtomicReference<AudioChain?>(null)
    private const val LEASE_NANOS = 300_000_000L
  }

  @Volatile private var lastProcess = 0L
  private var rate = 48_000
  private var channels = 2

  // Spatial state: delay line (interleaved stereo), the engine FIFO, and scratch buffers.
  private var ring = FloatArray(0)
  private var ringPos = 0
  private var input = FloatArray(0)
  private var engineOut = FloatArray(0)
  private var fifo = FloatArray(0)
  private var fifoStart = 0
  private var fifoCount = 0
  private var engine: SpatialStage? = null
  private var running = false
  private var skipEngine = 0
  private var fromRing = 0

  // Loudness: target gain set by the state, current gain glides to it; the meter reads the input.
  @Volatile private var targetGain = 1f
  private var gain = 1f
  private val meter = LoudnessMeter()
  private val meterLock = Any()
  @Volatile private var metering = false

  /** Linear gain the loudness decision asks for (1 = none). */
  fun setLoudnessGain(linear: Float) { targetGain = linear }

  /** Turns the loudness measurement on or off (off when the track already has a loudness). */
  fun setMetering(on: Boolean) { metering = on }

  /** The loudness measured since the last [takeMeasurement] and the seconds it covers; clears the meter. */
  fun takeMeasurement(): Pair<Double?, Double> = synchronized(meterLock) {
    (meter.result() to meter.measuredSeconds).also { meter.clear() }
  }

  /** True while the spatial stage is delaying the stereo output. */
  val spatialDelayFrames: Int get() = if (channels == 2) DELAY else 0

  override fun configure(rate: Int, channels: Int) {
    this.rate = rate
    this.channels = channels
    dsp.configure(rate, channels)
    synchronized(meterLock) { meter.configure(rate, channels) }
    SpatialControl.setStereoInput(channels == 2)
    resetSpatial()
  }

  override fun reset() {
    dsp.reset()
    SpatialControl.clearFailure()
    synchronized(meterLock) { meter.resetFilters() }
    resetSpatial()
  }

  private fun claim(): Boolean {
    val o = owner.get()
    if (o === this) return true
    if (o != null && System.nanoTime() - o.lastProcess < LEASE_NANOS) return false
    return owner.compareAndSet(o, this)
  }

  private fun resetSpatial() {
    owner.compareAndSet(this, null)
    ring.fill(0f); ringPos = 0
    fifoStart = 0; fifoCount = 0
    running = false
    engine?.reset()
    engine = null
  }

  override fun process(floats: FloatArray, frames: Int) {
    if (frames <= 0) return
    lastProcess = System.nanoTime()
    if (metering) synchronized(meterLock) { meter.process(floats, frames) }
    dsp.process(floats, frames)
    if (channels == 2) spatial(floats, frames)
    applyGain(floats, frames * channels)
  }

  private fun applyGain(floats: FloatArray, samples: Int) {
    val target = targetGain
    if (gain == target) {
      if (target == 1f) return
      for (i in 0 until samples) floats[i] *= target
      return
    }
    // Glide, one step per frame, so a change of track or of mode never clicks.
    val ch = channels
    var i = 0
    while (i < samples) {
      gain = if (abs(target - gain) <= GAIN_STEP) target else if (target > gain) gain + GAIN_STEP else gain - GAIN_STEP
      for (c in 0 until ch) floats[i + c] *= gain
      i += ch
    }
  }

  private fun spatial(io: FloatArray, frames: Int) {
    ensureCapacity(frames)
    System.arraycopy(io, 0, input, 0, frames * 2)
    try {
      val current: SpatialStage? = if (SpatialControl.failed || !claim()) null else stageFor(rate)
      if (current !== engine && (current != null || !running)) {
        val old = engine
        if (old != null && running && current != null && old.currentIntensity > 0.0) {
          // Another preset path is chosen while the old one plays: it glides to 0 first, then this one takes over.
        } else {
          engine = current
          running = false
        }
      }
      if (current == null && engine != null && running && SpatialControl.active && engine?.currentIntensity?.let { it <= 0.0 } == true) {
        engine = null
        running = false
      }
      val e = engine
      if (e != null && !running && SpatialControl.active) {
        e.reset(startIntensity = 0.0)
        running = true
        skipEngine = e.latency
        fromRing = DELAY
        fifoStart = 0; fifoCount = 0
      } else if (e != null && running && !SpatialControl.active && e.currentIntensity <= 0.0) {
        running = false
      }

      val rf = ringFrames()
      for (i in 0 until frames) {
        val p = 2 * ((ringPos + i) % rf)
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
          val p = 2 * (((ringPos + i - DELAY) % rf + rf) % rf)
          io[2 * i] = ring[p]
          io[2 * i + 1] = ring[p + 1]
          if (fromRing > 0) fromRing--
        } else {
          val p = 2 * fifoStart
          io[2 * i] = fifo[p]
          io[2 * i + 1] = fifo[p + 1]
          fifoStart = (fifoStart + 1) % fifoFrames()
          fifoCount--
        }
      }
      ringPos = (ringPos + frames) % rf
    } catch (t: Throwable) {
      // Never let the effect stop the music: the input plays as it is.
      running = false
      engine = null
      System.arraycopy(input, 0, io, 0, frames * 2)
      if (SpatialControl.reportFailure()) System.err.println("spatial.bypass: Spatial audio failed and is bypassed: $t")
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

  private fun ensureCapacity(frames: Int) {
    if (input.size < frames * 2) {
      input = FloatArray(frames * 2)
      engineOut = FloatArray((frames + HOP) * 2)
    }
    val needRing = DELAY + frames
    if (ringFrames() < needRing) {
      val size = Integer.highestOneBit(needRing) * 4
      val bigger = FloatArray(size * 2)
      val old = ringFrames()
      for (i in 0 until old) {
        val src = 2 * ((ringPos + i) % old)
        val dst = 2 * ((size - old + i) % size)
        bigger[dst] = ring[src]
        bigger[dst + 1] = ring[src + 1]
      }
      ring = bigger
      ringPos = 0
    }
    if (fifoFrames() == 0) fifo = FloatArray(4096 * 2)
  }
}
