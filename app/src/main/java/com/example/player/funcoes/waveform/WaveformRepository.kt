package com.example.data.waveform

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.Track
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The shape of every track, for the wave bar of the player: [BUCKETS] bytes (0..255) per track.
 *
 * The contract with the player is [observe]: it emits the bytes when they exist (from the saved copy at
 * once, otherwise after the track is measured) and null while there are none or when the track cannot be
 * shown as a wave (too long, protected, unreadable). The player draws its fallback bar for null.
 *
 * Measuring is one track at a time on a low-priority thread, and can be cancelled. The track asked for by
 * [observe] goes first; [prepare] queues the rest of the library behind it. Saved shapes are checked
 * against the file's size and modification time, so a replaced file is measured again.
 */
class WaveformRepository(
  private val context: Context,
  private val scope: CoroutineScope,
  private val store: WaveformStore = WaveformStore(File(context.filesDir, "waveforms"))
) {
  private val ready = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
  private val lock = Any()
  private val queue = ArrayDeque<Track>()
  private val known = HashSet<String>() // queued, measured or given up on, in this run
  private var running = false
  @Volatile private var stopRequested = false
  private val dispatcher = Executors.newSingleThreadExecutor { r ->
    Thread({
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      r.run()
    }, "waveform").apply { isDaemon = true }
  }.asCoroutineDispatcher()

  /** The wave of [track]; null while it is not known and when it cannot exist. */
  fun observe(track: Track): Flow<ByteArray?> =
    ready.map { it[track.id] }
      .distinctUntilChanged()
      .onStart { enqueue(listOf(track), urgent = true) }

  /** Queues the whole library behind whatever was asked for, skipping battery saver. */
  fun prepare(tracks: List<Track>) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm?.isPowerSaveMode == true) return
    enqueue(tracks, urgent = false)
  }

  /** Stops measuring (the app is closing, or the user turned it off); what was saved stays. */
  fun cancel() {
    stopRequested = true
    synchronized(lock) { queue.clear(); known.clear() }
  }

  /** Forgets the shapes of tracks that are gone from the library. */
  fun retain(tracks: List<Track>) {
    val ids = tracks.mapTo(HashSet()) { it.id }
    scope.launch(dispatcher) { store.retainOnly(ids) }
  }

  private fun enqueue(tracks: List<Track>, urgent: Boolean) {
    synchronized(lock) {
      for (t in tracks) {
        if (!t.isLocalFile || t.filePath == null || t.id in known) continue
        known += t.id
        if (urgent) queue.addFirst(t) else queue.addLast(t)
      }
      if (queue.isNotEmpty()) stopRequested = false
      if (queue.isNotEmpty() && !running) {
        running = true
        scope.launch(dispatcher) { drain() }
      }
    }
  }

  private fun drain() {
    while (true) {
      val track = synchronized(lock) {
        val next = queue.removeFirstOrNull()
        // Decided under the lock, so a track queued just now either is seen here or starts a new run.
        if (next == null) running = false
        next
      } ?: return
      if (!measure(track)) {
        synchronized(lock) { running = false }
        return
      }
    }
  }

  /** Returns false when asked to stop. */
  private fun measure(track: Track): Boolean {
    val uri = Uri.parse(track.filePath)
    val stamp = stampOf(uri)
    when (val saved = store.read(track.id, stamp)) {
      is StoredWaveform.Ready -> { publish(track.id, saved.amplitudes); return true }
      StoredWaveform.Unavailable -> return true
      null -> Unit
    }
    return when (val result = WaveformDecoder.decode(context, uri, track.durationMs) { stopRequested || !scope.isActive }) {
      is DecodeResult.Done -> { store.writeReady(track.id, stamp, result.amplitudes); publish(track.id, result.amplitudes); true }
      DecodeResult.Unsupported -> { store.writeUnavailable(track.id, stamp); true }
      DecodeResult.Cancelled -> false
    }
  }

  private fun publish(id: String, amplitudes: ByteArray) {
    ready.value = ready.value + (id to amplitudes)
  }

  /** Size and modification time of the file behind [uri]; zeros when the provider does not say. */
  private fun stampOf(uri: Uri): FileStamp {
    var size = 0L
    var modified = 0L
    try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) size = it.getLong(0)
      }
    } catch (_: Exception) {}
    try {
      context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) modified = it.getLong(0)
      }
    } catch (_: Exception) {}
    return FileStamp(size, modified)
  }

  companion object {
    const val BUCKETS = PeakAccumulator.BUCKETS
  }
}
