package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.assistant.AssistantEngine
import com.example.data.assistant.PlayerCommand
import com.example.data.audio.AudioEngineManager
import com.example.data.model.AssistantMessage
import com.example.data.model.AudioProfile
import com.example.data.model.Curiosity
import com.example.data.model.LocalReminder
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.repository.LibraryRepository
import com.example.data.notification.MusicNotificationManager
import com.example.data.playback.PlaybackOrder
import com.example.data.playback.PlaybackService
import com.example.data.notification.NotificationActionReceiver
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.systemaudio.ActiveAppsReader
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.SystemAudioCaptureManager
import com.example.data.systemaudio.SystemAudioStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class TarkasNavTab(val label: String) {
  HOME("Início"),
  MUSIC("Música"),
  LIBRARY("Biblioteca"),
  AUDIO("Áudio"),
  ASSISTANT("Assistente"),
  SETTINGS("Ajustes")
}

@OptIn(FlowPreview::class)
class TarkasViewModel(application: Application) : AndroidViewModel(application) {

  private companion object {
    const val IDLE_BEFORE_LEAVING_FOREGROUND_MS = 3000L
  }

  private val context = application.applicationContext
  val musicRepository = MusicRepository(context)
  val curiosityRepository = CuriosityRepository()
  val audioEngineManager = AudioEngineManager(context)
  val preferencesRepository = PreferencesRepository(context)
  val systemAudioCaptureManager = SystemAudioCaptureManager(context)
  val curiositySpeechManager = com.example.data.assistant.CuriositySpeechManager(context)

  val libraryRepository = LibraryRepository(context)
  val favorites: StateFlow<List<String>> = libraryRepository.favorites
  val playlists: StateFlow<List<Playlist>> = libraryRepository.playlists
  val savedTracks: StateFlow<Map<String, Track>> = libraryRepository.savedTracks

  // Playback order: manual queue ("Up Next") first, then the playback context.
  private val _queue = MutableStateFlow<List<Track>>(emptyList())
  val queue: StateFlow<List<Track>> = _queue.asStateFlow()

  private val _shuffle = MutableStateFlow(preferencesRepository.getShuffle())
  val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

  private val _repeatMode = MutableStateFlow(preferencesRepository.getRepeatMode())
  val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

  // null = the whole library; otherwise the playlist/favorites the user started from.
  private var contextTracks: List<Track>? = null
  private val playHistory = mutableListOf<Track>()

  private val _curiosityConfig = MutableStateFlow(preferencesRepository.getCuriosityConfig())
  val curiosityConfig: StateFlow<com.example.data.model.CuriosityConfig> = _curiosityConfig.asStateFlow()

  val isAiSpeakingCuriosity: StateFlow<Boolean> = curiositySpeechManager.isSpeaking
  val currentCuriositySpeechText: StateFlow<String?> = curiositySpeechManager.currentSpokenText
  val curiositySpeechStage: StateFlow<String?> = curiositySpeechManager.speechStage

  private val _isShowingCuriositySettings = MutableStateFlow(false)
  val isShowingCuriositySettings: StateFlow<Boolean> = _isShowingCuriositySettings.asStateFlow()

  private val _currentTab = MutableStateFlow(TarkasNavTab.HOME)
  val currentTab: StateFlow<TarkasNavTab> = _currentTab.asStateFlow()

  private val _allProfiles = MutableStateFlow<List<AudioProfile>>(AudioProfile.DEFAULT_PROFILES)
  val allProfiles: StateFlow<List<AudioProfile>> = _allProfiles.asStateFlow()

  private val _activeCuriosity = MutableStateFlow<Curiosity?>(null)
  val activeCuriosity: StateFlow<Curiosity?> = _activeCuriosity.asStateFlow()

  private val _assistantMessages = MutableStateFlow<List<AssistantMessage>>(emptyList())
  val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

  private val _isAssistantProcessing = MutableStateFlow(false)
  val isAssistantProcessing: StateFlow<Boolean> = _isAssistantProcessing.asStateFlow()

  private val _activeTimers = MutableStateFlow<List<LocalTimer>>(emptyList())
  val activeTimers: StateFlow<List<LocalTimer>> = _activeTimers.asStateFlow()

