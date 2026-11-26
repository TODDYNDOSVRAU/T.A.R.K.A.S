package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.audio_band_add
import com.example.windows.res.audio_band_delete
import com.example.windows.res.audio_band_freq_label
import com.example.windows.res.audio_band_gain_label
import com.example.windows.res.audio_band_no_gain
import com.example.windows.res.audio_band_off
import com.example.windows.res.audio_band_q_label
import com.example.windows.res.audio_band_select
import com.example.windows.res.audio_band_spoken
import com.example.windows.res.audio_band_title
import com.example.windows.res.audio_band_undo
import com.example.windows.res.audio_default
import com.example.windows.res.audio_enter_value
import com.example.windows.res.audio_freq_short
import com.example.windows.res.audio_gain
import com.example.windows.res.audio_gain_at
import com.example.windows.res.audio_stepper_less
import com.example.windows.res.audio_stepper_more
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import com.example.ui.components.IconAction
import com.example.ui.components.TonalButton
import com.example.ui.theme.Grid
import com.example.windows.equalizador.rememberReducedMotion
import kotlin.math.pow

/** How the bands are shown: one fader each, or one tile each with the editor behind it. */
internal enum class EqMode { GRAPHIC, PARAMETRIC }

/** A screen reader's adjust gesture moves gains in 1 dB steps across the +-15 dB range. */
private const val GAIN_STEPS = 29

/** Height of the value line above the faders and of one label row under them. */
private val LINE = Grid.u24 - Grid.u6

/** Neighbouring graphic bands closer than this share two staggered label rows. */
private val STAGGER_BELOW = Grid.u48 - Grid.u6 / 2

/** Press feedback: the control sinks to 0.96 in 150 ms and comes back the same way. */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
  val pressed by source.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "press")
  return this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/** Fades and lifts an item in with a delay of [index] steps, so a view arrives as a cascade. */
@Composable
private fun Modifier.stagger(index: Int, stepMs: Int = 16): Modifier {
  val reduced = rememberReducedMotion()
  val progress = remember { Animatable(if (reduced) 1f else 0f) }
  LaunchedEffect(Unit) {
    if (!reduced) progress.animateTo(1f, tween(260, delayMillis = index.coerceAtMost(15) * stepMs))
  }
  return this.graphicsLayer {
    alpha = progress.value
    translationY = (1f - progress.value) * Grid.u6.toPx()
  }
}

/** A band just deleted, kept for a few seconds so Undo can put it back where it was. */
private class RemovedBand(val index: Int, val band: EqBand, val number: Int, val after: List<EqBand>)

/**
 * The band area under the curve: faders aligned with their frequencies in the graphic mode, a tile grid
 * in the parametric one. A parametric tile opens the band editor. The two views cross-fade: the old one
 * leaves in 150 ms, the new one arrives in 260 ms with a short stagger between its items.
 */
