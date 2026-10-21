package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.assistant.AssistantEngine
import com.example.data.audio.AudioEngineManager
import com.example.data.model.AssistantMessage
import com.example.data.model.AudioProfile
import com.example.data.model.Curiosity
import com.example.data.model.LocalReminder
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.Track
import com.example.data.notification.MusicNotificationManager
import com.example.data.notification.NotificationActionReceiver
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.systemaudio.SystemAudioCaptureManager
import com.example.data.systemaudio.SystemAudioStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class TarkasNavTab(val label: String) {
  HOME("Início"),
  MUSIC("Música"),
  AUDIO("Áudio"),
  ASSISTANT("Assistente"),
  SETTINGS("Ajustes")
}

class TarkasViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext
  val musicRepository = MusicRepository(context)
  val curiosityRepository = CuriosityRepository()
  val audioEngineManager = AudioEngineManager(context)
  val preferencesRepository = PreferencesRepository(context)
  val systemAudioCaptureManager = SystemAudioCaptureManager(context)
  val curiositySpeechManager = com.example.data.assistant.CuriositySpeechManager(context)

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
    }
  )

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
          _activeCuriosity.value = curiosityRepository.getCuriosityForTrack(track.id)
          updateSystemNotification()
        }
      }
    }

    // Monitor isPlaying changes for notification
    viewModelScope.launch {
      audioEngineManager.isPlaying.collect {
        updateSystemNotification()
      }
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

  fun playTrack(track: Track) {
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
    if (config.enabled && (config.timing == com.example.data.model.CuriosityTiming.END_OF_TRACK || config.timing == com.example.data.model.CuriosityTiming.BOTH)) {
      // Outro curiosity spoken at the end of the track
      val cur = _activeCuriosity.value ?: curiosityRepository.getCuriosityForTrack(completedTrack)
      curiositySpeechManager.speakCuriosity(completedTrack, cur, isIntro = false, config = config) {
        playNextTrack()
      }
    } else {
      playNextTrack()
    }
  }

  fun togglePlayPause() {
    val current = musicRepository.currentTrack.value
    if (!audioEngineManager.isPlaying.value && audioEngineManager.currentPositionMs.value == 0L && current != null) {
      playTrack(current)
    } else {
      audioEngineManager.togglePlayPause()
    }
    updateSystemNotification()
  }

  fun playNextTrack() {
    curiositySpeechManager.stopSpeech()
    val next = musicRepository.selectNextTrack()
    if (next != null) {
      playTrack(next)
    }
  }

  fun playPreviousTrack() {
    curiositySpeechManager.stopSpeech()
    val prev = musicRepository.selectPreviousTrack()
    if (prev != null) {
      playTrack(prev)
    }
  }

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
      text = "Analisei **${track.title}** (${track.artist}):\n\n${cur.shortTrivia}\n\n*Fontes: ${cur.sources.firstOrNull() ?: "Arquivo Tarkas"}*"
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

  fun simulateExternalSpotifyConnection() {
    systemAudioCaptureManager.simulateExternalTrackDetection(
      appName = "Spotify",
      title = "Roundabout",
      artist = "Yes"
    )
    // Research curiosity for detected track
    _assistantMessages.value = _assistantMessages.value + AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = "Fluxo de áudio externo detectado: **Roundabout** (Yes, álbum Fragile 1971). Composta por Jon Anderson e Steve Howe em uma van durante viagem pelas colinas escocesas, a faixa ficou famosa pelo uso revolucionário do violão clássico gravado com microfone de fita e a icônica linha de baixo Rickenbacker 4001."
    )
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
    systemAudioCaptureManager.stopCapture()
    MusicNotificationManager.clearNotification(context)
  }
}
