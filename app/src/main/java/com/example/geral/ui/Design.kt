package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.theme.AppMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.tactilePressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.drawLiquidGlassThumb
import com.example.ui.theme.liquidChrome
import com.example.ui.theme.liquidLensPill
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.gridHeight

/*
 * The app's building blocks. Every screen is made of these, so a row, a group, a button or a header
 * looks and behaves the same everywhere: same type role, same height, same press feedback, same
 * accessibility semantics. Colours come from the theme, sizes from Tokens.kt.
 */

// ---- press feedback ----

/**
 * Click with the platform's quiet highlight: the surface darkens slightly while pressed, instead of a
 * ripple or a bounce. Driven purely in [drawBehind] via [Animatable] so tapping never recomposes.
 */
@Composable
fun Modifier.highlightClickable(
  enabled: Boolean = true,
  role: Role = Role.Button,
  onClickLabel: String? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onClick: () -> Unit
): Modifier {
  val highlightColor = MaterialTheme.colorScheme.onSurface
  val alpha = remember { Animatable(0f) }
  LaunchedEffect(interactionSource) {
    var pressCount = 0
    var job: Job? = null
    interactionSource.interactions.collect { ev ->
      when (ev) {
        is PressInteraction.Press -> {
          pressCount++
          job?.cancel()
          job = launch { alpha.snapTo(0.08f) }
        }
        is PressInteraction.Release, is PressInteraction.Cancel -> {
          pressCount = (pressCount - 1).coerceAtLeast(0)
          if (pressCount == 0) {
            job?.cancel()
            job = launch { alpha.animateTo(0f, Motion.defaultEffects()) }
          }
        }
      }
    }
  }
  return this
    .clickable(
      interactionSource = interactionSource,
      indication = null,
      enabled = enabled,
      role = role,
      onClickLabel = onClickLabel,
      onClick = onClick
    )
    .drawBehind {
      val a = alpha.value
      if (a > 0.001f) drawRect(highlightColor.copy(alpha = a))
    }
}

// ---- headers ----

/** A section's title inside a page, with an optional action at the trailing edge ("Ver tudo"). */
@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {}
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Grid.u48)
      .padding(horizontal = Grid.margin),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      style = GridText.Title,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    if (actionLabel != null) TextAction(actionLabel, onAction)
  }
}

/** The small label above a group of rows: 12 sp technical type, starting where the rows' text starts. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = GridText.Small.copy(fontWeight = FontWeight.Medium),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
      .padding(bottom = Grid.u12)
      .gridHeight(min = Grid.u24)
      .padding(start = Grid.margin + Grid.inset, end = Grid.margin + Grid.inset)
      .semantics { heading() }
  )
}

/** The note under a group of rows, explaining what its setting does. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = GridText.Small,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
      .padding(top = Grid.u12)
      .gridHeight(min = Grid.u24)
      .padding(start = Grid.margin + Grid.inset, end = Grid.margin + Grid.inset)
  )
}

/**
 * The top of a sub-page: a back control naming where it goes, then the page's large title. Back is a
 * real button (48 dp, announced as "Voltar para …").
 */
@Composable
fun SubPageHeader(
  title: String,
  backLabel: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  trailing: @Composable RowScope.() -> Unit = {}
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Grid.inset, end = Grid.inset),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val backActionLabel = stringResource(R.string.player_common_back)
      Row(
        modifier = Modifier
          .heightIn(min = Grid.u48)
          .clip(Grid.r24)
          .clickable(role = Role.Button, onClickLabel = backActionLabel, onClick = onBack)
          .padding(horizontal = Grid.inset),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBackIos,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(Grid.icon)
        )
        Spacer(Modifier.width(Grid.u6))
        Text(backLabel, style = GridText.Body, color = MaterialTheme.colorScheme.primary, maxLines = 1)
      }
      Spacer(Modifier.weight(1f))
      trailing()
    }
    Text(
      text = title,
      style = GridText.Title,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .gridHeight(min = Grid.u48)
        .padding(horizontal = Grid.margin)
        .semantics { heading() }
    )
  }
}

// ---- grouped lists ----

