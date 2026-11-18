package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.assistant.AssistantEngine
import com.example.data.assistant.AssistantSpeaker
import com.example.data.assistant.assistantHistoryFromJson
import com.example.data.assistant.assistantHistoryToJson
import com.example.data.assistant.shouldSpeakReply
import com.example.data.assistant.speakableReply
import com.example.data.assistant.PlayerCommand
import com.example.data.audio.AudioEngineManager
import com.example.data.model.AssistantMessage
import com.example.data.audius.AudiusApiService
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.ui.screens.audio.localizedName
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.data.model.Curiosity
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.repository.LibraryRepository
import com.example.data.playback.PlaybackOrder
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
import com.example.data.jamendo.JamendoApiService
import com.example.data.jamendo.JamendoNotConfigured
import com.example.data.musicbrainz.MusicBrainzService
import com.example.data.musicbrainz.RecordingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.data.diagnostics.Diagnostics
import com.example.data.model.AssistantFailure
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
  /** How many of [total] exist only in the online index (the rest come with the app and work offline). */
  val onlineOnly: Int = 0,
  val results: List<com.example.data.autoeq.AutoEqEntry> = emptyList(),
  /** Path of the entry whose curve is downloading, to show a spinner on its row. */
  val applyingPath: String? = null
)

/** A short message over the mini player, with an optional action. A new [id] shows it again. */
data class Notice(
  val text: String,
  val actionLabel: String? = null,
  val action: (() -> Unit)? = null,
  val id: Long = System.nanoTime()
)

/** A catalogue row of Início ("Em alta no Audius", "Popular no Jamendo"). */
data class TrendingState(
  val loading: Boolean = false,
  val tracks: List<Track> = emptyList(),
  /** The service did not answer: offline, or it is down. */
  val offline: Boolean = false,
  /** The build has no key for the service, so it is not asked at all. */
  val notConfigured: Boolean = false
)

/** One service's answer to the search of Início. */
data class OnlineSearchState(
  val results: List<Track> = emptyList(),
  val searching: Boolean = false,
  /** No answer (offline or down), as opposed to no results. */
  val offline: Boolean = false,
  /** The build has no key for the service. */
  val notConfigured: Boolean = false
)

/** "Sobre a faixa": what MusicBrainz says about the playing song. */
data class TrackDetailsState(
  val trackId: String? = null,
  val loading: Boolean = false,
  val info: RecordingInfo? = null,
  /** MusicBrainz answered but has nothing it is sure of. */
  val notFound: Boolean = false,
  val offline: Boolean = false
)

/** The pages; [labelRes] is the title, in the system language. */
enum class TarkasNavTab(@androidx.annotation.StringRes val labelRes: Int) {
  HOME(com.example.R.string.player_common_tab_home),
  MUSIC(com.example.R.string.player_common_tab_music),
  LIBRARY(com.example.R.string.player_common_tab_library),
  AUDIO(com.example.R.string.player_common_tab_audio),
  ASSISTANT(com.example.R.string.player_common_tab_assistant),
  SETTINGS(com.example.R.string.player_common_settings)
}

