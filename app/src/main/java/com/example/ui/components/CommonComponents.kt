package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import com.example.ui.theme.glassPill
import com.example.ui.theme.glassControl
import com.example.ui.theme.glassContentColor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.theme.SpaceGrotesk
import com.example.ui.theme.pressClickable
import com.example.ui.theme.TarkasCyanPrimary
import com.example.ui.theme.TarkasSkySecondary
import com.example.ui.viewmodel.TarkasNavTab

/**
 * The app's one chip/pill: a capsule that follows the chosen surface style (solid, frosted, liquid).
 * Used for section tabs, quick actions and toggles so every balloon in the app looks the same.
 */
@Composable
fun GlassChip(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  enabled: Boolean = true
) {
  val content = glassContentColor(selected).copy(alpha = if (enabled) 1f else 0.4f)
  // A soft springy squeeze while pressed, read in the draw phase so it never recomposes.
  val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale = androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f),
    label = "chipPress"
  )
  Row(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        // A disabled chip is faded as a whole, so a filled one does not still look tappable.
        alpha = if (enabled) 1f else 0.45f
      }
      .heightIn(min = 44.dp)
      .glassControl(RoundedCornerShape(22.dp), selected = selected)
      .selectable(
        selected = selected,
        enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Button,
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = label,
      color = content,
      fontSize = 15.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      softWrap = false
    )
  }
}

/**
 * What a section shows when it has nothing: an icon, what happened, what to do, and a button for it.
 * Used for the offline, failed and empty states, so they all read the same.
 */
@Composable
fun EmptyState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {}
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = title,
      fontSize = 17.sp,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = message,
      fontSize = 15.sp,
      lineHeight = 20.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    if (actionLabel != null) {
      Spacer(modifier = Modifier.height(14.dp))
      GlassChip(label = actionLabel, selected = true, onClick = onAction)
    }
  }
}

/** Large bold page title with a round gear button opening Ajustes. */
@Composable
fun TarkasLargeTitle(
  title: String,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .fillMaxWidth()
      .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      modifier = Modifier.weight(1f),
      fontWeight = FontWeight.Bold,
      fontSize = 34.sp,
      letterSpacing = (-0.5).sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = MaterialTheme.colorScheme.onBackground
    )
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(onClickLabel = "Abrir ajustes", onClick = onOpenSettings)
        .testTag("topbar_settings_button"),
      contentAlignment = Alignment.Center
    ) {
      // A gear on a round glass button, like the capsules; the brand mark here read as a logo.
      Box(
        modifier = Modifier
          .size(44.dp)
          .glassControl(CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(22.dp)
        )
      }
    }
  }
}

