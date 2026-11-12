package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import com.example.ui.theme.FrameGuard
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.request.SuccessResult
import com.example.data.model.Curiosity
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.CoverNeighbors
import com.example.data.playback.SleepTimer
import com.example.ui.theme.CoverCarousel
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.Constraints
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.PlayPauseIcon
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.TrackActionsMenu
import com.example.ui.components.TrackDetailsSheet
import com.example.ui.viewmodel.TrackDetailsState
import com.example.ui.components.sleepTimerLabel
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Motion
import com.example.ui.theme.PlayerOrigin
import com.example.ui.theme.PlayerTravel
import com.example.ui.theme.Radius
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.TarkasDarkOverride
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Text on the player sits on a dark tint in every theme. */
private val OnPage = Color.White
private val OnPageMuted = Color.White.copy(alpha = 0.7f)

/**
 * The full player (Now Playing). The page is tinted with the artwork's own colour, darkened until white
 * text clears 4.5:1, and is laid out like Apple Music's: large artwork, title and artist left-aligned
 * with favourite and "more" at the trailing edge, a thin scrubber, glyph-only transport, volume, and a
 * row of plain glyph buttons. It is pulled down (or the back gesture previewed) to close, and the handle
 * at the top is a real "Fechar player" button for anyone who cannot drag.
 */
@Composable
fun MusicScreen(
  currentTrack: Track?,
  isPlaying: Boolean,
  /** Drives the cover's size: true while a track is loading too, so a track change does not step it back. */
  coverPlaying: Boolean = isPlaying,
  currentPositionMs: () -> Long,
  durationMs: () -> Long,
  activeCuriosity: Curiosity?,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onPreviousTrack: () -> Unit,
  onSeekTo: (Long) -> Unit,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  favoriteIds: List<String>,
  playlists: List<Playlist>,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  onOpenQueue: () -> Unit = {},
  onClose: () -> Unit = {},
  sleepTimer: SleepTimer? = null,
  onSetSleepTimer: (Int) -> Unit = {},
  onSleepAtEndOfTrack: () -> Unit = {},
  onCancelSleepTimer: () -> Unit = {},
  trackDetails: TrackDetailsState = TrackDetailsState(),
  onLoadTrackDetails: (Track) -> Unit = {},
  /** The tracks beside the cover: what Previous and Next go to. */
  coverNeighbors: CoverNeighbors.Result = CoverNeighbors.Result(),
  /** Previous from the cover carousel: the cover of that track was swiped in, so it always moves. */
  onPreviousFromCover: () -> Unit = onPreviousTrack,
  modifier: Modifier = Modifier
) {
  var showCuriosityDialog by remember { mutableStateOf(false) }
  var showDetails by remember { mutableStateOf(false) }
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

  // ---- colour ----
  val tint = rememberArtworkTint(currentTrack)
  val colorSpec = if (reduced) snap<Color>() else tween<Color>(600, easing = FastOutSlowInEasing)
  val pageTop by animateColorAsState(pageTopFor(tint), colorSpec, label = "pageTop")
  // The container the player grows out of takes the same colour, so the opening has no seam.
  SideEffect { PlayerOrigin.tint = pageTop }

  // ---- cover behind the page ----
  // The cover, blurred, drifting slowly behind a heavy dark veil. It moves only while a track with a
  // cover plays and the frame guard allows it; otherwise (and with reduced motion) it stands still.
  val coverBlur = rememberCoverBlur(currentTrack)
  val coverClock = rememberCoverClock(running = !reduced && isPlaying && coverBlur != null)

  // ---- volume ----
  val volume = rememberStreamVolume()

  // The finger is on the progress bar: the cover comes forward a little (see PlayerTravel.coverScale).
  var scrubbing by remember { mutableStateOf(false) }

  // ---- the page's parts, shared by the portrait and the landscape layouts ----
  val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val timerLabel = sleepTimerLabel(sleepTimer)

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
      onAbout = { track -> onLoadTrackDetails(track); showDetails = true }
    )
  }
  // Only this block reads the position, so the rest of the page is not recomposed as it ticks.
  val progressPart: @Composable () -> Unit = {
    Column(modifier = Modifier.fillMaxWidth().revealWithPlayer()) {
      PlayerProgress(
        positionMs = currentPositionMs,
        durationMs = durationMs,
        knownDurationMs = currentTrack?.durationMs ?: 0L,
        isPlaying = isPlaying,
        reduced = reduced,
        onScrubbing = { scrubbing = it },
        onSeekTo = onSeekTo
      )
    }
  }
  val transportPart: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      GlyphButton(
        icon = Icons.Rounded.SkipPrevious,
        contentDescription = stringResource(R.string.player_screen_previous),
        onClick = onPreviousTrack,
        touch = 72.dp,
        glyph = 44.dp,
        tint = OnPage,
        modifier = Modifier.playerPart("prev", delay = 0.12f).testTag("music_btn_previous")
      )
      PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
      GlyphButton(
        icon = Icons.Rounded.SkipNext,
        contentDescription = stringResource(R.string.player_screen_next),
        onClick = onNextTrack,
        touch = 72.dp,
        glyph = 44.dp,
        tint = OnPage,
        modifier = Modifier.playerPart("next", delay = 0.12f).testTag("music_btn_next")
      )
    }
  }
  val volumePart: @Composable () -> Unit = {
    Row(modifier = Modifier.fillMaxWidth().revealWithPlayer(), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = OnPageMuted, modifier = Modifier.size(Size.iconSmall))
      ScrubBar(
        fraction = volume.fraction,
        description = stringResource(R.string.player_screen_volume),
        stateText = "${(volume.fraction * 100).roundToInt()}%",
        steps = (volume.max - 1).coerceAtLeast(0),
        reduced = reduced,
        onChange = { volume.set(it, final = false) },
        onRelease = { volume.set(it, final = true) },
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = Space.m)
          .testTag("music_volume_slider")
      )
      Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = OnPageMuted, modifier = Modifier.size(Size.iconSmall))
    }
  }
  // Curiosity (the app's lyrics), sleep timer, shuffle, repeat, queue: plain glyphs, the accent marks
  // what is on.
  val actionsPart: @Composable () -> Unit = {
    Row(
      modifier = Modifier.fillMaxWidth().revealWithPlayer(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      GlyphButton(
        icon = Icons.Rounded.Lightbulb,
        contentDescription = stringResource(R.string.player_screen_curiosity),
        stateDescription = if (activeCuriosity == null) stringResource(R.string.player_screen_curiosity_none) else null,
        onClick = { showCuriosityDialog = true },
        enabled = activeCuriosity != null,
        modifier = Modifier.testTag("music_btn_curiosity")
      )
      GlyphButton(
        icon = Icons.Rounded.Bedtime,
        contentDescription = stringResource(R.string.player_screen_sleep_timer),
        stateDescription = timerLabel?.let { stringResource(R.string.player_screen_on_with, it) } ?: stringResource(R.string.player_screen_off),
        active = timerLabel != null,
        onClick = { showSleepTimer = true },
        modifier = Modifier.testTag("music_btn_sleep_timer")
      )
      GlyphButton(
        icon = Icons.Rounded.Shuffle,
        contentDescription = stringResource(R.string.player_screen_shuffle),
        stateDescription = if (isShuffle) stringResource(R.string.player_screen_on) else stringResource(R.string.player_screen_off),
        active = isShuffle,
        onClick = onToggleShuffle,
        modifier = Modifier.testTag("music_btn_shuffle")
      )
      GlyphButton(
        icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
        contentDescription = stringResource(R.string.player_screen_repeat),
        stateDescription = when (repeatMode) {
          RepeatMode.OFF -> stringResource(R.string.player_screen_off)
          RepeatMode.ALL -> stringResource(R.string.player_screen_repeat_all)
          RepeatMode.ONE -> stringResource(R.string.player_screen_repeat_one)
        },
        active = repeatMode != RepeatMode.OFF,
        onClick = onCycleRepeat,
        modifier = Modifier.testTag("music_btn_repeat")
      )
      GlyphButton(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        contentDescription = stringResource(R.string.player_screen_queue),
        onClick = onOpenQueue,
        modifier = Modifier.testTag("music_btn_queue")
      )
    }
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
        shape = RoundedCornerShape((Radius.xl.toPx() * (p * 5f).coerceAtMost(1f)))
        clip = p > 0f
      }
      .draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { dy -> pull = (pull + dy / pageHeightPx).coerceAtLeast(0f) },
        onDragStarted = { settleJob[0]?.cancel() },
        onDragStopped = { velocity -> settlePull(velocity) }
      )
      .background(pageTop)
      .coverBackdrop(coverBlur, coverClock)
      .statusBarsPadding()
      // No tab bar under the player: the controls clear the system bar, plus any banner that floats
      // over the page (LocalBarInset).
      .navigationBarsPadding()
      .padding(horizontal = Space.xl)
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
              .padding(vertical = Space.s),
            // The half ends at its edge: the side covers fade out well before it.
            Space.xl * 2.5f
          )
          Column(modifier = Modifier.fillMaxWidth()) {
            titlePart()
            progressPart()
          }
          Spacer(Modifier.height(Space.s))
        }
        Spacer(Modifier.width(Space.xl))
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          CloseHandle(onClose = onClose, modifier = Modifier.revealWithPlayer())
          Spacer(Modifier.weight(1f))
          transportPart()
          Spacer(Modifier.height(Space.s))
          volumePart()
          Spacer(Modifier.height(Space.s))
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
            .padding(vertical = Space.l),
          // The side covers run off the screen: they fade out over the margin instead of being cut.
          Space.xl
        )

        // Everything under the cover rides the sheet's top edge up as the player opens and fades in on
        // the same progress (revealWithPlayer): no element travels on its own path or appears out of
        // nothing.
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          titlePart()
          Spacer(Modifier.height(Space.l))
          progressPart()
          Spacer(Modifier.height(Space.s))
          transportPart()
          Spacer(Modifier.height(Space.s))
          volumePart()
          Spacer(Modifier.height(Space.s))
          actionsPart()
          Spacer(Modifier.height(Space.s))
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

  if (showCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(curiosity = activeCuriosity, onDismiss = { showCuriosityDialog = false })
  }
}

