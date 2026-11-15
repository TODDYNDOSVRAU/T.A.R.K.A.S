package com.example.ui.screens.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.Curiosity
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.SleepTimer
import com.example.ui.components.PlayPauseIcon
import com.example.ui.components.TrackActionsMenu
import com.example.ui.components.WaveformBar
import com.example.ui.components.sleepTimerLabel
import com.example.ui.theme.AppText
import com.example.ui.theme.Motion
import com.example.ui.theme.PlayerOrigin
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.TarkasIce
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Text on the player sits on a dark tint in every theme. */
internal val OnPage = Color.White
internal val OnPageMuted = Color.White.copy(alpha = 0.7f)

/** The grabber at the top. Dragging works anywhere on the page; this also closes on a tap. */
@Composable
internal fun CloseHandle(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val closeLabel = stringResource(R.string.player_screen_close)
  Box(
    modifier = modifier
      .padding(top = Space.xs)
      .size(width = PlayerGrid.g96, height = PlayerGrid.g48)
      .clip(Shapes.pill)
      .clickable(role = Role.Button, onClick = onClose)
      .semantics { contentDescription = closeLabel }
      .testTag("music_btn_close"),
    contentAlignment = Alignment.Center
  ) {
    Box(
      Modifier
        .size(width = PlayerGrid.g48, height = 6.dp)
        .clip(Shapes.pill)
        .background(OnPage.copy(alpha = 0.4f))
    )
  }
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
internal fun Modifier.playerPart(part: String? = null, byHeight: Boolean = false, delay: Float = 0f): Modifier = this
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
      translationY = (1f - t) * Space.xl.toPx()
    }
  }

/** A part with no counterpart in the mini player: fades in and rises with the player (see [playerPart]). */
internal fun Modifier.revealWithPlayer(): Modifier = playerPart()

/** Title and artist on the left; favourite and "more" on the right. */
@Composable
internal fun TitleRow(
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
  onAbout: (Track) -> Unit,
  onLyrics: (Track) -> Unit
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
            style = AppText.Title3.copy(fontSize = PlayerGrid.text24, lineHeight = 30.sp),
            color = OnPage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = t?.artist ?: stringResource(R.string.player_screen_pick_song),
            style = AppText.Body.copy(fontSize = PlayerGrid.text18, lineHeight = 24.sp),
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
        onAbout = { onAbout(track) },
        onLyrics = { onLyrics(track) }
      )
    }
  }
}

/** Play and pause, the largest glyph on the page, with no circle behind it. */
@Composable
internal fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
  // The glyph (64 dp) travels to the mini player's (28 dp); the press feedback stays on the glyph.
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.defaultEffects(), label = "playPress")
  Box(
    modifier = Modifier
      .size(PlayerGrid.g96)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .testTag("music_btn_play_pause"),
    contentAlignment = Alignment.Center
  ) {
    PlayPauseIcon(
      isPlaying = isPlaying,
      tint = OnPage,
      modifier = Modifier
        .size(PlayerGrid.g48)
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
internal fun GlyphButton(
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
      // Decorative (contentDescription = null on purpose): the button around this icon carries the label
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
internal fun PlayerProgress(
  positionMs: () -> Long,
  durationMs: () -> Long,
  knownDurationMs: Long,
  isPlaying: Boolean,
  reduced: Boolean,
  onScrubbing: (Boolean) -> Unit,
  onSeekTo: (Long) -> Unit,
  waveform: ByteArray? = null
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
    waveform = waveform,
    modifier = Modifier.testTag("music_progress_slider")
  )
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    // Tabular figures keep the clocks from jittering as digits change.
    val clockStyle = AppText.Caption1.copy(fontSize = PlayerGrid.text12, fontFeatureSettings = "tnum")
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
internal fun ScrubBar(
  fraction: Float,
  description: String,
  stateText: String,
  steps: Int,
  reduced: Boolean,
  onChange: (Float) -> Unit,
  onRelease: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  /** The track's 100 amplitudes; when set the bar is drawn as a waveform (played part in Ice), else the thin rail. */
  waveform: ByteArray? = null
) {
  // The gesture blocks live as long as the bar, so they read the latest values through these.
  val change by rememberUpdatedState(onChange)
  val release by rememberUpdatedState(onRelease)
  val isEnabled by rememberUpdatedState(enabled)
  var dragging by remember { mutableStateOf(false) }
  val thickness by animateFloatAsState(
    targetValue = if (dragging) 10f else 4f,
    animationSpec = if (reduced) snap() else if (dragging) spring(dampingRatio = 1f, stiffness = 900f) else Motion.gel(),
    label = "scrubThickness"
  )
  val fill = if (dragging) OnPage else OnPage.copy(alpha = 0.85f)
  val rail = OnPage.copy(alpha = 0.22f)
  val shown = fraction.coerceIn(0f, 1f)
  var widthPx by remember { mutableStateOf(0) }
  val density = androidx.compose.ui.platform.LocalDensity.current
  val barPx = with(density) { WaveformBar.BAR_DP.dp.toPx() }
  val gapPx = with(density) { WaveformBar.GAP_DP.dp.toPx() }
  val bars = remember(waveform, widthPx) {
    if (waveform == null || widthPx <= 0) FloatArray(0)
    else WaveformBar.resample(waveform, WaveformBar.barCount(widthPx.toFloat(), barPx, gapPx))
  }
  // The waveform fades in over the thin bar when it arrives (and the thin one fades out).
  val waveAlpha by animateFloatAsState(
    targetValue = if (bars.isNotEmpty()) 1f else 0f,
    animationSpec = if (reduced) snap() else tween(durationMillis = 220),
    label = "waveformAlpha"
  )
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Size.touch)
      .onSizeChanged { widthPx = it.width }
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
        val thin = 1f - waveAlpha
        if (thin > 0f) {
          drawRoundRect(rail.copy(alpha = rail.alpha * thin), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = r)
          if (shown > 0f) {
            drawRoundRect(fill.copy(alpha = fill.alpha * thin), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width * shown, h), cornerRadius = r)
          }
        }
        if (waveAlpha > 0f) {
          with(WaveformBar) {
            drawBars(
              amplitudes = bars,
              fraction = shown,
              played = TarkasIce.copy(alpha = waveAlpha),
              rest = OnPage.copy(alpha = 0.4f * waveAlpha),
              barPx = barPx,
              gapPx = gapPx,
              grow = ((thickness - 4f) / 6f).coerceIn(0f, 1f)
            )
          }
        }
      }
  )
}

