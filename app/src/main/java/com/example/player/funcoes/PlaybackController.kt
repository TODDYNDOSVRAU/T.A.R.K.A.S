package com.example.data.playback

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.library.LibraryMigration
import com.example.data.diagnostics.Diagnostics
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
import kotlinx.coroutines.withContext
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
/** A message about playback. It carries string resources, so whoever shows it writes it in the current language. */
data class PlaybackNotice(
  @StringRes val textRes: Int,
  val textArgs: List<Any> = emptyList(),
  @StringRes val actionLabelRes: Int? = null,
  val action: (() -> Unit)? = null
)

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
  private val speech: CuriositySpeechManager,
  private val databaseLazy: Lazy<AppDatabase>
) {
  constructor(
    context: Context,
    musicRepository: MusicRepository,
    curiosityRepository: CuriosityRepository,
    audioEngine: AudioEngineManager,
    preferences: PreferencesRepository,
    speech: CuriositySpeechManager,
    database: AppDatabase
  ) : this(context, musicRepository, curiosityRepository, audioEngine, preferences, speech, lazyOf(database))

  private val database: AppDatabase get() = databaseLazy.value
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val store by lazy { PlaybackStateStore(database.playback()) }

  val currentTrack: StateFlow<Track?> get() = musicRepository.currentTrack

  // Playback order: manual queue ("Up Next") first, then the playback context.
  private val _queue = MutableStateFlow<List<Track>>(emptyList())
  val queue: StateFlow<List<Track>> = _queue.asStateFlow()

  private val _crossfadeSeconds = MutableStateFlow(preferences.getCrossfadeSeconds())
  /** Crossfade between tracks in seconds; 0 is off. */
  val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

  // Play and skip counters (declared before init, which restores the last track).
  private val stats by lazy { PlayStatsStore(database.playStats()) }
  /** The track whose end is not recorded yet: set when its audio starts, cleared when it ends. */
  private var unrecorded: Track? = null
  private val _playCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
  /** How many times each track counted as played, for "Mais tocadas". Loaded on first use. */
  val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()
  private var playCountsLoaded = false

  private val _upcoming = MutableStateFlow<List<Track>>(emptyList())
  /** What the list plays after the queue runs out (a few tracks), for the queue screen. */
  val upcoming: StateFlow<List<Track>> = _upcoming.asStateFlow()

  private val _coverNeighbors = MutableStateFlow(CoverNeighbors.Result())
  /** The tracks Previous and Next go to, for the covers beside the current one in the full player. */
  val coverNeighbors: StateFlow<CoverNeighbors.Result> = _coverNeighbors.asStateFlow()

  private val _shuffle = MutableStateFlow(preferences.getShuffle())
  val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

  private val _repeatMode = MutableStateFlow(preferences.getRepeatMode())
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  // null = the whole library; otherwise the playlist/favorites/results the user started from.
  private var contextTracks: List<Track>? = null
  private val playHistory = mutableListOf<Track>()
  /** With shuffle on: the order of the current pass through the list (track ids). */
  private var shuffleOrder = emptyList<String>()

  private val _curiosityConfig = MutableStateFlow(preferences.getCuriosityConfig())
  val curiosityConfig: StateFlow<CuriosityConfig> = _curiosityConfig.asStateFlow()

  private val _activeCuriosity = MutableStateFlow<Curiosity?>(null)
  val activeCuriosity: StateFlow<Curiosity?> = _activeCuriosity.asStateFlow()

  private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
  val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

  private val _abLoop = MutableStateFlow(AbLoop())
  /** Active or pending A-B segment loop for the playing track. */
  val abLoop: StateFlow<AbLoop> = _abLoop.asStateFlow()

  fun toggleAbLoop() {
    if (musicRepository.currentTrack.value == null) return
    _abLoop.value = _abLoop.value.nextStep(positionMs.value)
  }

  fun clearAbLoop() {
    if (_abLoop.value.startMs != null || _abLoop.value.endMs != null) _abLoop.value = AbLoop()
  }

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

  private val _problem = MutableStateFlow<PlaybackProblem?>(null)
  /** Why the current track does not play (shown on the player until it plays again); null when nothing is wrong. */
  val problem: StateFlow<PlaybackProblem?> = _problem.asStateFlow()
  private var retryProblem: (() -> Unit)? = null

  /** Tries again the track that failed; does nothing when nothing failed. */
  fun retryFailed() {
    val retry = retryProblem ?: return
    retry()
  }

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
    // The play counters of the old preferences file are copied once, before anything is counted.
    scope.launch {
      try {
        stats.migrateFrom(
          context.getSharedPreferences(PlayStatsStore.FILE, Context.MODE_PRIVATE),
          context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE),
          database
        )
      } catch (e: Exception) {
        Diagnostics.record("playback.migrate", "Play statistics migration failed", e)
      }
    }
    // Playing from the whole library, its neighbours change when the library is (re)loaded.
    scope.launch {
      musicRepository.tracks.drop(1).collect { if (contextTracks == null) refreshNeighbors() }
    }
    // Crossfade and gapless depend on whether Tarkas Spatial is active on the current track.
    scope.launch {
      com.example.data.audio.spatial.SpatialControl.status.drop(1).collect {
        if (musicRepository.currentTrack.value != null) refreshUpNext()
      }
    }
    scope.launch {
      // The first value is the restored, paused state; saving it could store position 0 before the
      // restored track has even been prepared.
      isPlaying.drop(1).collect { playing ->
        if (playing) {
          failuresInARow = 0
          _problem.value = null
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
    // A-B Loop: wrap back to point A whenever playback crosses point B.
    scope.launch {
      positionMs.collect { pos ->
        val loop = _abLoop.value
        if (loop.shouldWrap(pos) && isPlaying.value) {
          audioEngine.seekTo(loop.startMs!!)
        }
      }
    }
  }

  // ---- starting tracks ----

  /** Plays a track from the whole library. */
  fun playTrack(track: Track, keepQueue: Boolean = true) {
    if (!keepQueue) _queue.value = emptyList()
    contextTracks = null
    shuffleOrder = emptyList()
    playInternal(track, recordHistory = true)
  }

  /** Plays a track and keeps going through [list] (a playlist, favorites, search results...). */
  fun playFromList(track: Track, list: List<Track>, keepQueue: Boolean = true) {
    if (!keepQueue) _queue.value = emptyList()
    contextTracks = list
    // A track picked by hand starts a new shuffled pass from itself.
    shuffleOrder = emptyList()
    playInternal(track, recordHistory = true)
  }

  // ---- play and skip counters ----

  fun loadPlayCounts() {
    if (playCountsLoaded) return
    playCountsLoaded = true
    scope.launch { _playCounts.value = stats.allPlays() }
  }

  /** Records how the current track ended (see [PlayRules]); nothing when it was recorded already. */
  private fun recordEnd(reason: EndReason) {
    val t = unrecorded ?: return
    unrecorded = null
    if (t.isDemo) return
    val duration = t.durationMs.takeIf { it > 0 } ?: durationMs.value
    val played = if (reason == EndReason.NATURAL) duration else positionMs.value
    scope.launch {
      val after = stats.record(t.id, reason, played, duration)
      if (playCountsLoaded && _playCounts.value[t.id] != after.plays) _playCounts.value = _playCounts.value + (t.id to after.plays)
    }
  }

  /**
   * Plays [track] as the current one, with its start-of-track curiosity when there is one. [reason] is
   * how the track playing until now ends.
   */
  private fun playInternal(track: Track, recordHistory: Boolean, reason: EndReason = EndReason.NEW_SELECTION) {
    clearAbLoop()
    recordEnd(reason)
    if (recordHistory) {
      musicRepository.currentTrack.value?.let { prev ->
        val updated = PlaybackOrder.pushHistory(playHistory, prev, track)
        playHistory.clear()
        playHistory.addAll(updated)
      }
    }
    musicRepository.selectTrack(track)
    refreshNeighbors()
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
    _problem.value = null
    retryProblem = null
    // Repeat-one restarts the same track: its last round ended by itself.
    if (unrecorded?.id == track.id && startPositionMs == 0L) recordEnd(EndReason.NATURAL)
    unrecorded = track
    if (path == null) {
      onTrackFailed(track, network = false)
    } else {
      // The automatic volume mode keeps an album's own levels while it plays in order.
      audioEngine.markAlbumInOrder(
        track.id,
        AlbumOrder.inAlbumSequence(playHistory.lastOrNull(), track, resolveNext(auto = true).track)
      )
      audioEngine.playTrack(
        path,
        onCompletion = { onTrackPlaybackCompleted(track) },
        onError = { network -> onTrackFailed(track, network) },
        startPositionMs = startPositionMs,
        loudnessKey = track.id
      )
      refreshUpNext()
    }
  }

  /**
   * Says what went wrong. A bad file moves on to the next track, unless several failed in a row; a lost
   * connection stops on this track, and the retry goes on from where it stopped.
   */
  private fun onTrackFailed(track: Track, network: Boolean) {
    failuresInARow++
    _starting.value = false
    val canAdvance = contextList().size > 1 || _queue.value.isNotEmpty()
    when (val outcome = PlaybackFailure.decide(track.id, network, failuresInARow, canAdvance)) {
      PlaybackFailure.Outcome.Skip -> {
        _notices.tryEmit(PlaybackNotice(R.string.player_notice_skipped, listOf(track.title)))
        advance(auto = false)
      }
      is PlaybackFailure.Outcome.Stop -> {
        // A lost connection goes on from where it stopped; a bad file starts over.
        val at = if (network) audioEngine.lastErrorPositionMs else 0L
        val retry = {
          failuresInARow = 0
          if (network) startTrackAudio(track, startPositionMs = at) else playInternal(track, recordHistory = false)
        }
        retryProblem = retry
        _problem.value = outcome.problem
        _notices.tryEmit(
          PlaybackNotice(
            if (network) R.string.player_notice_offline else R.string.player_notice_failed,
            listOf(track.title),
            actionLabelRes = R.string.player_notice_retry,
            action = retry
          )
        )
      }
    }
  }

  private fun onTrackPlaybackCompleted(completedTrack: Track) {
    if (_sleepTimer.value?.endOfTrack == true) {
      // The sleep timer was set to the end of this track: stop here.
      recordEnd(EndReason.NATURAL)
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

  private fun refreshUpcoming() {
    val current = musicRepository.currentTrack.value
    val list = contextList()
    if (_shuffle.value) shuffleOrder = PlaybackOrder.shuffleOrderFor(list, current, shuffleOrder)
    _upcoming.value = PlaybackOrder.upcoming(current, list, _shuffle.value, shuffleOrder, _repeatMode.value, UPCOMING_SHOWN)
    refreshNeighbors()
  }

  /**
   * Recomputes the covers beside the current one. Called as soon as the current track, the history,
   * the queue or the order changes (not only once the audio starts, which waits for an intro narration).
   */
  private fun refreshNeighbors() {
    val current = musicRepository.currentTrack.value
    val list = contextList()
    if (_shuffle.value) shuffleOrder = PlaybackOrder.shuffleOrderFor(list, current, shuffleOrder)
    _coverNeighbors.value = CoverNeighbors.of(
      current = current, queue = _queue.value, list = list, history = playHistory.toList(),
      shuffle = _shuffle.value, shuffleOrder = shuffleOrder, repeat = _repeatMode.value
    )
  }

  /** [PlaybackOrder.resolveNext] with the shuffled order kept here, so a pass plays every track once. */
  private fun resolveNext(auto: Boolean): PlaybackOrder.Next {
    val current = musicRepository.currentTrack.value
    val list = contextList()
    if (_shuffle.value) shuffleOrder = PlaybackOrder.shuffleOrderFor(list, current, shuffleOrder)
    return PlaybackOrder.resolveNext(
      current = current, queue = _queue.value, list = list,
      shuffle = _shuffle.value, repeat = _repeatMode.value, auto = auto, shuffleOrder = shuffleOrder
    )
  }

  // ---- gapless ----

  /**
   * Queues the track that will follow in the player itself, so it starts with no gap. Not when a
   * narration, a web track or the end-of-track sleep timer sits between the two.
   */
  private fun refreshUpNext() {
    refreshUpcoming()
    // Armed again below when the coming transition is a crossfade.
    audioEngine.armCrossfade(null)
    val current = musicRepository.currentTrack.value
    if (current == null || _sleepTimer.value?.endOfTrack == true || narratesOutro(current)) {
      audioEngine.setUpNext(null) {}
      return
    }
    val next = resolveNext(auto = true)
    val track = next.track
    if (track == null || track.filePath == null || (!next.loopSameTrack && narratesIntro(track))) {
      audioEngine.setUpNext(null) {}
      return
    }
    val crossfade = CrossfadePlanner.eligible(
      current = current,
      next = track,
      seconds = _crossfadeSeconds.value,
      repeatsSameTrack = next.loopSameTrack,
      spatialOn = audioEngine.spatialActive,
      narrationBetween = false, // checked above: an outro or intro returns before this
      sleepAtEndOfTrack = false
    )
    if (crossfade) {
      // The two tracks overlap: no gapless queue, the engine calls back when the overlap begins.
      audioEngine.setUpNext(null) {}
      audioEngine.armCrossfade(_crossfadeSeconds.value * 1000L) { onCrossfadeStart() }
      return
    }
    audioEngine.markAlbumInOrder(track.id, AlbumOrder.follows(current, track))
    audioEngine.setUpNext(track.filePath, loudnessKey = track.id) { onGaplessAdvance(next, track) }
  }

  // ---- crossfade ----

  fun setCrossfadeSeconds(seconds: Int) {
    val s = if (seconds <= 0) 0 else seconds.coerceIn(CrossfadePlanner.CHOICES)
    _crossfadeSeconds.value = s
    preferences.saveCrossfadeSeconds(s)
    // Takes effect for the coming transition.
    if (s == 0) audioEngine.armCrossfade(null)
    refreshUpNext()
  }

  /** The overlap begins: the outgoing track goes on in the engine's tail while the next one starts. */
  private fun onCrossfadeStart() {
    audioEngine.startCrossfadeOut()
    advance(auto = true)
  }

  /** The player moved on to the queued track by itself: catch the rest of the state up. */
  private fun onGaplessAdvance(next: PlaybackOrder.Next, track: Track) {
    clearAbLoop()
    recordEnd(EndReason.NATURAL)
    unrecorded = track
    failuresInARow = 0
    _problem.value = null
    _queue.value = next.remainingQueue
    next.shuffleOrder?.let { shuffleOrder = it }
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
    // Recorded here too, since the end of the list plays nothing after it.
    recordEnd(if (auto) EndReason.NATURAL else EndReason.SKIP_FORWARD)
    speech.stopSpeech()
    afterNarration = null
    resumeAfterNarration = null
    val next = resolveNext(auto)
    _queue.value = next.remainingQueue
    next.shuffleOrder?.let { shuffleOrder = it }
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
    // The same rule the player's cover carousel shows on its left (CoverNeighbors).
    val prev = PlaybackOrder.previous(playHistory, contextList(), musicRepository.currentTrack.value)
    if (playHistory.isNotEmpty()) playHistory.removeAt(playHistory.lastIndex)
    if (prev != null) playInternal(prev, recordHistory = false, reason = EndReason.SKIP_BACKWARD)
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
    // On: a new pass from the current track. Off: the list's own order goes on from here.
    if (on != _shuffle.value) shuffleOrder = emptyList()
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

  /** Removes the queue item at [index] and returns it, so it can be put back. */
  fun removeFromQueue(index: Int): Track? {
    val removed = _queue.value.getOrNull(index) ?: return null
    updateQueue(PlaybackOrder.removeAt(_queue.value, index))
    return removed
  }

  /** Plays [track] from the list that plays now, keeping the list and the queue. */
  fun playFromCurrentList(track: Track) = playInternal(track, recordHistory = true)

  /** Puts [track] back at [index] (an undone removal). */
  fun restoreToQueue(index: Int, track: Track) {
    val list = _queue.value.toMutableList()
    list.add(index.coerceIn(0, list.size), track)
    updateQueue(list)
  }

  fun clearQueue() = updateQueue(emptyList())

  /** Whether playing from [list] (null: the whole library) would keep the list that plays now. */
  fun isCurrentContext(list: List<Track>?): Boolean {
    val now = contextTracks
    return if (list == null) now == null else now != null && now.map { it.id } == list.map { it.id }
  }

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
    val endsAt = SleepTimerPlan.endsAt(System.currentTimeMillis(), minutes)
    _sleepTimer.value = SleepTimer(endsAtMs = endsAt)
    refreshUpNext()
    sleepJob = scope.launch {
      delay(SleepTimerPlan.remainingMs(endsAt, System.currentTimeMillis()))
      speech.stopSpeech()
      audioEngine.fadeOutAndPause(SLEEP_FADE_MS)
      _sleepTimer.value = null
      _notices.tryEmit(PlaybackNotice(R.string.player_notice_sleep_done))
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
      store.clear()
      return
    }
    val o = JSONObject()
      .put("current", TrackJson.toJson(current))
      .put("queue", TrackJson.listToJson(_queue.value))
    contextTracks?.let { o.put("context", TrackJson.listToJson(it)) }
    store.save(o.toString(), positionMs.value, current.id)
  }

  /** The position goes with the track it belongs to, so a lost write never moves another track. */
  private fun savePosition() {
    val current = musicRepository.currentTrack.value ?: return
    store.savePosition(positionMs.value, current.id)
  }

  /** Puts back the track, its position, the queue and the list it came from, paused. */
  private fun restore() {
    // The old preferences are copied first, then the row is read; both wait their turn behind any write,
    // and the player stays as it is (paused, nothing selected) until the answer comes.
    scope.launch {
      val row = try {
        store.migrateFrom(
          context.getSharedPreferences(PlaybackStateStore.OLD_FILE, Context.MODE_PRIVATE),
          context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE)
        )
        remapPickedIds()
        store.load()
      } catch (e: Exception) {
        Diagnostics.record("playback.restore", "Saved playback could not be read from the database", e)
        null
      }
      // Something was already chosen while the row was being read: that choice wins.
      if (musicRepository.currentTrack.value != null) return@launch
      applyOutcome(PlaybackRestore.read(row?.stateJson, row?.positionTrackId, row?.positionMs?.takeIf { it >= 0 }))
    }
  }

  /** Once the library has moved to its new ids, what the player stored under the old ones follows. */
  private suspend fun remapPickedIds() {
    val flags = context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE)
    try {
      LibraryMigration.ensure(context, database)
      val map = LibraryMigration.idMap(database)
      stats.migrateFrom(context.getSharedPreferences(PlayStatsStore.FILE, Context.MODE_PRIVATE), flags, database)
      store.remapIds(map, flags)
      stats.remapIds(map, flags, database)
    } catch (e: Exception) {
      Diagnostics.record("playback.migrate", "Old picked-file ids could not be rewritten; will try again", e)
    }
  }

  private fun applyOutcome(outcome: RestoreOutcome) {
    when (outcome) {
      RestoreOutcome.Nothing -> return
      is RestoreOutcome.Unreadable -> {
        // Only what cannot be read is cleared; the message carries no title or path.
        Diagnostics.record("playback.restore", "Saved playback cleared: ${outcome.reason}")
        store.clear()
        return
      }
      is RestoreOutcome.Restored -> {
        val state = outcome.state
        if (state.problems.isNotEmpty()) {
          Diagnostics.record("playback.restore", "Saved playback partly restored: ${state.problems.joinToString("; ")}")
        }
        try {
          applyRestored(state)
        } catch (e: Exception) {
          // The state was read but could not be put back; it stays saved for the next start.
          Diagnostics.record("playback.restore", "Saved playback could not be applied", e)
        }
      }
    }
  }

  private fun applyRestored(state: SavedPlayback) {
    val track = state.track
    _queue.value = state.queue
    contextTracks = state.context
    musicRepository.selectTrack(track)
    refreshNeighbors()
    _activeCuriosity.value = curiosityRepository.getCuriosityForTrack(track)
    val path = track.filePath
    if (path != null) {
      // Resumed later, it counts like any other track.
      unrecorded = track
      audioEngine.playTrack(
        path,
        onCompletion = { onTrackPlaybackCompleted(track) },
        // A file that is gone since: forget it quietly instead of skipping through the list.
        onError = { audioEngine.stopPlayback() },
        startPositionMs = state.positionMs,
        autoStart = false,
        loudnessKey = track.id
      )
      refreshUpNext()
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
    /** How many of the list's next tracks the queue screen shows after the queue. */
    const val UPCOMING_SHOWN = 20
  }
}