/**
 * A rounded group of rows on the page, the way grouped settings lists are drawn. Put [ListRow]s,
 * [SwitchRow]s or [RadioRow]s inside, separated by [GroupDivider].
 */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Grid.margin)
      .clip(Grid.r12)
      .background(MaterialTheme.colorScheme.surface),
    content = content
  )
}

/**
 * Hairline between two rows of a group, inset to where the row's text starts. It is drawn over the
 * boundary and takes no height, so rows stay on the 12 dp rhythm.
 */
@Composable
fun GroupDivider(inset: Dp = Grid.inset, endInset: Dp = 0.dp) {
  val color = MaterialTheme.colorScheme.outline
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(0.dp)
      .drawBehind {
        val w = Grid.hairline.toPx()
        drawLine(color, Offset(inset.toPx(), -w / 2), Offset(size.width - endInset.toPx(), -w / 2), strokeWidth = w)
      }
  )
}

/** A full-width hairline. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.hairline)
      .background(MaterialTheme.colorScheme.outline)
  )
}

/** A row's icon: 24 dp, gray unless the row gives it a colour of its own. The accent is kept for state. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Grid.icon))
}

/**
 * The layout every row shares: 48 dp tall with one line, taller in steps of 12 dp when the text wraps
 * or has a subtitle; content 12 dp from the group's edge; title 18 sp, subtitle 12 sp.
 */
@Composable
private fun RowLayout(
  title: String,
  subtitle: String?,
  icon: ImageVector?,
  iconTint: Color,
  titleColor: Color,
  modifier: Modifier,
  trailing: @Composable RowScope.() -> Unit
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .gridHeight(min = Grid.u48)
      .padding(horizontal = Grid.inset, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      RowIcon(icon, iconTint)
      Spacer(Modifier.width(Grid.inset))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = GridText.Body, color = titleColor)
      if (subtitle != null) {
        Text(subtitle, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    trailing()
  }
}

/**
 * A row that opens something or runs an action. [value] shows the current choice at the trailing
 * edge; [chevron] says it leads to another page; [destructive] colours it as an irreversible action.
 */
@Composable
fun ListRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  value: String? = null,
  chevron: Boolean = true,
  destructive: Boolean = false,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = when {
      !enabled -> colors.onSurface.copy(alpha = 0.38f)
      destructive -> colors.error
      else -> iconTint
    },
    titleColor = when {
      !enabled -> colors.onSurface.copy(alpha = 0.38f)
      destructive -> colors.error
      else -> colors.onSurface
    },
    modifier = modifier.highlightClickable(enabled = enabled, onClick = onClick)
  ) {
    if (value != null) {
      Text(
        value,
        style = GridText.Label,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Grid.inset)
      )
    }
    if (chevron) {
      Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(Grid.icon)
      )
    }
  }
}

/** A row with a text that only informs (version, cache size). */
@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) {
  RowLayout(
    title = title,
    subtitle = null,
    icon = null,
    iconTint = Color.Unspecified,
    titleColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier.semantics(mergeDescendants = true) {}
  ) {
    Text(value, style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = Grid.inset))
  }
}

/** A setting that is on or off. The whole row toggles; the switch only shows the state. */
@Composable
fun SwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = colors.onSurfaceVariant,
    titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
  ) {
    Spacer(Modifier.width(Grid.inset))
    GridSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
  }
}

/**
 * The grid's switch: 48 x 24 dp, GPU-animated via [graphicsLayer] (0 relayouts per frame) with
 * iOS 26 spring travel, subtle press-stretch, optional horizontal drag-to-toggle and haptic tick.
 */