/** The grabber at the top. Dragging works anywhere on the page; this also closes on a tap. */
@Composable
private fun CloseHandle(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val closeLabel = stringResource(R.string.player_screen_close)
  Box(
    modifier = modifier
      .padding(top = Space.xs)
      .size(width = 72.dp, height = Size.touch)
      .clip(Shapes.pill)
      .clickable(role = Role.Button, onClick = onClose)
      .semantics { contentDescription = closeLabel }
      .testTag("music_btn_close"),
    contentAlignment = Alignment.Center
  ) {
    Box(
      Modifier
        .size(width = 36.dp, height = 5.dp)
        .clip(Shapes.pill)
        .background(OnPage.copy(alpha = 0.4f))
    )
  }
}

/**
 * The cover carousel. The current cover sits in the middle, as large as the space allows; the previous
 * track's cover rests behind it on the left and the next track's on the right, smaller, darker and
 * turned slightly away (see [CoverCarousel]). Paused, the middle cover eases back to 86% and its shadow
 * tightens; playing, it returns to full size, on a critically damped spring. Swiping follows the finger
 * 1:1 and the cover on the other side grows into the middle; a tap on a side cover goes to that track.
 * Only the middle cover travels to and from the mini player. The side covers wait behind it until the
 * player has opened and then slide out to their places ([spread]); when the player starts to close
 * (or is pulled down) they slide back behind it and are gone before the middle cover reaches the mini
 * player.
 */
