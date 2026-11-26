package com.example.windows.audio

import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** What the screen shows. Positions are in milliseconds. */
data class PlayerState(
  val file: File? = null,
  val playing: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val error: String? = null,
  /** True from the call to play until the file is open and sound starts: shown as loading, never as paused. */
  val loading: Boolean = false,
)

/**
 * Plays one file at a time through the shared [EqDsp] to the Windows default output
 * (Java Sound → DirectSound/WASAPI shared mode). One worker thread owns the decoder and the line.
 */
class DesktopPlayer(private val chunkFrames: Int = 512) : AudioOut {
  val dsp = EqDsp()
  /** win-audio: EQ + Spatial + loudness, run by the pipeline on the same float frames. */
  val chain = com.example.windows.equalizador.AudioChain(dsp)
  private val pipeline = PcmPipeline(dsp, chain)
  // win-player: the incoming track of a crossfade runs through its own filters; both follow setEq.
  private val dsp2 = EqDsp()
  val chain2 = com.example.windows.equalizador.AudioChain(dsp2)
  private val pipeline2 = PcmPipeline(dsp2, chain2)
  @Volatile private var lastEq: EqSettings? = null

  @Volatile override var volume: Float = 1f
  override var onEnded: ((Boolean) -> Unit)? = null
  @Volatile private var nextFile: File? = null
  @Volatile private var fadeMs: Int = 0
  @Volatile private var startPaused = false
  private val _state = MutableStateFlow(PlayerState())
  override val state: StateFlow<PlayerState> = _state.asStateFlow()

  private val lock = Object()
  @Volatile private var worker: Thread? = null
  @Volatile private var paused = false
  @Volatile private var seekToMs: Long = -1
  @Volatile private var stopRequested = false

  /** win-audio: name of the Java Sound mixer the music goes to; null = the Windows default. */
  @Volatile var outputMixer: String? = null

