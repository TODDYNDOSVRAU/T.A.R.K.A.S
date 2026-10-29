package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Curiosity
import com.example.data.model.ToolExecutionReport
import com.example.data.model.Track
import com.example.ui.theme.AppText
import com.example.ui.theme.Motion
import com.example.ui.theme.Shapes
import com.example.ui.theme.Space
import com.example.ui.theme.glassContentColor
import com.example.ui.theme.glassControl
import com.example.ui.theme.glassSurface
import com.example.ui.theme.pressClickable
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.TarkasNavTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.ui.theme.Size as Sizes

/**
 * The app's chip: a capsule with a flat fill (it sits on the page, so it is not glass), 48 dp tall.
 *
 * By default it is a plain button: [selected] only changes how it looks (the accent wash marks the
 * main action), and TalkBack announces a button, never "selected". When it is one choice of several
 * (a filter, a preset), pass [selectionRole] (Role.Tab or Role.RadioButton) and it is announced as
 * selectable with its state.
 */
@Composable
fun GlassChip(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  selectionRole: Role? = null
) {
  val content = glassContentColor(selected)
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // The quiet press highlight of the design system: in at once, out gently, no ripple, no bounce.
  val overlay by animateColorAsState(
    targetValue = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
    animationSpec = tween(if (pressed) 0 else 220),
    label = "chipPress"
  )
  val interactive = if (selectionRole != null) {
    Modifier.selectable(
      selected = selected,
      enabled = enabled,
      role = selectionRole,
      interactionSource = interaction,
      indication = null,
      onClick = onClick
    )
  } else {
    Modifier.clickable(
      enabled = enabled,
      role = Role.Button,
      interactionSource = interaction,
      indication = null,
      onClick = onClick
    )
  }
  Row(
    modifier = modifier
      // A disabled chip is faded as a whole, so a filled one does not still look tappable.
      .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
      .heightIn(min = Sizes.touch)
      .glassControl(Shapes.pill, selected = selected)
      .then(interactive)
      .background(overlay)
      .padding(horizontal = Space.l, vertical = Space.s),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(Space.s))
    }
    Text(
      text = label,
      style = AppText.Subhead.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
      color = content,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * What a section shows when it has nothing: an icon, what happened, what to do, and a button for it.
 * Used for the offline, failed and empty states, so they all read the same.
 */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {}
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.xl),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
    Spacer(modifier = Modifier.height(Space.m))
    Text(
      text = title,
      style = AppText.Headline,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(Space.xs))
    Text(
      text = message,
      style = AppText.Subhead,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    if (actionLabel != null) {
      Spacer(modifier = Modifier.height(Space.l))
      TonalButton(text = actionLabel, onClick = onAction)
    }
  }
}

/**
 * The large title at the top of a tab page, left-aligned at the page margin. [showSettings] adds the
 * Ajustes button at the trailing edge (only Início has it); without it the row keeps the same height,
 * so the title does not jump when switching tabs.
 */
