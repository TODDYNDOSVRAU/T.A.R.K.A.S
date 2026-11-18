package com.example.ui.screens

import android.content.res.Configuration
import com.example.ui.theme.Grid
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.example.ui.layout.rememberWidthClass
import com.example.ui.layout.twoPane
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.LyricsSheet
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.TrackDetailsSheet
import com.example.ui.screens.player.ActionsRow
import com.example.ui.screens.player.Artwork
import com.example.ui.screens.player.CloseHandle
import com.example.ui.screens.player.PlaybackProblemBanner
import com.example.ui.screens.player.PlayerActions
import com.example.ui.screens.player.PlayerState
import com.example.ui.screens.player.ProgressRow
import com.example.ui.screens.player.TitleRow
import com.example.ui.screens.player.TransportRow
import com.example.ui.screens.player.VolumeRow
import com.example.ui.screens.player.playerBackdrop
import com.example.ui.screens.player.rememberPlayerBackdrop
import com.example.ui.screens.player.rememberStreamVolume
import com.example.ui.screens.player.revealWithPlayer
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Motion
import com.example.ui.theme.PlayerTravel
import com.example.ui.theme.TarkasDarkOverride
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.TrackDetailsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The full player (Now Playing). The page is tinted with the artwork's own colour, darkened until white
 * text clears 4.5:1, and is laid out like Apple Music's: large artwork, title and artist left-aligned
 * with favourite and "more" at the trailing edge, a thin scrubber, glyph-only transport, volume, and a
 * row of plain glyph buttons. It is pulled down (or the back gesture previewed) to close, and the handle
 * at the top is a real "Fechar player" button for anyone who cannot drag.
 */
