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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

  override fun onCreate(savedInstanceState: Bundle?) {
    AppGraph.init(this)
    AppMotion.readSystem(this)
    // Must run before super.onCreate: swaps the splash theme for the normal one after the first frame.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: TarkasViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

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
              // The notice outlives this activity (it sits in the view model across rotation), so its
              // action holds only the application context, never the activity.
              action = openAppSettings(applicationContext)
            )
          )
        }
      }
      val askNotifications by viewModel.askNotifications.collectAsStateWithLifecycle()
      val notificationsMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      if (askNotifications && !notificationsMissing) {
        LaunchedEffect(Unit) { viewModel.notificationsAsked() }
      }

      // MediaProjection launcher: reads other apps' sound for the spectrum
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

      val accent by viewModel.accent.collectAsStateWithLifecycle()
      val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
      TarkasTheme(themeMode = themeMode, accent = accent) {
        CompositionLocalProvider(LocalAccent provides accent) {
        Box(modifier = Modifier.fillMaxSize()) {
        TarkasAppContent(
          viewModel = viewModel,
          // Under the first-run setup the app is hidden from screen readers, so focus stays on the setup.
          modifier = if (showOnboarding && !showBoot) Modifier.clearAndSetSemantics {} else Modifier,
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
        // First-run setup (and the terms again when they change). It waits under the opening animation,
        // already opaque, so the opening dissolves straight into it and the app never shows between them.
        androidx.compose.animation.AnimatedVisibility(
          visible = showOnboarding,
          enter = androidx.compose.animation.EnterTransition.None,
          exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(260)) +
            androidx.compose.animation.scaleOut(targetScale = 1.04f)
        ) {
          val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
          val surfaceStyle by viewModel.surfaceStyle.collectAsStateWithLifecycle()
          val curiosityConfig by viewModel.curiosityConfig.collectAsStateWithLifecycle()
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
}

/** The accent the user picked, for the screens that show the choice (it is collected once, at the root). */
private val LocalAccent = androidx.compose.runtime.staticCompositionLocalOf { com.example.ui.theme.AccentColor.ICE }

/** Opens this app's page in the system settings, from any context (no activity is held). */
private fun openAppSettings(context: Context): () -> Unit = {
  context.startActivity(
    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      .setData(android.net.Uri.fromParts("package", context.packageName, null))
      .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
  )
}

@Composable
fun TarkasAppContent(
  viewModel: TarkasViewModel,
  modifier: Modifier = Modifier,
  onRequestSystemAudioCapture: () -> Unit,
  spectrumAllowed: Boolean = true,
  onAllowSpectrum: () -> Unit = {}
) {
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val tracks by viewModel.musicRepository.tracks.collectAsStateWithLifecycle()
  val currentTrack by viewModel.musicRepository.currentTrack.collectAsStateWithLifecycle()
  val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
  // Values that change many times a second are kept as State and read only where they are drawn,
  // so a tick does not recompose the whole app.
  val currentPosMs = viewModel.positionMs.collectAsStateWithLifecycle()
  val durationMs = viewModel.durationMs.collectAsStateWithLifecycle()
  val eqSettings by viewModel.eqSettings.collectAsStateWithLifecycle()
  val eqPresets by viewModel.eqPresets.collectAsStateWithLifecycle()
  val activePresetId by viewModel.activePresetId.collectAsStateWithLifecycle()
  val eqModified by viewModel.eqModified.collectAsStateWithLifecycle()
  val capabilities by viewModel.audioEngineManager.capabilities.collectAsStateWithLifecycle()
  val activeCuriosity by viewModel.activeCuriosity.collectAsStateWithLifecycle()
  val assistantMessages by viewModel.assistantMessages.collectAsStateWithLifecycle()
  val isAssistantProcessing by viewModel.isAssistantProcessing.collectAsStateWithLifecycle()
  val activeTimers by viewModel.activeTimers.collectAsStateWithLifecycle()
  val notice by viewModel.notice.collectAsStateWithLifecycle()
  val trending by viewModel.trending.collectAsStateWithLifecycle()
  val audiusOffline by viewModel.audiusOffline.collectAsStateWithLifecycle()
  val openQueueRequest by viewModel.openQueueRequest.collectAsStateWithLifecycle()
  val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
  val surfaceStyle by viewModel.surfaceStyle.collectAsStateWithLifecycle()
  val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
  val deviceSearch by viewModel.deviceSearch.collectAsStateWithLifecycle()
  val reduceTransparency by viewModel.reduceTransparency.collectAsStateWithLifecycle()
  val systemAudioStatus by viewModel.systemAudioStatus.collectAsStateWithLifecycle()
  val spectrumBars = viewModel.audioEngineManager.spectrumBars.collectAsStateWithLifecycle()
  val activeApps by viewModel.activeApps.collectAsStateWithLifecycle()
  val favoriteIds by viewModel.favorites.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val savedTracks by viewModel.savedTracks.collectAsStateWithLifecycle()
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val isShuffle by viewModel.shuffle.collectAsStateWithLifecycle()
  val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
  val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()

  val isShowingCuriositySettings by viewModel.isShowingCuriositySettings.collectAsStateWithLifecycle()
  val eqReach by viewModel.eqReach.collectAsStateWithLifecycle()
  val globalEqStatus by viewModel.globalEqStatus.collectAsStateWithLifecycle()
  val audiusResults by viewModel.audiusResults.collectAsStateWithLifecycle()
  val audiusSearching by viewModel.audiusSearching.collectAsStateWithLifecycle()
  val jamendo by viewModel.jamendo.collectAsStateWithLifecycle()
  val jamendoPopular by viewModel.jamendoPopular.collectAsStateWithLifecycle()
  // Full-screen pages opened from Ajustes, shown over the tabs.
  val overlayOpen = isShowingCuriositySettings
  val curiosityConfig by viewModel.curiosityConfig.collectAsStateWithLifecycle()
  val isAiSpeakingCuriosity by viewModel.isAiSpeakingCuriosity.collectAsStateWithLifecycle()
  val currentCuriositySpeechText by viewModel.currentCuriositySpeechText.collectAsStateWithLifecycle()
  val curiositySpeechStage by viewModel.curiositySpeechStage.collectAsStateWithLifecycle()
  val reducedChrome = com.example.ui.theme.rememberReducedMotion()
  var settingsSubPage by remember { mutableStateOf(false) }
  LaunchedEffect(currentTab) { if (currentTab != TarkasNavTab.SETTINGS) settingsSubPage = false }

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
    modifier = modifier.fillMaxSize(),
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
              // The growing shape takes the colour read from the artwork (PlayerOrigin.tint, set by the
              // player); black until a cover was read, never the catalogue colour, which can be wrong.
              TarkasNavTab.MUSIC -> Modifier.expandFromOrigin(this@AnimatedContent, Color.Black, reducedMotion)
              "curiosity" -> Modifier.statusBarsPadding()
              else -> Modifier
            }
          )
      ) {
      // A sub-page of Ajustes (Sobre, the legal texts) draws its own title.
      if (tabPage && !(screen == TarkasNavTab.SETTINGS && settingsSubPage)) {
        // Ajustes opens from the title bar of Início only, as an account button does in Apple apps;
        // the other pages keep their title clean.
        TarkasLargeTitle(
          title = (screen as TarkasNavTab).label,
          onOpenSettings = { viewModel.setTab(TarkasNavTab.SETTINGS) },
          showSettings = screen == TarkasNavTab.HOME
        )
      }
      Box(modifier = Modifier.weight(1f)) {
      pageStates.SaveableStateProvider(screen.toString()) {
      if (screen == "curiosity") {
        MusicalCuriositySettingsScreen(
          config = curiosityConfig,
          isSpeaking = isAiSpeakingCuriosity,
          currentSpokenText = currentCuriositySpeechText,
          speechStage = curiositySpeechStage,
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
            jamendo = jamendo,
            onSearchJamendoOnline = { q -> viewModel.searchJamendoOnline(q) },
            onPlayJamendoResult = { track ->
              viewModel.playJamendoResult(track)
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            jamendoPopular = jamendoPopular,
            onRetryJamendoPopular = { viewModel.loadJamendoPopular() },
            onPlayJamendoPopular = { track ->
              viewModel.playJamendoPopular(track)
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
            sleepTimer = viewModel.sleepTimer.collectAsStateWithLifecycle().value,
            onSetSleepTimer = { m -> viewModel.setSleepTimer(m) },
            onSleepAtEndOfTrack = { viewModel.setSleepAtEndOfTrack() },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            trackDetails = viewModel.trackDetails.collectAsStateWithLifecycle().value,
            onLoadTrackDetails = { track -> viewModel.loadTrackDetails(track) },
            onClose = { viewModel.goBack() }
          )
        }

        TarkasNavTab.LIBRARY -> {
          LibraryScreen(
            playlists = playlists,
            favoriteTracks = favoriteTracks,
            savedTracks = savedTracks,
            allTracks = tracks,
            deviceTracks = viewModel.deviceTracks.collectAsStateWithLifecycle().value,
            deviceScanning = viewModel.deviceScanning.collectAsStateWithLifecycle().value,
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
            rootPresent = viewModel.rootAvailable.collectAsStateWithLifecycle().value,
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
              enabled = viewModel.spatialEnabled.collectAsStateWithLifecycle().value,
              preset = viewModel.spatialPreset.collectAsStateWithLifecycle().value,
              intensity = viewModel.spatialIntensity.collectAsStateWithLifecycle().value,
              status = viewModel.spatialStatus.collectAsStateWithLifecycle().value
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
            onListAnimationChange = { viewModel.setListAnimation(it) },
            reduceTransparency = reduceTransparency,
            onReduceTransparencyChange = { viewModel.setReduceTransparency(it) },
            accent = LocalAccent.current,
            onAccentChange = { viewModel.setAccent(it) },
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onSetRepeatMode = { viewModel.setRepeatMode(it) },
            onSubPageChange = { settingsSubPage = it },
            onOpenAudio = { viewModel.setTab(TarkasNavTab.AUDIO) },
            cacheSizeBytes = { viewModel.cacheSizeBytes() },
            onClearCache = { done -> viewModel.clearCache(done) },
            onExportData = { uri -> viewModel.writeExport(uri) },
            onEraseAllData = { viewModel.eraseAllData() },
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
        // It goes away by itself after a few seconds (longer when the user relies on accessibility
        // services), or when its action is used. Its clock waits while a full-screen page hides it.
        var shownNotice by remember { mutableStateOf(notice) }
        if (notice != null) shownNotice = notice
        val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
        val noticeVisible = notice != null && !isShowingCuriositySettings
        notice?.let { n ->
          LaunchedEffect(n.id, noticeVisible) {
            if (!noticeVisible) return@LaunchedEffect
            val base = if (n.actionLabel != null) 6000L else 3500L
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
              base, containsIcons = false, containsText = true, containsControls = n.actionLabel != null
            ) ?: base
            kotlinx.coroutines.delay(timeout)
            viewModel.dismissNotice(n.id)
          }
        }
        AnimatedVisibility(visible = noticeVisible) {
          val n = shownNotice
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .glassPill(cornerRadius = 30.dp)
              .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
              .heightIn(min = 40.dp)
              .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
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

        // The full player covers the tab bar, as in Apple Music; it slides back when the player closes.
        AnimatedVisibility(
          visible = currentTab != TarkasNavTab.MUSIC,
          enter = if (reducedChrome) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 80)) +
              androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 500f)) { it / 2 },
          exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
        ) {
          TarkasBottomNav(
            currentTab = currentTab,
            onTabSelected = { tab ->
              if (isShowingCuriositySettings) viewModel.closeCuriositySettings()
              viewModel.selectTab(tab)
            },
            collapsed = navCollapsed
          )
        }
       }
      }
    }
    }
  }
}
