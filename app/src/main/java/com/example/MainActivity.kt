package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TarkasBottomNav
import com.example.ui.components.TarkasTopBar
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.MusicalCuriositySettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.ui.viewmodel.TarkasViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: TarkasViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()

      // Request runtime permissions if required (POST_NOTIFICATIONS and RECORD_AUDIO)
      val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { /* Handled gracefully */ }

      LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
          permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissionsToRequest.isNotEmpty()) {
          permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
      }

      // MediaProjection launcher for system audio extraction (Spotify & YouTube Music)
      val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
      ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
          try {
            com.example.data.systemaudio.AudioCaptureService.onMediaProjectionReady = { mp ->
              viewModel.startSystemAudioCapture(mp)
            }
            com.example.data.systemaudio.AudioCaptureService.start(
              this@MainActivity,
              result.resultCode,
              result.data!!
            )
          } catch (e: Exception) {
            android.util.Log.e("MainActivity", "AudioCaptureService start failed, falling back to Spotify connection mode", e)
            viewModel.simulateExternalSpotifyConnection()
          }
        } else {
          // If rejected or cancelled, connect simulation mode
          viewModel.simulateExternalSpotifyConnection()
        }
      }

      TarkasTheme(themeMode = themeMode) {
        TarkasAppContent(
          viewModel = viewModel,
          onRequestSystemAudioCapture = {
            val intent = viewModel.systemAudioCaptureManager.createScreenCaptureIntent()
            if (intent != null) {
              mediaProjectionLauncher.launch(intent)
            } else {
              viewModel.simulateExternalSpotifyConnection()
            }
          }
        )
      }
    }
  }
}