@Composable
private fun Artwork(
  track: Track?,
  neighbors: CoverNeighbors.Result,
  isPlaying: Boolean,
  scrubbing: Boolean,
  reduced: Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  closingByHand: () -> Boolean,
  /** Width over which the carousel fades out at its left and right edges. */
  edgeFade: Dp,
  modifier: Modifier = Modifier
) {
  // [isPlaying] here is "playing or a track on its way" (PlaybackController.wantsToPlay), so the
  // moment between two tracks does not read as a pause: the cover stays full size through the change.
  val playScale = animateFloatAsState(
    targetValue = PlayerTravel.coverScale(isPlaying, track != null, scrubbing),
    // Jelly: pausing and playing swing a little past the size and settle back.
    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 260f),
    label = "artScale"
  )
  // A track change gives the middle cover a light jelly: a small squash that settles at once. 1 at the
  // change, back to 0 with a springy settle; not on the first track shown, and not with reduced motion.
  val changePop = remember { Animatable(0f) }
  var firstTrack by remember { mutableStateOf(true) }
  LaunchedEffect(trackIdForPop(track)) {
    if (firstTrack) { firstTrack = false; return@LaunchedEffect }
    if (reduced) return@LaunchedEffect
    changePop.snapTo(1f)
    changePop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f, visibilityThreshold = 0.001f))
  }
  // Where the middle cover rests in the window, so it can travel from the mini player (or the song
  // that was tapped) to here as the player opens, and back as it closes.
  var resting by remember { mutableStateOf<Rect?>(null) }
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val currentNext by rememberUpdatedState(onNext)
  val currentPrevious by rememberUpdatedState(onPrevious)
  val currentReduced by rememberUpdatedState(reduced)

  // The neighbours are computed with the current track; until they arrive for a new one, the
  // carousel shows the track alone rather than the old track's neighbours.
  val shown = if (neighbors.current != null) neighbors else CoverNeighbors.Result(current = track)
  val canPrevious by rememberUpdatedState(shown.canGoPrevious)
  val canNext by rememberUpdatedState(shown.canGoNext)
  val carousel = remember { CarouselState() }
  val cards = remember(shown) { carousel.arrange(shown) }
  val center = carousel.center

  // ---- the side covers coming out from behind the middle one ----
  // One number for all of them (CoverCarousel.layoutAt): 0 behind the middle cover and unseen, 1 in
  // their places. It goes to 1 once the opening has landed (or at once when the player opens without
  // travelling) and to 0 as soon as the player closes or is pulled down; changing tracks leaves it alone.
  val spread = remember { Animatable(0f) }
  val currentClosingByHand by rememberUpdatedState(closingByHand)
  LaunchedEffect(Unit) {
    snapshotFlow {
      val p = PlayerOrigin.progress
      when {
        currentClosingByHand() -> false
        p == null -> true
        // Read on each frame of the closing spring, so it is seen on its first frame.
        PlayerOrigin.closing -> false
        else -> p.value >= SidesOutAt
      }
    }.distinctUntilChanged().collectLatest { out ->
      when {
        // Reduced motion: no movement, a plain fade (layoutAt keeps the slots and fades them).
        currentReduced -> spread.animateTo(if (out) 1f else 0f, tween(if (out) 200 else 100))
        // Out: response 0.35 s (stiffness (2π / 0.35)² ≈ 322) with a jelly spring: the covers swing past
        // their places by about 13% and settle back (CoverCarousel.MaxSpread allows it).
        out -> spread.animateTo(
          1f,
          spring(dampingRatio = Motion.JellyDamping, stiffness = Motion.JellyStiffness, visibilityThreshold = 0.001f)
        )
        // Back (pulled down): response 0.15 s ((2π / 0.15)² ≈ 1755), behind the middle cover within
        // about 150 ms. Closing itself hides them at once (spreadNow).
        else -> spread.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1755f, visibilityThreshold = 0.001f))
      }
    }
  }

  /**
   * Moves the camera to the card at [target] on a spring that starts at the finger's [velocity]
   * (steps per second). Response 0.3 s (stiffness (2π / 0.3)² ≈ 439), no overshoot; a flick may
   * bounce slightly (damping 0.8). Once it is all but there (CommitDistance), the track changes (one haptic was given
   * at the release); if the track does not change, it goes back to the centre.
   */
  fun settle(target: Int, velocity: Float, flick: Boolean) {
    carousel.job?.cancel()
    carousel.target = target
    carousel.job = scope.launch {
      var committed = false
      fun commitIfDue(at: Float) {
        if (committed || target == carousel.center || abs(at - target) > CommitDistance) return
        committed = true
        carousel.pending = target
        if (target > carousel.center) currentNext() else currentPrevious()
      }
      if (currentReduced) {
        carousel.camera.floatValue = target.toFloat()
        commitIfDue(target.toFloat())
      } else {
        val spec = spring<Float>(
          dampingRatio = if (flick) Motion.FlickDamping else Motion.SwipeDamping,
          stiffness = CarouselStiffness,
          visibilityThreshold = 0.001f
        )
        animate(carousel.camera.floatValue, target.toFloat(), velocity, spec) { v, vel ->
          carousel.camera.floatValue = v
          carousel.velocity.floatValue = vel
          commitIfDue(v)
        }
        carousel.velocity.floatValue = 0f
      }
      if (committed) {
        delay(700)
        if (carousel.center != target) {
          carousel.target = carousel.center
          if (currentReduced) carousel.camera.floatValue = carousel.center.toFloat()
          else animate(carousel.camera.floatValue, carousel.center.toFloat(), 0f, Motion.criticalSpatial(CarouselStiffness)) { v, vel ->
            carousel.camera.floatValue = v
            carousel.velocity.floatValue = vel
          }
        }
      }
    }
  }

  fun go(step: Int) {
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    settle(carousel.center + step, 0f, flick = false)
  }

  // The track changed by other means (the buttons, the end of a track, the queue): slide to it.
  LaunchedEffect(center) {
    if (!carousel.dragging && carousel.target != center) settle(center, 0f, flick = false)
  }

  val carouselContext = LocalContext.current
  BoxWithConstraints(modifier = modifier.bleedHorizontally(Space.xl).carouselEdgeFade(edgeFade), contentAlignment = Alignment.Center) {
    // The middle cover keeps the size the single cover had, inside the page's margins
    // (CoverCarousel.centreSide); the side covers show in the margins and run off the screen.
    val density = LocalDensity.current
    val side: Dp = with(density) { CoverCarousel.centreSide(maxWidth.toPx(), maxHeight.toPx(), Space.xl.toPx()).toDp() }
    val sidePx by rememberUpdatedState(with(density) { side.toPx() })
    val coverPx = with(density) { side.roundToPx() }
    SideEffect { CarouselCovers.sizePx = coverPx }
    val stepPx by rememberUpdatedState(CoverCarousel.stepPx(sidePx))
    val minTouch = with(LocalDensity.current) { Size.touch.toPx() }
    Box(
      Modifier
        .size(side)
        .onGloballyPositioned { resting = it.boundsInWindow() }
    )
    Layout(
      content = {
        for (card in cards) {
          key(card.index) {
            CarouselCover(
              track = card.track,
              index = card.index,
              isCenter = card.index == center,
              carousel = carousel,
              side = side,
              coverPx = coverPx,
              playScale = playScale,
              changePop = changePop,
              spread = spread,
              resting = { resting },
              reduced = reduced
            )
          }
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(side)
        .semantics {
          contentDescription = track?.let { carouselContext.getString(R.string.player_screen_cover_of, it.title, it.artist) }
            ?: carouselContext.getString(R.string.player_screen_no_track)
          customActions = listOfNotNull(
            shown.previous?.let { p -> CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_previous_named, p.title)) { go(-1); true } },
            shown.next?.let { n -> CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_next_named, n.title)) { go(1); true } }
              ?: if (shown.nextUnknown) CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_next)) { go(1); true } else null
          )
        }
        .pointerInput(Unit) {
          val tracker = VelocityTracker()
          // Where the finger has put the camera, before the rubber band; absolute, so a change of
          // centre in the middle of a drag (a swipe settling just before) does not move anything.
          var raw = 0f
          fun release(velocity: Float) {
            carousel.dragging = false
            val offset = carousel.camera.floatValue - carousel.center
            val step = CoverCarousel.targetStep(offset, velocity, canPrevious, canNext)
            if (step != 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            settle(carousel.center + step, velocity, flick = step != 0 && abs(velocity) > CoverCarousel.FlickVelocity)
          }
          detectHorizontalDragGestures(
            onDragStart = {
              // Caught in the middle of a settle: it goes on from where it is.
              carousel.job?.cancel()
              carousel.dragging = true
              tracker.resetTracking()
              raw = carousel.camera.floatValue
            },
            onDragEnd = { release(-tracker.calculateVelocity().x / stepPx) },
            onDragCancel = { release(0f) }
          ) { change, dx ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            raw -= dx / stepPx
            val c = carousel.center
            val low = if (canPrevious) -1f else 0f
            val high = if (canNext) 1f else 0f
            carousel.camera.floatValue = c + CoverCarousel.band(raw - c, low, high)
            carousel.velocity.floatValue = -tracker.calculateVelocity().x / stepPx
          }
        }
        .pointerInput(Unit) {
          detectTapGestures { pos ->
            // The side covers show only a strip; each side's target is that strip, at least 48 dp wide.
            val half = minOf(sidePx * playScale.value / 2f, size.width / 2f - minTouch)
            val mid = size.width / 2f
            // Not while the side covers are still behind the middle one.
            if (spread.value < 0.5f) return@detectTapGestures
            when {
              pos.x < mid - half && canPrevious -> go(-1)
              pos.x > mid + half && canNext -> go(1)
            }
          }
        }
        .testTag("music_artwork_carousel")
    ) { measurables, constraints ->
      val px = side.roundToPx()
      val placeables = measurables.map { it.measure(Constraints.fixed(px, px)) }
      layout(constraints.maxWidth, px) {
        // The card nearest the middle is drawn on top. Read here (placement), not in composition.
        val cam = carousel.camera.floatValue
        placeables.forEachIndexed { i, p ->
          p.place((constraints.maxWidth - px) / 2, 0, zIndex = -abs(cards[i].index - cam))
        }
      }
    }
  }
}

