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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.LocalSurfaceStyle
import com.example.ui.theme.glassPill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.draggable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TarkasBottomNav
import com.example.ui.components.TarkasLargeTitle
import com.example.ui.screens.AssistantPlayback
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AudioScreen
import com.example.ui.screens.player.PrefetchCarouselCovers
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.library.LibraryActions
import com.example.ui.screens.library.LibraryState
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.player.PlayerActions
import com.example.ui.screens.player.PlayerState
import com.example.ui.screens.MusicalCuriositySettingsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.ui.viewmodel.SettingsViewModel
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
    com.example.data.update.UpdateManager.init(this)
    com.example.data.update.UpdateJobService.schedule(this)
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) { com.example.data.update.UpdateManager.runAutomatic() }
    AppMotion.readSystem(this)
    // Must run before super.onCreate: swaps the splash theme for the normal one after the first frame.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    com.example.ui.theme.RefreshRate.requestHighest(this)
    setContent {
      val viewModel: TarkasViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val systemColors by viewModel.systemColors.collectAsStateWithLifecycle()

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
              this@MainActivity.getString(R.string.nav_mic_denied_notice),
              actionLabel = this@MainActivity.getString(R.string.nav_mic_denied_action),
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
            viewModel.markSystemCaptureUnavailable(this@MainActivity.getString(R.string.nav_capture_start_failed))
          }
        } else {
          viewModel.markSystemCaptureUnavailable(this@MainActivity.getString(R.string.nav_capture_denied))
        }
      }

      // Status and navigation bar icons must follow the app theme, not only the system theme.
      val systemDark = isSystemInDarkTheme()
      val isDarkApp = com.example.ui.theme.ThemeMode.isDark(themeMode) { systemDark }
      SideEffect {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightNavigationBars = !isDarkApp
      }

      val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
      TarkasTheme(themeMode = themeMode, systemColors = systemColors) {
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
              viewModel.markSystemCaptureUnavailable(this@MainActivity.getString(R.string.nav_capture_unsupported))
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
            androidx.compose.animation.scaleOut(animationSpec = com.example.ui.theme.Motion.gelExit<Float>(), targetScale = 1.04f)
        ) {
          val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
          val surfaceStyle by viewModel.surfaceStyle.collectAsStateWithLifecycle()
  val liquidGlassOpacity by viewModel.liquidGlassOpacity.collectAsStateWithLifecycle()
          val curiosityConfig by viewModel.curiosityConfig.collectAsStateWithLifecycle()
          val onlyTerms = remember { viewModel.onlyTermsChanged }
          OnboardingScreen(
            onlyTerms = onlyTerms,
            themeMode = themeMode,
            onThemeMode = { viewModel.setThemeMode(it) },
            surfaceStyle = surfaceStyle,
            onSurfaceStyle = { viewModel.setSurfaceStyle(it) },
            reduceMotion = reduceMotion,
            onReduceMotion = { viewModel.setReduceMotion(it) },
            curiosityConfig = curiosityConfig,
            onCuriosityConfig = { viewModel.updateCuriosityConfig(it) },
            onAcceptTerms = { viewModel.acceptTerms() },
            onFinish = { viewModel.finishOnboarding() },
            systemColors = systemColors,
            onSystemColors = { viewModel.setSystemColors(it) }
          )
        }
        if (showBoot) {
          BootScreen(onFinished = { showBoot = false })
        }
        // Asked once, after the opening animation, the first time something plays.
        if (askNotifications && notificationsMissing && !showBoot && !showOnboarding) {
          com.example.ui.components.TarkasAlertDialog(
            onDismissRequest = { viewModel.notificationsAsked() },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.nav_notifications_title)) },
            text = {
              Text(androidx.compose.ui.res.stringResource(R.string.nav_notifications_message))
            },
            confirmButton = {
              androidx.compose.material3.TextButton(
                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                modifier = Modifier.testTag("notifications_allow")
              ) { Text(androidx.compose.ui.res.stringResource(R.string.nav_notifications_continue)) }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(onClick = { viewModel.notificationsAsked() }) { Text(androidx.compose.ui.res.stringResource(R.string.nav_notifications_not_now)) }
            }
          )
        }
        // After the notification question: permission to install updates, then the "new version" popup.
        com.example.ui.components.StartupUpdatePrompts(
          enabled = !showBoot && !showOnboarding && !(askNotifications && notificationsMissing)
        )
        }
      }
    }
  }
}

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
  val waveform by viewModel.currentWaveform.collectAsStateWithLifecycle()
  val waveformBar by viewModel.waveformBar.collectAsStateWithLifecycle()
  val homeMusicFirst by viewModel.homeMusicFirst.collectAsStateWithLifecycle()
  val wantsToPlay by viewModel.wantsToPlay.collectAsStateWithLifecycle()
  // Values that change many times a second are kept as State and read only where they are drawn,
  // so a tick does not recompose the whole app.
  val currentPosMs = viewModel.positionMs.collectAsStateWithLifecycle()
  val durationMs = viewModel.durationMs.collectAsStateWithLifecycle()
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
  val liquidGlassOpacity by viewModel.liquidGlassOpacity.collectAsStateWithLifecycle()
  val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
  val reduceTransparency by viewModel.reduceTransparency.collectAsStateWithLifecycle()
  val favoriteIds by viewModel.favorites.collectAsStateWithLifecycle()
  val playlists by viewModel.playlists.collectAsStateWithLifecycle()
  val savedTracks by viewModel.savedTracks.collectAsStateWithLifecycle()
  val queue by viewModel.queue.collectAsStateWithLifecycle()
  val isShuffle by viewModel.shuffle.collectAsStateWithLifecycle()
  val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
  val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()

  val isShowingCuriositySettings by viewModel.isShowingCuriositySettings.collectAsStateWithLifecycle()
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
  // While the keyboard is up the mini player and the tab bar step aside, as in Apple Music: they would
  // otherwise ride on the keyboard and leave the page (the assistant's chat) almost no room.
  @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
  val imeVisible = WindowInsets.isImeVisible
  val settingsPage by viewModel.settingsPage.collectAsStateWithLifecycle()
  // Ajustes and its pages are a place to go into, not a tab to stay on: the mini player and the tab bar
  // step aside, and the page's own back button (or the back gesture) leads out.
  val barsHidden = currentTab == TarkasNavTab.SETTINGS || isShowingCuriositySettings

  // Status bar icons follow the app theme, except on the Now Playing page, which is always a dark tint.
  val hostView = LocalView.current
  val systemDarkTheme = isSystemInDarkTheme()
  val isDarkTheme = com.example.ui.theme.ThemeMode.isDark(themeMode) { systemDarkTheme }
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

  // The mixer (the whole Audio tab) in one place: the tab shows it, and the player opens it in a sheet,
  // so the sound can be changed without leaving the player. Audio-only states are collected inside
  // this composable so EQ fader drags and 30 fps RTA frames never recompose the other tabs or dock.
  val audioContent: @Composable (Modifier, Boolean) -> Unit = { audioModifier, audioPlaying ->
    val eqSettings by viewModel.eqSettings.collectAsStateWithLifecycle()
    val eqPresets by viewModel.eqPresets.collectAsStateWithLifecycle()
    val activePresetId by viewModel.activePresetId.collectAsStateWithLifecycle()
    val eqModified by viewModel.eqModified.collectAsStateWithLifecycle()
    val capabilities by viewModel.audioEngineManager.capabilities.collectAsStateWithLifecycle()
    val deviceSearch by viewModel.deviceSearch.collectAsStateWithLifecycle()
    val systemAudioStatus by viewModel.systemAudioStatus.collectAsStateWithLifecycle()
    val spectrumBars = viewModel.audioEngineManager.spectrumBars.collectAsStateWithLifecycle()
    val rtaFrame = viewModel.audioEngineManager.rtaFrame.collectAsStateWithLifecycle()
    val activeApps by viewModel.activeApps.collectAsStateWithLifecycle()
    val eqReach by viewModel.eqReach.collectAsStateWithLifecycle()
    val globalEqStatus by viewModel.globalEqStatus.collectAsStateWithLifecycle()
    AudioScreen(
      modifier = audioModifier,
      capabilities = capabilities,
      eqSettings = eqSettings,
      presets = eqPresets,
      activePresetId = activePresetId,
      eqModified = eqModified,
      systemAudioStatus = systemAudioStatus,
      isPlaying = audioPlaying,
      spectrumBars = { spectrumBars.value },
      rtaFrame = { rtaFrame.value },
      onSoloBand = { viewModel.setSoloBand(it) },
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
      outputCorrection = com.example.ui.screens.OutputCorrectionUi(
        outputName = viewModel.output.collectAsStateWithLifecycle().value.name,
        auto = viewModel.autoOutputCorrection.collectAsStateWithLifecycle().value,
        suggestionQuery = viewModel.outputSuggestion.collectAsStateWithLifecycle().value?.query
      ),
      onSetAutoOutputCorrection = { viewModel.setAutoOutputCorrection(it) },
      onExportOutputProfiles = { viewModel.exportOutputProfilesJson() },
      onImportOutputProfiles = { viewModel.importOutputProfilesJson(it) },
      crossfadeSeconds = viewModel.crossfadeSeconds.collectAsStateWithLifecycle().value,
      onSetCrossfade = { viewModel.setCrossfadeSeconds(it) },
      devicePickerRequest = viewModel.devicePickerRequest.collectAsStateWithLifecycle().value,
      onDevicePickerOpened = { viewModel.devicePickerOpened() },
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
        status = viewModel.spatialStatus.collectAsStateWithLifecycle().value,
        tuning = viewModel.spatialTuning.collectAsStateWithLifecycle().value,
        headTracking = viewModel.headTracking.collectAsStateWithLifecycle().value,
        headStatus = viewModel.headStatus.collectAsStateWithLifecycle().value
      ),
      onCompare = { viewModel.compareWithoutEq(it) },
      loudness = viewModel.loudness.collectAsStateWithLifecycle().value,
      onSetNormalizationMode = { viewModel.setNormalizationMode(it) },
      onSetTargetLufs = { viewModel.setTargetLufs(it) },
      audioHealth = viewModel.audioEngineManager.audioHealth.collectAsStateWithLifecycle().value,
      spatialActions = remember {
        com.example.ui.screens.SpatialActions(
          onEnabled = { viewModel.setSpatialEnabled(it) },
          onPreset = { viewModel.setSpatialPreset(it) },
          onIntensity = { viewModel.setSpatialIntensity(it) },
          onKnob = { preset, knob, value -> viewModel.setSpatialKnob(preset, knob, value) },
          onResetTuning = { viewModel.resetSpatialTuning(it) },
          onHeadTracking = { viewModel.setHeadTracking(it) },
          onRecenterHead = { viewModel.recenterHead() }
        )
      }
    )
  }
  var showMixer by rememberSaveable { mutableStateOf(false) }
  if (showMixer) {
    MixerSheet(onDismiss = { showMixer = false }) { audioContent(Modifier, isPlaying) }
  }

  // Only run Android's Visualizer FFT while the EQ screen or mixer sheet is actually visible and the app is in foreground.
  val spectrumOnScreen = (currentTab == TarkasNavTab.AUDIO && !isShowingCuriositySettings) || showMixer
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  androidx.compose.runtime.DisposableEffect(lifecycleOwner, spectrumOnScreen) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      when (event) {
        androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.setSpectrumVisible(spectrumOnScreen)
        androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.setSpectrumVisible(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    val started = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    viewModel.setSpectrumVisible(started && spectrumOnScreen)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.setSpectrumVisible(false)
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
    // Haze is only needed when frosted (or fallback) blur is active; running both hazeSource and
    // layerBackdrop records the whole screen into two offscreen layers every frame.
    val hazeOn = blurAllowed && !liquidOn
    val pageColor = MaterialTheme.colorScheme.background
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
      onDraw = {
        drawRect(pageColor)
        drawContent()
      }
    )
    var barHeightPx by remember { mutableIntStateOf(0) }
    // The full player has no bars, only short messages floating over it: they must not push its layout
    // up while they show (they float over the controls instead).
    val barInset = if (currentTab == TarkasNavTab.MUSIC) 0.dp else with(LocalDensity.current) { barHeightPx.toDp() }
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

    // Swiping a page sideways moves to the neighbouring tab of the bar (the same order, and the same fresh
    // start as a tap on it). Controls that use horizontal drags themselves (sliders, carousels, the EQ graph)
    // take the gesture first; this one only gets what no child consumed.
    val barTabs = remember { listOf(TarkasNavTab.HOME, TarkasNavTab.LIBRARY, TarkasNavTab.AUDIO, TarkasNavTab.ASSISTANT) }
    val swipeDistancePx = with(LocalDensity.current) { Grid.u96.toPx() }
    var swipeTravel by remember { mutableStateOf(0f) }
    val swipeTabs = Modifier.draggable(
      orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
      enabled = currentTab in barTabs && !isShowingCuriositySettings,
      state = androidx.compose.foundation.gestures.rememberDraggableState { swipeTravel += it },
      onDragStarted = { swipeTravel = 0f },
      onDragStopped = {
        val index = barTabs.indexOf(currentTab)
        // Dragging left reveals the next tab, dragging right the previous one.
        val target = when {
          swipeTravel <= -swipeDistancePx -> barTabs.getOrNull(index + 1)
          swipeTravel >= swipeDistancePx -> barTabs.getOrNull(index - 1)
          else -> null
        }
        swipeTravel = 0f
        if (target != null) viewModel.selectTab(target)
      }
    )
    CompositionLocalProvider(
      LocalSurfaceStyle provides surfaceStyle,
      com.example.ui.theme.LocalLiquidGlassOpacity provides liquidGlassOpacity,
      LocalHazeState provides if (hazeOn) hazeState else null,
      com.example.ui.theme.LocalBackdrop provides if (liquidOn) backdrop else null,
      LocalBarInset provides barInset
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .then(if (hazeOn) Modifier.hazeSource(hazeState) else Modifier)
        .then(if (liquidOn) Modifier.layerBackdrop(backdrop) else Modifier)
        .nestedScroll(navScroll)
        .then(swipeTabs)
    ) {
      // The player is not a page of the switcher: it is drawn over the page it came from, which stays
      // composed underneath. Closing then shows a page that is already built, instead of composing it
      // in the first frames of the shrink (a hitch of ~170 ms on the S23).
      var pageBelowPlayer by remember { mutableStateOf<TarkasNavTab>(TarkasNavTab.HOME) }
      if (currentTab != TarkasNavTab.MUSIC) pageBelowPlayer = currentTab
      val screenKey: Any = when {
        isShowingCuriositySettings -> "curiosity"
        currentTab == TarkasNavTab.MUSIC -> pageBelowPlayer
        else -> currentTab
      }
      val pagePlaying = isPlaying && currentTab != TarkasNavTab.MUSIC
      val reducedMotion = com.example.ui.theme.rememberReducedMotion()
      // Each page keeps its saved state (list scroll, open playlist, search) while another page
      // is shown, so going back from the player lands exactly where the user left the list.
      val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
      // The four tabs stay composed after they are left, so a tab switch is only the fade, not a
      // new composition of the whole page (1–2 frames of 20–35 ms on the S23). Settings and the
      // curiosity page are built when opened and dropped when closed.
      com.example.ui.components.KeptPages(
        target = screenKey,
        keep = { it is TarkasNavTab && it != TarkasNavTab.SETTINGS },
        // While the full player covers the page, a screen reader must not reach it: its nodes stay
        // composed (for the quick close) but are removed from the accessibility tree.
        modifier = if (currentTab == TarkasNavTab.MUSIC) Modifier.clearAndSetSemantics {} else Modifier,
        reducedMotion = reducedMotion
      ) { screen ->
      // A page kept in the background shows nothing playing, so its bars and spectrum stop.
      val playingHere = pagePlaying && screen == screenKey
      // The large title belongs to each tab page, so it moves with the page in every transition
      // (and stays put under the player while that grows over it).
      val tabPage = screen is TarkasNavTab && screen != TarkasNavTab.MUSIC
      Column(
        modifier = Modifier
          .fillMaxSize()
          // The pages keep clear of the side insets (camera cutout in landscape); the full player does
          // not, so its background reaches the screen edge and only its content keeps clear.
          .padding(contentPadding)
          .then(
            when (screen) {
              // The growing shape takes the colour read from the artwork (PlayerOrigin.tint, set by the
              // player); black until a cover was read, never the catalogue colour, which can be wrong.
              "curiosity" -> Modifier.statusBarsPadding()
              else -> Modifier
            }
          )
      ) {
      // A sub-page of Ajustes (Sobre, the legal texts) draws its own title.
      // How far the page has been scrolled under its title: the title's soft fade shows only then, so at
      // rest it does not dim the first control below it.
      var scrolledUnder by remember(screen, settingsPage) { mutableStateOf(0f) }
      val scrollWatcher = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
          override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (consumed.y != 0f) scrolledUnder = (scrolledUnder - consumed.y).coerceAtLeast(0f)
            return androidx.compose.ui.geometry.Offset.Zero
          }
        }
      }
      if (tabPage && !(screen == TarkasNavTab.SETTINGS && settingsPage != null)) {
        // Ajustes opens from the title bar of Início only, as an account button does in Apple apps;
        // the other pages keep their title clean.
        TarkasLargeTitle(
          title = androidx.compose.ui.res.stringResource((screen as TarkasNavTab).labelRes),
          onOpenSettings = { viewModel.setTab(TarkasNavTab.SETTINGS) },
          showSettings = screen == TarkasNavTab.HOME,
          onBack = if (screen == TarkasNavTab.SETTINGS) ({ viewModel.goBack() }) else null,
          fadeBelow = scrolledUnder > 0f
        )
      }
      Box(modifier = Modifier.weight(1f).nestedScroll(scrollWatcher)) {
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
            musicFirst = homeMusicFirst,
            onLoadPlayStats = { viewModel.readPlayStats() },
            onLoadResume = { viewModel.readResumeInfo() },
            onResume = {
              viewModel.resumeListening()
              viewModel.setTab(TarkasNavTab.MUSIC)
            },
            currentTrack = currentTrack,
            tracks = tracks,
            isPlaying = playingHere,
            favoriteIds = favoriteIds,
            playlists = playlists,
            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
            onPlayNext = { track -> viewModel.playNext(track) },
            onAddToQueue = { track -> viewModel.addToQueue(track) },
            onAddToPlaylist = { id, track -> viewModel.addToPlaylist(id, track) },
            onCreatePlaylist = { name, track -> viewModel.createPlaylist(name, track) },
            onDownloadOffline = { track -> viewModel.downloadTrackOffline(track) },
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
              if (viewModel.playTrack(track)) viewModel.setTab(TarkasNavTab.MUSIC)
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

        TarkasNavTab.MUSIC -> Unit

        TarkasNavTab.LIBRARY -> {
          LibraryScreen(
            state = LibraryState(
              playlists = playlists,
              favoriteTracks = favoriteTracks,
              savedTracks = savedTracks,
              allTracks = tracks,
              deviceTracks = viewModel.deviceTracks.collectAsStateWithLifecycle().value,
              deviceScanning = viewModel.deviceScanning.collectAsStateWithLifecycle().value,
              hasMusicPermission = viewModel.hasMusicPermission(),
              songsPerFolder = viewModel.deviceFolders.collectAsStateWithLifecycle().value,
              trackFolders = viewModel.trackFolders.collectAsStateWithLifecycle().value,
              folderRules = viewModel.folderRules.collectAsStateWithLifecycle().value,
              queue = queue,
              currentTrack = currentTrack,
              isPlaying = playingHere,
              upcoming = viewModel.upcoming.collectAsStateWithLifecycle().value,
              initialSongOrder = viewModel.songOrder,
              playCounts = viewModel.playCounts.collectAsStateWithLifecycle().value,
              openQueue = openQueueRequest
            ),
            actions = LibraryActions(
              onScanDevice = { viewModel.scanDeviceMusic() },
              onSetFolderIncluded = { folder, included -> viewModel.setFolderIncluded(folder, included) },
              onPlayFromList = { track, list ->
                // With songs waiting in the queue it asks first; the player opens once it plays.
                if (viewModel.playFromList(track, list)) viewModel.setTab(TarkasNavTab.MUSIC)
              },
              onShuffleList = { list -> viewModel.shuffleFrom(list) },
              onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
              onImportPlaylistM3u = { name, text -> viewModel.importPlaylistM3u(name, text) },
              onAddLocalTrack = { title, artist, uri, dur ->
                viewModel.addLocalAudio(title, artist, uri, dur)
              },
              onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
              onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
              onRemoveFromPlaylist = { id, trackId -> viewModel.removeFromPlaylist(id, trackId) },
              onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
              onPlayNext = { track -> viewModel.playNext(track) },
              onAddToQueue = { track -> viewModel.addToQueue(track) },
              onAddToPlaylist = { id, track -> viewModel.addToPlaylist(id, track) },
              onCreatePlaylistWith = { name, track -> viewModel.createPlaylist(name, track) },
              onPlayQueueItem = { index ->
                viewModel.playFromQueue(index)
                viewModel.setTab(TarkasNavTab.MUSIC)
              },
              onRemoveQueueItem = { index -> viewModel.removeFromQueue(index) },
              onClearQueue = { viewModel.clearQueue() },
              onSaveQueueAsPlaylist = { viewModel.saveQueueAsPlaylist(it) },
              onSongOrderChange = { viewModel.setSongOrder(it) },
              onNeedPlayCounts = { viewModel.loadPlayCounts() },
              onLoadPlayStats = { viewModel.readPlayStats() },
              onMoveQueueItem = { from, to -> viewModel.moveInQueue(from, to) },
              onPlayUpcoming = { track ->
                viewModel.playFromCurrentList(track)
                viewModel.setTab(TarkasNavTab.MUSIC)
              },
              onQueueOpened = { viewModel.queueOpened() }
            )
          )
        }

        TarkasNavTab.AUDIO -> {
          audioContent(Modifier, playingHere)
        }

        TarkasNavTab.ASSISTANT -> {
          val onlineAi by viewModel.assistantOnlineAi.collectAsStateWithLifecycle()
          AssistantScreen(
            messages = assistantMessages,
            isProcessing = isAssistantProcessing,
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            activeTimers = activeTimers,
            onSendMessage = { text -> viewModel.sendAssistantMessage(text) },
            onSendVoiceMessage = { text -> viewModel.sendAssistantMessage(text, askedByVoice = true) },
            onStopSpeech = { viewModel.stopAssistantSpeech() },
            onCancelTimer = { id -> viewModel.cancelTimer(id) },
            onRetry = { id -> viewModel.retryAssistantReply(id) },
            onlineAi = onlineAi,
            onSetOnlineAi = { enabled -> viewModel.setAssistantOnlineAi(enabled) },
            playback = when {
              currentTrack == null -> AssistantPlayback.NONE
              isPlaying -> AssistantPlayback.PLAYING
              else -> AssistantPlayback.PAUSED
            },
            onClearConversation = { viewModel.clearAssistantConversation() }
          )
        }

        TarkasNavTab.SETTINGS -> {
          val settingsVm: SettingsViewModel = viewModel()
          val themeMode by settingsVm.themeMode.collectAsStateWithLifecycle()
          val systemColors by settingsVm.systemColors.collectAsStateWithLifecycle()
          val surfaceStyle by settingsVm.surfaceStyle.collectAsStateWithLifecycle()
          val liquidGlassOpacity by settingsVm.liquidGlassOpacity.collectAsStateWithLifecycle()
          val reduceMotion by settingsVm.reduceMotion.collectAsStateWithLifecycle()
          val reduceTransparency by settingsVm.reduceTransparency.collectAsStateWithLifecycle()
          val homeMusicFirst by settingsVm.homeMusicFirst.collectAsStateWithLifecycle()
          SettingsScreen(
            themeMode = themeMode,
            capabilities = viewModel.audioEngineManager.capabilities.collectAsStateWithLifecycle().value,
            onThemeModeChange = { mode -> settingsVm.setThemeMode(mode) },
            systemColors = systemColors,
            onSystemColorsChange = { settingsVm.setSystemColors(it) },
            curiosityConfig = curiosityConfig,
            onOpenCuriositySettings = { viewModel.openCuriositySettings() },
            hasGeminiKey = viewModel.assistantEngine.hasGeminiKey,
            surfaceStyle = surfaceStyle,
            onSurfaceStyleChange = { style -> settingsVm.setSurfaceStyle(style) },
            liquidGlassOpacity = liquidGlassOpacity,
            onLiquidGlassOpacityChange = { settingsVm.setLiquidGlassOpacity(it) },
            reduceMotion = reduceMotion,
            onReduceMotionChange = { settingsVm.setReduceMotion(it) },
            waveformBar = waveformBar,
            onWaveformBarChange = { viewModel.setWaveformBar(it) },
            homeMusicFirst = homeMusicFirst,
            onHomeMusicFirstChange = { settingsVm.setHomeMusicFirst(it) },
            onListAnimationChange = { settingsVm.setListAnimation(it) },
            reduceTransparency = reduceTransparency,
            onReduceTransparencyChange = { settingsVm.setReduceTransparency(it) },
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onSetRepeatMode = { viewModel.setRepeatMode(it) },
            subPage = settingsPage,
            onOpenSubPage = { viewModel.openSettingsPage(it) },
            onSubPageBack = { viewModel.goBack() },
            onOpenAudio = { viewModel.setTab(TarkasNavTab.AUDIO) },
            cacheSizeBytes = { viewModel.cacheSizeBytes() },
            onClearCache = { done -> viewModel.clearCache(done) },
            onExportData = { uri -> viewModel.writeExport(uri) },
            onRestoreData = { uri -> viewModel.restoreFrom(uri) },
            onExportDiagnostics = { uri -> viewModel.writeDiagnostics(uri) },
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
      // The covers around the current track are loaded ahead whenever it changes, before the player
      // is even opened, so its carousel has them in memory.
      PrefetchCarouselCovers(viewModel.coverNeighbors)
      // The full player, over the page below it. The shape grows from what was tapped and shrinks into
      // the mini player (expandFromOrigin); the visibility keeps it composed until the shape has landed.
      AnimatedVisibility(
        visible = currentTab == TarkasNavTab.MUSIC,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = Modifier.fillMaxSize()
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .expandFromOrigin(this, Color.Black, reducedMotion)
        ) {
          pageStates.SaveableStateProvider("music") {
            MusicScreen(
              state = PlayerState(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                coverPlaying = wantsToPlay,
                currentPositionMs = { currentPosMs.value },
                durationMs = { durationMs.value },
                activeCuriosity = activeCuriosity,
                coverNeighbors = viewModel.coverNeighbors.collectAsStateWithLifecycle().value,
                waveform = waveform,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                favoriteIds = favoriteIds,
                playlists = playlists,
                sleepTimer = viewModel.sleepTimer.collectAsStateWithLifecycle().value,
                trackDetails = viewModel.trackDetails.collectAsStateWithLifecycle().value,
                lyrics = viewModel.lyrics.collectAsStateWithLifecycle().value,
                problem = viewModel.playbackProblem.collectAsStateWithLifecycle().value,
                abLoop = viewModel.abLoop.collectAsStateWithLifecycle().value
              ),
              actions = PlayerActions(
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNextTrack = { viewModel.playNextTrack() },
                onPreviousTrack = { viewModel.playPreviousTrack() },
                onPreviousFromCover = { viewModel.playPreviousTrack(force = true) },
                onSeekTo = { pos -> viewModel.seekTo(pos) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                onPlayNext = { track -> viewModel.playNext(track) },
                onAddToQueue = { track -> viewModel.addToQueue(track) },
                onAddToPlaylist = { id, track -> viewModel.addToPlaylist(id, track) },
                onCreatePlaylist = { name, track -> viewModel.createPlaylist(name, track) },
                onOpenQueue = { viewModel.openQueue() },
                onOpenMixer = { showMixer = true },
                onClose = { viewModel.goBack() },
                onSetSleepTimer = { m -> viewModel.setSleepTimer(m) },
                onSleepAtEndOfTrack = { viewModel.setSleepAtEndOfTrack() },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onLoadTrackDetails = { track -> viewModel.loadTrackDetails(track) },
                onLoadLyrics = { track -> viewModel.loadLyrics(track) },
                onRetry = { viewModel.retryPlayback() },
                onToggleAbLoop = { viewModel.toggleAbLoop() },
                onImportLyrics = { track, text -> viewModel.importLyrics(track, text) },
                onDownloadOffline = { track -> viewModel.downloadTrackOffline(track) }
              )
            )
          }
        }
      }
    }
      val dockWidthClass = com.example.ui.layout.rememberWidthClass()
      val isTabletDock = dockWidthClass != com.example.ui.layout.WidthClass.COMPACT &&
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .onSizeChanged { barHeightPx = it.height }
          .imePadding()
          .navigationBarsPadding()
          .padding(start = Grid.margin, end = Grid.margin, bottom = Grid.u6),
        horizontalAlignment = if (isTabletDock) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Grid.u6)
      ) {
       com.example.ui.theme.TarkasDarkOverride(enabled = currentTab == TarkasNavTab.MUSIC) {
        // Floating AI Curiosity Speech Banner. Curiosities appear only on the player page (and in
        // notifications), never on the other tabs.
        AnimatedVisibility(
          visible = currentTab == TarkasNavTab.MUSIC &&
            !isShowingCuriositySettings &&
            isAiSpeakingCuriosity &&
            !currentCuriositySpeechText.isNullOrBlank() &&
            curiosityConfig.displayMode == com.example.data.model.CuriosityDisplayMode.VOICE_AND_BANNER,
          enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(com.example.ui.theme.Motion.gel<androidx.compose.ui.unit.IntSize>()),
          exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(com.example.ui.theme.Motion.gelExit<androidx.compose.ui.unit.IntSize>())
        ) {
          // A floating glass capsule, like the mini player under it.
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .glassPill(cornerRadius = Grid.u24)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = Grid.u12, end = Grid.u6, top = Grid.u6, bottom = Grid.u6),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(Grid.u48)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(Grid.icon)
                )
              }

              Spacer(modifier = Modifier.width(Grid.u12))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = androidx.compose.ui.res.stringResource(
                    if (curiositySpeechStage == "intro") R.string.nav_curiosity_intro else R.string.nav_curiosity_track
                  ),
                  fontWeight = FontWeight.Bold,
                  style = GridText.Small,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = currentCuriositySpeechText ?: "",
                  style = GridText.Small,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              Spacer(modifier = Modifier.width(Grid.u6))

              com.example.ui.components.GlassChip(
                label = androidx.compose.ui.res.stringResource(R.string.nav_curiosity_skip),
                onClick = { viewModel.skipCuriositySpeech() }
              )
            }
          }
        }

        // A short message (a track that could not play, a permission left off), over the mini player.
        // It goes away by itself after a few seconds (longer when the user relies on accessibility
        // services), or when its action is used. Its clock waits while a full-screen page hides it.
        // Starting another list while queued songs wait: keep them (they play first) or clear them.
        viewModel.listStartPrompt.collectAsStateWithLifecycle().value?.let { p ->
          com.example.ui.components.TarkasAlertDialog(
            onDismissRequest = { viewModel.answerListStart(null) },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.nav_keep_queue_title), style = com.example.ui.theme.AppText.Title3) },
            text = {
              Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.nav_keep_queue_message, p.queued, p.queued),
                style = com.example.ui.theme.AppText.Body
              )
            },
            confirmButton = {
              androidx.compose.material3.TextButton(onClick = { viewModel.answerListStart(true) }, modifier = Modifier.testTag("list_start_keep")) {
                Text(androidx.compose.ui.res.stringResource(R.string.nav_keep_queue_keep))
              }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(onClick = { viewModel.answerListStart(false) }, modifier = Modifier.testTag("list_start_clear")) {
                Text(androidx.compose.ui.res.stringResource(R.string.nav_keep_queue_clear))
              }
            }
          )
        }

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
        AnimatedVisibility(
          visible = noticeVisible,
          enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(com.example.ui.theme.Motion.gel<androidx.compose.ui.unit.IntSize>()),
          exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(com.example.ui.theme.Motion.gelExit<androidx.compose.ui.unit.IntSize>())
        ) {
          val n = shownNotice
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .glassPill(cornerRadius = Grid.u24)
              .padding(start = Grid.u12, end = Grid.u6, top = Grid.u6, bottom = Grid.u6)
              .heightIn(min = Grid.u48)
              .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
              .testTag("app_notice"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = n?.text ?: "",
              style = GridText.Body,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
            )
            if (n?.actionLabel != null) {
              Spacer(modifier = Modifier.width(Grid.u6))
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
        // Arriving from the closing player: no entrance of its own. The mini player is drawn with an
        // alpha taken from the player's shrink progress, so shrink and appearance are one movement.
        val miniFromPlayer = com.example.ui.theme.PlayerOrigin.progress != null &&
          currentTab != TarkasNavTab.MUSIC && !com.example.ui.theme.rememberReducedMotion()
        // In landscape (under 480 dp tall) the mini player and the tab bar share one row, so they take
        // one bar's height instead of two. Portrait keeps them stacked, as before.
        val compactBars = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
        val bars: @Composable (Modifier, Modifier) -> Unit = { miniSlot, navSlot ->
        AnimatedVisibility(
          modifier = miniSlot,
          visible = currentTab != TarkasNavTab.MUSIC &&
            currentTab != TarkasNavTab.ASSISTANT &&
            currentTrack != null && !imeVisible && !barsHidden,
          enter = if (miniFromPlayer || com.example.ui.theme.rememberReducedMotion()) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) +
              androidx.compose.animation.scaleIn(initialScale = 0.94f, animationSpec = com.example.ui.theme.Motion.gel<Float>()),
          // Gone within a few frames: the cover of the opening player is drawn above it and takes its place.
          exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(60))
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
              .graphicsLayer {
                // Still composed at alpha 0, so its bounds are registered for the cover to land on.
                if (miniFromPlayer) {
                  alpha = com.example.ui.theme.PlayerTravel.miniIn(
                    com.example.ui.theme.PlayerOrigin.progress?.value ?: 0f
                  )
                }
              }
              .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.mini = it.boundsInWindow() }
              .opensPlayer()
          )
        }

        // The full player covers the tab bar, as in Apple Music; it slides back when the player closes.
        AnimatedVisibility(
          modifier = navSlot,
          visible = currentTab != TarkasNavTab.MUSIC && !imeVisible && !barsHidden,
          enter = if (reducedChrome) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 80)) +
              androidx.compose.animation.slideInVertically(com.example.ui.theme.Motion.gel<androidx.compose.ui.unit.IntOffset>()) { it / 2 },
          // It leaves downwards, quickly, as the player rises (95% gone with 15% of the opening done).
          exit = if (reducedChrome) androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
            else androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
              androidx.compose.animation.slideOutVertically(
                androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutLinearInEasing)
              ) { it / 2 }
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
        if (compactBars) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(Grid.u6),
            verticalAlignment = Alignment.Bottom
          ) { bars(Modifier.weight(1f), Modifier.width(Grid.u384)) }
        } else if (isTabletDock && dockWidthClass == com.example.ui.layout.WidthClass.EXPANDED) {
          // Tablet landscape / expanded (>= 840 dp): floating dock centred, mini player + nav bar side by side.
          Row(
            horizontalArrangement = Arrangement.spacedBy(Grid.u12),
            verticalAlignment = Alignment.Bottom
          ) { bars(Modifier.width(Grid.u384), Modifier.width(Grid.u384)) }
        } else if (isTabletDock) {
          // Tablet portrait / medium (600..839 dp): stacked and centred at comfortable reach width.
          bars(Modifier.width(Grid.u384 + Grid.u96), Modifier.width(Grid.u384))
        } else {
          // Smartphone (< 600 dp): untouched full-width stacked layout.
          bars(Modifier, Modifier)
        }
       }
      }
    }
    }
  }
}

/** The mixer in a full-height sheet over the player, so the sound changes without leaving it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixerSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.background
  ) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) { content() }
  }
}