/** Page section title, optionally with the "›" that Apple Music uses for titles that open a list. */
@Composable
fun TarkasSectionTitle(
  text: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  Row(
    modifier = modifier
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = text,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      letterSpacing = (-0.3).sp,
      color = MaterialTheme.colorScheme.onBackground
    )
    if (onClick != null) {
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

/** The T.A.R.K.A.S. symbol: bar, diamond and forked tail, drawn on a 132x80 grid. */
@Composable
fun TarkasMark(
  modifier: Modifier = Modifier,
  symbolColor: Color = MaterialTheme.colorScheme.onBackground,
  accentColor: Color = MaterialTheme.colorScheme.primary
) {
  Canvas(modifier = modifier) {
    val k = size.width / 132f
    val stroke = 12f * k
    fun p(x: Float, y: Float) = Offset(x * k, y * k)

    // accent bar
    drawRect(accentColor, topLeft = p(0f, 34f), size = Size(29f * k, 12f * k))

    // diamond outline
    val diamond = androidx.compose.ui.graphics.Path().apply {
      moveTo(66f * k, 6f * k)
      lineTo(102f * k, 40f * k)
      lineTo(66f * k, 74f * k)
      lineTo(30f * k, 40f * k)
      close()
    }
    drawPath(
      diamond,
      symbolColor,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Miter)
    )

    // tail: horizontal bar and two diagonals
    drawRect(symbolColor, topLeft = p(103f, 34f), size = Size(29f * k, 12f * k))
    drawLine(symbolColor, p(100f, 40f), p(126f, 8f), strokeWidth = stroke)
    drawLine(symbolColor, p(100f, 40f), p(126f, 72f), strokeWidth = stroke)
  }
}

@Composable
fun TarkasBottomNav(
  currentTab: TarkasNavTab,
  onTabSelected: (TarkasNavTab) -> Unit,
  modifier: Modifier = Modifier,
  collapsed: Boolean = false
) {
  val items = listOf(
    // No "Música" tab: the player opens from the mini player, the only way in.
    Triple(TarkasNavTab.HOME, "Início", Icons.Default.PlayCircleFilled),
    Triple(TarkasNavTab.LIBRARY, "Biblioteca", Icons.Default.LibraryMusic),
    Triple(TarkasNavTab.AUDIO, "Áudio", Icons.Default.Equalizer),
    Triple(TarkasNavTab.ASSISTANT, "Assistente", Icons.Default.Psychology)
  )

  // One lighter capsule marks the selected tab. Its two ends travel on different springs: the end
  // facing the new tab leaves first and fast, the other follows slower, so the capsule stretches
  // towards the tab and then snaps back to size, like a drop of glass (the iOS 26 tab bar).
  val selectedIndex = items.indexOfFirst { it.first == currentTab }
  val reduced = com.example.ui.theme.rememberReducedMotion()
  val start0 = selectedIndex.coerceAtLeast(0).toFloat()
  val leftEdge = remember { androidx.compose.animation.core.Animatable(start0) }
  val rightEdge = remember { androidx.compose.animation.core.Animatable(start0 + 1f) }
  val indicatorAlpha by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (selectedIndex >= 0) 1f else 0f,
    animationSpec = tween(200),
    label = "navPillAlpha"
  )
  androidx.compose.runtime.LaunchedEffect(selectedIndex, reduced) {
    if (selectedIndex < 0) return@LaunchedEffect
    val target = selectedIndex.toFloat()
    if (reduced) {
      leftEdge.snapTo(target)
      rightEdge.snapTo(target + 1f)
      return@LaunchedEffect
    }
    val movingRight = target > leftEdge.value
    val lead = com.example.ui.theme.Motion.fastSpatial<Float>()
    val trail = com.example.ui.theme.Motion.slowSpatial<Float>()
    kotlinx.coroutines.coroutineScope {
      launch { leftEdge.animateTo(target, if (movingRight) trail else lead) }
      launch { rightEdge.animateTo(target + 1f, if (movingRight) lead else trail) }
    }
  }
  val pillColor = MaterialTheme.colorScheme.onSurface

  // While the page scrolls down the capsule draws back a little and its labels fade, like the
  // iOS 26 tab bar; scrolling up brings it back. Scale only, so the page layout never moves.
  val shrink by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (collapsed && !reduced) 1f else 0f,
    animationSpec = com.example.ui.theme.Motion.defaultSpatial(),
    label = "navShrink"
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        val s = 1f - 0.1f * shrink
        scaleX = s
        scaleY = s
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
      }
      .glassPill(cornerRadius = 32.dp)
      .padding(horizontal = 6.dp, vertical = 6.dp)
      .drawBehind {
        val slot = size.width / items.size
        val x = leftEdge.value * slot
        val w = (rightEdge.value - leftEdge.value) * slot
        drawRoundRect(
          pillColor.copy(alpha = 0.10f * indicatorAlpha),
          topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
          size = androidx.compose.ui.geometry.Size(w, size.height),
          cornerRadius = CornerRadius(26.dp.toPx())
        )
      },
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    items.forEach { (tab, label, icon) ->
      val selected = currentTab == tab
      val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navTint"
      )
      // The selected tab's icon pops up a little with a spring (scale only, no relayout).
      val pop by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "navPop"
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .testTag("nav_tab_${tab.name.lowercase()}")
          .clip(RoundedCornerShape(26.dp))
          .selectable(
            selected = selected,
            role = androidx.compose.ui.semantics.Role.Tab,
            onClick = { onTabSelected(tab) }
          )
          .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = tint,
          modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
              scaleX = pop
              scaleY = pop
              // With the labels faded, the icons drop to the middle of the capsule.
              translationY = shrink * 7.dp.toPx()
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = label,
          color = tint,
          fontSize = 11.sp,
          maxLines = 1,
          softWrap = false,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          modifier = Modifier.graphicsLayer { alpha = 1f - shrink }
        )
      }
    }
  }
}

