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
import com.example.data.web.WebPlayerHost
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
 * narrations between tracks, local and web tracks, gapless moves, the sleep timer and the saved
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

  // They follow the local engine, or the hidden web player for a web track.
  val isPlaying: StateFlow<Boolean> = combine(
    musicRepository.currentTrack, audioEngine.isPlaying, WebPlayerHost.state
  ) { track, local, web -> if (track?.isWeb == true) web.playing else local }
    .stateIn(scope, SharingStarted.Eagerly, false)

  val positionMs: StateFlow<Long> = combine(
    musicRepository.currentTrack, audioEngine.currentPositionMs, WebPlayerHost.state
  ) { track, local, web -> if (track?.isWeb == true) web.positionMs else local }
    .stateIn(scope, SharingStarted.Eagerly, 0L)

  val durationMs: StateFlow<Long> = combine(
    musicRepository.currentTrack, audioEngine.durationMs, WebPlayerHost.state
  ) { track, local, web ->
    if (track?.isWeb == true) web.durationMs.takeIf { it > 0 } ?: track.durationMs
    else local.takeIf { it > 0 } ?: track?.durationMs ?: 0L
  }.stateIn(scope, SharingStarted.Eagerly, 0L)

  /** Tracks that failed one after another; stops the skipping when nothing in the list plays. */
  private var failuresInARow = 0
  private var webWatchdog: Job? = null

  init {
    restore()
    scope.launch {
      isPlaying.collect { playing ->
        if (playing) failuresInARow = 0
        else save()
      }
    }
    // The position is saved every few seconds while playing, so a crash or a kill loses little.
    scope.launch {
      while (isActive) {
        delay(SAVE_EVERY_MS)
        if (isPlaying.value) save()
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

    if (narratesIntro(track)) {
      // The intro curiosity plays before the music.
      audioEngine.stopPlayback()
      WebPlayerHost.stop()
      speech.speakCuriosity(track, cur!!, isIntro = true, config = _curiosityConfig.value) {
        startTrackAudio(track)
      }
    } else {
      startTrackAudio(track)
    }
    save()
  }

  private fun startTrackAudio(track: Track, startPositionMs: Long = 0L) {
    val path = track.filePath
    webWatchdog?.cancel()
    if (track.isWeb) {
      audioEngine.stopPlayback()
      val done = { onTrackPlaybackCompleted(track) }
      if (track.webSource == "spotify") WebPlayerHost.playSpotify(context, track.webId.orEmpty(), done)
      else WebPlayerHost.playYouTube(context, track.webId.orEmpty(), done)
      // A page that never starts (no connection, a blocked video) would leave the player silent.
      webWatchdog = scope.launch {
        delay(WEB_START_TIMEOUT_MS)
        // A song the user paused right away is not a failure.
        if (musicRepository.currentTrack.value?.id == track.id && !isPlaying.value && positionMs.value == 0L &&
          WebPlayerHost.wantsToPlay
        ) {
          onTrackFailed(track, network = false)
        }
      }
    } else if (path == null) {
      onTrackFailed(track, network = false)
    } else {
      WebPlayerHost.stop()
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
      audioEngine.stopPlayback()
      WebPlayerHost.stop()
      save()
      return
    }
    val cur = _activeCuriosity.value ?: curiosityRepository.getCuriosityForTrack(completedTrack)
    if (narratesOutro(completedTrack, cur)) {
      speech.speakCuriosity(completedTrack, cur!!, isIntro = false, config = _curiosityConfig.value) {
        advance(auto = true)
      }
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
    if (current == null || current.isWeb || _sleepTimer.value?.endOfTrack == true || narratesOutro(current)) {
      audioEngine.setUpNext(null) {}
      return
    }
    val next = PlaybackOrder.resolveNext(
      current = current, queue = _queue.value, list = contextList(),
      shuffle = _shuffle.value, repeat = _repeatMode.value, auto = true
    )
    val track = next.track
    if (track == null || track.isWeb || track.filePath == null || (!next.loopSameTrack && narratesIntro(track))) {
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
    when {
      speech.isSpeaking.value -> speech.stopSpeech(continueAfter = true)
      current.isWeb && WebPlayerHost.state.value.active -> WebPlayerHost.togglePlayPause()
      !current.isWeb && audioEngine.hasLoadedTrack -> audioEngine.togglePlayPause()
      // Nothing loaded (the track ended, or failed): start it again.
      else -> playInternal(current, recordHistory = false)
    }
  }

  fun play() {
    if (!isPlaying.value && !speech.isSpeaking.value) togglePlayPause()
  }

  /** Pauses whatever plays; does nothing when already paused (unlike a toggle). */
  fun pause() {
    if (!isPlaying.value) return
    if (musicRepository.currentTrack.value?.isWeb == true) WebPlayerHost.pause() else audioEngine.pause()
  }

  /** Moves the playing track, local or web, to [ms]. */
  fun seekTo(ms: Long) {
    if (musicRepository.currentTrack.value?.isWeb == true) WebPlayerHost.seekTo(ms) else audioEngine.seekTo(ms)
  }

  fun playNextTrack() = advance(auto = false)

  /** [auto] is true when the track ended by itself; false for the Next button. */
  private fun advance(auto: Boolean) {
    speech.stopSpeech()
    val next = PlaybackOrder.resolveNext(
      current = musicRepository.currentTrack.value,
      queue = _queue.value,
      list = contextList(),
      shuffle = _shuffle.value,
      repeat = _repeatMode.value,
      auto = auto
    )
    _queue.value = next.remainingQueue
    val track = next.track ?: run { save(); return }
    if (next.loopSameTrack) startTrackAudio(track) else playInternal(track, recordHistory = true)
  }

  /**
   * Like Apple Music: a few seconds in, "previous" restarts the track. [force] skips that check (the
   * media session decided already).
   */
  fun playPreviousTrack(force: Boolean = false) {
    speech.stopSpeech()
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

  fun skipCuriositySpeech() = speech.stopSpeech(continueAfter = true)

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
      if (musicRepository.currentTrack.value?.isWeb == true) WebPlayerHost.pause()
      else audioEngine.fadeOutAndPause(SLEEP_FADE_MS)
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

  private fun save() {
    val current = musicRepository.currentTrack.value
    if (current == null) {
      store.edit().clear().apply()
      return
    }
    val o = JSONObject()
      .put("current", TrackJson.toJson(current))
      .put("pos", if (current.isWeb) 0L else positionMs.value)
      .put("queue", TrackJson.listToJson(_queue.value))
    contextTracks?.let { o.put("context", TrackJson.listToJson(it)) }
    store.edit().putString(KEY_STATE, o.toString()).apply()
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
      if (!track.isWeb && path != null) {
        audioEngine.playTrack(
          path,
          onCompletion = { onTrackPlaybackCompleted(track) },
          // A file that is gone since: forget it quietly instead of skipping through the list.
          onError = { audioEngine.stopPlayback() },
          startPositionMs = o.optLong("pos", 0L),
          autoStart = false
        )
        refreshUpNext()
      }
    } catch (_: Exception) {
      store.edit().clear().apply()
    }
  }

  /** Stops everything and forgets the current track (the app was swiped away while paused). */
  fun stopAll() {
    speech.stopSpeech()
    audioEngine.stopPlayback()
    WebPlayerHost.stop()
    save()
  }

  private companion object {
    const val WEB_START_TIMEOUT_MS = 25_000L
    const val RESTART_THRESHOLD_MS = 3000L
    const val SAVE_EVERY_MS = 10_000L
    const val SLEEP_FADE_MS = 6000L
    const val KEY_STATE = "state"
  }
}