@Composable
fun GridSwitch(
  checked: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onCheckedChange: ((Boolean) -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  val isLiquid = com.example.ui.theme.LocalSurfaceStyle.current == com.example.ui.theme.SurfaceStyle.LIQUID &&
    com.example.ui.theme.liquidGlassSupported
  val isDark = colors.background.luminance() < 0.5f
  val reduced = AppMotion.reduced
  val fade = if (enabled) 1f else 0.38f
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val currentChecked by rememberUpdatedState(checked)
  val currentOnChange by rememberUpdatedState(onCheckedChange)

  val progress = remember { Animatable(if (checked) 1f else 0f) }
  val stretch = remember { Animatable(1f) }
  var dragging = remember { booleanArrayOf(false) }

  LaunchedEffect(checked, reduced) {
    if (!dragging[0]) {
      val target = if (checked) 1f else 0f
      if (reduced) progress.snapTo(target)
      else progress.animateTo(target, Motion.fluid(visibilityThreshold = 0.002f))
    }
  }

  val track by animateColorAsState(
    if (checked) colors.primary.copy(alpha = (if (isLiquid) 0.78f else 1f) * fade)
    else colors.onSurface.copy(alpha = (if (isLiquid) 0.18f else 0.2f) * fade),
    Motion.defaultEffects(), label = "switchTrack"
  )
  val thumb by animateColorAsState(
    if (checked) colors.onPrimary else colors.onSurface.copy(alpha = fade),
    Motion.defaultEffects(), label = "switchThumb"
  )

  val travelDp = if (isLiquid) 20.dp else Grid.u24
  val startOffsetDp = if (isLiquid) 0.dp else 3.dp

  val gestureMod = if (!enabled) Modifier else Modifier.pointerInput(isLiquid, reduced) {
    val travelPx = travelDp.toPx().coerceAtLeast(1f)
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (!reduced) {
        scope.launch { stretch.animateTo(1.16f, Motion.criticalSpatial(stiffness = 900f)) }
      }
      val cb = currentOnChange
      if (cb != null) {
        var moved = false
        var lastSide = currentChecked
        try {
          horizontalDrag(down.id) { change ->
            val dx = change.positionChange().x
            if (moved || kotlin.math.abs(dx) > 2f) {
              moved = true
              dragging[0] = true
              change.consume()
              val nextP = (progress.value + dx / travelPx).coerceIn(0f, 1f)
              scope.launch { progress.snapTo(nextP) }
              val side = nextP >= 0.5f
              if (side != lastSide) {
                lastSide = side
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
            }
          }
        } finally {
          dragging[0] = false
          if (moved) {
            val targetChecked = progress.value >= 0.5f
            if (targetChecked != currentChecked) cb(targetChecked)
            scope.launch {
              if (reduced) progress.snapTo(if (targetChecked) 1f else 0f)
              else progress.animateTo(if (targetChecked) 1f else 0f, Motion.fluid(visibilityThreshold = 0.002f))
            }
          }
          if (!reduced) {
            scope.launch { stretch.animateTo(1f, Motion.fluidFast(visibilityThreshold = 0.002f)) }
          }
        }
      } else {
        // Observe release for press-stretch even when toggled by parent row.
        while (true) {
          val ev = awaitPointerEvent(PointerEventPass.Initial)
          if (ev.changes.none { it.pressed }) break
        }
        if (!reduced) {
          scope.launch { stretch.animateTo(1f, Motion.fluidFast(visibilityThreshold = 0.002f)) }
        }
      }
    }
  }

  val thumbTransform = Modifier.graphicsLayer {
    val p = progress.value
    translationX = startOffsetDp.toPx() + p * travelDp.toPx()
    val s = stretch.value
    scaleX = s
    scaleY = 1f - (s - 1f) * 0.25f
    transformOrigin = TransformOrigin(if (p >= 0.5f) 1f else 0f, 0.5f)
  }

  if (isLiquid) {
    val surfaceBg = colors.surface
    val trackBackdrop = rememberLayerBackdrop(
      onDraw = {
        drawRect(surfaceBg)
        drawContent()
      }
    )
    // Unclipped outer container so the oblong Liquid Glass pill floats cleanly over the track.
    Box(
      modifier = modifier
        .size(width = Grid.u48, height = Grid.u24)
        .then(gestureMod),
      contentAlignment = Alignment.CenterStart
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .layerBackdrop(trackBackdrop)
          .padding(vertical = 2.dp)
          .clip(Grid.r12)
          .background(track)
      )
      Box(
        modifier = Modifier
          .then(thumbTransform)
          .size(width = 28.dp, height = 20.dp)
          .liquidLensPill(trackBackdrop, isDark = isDark, trackAccent = if (checked) colors.primary else null)
      )
    }
  } else {
    Box(
      modifier = modifier
        .size(width = Grid.u48, height = Grid.u24)
        .then(gestureMod)
        .clip(Grid.r12)
        .background(track),
      contentAlignment = Alignment.CenterStart
    ) {
      Box(
        Modifier
          .then(thumbTransform)
          .size(18.dp)
          .clip(CircleShape)
          .background(thumb)
      )
    }
  }
}

