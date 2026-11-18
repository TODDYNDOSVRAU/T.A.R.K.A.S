package com.example.windows.player

import com.example.data.lyrics.Id3Lyrics
import com.example.data.lyrics.LrcParser
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsChoice
import com.example.data.lyrics.LyricsSource
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.waveform.PeakAccumulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Everything the always-on transport bar draws, from one flow. */
data class TransportState(
  val track: Track? = null,
  val playing: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val volume: Float = 1f,
  val shuffle: Boolean = false,
  val repeat: RepeatMode = RepeatMode.OFF,
  val queueSize: Int = 0,
  val sleepActive: Boolean = false,
  val peaks: ByteArray? = null,
  val error: String? = null,
) {
  val fraction: Float get() = SeekMath.toFraction(positionMs, durationMs)
}

/** Which side pane the player asks the shell for. */
enum class PlayerPane { QUEUE, LYRICS, DETAILS }

/** A queue as it was before a remove or a clear, so Delete can be undone. */
data class QueueUndo(val before: List<Track>, val removed: List<Track>)

/** The calls the shell's transport bar and global shortcuts make. One object for every area. */
class PlayerHooks(val controller: PlayerController, scope: CoroutineScope, peaks: StateFlow<ByteArray?>) {
  val state: StateFlow<TransportState> = combine(controller.ui, controller.engine, peaks) { ui, eng, p ->
    TransportState(
      track = ui.current, playing = eng.playing, positionMs = eng.positionMs, durationMs = eng.durationMs,
      volume = ui.volume, shuffle = ui.shuffle, repeat = ui.repeat, queueSize = ui.queue.size,
      sleepActive = ui.sleepEndsAtMs != null || ui.sleepAtEndOfTrack, peaks = p, error = eng.error,
    )
  }.stateIn(scope, SharingStarted.Eagerly, TransportState())

  /** Side pane asked for by the player (Ctrl+Q, Ctrl+L, details). Null means closed. */
  private val _pane = MutableStateFlow<PlayerPane?>(null)
  val pane: StateFlow<PlayerPane?> = _pane.asStateFlow()
  /** Set to true by the shell once it draws the pane itself; until then the player screen draws it. */
  @Volatile var shellDrawsPane = false

  private val _undo = MutableStateFlow<QueueUndo?>(null)
  val undo: StateFlow<QueueUndo?> = _undo.asStateFlow()

  fun toggle() = controller.togglePlayPause()
  fun next() = controller.next()
  fun prev() = controller.previous()
  fun seekFraction(f: Float) = controller.seekFraction(f)
  fun seekBy(ms: Long) = controller.seekBy(ms)
  fun setVolume(v: Float) = controller.setVolume(v)
  fun volumeBy(d: Float) = controller.volumeBy(d)
  fun toggleShuffle() = controller.toggleShuffle()
  fun cycleRepeat() = controller.cycleRepeat()

  fun showPane(p: PlayerPane?) { _pane.value = p }
  fun togglePane(p: PlayerPane) { _pane.value = if (_pane.value == p) null else p }

  fun removeFromQueue(index: Int) {
    val before = controller.ui.value.queue
    val t = before.getOrNull(index) ?: return
    controller.removeFromQueue(index)
    _undo.value = QueueUndo(before, listOf(t))
  }

  fun clearQueue() {
    val before = controller.ui.value.queue
    if (before.isEmpty()) return
    controller.clearQueue()
    _undo.value = QueueUndo(before, before)
  }

  /** Restores the queue as it was before the last remove or clear; false when there is nothing to undo. */
  fun undoQueue(): Boolean {
    val u = _undo.value ?: return false
    controller.setQueue(u.before)
    _undo.value = null
    return true
  }

  fun dismissUndo() { _undo.value = null }
}

/** Drag-to-reorder arithmetic of the queue rows, apart from the UI so it can be tested. */
object DragMath {
  /** The row an item dragged from [from] by [dy] pixels lands on, with rows [rowPx] tall in a list of [size]. */
  fun target(from: Int, dy: Float, rowPx: Float, size: Int): Int {
    if (size <= 0 || rowPx <= 0f) return from
    return (from + Math.round(dy / rowPx)).coerceIn(0, size - 1)
  }
}

/** Reads the loudness shape of a file, through [cache] when given; null when the file cannot be decoded. */
class WaveformReader(private val cache: WaveformCache?) {
  fun load(trackId: String, file: File): ByteArray? {
    val stamp = FileStamp.of(file)
    cache?.read(trackId, stamp)?.let { return it }
    val made = compute(file) ?: return null
    cache?.write(trackId, stamp, made)
    return made
  }

  companion object {
    fun compute(file: File): ByteArray? = try {
      AudioSystem.getAudioInputStream(file).use { raw ->
        val base = raw.format
        val pcm = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
        val src = if (base.matches(pcm)) raw else AudioSystem.getAudioInputStream(pcm, raw)
        val estimate = if (raw.frameLength > 0) raw.frameLength else
          ((AudioSystem.getAudioFileFormat(file).properties()["duration"] as? Long ?: 0L) * base.sampleRate.toLong() / 1_000_000)
        val acc = PeakAccumulator(estimatedFrames = estimate)
        val buf = ByteArray(pcm.frameSize * 4096)
        val shorts = ShortArray(buf.size / 2)
        while (true) {
          val n = src.read(buf, 0, buf.size)
          if (n <= 0) break
          val whole = n - n % pcm.frameSize
          for (i in 0 until whole / 2) shorts[i] = ((buf[2 * i].toInt() and 0xFF) or (buf[2 * i + 1].toInt() shl 8)).toShort()
          acc.add(shorts, whole / 2, pcm.channels)
        }
        if (acc.frames == 0L) null else acc.result()
      }
    } catch (e: Exception) { null }
  }
}

/** Finds the lyrics of a local file: a .lrc beside it and the ID3 tag; timed ones win. Null when none. */
object LyricsLoader {
  fun load(file: File): Lyrics? {
    if (!file.isFile) return null
    val lrc = File(file.parentFile, file.nameWithoutExtension + ".lrc")
    val sidecar = if (lrc.isFile) try { LrcParser.parse(lrc.readText(Charsets.UTF_8), LyricsSource.SIDECAR_LRC) } catch (e: Exception) { null } else null
    val embedded = try {
      file.inputStream().use { i ->
        val head = i.readNBytes(10)
        val size = Id3Lyrics.tagSize(head)
        if (size <= 0) null else Id3Lyrics.read(head + i.readNBytes(size - 10))
      }
    } catch (e: Exception) { null }
    return LyricsChoice.best(embedded, sidecar)
  }
}

/** Follows the current track and keeps its waveform and lyrics loaded off the UI thread. */
class TrackAssets(private val scope: CoroutineScope, private val waves: WaveformReader) {
  private val _peaks = MutableStateFlow<ByteArray?>(null)
  val peaks: StateFlow<ByteArray?> = _peaks.asStateFlow()
  private val _lyrics = MutableStateFlow<Lyrics?>(null)
  val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()
  private val _art = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)
  val art: StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = _art.asStateFlow()
  private var job: Job? = null
  private var loadedId: String? = null

  fun follow(track: Track?) {
    if (track?.id == loadedId) return
    loadedId = track?.id
    _peaks.value = null; _lyrics.value = null; _art.value = null
    job?.cancel()
    val f = track?.filePath?.let(::File) ?: return
    job = scope.launch(Dispatchers.IO) {
      _art.value = CoverArt.load(f)
      _lyrics.value = LyricsLoader.load(f)
      _peaks.value = waves.load(track.id, f)
    }
  }
}
