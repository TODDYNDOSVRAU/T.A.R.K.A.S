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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.ui.viewmodel.TarkasViewModel
import com.example.ui.theme.AppMotion
import com.example.ui.theme.expandFromOrigin
import com.example.ui.theme.opensPlayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

class MainActivity : ComponentActivity() {
  override fun onResume() {
    super.onResume()
    // Animation and blur follow the system settings, read again whenever the user comes back.
    AppMotion.readSystem(this)
  }

  /**
   * Connected to the media session while the app is on screen, which starts the playback service.
   * Once music plays the service keeps itself in the foreground, so playing goes on after this ends.
   */
  private var sessionConnection: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

  override fun onStart() {
    super.onStart()
    val token = androidx.media3.session.SessionToken(
      this, android.content.ComponentName(this, com.example.data.playback.PlaybackService::class.java)
    )
    sessionConnection = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
  }

  override fun onStop() {
    sessionConnection?.let { androidx.media3.session.MediaController.releaseFuture(it) }
    sessionConnection = null
    super.onStop()
  }

  override fun onDestroy() {
    // The hidden web views outlive the activity; take them off this window so it can be freed.
    com.example.data.web.WebSearchHost.release(this)
    com.example.data.web.WebPlayerHost.release(this)
    super.onDestroy()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    AppGraph.init(this)
    AppMotion.readSystem(this)
    // Must run before super.onCreate: swaps the splash theme for the normal one after the first frame.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // The hidden web views (search and player) need the window to exist.
    window.decorView.post {
      com.example.data.web.WebSearchHost.attach(this)
      com.example.data.web.WebPlayerHost.attach(this)
    }
    setContent {
      val viewModel: TarkasViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()

      // The opening animation plays once per launch (it survives rotation).
      var showBoot by rememberSaveable { mutableStateOf(true) }

      // Permissions are asked when they are needed, each with its reason: notifications the first
      // time something plays, the microphone from the equalizer's spectrum.
      // One launcher for both, told apart by the result, so a request never carries the other one.
      var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
      }
      val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { results ->
        if (Manifest.permission.POST_NOTIFICATIONS in results) viewModel.notificationsAsked()
        val granted = results[Manifest.permission.RECORD_AUDIO] ?: return@rememberLauncherForActivityResult
        micGranted = granted
        if (granted) {
          viewModel.spectrumAllowed()
        } else {
          viewModel.showNotice(
            com.example.ui.viewmodel.Notice(
              "Sem o microfone, o equalizador funciona igual, só sem o espectro ao vivo.",
              actionLabel = "Permissões",
              action = {
                startActivity(
                  android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", packageName, null))
                )
              }
            )
          )
        }
      }
      val askNotifications by viewModel.askNotifications.collectAsState()
      val notificationsMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      if (askNotifications && !notificationsMissing) {
        LaunchedEffect(Unit) { viewModel.notificationsAsked() }
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

      val accent by viewModel.accent.collectAsState()
      val showOnboarding by viewModel.showOnboarding.collectAsState()
      TarkasTheme(themeMode = themeMode, accent = accent) {
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
          },
          spectrumAllowed = micGranted,
          onAllowSpectrum = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
        )
        // First-run setup (and the terms again when they change), right after the opening animation.
        androidx.compose.animation.AnimatedVisibility(
          visible = showOnboarding && !showBoot,
          enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
          exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(260)) +
            androidx.compose.animation.scaleOut(targetScale = 1.04f)
        ) {
          val reduceMotion by viewModel.reduceMotion.collectAsState()
          val surfaceStyle by viewModel.surfaceStyle.collectAsState()
          val curiosityConfig by viewModel.curiosityConfig.collectAsState()
          val onlyTerms = remember { viewModel.onlyTermsChanged }
          OnboardingScreen(
            onlyTerms = onlyTerms,
            themeMode = themeMode,
            onThemeMode = { viewModel.setThemeMode(it) },
            surfaceStyle = surfaceStyle,
            onSurfaceStyle = { viewModel.setSurfaceStyle(it) },
            accent = accent,
            onAccent = { viewModel.setAccent(it) },
            reduceMotion = reduceMotion,
            onReduceMotion = { viewModel.setReduceMotion(it) },
            curiosityConfig = curiosityConfig,
            onCuriosityConfig = { viewModel.updateCuriosityConfig(it) },
            onAcceptTerms = { viewModel.acceptTerms() },
            onFinish = { viewModel.finishOnboarding() }
          )
        }
        if (showBoot) {
          BootScreen(onFinished = { showBoot = false })
        }
        // Asked once, after the opening animation, the first time something plays.
        if (askNotifications && notificationsMissing && !showBoot && !showOnboarding) {
          androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.notificationsAsked() },
            title = { Text("Controles na notificação") },
            text = {
              Text(
                "Com a permissão de notificações, a música aparece na tela bloqueada e na barra de notificações, " +
                  "com tocar, pausar e pular, e a curiosidade da faixa quando houver uma."
              )
            },
            confirmButton = {
              androidx.compose.material3.TextButton(
                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                modifier = Modifier.testTag("notifications_allow")
              ) { Text("Continuar") }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(onClick = { viewModel.notificationsAsked() }) { Text("Agora não") }
            }
          )
        }
        }
      }
    }
  }
}