  private val _reminders = MutableStateFlow<List<LocalReminder>>(emptyList())
  val reminders: StateFlow<List<LocalReminder>> = _reminders.asStateFlow()

  private val _themeMode = MutableStateFlow(preferencesRepository.getThemeMode())
  val themeMode: StateFlow<String> = _themeMode.asStateFlow()

  val systemAudioStatus: StateFlow<SystemAudioStatus> = systemAudioCaptureManager.status
  val isSearchingAudius: StateFlow<Boolean> = musicRepository.isSearching

  private var timerTickerJob: Job? = null

  val assistantEngine: AssistantEngine = AssistantEngine(
    musicRepository = musicRepository,
    curiosityRepository = curiosityRepository,
    audioEngineManager = audioEngineManager,
    onTimerCreated = { timer -> addTimer(timer) },
    onTimerCancelled = { cancelAllTimers() },
    onReminderCreated = { reminder -> addReminder(reminder) },
    onCuriosityUpdated = { cur ->
      _activeCuriosity.value = cur
      updateSystemNotification()
    },
    onPlayerCommand = { command -> runPlayerCommand(command) }
  )

  // The assistant runs on a background dispatcher; the player must be driven from the main thread.
  private suspend fun runPlayerCommand(command: PlayerCommand): String? = withContext(Dispatchers.Main) {
    val track = musicRepository.currentTrack.value
    when (command) {
      PlayerCommand.NEXT -> {
        playNextTrack()
        musicRepository.currentTrack.value?.let { "Tocando ${it.title}" }
      }
      PlayerCommand.PREVIOUS -> {
        playPreviousTrack()
        musicRepository.currentTrack.value?.let { "Tocando ${it.title}" }
      }
      PlayerCommand.PLAY -> {
        if (track == null) {
          null
        } else {
          if (!audioEngineManager.isPlaying.value) togglePlayPause()
          "Reproduzindo ${track.title}"
        }
      }
      PlayerCommand.PAUSE -> {
        if (audioEngineManager.isPlaying.value) {
          togglePlayPause()
          "Pausado"
        } else {
          "Já estava pausado"
        }
      }
      PlayerCommand.TOGGLE_SHUFFLE -> {
        toggleShuffle()
        if (_shuffle.value) "Aleatório ligado" else "Aleatório desligado"
      }
      PlayerCommand.CYCLE_REPEAT -> {
        cycleRepeatMode()
        when (_repeatMode.value) {
          RepeatMode.OFF -> "Repetir desligado"
          RepeatMode.ALL -> "Repetindo a lista toda"
          RepeatMode.ONE -> "Repetindo uma faixa"
        }
      }
      PlayerCommand.FAVORITE -> track?.let {
        toggleFavorite(it)
        if (libraryRepository.isFavorite(it.id)) "${it.title} nos favoritos" else "${it.title} fora dos favoritos"
      }
    }
  }

  init {
    loadInitialData()
    startTimerTicker()
    setupNotificationReceiver()
  }

  private fun setupNotificationReceiver() {
    NotificationActionReceiver.onActionReceived = { action ->
      when (action) {
        NotificationActionReceiver.ACTION_TOGGLE -> togglePlayPause()
        NotificationActionReceiver.ACTION_NEXT -> playNextTrack()
        NotificationActionReceiver.ACTION_PREV -> playPreviousTrack()
      }
    }
    MusicNotificationManager.onSeekToRequested = { pos ->
      audioEngineManager.seekTo(pos)
      updateSystemNotification()
    }
  }