// ---- volume ----

/** The music stream's volume, kept in step with the hardware buttons and Bluetooth absolute volume. */
internal class StreamVolume(private val audioManager: AudioManager) {
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
internal fun rememberStreamVolume(): StreamVolume {
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

internal fun formatClock(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  return String.format(java.util.Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60)
}

/** "2 min 05 s" for TalkBack, which reads "2:05" as a time of day. */
internal fun formatSpoken(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val m = totalSec / 60
  val s = totalSec % 60
  return if (m > 0) "$m min $s s" else "$s s"
}

internal fun trackIdForPop(track: Track?): String? = track?.id

// ---- the rows under the cover, shared by the portrait and the landscape layouts ----

/** Only this block reads the position, so the rest of the page is not recomposed as it ticks. */
@Composable
internal fun ProgressRow(
  currentTrack: Track?,
  positionMs: () -> Long,
  durationMs: () -> Long,
  isPlaying: Boolean,
  reduced: Boolean,
  waveform: ByteArray?,
  onScrubbing: (Boolean) -> Unit,
  onSeekTo: (Long) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth().revealWithPlayer()) {
    PlayerProgress(
      positionMs = positionMs,
      durationMs = durationMs,
      knownDurationMs = currentTrack?.durationMs ?: 0L,
      isPlaying = isPlaying,
      reduced = reduced,
      onScrubbing = onScrubbing,
      onSeekTo = onSeekTo,
      waveform = waveform
    )
  }
}

@Composable
internal fun TransportRow(isPlaying: Boolean, onPrevious: () -> Unit, onTogglePlayPause: () -> Unit, onNext: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
  ) {
    GlyphButton(
      icon = Icons.Rounded.SkipPrevious,
      contentDescription = stringResource(R.string.player_screen_previous),
      onClick = onPrevious,
      touch = PlayerGrid.g48,
      glyph = PlayerGrid.g24,
      tint = OnPage,
      modifier = Modifier.playerPart("prev", delay = 0.12f).testTag("music_btn_previous")
    )
    PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
    GlyphButton(
      icon = Icons.Rounded.SkipNext,
      contentDescription = stringResource(R.string.player_screen_next),
      onClick = onNext,
      touch = PlayerGrid.g48,
      glyph = PlayerGrid.g24,
      tint = OnPage,
      modifier = Modifier.playerPart("next", delay = 0.12f).testTag("music_btn_next")
    )
  }
}

@Composable
internal fun VolumeRow(volume: StreamVolume, reduced: Boolean) {
  Row(modifier = Modifier.fillMaxWidth().revealWithPlayer(), verticalAlignment = Alignment.CenterVertically) {
    // Decorative (contentDescription = null on purpose): the volume slider beside it carries the label
    Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = OnPageMuted, modifier = Modifier.size(PlayerGrid.g24))
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
    // Decorative (contentDescription = null on purpose): the volume slider beside it carries the label
    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = OnPageMuted, modifier = Modifier.size(PlayerGrid.g24))
  }
}

/** Curiosity, sleep timer, shuffle, repeat, queue: plain glyphs, the accent marks what is on. */
@Composable
internal fun ActionsRow(
  hasCuriosity: Boolean,
  sleepTimer: SleepTimer?,
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  onCuriosity: () -> Unit,
  onSleepTimer: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onOpenQueue: () -> Unit
) {
  val timerLabel = sleepTimerLabel(sleepTimer)
  Row(
    modifier = Modifier.fillMaxWidth().revealWithPlayer(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    GlyphButton(
      icon = Icons.Rounded.Lightbulb,
      contentDescription = stringResource(R.string.player_screen_curiosity),
      stateDescription = if (!hasCuriosity) stringResource(R.string.player_screen_curiosity_none) else null,
      onClick = onCuriosity,
      enabled = hasCuriosity,
      modifier = Modifier.testTag("music_btn_curiosity")
    )
    GlyphButton(
      icon = Icons.Rounded.Bedtime,
      contentDescription = stringResource(R.string.player_screen_sleep_timer),
      stateDescription = timerLabel?.let { stringResource(R.string.player_screen_on_with, it) } ?: stringResource(R.string.player_screen_off),
      active = timerLabel != null,
      onClick = onSleepTimer,
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