/** One choice among several. The chosen one carries a check mark, as in the platform's own lists. */
@Composable
fun RadioRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  /** Something of its own beside the check mark (a button that opens more settings of this choice). */
  accessory: (@Composable () -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = colors.onSurfaceVariant,
    titleColor = colors.onSurface,
    modifier = modifier
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
  ) {
    accessory?.invoke()
    Box(Modifier.padding(start = Grid.inset).size(Grid.icon), contentAlignment = Alignment.Center) {
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Grid.icon))
    }
  }
}

// ---- buttons ----

/** The one main action of a screen or a sheet: filled with the accent, full width by default. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  ButtonLayout(
    text = text,
    icon = icon,
    container = if (enabled) colors.primary else colors.surfaceContainerHigh,
    content = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

/** A secondary action: a quiet fill with the accent's text, like Play and Shuffle above a list. */
@Composable
fun TonalButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  destructive: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  ButtonLayout(
    text = text,
    icon = icon,
    container = colors.surfaceContainer,
    content = when {
      !enabled -> colors.onSurface.copy(alpha = 0.38f)
      destructive -> colors.error
      else -> colors.primary
    },
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun ButtonLayout(
  text: String,
  icon: ImageVector?,
  container: Color,
  content: Color,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier
) {
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier = modifier
      .tactilePressScale(interaction, pressedScale = 0.96f)
      .heightIn(min = Grid.u48)
      .clip(Grid.r24)
      .background(container)
      .highlightClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
      .padding(horizontal = Grid.inset),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Grid.icon))
      Spacer(Modifier.width(Grid.u6))
    }
    Text(text, style = GridText.BodyStrong, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A text-only action in the accent colour ("Ver tudo", "Editar", "Limpar"), 48 dp tall. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Box(
    modifier = modifier
      .heightIn(min = Grid.u48)
      .clip(Grid.r24)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = Grid.inset),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      style = GridText.Body,
      color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      maxLines = 1
    )
  }
}

/** A round icon button with a label for screen readers. 48 dp touch, [size] visible. */
@Composable
fun IconAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  filled: Boolean = false,
  size: Dp = Grid.u48
) {
  Box(
    modifier = modifier
      .size(maxOf(size, Grid.u48))
      .clip(androidx.compose.foundation.shape.CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(androidx.compose.foundation.shape.CircleShape)
        .then(if (filled) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Grid.icon))
    }
  }
}

/**
 * A filter among a few views of one list (Músicas, Playlists, Favoritos). Announced as a tab. A 48 dp
 * pill; the selected one is filled with the text colour.
 */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val isLiquid = com.example.ui.theme.LocalSurfaceStyle.current == com.example.ui.theme.SurfaceStyle.LIQUID &&
    com.example.ui.theme.liquidGlassSupported
  // 48 dp touch target with a leaner 36 dp Apple HIG visual pill inside.
  val chipInteraction = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .tactilePressScale(chipInteraction, pressedScale = 0.96f)
      .heightIn(min = Grid.u48)
      .selectable(
        selected = selected,
        interactionSource = chipInteraction,
        indication = null,
        role = Role.Tab,
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .height(36.dp)
        .clip(Grid.pill)
        .then(
          if (isLiquid) Modifier.liquidChrome(Grid.pill, selected = selected)
          else Modifier.background(if (selected) colors.onSurface else colors.surfaceContainer)
        )
        .padding(horizontal = 16.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        label,
        style = GridText.Small.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        color = when {
          isLiquid && selected -> colors.primary
          isLiquid -> colors.onSurface
          selected -> colors.background
          else -> colors.onSurface
        },
        maxLines = 1
      )
    }
  }
}

/**
 * A choice among a few views of one page, as one 48 dp pill split into equal parts. Put [Segment]s
 * inside. The chosen part is a raised pill 6 dp inside the track; the whole 48 dp height is the target.
 */