@Composable
fun TarkasLargeTitle(
  title: String,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  showSettings: Boolean = true
) {
  Row(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .fillMaxWidth()
      .heightIn(min = Sizes.touch + Space.m + Space.s)
      .padding(start = Space.page, end = Space.s, top = Space.m, bottom = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      style = AppText.LargeTitle,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    if (showSettings) {
      IconAction(
        icon = Icons.Rounded.Settings,
        contentDescription = "Ajustes",
        onClick = onOpenSettings,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("topbar_settings_button")
      )
    } else {
      // Holds the button's height, so every tab's title sits on the same line.
      Spacer(Modifier.size(Sizes.touch))
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
      .then(
        if (onClick != null) {
          Modifier
            .heightIn(min = Sizes.touch)
            .clip(Shapes.s)
            .clickable(role = Role.Button, onClick = onClick)
        } else {
          Modifier
        }
      )
      .semantics(mergeDescendants = true) { heading() },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = text,
      style = AppText.Title2,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    if (onClick != null) {
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Sizes.icon)
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
    val diamond = Path().apply {
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

/**
 * The floating tab bar: a glass capsule (it floats over the content) with four tabs, icon over a
 * Caption1 label. The selected tab is tinted with the accent and sits on a quiet filled indicator
 * that glides to it on a critically damped spring. While the page scrolls down the capsule draws back
 * a little and its labels fade; scrolling up brings them back. Scale and alpha only, so the page layout
 * never moves.
 */
@Composable
fun TarkasBottomNav(
  currentTab: TarkasNavTab,
  onTabSelected: (TarkasNavTab) -> Unit,
  modifier: Modifier = Modifier,
  collapsed: Boolean = false
) {
  val items = listOf(
    // No "Música" tab: the player opens from the mini player.
    Triple(TarkasNavTab.HOME, "Início", Icons.Rounded.PlayCircle),
    Triple(TarkasNavTab.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
    Triple(TarkasNavTab.AUDIO, "Áudio", Icons.Rounded.Equalizer),
    Triple(TarkasNavTab.ASSISTANT, "Assistente", Icons.Rounded.Psychology)
  )
  val selectedIndex = items.indexOfFirst { it.first == currentTab }
  val reduced = rememberReducedMotion()

  val indicator = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
  LaunchedEffect(selectedIndex, reduced) {
    if (selectedIndex < 0) return@LaunchedEffect
    if (reduced) indicator.snapTo(selectedIndex.toFloat())
    else indicator.animateTo(selectedIndex.toFloat(), Motion.criticalSpatial(stiffness = 700f))
  }
  val indicatorAlpha by animateFloatAsState(
    targetValue = if (selectedIndex >= 0) 1f else 0f,
    animationSpec = tween(150),
    label = "navIndicatorAlpha"
  )
  val shrink by animateFloatAsState(
    targetValue = if (collapsed && !reduced) 1f else 0f,
    animationSpec = Motion.criticalSpatial(stiffness = 400f),
    label = "navShrink"
  )
  val indicatorColor = MaterialTheme.colorScheme.onSurface

  Row(
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        val s = 1f - 0.08f * shrink
        scaleX = s
        scaleY = s
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
      }
      .glassSurface(Shapes.pill)
      .padding(Space.xs + Space.xxs)
      .drawBehind {
        val slot = size.width / items.size
        drawRoundRect(
          indicatorColor.copy(alpha = 0.09f * indicatorAlpha),
          topLeft = Offset(indicator.value * slot, 0f),
          size = Size(slot, size.height),
          cornerRadius = CornerRadius(size.height / 2f)
        )
      }
  ) {
    items.forEach { (tab, label, icon) ->
      val selected = currentTab == tab
      val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(150),
        label = "navTint"
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = Sizes.touch)
          .testTag("nav_tab_${tab.name.lowercase()}")
          .clip(Shapes.pill)
          .selectable(
            selected = selected,
            role = Role.Tab,
            onClick = { onTabSelected(tab) }
          )
          .padding(horizontal = Space.xs, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // The label below names the tab for TalkBack, so the icon stays silent (no double reading).
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier
            .size(Sizes.icon)
            // With the labels faded, the icons drop to the middle of the capsule.
            .graphicsLayer { translationY = shrink * 7.dp.toPx() }
        )
        Spacer(modifier = Modifier.height(Space.xxs))
        Text(
          text = label,
          style = AppText.Caption1.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
          color = tint,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.graphicsLayer { alpha = 1f - shrink }
        )
      }
    }
  }
}

/**
 * The mini player: a floating glass capsule with the artwork, title and artist, play/pause and next,
 * and a hairline of progress along its bottom edge. Swiping it sideways changes the track (left: next,
 * right: previous); TalkBack gets the same as custom actions.
 */
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

  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val reduced = rememberReducedMotion()
  val currentNext by rememberUpdatedState(onNext)
  val currentPrevious by rememberUpdatedState(onPrevious)
  val currentReduced by rememberUpdatedState(reduced)
  // The finger writes the offset directly; letting go starts one settle job (no coroutine per delta).
  var swipe by remember { mutableFloatStateOf(0f) }
  val settleJob = remember { arrayOfNulls<Job>(1) }
  val rail = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
  val fill = MaterialTheme.colorScheme.primary

  Box(
    modifier = modifier
      .fillMaxWidth()
      .glassSurface(Shapes.pill)
      .clip(Shapes.pill)
      .pointerInput(Unit) {
        val threshold = size.width * 0.22f
        fun settle(skipped: Int) {
          settleJob[0]?.cancel()
          settleJob[0] = scope.launch {
            if (currentReduced) {
              swipe = 0f
              return@launch
            }
            // The new track slides in from the side the old one left towards.
            if (skipped != 0) swipe = skipped * size.width * 0.3f
            animate(swipe, 0f, animationSpec = Motion.criticalSpatial()) { v, _ -> swipe = v }
          }
        }
        detectHorizontalDragGestures(
          onDragStart = { settleJob[0]?.cancel() },
          onDragEnd = {
            val skipped = when {
              swipe < -threshold -> 1
              swipe > threshold -> -1
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
      .pressClickable(pressedScale = 0.985f, onClickLabel = "Abrir player", onClick = onClick)
      .semantics {
        customActions = listOf(
          CustomAccessibilityAction("Faixa anterior") { currentPrevious(); true },
          CustomAccessibilityAction("Próxima faixa") { currentNext(); true }
        )
      }
      // A hairline of progress along the bottom edge, inside the capsule's straight part.
      .drawWithContent {
        drawContent()
        val h = 2.dp.toPx()
        val inset = size.height / 2f
        val w = size.width - inset * 2
        val top = size.height - h - 1.dp.toPx()
        drawRoundRect(rail, topLeft = Offset(inset, top), size = Size(w, h), cornerRadius = CornerRadius(h))
        drawRoundRect(fill, topLeft = Offset(inset, top), size = Size(w * progress().coerceIn(0f, 1f), h), cornerRadius = CornerRadius(h))
      }
      .testTag("mini_player_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          translationX = swipe
          alpha = 1f - (kotlin.math.abs(swipe) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
        }
        .padding(start = Space.s + Space.xxs, end = Space.xs, top = Space.s, bottom = Space.s),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TrackArtwork(track = track, shape = Shapes.xs, iconSize = 20.dp, modifier = Modifier.size(40.dp))
      Spacer(modifier = Modifier.width(Space.m))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          style = AppText.Subhead.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = track.artist,
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box(
        modifier = Modifier
          .size(Sizes.touch)
          .clip(Shapes.pill)
          .clickable(role = Role.Button, onClick = onTogglePlayPause)
          .testTag("mini_player_play_pause"),
        contentAlignment = Alignment.Center
      ) {
        PlayPauseIcon(isPlaying = isPlaying, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
      }
      IconAction(
        icon = Icons.Rounded.SkipNext,
        contentDescription = "Próxima faixa",
        onClick = onNext,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("mini_player_next")
      )
    }
  }
}

/**
 * Play and pause turn into each other: the two pause bars reshape into the two halves of the play
 * triangle (and back), on a short critically damped spring, so the glyph morphs precisely with no
 * overshoot. With reduced motion it swaps. Announced as "Pausar" or "Reproduzir".
 */
@Composable
fun PlayPauseIcon(isPlaying: Boolean, tint: Color, modifier: Modifier = Modifier) {
  val reduced = rememberReducedMotion()
  // 0 = play triangle, 1 = pause bars.
  val morph by animateFloatAsState(
    targetValue = if (isPlaying) 1f else 0f,
    animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 1100f),
    label = "playPauseMorph"
  )
  val path = remember { Path() }
  Canvas(
    modifier = modifier.semantics { contentDescription = if (isPlaying) "Pausar" else "Reproduzir" }
  ) {
    val k = size.minDimension / 24f
    val ox = (size.width - 24f * k) / 2f
    val oy = (size.height - 24f * k) / 2f
    val t = morph.coerceIn(0f, 1f)
    path.reset()
    for (shape in 0..1) {
      val play = PlayShape[shape]
      val pause = PauseShape[shape]
      for (i in 0 until 4) {
        val x = ox + (play[i * 2] + (pause[i * 2] - play[i * 2]) * t) * k
        val y = oy + (play[i * 2 + 1] + (pause[i * 2 + 1] - play[i * 2 + 1]) * t) * k
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      path.close()
    }
    drawPath(path, tint)
  }
}

// On a 24-unit grid, each shape as four corners (top-left, top-right, bottom-right, bottom-left). The
// play triangle (8,5) (19,12) (8,19) is cut at x = 13.5 into a quad and a triangle with a doubled
// point, so both glyphs have the same number of corners and morph point by point.
private val PlayShape = arrayOf(
  floatArrayOf(8f, 5f, 13.5f, 8.5f, 13.5f, 15.5f, 8f, 19f),
  floatArrayOf(13.5f, 8.5f, 19f, 12f, 19f, 12f, 13.5f, 15.5f)
)
private val PauseShape = arrayOf(
  floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f),
  floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f)
)

/**
 * What the assistant did, in words: a check or an alert, a short human title and the result. The
 * tool's code name stays out of the interface; unknown tools get a generic title.
 */
@Composable
fun ToolReportCard(
  report: ToolExecutionReport,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(Shapes.s)
      .background(colors.surfaceContainer)
      .padding(horizontal = Space.m, vertical = Space.s)
      .semantics(mergeDescendants = true) {}
      .testTag("tool_report_${report.toolName}"),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      imageVector = if (report.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
      contentDescription = if (report.success) "Concluído" else "Não concluído",
      tint = if (report.success) colors.primary else colors.error,
      modifier = Modifier
        .padding(top = 1.dp)
        .size(16.dp)
    )
    Spacer(modifier = Modifier.width(Space.s))
    Column {
      Text(
        text = toolTitle(report.toolName, report.success),
        style = AppText.Footnote.copy(fontWeight = FontWeight.SemiBold),
        color = colors.onSurface
      )
      if (report.resultSummary.isNotBlank()) {
        Text(text = report.resultSummary, style = AppText.Footnote, color = colors.onSurfaceVariant)
      }
    }
  }
}

/** A person's name for what a tool did. */
private fun toolTitle(toolName: String, success: Boolean): String = when (toolName) {
  "analyze_and_research_music" -> "Pesquisa sobre a música"
  "get_current_track" -> "Faixa atual"
  "get_audio_capabilities" -> "Recursos de áudio"
  "apply_audio_profile" -> "Perfil de áudio"
  "create_timer" -> "Timer criado"
  "cancel_timer" -> "Timer cancelado"
  else -> if (success) "Feito" else "Não deu certo"
}

/**
 * The track's curiosity (the app's equivalent of lyrics): the short story, the documented facts and
 * the sources. Scrolls when it is long; text never goes below Footnote.
 */
@Composable
fun CuriosityDetailDialog(
  curiosity: Curiosity,
  onDismiss: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(text = "Curiosidade da faixa", style = AppText.Title3, color = colors.onSurface)
        Spacer(Modifier.height(Space.xxs))
        Text(
          text = "${curiosity.trackTitle} · ${curiosity.artist}",
          style = AppText.Footnote,
          color = colors.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.l)
      ) {
        Text(text = curiosity.shortTrivia, style = AppText.Body, color = colors.onSurface)
        if (curiosity.documentedFacts.isNotBlank()) {
          Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
              text = "Fatos documentados",
              style = AppText.Footnote.copy(fontWeight = FontWeight.SemiBold),
              color = colors.onSurfaceVariant,
              modifier = Modifier.semantics { heading() }
            )
            Text(text = curiosity.documentedFacts, style = AppText.Subhead, color = colors.onSurface)
          }
        }
        if (curiosity.sources.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
              text = "Fontes",
              style = AppText.Footnote.copy(fontWeight = FontWeight.SemiBold),
              color = colors.onSurfaceVariant,
              modifier = Modifier.semantics { heading() }
            )
            curiosity.sources.forEach { src ->
              Text(text = "• $src", style = AppText.Footnote, color = colors.onSurfaceVariant)
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Fechar", style = AppText.Headline)
      }
    }
  )
}