/**
 * How close (in steps) the settling cover must be to the middle for the track to change: the cover is
 * visibly there, without waiting for the spring's long tail.
 */
private const val CommitDistance = 0.05f

/** Response 0.3 s for the carousel's springs: (2π / 0.3)². */
private const val CarouselStiffness = 439f

/**
 * Opening progress at which the middle cover counts as landed and the side covers come out. The
 * opening spring (damping 0.88) passes it in its last few frames.
 */
private const val SidesOutAt = 0.99f

/**
 * The covers of the carousel are loaded at one size, the middle cover's, in every slot (the side ones
 * are the same layout scaled down), and [request] is the only way they are asked for: shown or loaded
 * ahead, the same address at the same size makes the same memory-cache key, so a cover loaded ahead is
 * on screen in the first frame, without a crossfade (Coil fades only what was not in memory).
 */
object CarouselCovers {
  /** The middle cover's side in pixels, written by the player once it is laid out; 0 before. */
  @Volatile var sizePx: Int = 0

  fun request(context: Context, url: String, px: Int): ImageRequest =
    ImageRequest.Builder(context)
      .data(url)
      .size(px, px)
      // What ContentScale.Crop asks for; set here so a request made ahead decodes the same bitmap.
      .scale(Scale.FILL)
      // A cover smaller than this is not scaled up in memory.
      .precision(Precision.INEXACT)
      .memoryCacheKey("$url@$px")
      // Shown at once from memory while the request starts, so a cover loaded ahead never flashes the note.
      .placeholderMemoryCacheKey("$url@$px")
      .crossfade(220)
      .build()

  /** The middle cover's side before the player has been laid out: the width rule on the screen width. */
  fun estimatePx(screenWidthPx: Float, marginPx: Float): Int =
    CoverCarousel.centreSide(screenWidthPx, Float.MAX_VALUE, marginPx).roundToInt()
}

/**
 * Loads ahead the covers of up to [CoverNeighbors.Depth] tracks on each side of the current one (and
 * its own) as soon as they change, whether the player is open or not, so a cover arriving in the
 * middle or at a side is already in memory. Tracks without a cover are skipped (they show the note).
 */
@Composable
fun PrefetchCarouselCovers(neighbors: kotlinx.coroutines.flow.StateFlow<CoverNeighbors.Result>) {
  val n by neighbors.collectAsStateWithLifecycle()
  val context = LocalContext.current
  // The real pixel width (screenWidthDp is rounded down to whole dp, which would ask for a cover one
  // pixel off the one the player lays out, and miss the cache).
  val screenPx = LocalConfiguration.current.let { context.resources.displayMetrics.widthPixels.toFloat() }
  val marginPx = with(LocalDensity.current) { Space.xl.toPx() }
  LaunchedEffect(n) {
    val px = CarouselCovers.sizePx.takeIf { it > 0 } ?: CarouselCovers.estimatePx(screenPx, marginPx)
    if (px <= 0) return@LaunchedEffect
    val loader = context.imageLoader
    // Nearest first, so the covers about to be seen are asked for first.
    val tracks = listOfNotNull(n.current) + n.after.take(1) + n.before.take(1) +
      n.after.drop(1).take(CoverNeighbors.Depth - 1) + n.before.drop(1).take(CoverNeighbors.Depth - 1)
    tracks.mapNotNull { it.artworkUrl }.distinct().forEach { url ->
      loader.enqueue(CarouselCovers.request(context, url, px))
    }
  }
}

/** Covers drawn on each side of the middle one; the third neighbour is only loaded ahead. */
private const val Drawn = 2

/** A cover of the carousel: its place on the running index, and the track it shows. */
private class CarouselCard(val index: Int, val track: Track?)

/**
 * The carousel's state while the player is open. [camera] is where the view is along the cards'
 * running index (see [CoverCarousel.rebaseCenter]); only placement and drawing read it, so dragging
 * recomposes nothing.
 */
private class CarouselState {
  val camera = mutableFloatStateOf(0f)
  /** How fast the camera moves, in steps per second: the covers stretch with it (read in graphicsLayer only). */
  val velocity = mutableFloatStateOf(0f)
  /** The index of the current track's card. */
  var center = 0
  /** The index a settle is heading for. */
  var target = 0
  /** The index whose track was just asked for, until the track changes to it. */
  var pending: Int? = null
  var dragging = false
  var job: Job? = null
  private var cards: List<CarouselCard> = emptyList()
  private var arrangements = 0

  /** False for the covers composed with the player, which come out with the spread instead. */
  val fadeNewCards: Boolean get() = arrangements > 1

  /**
   * Places the current track and up to [Drawn] neighbours on each side (nearest first) around the card
   * that already shows it.
   */
  fun arrange(n: CoverNeighbors.Result): List<CarouselCard> {
    val c = if (arrangements == 0) 0 else CoverCarousel.rebaseCenter(
      cards.associate { it.index to (it.track?.id ?: "") }, center, pending, n.current?.id, camera.floatValue
    )
    // A cover that left past the second slot stays until it has faded out there (CoverCarousel.layoutAt).
    val leaving = cards.filter { abs(it.index - c) == Drawn + 1 }
    cards = leaving +
      n.before.take(Drawn).mapIndexed { i, t -> CarouselCard(c - 1 - i, t) }.reversed() +
      CarouselCard(c, n.current) +
      n.after.take(Drawn).mapIndexed { i, t -> CarouselCard(c + 1 + i, t) }
    if (pending == c) pending = null
    center = c
    arrangements++
    return cards
  }
}

/**
 * One cover. Every frame-to-frame change (place, size, turn, fade, scrim, shadow) is a layer property
 * read in the draw phase. The middle one also carries the travel to and from the mini player.
 */
