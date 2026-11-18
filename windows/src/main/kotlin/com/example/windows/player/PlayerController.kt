package com.example.windows.player

import com.example.data.lyrics.Lyrics
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.model.TrackJson
import com.example.data.playback.CoverNeighbors
import com.example.data.playback.CrossfadePlanner
import com.example.data.playback.PlaybackFailure
import com.example.data.playback.PlaybackOrder
import com.example.data.playback.PlaybackProblem
import com.example.data.playback.PlaybackRestore
import com.example.data.playback.RestoreOutcome
import com.example.data.playback.SleepTimerPlan
import com.example.windows.audio.AudioOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Where the controller keeps what survives a restart. The app wires it to the Room `settings` table. */
interface PlayerPrefs {
  fun get(key: String): String?
  fun put(key: String, value: String)
}

class MemoryPrefs : PlayerPrefs {
  val map = HashMap<String, String>()
  override fun get(key: String) = map[key]
  override fun put(key: String, value: String) { map[key] = value }
}

/** Everything the player screens read besides the live position (that comes from the audio engine). */
data class PlayerUi(
  val current: Track? = null,
  /** "A seguir": played before the list continues. */
  val queue: List<Track> = emptyList(),
  /** The playback context: the library, or the playlist being played. */
  val list: List<Track> = emptyList(),
  val history: List<Track> = emptyList(),
  val shuffle: Boolean = false,
  val repeat: RepeatMode = RepeatMode.OFF,
  val shuffleOrder: List<String> = emptyList(),
  val volume: Float = 1f,
  val problem: PlaybackProblem? = null,
  val sleepEndsAtMs: Long? = null,
  val sleepAtEndOfTrack: Boolean = false,
  val crossfadeSeconds: Int = 0,
  val lyrics: Lyrics? = null,
  /** True when the full player is open; false shows only the mini player. */
  val expanded: Boolean = false,
) {
  val hasTrack: Boolean get() = current != null
}

/**
 * The desktop twin of PlaybackController: queue, order, repeat, history, crossfade, sleep timer and what is
 * saved between runs, over any [AudioOut]. The rules come from the shared pure objects ([PlaybackOrder],
 * [CoverNeighbors], [CrossfadePlanner], [PlaybackRestore]) so the buttons behave as on Android.
 */