@Composable
fun TarkasAppContent(
  viewModel: TarkasViewModel,
  onRequestSystemAudioCapture: () -> Unit
) {
  val currentTab by viewModel.currentTab.collectAsState()
  val tracks by viewModel.musicRepository.tracks.collectAsState()
  val currentTrack by viewModel.musicRepository.currentTrack.collectAsState()
  val isPlaying by viewModel.audioEngineManager.isPlaying.collectAsState()
  val currentPosMs by viewModel.audioEngineManager.currentPositionMs.collectAsState()
  val durationMs by viewModel.audioEngineManager.durationMs.collectAsState()
  val waveformEnergy by viewModel.audioEngineManager.waveformEnergy.collectAsState()
  val activeProfile by viewModel.audioEngineManager.activeProfile.collectAsState()
  val allProfiles by viewModel.allProfiles.collectAsState()
  val isEffectEnabled by viewModel.audioEngineManager.isEffectEnabled.collectAsState()
  val capabilities by viewModel.audioEngineManager.capabilities.collectAsState()
  val activeCuriosity by viewModel.activeCuriosity.collectAsState()
  val assistantMessages by viewModel.assistantMessages.collectAsState()
  val isAssistantProcessing by viewModel.isAssistantProcessing.collectAsState()
  val activeTimers by viewModel.activeTimers.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val themeMode by viewModel.themeMode.collectAsState()
  val systemAudioStatus by viewModel.systemAudioStatus.collectAsState()
  val isSearchingAudius by viewModel.isSearchingAudius.collectAsState()
  val spectrumBars by viewModel.audioEngineManager.spectrumBars.collectAsState()
  val allCuriosities = viewModel.curiosityRepository.getAllCuriosities()

  val isShowingCuriositySettings by viewModel.isShowingCuriositySettings.collectAsState()
  val curiosityConfig by viewModel.curiosityConfig.collectAsState()
  val isAiSpeakingCuriosity by viewModel.isAiSpeakingCuriosity.collectAsState()
  val currentCuriositySpeechText by viewModel.currentCuriositySpeechText.collectAsState()
  val curiositySpeechStage by viewModel.curiositySpeechStage.collectAsState()

  // Hardware / gesture back button handling
  BackHandler(enabled = isShowingCuriositySettings || currentTab != TarkasNavTab.HOME) {
    if (isShowingCuriositySettings) {
      viewModel.closeCuriositySettings()
    } else {
      viewModel.setTab(TarkasNavTab.HOME)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TarkasTopBar(
        currentTab = currentTab,
        activeRouteName = if (systemAudioStatus.isCapturing) "SISTEMA (${systemAudioStatus.detectedApp?.take(10) ?: "SPOTIFY"})" else capabilities.activeRouteName,
        onOpenSettings = { viewModel.setTab(TarkasNavTab.SETTINGS) }
      )
    },
    bottomBar = {
      Column {
        // Floating AI Curiosity Speech Banner
        AnimatedVisibility(
          visible = isAiSpeakingCuriosity &&
            !currentCuriositySpeechText.isNullOrBlank() &&
            curiosityConfig.displayMode == com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER
        ) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Brush.radialGradient(listOf(AppleMusicPink, AppleMusicPurple))),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (curiositySpeechStage == "intro") "🎙️ IA narrando curiosidade antes da música..." else "🎙️ Curiosidade falada pela IA...",
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  color = AppleMusicPink
                )
                Text(
                  text = currentCuriositySpeechText ?: "",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Spacer(modifier = Modifier.width(8.dp))

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable { viewModel.skipCuriositySpeech() }
              ) {
                Text(
                  text = "Pular",
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
          }
        }

        // Frosted Mini player shown docked above bottom nav when not on full Music screen
        if (currentTab != TarkasNavTab.MUSIC && currentTrack != null) {
          MiniPlayerBar(
            track = currentTrack,
            isPlaying = isPlaying,
            waveformEnergy = waveformEnergy,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.playNextTrack() },
            onClick = { viewModel.setTab(TarkasNavTab.MUSIC) }
          )
        }

        TarkasBottomNav(
          currentTab = currentTab,
          onTabSelected = { tab ->
            if (isShowingCuriositySettings) viewModel.closeCuriositySettings()
            viewModel.setTab(tab)
          }
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (isShowingCuriositySettings) {
        MusicalCuriositySettingsScreen(
          config = curiosityConfig,
          isSpeaking = isAiSpeakingCuriosity,
          currentSpokenText = currentCuriositySpeechText,
          onConfigChange = { viewModel.updateCuriosityConfig(it) },
          onTestVoice = { viewModel.testCuriosityVoice() },
          onStopVoice = { viewModel.skipCuriositySpeech() },
          onBack = { viewModel.closeCuriositySettings() }
        )
      } else {
      when (currentTab) {
        TarkasNavTab.HOME -> {
          HomeScreen(
            currentTrack = currentTrack,
            tracks = tracks,
            isPlaying = isPlaying,
            waveformEnergy = waveformEnergy,
            activeCuriosity = activeCuriosity,
            allCuriosities = allCuriosities,
            onPlayTrack = { track -> viewModel.playTrack(track) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNextTrack = { viewModel.playNextTrack() },
            onPreviousTrack = { viewModel.playPreviousTrack() },
            onNavigateToTab = { tab -> viewModel.setTab(tab) },
            onSearchAudius = { q -> viewModel.searchAudius(q) },
            onAddLocalTrack = { title, artist, uri, dur ->
              viewModel.addLocalAudio(title, artist, uri, dur)
            }
          )
        }

        TarkasNavTab.MUSIC -> {
          MusicScreen(
            currentTrack = currentTrack,
            tracks = tracks,
            isPlaying = isPlaying,
            currentPositionMs = currentPosMs,
            durationMs = durationMs,
            waveformEnergy = waveformEnergy,
            activeCuriosity = activeCuriosity,
            isSearchingAudius = isSearchingAudius,
            onPlayTrack = { track -> viewModel.playTrack(track) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNextTrack = { viewModel.playNextTrack() },
            onPreviousTrack = { viewModel.playPreviousTrack() },
            onSeekTo = { pos -> viewModel.audioEngineManager.seekTo(pos) },
            onAddLocalTrack = { title, artist, uri, dur ->
              viewModel.addLocalAudio(title, artist, uri, dur)
            },
            onSearchAudius = { q -> viewModel.searchAudius(q) },
            onLoadAudiusTrending = { viewModel.loadAudiusTrending() }
          )
        }

        TarkasNavTab.AUDIO -> {
          AudioScreen(
            capabilities = capabilities,
            activeProfile = activeProfile,
            allProfiles = allProfiles,
            isEffectEnabled = isEffectEnabled,
            systemAudioStatus = systemAudioStatus,
            isPlaying = isPlaying,
            spectrumBars = spectrumBars,
            onSelectProfile = { profile -> viewModel.selectProfile(profile) },
            onSaveCustomProfile = { name -> viewModel.saveCustomProfile(name) },
            onUpdateBandLevel = { band, level ->
              viewModel.audioEngineManager.updateCustomBand(band, level)
            },
            onResetAllBandsToZero = { viewModel.resetAllBandsToZero() },
            onUpdateBassBoost = { strength ->
              viewModel.audioEngineManager.updateCustomBassBoost(strength)
            },
            onUpdateVirtualizer = { strength ->
              viewModel.audioEngineManager.updateCustomVirtualizer(strength)
            },
            onToggleEffectsEnabled = { enabled ->
              viewModel.audioEngineManager.setEffectsEnabled(enabled)
            },
            onRequestSystemAudioCapture = onRequestSystemAudioCapture
          )
        }

        TarkasNavTab.ASSISTANT -> {
          AssistantScreen(
            messages = assistantMessages,
            isProcessing = isAssistantProcessing,
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            activeTimers = activeTimers,
            reminders = reminders,
            onSendMessage = { text -> viewModel.sendAssistantMessage(text) },
            onCancelTimer = { id -> viewModel.cancelTimer(id) },
            curiosityConfig = curiosityConfig,
            onOpenCuriositySettings = { viewModel.openCuriositySettings() }
          )
        }

        TarkasNavTab.SETTINGS -> {
          SettingsScreen(
            themeMode = themeMode,
            capabilities = capabilities,
            onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
            onBackToHome = { viewModel.setTab(TarkasNavTab.HOME) },
            curiosityConfig = curiosityConfig,
            onOpenCuriositySettings = { viewModel.openCuriositySettings() }
          )
        }
      }
      }
    }
  }
}
