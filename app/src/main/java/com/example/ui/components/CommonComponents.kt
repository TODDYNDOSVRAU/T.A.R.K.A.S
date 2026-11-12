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
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import com.example.R
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
 * so the title does not jump when switching tabs. A page that hides the bars (Ajustes) passes [onBack],
 * which puts a back button in that spot, since the tab bar is no longer there to leave by.
 */
@Composable
fun TarkasLargeTitle(
  title: String,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  showSettings: Boolean = true,
  onBack: (() -> Unit)? = null
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
    if (onBack != null) {
      IconAction(
        icon = Icons.AutoMirrored.Rounded.ArrowBackIos,
        contentDescription = stringResource(R.string.player_common_back),
        onClick = onBack,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("topbar_back_button")
      )
    }
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
    if (showSettings && onBack == null) {
      IconAction(
        icon = Icons.Rounded.Settings,
        contentDescription = stringResource(R.string.player_common_settings),
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
 * The floating tab bar: a glass capsule (it floats over the content) with four tabs in the "expanding
 * label" style. The active tab is wider and shows its icon and Caption1 label inside a quiet filled
 * pill; the others are icon only and narrower. Changing tab moves one fractional position on a
 * critically damped spring (no bounce, response about 0.24 s): the widths redistribute, the pill
 * travels and resizes, the old label folds back into its icon and the new one slides out of it, all
 * from that single value, so a tap in the middle of the motion continues from what is on screen.
 * The position is read only while laying out and drawing, so the animation never recomposes.
 *
 * While the page scrolls down ([collapsed]) the capsule draws back a little, as before, and the bar
 * becomes icons only: the active tab keeps its accent icon and a round pill without text, and the
 * slots even out. A label alone in a smaller capsule was the inconsistent half (the inactive tabs
 * already have none), and the pill still says which tab is open. Scrolling up brings the label back.
 * The height of the bar never changes, so the page and the mini player above never move.
 */
@Composable
fun TarkasBottomNav(
  currentTab: TarkasNavTab,
  onTabSelected: (TarkasNavTab) -> Unit,
  modifier: Modifier = Modifier,
  collapsed: Boolean = false
) {
  val homeLabel = stringResource(R.string.player_common_tab_home)
  val libraryLabel = stringResource(R.string.player_common_tab_library)
  val audioLabel = stringResource(R.string.player_common_tab_audio)
  val assistantLabel = stringResource(R.string.player_common_tab_assistant)
  val items = remember(homeLabel, libraryLabel, audioLabel, assistantLabel) {
    listOf(
      // No "Música" tab: the player opens from the mini player.
      Triple(TarkasNavTab.HOME, homeLabel, Icons.Rounded.PlayCircle),
      Triple(TarkasNavTab.LIBRARY, libraryLabel, Icons.Rounded.LibraryMusic),
      Triple(TarkasNavTab.AUDIO, audioLabel, Icons.Rounded.Equalizer),
      Triple(TarkasNavTab.ASSISTANT, assistantLabel, Icons.Rounded.Psychology)
    )
  }
  val count = items.size
  val selectedIndex = items.indexOfFirst { it.first == currentTab }
  val reduced = rememberReducedMotion()

  // The one value that drives the whole bar: which tab is active, fractional while moving.
  val position = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
  LaunchedEffect(selectedIndex, reduced) {
    if (selectedIndex < 0) return@LaunchedEffect
    // Reduced motion: the bar switches at once (no width animation).
    if (reduced) position.snapTo(selectedIndex.toFloat())
    else position.animateTo(selectedIndex.toFloat(), Motion.criticalSpatial(stiffness = 700f))
  }
  // 1 while one of the four tabs is open. Ajustes is reached from Início but is none of them: the bar
  // folds to icons only, evenly spaced, with no pill and no accent. While the player covers the bar
  // (MUSIC) it stays as it was, so nothing in it changes while it leaves or comes back.
  val presence = animateFloatAsState(
    targetValue = if (selectedIndex >= 0 || currentTab == TarkasNavTab.MUSIC) 1f else 0f,
    animationSpec = if (reduced) snap() else Motion.criticalSpatial(stiffness = 700f),
    label = "navPresence"
  )
  val shrink = animateFloatAsState(
    targetValue = if (collapsed && !reduced) 1f else 0f,
    animationSpec = Motion.criticalSpatial(stiffness = 400f),
    label = "navShrink"
  )
  // 0: labels may show; 1: icons only (scrolled down, or no tab open).
  val fold = remember { { 1f - (1f - shrink.value) * presence.value } }

  val colors = MaterialTheme.colorScheme
  val pillColor = colors.onSurface.copy(alpha = 0.12f)
  val labelStyle = remember { AppText.Caption1.copy(fontWeight = FontWeight.SemiBold) }
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val geometry = remember { NavBarGeometry(count) }
  // Measured once per font size, not per frame: the labels' widths decide the active slot, and the
  // label's height keeps the bar exactly as tall as the old icon-over-label layout.
  remember(density, labelStyle) {
    var textHeight = 0
    items.forEachIndexed { i, item ->
      val size = measurer.measure(item.second, labelStyle, softWrap = false, maxLines = 1).size
      geometry.labelWidths[i] = size.width.toFloat()
      textHeight = maxOf(textHeight, size.height)
    }
    with(density) {
      geometry.icon = Sizes.icon.toPx()
      geometry.touch = Sizes.touch.toPx()
      geometry.slide = 8.dp.toPx()
      geometry.height = (6.dp * 2 + Sizes.icon + Space.xxs).roundToPx() + textHeight
      geometry.gapWide = 6.dp.toPx()
      geometry.gapTight = Space.xs.toPx()
      geometry.padWide = 14.dp.toPx()
      geometry.padTight = Space.s.toPx()
    }
    true
  }

  // The glass capsule is its own node, outside the animation: the moving tabs and pill live in a
  // child with its own layer, so a frame of the animation re-lays and redraws only that child and
  // never re-measures or re-records the glass (its backdrop and lens are the costly part).
  Box(
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        val s = 1f - 0.08f * shrink.value
        scaleX = s
        scaleY = s
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
      }
      .glassSurface(Shapes.pill)
      .padding(Space.xs + Space.xxs)
  ) {
    Layout(
      content = {
        items.forEachIndexed { index, (tab, label, icon) ->
          // Kept between recompositions, so a tab change does not recompose the tabs' contents.
          // How far this tab is open: its share of the position, folded away while collapsed.
          val reveal = remember(index) { { NavLayout.share(position.value, index, count) * (1f - fold()) } }
          val tint = remember(index) { { NavLayout.share(position.value, index, count) * presence.value } }
          NavTab(
            label = label,
            icon = icon,
            selected = currentTab == tab,
            tag = "nav_tab_${tab.name.lowercase()}",
            reveal = reveal,
            tint = tint,
            geometry = geometry,
            labelStyle = labelStyle,
            activeColor = colors.primary,
            inactiveColor = colors.onSurfaceVariant,
            reduced = reduced,
            onClick = { onTabSelected(tab) }
          )
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {}
        .drawBehind {
          val alpha = presence.value
          if (alpha <= 0f) return@drawBehind
          val p = position.value
          val s = fold()
          geometry.slots(p, s, size.width, geometry.drawWidths)
          val k = p.toInt().coerceIn(0, count - 1)
          val f = (p - k).coerceIn(0f, 1f)
          geometry.pill(k, NavLayout.share(p, k, count) * (1f - s), geometry.drawWidths, size.height)
          var left = geometry.pillLeft
          var right = geometry.pillRight
          if (k + 1 < count && f > 0f) {
            geometry.pill(k + 1, NavLayout.share(p, k + 1, count) * (1f - s), geometry.drawWidths, size.height)
            left += (geometry.pillLeft - left) * f
            right += (geometry.pillRight - right) * f
          }
          drawRoundRect(
            pillColor.copy(alpha = pillColor.alpha * alpha),
            topLeft = Offset(left, 0f),
            size = Size(right - left, size.height),
            cornerRadius = CornerRadius(size.height / 2f)
          )
        }
    ) { measurables, constraints ->
      val total = constraints.maxWidth.toFloat()
      geometry.fit(total)
      geometry.slots(position.value, fold(), total, geometry.layoutWidths)
      val h = geometry.height.coerceIn(constraints.minHeight, constraints.maxHeight)
      // Whole pixels that still add up to the full width: each slot ends where the running sum rounds.
      var sum = 0f
      var previous = 0
      for (i in 0 until count) {
        sum += geometry.layoutWidths[i]
        val end = if (i == count - 1) constraints.maxWidth else sum.roundToInt()
        geometry.placeables[i] = measurables[i].measure(Constraints.fixed((end - previous).coerceAtLeast(0), h))
        previous = end
      }
      layout(constraints.maxWidth, h) {
        var x = 0
        for (i in 0 until count) {
          val placeable = geometry.placeables[i] ?: continue
          placeable.place(x, 0)
          x += placeable.width
        }
      }
    }
  }
}

/**
 * Sizes of the tab bar, measured once per font size and kept between frames (the arrays are reused,
 * so the animation allocates nothing). Written while measuring, read while laying out and drawing.
 */
private class NavBarGeometry(val count: Int) {
  val labelWidths = FloatArray(count)
  val layoutWidths = FloatArray(count)
  val drawWidths = FloatArray(count)
  val placeables = arrayOfNulls<androidx.compose.ui.layout.Placeable>(count)
  var icon = 0f
  var touch = 0f
  var slide = 0f
  var height = 0
  var gapWide = 0f
  var gapTight = 0f
  var padWide = 0f
  var padTight = 0f

  /** Space between icon and label, and inside the pill on each side, after [fit]. */
  var gap = 0f
  var pad = 0f
  var activeWeight = 1f
  var pillLeft = 0f
  var pillRight = 0f

  /**
   * Gives the active slot room for the longest label. If the other tabs would drop below the touch
   * size (large system fonts), the spacing tightens first; the label is never shortened.
   */
  fun fit(total: Float) {
    var longest = 0f
    for (w in labelWidths) longest = maxOf(longest, w)
    gap = gapWide
    pad = padWide
    var needed = icon + gap + longest + 2f * pad
    if ((total - needed) / (count - 1) < touch) {
      gap = gapTight
      pad = padTight
      needed = icon + gap + longest + 2f * pad
    }
    val active = minOf(needed, total - (count - 1) * touch)
    // At least 1.5: with short labels the active tab still reads as the wider one.
    activeWeight = maxOf(1.5f, NavLayout.weightFor(active, count, total))
  }

  /** Slot widths at [position]; folding (collapsed, or no tab open) evens them out: all icon only. */
  fun slots(position: Float, fold: Float, total: Float, out: FloatArray) {
    val weight = 1f + (activeWeight - 1f) * (1f - fold)
    NavLayout.slotWidths(position, count, total, weight, out)
  }

  /** Pill around tab [index] opened by [reveal]: icon plus the revealed label, centred in its slot. */
  fun pill(index: Int, reveal: Float, widths: FloatArray, height: Float) {
    var start = 0f
    for (i in 0 until index) start += widths[i]
    val slot = widths[index]
    val content = icon + reveal * (gap + labelWidths[index]) + 2f * pad
    // Never narrower than tall (a circle around the icon alone), never wider than the slot.
    val width = maxOf(content, height).coerceAtMost(slot)
    val centre = start + slot / 2f
    pillLeft = centre - width / 2f
    pillRight = centre + width / 2f
  }
}

/**
 * One tab: the icon (inactive colour, with the accent copy over it faded in by [tint], so the colour
 * blends without recomposing) and the label, which slides out of the icon behind a clip as [reveal]
 * grows. Announced as a tab with its name and state; pressing scales it at once (on pointer-down).
 */
@Composable
private fun NavTab(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  tag: String,
  reveal: () -> Float,
  tint: () -> Float,
  geometry: NavBarGeometry,
  labelStyle: androidx.compose.ui.text.TextStyle,
  activeColor: Color,
  inactiveColor: Color,
  reduced: Boolean,
  onClick: () -> Unit
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press = animateFloatAsState(
    targetValue = if (pressed && !reduced) 0.9f else 1f,
    animationSpec = Motion.criticalSpatial(stiffness = 900f),
    label = "navPress"
  )
  Layout(
    content = {
      Icon(imageVector = icon, contentDescription = null, tint = inactiveColor, modifier = Modifier.size(Sizes.icon))
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = activeColor,
        modifier = Modifier
          .size(Sizes.icon)
          .graphicsLayer {
            alpha = tint()
            // One icon, nothing overlapping: the alpha goes on its pixels, with no offscreen buffer.
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
          }
      )
      Text(
        text = label,
        style = labelStyle,
        color = activeColor,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
          // The tab itself carries the name, so the label is not read twice.
          .clearAndSetSemantics {}
          .graphicsLayer {
            alpha = ((reveal() - 0.15f) / 0.85f).coerceIn(0f, 1f)
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
          }
          .drawWithContent {
            val r = reveal()
            if (r <= 0.001f) return@drawWithContent
            // Revealed from the icon's side; the text trails in from behind it.
            clipRect(left = 0f, top = -size.height, right = r * size.width, bottom = size.height * 2f) {
              translate(left = -(1f - r) * geometry.slide) { this@drawWithContent.drawContent() }
            }
          }
      )
    },
    modifier = Modifier
      .testTag(tag)
      .semantics { contentDescription = label }
      .selectable(
        selected = selected,
        interactionSource = interaction,
        indication = null,
        role = Role.Tab,
        onClick = onClick
      )
      .graphicsLayer {
        scaleX = press.value
        scaleY = press.value
      }
  ) { measurables, constraints ->
    val loose = Constraints()
    val base = measurables[0].measure(loose)
    val accent = measurables[1].measure(loose)
    val text = measurables[2].measure(loose)
    layout(constraints.maxWidth, constraints.maxHeight) {
      // Read here, so moving between tabs only re-places (no re-measure, no recomposition).
      val r = reveal()
      val content = base.width + r * (geometry.gap + text.width)
      val x = ((constraints.maxWidth - content) / 2f).roundToInt()
      val iconY = (constraints.maxHeight - base.height) / 2
      base.place(x, iconY)
      accent.place(x, iconY)
      text.place(x + base.width + (r * geometry.gap).roundToInt(), (constraints.maxHeight - text.height) / 2)
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

  val openPlayerLabel = stringResource(R.string.player_common_open_player)
  val previousLabel = stringResource(R.string.player_common_previous_track)
  val nextLabel = stringResource(R.string.player_common_next_track)
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
      .pressClickable(pressedScale = 0.985f, onClickLabel = openPlayerLabel, onClick = onClick)
      .semantics {
        customActions = listOf(
          CustomAccessibilityAction(previousLabel) { currentPrevious(); true },
          CustomAccessibilityAction(nextLabel) { currentNext(); true }
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
      TrackArtwork(
        track = track,
        shape = Shapes.s,
        iconSize = 20.dp,
        modifier = Modifier
          .size(40.dp)
          // The player's cover starts here and lands back here when it closes.
          .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.miniCover = it.boundsInWindow() }
      )
      Spacer(modifier = Modifier.width(Space.m))
      Column(
        modifier = Modifier
          .weight(1f)
          // The player's title goes to and from here.
          .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.miniTitle = it.boundsInWindow() }
      ) {
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
        PlayPauseIcon(
          isPlaying = isPlaying,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .size(28.dp)
            // The player's play glyph goes to and from here.
            .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.miniPlay = it.boundsInWindow() }
        )
      }
      IconAction(
        icon = Icons.Rounded.SkipNext,
        contentDescription = nextLabel,
        onClick = onNext,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .testTag("mini_player_next")
          // The player's next button goes to and from here.
          .onGloballyPositioned { com.example.ui.theme.PlayerOrigin.miniNext = it.boundsInWindow() }
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
  val description = stringResource(if (isPlaying) R.string.player_common_pause else R.string.player_common_play)
  // 0 = play triangle, 1 = pause bars.
  val morph by animateFloatAsState(
    targetValue = if (isPlaying) 1f else 0f,
    animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 1100f),
    label = "playPauseMorph"
  )
  val path = remember { Path() }
  Canvas(
    modifier = modifier.semantics { contentDescription = description }
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
      contentDescription = stringResource(if (report.success) R.string.player_common_done else R.string.player_common_not_done),
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
@Composable
private fun toolTitle(toolName: String, success: Boolean): String = when (toolName) {
  "analyze_and_research_music" -> stringResource(R.string.player_common_tool_research)
  "get_current_track" -> stringResource(R.string.player_common_tool_current_track)
  "get_audio_capabilities" -> stringResource(R.string.player_common_tool_audio_capabilities)
  "apply_audio_profile" -> stringResource(R.string.player_common_tool_audio_profile)
  "create_timer" -> stringResource(R.string.player_common_tool_timer_created)
  "cancel_timer" -> stringResource(R.string.player_common_tool_timer_cancelled)
  else -> if (success) stringResource(R.string.player_common_tool_done) else stringResource(R.string.player_common_tool_failed)
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
        Text(text = stringResource(R.string.player_common_curiosity_title), style = AppText.Title3, color = colors.onSurface)
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
              text = stringResource(R.string.player_common_curiosity_facts),
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
              text = stringResource(R.string.player_common_curiosity_sources),
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
        Text(stringResource(R.string.player_common_close), style = AppText.Headline)
      }
    }
  )
}