class PlayerController(
  private val out: AudioOut,
  private val prefs: PlayerPrefs = MemoryPrefs(),
  private val scope: CoroutineScope? = null,
  private val clock: () -> Long = System::currentTimeMillis,
  private val exists: (File) -> Boolean = File::exists,
) {
  private val _ui = MutableStateFlow(PlayerUi())
  val ui: StateFlow<PlayerUi> = _ui.asStateFlow()
  val engine get() = out.state

  private var failures = 0
  private var armed: Track? = null
  private var lastSavedPos = 0L

  init {
    out.onEnded = { crossfaded -> onEnded(crossfaded) }
    loadPrefs()
    scope?.launch {
      while (true) {
        delay(1000)
        tickSleep(clock())
        savePosition()
      }
    }
  }

  private fun update(f: (PlayerUi) -> PlayerUi) { _ui.value = f(_ui.value) }

  // Playing -----------------------------------------------------------------------------------------

  /** Plays [track]; [list] (when given) becomes the playback context, with a fresh shuffle order. */
  fun play(track: Track, list: List<Track>? = null) {
    failures = 0
    if (list != null) update { it.copy(list = list, shuffleOrder = emptyList()) }
    start(track, pushHistory = true)
  }

  private fun start(track: Track, pushHistory: Boolean, startMs: Long = 0, paused: Boolean = false) {
    val prev = _ui.value.current
    update {
      it.copy(
        current = track, problem = null, lyrics = null,
        history = if (pushHistory && prev != null) PlaybackOrder.pushHistory(it.history, prev, track) else it.history
      )
    }
    val file = track.filePath?.let(::File)
    if (file == null || !exists(file)) { fail(track, network = false); return }
    out.play(file, startMs, paused)
    armNext()
    save()
  }

  private fun fail(track: Track, network: Boolean) {
    failures++
    val s = _ui.value
    val canAdvance = s.queue.isNotEmpty() || s.list.size > 1
    when (val o = PlaybackFailure.decide(track.id, network, failures, canAdvance)) {
      PlaybackFailure.Outcome.Skip -> next(auto = true, afterFailure = true)
      is PlaybackFailure.Outcome.Stop -> { out.stop(); update { it.copy(problem = o.problem) } }
    }
  }

  fun retry() { _ui.value.current?.let { failures = 0; start(it, pushHistory = false) } }

  fun togglePlayPause() {
    val s = _ui.value
    val c = s.current ?: return
    if (out.state.value.file == null) start(c, pushHistory = false) else out.togglePause()
  }

  /** [auto] is true when the track ended on its own (repeat-one loops then; the Next button always moves on). */
  fun next(auto: Boolean = false, afterFailure: Boolean = false) {
    val s = _ui.value
    val r = PlaybackOrder.resolveNext(s.current, s.queue, s.list, s.shuffle, s.repeat, auto, shuffleOrder = s.shuffleOrder)
    update { it.copy(queue = r.remainingQueue, shuffleOrder = r.shuffleOrder ?: it.shuffleOrder) }
    val t = r.track
    when {
      t == null -> { out.stop(); armed = null; save() }
      r.loopSameTrack -> start(t, pushHistory = false)
      else -> { if (!afterFailure) failures = 0; start(t, pushHistory = true) }
    }
  }

  /** Past [RESTART_MS] Previous restarts the track; before it goes back through the history. */
  fun previous() {
    val s = _ui.value
    if (out.state.value.positionMs > RESTART_MS || s.current == null) { out.seek(0); return }
    val t = PlaybackOrder.previous(s.history, s.list, s.current) ?: run { out.seek(0); return }
    if (s.history.isNotEmpty()) update { it.copy(history = it.history.dropLast(1)) }
    start(t, pushHistory = false)
  }

  fun seekTo(ms: Long) = out.seek(SeekMath.clamp(ms, out.state.value.durationMs))
  fun seekBy(deltaMs: Long) = seekTo(out.state.value.positionMs + deltaMs)
  fun seekFraction(f: Float) = seekTo(SeekMath.fromFraction(f, out.state.value.durationMs))

  // Order -------------------------------------------------------------------------------------------

  fun setShuffle(on: Boolean) { update { it.copy(shuffle = on, shuffleOrder = emptyList()) }; armNext(); savePrefs() }
  fun toggleShuffle() = setShuffle(!_ui.value.shuffle)
  fun cycleRepeat() { update { it.copy(repeat = PlaybackOrder.nextRepeatMode(it.repeat)) }; armNext(); savePrefs() }

  // Queue -------------------------------------------------------------------------------------------

  fun playNext(t: Track) { update { it.copy(queue = PlaybackOrder.enqueueNext(it.queue, t)) }; armNext(); save() }
  fun addToQueue(t: Track) { update { it.copy(queue = PlaybackOrder.enqueueLast(it.queue, t)) }; armNext(); save() }
  fun removeFromQueue(index: Int) { update { it.copy(queue = PlaybackOrder.removeAt(it.queue, index)) }; armNext(); save() }
  /** Puts the queue back to [q] (undo of a remove or a clear). */
  fun setQueue(q: List<Track>) { update { it.copy(queue = q) }; armNext(); save() }
  /** Moves a queue item to the front: it plays right after the current track. */
  fun queueToFront(index: Int) = moveInQueue(index, 0)
  fun clearQueue() { update { it.copy(queue = emptyList()) }; armNext(); save() }

  fun moveInQueue(from: Int, to: Int) {
    val q = _ui.value.queue.toMutableList()
    if (from !in q.indices || to !in q.indices || from == to) return
    q.add(to, q.removeAt(from))
    update { it.copy(queue = q) }; armNext(); save()
  }

  /** Plays the queue item at [index] now and drops what was before it. */
  fun playQueueItem(index: Int) {
    val r = PlaybackOrder.jumpTo(_ui.value.queue, index) ?: return
    update { it.copy(queue = r.remainingQueue) }
    failures = 0
    start(r.track!!, pushHistory = true)
  }

  fun neighbors(): CoverNeighbors.Result {
    val s = _ui.value
    return CoverNeighbors.of(s.current, s.queue, s.list, s.history, s.shuffle, s.shuffleOrder, s.repeat)
  }

  /** What the list plays after the queue, for the "A seguir" list under it. */
  fun upcoming(limit: Int = 50): List<Track> {
    val s = _ui.value
    return PlaybackOrder.upcoming(s.current, s.list, s.shuffle, s.shuffleOrder, s.repeat, limit)
  }

  // Crossfade ---------------------------------------------------------------------------------------

  fun setCrossfade(seconds: Int) {
    update { it.copy(crossfadeSeconds = seconds.coerceIn(0, CrossfadePlanner.CHOICES.last)) }
    armNext(); savePrefs()
  }

  /** Tells the engine which file to fade into, or nothing when the planner says the pair does not qualify. */
  private fun armNext() {
    val s = _ui.value
    val nx = if (s.repeat == RepeatMode.ONE) null else neighbors().next
    val ok = CrossfadePlanner.eligible(
      current = s.current, next = nx, seconds = s.crossfadeSeconds,
      repeatsSameTrack = s.repeat == RepeatMode.ONE, spatialOn = spatialActive,
      narrationBetween = false, sleepAtEndOfTrack = s.sleepAtEndOfTrack
    )
    val f = if (ok) nx?.filePath?.let(::File)?.takeIf(exists) else null
    armed = if (f != null) nx else null
    out.armNext(f, s.crossfadeSeconds * 1000)
  }

  /** Set by the audio session when Tarkas Spatial is on: its processor is bound to one stream, so no crossfade. */
  @Volatile var spatialActive = false

  private fun onEnded(crossfaded: Boolean) {
    if (crossfaded) {
      val t = armed ?: return
      // The engine already plays the armed track: advance the state as Next would, without restarting.
      val s = _ui.value
      val r = PlaybackOrder.resolveNext(s.current, s.queue, s.list, s.shuffle, s.repeat, true, shuffleOrder = s.shuffleOrder)
      update {
        it.copy(
          current = t, queue = r.remainingQueue, shuffleOrder = r.shuffleOrder ?: it.shuffleOrder, lyrics = null,
          history = s.current?.let { c -> PlaybackOrder.pushHistory(it.history, c, t) } ?: it.history
        )
      }
      armNext(); save()
      return
    }
    if (_ui.value.sleepAtEndOfTrack) { sleepOff(); out.stop(); return }
    next(auto = true)
  }

  // Sleep timer -------------------------------------------------------------------------------------

  fun sleepIn(minutes: Int) {
    update { it.copy(sleepEndsAtMs = SleepTimerPlan.endsAt(clock(), minutes), sleepAtEndOfTrack = false) }
    armNext()
  }
  fun sleepAtEndOfTrack() { update { it.copy(sleepEndsAtMs = null, sleepAtEndOfTrack = true) }; armNext() }
  fun sleepOff() { update { it.copy(sleepEndsAtMs = null, sleepAtEndOfTrack = false) } }
  fun sleepRemainingMs(now: Long = clock()): Long? = _ui.value.sleepEndsAtMs?.let { SleepTimerPlan.remainingMs(it, now) }

  /** Pauses when the timer has run out. Called once a second, and directly by tests. */
  fun tickSleep(now: Long) {
    val end = _ui.value.sleepEndsAtMs ?: return
    if (SleepTimerPlan.remainingMs(end, now) <= 0L) { sleepOff(); out.pause() }
  }

  // Volume ------------------------------------------------------------------------------------------

  fun setVolume(v: Float) { val x = v.coerceIn(0f, 1f); out.volume = x; update { it.copy(volume = x) }; savePrefs() }
  fun volumeBy(d: Float) = setVolume(_ui.value.volume + d)

  fun setExpanded(on: Boolean) = update { it.copy(expanded = on) }
  fun setLyrics(l: Lyrics?) = update { it.copy(lyrics = l) }

  // Saved state -------------------------------------------------------------------------------------

  fun stateJson(): String? {
    val s = _ui.value
    val c = s.current ?: return null
    return JSONObject()
      .put("current", TrackJson.toJson(c))
      .put("queue", JSONArray(s.queue.map(TrackJson::toJson)))
      .put("context", JSONArray(s.list.map(TrackJson::toJson)))
      .toString()
  }

  private fun save() {
    val c = _ui.value.current
    val json = stateJson()
    if (json == null || c == null) { prefs.put(K_STATE, ""); return }
    prefs.put(K_STATE, json)
    prefs.put(K_OWNER, c.id)
    prefs.put(K_POS, out.state.value.positionMs.toString())
  }

  /** Saves the position, so a close right now still resumes where it was. */
  fun savePositionNow() {
    val c = _ui.value.current ?: return
    prefs.put(K_POS, out.state.value.positionMs.toString()); prefs.put(K_OWNER, c.id)
  }

  private fun savePosition() {
    val p = out.state.value.positionMs
    if (_ui.value.current != null && kotlin.math.abs(p - lastSavedPos) >= 5000) { lastSavedPos = p; savePositionNow() }
  }

  private fun savePrefs() {
    val s = _ui.value
    prefs.put(
      K_PREFS,
      JSONObject().put("shuffle", s.shuffle).put("repeat", s.repeat.name)
        .put("volume", s.volume.toDouble()).put("xfade", s.crossfadeSeconds).toString()
    )
  }

  private fun loadPrefs() {
    val raw = prefs.get(K_PREFS) ?: return
    try {
      val o = JSONObject(raw)
      val v = o.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f)
      out.volume = v
      update {
        it.copy(
          shuffle = o.optBoolean("shuffle"), volume = v,
          repeat = runCatching { RepeatMode.valueOf(o.optString("repeat", "OFF")) }.getOrDefault(RepeatMode.OFF),
          crossfadeSeconds = o.optInt("xfade", 0).coerceIn(0, CrossfadePlanner.CHOICES.last)
        )
      }
    } catch (_: Exception) { /* unreadable preferences: defaults */ }
  }

  /** Puts back the saved track, queue and position, paused. Returns what happened, for the caller to report. */
  fun restore(): RestoreOutcome {
    val raw = prefs.get(K_STATE)?.takeIf { it.isNotEmpty() }
    val outcome = PlaybackRestore.read(raw, prefs.get(K_OWNER), prefs.get(K_POS)?.toLongOrNull())
    if (outcome is RestoreOutcome.Restored) {
      val st = outcome.state
      update { it.copy(current = st.track, queue = st.queue, list = st.context ?: emptyList()) }
      val f = st.track.filePath?.let(::File)
      if (f != null && exists(f)) { out.play(f, st.positionMs, paused = true); armNext() }
      else update { it.copy(problem = PlaybackProblem(st.track.id, network = false)) }
    }
    return outcome
  }

  companion object {
    const val RESTART_MS = 3000L
    const val K_STATE = "player_state"
    const val K_OWNER = "player_pos_owner"
    const val K_POS = "player_pos"
    const val K_PREFS = "player_prefs"
  }
}

/** Seek arithmetic: positions are clamped to the track and a fraction of the bar is a fraction of its length. */
object SeekMath {
  fun clamp(ms: Long, durationMs: Long): Long = if (durationMs > 0) ms.coerceIn(0, durationMs) else ms.coerceAtLeast(0)
  fun fromFraction(f: Float, durationMs: Long): Long = (f.coerceIn(0f, 1f).toDouble() * durationMs).toLong()
  fun toFraction(ms: Long, durationMs: Long): Float = if (durationMs <= 0) 0f else (ms.toFloat() / durationMs).coerceIn(0f, 1f)
  fun label(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
}