@Composable
private fun CarouselCover(
  track: Track?,
  index: Int,
  isCenter: Boolean,
  carousel: CarouselState,
  side: Dp,
  coverPx: Int,
  playScale: State<Float>,
  changePop: Animatable<Float, *>,
  spread: Animatable<Float, *>,
  resting: () -> Rect?,
  reduced: Boolean
) {
  /**
   * How far the side covers are out: the spread, and nothing from the first frame of closing, so the
   * middle cover never travels to the mini player with covers beside it (a pull down, which comes
   * before, slides them back with the spread itself).
   */
  fun spreadNow(): Float = if (PlayerOrigin.closing && PlayerOrigin.progress != null) 0f else spread.value
  // A cover that arrives at a side after the player opened fades in rather than popping in.
  val appear = remember { Animatable(if (carousel.fadeNewCards) 0f else 1f) }
  LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, tween(180)) }
  Box(
    modifier = Modifier
      .size(side)
      .graphicsLayer {
        val t = if (isCenter) coverTravel(resting(), reduced) else null
        if (t != null) {
          transformOrigin = TransformOrigin(0f, 0f)
          scaleX = t.scale
          scaleY = t.scale
          translationX = t.dx
          translationY = t.dy
        } else if (isCenter && !reduced && resting() == null && PlayerOrigin.progress != null) {
          // Not measured yet: stay hidden for the frame it takes, rather than flash at the resting place.
          alpha = 0f
        }
      }
      .graphicsLayer {
        val o = index - carousel.camera.floatValue
        val slot = CoverCarousel.layoutAt(o, reduced, spreadNow())
        val near = abs(o).coerceAtMost(1f)
        val t = if (isCenter) coverTravel(resting(), reduced) else null
        // While travelling the cover is the mini player's size, so it does not step back as well.
        val p = t?.progress ?: 1f
        val play = 1f + (playScale.value - 1f) * p
        // Pausing steps back only the middle cover; the sides stay where they are.
        val s = slot.scale * (play + (1f - play) * near)
        // Jelly: the faster the carousel moves the more the covers stretch along it and flatten, and
        // they come back as it settles. Not with reduced motion.
        val stretch = if (reduced) 0f else (abs(carousel.velocity.floatValue) / 5f).coerceIn(0f, 1f) * 0.04f * (1f - near)
        // Light jelly on a track change, the middle cover only: a little wider and flatter, settling back.
        val pop = if (isCenter) changePop.value * 0.035f * p else 0f
        scaleX = s * (1f + stretch + pop)
        scaleY = s * (1f - stretch * 0.6f - pop * 0.7f)
        translationX = slot.shift * size.width
        rotationY = slot.rotationY
        cameraDistance = 12f * density
        alpha = slot.alpha * appear.value
        // The shadow is softer and wider at full size, tighter when it steps back, absent while it is
        // still the small cover of the mini player, and only under the middle cover.
        shadowElevation = if (near >= 1f) 0f else
          (8.dp + 16.dp * ((s - PlayerTravel.PausedScale) / (1f - PlayerTravel.PausedScale)).coerceIn(0f, 1f)).toPx() * p * (1f - near)
        shape = if (t != null) {
          RoundedCornerShape(PlayerTravel.cornerAt(Radius.s.toPx(), Radius.m.toPx(), t.progress, t.scale * s))
        } else {
          Shapes.m
        }
        clip = true
      }
      .background(Color.White.copy(alpha = 0.08f)),
    contentAlignment = Alignment.Center
  ) {
    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OnPage.copy(alpha = 0.5f), modifier = Modifier.size(side * 0.25f))
    // Before the player is laid out the cover has no size (0 px), and Coil refuses a request of that size.
    track?.artworkUrl?.takeIf { coverPx > 0 }?.let { url ->
      val context = LocalContext.current
      // The same request in every slot and in the loading ahead (CarouselCovers), so a cover moving
      // to the middle keeps its image and one loaded ahead is shown from memory at once.
      val request = remember(url, coverPx) { CarouselCovers.request(context, url, coverPx) }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
      )
    }
    // Darker at the sides: the identity's black, by opacity.
    Box(
      Modifier
        .matchParentSize()
        .graphicsLayer { alpha = CoverCarousel.layoutAt(index - carousel.camera.floatValue, reduced, spreadNow()).scrim }
        .background(AbsoluteBlack)
    )
    // Glass finish at the sides: a faint lift at the top and a hairline rim.
    Box(
      Modifier
        .matchParentSize()
        .graphicsLayer { alpha = abs(index - carousel.camera.floatValue).coerceAtMost(1f) }
        .drawBehind {
          drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), endY = size.height * 0.5f))
          val w = 1.dp.toPx()
          val r = Radius.m.toPx()
          drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(w / 2f, w / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(r),
            style = Stroke(width = w)
          )
        }
    )
  }
}

private val AbsoluteBlack = Color(0xFF080808)

/**
 * Fades the content to nothing over [width] at its left and right edges, so the side covers do not end
 * in a hard line at the screen's (or the half's) edge. The fade needs an offscreen layer, whose bounds
 * would cut the cover travelling to and from the mini player, so it is on only once the player has
 * opened and not while it closes.
 */
private fun Modifier.carouselEdgeFade(width: Dp): Modifier {
  fun settled(): Boolean = PlayerOrigin.progress?.let { it.value >= 1f && !PlayerOrigin.closing } ?: true
  // The offscreen layer cuts at its bounds, and the middle cover's shadow reaches well past the carousel
  // above and below: the layer is made taller than the carousel (room on both sides), while the
  // carousel itself keeps its size in the page's layout.
  val room = 72.dp
  return this
    .layout { measurable, c ->
      val extra = room.roundToPx() * 2
      val p = measurable.measure(
        c.copy(
          minHeight = if (c.minHeight > 0) c.minHeight + extra else 0,
          maxHeight = if (c.hasBoundedHeight) c.maxHeight + extra else c.maxHeight
        )
      )
      layout(p.width, (p.height - extra).coerceAtLeast(0)) { p.place(0, -extra / 2) }
    }
    .graphicsLayer {
      compositingStrategy = if (settled()) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
      else androidx.compose.ui.graphics.CompositingStrategy.Auto
    }
    .layout { measurable, c ->
      val extra = room.roundToPx() * 2
      val p = measurable.measure(
        c.copy(
          minHeight = (c.minHeight - extra).coerceAtLeast(0),
          maxHeight = if (c.hasBoundedHeight) (c.maxHeight - extra).coerceAtLeast(0) else c.maxHeight
        )
      )
      layout(p.width, p.height + extra) { p.place(0, extra / 2) }
    }
    .drawWithContent {
      drawContent()
      if (settled()) {
        val f = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
          brush = Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
          blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
      }
    }
}
/**
 * Lets the content reach [x] past both sides of the padding around it (the carousel's side covers
 * reach the screen's edges), while it still takes only the padded width in the layout.
 */