@Composable
internal fun BandArea(
  settings: EqSettings,
  mode: EqMode,
  selectedBand: Int,
  onSelectBand: (Int) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  modifier: Modifier = Modifier
) {
  val reduced = rememberReducedMotion()
  var editing by rememberSaveable { mutableIntStateOf(-1) }
  var removed by remember { mutableStateOf<RemovedBand?>(null) }
  LaunchedEffect(removed) {
    if (removed != null) {
      kotlinx.coroutines.delay(8000)
      removed = null
    }
  }
  // Undo only makes sense for the list the delete left behind: a change of view, or a list replaced by
  // anything else (a preset, an import, a reset), drops it, so Undo never inserts into another layout.
  LaunchedEffect(mode) { removed = null }
  LaunchedEffect(settings.bands) {
    val pending = removed
    if (pending != null && settings.bands != pending.after) removed = null
  }

  fun deleteBand(i: Int) {
    val band = settings.bands.getOrNull(i) ?: return
    removed = RemovedBand(i, band, i + 1, settings.bands.filterIndexed { j, _ -> j != i })
    editing = -1
    onUpdateEq { s -> s.copy(bands = s.bands.filterIndexed { j, _ -> j != i }) }
    onSelectBand((i - 1).coerceAtLeast(0))
  }

  val open = editing >= 0 && mode == EqMode.PARAMETRIC && settings.bands.getOrNull(editing) != null
  // The band the bubble shows, kept while it closes so its content does not vanish before it has left.
  val shown = remember { intArrayOf(-1) }
  if (open) shown[0] = editing
  val bubble = remember { MutableTransitionState(false) }
  bubble.targetState = open
  var area by remember { mutableStateOf(IntRect.Zero) }
  // Where the tile that opened the editor sits: the popover hangs from it.
  var anchor by remember { mutableStateOf(IntRect.Zero) }

  Box(modifier = modifier.onGloballyPositioned { area = it.inWindow() }) {
  Column(modifier = Modifier.fillMaxSize()) {
    AnimatedContent(
      targetState = mode,
      transitionSpec = {
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else fadeIn(tween(260, delayMillis = 150)) togetherWith fadeOut(tween(150))
      },
      label = "eqMode",
      modifier = Modifier.weight(1f).fillMaxWidth()
    ) { m ->
      when (m) {
        EqMode.GRAPHIC -> GraphicBands(settings) { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } }
        EqMode.PARAMETRIC -> ParametricTiles(
          settings = settings,
          selected = selectedBand,
          dimmed = open,
          onOpen = { i, at -> anchor = at; onSelectBand(i); editing = i },
          onAdd = { at ->
            anchor = at
            onUpdateEq { s -> s.copy(bands = s.bands + EqBand(freqHz = 1000f, q = 1.4f)) }
            onSelectBand(settings.bands.size)
            editing = settings.bands.size
          }
        )
      }
    }
    // Under the tiles, never over them: the button takes its own row at the bottom of the band area.
    removed?.takeIf { mode == EqMode.PARAMETRIC }?.let { gone ->
      Box(modifier = Modifier.fillMaxWidth().padding(top = Grid.u6), contentAlignment = Alignment.Center) {
        TonalButton(
          text = tr(Res.string.audio_band_undo, gone.number),
          icon = Icons.Rounded.Undo,
          onClick = {
            if (mode == EqMode.PARAMETRIC && settings.bands == gone.after) {
              val at = gone.index.coerceIn(0, settings.bands.size)
              onUpdateEq { s -> s.copy(bands = s.bands.toMutableList().also { it.add(at.coerceAtMost(it.size), gone.band) }) }
              onSelectBand(at)
            }
            removed = null
          },
          modifier = Modifier.testTag("eq_undo_delete_band")
        )
      }
    }
  }

  val band = settings.bands.getOrNull(shown[0])
  if ((bubble.currentState || bubble.targetState) && band != null && mode == EqMode.PARAMETRIC) {
    val index = shown[0]
    BandBubble(state = bubble, area = area, anchor = anchor, onDismiss = { editing = -1 }) {
      BandEditor(
        index = index,
        band = band,
        eqEnabled = settings.enabled,
        onChange = { t -> onUpdateBand(index, t) },
        onDelete = { shown[0] = -1; deleteBand(index) }
      )
    }
  }
  }
}

/** Where a layout sits in the window, so the screen and its popup (the screen and a popup) can share one position. */
private fun LayoutCoordinates.inWindow(): IntRect {
  val r = boundsInWindow()
  return IntRect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
}

/** Width of the band bubble as a share of the tile grid it covers. */
private const val BUBBLE_SHARE = 0.75f

/** On a wide window the popover stays one phone-sheet wide (384 dp, 2x chain), never the width of the grid. */
private val BUBBLE_MAX_WIDTH = Grid.u384

/** Five 48 dp rows (header, type, Hz, dB, Q) inside a 6 dp inset. */
private val BUBBLE_HEIGHT = Grid.u48 * 5 + Grid.u12

/**
 * The band editor as a bubble over the tile grid: centred on the grid's columns, its top on the grid's
 * top, 0.75x as wide. It grows from 0.25 in 280 ms and leaves in 220 ms. A tap outside or Back closes it.
 */
