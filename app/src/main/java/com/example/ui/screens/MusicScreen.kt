package com.example.ui.screens

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Curiosity
import com.example.ui.theme.LocalBarInset
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.TrackActionsMenu
import com.example.ui.theme.TarkasDarkOverride
import com.example.ui.theme.glassControl
import com.example.ui.theme.glassContentColor
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

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
  sleepTimer: com.example.data.playback.SleepTimer? = null,
  onSetSleepTimer: (Int) -> Unit = {},
  onSleepAtEndOfTrack: () -> Unit = {},
  onCancelSleepTimer: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showCuriosityDialog by remember { mutableStateOf(false) }
  var showSleepTimer by remember { mutableStateOf(false) }
  if (showSleepTimer) {
    com.example.ui.components.SleepTimerDialog(
      timer = sleepTimer,
      onMinutes = onSetSleepTimer,
      onEndOfTrack = onSleepAtEndOfTrack,
      onCancel = onCancelSleepTimer,
      onDismiss = { showSleepTimer = false }
    )
  }
  val reduced = com.example.ui.theme.rememberReducedMotion()
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

  // Pulling the page down: 0 at rest, 1 a full screen down. The page follows the finger, shrinks a
  // little and rounds its corners; let go past 30% (or flick) and it closes into the mini player,
  // otherwise it springs back. The system back gesture previews the same pull (Android 14+).
  val pull = remember { androidx.compose.animation.core.Animatable(0f) }
  var pageHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
  val currentClose by rememberUpdatedState(onClose)
  fun settlePull(velocity: Float = 0f) {
    scope.launch {
      // Past 30% it closes; a short pull closes only with a real flick downwards.
      if (pull.value > 0.3f || (velocity > 3500f && pull.value > 0.1f)) {
        currentClose()
      } else {
        pull.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial())
      }
    }
  }
  androidx.activity.compose.PredictiveBackHandler { events ->
    try {
      events.collect { e -> if (!reduced) pull.snapTo(e.progress * 0.25f) }
      currentClose()
    } catch (e: kotlinx.coroutines.CancellationException) {
      pull.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial())
      throw e
    }
  }

  // The Now Playing page takes its tint from the artwork, like the Apple Music player sheet.
  // The tint glides to the next track's colour instead of jumping.
  val trackColor by animateColorAsState(
    targetValue = Color(currentTrack?.colorHex ?: 0xFF46C9D3),
    animationSpec = tween(durationMillis = 700, easing = Motion.Settle),
    label = "trackColor"
  )
  // The artwork shrinks a little when paused and swells back when playing, like Apple Music.
  val artScale = animateFloatAsState(
    targetValue = if (isPlaying) 1f else 0.86f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
    label = "artScale"
  )
  val pageTop = lerp(trackColor, Color.Black, 0.5f)
  val pageBottom = lerp(trackColor, Color.Black, 0.82f)
  val onPage = Color.White
  val onPageMuted = Color.White.copy(alpha = 0.72f)
  val trackFill = Color.White.copy(alpha = 0.28f)

  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
  var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
  // The slider follows the phone's volume buttons too (the system writes the level to its settings).
  DisposableEffect(audioManager) {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
      }
    }
    context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    onDispose { context.contentResolver.unregisterContentObserver(observer) }
  }

  // The now-playing page is always dark, so its glass controls use the dark scheme in any theme.
  TarkasDarkOverride(enabled = true) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .onSizeChanged { pageHeightPx = it.height.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
          val p = pull.value
          translationY = p * size.height
          val s = 1f - 0.08f * p.coerceAtMost(1f)
          scaleX = s
          scaleY = s
          shape = RoundedCornerShape((36f * (p * 4f).coerceAtMost(1f)).dp)
          clip = p > 0f
        }
        .draggable(
          orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
          state = androidx.compose.foundation.gestures.rememberDraggableState { dy ->
            scope.launch { pull.snapTo((pull.value + dy / pageHeightPx).coerceAtLeast(0f)) }
          },
          onDragStopped = { velocity -> settlePull(velocity) }
        )
        .background(Brush.verticalGradient(listOf(pageTop, pageBottom)))
        .statusBarsPadding()
        .padding(horizontal = 24.dp)
        .padding(bottom = LocalBarInset.current)
        .testTag("music_full_player_card"),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(10.dp))
      Box(
        modifier = Modifier
          .width(40.dp)
          .height(5.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.4f))
      )

      Spacer(modifier = Modifier.weight(1f))

      // Swiping the artwork sideways changes the track with a tick (Spotify, Apple Music): it follows
      // the finger, and the next cover comes in from the side the old one left towards.
      val artSwipe = remember { androidx.compose.animation.core.Animatable(0f) }
      val currentNext by rememberUpdatedState(onNextTrack)
      val currentPrevious by rememberUpdatedState(onPreviousTrack)
      Box(
        modifier = Modifier
          .pointerInput(Unit) {
            val threshold = size.width * 0.25f
            detectHorizontalDragGestures(
              onDragEnd = {
                val offset = artSwipe.value
                scope.launch {
                  if (kotlin.math.abs(offset) > threshold) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (offset < 0) currentNext() else currentPrevious()
                    if (!reduced) artSwipe.snapTo(if (offset < 0) size.width * 0.6f else -size.width * 0.6f)
                  }
                  artSwipe.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial())
                }
              },
              onDragCancel = { scope.launch { artSwipe.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial()) } }
            ) { change, dx ->
              change.consume()
              scope.launch { artSwipe.snapTo(artSwipe.value + dx) }
            }
          }
          .graphicsLayer {
            scaleX = artScale.value
            scaleY = artScale.value
            translationX = artSwipe.value
            rotationZ = artSwipe.value / size.width.coerceAtLeast(1f) * 6f
            alpha = 1f - (kotlin.math.abs(artSwipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
          }
          .fillMaxWidth(0.8f)
          .aspectRatio(1f)
          .shadow(24.dp, RoundedCornerShape(10.dp))
          .clip(RoundedCornerShape(10.dp))
          .background(
            Brush.linearGradient(
              listOf(trackColor, lerp(trackColor, Color.Black, 0.45f))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(72.dp)
        )
        currentTrack?.artworkUrl?.let { url ->
          AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = currentTrack?.title ?: "Nada tocando",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = onPage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = currentTrack?.artist ?: "Escolha uma música no Início",
            fontSize = 22.sp,
            color = onPageMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (currentTrack != null) {
          val isFav = currentTrack.id in favoriteIds
          FavoriteButton(
            isFavorite = isFav,
            tint = onPage,
            onToggle = {
              haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
              onToggleFavorite(currentTrack)
            }
          )
          Spacer(modifier = Modifier.width(8.dp))
          Box(
            modifier = Modifier
              .size(44.dp)
              .glassControl(CircleShape),
            contentAlignment = Alignment.Center
          ) {
            TrackActionsMenu(
              track = currentTrack,
              isFavorite = isFav,
              playlists = playlists,
              onToggleFavorite = { onToggleFavorite(currentTrack) },
              onPlayNext = { onPlayNext(currentTrack) },
              onAddToQueue = { onAddToQueue(currentTrack) },
              onAddToPlaylist = { id -> onAddToPlaylist(id, currentTrack) },
              onCreatePlaylist = { name -> onCreatePlaylist(name, currentTrack) },
              iconTint = onPage,
              icon = Icons.Default.MoreHoriz,
              sleepTimerLabel = com.example.ui.components.sleepTimerLabel(sleepTimer),
              onSleepTimer = { showSleepTimer = true }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Only this block reads the position, so the rest of the page is not redrawn as it ticks.
      PlayerProgress(
        positionMs = currentPositionMs,
        durationMs = durationMs,
        filled = onPage.copy(alpha = 0.85f),
        track = trackFill,
        labelColor = onPageMuted,
        onSeekTo = onSeekTo
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Transport: previous, play, next
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onPreviousTrack,
          modifier = Modifier.size(72.dp).testTag("music_btn_previous")
        ) {
          Icon(Icons.Default.SkipPrevious, contentDescription = "Faixa anterior", modifier = Modifier.size(52.dp), tint = onPage)
        }
        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier.size(88.dp).testTag("music_btn_play_pause")
        ) {
          com.example.ui.components.PlayPauseIcon(isPlaying = isPlaying, tint = onPage, modifier = Modifier.size(76.dp))
        }
        IconButton(
          onClick = onNextTrack,
          modifier = Modifier.size(72.dp).testTag("music_btn_next")
        ) {
          Icon(Icons.Default.SkipNext, contentDescription = "Próxima faixa", modifier = Modifier.size(52.dp), tint = onPage)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Volume
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = onPageMuted, modifier = Modifier.size(22.dp))
        ScrubBar(
          fraction = volume,
          description = "Volume",
          filled = onPage.copy(alpha = 0.85f),
          track = trackFill,
          onChange = { frac ->
            volume = frac
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (frac * maxVolume).roundToInt(), 0)
          },
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 12.dp)
            .testTag("music_volume_slider")
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = onPageMuted, modifier = Modifier.size(22.dp))
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Shuffle, repeat, curiosity and queue (the Apple Music bottom row)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Round glass buttons; shuffle and repeat take the "selected" glass while they are on.
        IconButton(
          onClick = onToggleShuffle,
          modifier = Modifier.size(52.dp).glassControl(CircleShape, selected = isShuffle).testTag("music_btn_shuffle")
        ) {
          Icon(
            imageVector = Icons.Default.Shuffle,
            contentDescription = if (isShuffle) "Aleatório ligado" else "Aleatório desligado",
            tint = if (isShuffle) glassContentColor(true) else onPageMuted
          )
        }
        val repeatOn = repeatMode != RepeatMode.OFF
        IconButton(
          onClick = onCycleRepeat,
          modifier = Modifier.size(52.dp).glassControl(CircleShape, selected = repeatOn).testTag("music_btn_repeat")
        ) {
          Icon(
            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = when (repeatMode) {
              RepeatMode.OFF -> "Repetir desligado"
              RepeatMode.ALL -> "Repetir tudo"
              RepeatMode.ONE -> "Repetir uma faixa"
            },
            tint = if (repeatOn) glassContentColor(true) else onPageMuted
          )
        }
        IconButton(
          onClick = { showCuriosityDialog = true },
          enabled = activeCuriosity != null,
          modifier = Modifier.size(52.dp).glassControl(CircleShape).testTag("music_btn_curiosity")
        ) {
          Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = if (activeCuriosity != null) "Curiosidade da faixa" else "Sem curiosidade verificada para esta faixa",
            tint = if (activeCuriosity != null) onPage else onPageMuted.copy(alpha = 0.3f)
          )
        }
        IconButton(
          onClick = onOpenQueue,
          modifier = Modifier.size(52.dp).glassControl(CircleShape).testTag("music_btn_queue")
        ) {
          Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Fila de reprodução", tint = onPage)
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  // Curiosity Detail Dialog when triggered
  if (showCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(
      curiosity = activeCuriosity,
      onDismiss = { showCuriosityDialog = false }
    )
  }
}

/** Progress bar and the two clocks. Takes providers so that only this composable follows the playback position. */
@Composable
private fun PlayerProgress(
  positionMs: () -> Long,
  durationMs: () -> Long,
  filled: Color,
  track: Color,
  labelColor: Color,
  onSeekTo: (Long) -> Unit
) {
  val duration = durationMs()
  val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
  // While the finger is on the bar, the bar and the clock follow it; the seek happens on release.
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val position = dragFraction?.let { (it * duration).toLong() } ?: positionMs()
  val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
  ScrubBar(
    fraction = progress,
    description = "Progresso da faixa",
    filled = filled,
    track = track,
    onChange = { frac -> if (duration > 0) dragFraction = frac },
    onRelease = { frac ->
      dragFraction = null
      // A firmer tick when the scrub lands on the very start or end.
      if (frac <= 0.01f || frac >= 0.99f) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
      if (duration > 0) onSeekTo((frac * duration).toLong())
    },
    modifier = Modifier.testTag("music_progress_slider")
  )
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    // Tabular figures keep the clocks from jittering as digits change. Until the track reports its
    // length, the remaining time is unknown, so it shows a dash instead of a misleading "-0:00".
    val clockStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = labelColor, fontFeatureSettings = "tnum")
    Text(text = formatClock(position), style = clockStyle)
    Text(
      text = if (duration > 0) "-" + formatClock((duration - position).coerceAtLeast(0L)) else "—",
      style = clockStyle
    )
  }
}

/**
 * Thin rounded bar that can be tapped or dragged (track position and volume). [onChange] follows
 * the finger; [onRelease], when given, gets the final value once the finger lifts.
 */
@Composable
private fun ScrubBar(
  fraction: Float,
  description: String,
  filled: Color,
  track: Color,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  onRelease: ((Float) -> Unit)? = null
) {
  // The gesture blocks live as long as the bar, so they read the latest callbacks through these.
  val change by rememberUpdatedState(onChange)
  val release by rememberUpdatedState(onRelease)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(28.dp)
      .semantics { contentDescription = "$description ${(fraction * 100).roundToInt()}%" }
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          val f = (offset.x / size.width).coerceIn(0f, 1f)
          release?.invoke(f) ?: change(f)
        }
      }
      .pointerInput(Unit) {
        var last = 0f
        detectHorizontalDragGestures(
          onDragEnd = { release?.invoke(last) },
          onDragCancel = { release?.invoke(last) }
        ) { pointer, _ ->
          last = (pointer.position.x / size.width).coerceIn(0f, 1f)
          change(last)
        }
      },
    contentAlignment = Alignment.CenterStart
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(CircleShape)
        .background(track)
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(fraction.coerceIn(0f, 1f))
        .height(6.dp)
        .clip(CircleShape)
        .background(filled)
    )
  }
}