private fun Modifier.bleedHorizontally(x: Dp): Modifier = layout { measurable, constraints ->
  val extra = x.roundToPx() * 2
  val placeable = measurable.measure(
    constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
  )
  layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** The most the opening progress counts past 1 for the travelling cover (the spring overshoots about 8%). */
private const val OvershootCap = 1.2f

/** What a travelling cover needs at this frame: its progress and the transform that puts it on its path. */
private class CoverTravel(val progress: Float, val scale: Float, val dx: Float, val dy: Float)

/**
 * The cover's place on its way between the small cover it comes from (the mini player's, or the tapped
 * row's) and [resting], by the same progress that grows the player; towards the mini player when
 * closing. Null when it should simply sit at rest: fully open, reduced motion, or nothing to travel from.
 */
private fun coverTravel(resting: Rect?, reduced: Boolean): CoverTravel? {
  val progress = PlayerOrigin.progress ?: return null
  if (reduced || resting == null || resting.width <= 0f) return null
  // Opening may overshoot (the jelly spring): the cover then goes a little past its place and comes
  // back. Closing never exceeds 1.
  val raw = progress.value
  val p = raw.coerceIn(0f, if (PlayerOrigin.closing) 1f else OvershootCap)
  if (p == 1f) return null
  val from = (if (PlayerOrigin.closing) PlayerOrigin.miniCover else PlayerOrigin.openingCover) ?: return null
  val sx = from.width + (resting.width - from.width) * p
  val left = from.left + (resting.left - from.left) * p
  val top = from.top + (resting.top - from.top) * p
  return CoverTravel(p.coerceAtMost(1f), sx / resting.width, left - resting.left, top - resting.top)
}

/**
 * A part of the page that travels with the player: when the player opens out of the mini player (and
 * when it closes back into it) it moves, and scales, between the mini player's own part and its place,
 * on the player's progress: the title, play and the buttons beside it go to and from where they are in
 * the mini player. [part] names the mini player's counterpart ("title", "play", "next"; "prev" comes from
 * play); [byHeight] scales by height (text) instead of width. [delay] holds the part back a little, so
 * the parts follow the cover one after another instead of all at once. A part with no counterpart
 * (null, or the player opened from a row) fades in over the second half of the opening and out first when
 * closing, rising a little.
 */
private fun Modifier.playerPart(part: String? = null, byHeight: Boolean = false, delay: Float = 0f): Modifier = this
  .onGloballyPositioned { c ->
    if (part != null) {
      PlayerOrigin.pageCoords?.takeIf { it.isAttached && c.isAttached }?.let {
        PlayerOrigin.rest[part] = it.localBoundingBoxOf(c, clipBounds = false)
      }
    }
  }
  .graphicsLayer {
    val p = (PlayerOrigin.progress?.value ?: 1f).coerceIn(0f, 1f)
    if (p >= 1f) return@graphicsLayer
    val rest = part?.let { PlayerOrigin.rest[it] }
    val mini = if (part != null && (PlayerOrigin.closing || PlayerOrigin.fromMini)) {
      when (part) {
        "title" -> PlayerOrigin.miniTitle
        "next" -> PlayerOrigin.miniNext
        else -> PlayerOrigin.miniPlay
      }
    } else null
    if (rest != null && mini != null && rest.width > 0f && rest.height > 0f) {
      val q = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
      val from = mini.translate(-PlayerOrigin.pageOrigin)
      val s0 = if (byHeight) from.height / rest.height else from.width / rest.width
      val s = s0 + (1f - s0) * q
      transformOrigin = TransformOrigin(0f, 0f)
      scaleX = s
      scaleY = s
      translationX = (from.left - rest.left) * (1f - q)
      translationY = (from.top - rest.top) * (1f - q)
    } else {
      val t = ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)
      alpha = t * t * (3f - 2f * t)
      translationY = (1f - t) * 24.dp.toPx()
    }
  }

/** A part with no counterpart in the mini player: fades in and rises with the player (see [playerPart]). */
private fun Modifier.revealWithPlayer(): Modifier = playerPart()

/** Title and artist on the left; favourite and "more" on the right. */
@Composable
private fun TitleRow(
  track: Track?,
  isFavorite: Boolean,
  playlists: List<Playlist>,
  sleepTimer: SleepTimer?,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  onSleepTimer: () -> Unit,
  onAbout: (Track) -> Unit
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(
      modifier = Modifier
        .weight(1f)
        .playerPart("title", byHeight = true, delay = 0.05f)
    ) {
      // The words change with a short crossfade when the carousel moves to another track.
      Crossfade(targetState = track, animationSpec = tween(150), label = "titleSwap") { t ->
        Column {
          Text(
            text = t?.title ?: stringResource(R.string.player_screen_nothing_playing),
            style = AppText.Title3,
            color = OnPage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = t?.artist ?: stringResource(R.string.player_screen_pick_song),
            style = AppText.Body,
            color = OnPageMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
    if (track != null) {
      GlyphButton(
        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = stringResource(R.string.player_screen_favorite),
        stateDescription = if (isFavorite) stringResource(R.string.player_screen_favorite_on) else stringResource(R.string.player_screen_favorite_off),
        active = isFavorite,
        inactiveTint = OnPage,
        onClick = { onToggleFavorite(track) },
        modifier = Modifier.revealWithPlayer().testTag("music_btn_favorite")
      )
      TrackActionsMenu(
        modifier = Modifier.revealWithPlayer(),
        track = track,
        isFavorite = isFavorite,
        playlists = playlists,
        onToggleFavorite = { onToggleFavorite(track) },
        onPlayNext = { onPlayNext(track) },
        onAddToQueue = { onAddToQueue(track) },
        onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
        onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
        iconTint = OnPage,
        icon = Icons.Rounded.MoreHoriz,
        sleepTimerLabel = sleepTimerLabel(sleepTimer),
        onSleepTimer = onSleepTimer,
        onAbout = { onAbout(track) }
      )
    }
  }
}

/** Play and pause, the largest glyph on the page, with no circle behind it. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
  // The glyph (64 dp) travels to the mini player's (28 dp); the press feedback stays on the glyph.
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.defaultEffects(), label = "playPress")
  Box(
    modifier = Modifier
      .size(88.dp)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .testTag("music_btn_play_pause"),
    contentAlignment = Alignment.Center
  ) {
    PlayPauseIcon(
      isPlaying = isPlaying,
      tint = OnPage,
      modifier = Modifier
        .size(64.dp)
        .playerPart("play", delay = 0.08f)
        .graphicsLayer {
          val s = 1f - 0.08f * press
          scaleX = s
          scaleY = s
          alpha = 1f - 0.35f * press
        }
    )
  }
}

/**
 * A glyph-only button for the player: 48 dp (or [touch]) target, no fill or ripple. The press dims and
 * slightly shrinks the glyph, critically damped. [active] tints it with the accent (shuffle on, timer
 * set); [stateDescription] tells TalkBack the state that the colour shows.
 */
@Composable
private fun GlyphButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  stateDescription: String? = null,
  active: Boolean = false,
  enabled: Boolean = true,
  touch: Dp = Size.touch,
  glyph: Dp = Size.icon,
  tint: Color = OnPageMuted,
  inactiveTint: Color = tint
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.defaultEffects(), label = "glyphPress")
  val target = when {
    !enabled -> OnPage.copy(alpha = 0.3f)
    active -> MaterialTheme.colorScheme.primary
    else -> inactiveTint
  }
  val color by animateColorAsState(target, tween(150), label = "glyphTint")
  Box(
    modifier = modifier
      .size(touch)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
      .semantics {
        this.contentDescription = contentDescription
        if (stateDescription != null) this.stateDescription = stateDescription
      },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = color,
      modifier = Modifier
        .size(glyph)
        .graphicsLayer {
          val s = 1f - 0.1f * press
          scaleX = s
          scaleY = s
          alpha = 1f - 0.4f * press
        }
    )
  }
}