  private fun loadInitialData() {
    viewModelScope.launch {
      musicRepository.initializeCatalog()

      // Load saved profiles
      val custom = preferencesRepository.getCustomProfiles()
      _allProfiles.value = AudioProfile.DEFAULT_PROFILES + custom

      // Apply saved profile (default is FLAT - 0 dB for all 15 bands!)
      val savedId = preferencesRepository.getSavedProfileId()
      val profileToApply = _allProfiles.value.find { it.id == savedId } ?: AudioProfile.FLAT
      audioEngineManager.applyProfile(profileToApply)

      // Initial track curiosity
      musicRepository.currentTrack.collect { track ->
        if (track != null) {
          _activeCuriosity.value = curiosityRepository.getCuriosityForTrack(track)
          updateSystemNotification()
        }
      }
    }

    // Monitor isPlaying changes for notification
    viewModelScope.launch {
      audioEngineManager.isPlaying.collect { playing ->
        updateSystemNotification()
        // Playing: keep the app alive in the background as a foreground media service.
        if (playing) PlaybackService.start(context)
      }
    }

    // Nothing playing and nothing being narrated for a few seconds: leave the foreground.
    // The delay covers the gap between two tracks, so the service is not torn down and rebuilt.
    viewModelScope.launch {
      combine(audioEngineManager.isPlaying, curiositySpeechManager.isSpeaking) { playing, speaking ->
        playing || speaking
      }
        .debounce(IDLE_BEFORE_LEAVING_FOREGROUND_MS)
        .distinctUntilChanged()
        .collect { active -> if (!active) PlaybackService.release() }
    }

    // Initial Siri greeting (concise without listing commands unless user asks for help)
    _assistantMessages.value = listOf(
      AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = "Como posso ajudar com sua música ou áudio hoje?"
      )
    )
  }

  fun updateSystemNotification() {
    val track = musicRepository.currentTrack.value
    val isPlaying = audioEngineManager.isPlaying.value
    val cur = _activeCuriosity.value
    val pos = audioEngineManager.currentPositionMs.value
    MusicNotificationManager.updatePlaybackNotification(context, track, isPlaying, cur, pos)
  }

  fun setTab(tab: TarkasNavTab) {
    _currentTab.value = tab
  }

  /** Plays a track from the whole library. */
  fun playTrack(track: Track) {
    contextTracks = null
    playInternal(track, recordHistory = true)
  }

  /** Plays a track and keeps going through [list] (a playlist, favorites, ...). */
  fun playFromList(track: Track, list: List<Track>) {
    contextTracks = list
    playInternal(track, recordHistory = true)
  }

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
    updateSystemNotification()

    val config = _curiosityConfig.value
    if (config.enabled && (config.timing == com.example.data.model.CuriosityTiming.START_OF_TRACK || config.timing == com.example.data.model.CuriosityTiming.BOTH)) {
      // Intro curiosity begins playing BEFORE the music track
      audioEngineManager.stopPlayback()
      curiositySpeechManager.speakCuriosity(track, cur, isIntro = true, config = config) {
        startTrackAudio(track)
      }
    } else {
      startTrackAudio(track)
    }
  }

  private fun startTrackAudio(track: Track) {
    track.filePath?.let { path ->
      audioEngineManager.playTrack(path) {
        onTrackPlaybackCompleted(track)
      }
    }
    updateSystemNotification()
  }

  private fun onTrackPlaybackCompleted(completedTrack: Track) {
    val config = _curiosityConfig.value
    // Repeat-one loops the same track, so don't narrate its curiosity again on every pass.
    val narrate = _repeatMode.value != RepeatMode.ONE
    if (narrate && config.enabled && (config.timing == com.example.data.model.CuriosityTiming.END_OF_TRACK || config.timing == com.example.data.model.CuriosityTiming.BOTH)) {
      // Outro curiosity spoken at the end of the track
      val cur = _activeCuriosity.value ?: curiosityRepository.getCuriosityForTrack(completedTrack)
      curiositySpeechManager.speakCuriosity(completedTrack, cur, isIntro = false, config = config) {
        advance(auto = true)
      }
    } else {
      advance(auto = true)
    }
  }

  fun togglePlayPause() {
    val current = musicRepository.currentTrack.value
    if (!audioEngineManager.isPlaying.value && audioEngineManager.currentPositionMs.value == 0L && current != null) {
      // Restart the same track without losing the playlist context or adding it to the history.
      playInternal(current, recordHistory = false)
    } else {
      audioEngineManager.togglePlayPause()
    }
    updateSystemNotification()
  }

  fun playNextTrack() = advance(auto = false)

  /** [auto] is true when the track ended by itself; false for the Next button. */
  private fun advance(auto: Boolean) {
    curiositySpeechManager.stopSpeech()
    val current = musicRepository.currentTrack.value
    val next = PlaybackOrder.resolveNext(
      current = current,
      queue = _queue.value,
      list = contextTracks ?: musicRepository.tracks.value,
      shuffle = _shuffle.value,
      repeat = _repeatMode.value,
      auto = auto
    )
    _queue.value = next.remainingQueue
    val track = next.track ?: return
    if (next.loopSameTrack) {
      startTrackAudio(track)
    } else {
      playInternal(track, recordHistory = true)
    }
  }
  fun playPreviousTrack() {
    curiositySpeechManager.stopSpeech()
    // Like Apple Music: a few seconds in, "previous" restarts the track.
    if (audioEngineManager.currentPositionMs.value > 3000L) {
      audioEngineManager.seekTo(0L)
      return
    }
    val prev = if (playHistory.isNotEmpty()) {
      playHistory.removeAt(playHistory.lastIndex)
    } else {
      musicRepository.selectPreviousTrack()
    }
    if (prev != null) playInternal(prev, recordHistory = false)
  }

  // --- queue, shuffle, repeat ---

  fun toggleShuffle() {
    _shuffle.value = !_shuffle.value
    preferencesRepository.saveShuffle(_shuffle.value)
  }

  fun cycleRepeatMode() {
    _repeatMode.value = PlaybackOrder.nextRepeatMode(_repeatMode.value)
    preferencesRepository.saveRepeatMode(_repeatMode.value)
  }

  /** Turns shuffle on and starts a random track of [list]. */
  fun shuffleFrom(list: List<Track>) {
    if (list.isEmpty()) return
    if (!_shuffle.value) toggleShuffle()
    playFromList(list.random(), list)
  }

  fun playNext(track: Track) {
    _queue.value = PlaybackOrder.enqueueNext(_queue.value, track)
  }

  fun addToQueue(track: Track) {
    _queue.value = PlaybackOrder.enqueueLast(_queue.value, track)
  }

  fun removeFromQueue(index: Int) {
    _queue.value = PlaybackOrder.removeAt(_queue.value, index)
  }

  fun clearQueue() {
    _queue.value = emptyList()
  }

  fun playFromQueue(index: Int) {
    val jump = PlaybackOrder.jumpTo(_queue.value, index) ?: return
    _queue.value = jump.remainingQueue
    jump.track?.let { playInternal(it, recordHistory = true) }
  }

  // --- favorites and playlists ---

  fun toggleFavorite(track: Track) = libraryRepository.toggleFavorite(track)

  fun createPlaylist(name: String, firstTrack: Track? = null) {
    libraryRepository.createPlaylist(name, firstTrack)
  }

  fun renamePlaylist(id: String, name: String) = libraryRepository.renamePlaylist(id, name)

  fun deletePlaylist(id: String) = libraryRepository.deletePlaylist(id)

  fun addToPlaylist(playlistId: String, track: Track) = libraryRepository.addToPlaylist(playlistId, track)

  fun removeFromPlaylist(playlistId: String, trackId: String) =
    libraryRepository.removeFromPlaylist(playlistId, trackId)

  fun skipCuriositySpeech() {
    curiositySpeechManager.stopSpeech()
  }

  fun openCuriositySettings() {
    _isShowingCuriositySettings.value = true
  }

  fun closeCuriositySettings() {
    _isShowingCuriositySettings.value = false
  }

  fun updateCuriosityConfig(newConfig: com.example.data.model.CuriosityConfig) {
    _curiosityConfig.value = newConfig
    preferencesRepository.saveCuriosityConfig(newConfig)
  }

  fun testCuriosityVoice() {
    curiositySpeechManager.testVoiceSample(_curiosityConfig.value)
  }

  fun selectProfile(profile: AudioProfile) {
    audioEngineManager.applyProfile(profile)
    preferencesRepository.saveActiveProfileId(profile.id)
  }

  fun resetAllBandsToZero() {
    selectProfile(AudioProfile.FLAT)
    audioEngineManager.setPreampDb(0f)
  }

  fun saveCustomProfile(name: String) {
    val current = audioEngineManager.activeProfile.value
    val newProfile = current.copy(
      id = "custom_${System.currentTimeMillis()}",
      name = name.ifBlank { "Personalizado" },
      description = "Perfil criado pelo usuário em ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
      isCustom = true
    )
    preferencesRepository.saveCustomProfile(newProfile)
    val customList = preferencesRepository.getCustomProfiles()
    _allProfiles.value = AudioProfile.DEFAULT_PROFILES + customList
    selectProfile(newProfile)
  }

  fun analyzeCurrentTrack() {
    val track = musicRepository.currentTrack.value ?: return
    val cur = curiosityRepository.getCuriosityForTrack(track.id)
    _activeCuriosity.value = cur
    updateSystemNotification()

    val msg = AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = "Analisei **${track.title}** (${track.artist}):\n\n${cur.shortTrivia}\n\n*Fontes: ${cur.sources.firstOrNull() ?: "nenhuma fonte encontrada"}*"
    )
    _assistantMessages.value = _assistantMessages.value + msg
  }

  fun setThemeMode(mode: String) {
    _themeMode.value = mode
    preferencesRepository.saveThemeMode(mode)
  }

  fun sendAssistantMessage(text: String) {
    if (text.isBlank()) return

    val userMsg = AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.USER,
      text = text
    )
    _assistantMessages.value = _assistantMessages.value + userMsg
    _isAssistantProcessing.value = true

    viewModelScope.launch {
      val reply = assistantEngine.processUserMessage(text)
      _assistantMessages.value = _assistantMessages.value + reply
      _isAssistantProcessing.value = false
    }
  }

  fun startSystemAudioCapture(mediaProjection: MediaProjection) {
    systemAudioCaptureManager.startCaptureWithProjection(mediaProjection)
  }

  /** Shows an honest "unavailable" state; never fakes a detected track. */
  fun markSystemCaptureUnavailable(reason: String) {
    systemAudioCaptureManager.markUnavailable(reason)
  }

  // Apps playing now, read from `dumpsys audio` (needs the DUMP permission granted over adb)
  private val activeAppsReader = ActiveAppsReader(context)
  private val _activeApps = MutableStateFlow<ActiveAppsState>(ActiveAppsState.PermissionMissing)
  val activeApps: StateFlow<ActiveAppsState> = _activeApps.asStateFlow()

  fun refreshActiveApps() {
    viewModelScope.launch { _activeApps.value = activeAppsReader.read() }
  }

  fun setPreampDb(db: Float) {
    audioEngineManager.setPreampDb(db)
  }

  private fun addTimer(timer: LocalTimer) {
    _activeTimers.value = _activeTimers.value + timer
  }

  fun cancelTimer(timerId: String) {
    _activeTimers.value = _activeTimers.value.filter { it.id != timerId }
  }

  private fun cancelAllTimers() {
    _activeTimers.value = emptyList()
  }

  private fun addReminder(reminder: LocalReminder) {
    _reminders.value = _reminders.value + reminder
  }

  private fun startTimerTicker() {
    timerTickerJob?.cancel()
    timerTickerJob = viewModelScope.launch {
      while (isActive) {
        delay(1000)
        val currentTimers = _activeTimers.value
        if (currentTimers.isNotEmpty()) {
          val updated = currentTimers.mapNotNull { timer ->
            if (timer.isRunning && timer.remainingSeconds > 0) {
              val rem = timer.remainingSeconds - 1
              if (rem <= 0) {
                timer.copy(remainingSeconds = 0, isRunning = false, isCompleted = true)
              } else {
                timer.copy(remainingSeconds = rem)
              }
            } else {
              timer
            }
          }
          _activeTimers.value = updated
        }
      }
    }
  }

  fun addLocalAudio(title: String, artist: String, uriString: String, durationMs: Long) {
    musicRepository.addLocalTrack(title, artist, uriString, durationMs)
    val addedTrack = musicRepository.currentTrack.value
    addedTrack?.filePath?.let {
      audioEngineManager.playTrack(it) { playNextTrack() }
    }
    updateSystemNotification()
  }

  fun searchAudius(query: String) {
    viewModelScope.launch {
      val results = musicRepository.searchAudius(query)
      if (results.isNotEmpty()) {
        playTrack(results.first())
      }
    }
  }

  fun loadAudiusTrending() {
    viewModelScope.launch {
      val results = musicRepository.loadAudiusTrending()
      if (results.isNotEmpty() && musicRepository.currentTrack.value == null) {
        playTrack(results.first())
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    timerTickerJob?.cancel()
    curiositySpeechManager.shutdown()
    audioEngineManager.stopPlayback()
    PlaybackService.stop()
    systemAudioCaptureManager.stopCapture()
    MusicNotificationManager.clearNotification(context)
  }
}