@Composable
fun SegmentedTabs(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  val isLiquid = com.example.ui.theme.LocalSurfaceStyle.current == com.example.ui.theme.SurfaceStyle.LIQUID &&
    com.example.ui.theme.liquidGlassSupported
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .clip(Grid.r24)
      .then(
        if (isLiquid) Modifier.liquidChrome(Grid.r24, selected = false)
        else Modifier.background(MaterialTheme.colorScheme.surface)
      )
      .padding(horizontal = Grid.u6)
      .selectableGroup(),
    content = content
  )
}

/** One part of [SegmentedTabs]. Announced as a tab. */
@Composable
fun RowScope.Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val isLiquid = com.example.ui.theme.LocalSurfaceStyle.current == com.example.ui.theme.SurfaceStyle.LIQUID &&
    com.example.ui.theme.liquidGlassSupported
  val fill by animateColorAsState(
    if (selected) colors.surfaceContainerHigh else colors.surfaceContainerHigh.copy(alpha = 0f),
    Motion.defaultEffects(), label = "segmentFill"
  )
  val text by animateColorAsState(
    when {
      isLiquid && selected -> colors.primary
      selected -> colors.onSurface
      else -> colors.onSurfaceVariant
    },
    Motion.defaultEffects(), label = "segmentText"
  )
  Box(
    modifier = modifier
      .weight(1f)
      .fillMaxHeight()
      .selectable(
        selected = selected,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Tab,
        onClick = onClick
      )
      .padding(vertical = Grid.u6),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(Grid.r24)
        .then(
          if (isLiquid && selected) Modifier.liquidChrome(Grid.r24, selected = true)
          else if (!isLiquid) Modifier.background(fill)
          else Modifier
        )
        .padding(horizontal = Grid.u6),
      contentAlignment = Alignment.Center
    ) {
      Text(label, style = GridText.Small.copy(fontWeight = FontWeight.Medium), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

// ---- search ----

/** The search field: a quiet rounded fill with the magnifier, and a clear button once there is text. */
@Composable
fun SearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  onSearch: () -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val textStyle: TextStyle = GridText.Body.copy(color = colors.onSurface)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Grid.u48)
      .clip(Grid.r24)
      .background(colors.surfaceContainer)
      .padding(start = Grid.inset),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Grid.icon))
    Spacer(Modifier.width(Grid.inset))
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (value.isEmpty()) {
        Text(placeholder, style = GridText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
          .fillMaxWidth()
          .semantics { contentDescription = placeholder }
      )
    }
    if (value.isNotEmpty()) {
      IconAction(
        icon = Icons.Rounded.Cancel,
        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.library_search_clear),
        onClick = { onValueChange("") },
        tint = colors.onSurfaceVariant,
        size = Grid.u24
      )
    } else {
      Spacer(Modifier.width(Grid.inset))
    }
  }
}

// ---- tracks ----

/**
 * A song in a list: artwork, title, artist line, and a "more" button. The current track shows its
 * title in the accent with a small equaliser on the artwork while it plays.
 */
@Composable
fun TrackRow(
  track: Track,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isCurrent: Boolean = false,
  isPlaying: Boolean = false,
  onMore: (() -> Unit)? = null,
  trailing: @Composable RowScope.() -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val playActionLabel = stringResource(R.string.home_play_track, track.title)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = playActionLabel, onClick = onClick)
      // Artwork on the 24 dp margin. With a "more" button (48 dp target, 24 dp glyph) the end is 12, so
      // the glyph itself ends on the margin.
      .padding(start = Grid.margin, end = if (onMore != null) Grid.inset else Grid.margin, top = Grid.u6, bottom = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrackArtwork(
      track = track,
      shape = Grid.r6,
      iconSize = Grid.icon,
      playing = isCurrent && isPlaying,
      modifier = Modifier.size(Grid.u48)
    )
    Spacer(Modifier.width(Grid.u12))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        track.title,
        style = GridText.Body,
        color = if (isCurrent) colors.primary else colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        subtitle,
        style = GridText.Small,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    trailing()
    if (onMore != null) {
      IconAction(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.player_more_options_for, track.title),
        onClick = onMore,
        tint = colors.onSurfaceVariant
      )
    }
  }
}

/** Provides [color] as the content colour for icons placed inside a custom container. */
@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalContentColor provides color, content = content)
}

/** Corner radius of a group, exposed for screens that draw their own grouped surfaces. */
val GroupRadius: Dp get() = Grid.u12
