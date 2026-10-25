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
import com.example.data.audius.AudiusApiService
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
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
import com.example.ui.theme.AppMotion
import com.example.ui.theme.SurfaceStyle
import com.example.data.systemaudio.ActiveAppsReader
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.SystemAudioCaptureManager
import com.example.data.systemaudio.EqReach
import com.example.data.systemaudio.GlobalEq
import com.example.data.systemaudio.GlobalEqService
import com.example.data.systemaudio.GlobalEqStatus
import com.example.data.systemaudio.RootShell
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

/** State of the fone picker: the downloaded list, the current search and what is being applied. */
data class DeviceSearchState(
  val loading: Boolean = false,
  val error: String? = null,
  val query: String = "",
  /** Null = every source. */
  val source: String? = null,
  val sources: List<String> = emptyList(),
  val total: Int = 0,
  val results: List<com.example.data.autoeq.AutoEqEntry> = emptyList(),
  /** Path of the entry whose curve is downloading, to show a spinner on its row. */
  val applyingPath: String? = null
)

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

  // Equalizer presets: the built-in ones followed by the user's own.
  private val _eqPresets = MutableStateFlow(EqPresets.BUILT_IN)
  val eqPresets: StateFlow<List<EqPreset>> = _eqPresets.asStateFlow()

  /** The preset the curve came from; [eqModified] tells whether it was edited since. */
  private val _activePresetId = MutableStateFlow(EqPresets.FLAT.id)
  val activePresetId: StateFlow<String> = _activePresetId.asStateFlow()

  private val _eqModified = MutableStateFlow(false)
  val eqModified: StateFlow<Boolean> = _eqModified.asStateFlow()

  val eqSettings: StateFlow<EqSettings> get() = audioEngineManager.eqSettings
  private var eqSaveJob: Job? = null

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
    onPlayerCommand = { command -> runPlayerCommand(command) },
    onEqPreset = { preset -> selectEqPreset(preset) }
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

      // Equalizer: presets, then the exact curve the user left (default is Flat, 0 dB everywhere)
      _eqPresets.value = EqPresets.BUILT_IN + preferencesRepository.getUserPresets()
      val savedId = preferencesRepository.getSavedProfileId()
      val preset = _eqPresets.value.find { it.id == savedId } ?: EqPresets.FLAT
      val settings = preferencesRepository.getEqSettings() ?: preset.settings
      _activePresetId.value = preset.id
      _eqModified.value = settings.userCurve != preset.settings.userCurve
      audioEngineManager.applyEqSettings(settings)

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

  /** The tab the player was opened from, so back returns there instead of to Início. */
  private var tabBeforePlayer = TarkasNavTab.HOME

  fun setTab(tab: TarkasNavTab) {
    val from = _currentTab.value
    if (tab == TarkasNavTab.MUSIC && from != TarkasNavTab.MUSIC) tabBeforePlayer = from
    _currentTab.value = tab
  }

  /** Back: the player returns to the tab it was opened from; any other tab returns to Início. */
  fun goBack() {
    setTab(if (_currentTab.value == TarkasNavTab.MUSIC) tabBeforePlayer else TarkasNavTab.HOME)
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

  // ---- Audius (searched from the Início search bar; full tracks played by the app's own player) ----

  private val _audiusResults = MutableStateFlow<List<Track>>(emptyList())
  val audiusResults: StateFlow<List<Track>> = _audiusResults.asStateFlow()

  private val _audiusSearching = MutableStateFlow(false)
  val audiusSearching: StateFlow<Boolean> = _audiusSearching.asStateFlow()

  private var audiusSearchJob: Job? = null

  init {
    // The Spotify integration was removed: drop the encrypted store and key it may have left.
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { context.deleteSharedPreferences("tarkas_secure") }
      runCatching {
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("tarkas_spotify_tokens")
      }
    }
  }

  /** Searches Audius as the user types in Início, without adding the results to the library. */
  fun searchAudiusOnline(query: String) {
    audiusSearchJob?.cancel()
    if (query.isBlank()) {
      _audiusResults.value = emptyList()
      _audiusSearching.value = false
      return
    }
    audiusSearchJob = viewModelScope.launch {
      // Wait for a pause in typing so each keystroke is not a request.
      kotlinx.coroutines.delay(350)
      _audiusSearching.value = true
      try {
        _audiusResults.value = withContext(Dispatchers.IO) { AudiusApiService.searchTracks(query.trim()) }
      } finally {
        _audiusSearching.value = false
      }
    }
  }

  /** Plays an Audius result; Next goes on through the rest of the results. */
  fun playAudiusResult(track: Track) = playFromList(track, _audiusResults.value)

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

  // ---- Equalizer ----

  /** Changes the curve. Saved shortly after the last change, so dragging does not write on every frame. */
  fun updateEq(transform: (EqSettings) -> EqSettings) {
    val next = transform(audioEngineManager.eqSettings.value)
    audioEngineManager.applyEqSettings(next)
    val preset = _eqPresets.value.find { it.id == _activePresetId.value }
    _eqModified.value = preset == null || next.userCurve != preset.settings.userCurve
    eqSaveJob?.cancel()
    eqSaveJob = viewModelScope.launch {
      kotlinx.coroutines.delay(400)
      preferencesRepository.saveEqSettings(next)
    }
  }

  fun setEqEnabled(enabled: Boolean) = updateEq { it.copy(enabled = enabled) }

  fun updateEqBand(index: Int, transform: (EqBand) -> EqBand) = updateEq { s ->
    if (index !in s.bands.indices) s
    else s.copy(bands = s.bands.toMutableList().also { it[index] = transform(it[index]) })
  }

  /** Loads a preset. The on/off switch stays as it is. */
  fun selectEqPreset(preset: EqPreset) {
    val current = audioEngineManager.eqSettings.value
    _activePresetId.value = preset.id
    preferencesRepository.saveActiveProfileId(preset.id)
    // The fone correction is its own stage: changing the preset keeps it.
    updateEq { preset.settings.copy(enabled = current.enabled, device = current.device) }
  }

  fun resetEq() = selectEqPreset(EqPresets.FLAT)

  fun saveEqPresetAs(name: String) {
    val preset = EqPreset(
      id = "custom_${System.currentTimeMillis()}",
      name = name.trim().ifBlank { "Meu preset" },
      settings = audioEngineManager.eqSettings.value.userCurve,
      isCustom = true
    )
    storeUserPresets(userPresets() + preset)
    selectEqPreset(preset)
  }

  /** Writes the current curve into the active user preset. */
  fun overwriteActiveEqPreset() {
    val id = _activePresetId.value
    val current = audioEngineManager.eqSettings.value.userCurve
    if (userPresets().none { it.id == id }) return
    storeUserPresets(userPresets().map { if (it.id == id) it.copy(settings = current) else it })
    _eqModified.value = false
  }

  fun renameEqPreset(id: String, name: String) {
    if (name.isBlank()) return
    storeUserPresets(userPresets().map { if (it.id == id) it.copy(name = name.trim()) else it })
  }

  fun deleteEqPreset(id: String) {
    storeUserPresets(userPresets().filterNot { it.id == id })
    if (_activePresetId.value == id) {
      // Keep the sound as it is; it simply no longer belongs to a saved preset.
      _activePresetId.value = EqPresets.FLAT.id
      _eqModified.value = true
    }
  }

  private fun userPresets(): List<EqPreset> = _eqPresets.value.filter { it.isCustom }

  private fun storeUserPresets(list: List<EqPreset>) {
    preferencesRepository.saveUserPresets(list)
    _eqPresets.value = EqPresets.BUILT_IN + list
  }

  fun analyzeCurrentTrack() {
    val track = musicRepository.currentTrack.value ?: return
    val cur = curiosityRepository.getCuriosityForTrack(track.id)
    _activeCuriosity.value = cur
    updateSystemNotification()

    val msg = AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      // Curiosities stay in the player and the notification; the chat only points there.
      text = "Analisei **${track.title}** (${track.artist}). A curiosidade fica no player: toque no mini player acima da barra e depois na lâmpada."
    )
    _assistantMessages.value = _assistantMessages.value + msg
  }

  private val _surfaceStyle = MutableStateFlow(SurfaceStyle.fromKey(preferencesRepository.getSurfaceStyle()))
  val surfaceStyle: StateFlow<SurfaceStyle> = _surfaceStyle.asStateFlow()

  fun setSurfaceStyle(style: SurfaceStyle) {
    _surfaceStyle.value = style
    preferencesRepository.saveSurfaceStyle(style.key)
  }

  // ---- Fone correction (AutoEq: oratory1990, crinacle, Rtings…) ----

  private val autoEq = com.example.data.autoeq.AutoEqRepository(context)
  private var deviceIndex: List<com.example.data.autoeq.AutoEqEntry> = emptyList()

  private val _deviceSearch = MutableStateFlow(DeviceSearchState())
  val deviceSearch: StateFlow<DeviceSearchState> = _deviceSearch.asStateFlow()
  private var deviceSearchJob: Job? = null

  /** Loads the list of fones (downloaded once, then cached) and shows the first matches. */
  fun openDevicePicker() {
    if (deviceIndex.isNotEmpty()) return searchDevices(_deviceSearch.value.query, _deviceSearch.value.source)
    _deviceSearch.value = _deviceSearch.value.copy(loading = true, error = null)
    viewModelScope.launch {
      try {
        deviceIndex = autoEq.index()
        // The best-known reviewers first, then the rest by how many fones they measured.
        val preferred = listOf("oratory1990", "crinacle", "Rtings", "Innerfidelity")
        val sources = deviceIndex.groupingBy { it.source }.eachCount().entries
          .sortedWith(compareBy({ preferred.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { -it.value }))
          .map { it.key }
        _deviceSearch.value = _deviceSearch.value.copy(loading = false, sources = sources, total = deviceIndex.size)
        searchDevices(_deviceSearch.value.query, _deviceSearch.value.source)
      } catch (e: Exception) {
        _deviceSearch.value = _deviceSearch.value.copy(loading = false, error = "Sem conexão para baixar a lista de fones.")
      }
    }
  }

  fun searchDevices(query: String, source: String?) {
    _deviceSearch.value = _deviceSearch.value.copy(query = query, source = source)
    deviceSearchJob?.cancel()
    deviceSearchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
      kotlinx.coroutines.delay(120)
      val results = com.example.data.autoeq.AutoEqParser.search(deviceIndex, query, source)
      _deviceSearch.value = _deviceSearch.value.copy(results = results)
    }
  }

  /** Downloads the fone's curve and applies it as the correction stage. */
  fun applyDevice(entry: com.example.data.autoeq.AutoEqEntry, onDone: () -> Unit) {
    _deviceSearch.value = _deviceSearch.value.copy(applyingPath = entry.path, error = null)
    viewModelScope.launch {
      try {
        val correction = autoEq.correction(entry) ?: throw IllegalStateException("curva vazia")
        updateEq { it.copy(device = correction) }
        _deviceSearch.value = _deviceSearch.value.copy(applyingPath = null)
        onDone()
      } catch (e: Exception) {
        _deviceSearch.value = _deviceSearch.value.copy(applyingPath = null, error = "Não foi possível baixar a curva de ${entry.name}.")
      }
    }
  }

  fun clearDevice() = updateEq { it.copy(device = null) }

  private val _reduceMotion = MutableStateFlow(preferencesRepository.getReduceMotion().also { AppMotion.userReduced = it })
  val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

  fun setReduceMotion(reduce: Boolean) {
    _reduceMotion.value = reduce
    AppMotion.userReduced = reduce
    preferencesRepository.saveReduceMotion(reduce)
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

  // ---- Equalizer reach: only the app (rootless), every app with ADB, or every app with root ----

  private val _eqReach = MutableStateFlow(EqReach.fromKey(preferencesRepository.getEqReach()))
  val eqReach: StateFlow<EqReach> = _eqReach.asStateFlow()
  val globalEqStatus: StateFlow<GlobalEqStatus> = GlobalEq.status

  /** Whether this device has `su` at all; root mode is offered either way, with a clear status. */
  val rootPresent: Boolean = RootShell.suPresent()

  init {
    // The service applies the same curve the app uses.
    viewModelScope.launch { audioEngineManager.eqSettings.collect { GlobalEq.settings.value = it } }
    applyEqReach(_eqReach.value)
  }

  fun setEqReach(reach: EqReach) {
    _eqReach.value = reach
    preferencesRepository.saveEqReach(reach.key)
    applyEqReach(reach)
  }

  private fun applyEqReach(reach: EqReach) {
    GlobalEq.reach.value = reach
    if (reach == EqReach.APP) {
      GlobalEqService.stop(context)
    } else {
      // Restart so a change between ADB and root takes effect (root asks for su on start).
      GlobalEqService.stop(context)
      GlobalEqService.start(context)
    }
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
