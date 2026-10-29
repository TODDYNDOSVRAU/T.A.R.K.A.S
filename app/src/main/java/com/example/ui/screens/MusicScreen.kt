package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.LocalHapticFeedback
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
import coil.request.SuccessResult
import com.example.data.model.Curiosity
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.SleepTimer
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

/** Artwork scale while paused: the cover eases back, as Apple Music does. */
private const val PausedArtScale = 0.86f

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
  fun settlePull(velocityPx: Float) {
    // Past 30% it closes; a shorter pull closes only with a real flick downwards.
    if (pull > 0.3f || (velocityPx > 1800f && pull > 0.06f)) currentClose() else springBack()
  }
  PredictiveBackHandler { events ->
    try {
      events.collect { e -> if (!currentReduced) pull = e.progress * 0.2f }
      currentClose()
    } catch (e: CancellationException) {
      springBack()
      throw e
    }
  }

  // ---- colour ----
  val tint = rememberArtworkTint(currentTrack)
  val colorSpec = if (reduced) snap<Color>() else tween<Color>(600, easing = FastOutSlowInEasing)
  val pageTop by animateColorAsState(pageTopFor(tint), colorSpec, label = "pageTop")
  val pageBottom by animateColorAsState(lerp(pageTopFor(tint), Color.Black, 0.6f), colorSpec, label = "pageBottom")
  // The container the player grows out of takes the same colour, so the opening has no seam.
  SideEffect { PlayerOrigin.tint = pageTop }

  // ---- volume ----
  val volume = rememberStreamVolume()

  TarkasDarkOverride(enabled = true) {
    Column(
      modifier = modifier
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
        .background(Brush.verticalGradient(listOf(pageTop, pageBottom)))
        .statusBarsPadding()
        // No tab bar under the player: the controls clear the system bar, plus any banner that floats
        // over the page (LocalBarInset).
        .navigationBarsPadding()
        .padding(horizontal = Space.xl)
        .padding(bottom = LocalBarInset.current)
        .testTag("music_full_player_card"),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      CloseHandle(onClose = onClose)

      Artwork(
        track = currentTrack,
        isPlaying = isPlaying,
        reduced = reduced,
        onNext = onNextTrack,
        onPrevious = onPreviousTrack,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(vertical = Space.l)
      )

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

      Spacer(Modifier.height(Space.l))

      // Only this block reads the position, so the rest of the page is not recomposed as it ticks.
      PlayerProgress(
        positionMs = currentPositionMs,
        durationMs = durationMs,
        knownDurationMs = currentTrack?.durationMs ?: 0L,
        isPlaying = isPlaying,
        reduced = reduced,
        onSeekTo = onSeekTo
      )

      Spacer(Modifier.height(Space.s))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        GlyphButton(
          icon = Icons.Rounded.SkipPrevious,
          contentDescription = "Faixa anterior",
          onClick = onPreviousTrack,
          touch = 72.dp,
          glyph = 44.dp,
          tint = OnPage,
          modifier = Modifier.testTag("music_btn_previous")
        )
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
        GlyphButton(
          icon = Icons.Rounded.SkipNext,
          contentDescription = "Próxima faixa",
          onClick = onNextTrack,
          touch = 72.dp,
          glyph = 44.dp,
          tint = OnPage,
          modifier = Modifier.testTag("music_btn_next")
        )
      }

      Spacer(Modifier.height(Space.s))

      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = OnPageMuted, modifier = Modifier.size(Size.iconSmall))
        ScrubBar(
          fraction = volume.fraction,
          description = "Volume",
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

      Spacer(Modifier.height(Space.s))

      // Curiosity (the app's lyrics), sleep timer, shuffle, repeat, queue: plain glyphs, the accent
      // marks what is on.
      val timerLabel = sleepTimerLabel(sleepTimer)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        GlyphButton(
          icon = Icons.Rounded.Lightbulb,
          contentDescription = "Curiosidade da faixa",
          stateDescription = if (activeCuriosity == null) "Sem curiosidade verificada para esta faixa" else null,
          onClick = { showCuriosityDialog = true },
          enabled = activeCuriosity != null,
          modifier = Modifier.testTag("music_btn_curiosity")
        )
        GlyphButton(
          icon = Icons.Rounded.Bedtime,
          contentDescription = "Timer de sono",
          stateDescription = timerLabel?.let { "Ligado, $it" } ?: "Desligado",
          active = timerLabel != null,
          onClick = { showSleepTimer = true },
          modifier = Modifier.testTag("music_btn_sleep_timer")
        )
        GlyphButton(
          icon = Icons.Rounded.Shuffle,
          contentDescription = "Aleatório",
          stateDescription = if (isShuffle) "Ligado" else "Desligado",
          active = isShuffle,
          onClick = onToggleShuffle,
          modifier = Modifier.testTag("music_btn_shuffle")
        )
        GlyphButton(
          icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
          contentDescription = "Repetir",
          stateDescription = when (repeatMode) {
            RepeatMode.OFF -> "Desligado"
            RepeatMode.ALL -> "Todas as faixas"
            RepeatMode.ONE -> "Uma faixa"
          },
          active = repeatMode != RepeatMode.OFF,
          onClick = onCycleRepeat,
          modifier = Modifier.testTag("music_btn_repeat")
        )
        GlyphButton(
          icon = Icons.AutoMirrored.Rounded.QueueMusic,
          contentDescription = "Fila de reprodução",
          onClick = onOpenQueue,
          modifier = Modifier.testTag("music_btn_queue")
        )
      }
      Spacer(Modifier.height(Space.s))
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
private fun CloseHandle(onClose: () -> Unit) {
  Box(
    modifier = Modifier
      .padding(top = Space.xs)
      .size(width = 72.dp, height = Size.touch)
      .clip(Shapes.pill)
      .clickable(role = Role.Button, onClick = onClose)
      .semantics { contentDescription = "Fechar player" }
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
 * The cover, as large as the space allows. Paused, it eases back to 86% and its shadow tightens; playing,
 * it returns to full size, on a critically damped spring (no wobble). Swiping it sideways changes the
 * track: it follows the finger and the next cover comes in from the side the old one left towards.
 */
@Composable
private fun Artwork(
  track: Track?,
  isPlaying: Boolean,
  reduced: Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scale by animateFloatAsState(
    targetValue = if (isPlaying || track == null) 1f else PausedArtScale,
    animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 260f),
    label = "artScale"
  )
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  var swipe by remember { mutableFloatStateOf(0f) }
  val swipeJob = remember { arrayOfNulls<Job>(1) }
  val currentNext by rememberUpdatedState(onNext)
  val currentPrevious by rememberUpdatedState(onPrevious)
  val currentReduced by rememberUpdatedState(reduced)

  BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val side: Dp = minOf(maxWidth, maxHeight)
    Box(
      modifier = Modifier
        .size(side)
        .pointerInput(Unit) {
          val threshold = size.width * 0.25f
          fun settle(skipped: Int) {
            swipeJob[0]?.cancel()
            swipeJob[0] = scope.launch {
              // Next (swiped left) brings the new cover in from the right, and the other way round.
              if (skipped != 0 && !currentReduced) swipe = skipped * size.width * 0.5f
              if (currentReduced) swipe = 0f
              else animate(swipe, 0f, animationSpec = Motion.criticalSpatial()) { v, _ -> swipe = v }
            }
          }
          detectHorizontalDragGestures(
            onDragStart = { swipeJob[0]?.cancel() },
            onDragEnd = {
              val offset = swipe
              val skipped = when {
                offset < -threshold -> 1
                offset > threshold -> -1
                else -> 0
              }
              if (skipped != 0) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (skipped > 0) currentNext() else currentPrevious()
              }
              settle(skipped)
            },
            onDragCancel = { settle(0) }
          ) { change, dx ->
            change.consume()
            swipe += dx
          }
        }
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          translationX = swipe
          alpha = 1f - (abs(swipe) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
          // The shadow is softer and wider when the cover is at full size, tighter when it steps back.
          shadowElevation = (8.dp + 16.dp * ((scale - PausedArtScale) / (1f - PausedArtScale)).coerceIn(0f, 1f)).toPx()
          shape = Shapes.m
          clip = true
        }
        .background(Color.White.copy(alpha = 0.08f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OnPage.copy(alpha = 0.5f), modifier = Modifier.size(side * 0.25f))
      track?.artworkUrl?.let { url ->
        val context = LocalContext.current
        val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(220).build() }
        AsyncImage(
          model = request,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize()
        )
      }
    }
  }
}

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
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track?.title ?: "Nada tocando",
        style = AppText.Title3,
        color = OnPage,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = track?.artist ?: "Escolha uma música no Início",
        style = AppText.Body,
        color = OnPageMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (track != null) {
      GlyphButton(
        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = "Favorito",
        stateDescription = if (isFavorite) "Nos favoritos" else "Fora dos favoritos",
        active = isFavorite,
        inactiveTint = OnPage,
        onClick = { onToggleFavorite(track) },
        modifier = Modifier.testTag("music_btn_favorite")
      )
      TrackActionsMenu(
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
  onSeekTo: (Long) -> Unit
) {
  val loadedDuration = durationMs()
  val rawPosition = positionMs()
  val haptics = LocalHapticFeedback.current
  // While the finger is on the bar, the bar and the clock follow it; the seek happens on release.
  var dragFraction by remember { mutableStateOf<Float?>(null) }
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
    description = "Posição na faixa",
    stateText = "${formatSpoken(position)} de ${if (duration > 0) formatSpoken(duration) else "duração desconhecida"}",
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