@OptIn(FlowPreview::class)
class TarkasViewModel(
  application: Application,
  private val savedState: androidx.lifecycle.SavedStateHandle
) : AndroidViewModel(application) {

  private val context = application.applicationContext
  // The player and what feeds it live as long as the process, not as long as this view model.
  private val graph = com.example.AppGraph.also { it.init(context) }
  private val playback = graph.playback
  val musicRepository = graph.musicRepository
  val curiosityRepository = graph.curiosityRepository
  val audioEngineManager = graph.audioEngineManager
  val preferencesRepository = graph.preferencesRepository
  val systemAudioCaptureManager = SystemAudioCaptureManager(context)
  val curiositySpeechManager = graph.curiositySpeechManager

  val libraryRepository = graph.libraryRepository
  val favorites: StateFlow<List<String>> = libraryRepository.favorites
  val playlists: StateFlow<List<Playlist>> = libraryRepository.playlists
  val savedTracks: StateFlow<Map<String, Track>> = libraryRepository.savedTracks

  val queue: StateFlow<List<Track>> = playback.queue
  val shuffle: StateFlow<Boolean> = playback.shuffle
  val repeatMode: StateFlow<RepeatMode> = playback.repeatMode
  val curiosityConfig: StateFlow<com.example.data.model.CuriosityConfig> = playback.curiosityConfig
  val sleepTimer: StateFlow<com.example.data.playback.SleepTimer?> = playback.sleepTimer
  val playbackProblem: StateFlow<com.example.data.playback.PlaybackProblem?> = playback.problem
  fun retryPlayback() = playback.retryFailed()

  val isAiSpeakingCuriosity: StateFlow<Boolean> = curiositySpeechManager.isSpeaking
  val currentCuriositySpeechText: StateFlow<String?> = curiositySpeechManager.currentSpokenText
  val curiositySpeechStage: StateFlow<String?> = curiositySpeechManager.speechStage

  // The route stack: the page shown and the pages to go back to, kept across process death.
  private val nav = NavStack.decode(savedState.get<ArrayList<String>>(KEY_ROUTES))
  private val _currentTab = MutableStateFlow(nav.currentTab)
  val currentTab: StateFlow<TarkasNavTab> = _currentTab.asStateFlow()
  private val _isShowingCuriositySettings = MutableStateFlow(nav.showingCuriosity)
  val isShowingCuriositySettings: StateFlow<Boolean> = _isShowingCuriositySettings.asStateFlow()
  private val _settingsPage = MutableStateFlow(nav.settingsPage)
  /** The page open inside Ajustes (About, Terms, …), or null on Ajustes itself. */
  val settingsPage: StateFlow<SettingsSubPage?> = _settingsPage.asStateFlow()

  /** Favourites as tracks, in the order they were added; worked out here once instead of on every frame. */
  val favoriteTracks: StateFlow<List<Track>> = kotlinx.coroutines.flow.combine(
    libraryRepository.favorites, musicRepository.tracks, libraryRepository.savedTracks
  ) { ids, tracks, saved ->
    val byId = tracks.associateBy { it.id }
    ids.mapNotNull { id -> byId[id] ?: saved[id] }
  }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

  // Equalizer presets: the built-in ones followed by the user's own.
  /** The saved curve, once loaded: what runs on it waits for this. */
  private val eqLoaded = kotlinx.coroutines.CompletableDeferred<EqSettings>()
  private val _eqPresets = MutableStateFlow(EqPresets.BUILT_IN)
  val eqPresets: StateFlow<List<EqPreset>> = _eqPresets.asStateFlow()

  /** The preset the curve came from; [eqModified] tells whether it was edited since. */
  private val _activePresetId = MutableStateFlow(EqPresets.FLAT.id)
  val activePresetId: StateFlow<String> = _activePresetId.asStateFlow()

  private val _eqModified = MutableStateFlow(false)
  val eqModified: StateFlow<Boolean> = _eqModified.asStateFlow()

  val eqSettings: StateFlow<EqSettings> get() = audioEngineManager.eqSettings
  private var eqSaveJob: Job? = null

  val activeCuriosity: StateFlow<Curiosity?> = playback.activeCuriosity

  private val _assistantMessages = MutableStateFlow<List<AssistantMessage>>(emptyList())
  val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

  private val _isAssistantProcessing = MutableStateFlow(false)
  val isAssistantProcessing: StateFlow<Boolean> = _isAssistantProcessing.asStateFlow()

  // Online answers (Gemini) on or off. Saved in the app's preferences; on by default, as before.
  private val assistantPrefs = context.getSharedPreferences("tarkas_prefs", android.content.Context.MODE_PRIVATE)
  private val _assistantOnlineAi = MutableStateFlow(
    runCatching { assistantPrefs.getBoolean("assistant_online_ai", true) }.getOrDefault(true)
  )
  val assistantOnlineAi: StateFlow<Boolean> = _assistantOnlineAi.asStateFlow()

  fun setAssistantOnlineAi(enabled: Boolean) {
    _assistantOnlineAi.value = enabled
    runCatching { assistantPrefs.edit().putBoolean("assistant_online_ai", enabled).apply() }
  }

  // The conversation is kept in a file, so it is still there after the app restarts. Writes run one at a time.
  private val assistantHistoryFile = java.io.File(context.filesDir, "assistant_history.json")
  private val assistantSaveDispatcher = Dispatchers.IO.limitedParallelism(1)

  private fun assistantGreeting(): List<AssistantMessage> = listOf(
    AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = context.getString(com.example.R.string.assistant_greeting)
    )
  )

  private fun restoreAssistantHistory(): List<AssistantMessage>? = runCatching {
    if (!assistantHistoryFile.exists()) return@runCatching null
    assistantHistoryFromJson(assistantHistoryFile.readText()).takeIf { it.isNotEmpty() }
  }.getOrNull()

  private fun publishAssistantMessages(list: List<AssistantMessage>) {
    _assistantMessages.value = list
    val json = assistantHistoryToJson(list)
    viewModelScope.launch(assistantSaveDispatcher) {
      runCatching { assistantHistoryFile.writeText(json) }
    }
  }

  // "Limpar conversa": the chat starts again from the greeting. The saved copy is replaced with it.
  fun clearAssistantConversation() = publishAssistantMessages(assistantGreeting())

  private val _activeTimers = MutableStateFlow<List<LocalTimer>>(emptyList())
  val activeTimers: StateFlow<List<LocalTimer>> = _activeTimers.asStateFlow()

  // Appearance and behaviour switches have one owner, shared with SettingsViewModel.
  private val settings = graph.settings
  val themeMode: StateFlow<String> = settings.themeMode
  val systemColors: StateFlow<Boolean> = settings.systemColors

  val systemAudioStatus: StateFlow<SystemAudioStatus> = systemAudioCaptureManager.status

  // The player screens read these, from the app's own engine.
  val isPlaying: StateFlow<Boolean> = playback.isPlaying
  /** Playing, or a track on its way: stays true while the next track loads, false only on a real pause. */
  val wantsToPlay: StateFlow<Boolean> = playback.wantsToPlay
  val positionMs: StateFlow<Long> = playback.positionMs
  val durationMs: StateFlow<Long> = playback.durationMs

  private val _notice = MutableStateFlow<Notice?>(null)
  val notice: StateFlow<Notice?> = _notice.asStateFlow()

  fun showNotice(notice: Notice) {
    _notice.value = notice
  }

  fun dismissNotice(id: Long) {
    if (_notice.value?.id == id) _notice.value = null
  }

  /** Set by the player's queue button: the Library opens on Fila, then clears it. */
  private val _openQueueRequest = MutableStateFlow(false)
  val openQueueRequest: StateFlow<Boolean> = _openQueueRequest.asStateFlow()

  fun openQueue() {
    _openQueueRequest.value = true
    setTab(TarkasNavTab.LIBRARY)
  }

  fun queueOpened() {
    _openQueueRequest.value = false
  }

  private var timerTickerJob: Job? = null

  // What the assistant may change. The engine calls these only after the user confirmed the action.
  // Playing keeps the songs already queued (no second question), and the volume is the device's music stream,
  // because the app has no volume control of its own.
  private val assistantActions = object : com.example.data.assistant.AssistantActions {
    override fun library(): com.example.data.assistant.LibrarySnapshot {
      val tracks = musicRepository.tracks.value
      val byId = tracks.associateBy { it.id } + savedTracks.value
      return com.example.data.assistant.LibrarySnapshot(
        tracks = tracks,
        playlists = libraryRepository.playlists.value.map { p ->
          com.example.data.assistant.LibraryPlaylist(p.name, p.trackIds.mapNotNull { byId[it] })
        }
      )
    }

    override fun playTracks(tracks: List<Track>): Boolean {
      val first = tracks.firstOrNull() ?: return false
      playback.playFromList(first, tracks, keepQueue = true)
      return true
    }

    override fun queueTracks(tracks: List<Track>, asNext: Boolean): Boolean {
      if (tracks.isEmpty()) return false
      // Next-in-queue inserts at the front, so the list is added in reverse to keep its order.
      if (asNext) tracks.asReversed().forEach { playback.playNext(it) } else tracks.forEach { playback.addToQueue(it) }
      return true
    }

    override fun clearQueue(): Boolean {
      playback.clearQueue()
      return true
    }

    override fun sleepIn(minutes: Int): Boolean {
      playback.setSleepTimer(minutes)
      return true
    }

    override fun sleepAtEndOfTrack(): Boolean {
      playback.setSleepAtEndOfTrack()
      return true
    }

    override fun cancelSleep(): Boolean {
      playback.cancelSleepTimer()
      return true
    }

    override fun setSpatial(on: Boolean): Boolean {
      setSpatialEnabled(on)
      return true
    }

    override fun setVolumePercent(percent: Int): Boolean {
      val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return false
      val stream = android.media.AudioManager.STREAM_MUSIC
      val target = Math.round(audio.getStreamMaxVolume(stream) * percent.coerceIn(0, 100) / 100f)
      audio.setStreamVolume(stream, target, 0)
      return true
    }
  }

  val assistantEngine: AssistantEngine = AssistantEngine(
    context = context,
    musicRepository = musicRepository,
    curiosityRepository = curiosityRepository,
    audioEngineManager = audioEngineManager,
    onTimerCreated = { timer -> addTimer(timer) },
    onTimerCancelled = { cancelAllTimers() },
    onCuriosityUpdated = { cur -> playback.setActiveCuriosity(cur) },
    onPlayerCommand = { command -> runPlayerCommand(command) },
    onEqPreset = { preset -> selectEqPreset(preset) },
    // "desfazer" puts back the equalizer state from before the chat change, through updateEq so it is saved.
    onEqRestore = { saved -> updateEq { saved } },
    // Through updateEq, so the change is saved and marks the preset as edited like a turn of the knob.
    onTone = { bass, delta ->
      val r = EqSettings.TONE_RANGE_DB
      updateEq { s -> if (bass) s.copy(bassDb = (s.bassDb + delta).coerceIn(-r, r)) else s.copy(trebleDb = (s.trebleDb + delta).coerceIn(-r, r)) }
      audioEngineManager.eqSettings.value.let { if (bass) it.bassDb else it.trebleDb }
    },
    actions = assistantActions
  )

  // The assistant runs on a background dispatcher; the player must be driven from the main thread.
  private suspend fun runPlayerCommand(command: PlayerCommand): String? = withContext(Dispatchers.Main) {
    val track = musicRepository.currentTrack.value
    when (command) {
      PlayerCommand.NEXT -> {
        playNextTrack()
        musicRepository.currentTrack.value?.let { context.getString(com.example.R.string.assistant_result_now_playing, it.title) }
      }
      PlayerCommand.PREVIOUS -> {
        playPreviousTrack()
        musicRepository.currentTrack.value?.let { context.getString(com.example.R.string.assistant_result_now_playing, it.title) }
      }
      PlayerCommand.PLAY -> {
        if (track == null) {
          null
        } else {
          if (!isPlaying.value) togglePlayPause()
          context.getString(com.example.R.string.assistant_result_playing, track.title)
        }
      }
      PlayerCommand.PAUSE -> {
        if (isPlaying.value) {
          togglePlayPause()
          context.getString(com.example.R.string.assistant_result_paused)
        } else {
          context.getString(com.example.R.string.assistant_result_already_paused)
        }
      }
      PlayerCommand.TOGGLE_SHUFFLE -> {
        toggleShuffle()
        context.getString(if (shuffle.value) com.example.R.string.assistant_result_shuffle_on else com.example.R.string.assistant_result_shuffle_off)
      }
      PlayerCommand.CYCLE_REPEAT -> {
        cycleRepeatMode()
        context.getString(
          when (repeatMode.value) {
            RepeatMode.OFF -> com.example.R.string.assistant_result_repeat_off
            RepeatMode.ALL -> com.example.R.string.assistant_result_repeat_all
            RepeatMode.ONE -> com.example.R.string.assistant_result_repeat_one
          }
        )
      }
      PlayerCommand.FAVORITE -> track?.let {
        toggleFavorite(it)
        if (libraryRepository.isFavorite(it.id)) {
          context.getString(com.example.R.string.assistant_result_favorite_added, it.title)
        } else {
          context.getString(com.example.R.string.assistant_result_favorite_removed, it.title)
        }
      }
    }
  }

  init {
    loadInitialData()
    startTimerTicker()
    // Playback messages (a track that failed, the sleep timer) appear over the mini player.
    viewModelScope.launch {
      playback.notices.collect { showNotice(Notice(context.getString(it.textRes, *it.textArgs.toTypedArray()), it.actionLabelRes?.let { r -> context.getString(r) }, it.action)) }
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
      _eqModified.value = com.example.data.model.EqCompare.edited(settings, preset.settings)
      audioEngineManager.applyEqSettings(settings)
      eqLoaded.complete(settings)
    }

    // Initial Siri greeting (concise without listing commands unless user asks for help)
    // The saved conversation comes back when there is one; otherwise the greeting starts it.
    _assistantMessages.value = restoreAssistantHistory() ?: assistantGreeting()
  }

  /**
   * Opens a page on top of the current one (the player, Ajustes, Áudio from Ajustes): back returns to
   * where it was opened from.
   */
  fun setTab(tab: TarkasNavTab) = navigate { it.open(Route.Tab(tab)) }

  /** A tab picked in the tab bar: a fresh start at that tab, like every tab bar on the platform. */
  fun selectTab(tab: TarkasNavTab) = navigate { it.select(tab) }

  /** Back: the page it was opened from; from a tab with nothing under it, Início. */
  fun goBack() = navigate { it.back() }

  /** Opens a page inside Ajustes (About, Terms, …); back returns to where it was opened from. */
  fun openSettingsPage(page: SettingsSubPage) = navigate {
    if (it.currentTab != TarkasNavTab.SETTINGS) it.open(Route.Tab(TarkasNavTab.SETTINGS))
    it.open(Route.Settings(page))
  }

  /** True while back has somewhere to go inside the app. */
  val canGoBack: Boolean get() = nav.canGoBack

  private fun navigate(change: (NavStack) -> Unit) {
    change(nav)
    _currentTab.value = nav.currentTab
    _isShowingCuriositySettings.value = nav.showingCuriosity
    _settingsPage.value = nav.settingsPage
    savedState[KEY_ROUTES] = nav.encode()
  }

  fun playTrack(track: Track) = startList(track, null)

  fun playFromList(track: Track, list: List<Track>) = startList(track, list)

  /** Starting another list while songs the user queued are waiting: keep them or clear them? */
  data class ListStartPrompt(val track: Track, val list: List<Track>?, val queued: Int)

  private val _listStartPrompt = MutableStateFlow<ListStartPrompt?>(null)
  val listStartPrompt: StateFlow<ListStartPrompt?> = _listStartPrompt.asStateFlow()

  /**
   * [list] null plays from the whole library. Asks first when it replaces the list and the queue has
   * songs; false then, and the answer ([answerListStart]) plays it.
   */
  private fun startList(track: Track, list: List<Track>?): Boolean {
    val queued = playback.queue.value.size
    if (queued > 0 && !playback.isCurrentContext(list)) {
      _listStartPrompt.value = ListStartPrompt(track, list, queued)
      return false
    }
    if (list == null) playback.playTrack(track) else playback.playFromList(track, list)
    return true
  }

  /** The answer to [listStartPrompt]: null dismisses it without playing. Playing opens the player. */
  fun answerListStart(keepQueue: Boolean?) {
    val p = _listStartPrompt.value ?: return
    _listStartPrompt.value = null
    if (keepQueue == null) return
    if (p.list == null) playback.playTrack(p.track, keepQueue) else playback.playFromList(p.track, p.list, keepQueue)
    setTab(TarkasNavTab.MUSIC)
  }

  /** What the list plays after the queue runs out. */
  val upcoming: StateFlow<List<Track>> get() = playback.upcoming

  fun playFromCurrentList(track: Track) = playback.playFromCurrentList(track)

  /** How Biblioteca lists the device's songs, kept between launches. */
  val songOrder: com.example.data.playback.SongOrder
    get() = com.example.data.playback.SongOrder.fromKey(preferencesRepository.getSongOrder())

  fun setSongOrder(order: com.example.data.playback.SongOrder) = preferencesRepository.saveSongOrder(order.key)

  /** Plays counted on this device per track, for "Mais tocadas". */
  val playCounts: StateFlow<Map<String, Int>> get() = playback.playCounts

  fun loadPlayCounts() = playback.loadPlayCounts()

  /** Plays, skips and last play per track, read from the database for the automatic lists. */
  suspend fun readPlayStats(): Map<String, com.example.data.playback.TrackStats> =
    com.example.data.playback.PlayStatsStore(graph.database.playStats()).all()

  // A second reader of the saved playback state, only for "Continuar ouvindo" on Início.
  private val resumeStore by lazy { com.example.data.playback.PlaybackStateStore(graph.database.playback()) }

  /** Where the saved track stopped, or null when there is nothing valid to continue. */
  suspend fun readResumeInfo(): com.example.data.playback.ResumeInfo? = resumeStore.resumeInfo()

  /** Continues the track that is loaded and paused, from where it stopped. */
  fun resumeListening() {
    if (!playback.isPlaying.value) playback.togglePlayPause()
  }

  /** Crossfade between tracks in seconds; 0 is off. */
  val crossfadeSeconds: StateFlow<Int> get() = playback.crossfadeSeconds

  fun setCrossfadeSeconds(seconds: Int) = playback.setCrossfadeSeconds(seconds)

  fun togglePlayPause() = playback.togglePlayPause()

  fun pause() = playback.pause()

  fun seekTo(ms: Long) = playback.seekTo(ms)

  fun playNextTrack() = playback.playNextTrack()

  /** [force] goes back even a few seconds into the track (the cover of the previous one was swiped in). */
  fun playPreviousTrack(force: Boolean = false) = playback.playPreviousTrack(force)

  /** The tracks Previous and Next go to, shown beside the cover in the full player. */
  val coverNeighbors: StateFlow<com.example.data.playback.CoverNeighbors.Result> get() = playback.coverNeighbors

  // --- queue, shuffle, repeat, sleep timer ---

  fun toggleShuffle() = playback.toggleShuffle()

  fun cycleRepeatMode() = playback.cycleRepeatMode()

  fun setRepeatMode(mode: RepeatMode) = playback.setRepeatMode(mode)

  fun shuffleFrom(list: List<Track>) = playback.shuffleFrom(list)

  fun playNext(track: Track) {
    playback.playNext(track)
    showNotice(Notice(context.getString(com.example.R.string.library_notice_play_next, track.title)))
  }

  fun addToQueue(track: Track) {
    playback.addToQueue(track)
    showNotice(Notice(context.getString(com.example.R.string.library_notice_added_to_queue, track.title)))
  }

  /** Removes a queue item, with an undo on the notice. */
  fun removeFromQueue(index: Int) {
    val removed = playback.removeFromQueue(index) ?: return
    showNotice(Notice(context.getString(com.example.R.string.player_notice_removed_from_queue, removed.title), actionLabel = context.getString(com.example.R.string.player_notice_undo), action = { playback.restoreToQueue(index, removed) }))
  }

  fun moveInQueue(from: Int, to: Int) = playback.moveInQueue(from, to)

  fun clearQueue() = playback.clearQueue()

  fun playFromQueue(index: Int) = playback.playFromQueue(index)

  fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)

  fun setSleepAtEndOfTrack() = playback.setSleepAtEndOfTrack()

  fun cancelSleepTimer() = playback.cancelSleepTimer()

  // --- favorites and playlists ---

  fun toggleFavorite(track: Track) = libraryRepository.toggleFavorite(track)

  fun createPlaylist(name: String, firstTrack: Track? = null) {
    libraryRepository.createPlaylist(name, firstTrack)
  }

  /** P3: the queue screen's list (playing now, queued, then what follows) saved as a new playlist. */
  fun saveQueueAsPlaylist(name: String) {
    val tracks = com.example.data.playback.QueueSnapshot.tracks(musicRepository.currentTrack.value, playback.queue.value, playback.upcoming.value)
    if (tracks.isEmpty()) return
    val playlist = libraryRepository.createPlaylist(name, tracks)
    showNotice(Notice(context.getString(com.example.R.string.queue_saved_notice, tracks.size, playlist.name)))
  }

  fun renamePlaylist(id: String, name: String) = libraryRepository.renamePlaylist(id, name)

  fun deletePlaylist(id: String) = libraryRepository.deletePlaylist(id)

  fun addToPlaylist(playlistId: String, track: Track) {
    val playlist = playlists.value.firstOrNull { it.id == playlistId } ?: return
    if (track.id in playlist.trackIds) {
      showNotice(Notice(context.getString(com.example.R.string.library_notice_already_in_playlist, track.title, playlist.name)))
      return
    }
    libraryRepository.addToPlaylist(playlistId, track)
    showNotice(
      Notice(
        context.getString(com.example.R.string.library_notice_added_to_playlist, playlist.name),
        actionLabel = context.getString(com.example.R.string.player_notice_undo),
        action = { libraryRepository.removeFromPlaylist(playlistId, track.id) }
      )
    )
  }

  fun removeFromPlaylist(playlistId: String, trackId: String) =
    libraryRepository.removeFromPlaylist(playlistId, trackId)

  fun skipCuriositySpeech() = playback.skipCuriositySpeech()

  // ---- Audius (searched from the Início search bar; full tracks played by the app's own player) ----

  private val _audiusResults = MutableStateFlow<List<Track>>(emptyList())
  val audiusResults: StateFlow<List<Track>> = _audiusResults.asStateFlow()

  private val _audiusSearching = MutableStateFlow(false)
  val audiusSearching: StateFlow<Boolean> = _audiusSearching.asStateFlow()

  /** The last search got no answer from Audius (offline or Audius down), as opposed to no results. */
  private val _audiusOffline = MutableStateFlow(false)
  val audiusOffline: StateFlow<Boolean> = _audiusOffline.asStateFlow()

  private var audiusSearchJob: Job? = null

  init {
    // The old Spotify integration was removed: drop the encrypted store and key it may have left, once.
    viewModelScope.launch(Dispatchers.IO) {
      val prefs = context.getSharedPreferences("tarkas_prefs", android.content.Context.MODE_PRIVATE)
      if (prefs.getBoolean(KEY_LEGACY_CLEANED, false)) return@launch
      runCatching { context.deleteSharedPreferences("tarkas_secure") }
      runCatching {
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("tarkas_spotify_tokens")
      }
      prefs.edit().putBoolean(KEY_LEGACY_CLEANED, true).apply()
    }
    // YouTube Music and Spotify were removed in 1.14: their sign-in cookies and site data, kept by the
    // WebView the app no longer has, are deleted once, so no session is left behind on the device.
    viewModelScope.launch(Dispatchers.IO) {
      val prefs = context.getSharedPreferences("tarkas_prefs", android.content.Context.MODE_PRIVATE)
      if (prefs.getBoolean(KEY_WEB_CLEANED, false)) return@launch
      runCatching { java.io.File(context.filesDir.parentFile, "app_webview").deleteRecursively() }
      runCatching { java.io.File(context.cacheDir, "WebView").deleteRecursively() }
      prefs.edit().putBoolean(KEY_WEB_CLEANED, true).apply()
    }
  }

  /** Searches Audius as the user types in Início, without adding the results to the library. */
  fun searchAudiusOnline(query: String) {
    audiusSearchJob?.cancel()
    if (query.isBlank()) {
      _audiusResults.value = emptyList()
      _audiusSearching.value = false
      _audiusOffline.value = false
      return
    }
    audiusSearchJob = viewModelScope.launch {
      // Wait for a pause in typing so each keystroke is not a request.
      kotlinx.coroutines.delay(350)
      _audiusSearching.value = true
      try {
        _audiusResults.value = withContext(Dispatchers.IO) { AudiusApiService.searchTracks(query.trim()) }
        _audiusOffline.value = false
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        // Offline, or an answer that could not be read: never a crash.
        _audiusResults.value = emptyList()
        _audiusOffline.value = true
      } finally {
        _audiusSearching.value = false
      }
    }
  }

  /** Plays an Audius result; Next goes on through the rest of the results. */
  fun playAudiusResult(track: Track) = playFromList(track, _audiusResults.value)

  // ---- Jamendo (same search bar; Creative Commons music played in full by the app's own player) ----

  private val _jamendo = MutableStateFlow(OnlineSearchState(notConfigured = !JamendoApiService.isConfigured))
  val jamendo: StateFlow<OnlineSearchState> = _jamendo.asStateFlow()

  private var jamendoSearchJob: Job? = null

  /** Searches Jamendo as the user types in Início, without adding the results to the library. */
  fun searchJamendoOnline(query: String) {
    jamendoSearchJob?.cancel()
    if (query.isBlank() || !JamendoApiService.isConfigured) {
      _jamendo.value = OnlineSearchState(notConfigured = !JamendoApiService.isConfigured)
      return
    }
    jamendoSearchJob = viewModelScope.launch {
      delay(350)
      _jamendo.value = _jamendo.value.copy(searching = true)
      _jamendo.value = try {
        OnlineSearchState(results = JamendoApiService.searchTracks(query.trim()))
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: JamendoNotConfigured) {
        OnlineSearchState(notConfigured = true)
      } catch (e: Exception) {
        OnlineSearchState(offline = true)
      }
    }
  }

  /** Plays a Jamendo result; Next goes on through the rest of the results. */
  fun playJamendoResult(track: Track) = playFromList(track, _jamendo.value.results)

  // ---- MusicBrainz: "Sobre a faixa" ----

  private val _trackDetails = MutableStateFlow(TrackDetailsState())
  val trackDetails: StateFlow<TrackDetailsState> = _trackDetails.asStateFlow()

  private var detailsJob: Job? = null

  /** Looks [track] up on MusicBrainz for the "Sobre a faixa" sheet; a repeat for the same track is free. */
  fun loadTrackDetails(track: Track) {
    val current = _trackDetails.value
    if (current.trackId == track.id && (current.loading || current.info != null || current.notFound)) return
    detailsJob?.cancel()
    _trackDetails.value = TrackDetailsState(trackId = track.id, loading = true)
    detailsJob = viewModelScope.launch {
      _trackDetails.value = try {
        val info = MusicBrainzService.lookup(track.title, track.artist)
        TrackDetailsState(trackId = track.id, info = info, notFound = info == null)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        android.util.Log.w("TarkasViewModel", "MusicBrainz lookup failed", e)
        TrackDetailsState(trackId = track.id, offline = true)
      }
    }
  }


  // ---- Letras ----

  private val lyricsRepository = com.example.data.lyrics.LyricsRepository(context)
  private val _lyrics = MutableStateFlow(com.example.data.lyrics.LyricsState())
  val lyrics: StateFlow<com.example.data.lyrics.LyricsState> = _lyrics.asStateFlow()
  private var lyricsJob: Job? = null

  /** Looks for the lyrics of [track] (tag and .lrc beside the file); a repeat for the same song is free. */
  fun loadLyrics(track: Track) {
    val current = _lyrics.value
    if (current.trackId == track.id) return
    lyricsJob?.cancel()
    _lyrics.value = com.example.data.lyrics.LyricsState(trackId = track.id, loading = true)
    lyricsJob = viewModelScope.launch {
      val result = lyricsRepository.load(track)
      _lyrics.value = when (result) {
        is com.example.data.lyrics.LyricsRepository.Result.Found -> com.example.data.lyrics.LyricsState(trackId = track.id, lyrics = result.lyrics)
        com.example.data.lyrics.LyricsRepository.Result.NotOnDevice -> com.example.data.lyrics.LyricsState(trackId = track.id, notOnDevice = true)
        com.example.data.lyrics.LyricsRepository.Result.None -> com.example.data.lyrics.LyricsState(trackId = track.id)
      }
    }
  }

  fun openCuriositySettings() = navigate { it.open(Route.CuriositySettings) }

  fun closeCuriositySettings() = navigate { if (it.showingCuriosity) it.back() }

  fun updateCuriosityConfig(newConfig: com.example.data.model.CuriosityConfig) = playback.setCuriosityConfig(newConfig)

  fun testCuriosityVoice() {
    curiositySpeechManager.testVoiceSample(curiosityConfig.value)
  }

  // ---- Equalizer ----

  /** Changes the curve. Saved shortly after the last change, so dragging does not write on every frame. */
  fun updateEq(transform: (EqSettings) -> EqSettings) {
    val next = transform(audioEngineManager.eqSettings.value)
    audioEngineManager.applyEqSettings(next)
    val preset = _eqPresets.value.find { it.id == _activePresetId.value }
    _eqModified.value = preset == null || com.example.data.model.EqCompare.edited(next, preset.settings)
    eqSaveJob?.cancel()
    eqSaveJob = viewModelScope.launch {
      kotlinx.coroutines.delay(400)
      preferencesRepository.saveEqSettings(next)
    }
  }

  fun setEqEnabled(enabled: Boolean) = updateEq { it.copy(enabled = enabled) }

  /** Hold-to-compare: the player without the equalizer while [on]; nothing is saved. */
  fun compareWithoutEq(on: Boolean) = audioEngineManager.setBypass(on)

  /** Volume normalization: the mode and what it does to the playing track. */
  val loudness: StateFlow<com.example.data.audio.loudness.LoudnessUi> get() = audioEngineManager.loudness

  fun setNormalizationMode(mode: com.example.data.audio.loudness.NormalizationMode) =
    audioEngineManager.setNormalizationMode(mode)

  /** Edits a band of the mode in use (graphic or parametric). */
  fun updateEqBand(index: Int, transform: (EqBand) -> EqBand) = updateEq { s ->
    val bands = s.bands
    if (index !in bands.indices) s
    else s.copy(bands = bands.toMutableList().also { it[index] = transform(it[index]) })
  }

  /** The equalizer as it is, to put back with "Desfazer". */
  private inner class EqSnapshot {
    val settings = audioEngineManager.eqSettings.value
    val presetId = _activePresetId.value
    val modified = _eqModified.value
    val saved = userPresets()

    fun restore() {
      if (saved != userPresets()) storeUserPresets(saved)
      _activePresetId.value = presetId
      preferencesRepository.saveActiveProfileId(presetId)
      updateEq { settings }
      _eqModified.value = modified
    }
  }

  private fun offerEqUndo(text: String, snapshot: EqSnapshot) =
    showNotice(Notice(text, actionLabel = context.getString(com.example.R.string.audio_action_undo), action = { snapshot.restore() }))

  /** Loads a preset; edits not saved to the one in use are offered back with "Desfazer". */
  fun selectEqPreset(preset: EqPreset) {
    val snapshot = EqSnapshot()
    val discards = _eqModified.value && preset.id != _activePresetId.value
    loadEqPreset(preset)
    if (discards) offerEqUndo(context.getString(com.example.R.string.audio_notice_preset_loaded, preset.localizedName(context)), snapshot)
  }

  /** Loads a preset. The on/off switch stays as it is. */
  private fun loadEqPreset(preset: EqPreset) {
    val current = audioEngineManager.eqSettings.value
    _activePresetId.value = preset.id
    preferencesRepository.saveActiveProfileId(preset.id)
    // The fone correction is its own stage: changing the preset keeps it. The layout the preset does
    // not use stays remembered, so switching back finds the bands as they were.
    updateEq { current.withCurveOf(preset.settings) }
  }

  /** Back to flat in the mode in use: graphic faders at zero, or the parametric back to one band. */
  fun resetEq() {
    val snapshot = EqSnapshot()
    val wasParametric = audioEngineManager.eqSettings.value.parametric
    loadEqPreset(EqPresets.FLAT)
    if (wasParametric) updateEq { it.withLayout(true).copy(bands = EqSettings.DEFAULT_PARAM_BANDS) }
    offerEqUndo(context.getString(com.example.R.string.audio_notice_eq_reset), snapshot)
  }

  fun saveEqPresetAs(name: String) {
    val preset = EqPreset(
      id = "custom_${System.currentTimeMillis()}",
      name = name.trim().ifBlank { context.getString(com.example.R.string.audio_preset_default_name) },
      settings = audioEngineManager.eqSettings.value.userCurve,
      isCustom = true
    )
    storeUserPresets(userPresets() + preset)
    loadEqPreset(preset)
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
    val snapshot = EqSnapshot()
    val name = userPresets().firstOrNull { it.id == id }?.name
    storeUserPresets(userPresets().filterNot { it.id == id })
    if (_activePresetId.value == id) {
      // Keep the sound as it is; it simply no longer belongs to a saved preset.
      _activePresetId.value = EqPresets.FLAT.id
      _eqModified.value = true
    }
    if (name != null) offerEqUndo(context.getString(com.example.R.string.audio_notice_preset_deleted, name), snapshot)
  }

  private fun userPresets(): List<EqPreset> = _eqPresets.value.filter { it.isCustom }

  private fun storeUserPresets(list: List<EqPreset>) {
    preferencesRepository.saveUserPresets(list)
    _eqPresets.value = EqPresets.BUILT_IN + list
  }

  val surfaceStyle: StateFlow<SurfaceStyle> = settings.surfaceStyle

  fun setSurfaceStyle(style: SurfaceStyle) = settings.setSurfaceStyle(style)

  // ---- first-run setup and the terms ----

  /** Shown after the opening animation until finished, and again (terms step) when the terms change. */
  private val _showOnboarding = MutableStateFlow(
    !preferencesRepository.getOnboardingDone() ||
      preferencesRepository.getTermsVersionAccepted() < com.example.ui.legal.LegalTexts.TERMS_VERSION
  )
  val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

  /** True when the setup was done before and only the terms changed: just the terms step is shown. */
  val onlyTermsChanged: Boolean
    get() = preferencesRepository.getOnboardingDone() &&
      preferencesRepository.getTermsVersionAccepted() < com.example.ui.legal.LegalTexts.TERMS_VERSION

  fun acceptTerms() {
    preferencesRepository.saveTermsAccepted(com.example.ui.legal.LegalTexts.TERMS_VERSION, System.currentTimeMillis())
  }

  val termsAcceptedAt: Long get() = preferencesRepository.getTermsAcceptedAt()

  /** Ends the setup, but never while the current terms are not accepted. */
  fun finishOnboarding() {
    if (preferencesRepository.getTermsVersionAccepted() < com.example.ui.legal.LegalTexts.TERMS_VERSION) return
    preferencesRepository.saveOnboardingDone(true)
    _showOnboarding.value = false
  }

  // ---- storage and the user's data (Ajustes > Armazenamento / Privacidade) ----

  /** Cache the user can clear: cover images and the downloaded list of fones. Demo tracks are kept. */
  private fun clearableCacheFiles(): List<java.io.File> =
    (context.cacheDir.listFiles()?.filter { it.name != "tarkas_audio" } ?: emptyList()) +
      listOfNotNull(java.io.File(context.filesDir, "autoeq_index.md").takeIf { it.exists() })

  suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
    clearableCacheFiles().sumOf { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() }
  }

  fun clearCache(onDone: () -> Unit) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        coil.Coil.imageLoader(context).let { loader ->
          loader.memoryCache?.clear()
          loader.diskCache?.clear()
        }
        clearableCacheFiles().forEach { it.deleteRecursively() }
      }
      showNotice(Notice(context.getString(com.example.R.string.audio_notice_cache_cleared)))
      onDone()
    }
  }

  private val userDataBackup by lazy {
    com.example.data.backup.UserDataBackup(graph.database) { context.getSharedPreferences(it, android.content.Context.MODE_PRIVATE) }
  }

  /** Everything the app keeps about the user, as JSON (LGPD: access and portability; also what Restore reads). */
  suspend fun exportUserDataJson(): String =
    userDataBackup.export(
      com.example.BuildConfig.VERSION_NAME,
      java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())
    ).toString(2)

  fun writeExport(uri: android.net.Uri) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val text = exportUserDataJson()
          context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
      }
      showNotice(Notice(context.getString(if (ok) com.example.R.string.player_export_saved else com.example.R.string.player_export_failed)))
    }
  }

  /** Restores a file made by [writeExport]. A file that is not valid changes nothing; a valid one reopens the app. */
  fun restoreFrom(uri: android.net.Uri) {
    viewModelScope.launch {
      val failure = withContext(Dispatchers.IO) {
        try {
          val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext "the file could not be read"
          userDataBackup.restore(text)
          null
        } catch (e: com.example.data.backup.RestoreException) {
          e.reason
        } catch (e: java.io.IOException) {
          "the file could not be read"
        }
      }
      if (failure != null) {
        showNotice(Notice(context.getString(com.example.R.string.player_restore_failed, failure)))
        return@launch
      }
      showNotice(Notice(context.getString(com.example.R.string.player_restore_done)))
      // Every screen and the player read their data at start-up: reopen the process on the restored data.
      kotlinx.coroutines.delay(1200)
      val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
      if (launch != null) context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
      Runtime.getRuntime().exit(0)
    }
  }

  /**
   * Erases everything the app keeps on this device (LGPD art. 18: elimination). Android clears the
   * app's storage and closes it; the next launch starts from the first-run setup.
   */
  /** Saves the failure record (crashes, process exits, recovered errors) where the user chose. */
  fun writeDiagnostics(uri: android.net.Uri) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        runCatching {
          val text = com.example.data.diagnostics.Diagnostics.report(context)
          context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
      }
      showNotice(Notice(context.getString(if (ok) com.example.R.string.settings_diagnostics_saved else com.example.R.string.player_export_failed)))
    }
  }

  fun eraseAllData() {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.clearApplicationUserData()
  }

  /** "Refazer configuração inicial" in Sobre. */
  fun restartOnboarding() {
    preferencesRepository.saveOnboardingDone(false)
    _showOnboarding.value = true
  }

  /** "Reduzir transparência" in the app: every glass surface becomes solid. */
  val reduceTransparency: StateFlow<Boolean> = settings.reduceTransparency

  fun setReduceTransparency(reduce: Boolean) = settings.setReduceTransparency(reduce)

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
        _deviceSearch.value = _deviceSearch.value.copy(loading = false, sources = sources, total = deviceIndex.size, onlineOnly = deviceIndex.size - autoEq.bundledCount)
        searchDevices(_deviceSearch.value.query, _deviceSearch.value.source)
      } catch (e: Exception) {
        _deviceSearch.value = _deviceSearch.value.copy(loading = false, error = context.getString(com.example.R.string.audio_error_device_list))
      }
    }
  }

  fun searchDevices(query: String, source: String?) {
    _deviceSearch.update { it.copy(query = query, source = source) }
    deviceSearchJob?.cancel()
    deviceSearchJob = viewModelScope.launch {
      kotlinx.coroutines.delay(120)
      val index = deviceIndex
      // Searched off the main thread, written back on it: the other fields of the state change there too.
      val results = withContext(Dispatchers.Default) { com.example.data.autoeq.AutoEqParser.search(index, query, source) }
      _deviceSearch.update { it.copy(results = results) }
    }
  }

  /** Downloads the fone's curve and applies it as the correction stage. */
  fun applyDevice(entry: com.example.data.autoeq.AutoEqEntry, onDone: () -> Unit) {
    _deviceSearch.value = _deviceSearch.value.copy(applyingPath = entry.path, error = null)
    viewModelScope.launch {
      try {
        val correction = autoEq.correction(entry) ?: throw IllegalStateException("curva vazia")
        updateEq { it.copy(device = correction) }
        _outputSuggestion.value = null
        _deviceSearch.value = _deviceSearch.value.copy(applyingPath = null)
        onDone()
      } catch (e: Exception) {
        _deviceSearch.value = _deviceSearch.value.copy(applyingPath = null, error = context.getString(com.example.R.string.audio_error_device_curve, entry.name))
      }
    }
  }

  fun clearDevice() {
    updateEq { it.copy(device = null) }
    _outputSuggestion.value = null
  }

  /**
   * A fone with no correction chosen yet, whose name matches an AutoEq measurement: offered once.
   * Needs the AutoEq list (cached for a week, downloaded otherwise); offline, nothing is offered.
   */
  private fun suggestCorrection(out: com.example.data.audio.OutputDevice) {
    viewModelScope.launch {
      val index = runCatching { autoEq.index() }
        .onFailure { android.util.Log.i("TarkasViewModel", "No AutoEq list for a suggestion: ${it.javaClass.simpleName}") }
        .getOrNull() ?: return@launch
      val found = withContext(Dispatchers.Default) {
        com.example.data.audio.OutputRouting.suggestions(index, out.name)
      }
      android.util.Log.i("TarkasViewModel", "Suggestion for an output: ${found?.let { "${it.entries.size} model(s) for \"${it.query}\", exact=${it.exact}" } ?: "none"}")
      if (found == null) return@launch
      // Still on that fone, and still without a choice?
      if (audioEngineManager.output.value.key != out.key || profiles.hasChosen(out.key)) return@launch
      // The fone list keeps it for as long as this fone has no correction; the notice comes once.
      _outputSuggestion.value = found
      val offered = preferencesRepository.getSuggestedOutputs()
      if (out.key in offered) return@launch
      preferencesRepository.saveSuggestedOutputs(offered + out.key)
      val only = found.entries.singleOrNull()
      showNotice(
        if (found.exact && only != null) {
          Notice(context.getString(com.example.R.string.audio_notice_suggest_one, only.name, only.source), actionLabel = context.getString(com.example.R.string.audio_action_apply), action = { applyDevice(only) {} })
        } else {
          // Only part of the name matched, or several models: the user picks the right one.
          Notice(
            context.getString(com.example.R.string.audio_notice_suggest_many, found.query),
            actionLabel = context.getString(com.example.R.string.audio_action_choose),
            action = { openDevicePickerFor(found.query) }
          )
        }
      )
    }
  }

  private val _devicePickerRequest = MutableStateFlow<String?>(null)
  /** A search to open the fone list with (from a suggestion); cleared once the list opens. */
  val devicePickerRequest: StateFlow<String?> = _devicePickerRequest.asStateFlow()

  private fun openDevicePickerFor(query: String) {
    setTab(TarkasNavTab.AUDIO)
    _devicePickerRequest.value = query
  }

  fun devicePickerOpened() {
    _devicePickerRequest.value = null
  }

  /** Início: the user's own music before (true) or after (false) the online catalogue rows. */
  val homeMusicFirst: StateFlow<Boolean> = settings.homeMusicFirst

  fun setHomeMusicFirst(on: Boolean) = settings.setHomeMusicFirst(on)

  private val _waveformBar = MutableStateFlow(preferencesRepository.getWaveformBar())
  /** Advanced: draw the player progress as the track.s waveform when it is known. */
  val waveformBar: StateFlow<Boolean> = _waveformBar.asStateFlow()

  fun setWaveformBar(on: Boolean) {
    _waveformBar.value = on
    preferencesRepository.saveWaveformBar(on)
    if (on) graph.waveforms.prepare(deviceTracks.value) else graph.waveforms.cancel()
  }

  /** The playing track's 100 amplitudes while the setting is on and they are known; null otherwise. */
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val currentWaveform: StateFlow<ByteArray?> = combine(_waveformBar, musicRepository.currentTrack) { on, track -> if (on) track else null }
    .distinctUntilChanged { a, b -> a?.id == b?.id }
    .flatMapLatest { track -> if (track == null) kotlinx.coroutines.flow.flowOf(null) else graph.waveformSource.waveform(track) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val reduceMotion: StateFlow<Boolean> = settings.reduceMotion

  fun setReduceMotion(reduce: Boolean) = settings.setReduceMotion(reduce)

  fun setListAnimation(style: com.example.ui.theme.ListAnimation) = settings.setListAnimation(style)

  fun setThemeMode(mode: String) = settings.setThemeMode(mode)
  fun setSystemColors(on: Boolean) = settings.setSystemColors(on)

  // Replies run one at a time, in the order they were asked (see enqueueAssistantReply).
  private val assistantReplyMutex = Mutex()
  private var assistantRepliesPending = 0
  // Read aloud only for questions asked by voice (A13); created without starting the engine.
  private val assistantSpeaker = AssistantSpeaker(context)

  /** Stops the reading of a previous reply: a new question, typed or spoken, takes over the voice. */
  fun stopAssistantSpeech() = assistantSpeaker.stop()

  fun sendAssistantMessage(text: String, askedByVoice: Boolean = false) {
    if (text.isBlank()) return

    val userMsg = AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.USER,
      text = text
    )
    assistantSpeaker.stop()
    publishAssistantMessages(_assistantMessages.value + userMsg)
    enqueueAssistantReply(text, askedByVoice)
  }

  // "Try again" on a failed reply: the error line goes away and the same question is asked again.
  // The user's line is not repeated in the conversation.
  fun retryAssistantReply(errorMessageId: String) {
    val list = _assistantMessages.value
    val index = list.indexOfFirst { it.id == errorMessageId }
    if (index < 0) return
    val question = list.take(index).lastOrNull { it.sender == MessageSender.USER }?.text ?: return
    publishAssistantMessages(list.filterNot { it.id == errorMessageId })
    enqueueAssistantReply(question)
  }

  // Policy for a message sent while another is pending: it waits in line. Cancelling the earlier one
  // would drop a reply the user is still waiting for, and could stop a player command halfway.
  // Every message gets its answer, in order. "Thinking" stays on until the line is empty.
  private fun enqueueAssistantReply(text: String, askedByVoice: Boolean = false) {
    assistantRepliesPending++
    _isAssistantProcessing.value = true
    viewModelScope.launch {
      try {
        assistantReplyMutex.withLock {
          val reply = try {
            assistantEngine.processUserMessage(text, useGeminiIfAvailable = _assistantOnlineAi.value)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Diagnostics.record("assistant", "reply failed: unexpected ${e.javaClass.simpleName}")
            AssistantMessage(
              id = UUID.randomUUID().toString(),
              sender = MessageSender.ASSISTANT,
              text = "",
              failure = AssistantFailure.SERVER
            )
          }
          val failure = reply.failure
          val shown = if (failure == null) reply else reply.copy(text = context.getString(failure.textRes))
          publishAssistantMessages(_assistantMessages.value + shown)
          if (shouldSpeakReply(askedByVoice, shown)) assistantSpeaker.speak(speakableReply(shown.text))
        }
      } finally {
        // Runs on success, on failure and on cancellation, so "thinking" always ends.
        assistantRepliesPending--
        if (assistantRepliesPending == 0) _isAssistantProcessing.value = false
      }
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
  private val _rootAvailable = MutableStateFlow(false)
  val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()
  val rootPresent: Boolean get() = _rootAvailable.value

  init {
    // A file check, kept off the main thread while the first screen is being built.
    viewModelScope.launch { _rootAvailable.value = withContext(Dispatchers.IO) { RootShell.suPresent() } }
  }

  init {
    // The service applies the same curve the app uses.
    viewModelScope.launch { audioEngineManager.eqSettings.collect { GlobalEq.settings.value = it } }
    // The quick settings tile: its taps come here, and it is told when the equalizer turns on or off.
    viewModelScope.launch { GlobalEq.enabledRequests.collect { setEqEnabled(it) } }
    GlobalEq.appListening = true
    viewModelScope.launch {
      audioEngineManager.eqSettings.map { it.enabled }.distinctUntilChanged().collect {
        runCatching {
          android.service.quicksettings.TileService.requestListeningState(
            context, android.content.ComponentName(context, com.example.data.systemaudio.EqTileService::class.java)
          )
        }
      }
    }
    // Root on the output mix already equalizes this app's own player too: its own processor steps aside.
    viewModelScope.launch {
      GlobalEq.status.map { it.globalOutput }.distinctUntilChanged().collect { audioEngineManager.setCoveredByOutputMix(it) }
    }
    GlobalEq.loadedByApp = true
    applyEqReach(_eqReach.value)
  }

  fun setEqReach(reach: EqReach) {
    _eqReach.value = reach
    preferencesRepository.saveEqReach(reach.key)
    applyEqReach(reach, restart = true)
  }

  private fun applyEqReach(reach: EqReach, restart: Boolean = false) {
    GlobalEq.reach.value = reach
    if (reach == EqReach.APP) {
      GlobalEqService.stop(context)
    } else {
      // A change between ADB and root restarts the service (root asks for su on start); opening the app
      // only makes sure it runs, without tearing down effects that are already applied.
      if (restart) GlobalEqService.stop(context)
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

  // ---- Tarkas Spatial ----

  private val _spatialEnabled = MutableStateFlow(com.example.data.audio.spatial.SpatialControl.enabled)
  val spatialEnabled: StateFlow<Boolean> = _spatialEnabled.asStateFlow()

  private val _spatialPreset = MutableStateFlow(com.example.data.audio.spatial.SpatialControl.preset)
  val spatialPreset: StateFlow<com.example.data.audio.spatial.SpatialPreset> = _spatialPreset.asStateFlow()

  private val _spatialIntensity = MutableStateFlow(com.example.data.audio.spatial.SpatialControl.userIntensity.toFloat())
  val spatialIntensity: StateFlow<Float> = _spatialIntensity.asStateFlow()

  val spatialStatus: StateFlow<com.example.data.audio.spatial.SpatialStatus> = com.example.data.audio.spatial.SpatialControl.status

  fun setSpatialEnabled(enabled: Boolean) {
    _spatialEnabled.value = enabled
    applySpatial()
  }

  fun setSpatialPreset(preset: com.example.data.audio.spatial.SpatialPreset) {
    _spatialPreset.value = preset
    applySpatial()
  }

  private var spatialSaveJob: Job? = null

  /** A slider drag: only the target moves (no new tables); saved once the drag pauses. */
  fun setSpatialIntensity(intensity: Float) {
    _spatialIntensity.value = intensity
    com.example.data.audio.spatial.SpatialControl.setIntensity(intensity.toDouble())
    spatialSaveJob?.cancel()
    spatialSaveJob = viewModelScope.launch {
      delay(400)
      saveSpatial()
    }
  }

  private val _spatialTuning = MutableStateFlow(
    com.example.data.audio.spatial.SpatialPreset.entries.associateWith { preferencesRepository.getSpatialTuning(it.key) }
  )
  /** The hand-set values of each preset (only the ones that differ from the preset). */
  val spatialTuning: StateFlow<Map<com.example.data.audio.spatial.SpatialPreset, Map<com.example.data.audio.spatial.SpatialKnob, Double>>> =
    _spatialTuning.asStateFlow()

  private var tuningSaveJob: Job? = null

  /** A tuning slider: the sound follows at once, the value is saved once the drag pauses. */
  fun setSpatialKnob(preset: com.example.data.audio.spatial.SpatialPreset, knob: com.example.data.audio.spatial.SpatialKnob, value: Double) {
    val v = knob.clamp(value)
    val current = _spatialTuning.value[preset].orEmpty()
    // A value equal to the preset's own is not an override.
    val next = if (kotlin.math.abs(knob.read(preset.params) - v) < 1e-6) current - knob else current + (knob to v)
    updateSpatialTuning(preset, next)
  }

  /** Back to the preset's own values. */
  fun resetSpatialTuning(preset: com.example.data.audio.spatial.SpatialPreset) = updateSpatialTuning(preset, emptyMap())

  private fun updateSpatialTuning(preset: com.example.data.audio.spatial.SpatialPreset, values: Map<com.example.data.audio.spatial.SpatialKnob, Double>) {
    _spatialTuning.value = _spatialTuning.value + (preset to values)
    com.example.data.audio.spatial.SpatialControl.setTuning(preset, values)
    tuningSaveJob?.cancel()
    tuningSaveJob = viewModelScope.launch {
      delay(400)
      _spatialTuning.value.forEach { (p, v) -> preferencesRepository.saveSpatialTuning(p.key, v) }
    }
  }

  private fun applySpatial() {
    com.example.data.audio.spatial.SpatialControl.configure(_spatialEnabled.value, _spatialPreset.value, _spatialIntensity.value.toDouble())
    saveSpatial()
    syncHeadTracking()
  }

  // ---- Head tracking: the scene follows the head of headphones that have a sensor ----

  private val headTracker = com.example.data.audio.spatial.HeadTracker(application)
  private val _headTracking = MutableStateFlow(preferencesRepository.getHeadTracking())
  val headTracking: StateFlow<Boolean> = _headTracking.asStateFlow()
  val headStatus: StateFlow<com.example.data.audio.spatial.HeadTrackStatus> = headTracker.status

  fun setHeadTracking(on: Boolean) {
    _headTracking.value = on
    preferencesRepository.saveHeadTracking(on)
    syncHeadTracking()
  }

  /** The direction the listener faces now becomes the front of the scene. */
  fun recenterHead() = headTracker.recenter()

  /** It runs only while the spatial stage is on: without it there is no scene to turn. */
  private fun syncHeadTracking() = headTracker.setEnabled(_headTracking.value && _spatialEnabled.value)

  init { syncHeadTracking() }

  private fun saveSpatial() {
    preferencesRepository.saveSpatial(_spatialEnabled.value, _spatialPreset.value.key, _spatialIntensity.value)
  }

  // ---- Profile per output: what was set on an output comes back with it (the rules live in OutputProfileManager) ----

  /** The output the music goes to now. */
  val output: StateFlow<com.example.data.audio.OutputDevice> get() = audioEngineManager.output

  // Declared before the manager starts: its first decision can run at once and writes to it.
  private val _outputSuggestion = MutableStateFlow<com.example.data.audio.OutputRouting.Suggestion?>(null)
  /** What AutoEq has for the fone playing now, while it has no correction; null otherwise. */
  val outputSuggestion: StateFlow<com.example.data.audio.OutputRouting.Suggestion?> = _outputSuggestion.asStateFlow()

  private val profileHost = object : com.example.data.audio.OutputProfileHost {
    override val output get() = audioEngineManager.output
    override val changes: kotlinx.coroutines.flow.Flow<Unit>
      get() = kotlinx.coroutines.flow.merge(
        audioEngineManager.eqSettings.map { }, _activePresetId.map { },
        _spatialEnabled.map { }, _spatialPreset.map { }, _spatialIntensity.map { }
      )

    override fun current() = com.example.data.audio.OutputProfile(
      eq = audioEngineManager.eqSettings.value,
      spatial = com.example.data.audio.SpatialProfile(_spatialEnabled.value, _spatialPreset.value.key, _spatialIntensity.value),
      presetId = _activePresetId.value
    )

    override fun apply(profile: com.example.data.audio.OutputProfile) {
      profile.presetId?.takeIf { id -> _eqPresets.value.any { it.id == id } }?.let {
        _activePresetId.value = it
        preferencesRepository.saveActiveProfileId(it)
      }
      updateEq { profile.eq }
      val preset = com.example.data.audio.spatial.SpatialPreset.entries.firstOrNull { it.key == profile.spatial.presetKey }
      _spatialEnabled.value = profile.spatial.enabled
      if (preset != null) _spatialPreset.value = preset
      _spatialIntensity.value = profile.spatial.intensity
      applySpatial()
    }
  }

  private val profileStore = object : com.example.data.audio.OutputProfileStore {
    override fun profilesJson() = preferencesRepository.getOutputProfiles()
    override fun saveProfilesJson(json: String) = preferencesRepository.saveOutputProfiles(json)
    override fun legacyBindingsJson() = preferencesRepository.getOutputBindings()
    override fun auto() = preferencesRepository.getAutoOutputCorrection()
    override fun saveAuto(on: Boolean) = preferencesRepository.saveAutoOutputCorrection(on)
  }

  private val profiles: com.example.data.audio.OutputProfileManager = com.example.data.audio.OutputProfileManager(viewModelScope, profileHost, profileStore) { out, correctionChanged, announce ->
    _outputSuggestion.value = null
    // At launch the correction changes quietly; later, a notice says what changed.
    if (announce && correctionChanged) {
      val device = audioEngineManager.eqSettings.value.device
      showNotice(Notice(device?.let { context.getString(com.example.R.string.audio_notice_correction_on, it.name) } ?: context.getString(com.example.R.string.audio_notice_correction_off, out.name)))
    }
    if (out.isExternal && !profiles.hasChosen(out.key) && profiles.correctionOf(out.key) == null) suggestCorrection(out)
  }

  /** "Trocar com o fone": each output gets the curve, spatial and correction last set on it. */
  val autoOutputCorrection: StateFlow<Boolean> = profiles.auto

  fun setAutoOutputCorrection(on: Boolean) = profiles.setAuto(on)

  init {
    viewModelScope.launch {
      eqLoaded.await()
      profiles.start()
    }
  }

  // ---- the music on the device ----

  val deviceTracks: StateFlow<List<Track>> = musicRepository.deviceTracks

  /** Songs per folder on the device, and which folders are in the library. */
  val deviceFolders: StateFlow<Map<String, Int>> = musicRepository.deviceFolders
  val folderRules: StateFlow<com.example.data.library.FolderRules> = musicRepository.folderRules

  fun setFolderIncluded(folder: String, included: Boolean) {
    val rules = musicRepository.folderRules.value.with(folder, included)
    preferencesRepository.saveFolderRules(rules.encode())
    musicRepository.setFolderRules(rules)
  }

  private val _deviceScanning = MutableStateFlow(false)
  val deviceScanning: StateFlow<Boolean> = _deviceScanning.asStateFlow()

  /** Reads the device's music again (after the permission was granted, or when Biblioteca opens). */
  fun scanDeviceMusic() {
    if (_deviceScanning.value) return
    _deviceScanning.value = true
    viewModelScope.launch {
      try {
        musicRepository.scanDevice()
        graph.waveforms.retain(deviceTracks.value)
        if (_waveformBar.value) graph.waveforms.prepare(deviceTracks.value)
      } finally {
        _deviceScanning.value = false
      }
    }
  }

  init {
    if (hasMusicPermission()) scanDeviceMusic()
    viewModelScope.launch {
      deviceTracks.drop(1).collect { tracks ->
        graph.waveforms.retain(tracks)
        if (_waveformBar.value) graph.waveforms.prepare(tracks)
      }
    }
  }

  fun hasMusicPermission(): Boolean = com.example.data.library.MusicPermission.granted(context)

  /** Adds a file picked on the device and plays it like any other track of the library. */
  fun addLocalAudio(title: String, artist: String, uriString: String, durationMs: Long) {
    playTrack(musicRepository.addLocalTrack(title, artist, uriString, durationMs))
  }

  // ---- Jamendo's popular tracks, on Início ----

  private val _jamendoPopular = MutableStateFlow(TrendingState(notConfigured = !JamendoApiService.isConfigured))
  val jamendoPopular: StateFlow<TrendingState> = _jamendoPopular.asStateFlow()

  fun loadJamendoPopular() {
    if (!JamendoApiService.isConfigured || _jamendoPopular.value.loading) return
    _jamendoPopular.value = _jamendoPopular.value.copy(loading = true, offline = false)
    viewModelScope.launch {
      _jamendoPopular.value = try {
        TrendingState(tracks = JamendoApiService.getPopularTracks())
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: JamendoNotConfigured) {
        TrendingState(notConfigured = true)
      } catch (e: Exception) {
        TrendingState(tracks = _jamendoPopular.value.tracks, offline = true)
      }
    }
  }

  /** Plays a popular Jamendo track; Next goes on through the rest of the row. */
  fun playJamendoPopular(track: Track) = playFromList(track, _jamendoPopular.value.tracks)

  // ---- Audius trending, on Início ----

  private val _trending = MutableStateFlow(TrendingState())
  val trending: StateFlow<TrendingState> = _trending.asStateFlow()

  fun loadTrending() {
    if (_trending.value.loading) return
    _trending.value = _trending.value.copy(loading = true, offline = false)
    viewModelScope.launch {
      _trending.value = try {
        TrendingState(tracks = withContext(Dispatchers.IO) { AudiusApiService.getTrendingTracks() })
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        TrendingState(tracks = _trending.value.tracks, offline = true)
      }
    }
  }

  /** Plays a trending track; Next goes on through the rest of the row. */
  fun playTrending(track: Track) = playFromList(track, _trending.value.tracks)

  init {
    loadTrending()
    loadJamendoPopular()
  }

  // ---- permissions asked when they are needed ----

  private val _askNotifications = MutableStateFlow(false)
  /** True while the notification permission was never asked; the dialog waits for the opening animation. */
  val askNotifications: StateFlow<Boolean> = _askNotifications.asStateFlow()

  init {
    // Asked as soon as the app opens (once), because updates and downloads notify too.
    if (!preferencesRepository.getNotificationsAsked()) _askNotifications.value = true
  }

  fun notificationsAsked() {
    _askNotifications.value = false
    preferencesRepository.saveNotificationsAsked()
  }

  /** The microphone permission was just granted: the live spectrum can attach now. */
  fun spectrumAllowed() = audioEngineManager.refreshVisualizer()

  /** Turns the level meter off and ends its foreground service and screen capture. */
  fun stopSystemAudioCapture() {
    systemAudioCaptureManager.stopCapture()
    com.example.data.systemaudio.AudioCaptureService.stop(context)
  }

  override fun onCleared() {
    super.onCleared()
    headTracker.setEnabled(false)
    GlobalEq.appListening = false
    assistantSpeaker.shutdown()
    timerTickerJob?.cancel()
    // The static callback held this view model; a new one sets its own when it asks for capture again.
    com.example.data.systemaudio.AudioCaptureService.onMediaProjectionReady = null
    // The player is not stopped: it belongs to the process and the media session, and keeps playing.
    stopSystemAudioCapture()
  }

  private companion object {
    const val KEY_ROUTES = "nav_routes"
    const val KEY_LEGACY_CLEANED = "legacy_spotify_cleaned"
    const val KEY_WEB_CLEANED = "legacy_web_players_cleaned"
  }
}