/**
 * Scrubber and the two clocks. Takes providers so that only this composable follows the position.
 *
 * States: while loading (no duration yet) the bar is empty and the remaining time is the track's
 * known length, or a dash; once a track has ended (paused at its end) it reads as ready to play
 * again from the start, because Play restarts it, instead of a full bar and "-0:00".
 */
@Composable
private fun PlayerProgress(
  positionMs: () -> Long,
  durationMs: () -> Long,
  knownDurationMs: Long,
  isPlaying: Boolean,
  reduced: Boolean,
  onScrubbing: (Boolean) -> Unit,
  onSeekTo: (Long) -> Unit
) {
  val loadedDuration = durationMs()
  val rawPosition = positionMs()
  val haptics = LocalHapticFeedback.current
  // While the finger is on the bar, the bar and the clock follow it; the seek happens on release.
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val currentOnScrubbing by rememberUpdatedState(onScrubbing)
  val isScrubbing = dragFraction != null
  LaunchedEffect(isScrubbing) { currentOnScrubbing(isScrubbing) }
  val ended = !isPlaying && loadedDuration > 0 && rawPosition >= loadedDuration - 500
  val duration = if (loadedDuration > 0) loadedDuration else knownDurationMs
  val seekable = loadedDuration > 0
  val position = when {
    dragFraction != null -> (dragFraction!! * duration).toLong()
    ended || !seekable -> 0L
    else -> rawPosition.coerceIn(0L, duration)
  }
  val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
  val remaining = if (duration > 0) "-" + formatClock(duration - position) else "—"
  ScrubBar(
    fraction = fraction,
    description = stringResource(R.string.player_screen_position),
    stateText = stringResource(
      R.string.player_screen_position_of,
      formatSpoken(position),
      if (duration > 0) formatSpoken(duration) else stringResource(R.string.player_screen_duration_unknown)
    ),
    steps = 0,
    reduced = reduced,
    enabled = seekable,
    onChange = { dragFraction = it },
    onRelease = { frac ->
      dragFraction = null
      // A firmer tick when the scrub lands on the very start or end.
      if (frac <= 0.01f || frac >= 0.99f) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
      onSeekTo((frac * loadedDuration).toLong())
    },
    modifier = Modifier.testTag("music_progress_slider")
  )
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    // Tabular figures keep the clocks from jittering as digits change.
    val clockStyle = AppText.Caption1.copy(fontFeatureSettings = "tnum")
    Text(text = formatClock(position), style = clockStyle, color = OnPageMuted)
    Text(text = remaining, style = clockStyle, color = OnPageMuted)
  }
}

/**
 * A thin bar that can be tapped or dragged (position and volume). It draws 4 dp and grows to 10 dp
 * while dragged, inside a 48 dp touch area. [onChange] follows the finger; [onRelease] gets the final
 * value once it lifts. It is a slider for TalkBack too: range info, the current value read as
 * [stateText], and setProgress, so swipe up and down move it. [steps] is the number of stops between
 * the ends (0 = continuous).
 */
@Composable
private fun ScrubBar(
  fraction: Float,
  description: String,
  stateText: String,
  steps: Int,
  reduced: Boolean,
  onChange: (Float) -> Unit,
  onRelease: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  // The gesture blocks live as long as the bar, so they read the latest values through these.
  val change by rememberUpdatedState(onChange)
  val release by rememberUpdatedState(onRelease)
  val isEnabled by rememberUpdatedState(enabled)
  var dragging by remember { mutableStateOf(false) }
  val thickness by animateFloatAsState(
    targetValue = if (dragging) 10f else 4f,
    animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 900f),
    label = "scrubThickness"
  )
  val fill = if (dragging) OnPage else OnPage.copy(alpha = 0.85f)
  val rail = OnPage.copy(alpha = 0.22f)
  val shown = fraction.coerceIn(0f, 1f)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Size.touch)
      .semantics {
        contentDescription = description
        stateDescription = stateText
        progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f, steps)
        if (enabled) {
          setProgress { target ->
            release(target.coerceIn(0f, 1f))
            true
          }
        }
      }
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          if (isEnabled) release((offset.x / size.width).coerceIn(0f, 1f))
        }
      }
      .pointerInput(Unit) {
        var last = 0f
        detectHorizontalDragGestures(
          onDragStart = { offset ->
            if (isEnabled) {
              dragging = true
              last = (offset.x / size.width).coerceIn(0f, 1f)
              change(last)
            }
          },
          onDragEnd = {
            if (dragging) release(last)
            dragging = false
          },
          onDragCancel = {
            if (dragging) release(last)
            dragging = false
          }
        ) { pointer, _ ->
          if (dragging) {
            pointer.consume()
            last = (pointer.position.x / size.width).coerceIn(0f, 1f)
            change(last)
          }
        }
      }
      .drawBehind {
        val h = thickness.dp.toPx()
        val top = (size.height - h) / 2f
        val r = CornerRadius(h / 2f)
        drawRoundRect(rail, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = r)
        if (shown > 0f) {
          drawRoundRect(fill, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width * shown, h), cornerRadius = r)
        }
      }
  )
}

// ---- volume ----

/** The music stream's volume, kept in step with the hardware buttons and Bluetooth absolute volume. */
private class StreamVolume(private val audioManager: AudioManager) {
  val max: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
  var fraction by mutableFloatStateOf(read())
  /** True while the finger is on the slider, so the system's echo does not fight it. */
  var adjusting = false

  fun read(): Float = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max

  fun sync() {
    if (!adjusting) fraction = read()
  }

  fun set(value: Float, final: Boolean) {
    adjusting = !final
    fraction = value
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0)
    // On release the slider lands on the step the stream really took.
    if (final) fraction = read()
  }
}

/**
 * Listens for the system's volume broadcast (sent for the hardware keys and for Bluetooth absolute
 * volume, which the Settings.System observer used before missed), and re-reads the level when the
 * output device changes.
 */
@Composable
private fun rememberStreamVolume(): StreamVolume {
  val context = LocalContext.current
  val volume = remember { StreamVolume(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager) }
  DisposableEffect(context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context?, intent: Intent?) = volume.sync()
    }
    val filter = IntentFilter().apply {
      addAction("android.media.VOLUME_CHANGED_ACTION")
      addAction("android.media.STREAM_DEVICES_CHANGED_ACTION")
    }
    // Both are sent by the system, which reaches a non-exported receiver.
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    volume.sync()
    onDispose { context.unregisterReceiver(receiver) }
  }
  return volume
}

// ---- artwork colour ----

/** Shown while the artwork's colour is unknown and after it fails: a calm blue-grey ink. */
private val NeutralTint = Color(0xFF30363C)

/**
 * The colour the page is tinted with. It comes from the artwork's pixels: [Track.colorHex] is not the
 * cover's colour (web results carry a fixed brand colour, Audius a hashed one, so a red cover used to
 * give a green page). colorHex is used only for tracks without artwork, where it is the designed colour.
 * While a new cover is read, the previous colour stays, so the page never flashes a wrong one.
 */