  private fun openLine(fmt: javax.sound.sampled.AudioFormat): SourceDataLine {
    val name = outputMixer
    if (name != null) {
      val info = AudioSystem.getMixerInfo().firstOrNull { it.name == name }
      if (info != null) runCatching {
        val m = AudioSystem.getMixer(info)
        val li = javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, fmt)
        if (m.isLineSupported(li)) return m.getLine(li) as SourceDataLine
      }
    }
    return AudioSystem.getSourceDataLine(fmt)
  }

  fun setEq(settings: EqSettings) { lastEq = settings; dsp.update(settings); dsp2.update(settings) }

  fun play(file: File) = play(file, 0, false)

  override fun play(file: File, startMs: Long, paused: Boolean) {
    stop()
    stopRequested = false
    this.paused = paused
    nextFile = null
    // A seek asked for the previous track must not leak into this one.
    seekToMs = -1
    _state.value = PlayerState(file = file, playing = false, positionMs = startMs, loading = true)
    worker = Thread({ run(file, startMs) }, "tarkas-audio").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
  }

  override fun armNext(file: File?, fadeMs: Int) { this.fadeMs = fadeMs; nextFile = file }

  /** True while a worker thread is running: after a natural end or an error the thread is gone but [worker] is still set. */
  private fun alive() = worker?.isAlive == true

  override fun pause() { if (alive() && !paused) togglePause() }
  override fun resume() { if (alive() && paused) togglePause() }

  override fun togglePause() {
    if (!alive()) { _state.value.file?.let(::play); return }
    paused = !paused
    _state.value = _state.value.copy(playing = !paused)
    synchronized(lock) { lock.notifyAll() }
  }

  /**
   * The requested position is published at once, so the bars and the Previous button see it without waiting for
   * the worker to reopen the file. With no worker (the track ended) the file starts again from there.
   */
  override fun seek(ms: Long) {
    val max = _state.value.durationMs
    val target = if (max > 0) ms.coerceIn(0, max) else ms.coerceAtLeast(0)
    if (!alive()) { _state.value.file?.let { play(it, target, false) }; return }
    seekToMs = target
    _state.value = _state.value.copy(positionMs = target)
    synchronized(lock) { lock.notifyAll() }
  }

  override fun stop() {
    val w = worker ?: return
    stopRequested = true
    synchronized(lock) { lock.notifyAll() }
    w.join(2000)
    worker = null
  }

  private fun run(startFile: File, startMs: Long) {
    var file = startFile
    var line: SourceDataLine? = null
    var src: PcmPipeline.Source? = null
    var xsrc: PcmPipeline.Source? = null
    var active = pipeline
    var activeDsp = dsp
    var natural = false
    var crossfaded = false
    try {
      src = active.open(file)
      val fmt = src.format
      val frameBytes = fmt.frameSize
      val rate = fmt.sampleRate.toLong()
      var totalFrames = src.totalFrames
      val durationMs = if (totalFrames > 0) totalFrames * 1000 / rate else 0
      line = openLine(fmt).apply { open(fmt, chunkFrames * 4 * frameBytes); start() }
      var framesDone = skip(src, startMs, fmt.sampleRate, activeDsp)
      _state.value = _state.value.copy(playing = !paused, durationMs = durationMs, positionMs = if (seekToMs >= 0) seekToMs else framesDone * 1000 / rate, error = null, loading = false)
      val out = ByteArray(chunkFrames * frameBytes)
      val inBuf = ByteArray(chunkFrames * frameBytes)
      var fadeFrames = 0L
      var fadeDone = 0L
      while (!stopRequested) {
        val target = seekToMs
        if (target >= 0) {
          seekToMs = -1
          line.flush()
          src!!.close()
          xsrc?.close(); xsrc = null; fadeDone = 0
          src = active.open(file)
          framesDone = skip(src!!, target, fmt.sampleRate, activeDsp)
          _state.value = _state.value.copy(positionMs = framesDone * 1000 / rate)
        }
        if (paused) {
          line.stop()
          synchronized(lock) { while (paused && !stopRequested && seekToMs < 0) lock.wait(250) }
          line.start()
          continue
        }
        // Arm the crossfade when the remaining time fits the fade (same rate and channels only).
        val nf = nextFile
        if (xsrc == null && nf != null && fadeMs > 0 && totalFrames > 0 && framesDone > 0) {
          val want = fadeMs * rate / 1000
          if (totalFrames - framesDone <= want) {
            val other = if (active === pipeline) pipeline2 else pipeline
            val s = try { other.open(nf) } catch (e: Exception) { null }
            if (s != null && s.format.matches(fmt)) { xsrc = s; fadeFrames = (totalFrames - framesDone).coerceAtLeast(1); fadeDone = 0 }
            else { s?.close(); nextFile = null }
          }
        }
        val n = active.render(src!!, chunkFrames, out)
        if (n < 0) { natural = true; break }
        var bytesOut = n
        val xs = xsrc
        if (xs != null) {
          val other = if (active === pipeline) pipeline2 else pipeline
          val m = other.render(xs, chunkFrames, inBuf)
          val frames = n / frameBytes
          PcmMix.crossfade(out, inBuf, if (m < 0) 0 else m, frames, fmt.channels, fadeDone, fadeFrames)
          fadeDone += frames
          if (fadeDone >= fadeFrames) {
            // The incoming track takes over: it is already `fadeDone` frames in.
            src.close()
            src = xs; xsrc = null
            active = other; activeDsp = if (active === pipeline) dsp else dsp2
            file = nf!!
            nextFile = null
            totalFrames = src.totalFrames
            framesDone = fadeDone
            fadeDone = 0
            crossfaded = true
            _state.value = _state.value.copy(
              file = file, durationMs = if (totalFrames > 0) totalFrames * 1000 / rate else 0,
              positionMs = framesDone * 1000 / rate
            )
            val cb = onEnded
            if (cb != null) Thread({ cb(true) }, "tarkas-advance").apply { isDaemon = true; start() }
            line.write(out, 0, bytesOut)
            continue
          }
        }
        PcmMix.gain(out, bytesOut, volume)
        line.write(out, 0, bytesOut)
        framesDone += n / frameBytes
        // What is audible is what was written minus what still waits in the line's buffer; a seek asked
        // meanwhile keeps its published position until the worker applies it (no flicker back).
        if (seekToMs < 0) {
          val buffered = ((line.bufferSize - line.available()) / frameBytes).coerceAtLeast(0)
          _state.value = _state.value.copy(positionMs = (framesDone - buffered).coerceAtLeast(0) * 1000 / rate)
        }
      }
      if (!stopRequested) line.drain()
      _state.value = if (natural && !stopRequested && _state.value.durationMs > 0) _state.value.copy(playing = false, positionMs = _state.value.durationMs)
      else _state.value.copy(playing = false)
    } catch (e: Exception) {
      _state.value = _state.value.copy(playing = false, loading = false, error = e.message ?: e.javaClass.simpleName)
    } finally {
      line?.close()
      src?.close()
      xsrc?.close()
    }
    if (natural && !stopRequested) {
      val cb = onEnded
      if (cb != null) Thread({ cb(false) }, "tarkas-ended").apply { isDaemon = true; start() }
    }
  }

  private fun skip(src: PcmPipeline.Source, ms: Long, rate: Float, d: EqDsp): Long {
    val frames = (ms * rate / 1000).toLong()
    val fs = src.decoded.frameSize
    var left = frames * fs
    while (left > 0) {
      val n = src.stream.skip(left)
      if (n <= 0) break
      left -= n
    }
    d.reset()
    return frames - left / fs
  }
}