@Composable
fun MusicScreen(
  state: PlayerState,
  actions: PlayerActions,
  modifier: Modifier = Modifier
) {
  val currentTrack = state.currentTrack
  val isPlaying = state.isPlaying
  val coverPlaying = state.coverPlaying
  val currentPositionMs = state.currentPositionMs
  val durationMs = state.durationMs
  val activeCuriosity = state.activeCuriosity
  val waveform = state.waveform
  val isShuffle = state.isShuffle
  val repeatMode = state.repeatMode
  val favoriteIds = state.favoriteIds
  val playlists = state.playlists
  val sleepTimer = state.sleepTimer
  val trackDetails = state.trackDetails
  val lyrics = state.lyrics
  val coverNeighbors = state.coverNeighbors
  val onTogglePlayPause = actions.onTogglePlayPause
  val onNextTrack = actions.onNextTrack
  val onPreviousTrack = actions.onPreviousTrack
  val onPreviousFromCover = actions.onPreviousFromCover
  val onSeekTo = actions.onSeekTo
  val onToggleShuffle = actions.onToggleShuffle
  val onCycleRepeat = actions.onCycleRepeat
  val onToggleFavorite = actions.onToggleFavorite
  val onPlayNext = actions.onPlayNext
  val onAddToQueue = actions.onAddToQueue
  val onAddToPlaylist = actions.onAddToPlaylist
  val onCreatePlaylist = actions.onCreatePlaylist
  val onOpenQueue = actions.onOpenQueue
  val onOpenMixer = actions.onOpenMixer
  val onClose = actions.onClose
  val onSetSleepTimer = actions.onSetSleepTimer
  val onSleepAtEndOfTrack = actions.onSleepAtEndOfTrack
  val onCancelSleepTimer = actions.onCancelSleepTimer
  val onLoadTrackDetails = actions.onLoadTrackDetails
  val onLoadLyrics = actions.onLoadLyrics
  var showCuriosityDialog by remember { mutableStateOf(false) }
  var showDetails by remember { mutableStateOf(false) }
  var showLyrics by remember { mutableStateOf(false) }
  var showSleepTimer by remember { mutableStateOf(false) }
  if (showSleepTimer) {
    SleepTimerDialog(
      timer = sleepTimer,
      onMinutes = onSetSleepTimer,
      onEndOfTrack = onSleepAtEndOfTrack,
      onCancel = onCancelSleepTimer,
      onDismiss = { showSleepTimer = false }
    )
  }
  val reduced = rememberReducedMotion()
  val currentReduced by rememberUpdatedState(reduced)
  val scope = rememberCoroutineScope()
  val currentClose by rememberUpdatedState(onClose)

  // ---- pull down to close ----
  // 0 at rest, 1 a full page down. The finger writes the value directly (no coroutine per delta); the
  // spring back runs in one job of the screen's own scope, so a cancelled gesture cannot cancel it and
  // leave the page shrunk.
  var pull by remember { mutableFloatStateOf(0f) }
  var pageHeightPx by remember { mutableFloatStateOf(1f) }
  val settleJob = remember { arrayOfNulls<Job>(1) }
  fun springBack() {
    settleJob[0]?.cancel()
    if (currentReduced) {
      pull = 0f
      return
    }
    settleJob[0] = scope.launch {
      animate(pull, 0f, animationSpec = Motion.criticalSpatial()) { v, _ -> pull = v }
    }
  }
  // Closing hands the page back to rest while the container and the cover travel to the mini player,
  // so the cover lands on it instead of the whole page sliding off the screen.
  fun closeFromPull() {
    springBack()
    currentClose()
  }
  fun settlePull(velocityPx: Float) {
    // Past 30% it closes; a shorter pull closes only with a real flick downwards.
    if (pull > 0.3f || (velocityPx > 1800f && pull > 0.06f)) closeFromPull() else springBack()
  }
  PredictiveBackHandler { events ->
    try {
      events.collect { e -> if (!currentReduced) pull = e.progress * 0.2f }
      closeFromPull()
    } catch (e: CancellationException) {
      springBack()
      throw e
    }
  }

  // ---- colour and the cover behind the page ----
  val backdrop = rememberPlayerBackdrop(currentTrack, isPlaying, reduced)

  // ---- volume ----
  val volume = rememberStreamVolume()

  // The finger is on the progress bar: the cover comes forward a little (see PlayerTravel.coverScale).
  var scrubbing by remember { mutableStateOf(false) }

  // ---- the page's parts, shared by the portrait and the landscape layouts ----
  // Two halves when the window is on its side or wide enough (a tablet upright), one column otherwise.
  val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ||
    rememberWidthClass().twoPane

  val artworkPart: @Composable (Modifier, Dp) -> Unit = { m, fade ->
    Artwork(
      track = currentTrack,
      neighbors = coverNeighbors,
      isPlaying = coverPlaying,
      scrubbing = scrubbing,
      reduced = reduced,
      onNext = onNextTrack,
      onPrevious = onPreviousFromCover,
      // Pulled down (or the back gesture previewed): the side covers go back behind the middle one.
      closingByHand = { pull > 0.01f },
      edgeFade = fade,
      modifier = m
    )
  }
  val titlePart: @Composable () -> Unit = {
    Column {
    PlaybackProblemBanner(state.problem, actions.onRetry, Modifier.padding(bottom = Grid.u12))
    TitleRow(
      track = currentTrack,
      isFavorite = currentTrack != null && currentTrack.id in favoriteIds,
      playlists = playlists,
      sleepTimer = sleepTimer,
      onToggleFavorite = onToggleFavorite,
      onPlayNext = onPlayNext,
      onAddToQueue = onAddToQueue,
      onAddToPlaylist = onAddToPlaylist,
      onCreatePlaylist = onCreatePlaylist,
      onSleepTimer = { showSleepTimer = true },
      onAbout = { track -> onLoadTrackDetails(track); showDetails = true },
      onLyrics = { track -> onLoadLyrics(track); showLyrics = true }
    )
    }
  }
  val progressPart: @Composable () -> Unit = {
    ProgressRow(
      currentTrack = currentTrack,
      positionMs = currentPositionMs,
      durationMs = durationMs,
      isPlaying = isPlaying,
      reduced = reduced,
      waveform = waveform,
      onScrubbing = { scrubbing = it },
      onSeekTo = onSeekTo
    )
  }
  val transportPart: @Composable () -> Unit = {
    TransportRow(isPlaying, onPrevious = onPreviousTrack, onTogglePlayPause = onTogglePlayPause, onNext = onNextTrack)
  }
  val volumePart: @Composable () -> Unit = { VolumeRow(volume, reduced) }
  val actionsPart: @Composable () -> Unit = {
    ActionsRow(
      hasCuriosity = activeCuriosity != null,
      sleepTimer = sleepTimer,
      isShuffle = isShuffle,
      repeatMode = repeatMode,
      onCuriosity = { showCuriosityDialog = true },
      onSleepTimer = { showSleepTimer = true },
      onToggleShuffle = onToggleShuffle,
      onCycleRepeat = onCycleRepeat,
      onOpenMixer = onOpenMixer,
      onOpenQueue = onOpenQueue
    )
  }

  TarkasDarkOverride(enabled = true) {
    val pageModifier = modifier
      .fillMaxSize()
      .onSizeChanged { pageHeightPx = it.height.toFloat().coerceAtLeast(1f) }
      .graphicsLayer {
        val p = pull
        translationY = p * size.height
        val s = 1f - 0.06f * p.coerceAtMost(1f)
        scaleX = s
        scaleY = s
        shape = RoundedCornerShape((Grid.u24.toPx() * (p * 5f).coerceAtMost(1f)))
        clip = p > 0f
      }
      .draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { dy -> pull = (pull + dy / pageHeightPx).coerceAtLeast(0f) },
        onDragStarted = { settleJob[0]?.cancel() },
        onDragStopped = { velocity -> settlePull(velocity) }
      )
      .playerBackdrop(backdrop)
      .statusBarsPadding()
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
      // No tab bar under the player: the controls clear the system bar, plus any banner that floats
      // over the page (LocalBarInset).
      .navigationBarsPadding()
      .padding(horizontal = Grid.margin)
      .padding(bottom = LocalBarInset.current)
      .testTag("music_full_player_card")

    if (landscape) {
      // Landscape: two equal halves. Left, the cover carousel with the title and the progress; right, the
      // controls (transport, volume and the other functions). The side covers are cut at the half's edge
      // so they never run over the controls.
      Row(modifier = pageModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          artworkPart(
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(vertical = Grid.u12),
            // The half ends at its edge: the side covers fade out well before it.
            Grid.u48
          )
          Column(modifier = Modifier.fillMaxWidth()) {
            titlePart()
            Spacer(Modifier.height(Grid.u12))
            progressPart()
          }
          Spacer(Modifier.height(Grid.u12))
        }
        Spacer(Modifier.width(Grid.margin))
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          CloseHandle(onClose = onClose, modifier = Modifier.revealWithPlayer())
          Spacer(Modifier.weight(1f))
          transportPart()
          Spacer(Modifier.height(Grid.u12))
          volumePart()
          Spacer(Modifier.height(Grid.u12))
          actionsPart()
          Spacer(Modifier.weight(1f))
        }
      }
    } else {
      Column(modifier = pageModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CloseHandle(onClose = onClose, modifier = Modifier.revealWithPlayer())

        artworkPart(
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = Grid.u12),
          // The side covers run off the screen: they fade out over the margin instead of being cut.
          Grid.margin
        )

        // Everything under the cover rides the sheet's top edge up as the player opens and fades in on
        // the same progress (revealWithPlayer): no element travels on its own path or appears out of
        // nothing.
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          titlePart()
          Spacer(Modifier.height(Grid.u12))
          progressPart()
          Spacer(Modifier.height(Grid.u12))
          transportPart()
          Spacer(Modifier.height(Grid.u12))
          volumePart()
          Spacer(Modifier.height(Grid.u12))
          actionsPart()
          Spacer(Modifier.height(Grid.u12))
        }
      }
    }
  }

  if (showDetails && currentTrack != null) {
    TrackDetailsSheet(
      track = currentTrack,
      // Details of another song (the track changed under an open sheet) are not shown as this one's.
      state = if (trackDetails.trackId == currentTrack.id) trackDetails else TrackDetailsState(loading = true),
      onRetry = { onLoadTrackDetails(currentTrack) },
      onDismiss = { showDetails = false }
    )
    LaunchedEffect(currentTrack.id) { onLoadTrackDetails(currentTrack) }
  }

  if (showLyrics && currentTrack != null) {
    LyricsSheet(
      track = currentTrack,
      state = lyrics,
      currentPositionMs = currentPositionMs,
      onSeek = onSeekTo,
      onDismiss = { showLyrics = false }
    )
    LaunchedEffect(currentTrack.id) { onLoadLyrics(currentTrack) }
  }

  if (showCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(curiosity = activeCuriosity, onDismiss = { showCuriosityDialog = false })
  }
}