@Composable
private fun rememberArtworkTint(track: Track?): Color {
  val context = LocalContext.current
  val url = track?.artworkUrl
  var tint by remember { mutableStateOf(url?.let { ArtworkColors.cached(it) }?.let { Color(it) } ?: NeutralTint) }
  LaunchedEffect(url, track?.colorHex) {
    tint = when {
      track == null -> NeutralTint
      url == null -> Color(track.colorHex)
      else -> ArtworkColors.cached(url)?.let { Color(it) }
        ?: ArtworkColors.extract(context, url)?.let { Color(it) }
        ?: NeutralTint
    }
  }
  return tint
}

/**
 * The cover as a 32 px bitmap, kept until the next one has loaded. Drawn scaled up with filtering it is
 * already a blur, so no blur effect runs on the GPU. Null for tracks without a cover.
 */
@Composable
private fun rememberCoverBlur(track: Track?): ImageBitmap? {
  val context = LocalContext.current
  val url = track?.artworkUrl
  var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(url) {
    if (url == null) {
      bitmap = null
      return@LaunchedEffect
    }
    val request = ImageRequest.Builder(context).data(url).size(32).allowHardware(false).build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return@LaunchedEffect
    bitmap = withContext(Dispatchers.Default) {
      ((drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(32, 32)).asImageBitmap()
    }
  }
  return bitmap
}

/**
 * Seconds of drift for the cover behind the page, advanced on every frame while [running]. It
 * measures the time between frames and stops for good (see [FrameGuard]) when the device cannot keep up.
 */
@Composable
private fun rememberCoverClock(running: Boolean): State<Float> {
  val seconds = remember { mutableFloatStateOf(0f) }
  // A frame is slow when it takes 1.5 refresh intervals or more, whatever the panel's rate is.
  val hz = LocalView.current.display?.refreshRate ?: 60f
  val guard = remember(hz) { FrameGuard(limitMs = 1500f / hz) }
  LaunchedEffect(running, guard) {
    if (!running) return@LaunchedEffect
    var last = 0L
    while (guard.allowed) {
      val now = withFrameNanos { it }
      if (last != 0L) {
        val delta = (now - last) / 1_000_000f
        guard.onFrame(delta)
        seconds.floatValue += delta / 1000f
      }
      last = now
    }
  }
  return seconds
}

private val CoverPeriods = floatArrayOf(120f, 90f, 70f)
private val CoverCentres = arrayOf(Offset(0.4f, 0.4f), Offset(0.6f, 0.5f), Offset(0.45f, 0.6f))
private val CoverSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.7f) })

/** Three copies of [bitmap] turning at different speeds, under a veil of the identity's black. */
private fun Modifier.coverBackdrop(bitmap: ImageBitmap?, seconds: State<Float>): Modifier = drawBehind {
  val image = bitmap ?: return@drawBehind
  // Large enough that the edges of the turning squares stay outside the page.
  val side = kotlin.math.hypot(size.width, size.height) * 1.5f
  clipRect {
    for (i in CoverPeriods.indices) {
      val centre = Offset(size.width * CoverCentres[i].x, size.height * CoverCentres[i].y)
      val direction = if (i == 1) -1f else 1f
      rotate(degrees = direction * 360f * seconds.value / CoverPeriods[i], pivot = centre) {
        drawImage(
          image = image,
          dstOffset = IntOffset((centre.x - side / 2).roundToInt(), (centre.y - side / 2).roundToInt()),
          dstSize = IntSize(side.roundToInt(), side.roundToInt()),
          alpha = 0.5f,
          colorFilter = CoverSaturation,
          filterQuality = FilterQuality.Low
        )
      }
    }
    drawRect(Color(0xFF080808).copy(alpha = 0.62f))
  }
}

/** The page's top colour: the tint, darkened until white text clears 4.5:1 with room for 70% white. */
private fun pageTopFor(tint: Color): Color {
  var c = tint
  repeat(16) { if (c.luminance() > 0.08f) c = lerp(c, Color.Black, 0.15f) }
  // A black cover would make a hole; lift it to a dark grey with a trace of its hue.
  if (c.luminance() < 0.012f) c = lerp(c, Color(0xFF2A2C30), 0.6f)
  return c
}

/** Reads the dominant colour of artwork bitmaps, off the main thread, with a small cache by URL. */
private object ArtworkColors {
  private val cache = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 48
  }

  fun cached(url: String): Int? = synchronized(cache) { cache[url] }

  suspend fun extract(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context)
      .data(url)
      .size(64)
      .allowHardware(false)
      .build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
    val color = withContext(Dispatchers.Default) {
      val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(64, 64)
      dominantColor(bitmap)
    }
    synchronized(cache) { cache[url] = color }
    return color
  }

  /**
   * The cover's dominant hue: pixels are grouped by hue in 12 bins weighted by how vivid they are, and
   * the heaviest bin's average is the colour. Grey and near-black pixels do not vote. A cover that is
   * mostly grey gives its plain average instead.
   */
  fun dominantColor(bitmap: Bitmap): Int {
    val step = max(1, max(bitmap.width, bitmap.height) / 48)
    val w = FloatArray(12)
    val rs = FloatArray(12)
    val gs = FloatArray(12)
    val bs = FloatArray(12)
    var ar = 0f
    var ag = 0f
    var ab = 0f
    var n = 0
    var y = 0
    while (y < bitmap.height) {
      var x = 0
      while (x < bitmap.width) {
        val p = bitmap.getPixel(x, y)
        x += step
        if ((p ushr 24) < 128) continue
        val r = ((p shr 16) and 0xFF) / 255f
        val g = ((p shr 8) and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        ar += r; ag += g; ab += b; n++
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val sat = if (mx > 0f) (mx - mn) / mx else 0f
        if (sat < 0.2f || mx < 0.12f) continue
        val d = mx - mn
        val hue = when (mx) {
          r -> 60f * (((g - b) / d) % 6f)
          g -> 60f * ((b - r) / d + 2f)
          else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val bin = (hue / 30f).toInt().coerceIn(0, 11)
        val weight = sat * mx
        w[bin] += weight
        rs[bin] += r * weight
        gs[bin] += g * weight
        bs[bin] += b * weight
      }
      y += step
    }
    if (n == 0) return NeutralTint.toArgbInt()
    val best = w.indices.maxBy { w[it] }
    // Fewer than ~4% of pixels carry colour: the cover is grey, black or white; use its average.
    return if (w[best] < n * 0.04f) {
      argb(ar / n, ag / n, ab / n)
    } else {
      argb(rs[best] / w[best], gs[best] / w[best], bs[best] / w[best])
    }
  }

  private fun argb(r: Float, g: Float, b: Float): Int =
    (0xFF shl 24) or ((r * 255).roundToInt().coerceIn(0, 255) shl 16) or
      ((g * 255).roundToInt().coerceIn(0, 255) shl 8) or (b * 255).roundToInt().coerceIn(0, 255)

  private fun Color.toArgbInt(): Int = argb(red, green, blue)
}

private fun formatClock(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  return String.format(java.util.Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60)
}

/** "2 min 05 s" for TalkBack, which reads "2:05" as a time of day. */
private fun formatSpoken(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val m = totalSec / 60
  val s = totalSec % 60
  return if (m > 0) "$m min $s s" else "$s s"
}

private fun trackIdForPop(track: Track?): String? = track?.id