@Composable
private fun BandBubble(state: MutableTransitionState<Boolean>, area: IntRect, anchor: IntRect, onDismiss: () -> Unit, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  Popup(
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false)
  ) {
    var window by remember { mutableStateOf(IntRect.Zero) }
    val density = LocalDensity.current
    val maxW = with(density) { BUBBLE_MAX_WIDTH.roundToPx() }
    val bubbleW = minOf((area.width * BUBBLE_SHARE).roundToInt(), maxW)
    val width = with(density) { bubbleW.toDp() }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { window = it.inWindow() }
        .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
      Box(
        modifier = Modifier
          .offset {
            val w = bubbleW
            val h = BUBBLE_HEIGHT.roundToPx()
            val edge = Grid.u24.roundToPx()
            val gap = Grid.u6.roundToPx()
            // Desktop popover: centred on the tile that opened it, just under it; above it when the window ends first.
            val x = (anchor.left + (anchor.width - w) / 2 - window.left).coerceIn(edge, (window.width - w - edge).coerceAtLeast(edge))
            val below = anchor.bottom + gap - window.top
            val above = anchor.top - gap - h - window.top
            val y = (if (below + h <= window.height - edge || above < edge) below else above)
              .coerceAtMost(window.height - h - edge).coerceAtLeast(edge)
            IntOffset(x, y)
          }
          .size(width, BUBBLE_HEIGHT)
      ) {
        AnimatedVisibility(
          visibleState = state,
          enter = if (reduced) EnterTransition.None else scaleIn(tween(280), initialScale = 0.25f) + fadeIn(tween(200)),
          exit = if (reduced) ExitTransition.None else scaleOut(tween(220), targetScale = 0.25f) + fadeOut(tween(160))
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clip(Grid.r24)
              .background(colors.surfaceContainerHigh)
              .border(Grid.hairline, colors.outlineVariant, Grid.r24)
              .pointerInput(Unit) { detectTapGestures { } }
              .padding(Grid.u6)
              .testTag("band_bubble")
          ) { content() }
        }
      }
    }
  }
}

/**
 * The graphic mode: each fader stands exactly under its frequency, on the same log scale and inset as
 * the curve above, so a fader and its point on the curve share an x. The 10 bands sit about 38 dp apart (an octave each),
 * so a 48 dp wide target cannot fit: the thumb is 12 x 48 and the column 18 wide. Every band shows its
 * value above the fader and its frequency under it; both alternate between two rows, so neighbours
 * never overlap. The band being moved takes the accent.
 */
@Composable
private fun GraphicBands(settings: EqSettings, onBandGain: (Int, Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  var held by remember { mutableIntStateOf(-1) }
  var entry by remember { mutableIntStateOf(-1) }
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val plot = maxWidth - Grid.inset * 2
    // Two staggered rows of value and frequency only where neighbouring columns are too close for
    // one row (about 44 dp for "-12.5"); a wide window gets one row each, so the fader grows.
    val xs = settings.bands.map { plot * freqToX(it.freqHz) }.sortedBy { it.value }
    val staggered = xs.zipWithNext().any { (a, b) -> b - a < STAGGER_BELOW }
    val rows = if (staggered) 2 else 1
    val faderHeight = maxHeight - LINE * 2 * rows
    val zeroLine = colors.onSurface.copy(alpha = 0.24f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val y = LINE.toPx() * rows + faderHeight.toPx() / 2f
      drawLine(
        color = zeroLine,
        start = Offset(Grid.inset.toPx(), y),
        end = Offset(size.width - Grid.inset.toPx(), y),
        strokeWidth = Grid.hairline.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(Grid.u6.toPx(), Grid.u6.toPx()))
      )
    }
    settings.bands.forEachIndexed { i, band ->
      val usesGain = band.type.usesGain
      val centre = Grid.inset + plot * freqToX(band.freqHz)
      Column(
        modifier = Modifier
          .offset(x = centre - Grid.u6 * 1.5f)
          .width(Grid.u6 * 3)
          .fillMaxHeight()
          .stagger(i),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Staggered: even bands keep the row next to the fader, above and below; odd ones the outer row.
        val near = !staggered || i % 2 == 0
        val outerNear = if (staggered && near) LINE else 0.dp
        val outerFar = if (staggered && !near) LINE else 0.dp
        Spacer(Modifier.height(outerNear))
        Box(modifier = Modifier.height(LINE).wrapContentWidth(unbounded = true), contentAlignment = Alignment.Center) {
          if (usesGain) {
            Text(
              formatDb(band.gainDb),
              style = if (held == i) EqType.MonoStrong else EqType.Mono,
              color = when {
                !settings.enabled -> colors.onSurfaceVariant
                held == i -> colors.primary
                else -> colors.onSurface
              },
              maxLines = 1,
              modifier = Modifier.clearAndSetSemantics {}
            )
          }
        }
        Spacer(Modifier.height(outerFar))
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) },
          label = tr(Res.string.audio_band_spoken, spokenHz(band.freqHz)),
          stateText = if (usesGain) "${formatDb(band.gainDb)} dB" else tr(Res.string.audio_band_no_gain, filterTypeLabel(band.type)),
          enabled = settings.enabled && usesGain,
          steps = GAIN_STEPS,
          onHeldChange = { isHeld -> held = if (isHeld) i else if (held == i) -1 else held },
          tint = bandColor(i),
          handleWidth = Grid.u12,
          handleHeight = Grid.u48,
          marks = false,
          trackWidth = EqSize.lineBold,
          handleColor = when {
            !(settings.enabled && usesGain) -> colors.onSurface.copy(alpha = 0.38f)
            held == i -> colors.primary
            else -> colors.onSurface
          },
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("vertical_fader_band_$i")
        )
        Spacer(Modifier.height(outerFar))
        Text(
          formatHz(band.freqHz),
          style = EqType.Mono,
          color = if (settings.enabled) colors.onSurface else colors.onSurfaceVariant,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .height(LINE)
            .wrapContentWidth(unbounded = true)
            .clickable(enabled = usesGain && settings.enabled, onClickLabel = stringResource(Res.string.audio_enter_value)) { entry = i }
        )
        Spacer(Modifier.height(outerNear))
      }
    }
  }
  val band = settings.bands.getOrNull(entry)
  if (band != null) {
    NumberEntryFor(
      NumberEdit(
        title = tr(Res.string.audio_gain_at, spokenHz(band.freqHz)), unit = "dB", value = band.gainDb,
        min = EqBand.MIN_GAIN, max = EqBand.MAX_GAIN, step = 0.5f, decimals = 1, defaultValue = 0f,
        onApply = { v -> onBandGain(entry, v) }
      )
    ) { entry = -1 }
  }
}