@Composable
fun TarkasAppContent(
  viewModel: TarkasViewModel,
  onRequestSystemAudioCapture: () -> Unit,
  spectrumAllowed: Boolean = true,
  onAllowSpectrum: () -> Unit = {}
) {
  val currentTab by viewModel.currentTab.collectAsState()
  val tracks by viewModel.musicRepository.tracks.collectAsState()
  val currentTrack by viewModel.musicRepository.currentTrack.collectAsState()
  val isPlaying by viewModel.isPlaying.collectAsState()
  // Values that change many times a second are kept as State and read only where they are drawn,
  // so a tick does not recompose the whole app.
  val currentPosMs = viewModel.positionMs.collectAsState()
  val durationMs = viewModel.durationMs.collectAsState()
  val eqSettings by viewModel.eqSettings.collectAsState()
  val eqPresets by viewModel.eqPresets.collectAsState()
  val activePresetId by viewModel.activePresetId.collectAsState()
  val eqModified by viewModel.eqModified.collectAsState()
  val capabilities by viewModel.audioEngineManager.capabilities.collectAsState()
  val activeCuriosity by viewModel.activeCuriosity.collectAsState()
  val assistantMessages by viewModel.assistantMessages.collectAsState()
  val isAssistantProcessing by viewModel.isAssistantProcessing.collectAsState()
  val activeTimers by viewModel.activeTimers.collectAsState()
  val notice by viewModel.notice.collectAsState()
  val trending by viewModel.trending.collectAsState()
  val audiusOffline by viewModel.audiusOffline.collectAsState()
  val openQueueRequest by viewModel.openQueueRequest.collectAsState()
  val themeMode by viewModel.themeMode.collectAsState()
  val surfaceStyle by viewModel.surfaceStyle.collectAsState()
  val reduceMotion by viewModel.reduceMotion.collectAsState()
  val deviceSearch by viewModel.deviceSearch.collectAsState()
  val reduceTransparency by viewModel.reduceTransparency.collectAsState()
  val systemAudioStatus by viewModel.systemAudioStatus.collectAsState()
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
  val youtubeResults by viewModel.youtubeResults.collectAsState()
  val youtubeSearching by viewModel.youtubeSearching.collectAsState()
  val youtubeOffline by viewModel.youtubeOffline.collectAsState()
  val spotifyResults by viewModel.spotifyResults.collectAsState()
  val spotifySearching by viewModel.spotifySearching.collectAsState()
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
      viewModel.goBack()
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing
  ) { innerPadding ->
    // The bottom bar floats over the content (so glass styles have something to blur). The content
    // keeps its own bottom space through LocalBarInset. Every page draws under the status bar: tab
    // pages pad their title below it, and the player page uses the space for its artwork.
    val layoutDirection = LocalLayoutDirection.current
    val contentPadding = PaddingValues(
      start = innerPadding.calculateStartPadding(layoutDirection),
      end = innerPadding.calculateEndPadding(layoutDirection),
      top = 0.dp,
      bottom = 0.dp
    )
    val hazeState = remember { HazeState() }
    // With blur and transparency reduced (in the system or in Ajustes), there is no blur source, so
    // the floating bars fall back to solid surfaces.
    val blurAllowed = !AppMotion.systemBlurOff && !reduceTransparency
    // Liquid glass records the screen content and refracts it; only while that style is on.
    val liquidOn = blurAllowed && surfaceStyle == com.example.ui.theme.SurfaceStyle.LIQUID &&
      com.example.ui.theme.liquidGlassSupported
    val pageColor = MaterialTheme.colorScheme.background
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
      onDraw = {
        drawRect(pageColor)
        drawContent()
      }
    )
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barInset = with(LocalDensity.current) { barHeightPx.toDp() }
    // Scrolling a page down draws the tab capsule back; scrolling up (or changing tab) restores it.
    var navCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(currentTab) { navCollapsed = false }
    val navScroll = remember {
      object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(
          available: androidx.compose.ui.geometry.Offset,
          source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
        ): androidx.compose.ui.geometry.Offset {
          if (available.y < -8f) navCollapsed = true else if (available.y > 8f) navCollapsed = false
          return androidx.compose.ui.geometry.Offset.Zero
        }
      }
    }

    CompositionLocalProvider(
      LocalSurfaceStyle provides surfaceStyle,
      LocalHazeState provides if (blurAllowed) hazeState else null,
      com.example.ui.theme.LocalBackdrop provides if (liquidOn) backdrop else null,
      LocalBarInset provides barInset
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .then(if (blurAllowed) Modifier.hazeSource(hazeState) else Modifier)
        .then(if (liquidOn) Modifier.layerBackdrop(backdrop) else Modifier)
        .nestedScroll(navScroll)
    ) {
      val screenKey: Any = when {
        isShowingCuriositySettings -> "curiosity"
        else -> currentTab
      }
      val reducedMotion = com.example.ui.theme.rememberReducedMotion()
      // Each page keeps its saved state (list scroll, open playlist, search) while another page
      // is shown, so going back from the player lands exactly where the user left the list.
      val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
      AnimatedContent(
        targetState = screenKey,
        transitionSpec = { screenTransition(initialState, targetState, reducedMotion) },
        label = "screen"
      ) { screen ->
      // The large title belongs to each tab page, so it moves with the page in every transition
      // (and stays put under the player while that grows over it).
      val tabPage = screen is TarkasNavTab && screen != TarkasNavTab.MUSIC
      Column(
        modifier = Modifier
          .fillMaxSize()
          .then(
            when (screen) {
              // The growing shape is filled with the track's colour, darkened like the player's
              // background, so the page appears to grow out of the card instead of a black hole.
              TarkasNavTab.MUSIC -> Modifier.expandFromOrigin(
                this@AnimatedContent,
                currentTrack?.let { androidx.compose.ui.graphics.lerp(Color(it.colorHex), Color.Black, 0.62f) } ?: Color.Black,
                reducedMotion
              )
              "curiosity" -> Modifier.statusBarsPadding()
              else -> Modifier
            }
          )
      ) {
      if (tabPage) {
        // The title's gear button is a glass control, so it needs the chosen surface style too.
        TarkasLargeTitle(
          title = (screen as TarkasNavTab).label,
          onOpenSettings = { viewModel.setTab(TarkasNavTab.SETTINGS) }
        )
      }
      Box(modifier = Modifier.weight(1f)) {
      pageStates.SaveableStateProvider(screen.toString()) {
      if (screen == "curiosity") {
        MusicalCuriositySettingsScreen(
          config = curiosityConfig,
          isSpeaking = isAiSpeakingCuriosity,
          currentSpokenText = currentCuriositySpeechText,
          speechStage = viewModel.curiositySpeechStage.collectAsState().value,
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
            audiusOffline = audiusOffline,
            onSearchAudiusOnline = { q -> viewModel.searchAudiusOnline(q) },
            // Opening a song plays it and grows the player out of the card that was tapped.
            onPlayAudiusResult = { track ->
              viewModel.playAudiusResult(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            youtubeResults = youtubeResults,
            youtubeSearching = youtubeSearching,
            youtubeOffline = youtubeOffline,
            onSearchYouTubeOnline = { q -> viewModel.searchYouTubeOnline(q) },
            onPlayYouTubeResult = { track ->
              viewModel.playYouTubeResult(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            spotifyResults = spotifyResults,
            spotifySearching = spotifySearching,
            onSearchSpotifyOnline = { q -> viewModel.searchSpotifyOnline(q) },
            onPlaySpotifyResult = { track ->
              viewModel.playSpotifyResult(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            onPlayTrack = { track ->
              viewModel.playTrack(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            trending = trending,
            onRetryTrending = { viewModel.loadTrending() },
            onPlayTrending = { track ->
              viewModel.playTrending(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            onOpenLibrary = { viewModel.setTab(TarkasNavTab.LIBRARY) }
          )
        }

        TarkasNavTab.MUSIC -> {
          MusicScreen(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionMs = { currentPosMs.value },
            durationMs = { durationMs.value },
            activeCuriosity = activeCuriosity,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNextTrack = { viewModel.playNextTrack() },
            onPreviousTrack = { viewModel.playPreviousTrack() },
            onSeekTo = { pos -> viewModel.seekTo(pos) },
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
            onOpenQueue = { viewModel.openQueue() },
            sleepTimer = viewModel.sleepTimer.collectAsState().value,
            onSetSleepTimer = { m -> viewModel.setSleepTimer(m) },
            onSleepAtEndOfTrack = { viewModel.setSleepAtEndOfTrack() },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            onClose = { viewModel.goBack() }
          )
        }

        TarkasNavTab.LIBRARY -> {
          LibraryScreen(
            playlists = playlists,
            favoriteTracks = favoriteTracks,
            savedTracks = savedTracks,
            allTracks = tracks,
            deviceTracks = viewModel.deviceTracks.collectAsState().value,
            deviceScanning = viewModel.deviceScanning.collectAsState().value,
            hasMusicPermission = viewModel.hasMusicPermission(),
            onScanDevice = { viewModel.scanDeviceMusic() },
            queue = queue,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onPlayFromList = { track, list ->
              viewModel.playFromList(track, list)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            onShuffleList = { list -> viewModel.shuffleFrom(list) },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddLocalTrack = { title, artist, uri, dur ->
              viewModel.addLocalAudio(title, artist, uri, dur)
            },
            onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
            onRemoveFromPlaylist = { id, trackId -> viewModel.removeFromPlaylist(id, trackId) },
            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
            onPlayQueueItem = { index ->
              viewModel.playFromQueue(index)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            onRemoveQueueItem = { index -> viewModel.removeFromQueue(index) },
            onClearQueue = { viewModel.clearQueue() },
            openQueue = openQueueRequest,
            onQueueOpened = { viewModel.queueOpened() }
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
            deviceSearch = deviceSearch,
            onOpenDevicePicker = { viewModel.openDevicePicker() },
            onSearchDevices = { q, source -> viewModel.searchDevices(q, source) },
            onApplyDevice = { entry, done -> viewModel.applyDevice(entry, done) },
            onClearDevice = { viewModel.clearDevice() },
            onSetEnabled = { on -> viewModel.setEqEnabled(on) },
            onUpdateEq = { transform -> viewModel.updateEq(transform) },
            onUpdateBand = { index, transform -> viewModel.updateEqBand(index, transform) },
            onSelectPreset = { preset -> viewModel.selectEqPreset(preset) },
            onSavePresetAs = { name -> viewModel.saveEqPresetAs(name) },
            onOverwritePreset = { viewModel.overwriteActiveEqPreset() },
            onRenamePreset = { id, name -> viewModel.renameEqPreset(id, name) },
            onDeletePreset = { id -> viewModel.deleteEqPreset(id) },
            onReset = { viewModel.resetEq() },
            onRequestSystemAudioCapture = onRequestSystemAudioCapture,
            onStopSystemAudioCapture = { viewModel.stopSystemAudioCapture() },
            spectrumAllowed = spectrumAllowed,
            onAllowSpectrum = onAllowSpectrum,
            spatial = com.example.ui.screens.SpatialUi(
              enabled = viewModel.spatialEnabled.collectAsState().value,
              preset = viewModel.spatialPreset.collectAsState().value,
              intensity = viewModel.spatialIntensity.collectAsState().value,
              status = viewModel.spatialStatus.collectAsState().value
            ),
            spatialActions = remember {
              com.example.ui.screens.SpatialActions(
                onEnabled = { viewModel.setSpatialEnabled(it) },
                onPreset = { viewModel.setSpatialPreset(it) },
                onIntensity = { viewModel.setSpatialIntensity(it) }
              )
            }
          )
        }

        TarkasNavTab.ASSISTANT -> {
          AssistantScreen(
            messages = assistantMessages,
            isProcessing = isAssistantProcessing,
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            activeTimers = activeTimers,
            onSendMessage = { text -> viewModel.sendAssistantMessage(text) },
            onCancelTimer = { id -> viewModel.cancelTimer(id) }
          )
        }

        TarkasNavTab.SETTINGS -> {
          SettingsScreen(
            themeMode = themeMode,
            capabilities = capabilities,
            onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
            curiosityConfig = curiosityConfig,
            onOpenCuriositySettings = { viewModel.openCuriositySettings() },
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            surfaceStyle = surfaceStyle,
            onSurfaceStyleChange = { style -> viewModel.setSurfaceStyle(style) },
            reduceMotion = reduceMotion,
            onReduceMotionChange = { viewModel.setReduceMotion(it) },
            reduceTransparency = reduceTransparency,
            onReduceTransparencyChange = { viewModel.setReduceTransparency(it) },
            accent = viewModel.accent.collectAsState().value,
            onAccentChange = { viewModel.setAccent(it) },
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onOpenAudio = { viewModel.setTab(TarkasNavTab.AUDIO) },
            cacheSizeBytes = { viewModel.cacheSizeBytes() },
            onClearCache = { done -> viewModel.clearCache(done) },
            onExportData = { uri -> viewModel.writeExport(uri) },
            onEraseAllData = { viewModel.eraseAllData() },
            onSignOutWeb = { viewModel.signOutOfWebServices() },
            onRestartOnboarding = { viewModel.restartOnboarding() },
            termsAcceptedAt = viewModel.termsAcceptedAt
          )
        }
      }
      }
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

        // A short message (a track that could not play, a permission left off), over the mini player.
        // It goes away by itself after a few seconds, or when its action is used.
        var shownNotice by remember { mutableStateOf(notice) }
        if (notice != null) shownNotice = notice
        notice?.let { n ->
          LaunchedEffect(n.id) {
            kotlinx.coroutines.delay(if (n.actionLabel != null) 6000L else 3500L)
            viewModel.dismissNotice(n.id)
          }
        }
        AnimatedVisibility(visible = notice != null && !isShowingCuriositySettings) {
          val n = shownNotice
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .glassPill(cornerRadius = 30.dp)
              .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
              .heightIn(min = 40.dp)
              .testTag("app_notice"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = n?.text ?: "",
              fontSize = 14.sp,
              lineHeight = 19.sp,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
            )
            if (n?.actionLabel != null) {
              Spacer(modifier = Modifier.width(8.dp))
              com.example.ui.components.GlassChip(
                label = n.actionLabel,
                selected = true,
                onClick = {
                  viewModel.dismissNotice(n.id)
                  n.action?.invoke()
                }
              )
            }
          }
        }

        // Frosted Mini player shown docked above bottom nav when not on full Music screen
        // It fades in as the closing player lands on it, and steps aside at once when the player
        // grows out of it. The last track is kept so the capsule does not empty while leaving.
        var lastTrack by remember { mutableStateOf(currentTrack) }
        if (currentTrack != null) lastTrack = currentTrack
        AnimatedVisibility(
          visible = currentTab != TarkasNavTab.MUSIC && currentTrack != null,
          enter = if (com.example.ui.theme.rememberReducedMotion()) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 140)) +
              androidx.compose.animation.scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f)),
          exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
        ) {
          MiniPlayerBar(
            track = lastTrack,
            isPlaying = isPlaying,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.playNextTrack() },
            onPrevious = { viewModel.playPreviousTrack() },
            progress = {
              val d = durationMs.value
              if (d > 0) currentPosMs.value.toFloat() / d else 0f
            },
            onClick = { viewModel.setTab(TarkasNavTab.MUSIC) },
            modifier = Modifier
              .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.mini = it.boundsInWindow() }
              .opensPlayer()
          )
        }

        TarkasBottomNav(
          currentTab = currentTab,
          onTabSelected = { tab ->
            if (isShowingCuriositySettings) viewModel.closeCuriositySettings()
            viewModel.setTab(tab)
          },
          collapsed = navCollapsed && currentTab != TarkasNavTab.MUSIC
        )
       }
      }
    }
    }
  }
}
