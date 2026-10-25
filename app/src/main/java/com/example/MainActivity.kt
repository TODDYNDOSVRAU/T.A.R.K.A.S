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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.LocalSurfaceStyle
import com.example.ui.theme.glassPill
import com.example.ui.theme.screenTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.example.ui.components.BootScreen
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.setValue
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
import com.example.ui.components.TarkasLargeTitle
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
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
    // Must run before super.onCreate: swaps the splash theme for the normal one after the first frame.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: TarkasViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()

      // Request runtime permissions if required (POST_NOTIFICATIONS and RECORD_AUDIO)
      val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { /* Handled gracefully */ }

      // The opening animation plays once per launch (it survives rotation).
      var showBoot by rememberSaveable { mutableStateOf(true) }

      // Asked only after the opening animation, so the system dialogs never cover it.
      LaunchedEffect(showBoot) {
        if (showBoot) return@LaunchedEffect
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
            android.util.Log.e("MainActivity", "AudioCaptureService start failed", e)
            viewModel.markSystemCaptureUnavailable("Não foi possível iniciar o serviço de captura de áudio.")
          }
        } else {
          viewModel.markSystemCaptureUnavailable("Permissão de captura negada. O medidor continua desligado.")
        }
      }

      // Status and navigation bar icons must follow the app theme, not only the system theme.
      val isDarkApp = when (themeMode) {
        "amoled", "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      SideEffect {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightNavigationBars = !isDarkApp
      }

      TarkasTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
        TarkasAppContent(
          viewModel = viewModel,
          onRequestSystemAudioCapture = {
            val intent = viewModel.systemAudioCaptureManager.createScreenCaptureIntent()
            if (intent != null) {
              mediaProjectionLauncher.launch(intent)
            } else {
              viewModel.markSystemCaptureUnavailable("Este aparelho não oferece captura de áudio do sistema.")
            }
          }
        )
        if (showBoot) {
          BootScreen(onFinished = { showBoot = false })
        }
        }
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
  // Values that change many times a second are kept as State and read only where they are drawn,
  // so a tick does not recompose the whole app.
  val currentPosMs = viewModel.audioEngineManager.currentPositionMs.collectAsState()
  val durationMs = viewModel.audioEngineManager.durationMs.collectAsState()
  val eqSettings by viewModel.eqSettings.collectAsState()
  val eqPresets by viewModel.eqPresets.collectAsState()
  val activePresetId by viewModel.activePresetId.collectAsState()
  val eqModified by viewModel.eqModified.collectAsState()
  val capabilities by viewModel.audioEngineManager.capabilities.collectAsState()
  val activeCuriosity by viewModel.activeCuriosity.collectAsState()
  val assistantMessages by viewModel.assistantMessages.collectAsState()
  val isAssistantProcessing by viewModel.isAssistantProcessing.collectAsState()
  val activeTimers by viewModel.activeTimers.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val themeMode by viewModel.themeMode.collectAsState()
  val surfaceStyle by viewModel.surfaceStyle.collectAsState()
  // One drifting light for every liquid glass surface (off for the other styles and with reduced motion).
  val glassLight = com.example.ui.theme.rememberGlassLight(
    active = surfaceStyle == com.example.ui.theme.SurfaceStyle.LIQUID && !com.example.ui.theme.rememberReducedMotion()
  )
  val systemAudioStatus by viewModel.systemAudioStatus.collectAsState()
  val isSearchingAudius by viewModel.isSearchingAudius.collectAsState()
  val spectrumBars = viewModel.audioEngineManager.spectrumBars.collectAsState()
  val activeApps by viewModel.activeApps.collectAsState()
  val favoriteIds by viewModel.favorites.collectAsState()
  val playlists by viewModel.playlists.collectAsState()
  val savedTracks by viewModel.savedTracks.collectAsState()
  val queue by viewModel.queue.collectAsState()
  val isShuffle by viewModel.shuffle.collectAsState()
  val repeatMode by viewModel.repeatMode.collectAsState()
  val favoriteTracks = favoriteIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } ?: savedTracks[id] }

  val isShowingCuriositySettings by viewModel.isShowingCuriositySettings.collectAsState()
  val eqReach by viewModel.eqReach.collectAsState()
  val globalEqStatus by viewModel.globalEqStatus.collectAsState()
  val audiusResults by viewModel.audiusResults.collectAsState()
  val audiusSearching by viewModel.audiusSearching.collectAsState()
  // Full-screen pages opened from Ajustes, shown over the tabs.
  val overlayOpen = isShowingCuriositySettings
  val curiosityConfig by viewModel.curiosityConfig.collectAsState()
  val isAiSpeakingCuriosity by viewModel.isAiSpeakingCuriosity.collectAsState()
  val currentCuriositySpeechText by viewModel.currentCuriositySpeechText.collectAsState()
  val curiositySpeechStage by viewModel.curiositySpeechStage.collectAsState()

  // Status bar icons follow the app theme, except on the Now Playing page, which is always a dark tint.
  val hostView = LocalView.current
  val isDarkTheme = when (themeMode) {
    "amoled", "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }
  val statusOverDarkPage = currentTab == TarkasNavTab.MUSIC && !overlayOpen
  SideEffect {
    val activityWindow = (hostView.context as? android.app.Activity)?.window ?: return@SideEffect
    WindowCompat.getInsetsController(activityWindow, hostView).isAppearanceLightStatusBars =
      !(isDarkTheme || statusOverDarkPage)
  }

  // Hardware / gesture back button handling
  BackHandler(enabled = overlayOpen || currentTab != TarkasNavTab.HOME) {
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
      // The full player has its own chrome (grabber + artwork), like the Apple Music Now Playing sheet
      if (currentTab != TarkasNavTab.MUSIC && !overlayOpen) {
        // The title's gear button is a glass control, so it needs the chosen surface style too.
        CompositionLocalProvider(
          LocalSurfaceStyle provides surfaceStyle,
          com.example.ui.theme.LocalGlassLight provides glassLight
        ) {
          TarkasLargeTitle(
            title = currentTab.label,
            onOpenSettings = { viewModel.setTab(TarkasNavTab.SETTINGS) }
          )
        }
      }
    }
  ) { innerPadding ->
    // The bottom bar floats over the content (so glass styles have something to blur). The content
    // keeps its own bottom space through LocalBarInset, and the player page draws under the status bar.
    val layoutDirection = LocalLayoutDirection.current
    val playerPage = currentTab == TarkasNavTab.MUSIC && !overlayOpen
    val contentPadding = PaddingValues(
      start = innerPadding.calculateStartPadding(layoutDirection),
      end = innerPadding.calculateEndPadding(layoutDirection),
      top = if (playerPage) 0.dp else innerPadding.calculateTopPadding(),
      bottom = 0.dp
    )
    val hazeState = remember { HazeState() }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barInset = with(LocalDensity.current) { barHeightPx.toDp() }

    CompositionLocalProvider(
      LocalSurfaceStyle provides surfaceStyle,
      com.example.ui.theme.LocalGlassLight provides glassLight,
      LocalHazeState provides hazeState,
      LocalBarInset provides barInset
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .hazeSource(hazeState)
    ) {
      val screenKey: Any = when {
        isShowingCuriositySettings -> "curiosity"
        else -> currentTab
      }
      val reducedMotion = com.example.ui.theme.rememberReducedMotion()
      AnimatedContent(
        targetState = screenKey,
        transitionSpec = { screenTransition(initialState, targetState, reducedMotion) },
        label = "screen"
      ) { screen ->
      if (screen == "curiosity") {
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
      when (screen as TarkasNavTab) {
        TarkasNavTab.HOME -> {
          HomeScreen(
            currentTrack = currentTrack,
            tracks = tracks,
            isPlaying = isPlaying,
            favoriteIds = favoriteIds,
            playlists = playlists,
            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
            onPlayNext = { track -> viewModel.playNext(track) },
            onAddToQueue = { track -> viewModel.addToQueue(track) },
            onAddToPlaylist = { id, track -> viewModel.addToPlaylist(id, track) },
            onCreatePlaylist = { name, track -> viewModel.createPlaylist(name, track) },
            audiusResults = audiusResults,
            audiusSearching = audiusSearching,
            onSearchAudiusOnline = { q -> viewModel.searchAudiusOnline(q) },
            onPlayAudiusResult = { track -> viewModel.playAudiusResult(track) },
            onPlayTrack = { track -> viewModel.playTrack(track) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNextTrack = { viewModel.playNextTrack() },
            onPreviousTrack = { viewModel.playPreviousTrack() },
            onNavigateToTab = { tab -> viewModel.setTab(tab) }
          )
        }

        TarkasNavTab.MUSIC -> {
          MusicScreen(
            currentTrack = currentTrack,
            tracks = tracks,
            isPlaying = isPlaying,
            currentPositionMs = { currentPosMs.value },
            durationMs = { durationMs.value },
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
            onLoadAudiusTrending = { viewModel.loadAudiusTrending() },
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            favoriteIds = favoriteIds,
            playlists = playlists,
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
            onPlayNext = { track -> viewModel.playNext(track) },
            onAddToQueue = { track -> viewModel.addToQueue(track) },
            onAddToPlaylist = { id, track -> viewModel.addToPlaylist(id, track) },
            onCreatePlaylist = { name, track -> viewModel.createPlaylist(name, track) },
            onOpenQueue = { viewModel.setTab(TarkasNavTab.LIBRARY) }
          )
        }

        TarkasNavTab.LIBRARY -> {
          LibraryScreen(
            playlists = playlists,
            favoriteTracks = favoriteTracks,
            savedTracks = savedTracks,
            allTracks = tracks,
            queue = queue,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onPlayFromList = { track, list -> viewModel.playFromList(track, list) },
            onShuffleList = { list -> viewModel.shuffleFrom(list) },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddLocalTrack = { title, artist, uri, dur ->
              viewModel.addLocalAudio(title, artist, uri, dur)
            },
            onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
            onRemoveFromPlaylist = { id, trackId -> viewModel.removeFromPlaylist(id, trackId) },
            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
            onPlayQueueItem = { index -> viewModel.playFromQueue(index) },
            onRemoveQueueItem = { index -> viewModel.removeFromQueue(index) },
            onClearQueue = { viewModel.clearQueue() }
          )
        }

        TarkasNavTab.AUDIO -> {
          AudioScreen(
            capabilities = capabilities,
            eqSettings = eqSettings,
            presets = eqPresets,
            activePresetId = activePresetId,
            eqModified = eqModified,
            systemAudioStatus = systemAudioStatus,
            isPlaying = isPlaying,
            spectrumBars = { spectrumBars.value },
            activeApps = activeApps,
            onRefreshActiveApps = { viewModel.refreshActiveApps() },
            eqReach = eqReach,
            globalEqStatus = globalEqStatus,
            rootPresent = viewModel.rootPresent,
            onSetEqReach = { viewModel.setEqReach(it) },
            onSetEnabled = { on -> viewModel.setEqEnabled(on) },
            onUpdateEq = { transform -> viewModel.updateEq(transform) },
            onUpdateBand = { index, transform -> viewModel.updateEqBand(index, transform) },
            onSelectPreset = { preset -> viewModel.selectEqPreset(preset) },
            onSavePresetAs = { name -> viewModel.saveEqPresetAs(name) },
            onOverwritePreset = { viewModel.overwriteActiveEqPreset() },
            onRenamePreset = { id, name -> viewModel.renameEqPreset(id, name) },
            onDeletePreset = { id -> viewModel.deleteEqPreset(id) },
            onReset = { viewModel.resetEq() },
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
            onOpenCuriositySettings = { viewModel.openCuriositySettings() },
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            surfaceStyle = surfaceStyle,
            onSurfaceStyleChange = { style -> viewModel.setSurfaceStyle(style) }
          )
        }
      }
      }
      }
    }
      // Fades the content into the page colour behind the floating capsules, so text never runs
      // legibly under or below them. Drawn outside the blur source, so the glass still sees the content.
      val scrimColor = if (currentTab == TarkasNavTab.MUSIC) Color.Black else MaterialTheme.colorScheme.background
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(barInset + 36.dp)
          .background(
            Brush.verticalGradient(
              0f to scrimColor.copy(alpha = 0f),
              0.35f to scrimColor.copy(alpha = 0.9f),
              1f to scrimColor.copy(alpha = 0.97f)
            )
          )
      )
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .onSizeChanged { barHeightPx = it.height }
          .imePadding()
          .navigationBarsPadding()
          .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       com.example.ui.theme.TarkasDarkOverride(enabled = currentTab == TarkasNavTab.MUSIC) {
        // Floating AI Curiosity Speech Banner. Curiosities appear only on the player page (and in
        // notifications), never on the other tabs.
        AnimatedVisibility(
          visible = currentTab == TarkasNavTab.MUSIC &&
            !isShowingCuriositySettings &&
            isAiSpeakingCuriosity &&
            !currentCuriositySpeechText.isNullOrBlank() &&
            curiosityConfig.displayMode == com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER
        ) {
          // A floating glass capsule, like the mini player under it.
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .glassPill(cornerRadius = 30.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (curiositySpeechStage == "intro") "Curiosidade antes da música" else "Curiosidade da faixa",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = currentCuriositySpeechText ?: "",
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Spacer(modifier = Modifier.width(8.dp))

              com.example.ui.components.GlassChip(
                label = "Pular",
                onClick = { viewModel.skipCuriositySpeech() }
              )
            }
          }
        }

        // Frosted Mini player shown docked above bottom nav when not on full Music screen
        if (currentTab != TarkasNavTab.MUSIC && currentTrack != null) {
          MiniPlayerBar(
            track = currentTrack,
            isPlaying = isPlaying,
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
    }
    }
  }
}