/** The parametric mode: three tiles per row, then a dashed tile that adds a band. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ParametricTiles(settings: EqSettings, selected: Int, dimmed: Boolean, onOpen: (Int, IntRect) -> Unit, onAdd: (IntRect) -> Unit) {
  // Behind the open bubble the tiles step back: slightly blurred and at half strength, in 240 ms.
  val dim by animateFloatAsState(if (dimmed) 1f else 0f, tween(240), label = "tilesDim")
  Column(
    modifier = Modifier
      .fillMaxSize()
      .blur(Grid.u6 / 2 * dim)
      .graphicsLayer { alpha = 1f - 0.5f * dim }
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    // 96 x 48 pieces on the grid, as many per row as the width allows (3 on a phone, more on a desktop).
    androidx.compose.foundation.layout.FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Grid.u12),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      for (c in 0..settings.bands.size) {
        var at by remember { mutableStateOf(IntRect.Zero) }
        Box(modifier = Modifier.width(Grid.u96).onGloballyPositioned { at = it.inWindow() }) {
          if (c < settings.bands.size) BandTile(c, settings.bands[c], c == selected, settings.enabled, { onOpen(c, at) }, Modifier.stagger(c))
          else AddTile({ onAdd(at) }, Modifier.stagger(c))
        }
      }
    }
  }
}

@Composable
private fun BandTile(index: Int, band: EqBand, selected: Boolean, eqEnabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val live = eqEnabled && band.enabled
  val number = index + 1
  val gainText = if (band.type.usesGain) "${formatDb(band.gainDb)} dB" else tr(Res.string.audio_band_no_gain, filterTypeLabel(band.type))
  val tileState = if (live) gainText else gainText + ", " + stringResource(Res.string.audio_band_off)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .pressScale(source)
      .clip(Grid.r12)
      .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = tr(Res.string.audio_band_select, number), onClick = onClick)
      .semantics { stateDescription = tileState }
      .padding(horizontal = Grid.u12)
      .testTag("band_card_$index"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(modifier = Modifier.size(Grid.u6).clip(CircleShape).background(if (live) bandColor(index) else colors.onSurface.copy(alpha = 0.38f)))
    Spacer(Modifier.width(Grid.u6))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        formatHz(band.freqHz),
        style = EqType.MonoStrong,
        color = if (live) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip
      )
      Text(
        if (band.type.usesGain) formatDb(band.gainDb) else "—",
        style = EqType.Mono,
        color = if (live && band.type.usesGain && band.gainDb != 0f) colors.primary else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip
      )
    }
  }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val edge = colors.onSurface.copy(alpha = 0.38f)
  val label = stringResource(Res.string.audio_band_add)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .pressScale(source)
      .clip(Grid.r12)
      // The dashed outline is drawn inside the box, so it never grows past its neighbours.
      .drawBehind {
        val w = Grid.hairline.toPx()
        drawRoundRect(
          color = edge,
          topLeft = Offset(w / 2f, w / 2f),
          size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
          cornerRadius = CornerRadius(Grid.u12.toPx()),
          style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(Grid.u6.toPx(), Grid.u6.toPx())))
        )
      }
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
      .semantics { contentDescription = label }
      .testTag("eq_add_band"),
    contentAlignment = Alignment.Center
  ) {
    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Grid.icon))
  }
}

/**
 * Everything one parametric band has, on the bubble's columns 48 | 48 | 1fr | 48: the header (power,
 * title with the filter type under it, delete), the filter type as five shapes, and steppers for Hz, dB
 * and Q under their unit. A tap on a value opens number entry; a double tap restores the default.
 */