@Composable
fun MiniPlayerBar(
  track: Track?,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onPrevious: () -> Unit = {},
  progress: () -> Float = { 0f }
) {
  if (track == null) return

  // Swiping the capsule sideways changes the track (left: next, right: previous) with a tick, like
  // Apple Music's mini player. The content follows the finger and springs back.
  val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
  val reduced = com.example.ui.theme.rememberReducedMotion()
  val currentNext by androidx.compose.runtime.rememberUpdatedState(onNext)
  val currentPrevious by androidx.compose.runtime.rememberUpdatedState(onPrevious)
  val track0 = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val fill = MaterialTheme.colorScheme.primary

  // Its own floating capsule of glass, like the tab bar below it.
  Box(
    modifier = modifier
      .fillMaxWidth()
      .glassPill(cornerRadius = 30.dp)
      .pointerInput(Unit) {
        val threshold = size.width * 0.22f
        detectHorizontalDragGestures(
          onDragEnd = {
            val offset = swipe.value
            scope.launch {
              if (kotlin.math.abs(offset) > threshold) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (offset < 0) currentNext() else currentPrevious()
                // The new track slides in from the side the old one left towards.
                if (!reduced) swipe.snapTo(if (offset < 0) size.width * 0.3f else -size.width * 0.3f)
              }
              swipe.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial())
            }
          },
          onDragCancel = { scope.launch { swipe.animateTo(0f, com.example.ui.theme.Motion.defaultSpatial()) } }
        ) { change, dx ->
          change.consume()
          scope.launch { swipe.snapTo(swipe.value + dx) }
        }
      }
      .pressClickable(pressedScale = 0.985f, onClick = onClick)
      // A thin line of progress along the bottom edge.
      .drawBehind {
        val h = 2.5.dp.toPx()
        val inset = 22.dp.toPx()
        val w = size.width - inset * 2
        drawRoundRect(track0, topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - h - 3.dp.toPx()), size = Size(w, h), cornerRadius = CornerRadius(h))
        drawRoundRect(fill, topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - h - 3.dp.toPx()), size = Size(w * progress().coerceIn(0f, 1f), h), cornerRadius = CornerRadius(h))
      }
      .testTag("mini_player_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          translationX = swipe.value
          alpha = 1f - (kotlin.math.abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
        }
        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TrackArtwork(track = track, shape = RoundedCornerShape(16.dp), iconSize = 24.dp, modifier = Modifier.size(46.dp))

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = track.artist,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier
          .size(48.dp)
          .testTag("mini_player_play_pause")
      ) {
        PlayPauseIcon(
          isPlaying = isPlaying,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(34.dp)
        )
      }

      IconButton(
        onClick = onNext,
        modifier = Modifier
          .size(48.dp)
          .testTag("mini_player_next")
      ) {
        Icon(
          imageVector = Icons.Default.SkipNext,
          contentDescription = "Próxima Faixa",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(34.dp)
        )
      }
    }
  }
}

/**
 * Play and pause turn into each other: the old symbol spins away and shrinks while the new one
 * springs in, instead of a hard swap. With reduced motion it simply swaps.
 */
@Composable
fun PlayPauseIcon(isPlaying: Boolean, tint: Color, modifier: Modifier = Modifier) {
  val reduced = com.example.ui.theme.rememberReducedMotion()
  androidx.compose.animation.AnimatedContent(
    targetState = isPlaying,
    transitionSpec = {
      if (reduced) {
        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
      } else {
        (androidx.compose.animation.scaleIn(com.example.ui.theme.Motion.fastSpatial(), initialScale = 0.4f) +
          androidx.compose.animation.fadeIn(tween(120))) togetherWith
          (androidx.compose.animation.scaleOut(tween(120), targetScale = 0.4f) + androidx.compose.animation.fadeOut(tween(90)))
      }
    },
    label = "playPause",
    modifier = modifier
  ) { playing ->
    Icon(
      imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
      contentDescription = if (playing) "Pausar" else "Reproduzir",
      tint = tint,
      modifier = Modifier.fillMaxSize()
    )
  }
}

@Composable
fun ToolReportCard(
  report: com.example.data.model.ToolExecutionReport,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
      .fillMaxWidth()
      .testTag("tool_report_${report.toolName}")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (report.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = if (report.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Column {
        Text(
          text = "Ação: ${report.toolName}()",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = report.resultSummary,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun CuriosityDetailDialog(
  curiosity: com.example.data.model.Curiosity,
  onDismiss: () -> Unit
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "💡 Curiosidade da Faixa",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "${curiosity.trackTitle} • ${curiosity.artist}",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = curiosity.shortTrivia,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 19.sp
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "FATOS HISTÓRICOS:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = curiosity.documentedFacts,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            Text(
              text = "FONTES DOCUMENTADAS:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            curiosity.sources.forEach { src ->
              Text(
                text = "• $src",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
              )
            }
          }
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.Button(onClick = onDismiss) {
        Text("Fechar")
      }
    }
  )
}