/**
 * The heart next to the title. Liking pops it (a quick overshoot) and sends out a small ring of
 * dots, the way the public knows from Twitter and Apple Music; unliking just settles it.
 */
@Composable
private fun FavoriteButton(isFavorite: Boolean, tint: Color, onToggle: () -> Unit) {
  val reduced = com.example.ui.theme.rememberReducedMotion()
  val pop = remember { androidx.compose.animation.core.Animatable(1f) }
  val burst = remember { androidx.compose.animation.core.Animatable(1f) }
  var wasFavorite by remember { mutableStateOf(isFavorite) }
  androidx.compose.runtime.LaunchedEffect(isFavorite) {
    if (isFavorite && !wasFavorite && !reduced) {
      launch {
        pop.snapTo(0.6f)
        pop.animateTo(1f, com.example.ui.theme.Motion.fastSpatial())
      }
      burst.snapTo(0f)
      burst.animateTo(1f, tween(420, easing = Motion.Settle))
    }
    wasFavorite = isFavorite
  }
  val dot = androidx.compose.material3.MaterialTheme.colorScheme.primary
  IconButton(
    onClick = onToggle,
    modifier = Modifier
      .size(44.dp)
      .drawBehind {
        val b = burst.value
        if (b in 0f..0.999f) {
          val r = size.minDimension * (0.35f + 0.45f * b)
          repeat(8) { i ->
            val a = Math.toRadians(i * 45.0)
            drawCircle(
              dot.copy(alpha = 1f - b),
              radius = 3.dp.toPx() * (1f - b * 0.6f),
              center = center + androidx.compose.ui.geometry.Offset((kotlin.math.cos(a) * r).toFloat(), (kotlin.math.sin(a) * r).toFloat())
            )
          }
        }
      }
      .testTag("music_btn_favorite")
  ) {
    Icon(
      imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
      contentDescription = if (isFavorite) "Remover dos favoritos" else "Favoritar",
      tint = if (isFavorite) dot else tint,
      modifier = Modifier
        .size(28.dp)
        .graphicsLayer {
          scaleX = pop.value
          scaleY = pop.value
        }
    )
  }
}

private fun formatClock(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  return String.format("%d:%02d", totalSec / 60, totalSec % 60)
}
