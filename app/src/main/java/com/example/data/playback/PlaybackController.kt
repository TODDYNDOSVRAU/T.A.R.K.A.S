package com.example.data.playback

import android.content.Context
import com.example.data.assistant.CuriositySpeechManager
import com.example.data.audio.AudioEngineManager
import com.example.data.model.Curiosity
import com.example.data.model.CuriosityConfig
import com.example.data.model.CuriosityTiming
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.model.TrackJson
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A message about playback for the screen (a track that failed, the sleep timer). */
data class PlaybackNotice(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** The sleep timer: playback pauses at [endsAtMs] (wall clock), or when the current track ends. */
data class SleepTimer(val endsAtMs: Long? = null, val endOfTrack: Boolean = false)

/**
 * Everything about what plays and in what order: the queue, shuffle and repeat, the curiosity
 * narrations between tracks, tracks from the device, Audius and Jamendo, gapless moves, the sleep timer and the saved
 * queue. It lives as long as the process (see [com.example.AppGraph]), so music keeps playing after
 * the screen is gone; the media session service and the screens both drive it.
 */
class PlaybackController(
  private val context: Context,
  private val musicRepository: MusicRepository,
  private val curiosityRepository: CuriosityRepository,
  private val audioEngine: AudioEngineManager,
  private val preferences: PreferencesRepository,
  private val speech: CuriositySpeechManager
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val store = context.getSharedPreferences("tarkas_playback", Context.MODE_PRIVATE)

  val currentTrack: StateFlow<Track?> get() = musicRepository.currentTrack

  // Playback order: manual queue ("Up Next") first, then the playback context.
  private val _queue = MutableStateFlow<List<Track>>(emptyList())
  val queue: StateFlow<List<Track>> = _queue.asStateFlow()

  private val _shuffle = MutableStateFlow(preferences.getShuffle())
  val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

  private val _repeatMode = MutableStateFlow(preferences.getRepeatMode())
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  // null = the whole library; otherwise the playlist/favorites/results the user started from.
  private var contextTracks: List<Track>? = null
  private val playHistory = mutableListOf<Track>()

  private val _curiosityConfig = MutableStateFlow(preferences.getCuriosityConfig())
  val curiosityConfig: StateFlow<CuriosityConfig> = _curiosityConfig.asStateFlow()

  private val _activeCuriosity = MutableStateFlow<Curiosity?>(null)
  val activeCuriosity: StateFlow<Curiosity?> = _activeCuriosity.asStateFlow()

  private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
  val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

  private val _notices = MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 4)
  val notices: SharedFlow<PlaybackNotice> = _notices.asSharedFlow()

  val isSpeaking: StateFlow<Boolean> get() = speech.isSpeaking

  // Every track, from the device, Audius or Jamendo, plays in the app's own engine.
  val isPlaying: StateFlow<Boolean> = audioEngine.isPlaying

  val positionMs: StateFlow<Long> = audioEngine.currentPositionMs

  /** The engine's duration once it knows it, the catalogue's until then. */
  val durationMs: StateFlow<Long> = combine(musicRepository.currentTrack, audioEngine.durationMs) { track, local ->
    local.takeIf { it > 0 } ?: track?.durationMs ?: 0L
  }.stateIn(scope, SharingStarted.Eagerly, 0L)

  /** Tracks that failed one after another; stops the skipping when nothing in the list plays. */
  private var failuresInARow = 0

  /**
   * True from the moment a track is asked to start until it plays (or fails, or is paused). Between two
   * tracks neither the engine nor the narration is playing; without this the media session would report
   * "paused" at every boundary, drop the foreground service, and Android 15+ would then refuse the next
   * track its audio focus while the screen is off.
   */
  private val _starting = MutableStateFlow(false)

  /** What the media session reports: music, a narration, or a track on its way. */
  val wantsToPlay: StateFlow<Boolean> = combine(isPlaying, speech.isSpeaking, _starting) { p, s, st -> p || s || st }
    .stateIn(scope, SharingStarted.Eagerly, false)

  /** What runs once the current narration ends (the track after an intro, the next track after an outro). */
  private var afterNarration: (() -> Unit)? = null
  /** Set when the user paused during a narration: Play runs it instead of narrating again. */
  private var resumeAfterNarration: (() -> Unit)? = null

  init {
    restore()
    scope.launch {
      // The first value is the restored, paused state; saving it could store position 0 before the
      // restored track has even been prepared.
      isPlaying.drop(1).collect { playing ->
        if (playing) {
          failuresInARow = 0
          _starting.value = false
        } else {
          savePosition()
        }
      }
    }
    // The position is saved every few seconds while playing, so a crash or a kill loses little.
    scope.launch {
      while (isActive) {
        delay(SAVE_EVERY_MS)
        if (isPlaying.value) savePosition()
      }
    }
  }

  // ---- starting tracks ----

  /** Plays a track from the whole library. */
  fun playTrack(track: Track) {
    contextTracks = null
    playInternal(track, recordHistory = true)
  }

  /** Plays a track and keeps going through [list] (a playlist, favorites, search results...). */
  fun playFromList(track: Track, list: List<Track>) {
    contextTracks = list
    playInternal(track, recordHistory = true)
  }

  /** Plays [track] as the current one, with its start-of-track curiosity when there is one. */
  private fun playInternal(track: Track, recordHistory: Boolean) {
    if (recordHistory) {
      musicRepository.currentTrack.value?.let { prev ->
        val updated = PlaybackOrder.pushHistory(playHistory, prev, track)
        playHistory.clear()
        playHistory.addAll(updated)
      }
    }
    musicRepository.selectTrack(track)
    val cur = curiosityRepository.getCuriosityForTrack(track)
    _activeCuriosity.value = cur
    resumeAfterNarration = null
    _starting.value = true

    if (narratesIntro(track)) {
      // The intro curiosity plays before the music.
      audioEngine.stopPlayback()
      narrate(track, cur!!, isIntro = true) { startTrackAudio(track) }
    } else {
      startTrackAudio(track)
    }
    save()
  }

  /** Speaks a curiosity, remembering what follows it so a pause in the middle can resume into it. */
  private fun narrate(track: Track, curiosity: Curiosity, isIntro: Boolean, then: () -> Unit) {
    afterNarration = then
    speech.speakCuriosity(track, curiosity, isIntro = isIntro, config = _curiosityConfig.value) {
      afterNarration = null
      then()
    }
  }

  /** Pauses during a narration: the voice stops and Play later goes straight to what came after it. */
  private fun pauseNarration() {
    resumeAfterNarration = afterNarration
    afterNarration = null
    speech.stopSpeech(continueAfter = false)
    _starting.value = false
  }

  private fun startTrackAudio(track: Track, startPositionMs: Long = 0L) {
    val path = track.filePath
    _starting.value = true
    if (path == null) {
      onTrackFailed(track, network = false)
    } else {
      audioEngine.playTrack(
        path,
        onCompletion = { onTrackPlaybackCompleted(track) },
        onError = { network -> onTrackFailed(track, network) },
        startPositionMs = startPositionMs
      )
      refreshUpNext()
    }
  }

  /** Says what went wrong and moves on, unless several tracks in a row failed (offline, say). */
  private fun onTrackFailed(track: Track, network: Boolean) {
    failuresInARow++
    _starting.value = false
    val reason = if (network) "Sem conexão para tocar “${track.title}”." else "Não foi possível tocar “${track.title}”."
    val canSkip = failuresInARow < 3 && (contextList().size > 1 || _queue.value.isNotEmpty())
    if (canSkip) {
      _notices.tryEmit(PlaybackNotice("$reason Pulando para a próxima."))
      advance(auto = false)
    } else {
      _notices.tryEmit(
        PlaybackNotice(reason, actionLabel = "Tentar de novo", action = { failuresInARow = 0; playInternal(track, recordHistory = false) })
      )
    }
  }

  private fun onTrackPlaybackCompleted(completedTrack: Track) {
    if (_sleepTimer.value?.endOfTrack == true) {
      // The sleep timer was set to the end of this track: stop here.
      _sleepTimer.value = null
      _starting.value = false
      audioEngine.stopPlayback()
      save()
      return
    }
    val cur = _activeCuriosity.value ?: curiosityRepository.getCuriosityForTrack(completedTrack)
    if (narratesOutro(completedTrack, cur)) {
      _starting.value = true
      narrate(completedTrack, cur!!, isIntro = false) { advance(auto = true) }
    } else {
      advance(auto = true)
    }
  }

  private fun narratesIntro(track: Track): Boolean {
    val config = _curiosityConfig.value
    // Only a verified curiosity is spoken; a track without one simply plays.
    return curiosityRepository.getCuriosityForTrack(track) != null && config.enabled &&
      (config.timing == CuriosityTiming.START_OF_TRACK || config.timing == CuriosityTiming.BOTH)
  }

  private fun narratesOutro(track: Track, cur: Curiosity? = curiosityRepository.getCuriosityForTrack(track)): Boolean {
    val config = _curiosityConfig.value
    // Repeat-one loops the same track, so its curiosity is not narrated again on every pass.
    return cur != null && _repeatMode.value != RepeatMode.ONE && config.enabled &&
      (config.timing == CuriosityTiming.END_OF_TRACK || config.timing == CuriosityTiming.BOTH)
  }

  private fun contextList(): List<Track> = contextTracks ?: musicRepository.tracks.value

  // ---- gapless ----

  /**
   * Queues the track that will follow in the player itself, so it starts with no gap. Not when a
   * narration, a web track or the end-of-track sleep timer sits between the two.
   */
  private fun refreshUpNext() {
    val current = musicRepository.currentTrack.value
    if (current == null || _sleepTimer.value?.endOfTrack == true || narratesOutro(current)) {
      audioEngine.setUpNext(null) {}
      return
    }
    val next = PlaybackOrder.resolveNext(
      current = current, queue = _queue.value, list = contextList(),
      shuffle = _shuffle.value, repeat = _repeatMode.value, auto = true
    )
    val track = next.track
    if (track == null || track.filePath == null || (!next.loopSameTrack && narratesIntro(track))) {
      audioEngine.setUpNext(null) {}
      return
    }
    audioEngine.setUpNext(track.filePath) { onGaplessAdvance(next, track) }
  }

  /** The player moved on to the queued track by itself: catch the rest of the state up. */
  private fun onGaplessAdvance(next: PlaybackOrder.Next, track: Track) {
    failuresInARow = 0
    _queue.value = next.remainingQueue
    if (!next.loopSameTrack) {
      musicRepository.currentTrack.value?.let { prev ->
        val updated = PlaybackOrder.pushHistory(playHistory, prev, track)
        playHistory.clear()
        playHistory.addAll(updated)
      }
      musicRepository.selectTrack(track)
      _activeCuriosity.value = curiosityRepository.getCuriosityForTrack(track)
    }
    audioEngine.updateCallbacks(
      onCompletion = { onTrackPlaybackCompleted(track) },
      onError = { network -> onTrackFailed(track, network) }
    )
    refreshUpNext()
    save()
  }

  // ---- transport ----

  fun togglePlayPause() {
    val current = musicRepository.currentTrack.value ?: return
    val resume = resumeAfterNarration
    when {
      speech.isSpeaking.value -> pauseNarration()
      // Paused in the middle of a narration: carry on with what followed it.
      resume != null -> {
        resumeAfterNarration = null
        _starting.value = true
        resume()
      }
      audioEngine.hasLoadedTrack -> audioEngine.togglePlayPause()
      // Nothing loaded (the track ended, or failed): start it again.
      else -> playInternal(current, recordHistory = false)
    }
  }

  fun play() {
    if (!isPlaying.value && !speech.isSpeaking.value) togglePlayPause()
  }

  /** Pauses whatever plays; does nothing when already paused (unlike a toggle). */
  fun pause() {
    _starting.value = false
    if (speech.isSpeaking.value) {
      pauseNarration()
      return
    }
    if (!isPlaying.value) return
    audioEngine.pause()
  }

  /** Moves the playing track to [ms]. */
  fun seekTo(ms: Long) {
    audioEngine.seekTo(ms)
  }

  fun playNextTrack() = advance(auto = false)

  /** [auto] is true when the track ended by itself; false for the Next button. */
  private fun advance(auto: Boolean) {
    speech.stopSpeech()
    afterNarration = null
    resumeAfterNarration = null
    val next = PlaybackOrder.resolveNext(
      current = musicRepository.currentTrack.value,
      queue = _queue.value,
      list = contextList(),
      shuffle = _shuffle.value,
      repeat = _repeatMode.value,
      auto = auto
    )
    _queue.value = next.remainingQueue
    val track = next.track ?: run { _starting.value = false; save(); return }
    if (next.loopSameTrack) startTrackAudio(track) else playInternal(track, recordHistory = true)
  }

  /**
   * Like Apple Music: a few seconds in, "previous" restarts the track. [force] skips that check (the
   * media session decided already).
   */
  fun playPreviousTrack(force: Boolean = false) {
    speech.stopSpeech()
    afterNarration = null
    resumeAfterNarration = null
    if (!force && positionMs.value > RESTART_THRESHOLD_MS) {
      seekTo(0L)
      return
    }
    val prev = if (playHistory.isNotEmpty()) {
      playHistory.removeAt(playHistory.lastIndex)
    } else {
      val list = contextList()
      val i = list.indexOfFirst { it.id == musicRepository.currentTrack.value?.id }
      list.getOrNull(if (i > 0) i - 1 else list.lastIndex)
    }
    if (prev != null) playInternal(prev, recordHistory = false)
  }

  /** True when Next has somewhere to go, for the media session's buttons. */
  val hasNext: Boolean
    get() = _queue.value.isNotEmpty() || contextList().size > 1 || _repeatMode.value != RepeatMode.OFF

  val hasPrevious: Boolean get() = playHistory.isNotEmpty() || contextList().size > 1

  fun skipCuriositySpeech() {
    afterNarration = null
    speech.stopSpeech(continueAfter = true)
  }

  // ---- queue, shuffle, repeat ----

  fun toggleShuffle() = setShuffle(!_shuffle.value)

  fun setShuffle(on: Boolean) {
    _shuffle.value = on
    preferences.saveShuffle(on)
    refreshUpNext()
  }

  fun cycleRepeatMode() = setRepeatMode(PlaybackOrder.nextRepeatMode(_repeatMode.value))

  fun setRepeatMode(mode: RepeatMode) {
    _repeatMode.value = mode
    preferences.saveRepeatMode(mode)
    refreshUpNext()
  }

  /** Turns shuffle on and starts a random track of [list]. */
  fun shuffleFrom(list: List<Track>) {
    if (list.isEmpty()) return
    if (!_shuffle.value) setShuffle(true)
    playFromList(list.random(), list)
  }

  fun playNext(track: Track) = updateQueue(PlaybackOrder.enqueueNext(_queue.value, track))

  fun addToQueue(track: Track) = updateQueue(PlaybackOrder.enqueueLast(_queue.value, track))

  fun removeFromQueue(index: Int) = updateQueue(PlaybackOrder.removeAt(_queue.value, index))

  fun clearQueue() = updateQueue(emptyList())

  /** Moves the queue item at [from] to [to]. */
  fun moveInQueue(from: Int, to: Int) {
    val list = _queue.value.toMutableList()
    if (from !in list.indices || to !in list.indices) return
    list.add(to, list.removeAt(from))
    updateQueue(list)
  }

  private fun updateQueue(list: List<Track>) {
    _queue.value = list
    refreshUpNext()
    save()
  }

  fun playFromQueue(index: Int) {
    val jump = PlaybackOrder.jumpTo(_queue.value, index) ?: return
    _queue.value = jump.remainingQueue
    jump.track?.let { playInternal(it, recordHistory = true) }
  }

  // ---- curiosities ----

  fun setCuriosityConfig(config: CuriosityConfig) {
    _curiosityConfig.value = config
    preferences.saveCuriosityConfig(config)
    refreshUpNext()
  }

  /** The assistant looked up a curiosity for the current track. */
  fun setActiveCuriosity(curiosity: Curiosity?) {
    _activeCuriosity.value = curiosity
  }

  // ---- sleep timer ----

  private var sleepJob: Job? = null

  /** Pauses (with a short fade) after [minutes]. */
  fun setSleepTimer(minutes: Int) {
    sleepJob?.cancel()
    val endsAt = System.currentTimeMillis() + minutes * 60_000L
    _sleepTimer.value = SleepTimer(endsAtMs = endsAt)
    refreshUpNext()
    sleepJob = scope.launch {
      delay(endsAt - System.currentTimeMillis())
      speech.stopSpeech()
      audioEngine.fadeOutAndPause(SLEEP_FADE_MS)
      _sleepTimer.value = null
      _notices.tryEmit(PlaybackNotice("Timer de dormir: música pausada."))
    }
  }

  /** Stops when the current track ends. */
  fun setSleepAtEndOfTrack() {
    sleepJob?.cancel()
    _sleepTimer.value = SleepTimer(endOfTrack = true)
    refreshUpNext()
  }

  fun cancelSleepTimer() {
    sleepJob?.cancel()
    _sleepTimer.value = null
    refreshUpNext()
  }

  // ---- saved queue: what was playing comes back after the app is closed ----

  /**
   * Saves the track, the queue and the list it came from. The list can be the whole device library,
   * so this runs only when one of them changes; the position is saved on its own ([savePosition]).
   */
  private fun save() {
    val current = musicRepository.currentTrack.value
    if (current == null) {
      store.edit().clear().apply()
      return
    }
    val o = JSONObject()
      .put("current", TrackJson.toJson(current))
      .put("queue", TrackJson.listToJson(_queue.value))
    contextTracks?.let { o.put("context", TrackJson.listToJson(it)) }
    store.edit()
      .putString(KEY_STATE, o.toString())
      .putLong(KEY_POSITION, positionMs.value)
      .apply()
  }

  private fun savePosition() {
    val current = musicRepository.currentTrack.value ?: return
    store.edit().putLong(KEY_POSITION, positionMs.value).apply()
  }

  /** Puts back the track, its position, the queue and the list it came from, paused. */
  private fun restore() {
    val raw = store.getString(KEY_STATE, null) ?: return
    try {
      val o = JSONObject(raw)
      val track = TrackJson.fromJson(o.getJSONObject("current")) ?: return
      // The demo tones are generated at first launch and are not worth a restore.
      if (track.isDemo) return
      _queue.value = TrackJson.listFromJson(o.optJSONArray("queue"))
      contextTracks = o.optJSONArray("context")?.let { TrackJson.listFromJson(it) }
      musicRepository.selectTrack(track)
      _activeCuriosity.value = curiosityRepository.getCuriosityForTrack(track)
      val path = track.filePath
      if (path != null) {
        audioEngine.playTrack(
          path,
          onCompletion = { onTrackPlaybackCompleted(track) },
          // A file that is gone since: forget it quietly instead of skipping through the list.
          onError = { audioEngine.stopPlayback() },
          // Older saves kept the position inside the JSON.
          startPositionMs = store.getLong(KEY_POSITION, o.optLong("pos", 0L)),
          autoStart = false
        )
        refreshUpNext()
      }
    } catch (_: Exception) {
      store.edit().clear().apply()
    }
  }

  /**
   * Stops everything (the app was swiped away while paused). The track and its position stay saved,
   * so it comes back, paused, the next time the app opens.
   */
  fun stopAll() {
    savePosition()
    speech.stopSpeech()
    afterNarration = null
    resumeAfterNarration = null
    _starting.value = false
    audioEngine.stopPlayback()
  }

  private companion object {
    const val RESTART_THRESHOLD_MS = 3000L
    const val SAVE_EVERY_MS = 10_000L
    const val SLEEP_FADE_MS = 6000L
    const val KEY_STATE = "state"
    const val KEY_POSITION = "pos"
  }
}