@Composable
private fun BandEditor(index: Int, band: EqBand, eqEnabled: Boolean, onChange: ((EqBand) -> EqBand) -> Unit, onDelete: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val number = index + 1
  val live = eqEnabled && band.enabled
  Column(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
      BandPowerToggle(number, band.enabled, bandColor(index)) { on -> onChange { it.copy(enabled = on) } }
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr(Res.string.audio_band_title, number), style = EqType.BodyStrong, color = colors.onSurface, maxLines = 1)
        Text(filterTypeLabel(band.type), style = EqType.Label, color = colors.onSurfaceVariant, maxLines = 1)
      }
      IconAction(
        icon = Icons.Rounded.Delete,
        contentDescription = tr(Res.string.audio_band_delete, number),
        onClick = onDelete,
        tint = colors.onSurfaceVariant,
        size = Grid.u48
      )
    }
    TypeSegments(index, band.type, live) { picked -> onChange { it.copy(type = picked) } }

    val freqEdit = NumberEdit(
      title = tr(Res.string.audio_band_freq_label, number), unit = "Hz", value = band.freqHz,
      min = EqBand.MIN_FREQ, max = EqBand.MAX_FREQ, step = maxOf(1f, band.freqHz * 0.05f), decimals = 0,
      defaultValue = 1000f, resetLabel = "1000 Hz", allowK = true,
      onApply = { v -> onChange { it.copy(freqHz = v) } }
    )
    StepperRow(
      label = "Hz",
      spoken = stringResource(Res.string.audio_freq_short),
      compact = true,
      value = formatHz(band.freqHz),
      enabled = live,
      onLess = { onChange { it.copy(freqHz = (it.freqHz / SIXTH_OCTAVE).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) } },
      onMore = { onChange { it.copy(freqHz = (it.freqHz * SIXTH_OCTAVE).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) } },
      onReset = { onChange { it.copy(freqHz = 1000f) } },
      edit = freqEdit,
      tag = "band_freq_$index"
    )
    // The row stays when the filter has no gain, so the bubble never changes height.
    StepperRow(
      label = "dB",
      spoken = stringResource(Res.string.audio_gain),
      compact = true,
      value = if (band.type.usesGain) formatDb(band.gainDb) else "\u2014",
      enabled = live && band.type.usesGain,
      onLess = { onChange { it.copy(gainDb = (it.gainDb - 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
      onMore = { onChange { it.copy(gainDb = (it.gainDb + 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
      onReset = { onChange { it.copy(gainDb = 0f) } },
      edit = NumberEdit(
        title = tr(Res.string.audio_band_gain_label, number), unit = "dB", value = band.gainDb,
        min = EqBand.MIN_GAIN, max = EqBand.MAX_GAIN, step = 0.5f, decimals = 1, defaultValue = 0f,
        onApply = { v -> onChange { it.copy(gainDb = v) } }
      ),
      tag = "band_gain_$index"
    )
    StepperRow(
      label = "Q",
      compact = true,
      value = formatQ(band.q),
      enabled = live,
      onLess = { onChange { it.copy(q = (it.q - 0.1f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)) } },
      onMore = { onChange { it.copy(q = (it.q + 0.1f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)) } },
      onReset = { onChange { it.copy(q = EqBand.DEFAULT_Q) } },
      edit = NumberEdit(
        title = tr(Res.string.audio_band_q_label, number), unit = "", value = band.q,
        min = EqBand.MIN_Q, max = EqBand.MAX_Q, step = 0.1f, decimals = 2, defaultValue = EqBand.DEFAULT_Q,
        resetLabel = stringResource(Res.string.audio_default), onApply = { v -> onChange { it.copy(q = v) } }
      ),
      tag = "band_q_$index"
    )
  }
}

/** One sixth of an octave: the frequency stepper moves by this factor. */
private val SIXTH_OCTAVE = 2f.pow(1f / 6f)

/** Label, minus, value, plus. Every drag-free way to set a value: the buttons, a tap for number entry, a double tap for the default. */
@Composable
internal fun StepperRow(
  label: String,
  value: String,
  enabled: Boolean,
  onLess: () -> Unit,
  onMore: () -> Unit,
  onReset: (() -> Unit)?,
  edit: NumberEdit?,
  tag: String,
  /** What a screen reader says for [label], when the label on screen is only a unit. */
  spoken: String = label,
  /** In a bubble the label is a unit in the first 48 dp column; in a sheet it is a word 96 dp wide. */
  compact: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  var editing by remember { mutableStateOf(false) }
  if (editing && edit != null) NumberEntryFor(edit) { editing = false }
  Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
    if (compact) {
      Text(label, style = EqType.Label, color = colors.onSurface, maxLines = 1, modifier = Modifier.width(Grid.u48).padding(start = Grid.u6))
    } else {
      Text(label, style = EqType.Body, color = colors.onSurface, maxLines = 1, modifier = Modifier.width(Grid.u96))
    }
    StepButton(Icons.Rounded.Remove, tr(Res.string.audio_stepper_less, spoken), enabled, onLess)
    Box(
      modifier = Modifier
        .weight(1f)
        .height(Grid.u48)
        .pointerInput(enabled, edit, onReset) {
          if (!enabled) return@pointerInput
          detectTapGestures(onTap = { if (edit != null) editing = true }, onDoubleTap = { onReset?.invoke() })
        }
        .semantics {
          role = Role.Button
          contentDescription = "$spoken $value"
          onClick { if (enabled && edit != null) { editing = true; true } else false }
        }
        .testTag(tag),
      contentAlignment = Alignment.Center
    ) {
      Text(value, style = EqType.MonoStrong, color = if (enabled) colors.onSurface else colors.onSurfaceVariant, maxLines = 1)
    }
    StepButton(Icons.Rounded.Add, tr(Res.string.audio_stepper_more, spoken), enabled, onMore)
  }
}

@Composable
internal fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier
      .size(Grid.u48)
      .pressScale(source)
      .clip(CircleShape)
      .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      icon,
      contentDescription = description,
      tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
      modifier = Modifier.size(Grid.icon)
    )
  }
}

/** The filter type as a segmented control: the five response shapes in one row, each a full 48 dp target. */
@Composable
private fun TypeSegments(index: Int, type: FilterType, enabled: Boolean, onPick: (FilterType) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val options = listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF, FilterType.HIGH_PASS, FilterType.LOW_PASS)
  Row(modifier = Modifier.fillMaxWidth().height(Grid.u48).selectableGroup().testTag("band_type_$index")) {
    options.forEach { option ->
      val chosen = option == type
      val name = filterTypeLabel(option)
      // The touch target is the full cell; the highlight is inset 6 inside it.
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = { onPick(option) })
          .semantics { contentDescription = name }
          .testTag("band_type_${index}_${option.key}")
          .padding(Grid.u6)
          .clip(Grid.r6)
          .background(if (chosen) colors.primary.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center
      ) {
        FilterShapeIcon(
          type = option,
          color = when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            chosen -> colors.primary
            else -> colors.onSurface
          }
        )
      }
    }
  }
}

/** Turns one band on or off: a switch for screen readers, a power icon lit in the band colour when on. */
@Composable
private fun BandPowerToggle(number: Int, on: Boolean, tint: Color, onToggle: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val bandTitle = tr(Res.string.audio_band_title, number)
  Box(
    modifier = Modifier
      .size(Grid.u48)
      .clip(CircleShape)
      .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
      .semantics { contentDescription = bandTitle },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      Icons.Rounded.PowerSettingsNew,
      contentDescription = null,
      tint = if (on) tint else colors.onSurfaceVariant,
      modifier = Modifier.size(Grid.icon)
    )
  }
}
