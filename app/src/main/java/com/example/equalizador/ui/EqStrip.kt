package com.example.ui.screens.audio

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.components.FilterShapeIcon
import kotlin.math.roundToInt
import com.example.R
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.res.stringResource
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
import com.example.ui.components.EqFader
import com.example.ui.components.IconAction
import com.example.ui.components.TonalButton
import com.example.ui.theme.EqSize
import com.example.ui.theme.Grid
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.tactilePressScale
import kotlin.math.pow

/** How the bands are shown: one fader each, or one tile each with the editor behind it. */
internal enum class EqMode { GRAPHIC, PARAMETRIC }

/** A screen reader's adjust gesture moves gains in 1 dB steps across the +-15 dB range. */
private const val GAIN_STEPS = 29

/** Height of the value line above the faders and of one label row under them. */
private val LINE = Grid.u24 - Grid.u6

/** Press feedback: zero-recomposition GPU press compression (0.96x) with iOS spring release. */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier =
  this.tactilePressScale(source, pressedScale = 0.96f)

/** Fades and lifts an item in with a short spring cascade (GPU-only). */
@Composable
private fun Modifier.stagger(index: Int, stepMs: Int = 14): Modifier {
  val reduced = rememberReducedMotion()
  val progress = remember { Animatable(if (reduced) 1f else 0f) }
  LaunchedEffect(Unit) {
    if (!reduced) {
      if (index > 0) kotlinx.coroutines.delay(index.coerceAtMost(12) * stepMs.toLong())
      progress.animateTo(1f, Motion.fluid(visibilityThreshold = 0.002f))
    }
  }
  return this.graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * Grid.u6.toPx()
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
  modifier: Modifier = Modifier,
  onSoloBand: (EqBand?) -> Unit = {}
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
  val view = LocalView.current
  var area by remember { mutableStateOf(IntRect.Zero) }

  Box(modifier = modifier.onGloballyPositioned { area = it.onScreen(view) }) {
  Column(modifier = Modifier.fillMaxWidth()) {
    AnimatedContent(
      targetState = mode,
      transitionSpec = {
        if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(100))
        else (fadeIn(Motion.defaultEffects()) + scaleIn(initialScale = 0.98f, animationSpec = Motion.fluidFast())) togetherWith
          (fadeOut(Motion.defaultEffects()) + scaleOut(targetScale = 0.98f, animationSpec = Motion.fluidExit()))
      },
      label = "eqMode",
      modifier = Modifier.weight(1f, fill = mode == EqMode.GRAPHIC).fillMaxWidth()
    ) { m ->
      when (m) {
        EqMode.GRAPHIC -> GraphicBands(settings) { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } }
        EqMode.PARAMETRIC -> Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(Grid.u12)
        ) {
          val selBand = settings.bands.getOrNull(selectedBand)
          if (selBand != null) {
            SelectedBandQuickBar(
              index = selectedBand,
              band = selBand,
              eqEnabled = settings.enabled,
              onChangeBand = { transform -> onUpdateBand(selectedBand, transform) },
              onSoloBand = onSoloBand,
              onOpenEditor = { editing = selectedBand }
            )
          }
          Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
            ParametricTiles(
              settings = settings,
              selected = selectedBand,
              dimmed = open,
              onOpen = { i -> onSelectBand(i); editing = i },
              onAdd = {
                onUpdateEq { s -> s.copy(bands = s.bands + EqBand(freqHz = 1000f, q = 1.4f)) }
                onSelectBand(settings.bands.size)
                editing = settings.bands.size
              }
            )
          }
        }
      }
    }
    // Under the tiles, never over them: the button takes its own row at the bottom of the band area.
    removed?.takeIf { mode == EqMode.PARAMETRIC }?.let { gone ->
      Box(modifier = Modifier.fillMaxWidth().padding(top = Grid.u6), contentAlignment = Alignment.Center) {
        TonalButton(
          text = stringResource(R.string.audio_band_undo, gone.number),
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
    BandBubble(state = bubble, area = area, onDismiss = { onSoloBand(null); editing = -1 }) {
      BandEditor(
        index = index,
        band = band,
        eqEnabled = settings.enabled,
        onChange = { t -> onUpdateBand(index, t) },
        onDelete = { onSoloBand(null); shown[0] = -1; deleteBand(index) },
        onSoloBand = onSoloBand
      )
    }
  }
  }
}

/** Where a layout sits on the screen, so two windows (the screen and a popup) can share one position. */
private fun LayoutCoordinates.onScreen(view: View): IntRect {
  val at = IntArray(2).also { view.getLocationOnScreen(it) }
  val root = positionInRoot()
  val left = at[0] + root.x.roundToInt()
  val top = at[1] + root.y.roundToInt()
  return IntRect(left, top, left + size.width, top + size.height)
}

/** Width of the band bubble as a share of the tile grid it covers. */
private const val BUBBLE_SHARE = 0.75f

/** Six 48 dp rows (header, type, channel, Hz, dB, Q) inside a 6 dp inset (scrolls when slope row shows). */
private val BUBBLE_HEIGHT = Grid.u48 * 6 + Grid.u12

/**
 * The band editor as a bubble over the tile grid: centred on the grid's columns, its top on the grid's
 * top, 0.75x as wide. It grows from 0.25 in 280 ms and leaves in 220 ms. A tap outside or Back closes it.
 */
@Composable
private fun BandBubble(state: MutableTransitionState<Boolean>, area: IntRect, onDismiss: () -> Unit, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  Popup(
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false)
  ) {
    val view = LocalView.current
    var window by remember { mutableStateOf(IntRect.Zero) }
    val density = LocalDensity.current
    val width = with(density) { (area.width * BUBBLE_SHARE).toDp() }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { window = it.onScreen(view) }
        .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
      Box(
        modifier = Modifier
          .offset {
            val w = (area.width * BUBBLE_SHARE).roundToInt()
            val h = BUBBLE_HEIGHT.roundToPx()
            val edge = Grid.u24.roundToPx()
            val x = area.left + (area.width - w) / 2 - window.left
            val y = (area.top - window.top).coerceAtMost(window.height - h - edge).coerceAtLeast(edge)
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
    val faderHeight = maxHeight - LINE * 4
    val zeroLine = colors.onSurface.copy(alpha = 0.24f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val y = LINE.toPx() * 2 + faderHeight.toPx() / 2f
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
        // Even bands keep the row next to the fader, above and below; odd ones the outer row.
        Spacer(Modifier.height(if (i % 2 == 0) LINE else 0.dp))
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
        Spacer(Modifier.height(if (i % 2 == 0) 0.dp else LINE))
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) },
          label = stringResource(R.string.audio_band_spoken, spokenHz(band.freqHz)),
          stateText = if (usesGain) "${formatDb(band.gainDb)} dB" else stringResource(R.string.audio_band_no_gain, filterTypeLabel(band.type)),
          enabled = settings.enabled && usesGain,
          steps = GAIN_STEPS,
          onHeldChange = { isHeld -> held = if (isHeld) i else if (held == i) -1 else held },
          tint = com.example.ui.components.eqIntensityColor(band.gainDb / EqBand.MAX_GAIN),
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
        Spacer(Modifier.height(if (i % 2 == 0) 0.dp else LINE))
        Text(
          formatHz(band.freqHz),
          style = EqType.Mono,
          color = if (settings.enabled) colors.onSurface else colors.onSurfaceVariant,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .height(LINE)
            .wrapContentWidth(unbounded = true)
            .clickable(enabled = usesGain && settings.enabled, onClickLabel = stringResource(R.string.audio_enter_value)) { entry = i }
        )
        Spacer(Modifier.height(if (i % 2 == 0) LINE else 0.dp))
      }
    }
  }
  val band = settings.bands.getOrNull(entry)
  if (band != null) {
    NumberEntryFor(
      NumberEdit(
        title = stringResource(R.string.audio_gain_at, spokenHz(band.freqHz)), unit = "dB", value = band.gainDb,
        min = EqBand.MIN_GAIN, max = EqBand.MAX_GAIN, step = 0.5f, decimals = 1, defaultValue = 0f,
        onApply = { v -> onBandGain(entry, v) }
      )
    ) { entry = -1 }
  }
}

/** The parametric mode: three tiles per row on phones, scaling to 4–6 columns on tablets. */
@Composable
private fun ParametricTiles(settings: EqSettings, selected: Int, dimmed: Boolean, onOpen: (Int) -> Unit, onAdd: () -> Unit) {
  val cells = settings.bands.size + 1
  // Behind the open bubble the tiles step back: slightly blurred and at half strength, in 240 ms.
  val dim by animateFloatAsState(if (dimmed) 1f else 0f, tween(240), label = "tilesDim")
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val cols = if (maxWidth >= 600.dp) (maxWidth / (Grid.u96 + Grid.u48)).toInt().coerceIn(4, 6) else 3
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .blur(Grid.u6 / 2 * dim)
        .graphicsLayer { alpha = 1f - 0.5f * dim }
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      for (rowStart in 0 until cells step cols) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
          for (c in rowStart until rowStart + cols) {
            Box(modifier = Modifier.weight(1f)) {
              when {
                c < settings.bands.size -> BandTile(c, settings.bands[c], c == selected, settings.enabled, { onOpen(c) }, Modifier.stagger(c))
                c == settings.bands.size -> AddTile(onAdd, Modifier.stagger(c))
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Hybrid Logic Pro iPad / Pro-Q Band Inspector above the parametric tiles: on wide tablet canvases
 * (`>= 520 dp`) surfaces Frequency (Hz), Gain (dB) and Q side-by-side; on compact phones shows Q,
 * plus press-and-hold Solo and quick open into the full band editor.
 */
@Composable
private fun SelectedBandQuickBar(
  index: Int,
  band: EqBand,
  eqEnabled: Boolean,
  onChangeBand: ((EqBand) -> EqBand) -> Unit,
  onSoloBand: (EqBand?) -> Unit,
  onOpenEditor: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val live = eqEnabled && band.enabled
  val tint = bandColor(index)
  var soloHeld by remember { mutableStateOf(false) }
  androidx.compose.runtime.DisposableEffect(index) { onDispose { onSoloBand(null) } }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .clip(Grid.r12)
      .background(colors.surfaceContainer)
      .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), Grid.r12)
      .padding(horizontal = Grid.u6)
      .testTag("band_quick_bar")
  ) {
    val wideInspector = maxWidth >= 520.dp
    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Grid.u6)
    ) {
      Row(
        modifier = Modifier
          .height(Grid.u48)
          .clip(Grid.r6)
          .clickable(role = Role.Button, onClick = onOpenEditor)
          .padding(horizontal = Grid.u6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Grid.u6)
      ) {
        Box(modifier = Modifier.size(Grid.u6).clip(CircleShape).background(if (live) tint else colors.onSurfaceVariant))
        Text("#${index + 1}", style = EqType.MonoStrong, color = colors.onSurface, maxLines = 1)
        FilterShapeIcon(type = band.type, color = if (live) colors.primary else colors.onSurfaceVariant)
      }

      if (wideInspector) {
        // Frequency stepper (1/24-octave musical steps)
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          StepButton(Icons.Rounded.Remove, "Hz -", live) {
            val step = (band.freqHz * 0.03f).coerceAtLeast(1f)
            onChangeBand { it.copy(freqHz = (it.freqHz - step).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) }
          }
          Text(
            formatHz(band.freqHz),
            style = EqType.MonoStrong,
            color = if (live) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp)
          )
          StepButton(Icons.Rounded.Add, "Hz +", live) {
            val step = (band.freqHz * 0.03f).coerceAtLeast(1f)
            onChangeBand { it.copy(freqHz = (it.freqHz + step).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) }
          }
        }
        // Gain stepper (0.5 dB steps, when filter uses gain)
        if (band.type.usesGain) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            StepButton(Icons.Rounded.Remove, "dB -", live) {
              onChangeBand { it.copy(gainDb = (it.gainDb - 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) }
            }
            Text(
              "${formatDb(band.gainDb)} dB",
              style = EqType.MonoStrong,
              color = if (live) colors.primary else colors.onSurfaceVariant,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 4.dp)
            )
            StepButton(Icons.Rounded.Add, "dB +", live) {
              onChangeBand { it.copy(gainDb = (it.gainDb + 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) }
            }
          }
        }
      }

      // 1-finger Q control: - / Q value / +
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        StepButton(Icons.Rounded.Remove, "Q -", live) {
          onChangeBand { it.copy(q = (it.q - 0.1f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)) }
        }
        Text(
          "Q ${formatQ(band.q)}",
          style = EqType.MonoStrong,
          color = if (live) colors.onSurface else colors.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = if (wideInspector) 4.dp else Grid.u6)
        )
        StepButton(Icons.Rounded.Add, "Q +", live) {
          onChangeBand { it.copy(q = (it.q + 0.1f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)) }
        }
      }

      // Press-and-hold Solo
      Box(
        modifier = Modifier
          .size(Grid.u48)
          .clip(CircleShape)
          .background(if (soloHeld) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
          .pointerInput(band.freqHz, band.q, live) {
            if (!live) return@pointerInput
            detectTapGestures(
              onPress = {
                soloHeld = true
                onSoloBand(band)
                try {
                  tryAwaitRelease()
                } finally {
                  soloHeld = false
                  onSoloBand(null)
                }
              }
            )
          }
          .testTag("band_quick_solo"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Rounded.Headphones,
          contentDescription = stringResource(R.string.audio_band_solo),
          tint = if (soloHeld) colors.primary else colors.onSurfaceVariant,
          modifier = Modifier.size(Grid.icon)
        )
      }

      IconAction(
        icon = Icons.Rounded.Tune,
        contentDescription = stringResource(R.string.audio_band_edit),
        onClick = onOpenEditor,
        tint = colors.onSurface,
        size = Grid.u48
      )
    }
  }
}

@Composable
private fun BandTile(index: Int, band: EqBand, selected: Boolean, eqEnabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val live = eqEnabled && band.enabled
  val number = index + 1
  val tint = bandColor(index)
  val gainText = if (band.type.usesGain) "${formatDb(band.gainDb)} dB" else stringResource(R.string.audio_band_no_gain, filterTypeLabel(band.type))
  val tileState = if (live) gainText else gainText + ", " + stringResource(R.string.audio_band_off)
  val badge = buildString {
    when (band.channel) {
      com.example.data.model.BandChannel.LEFT -> append("L")
      com.example.data.model.BandChannel.RIGHT -> append("R")
      com.example.data.model.BandChannel.MID -> append("M")
      com.example.data.model.BandChannel.SIDE -> append("S")
      com.example.data.model.BandChannel.STEREO -> Unit
    }
    if (band.type.usesSlope && band.slope != com.example.data.model.FilterSlope.DB_12) {
      if (isNotEmpty()) append("·")
      append("${band.slope.dbPerOct}")
    }
    if (band.type.usesGain && kotlin.math.abs(band.dynamicRangeDb) >= 0.05f) {
      if (isNotEmpty()) append("·")
      append("DYN")
    }
  }
  val gainFrac = if (live && band.type.usesGain) (band.gainDb / EqBand.MAX_GAIN).coerceIn(-1f, 1f) else 0f

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .pressScale(source)
      .clip(Grid.r12)
      .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
      .drawBehind {
        if (kotlin.math.abs(gainFrac) > 0.01f) {
          val barH = Grid.hairline.toPx() * 2f
          val midX = size.width / 2f
          val span = (size.width / 2f) * kotlin.math.abs(gainFrac)
          val startX = if (gainFrac > 0f) midX else midX - span
          drawRect(
            color = tint.copy(alpha = 0.75f),
            topLeft = Offset(startX, size.height - barH),
            size = androidx.compose.ui.geometry.Size(span, barH)
          )
        }
      }
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.audio_band_select, number), onClick = onClick)
      .semantics { stateDescription = tileState }
      .padding(horizontal = Grid.u12)
      .testTag("band_card_$index"),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.size(Grid.u6).clip(CircleShape).background(if (live) tint else colors.onSurface.copy(alpha = 0.38f)))
      Spacer(Modifier.width(Grid.u6))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          formatHz(band.freqHz),
          style = EqType.MonoStrong,
          color = if (live) colors.onSurface else colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Clip
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)) {
          Text(
            if (band.type.usesGain) formatDb(band.gainDb) else "—",
            style = EqType.Mono,
            color = if (live && band.type.usesGain && band.gainDb != 0f) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip
          )
          if (badge.isNotEmpty()) {
            Text(
              badge,
              style = EqType.MonoStrong,
              color = colors.primary.copy(alpha = 0.85f),
              maxLines = 1,
              overflow = TextOverflow.Clip
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val edge = colors.onSurface.copy(alpha = 0.38f)
  val label = stringResource(R.string.audio_band_add)
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
private fun BandEditor(
  index: Int,
  band: EqBand,
  eqEnabled: Boolean,
  onChange: ((EqBand) -> EqBand) -> Unit,
  onDelete: () -> Unit,
  onSoloBand: (EqBand?) -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val number = index + 1
  val live = eqEnabled && band.enabled
  var soloHeld by remember { mutableStateOf(false) }
  androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onSoloBand(null) } }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
      BandPowerToggle(number, band.enabled, bandColor(index)) { on -> onChange { it.copy(enabled = on) } }
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.audio_band_title, number), style = EqType.BodyStrong, color = colors.onSurface, maxLines = 1)
        Text(filterTypeLabel(band.type), style = EqType.Label, color = colors.onSurfaceVariant, maxLines = 1)
      }
      // Press-and-hold Solo (Audition): isolates this band's frequency and Q while held.
      Box(
        modifier = Modifier
          .size(Grid.u48)
          .clip(CircleShape)
          .background(if (soloHeld) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
          .pointerInput(band.freqHz, band.q, live) {
            if (!live) return@pointerInput
            detectTapGestures(
              onPress = {
                soloHeld = true
                onSoloBand(band)
                try {
                  tryAwaitRelease()
                } finally {
                  soloHeld = false
                  onSoloBand(null)
                }
              }
            )
          }
          .semantics { contentDescription = "Solo" }
          .testTag("band_solo_$index"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.Headphones,
          contentDescription = stringResource(R.string.audio_band_solo),
          tint = if (soloHeld) colors.primary else colors.onSurfaceVariant,
          modifier = Modifier.size(Grid.icon)
        )
      }
      IconAction(
        icon = Icons.Rounded.Delete,
        contentDescription = stringResource(R.string.audio_band_delete, number),
        onClick = onDelete,
        tint = colors.onSurfaceVariant,
        size = Grid.u48
      )
    }
    TypeSegments(index, band.type, live) { picked -> onChange { it.copy(type = picked) } }

    val freqEdit = NumberEdit(
      title = stringResource(R.string.audio_band_freq_label, number), unit = "Hz", value = band.freqHz,
      min = EqBand.MIN_FREQ, max = EqBand.MAX_FREQ, step = maxOf(1f, band.freqHz * 0.05f), decimals = 0,
      defaultValue = 1000f, resetLabel = "1000 Hz", allowK = true,
      onApply = { v -> onChange { it.copy(freqHz = v) } }
    )
    StepperRow(
      label = "Hz",
      spoken = stringResource(R.string.audio_freq_short),
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
      spoken = stringResource(R.string.audio_gain),
      compact = true,
      value = if (band.type.usesGain) formatDb(band.gainDb) else "\u2014",
      enabled = live && band.type.usesGain,
      onLess = { onChange { it.copy(gainDb = (it.gainDb - 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
      onMore = { onChange { it.copy(gainDb = (it.gainDb + 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
      onReset = { onChange { it.copy(gainDb = 0f) } },
      edit = NumberEdit(
        title = stringResource(R.string.audio_band_gain_label, number), unit = "dB", value = band.gainDb,
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
        title = stringResource(R.string.audio_band_q_label, number), unit = "", value = band.q,
        min = EqBand.MIN_Q, max = EqBand.MAX_Q, step = 0.1f, decimals = 2, defaultValue = EqBand.DEFAULT_Q,
        resetLabel = stringResource(R.string.audio_default), onApply = { v -> onChange { it.copy(q = v) } }
      ),
      tag = "band_q_$index"
    )
    // Level 2 (expandable "More"): Channel (L/R/M/S), Slope (6..48 dB/oct), Dynamic EQ (Range/Threshold).
    val hasAdvanced = band.channel != com.example.data.model.BandChannel.STEREO ||
      (band.type.usesSlope && band.slope != com.example.data.model.FilterSlope.DB_12) ||
      (band.type.usesGain && kotlin.math.abs(band.dynamicRangeDb) >= 0.05f)
    var showMore by remember(index) { mutableStateOf(hasAdvanced) }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clip(Grid.r6)
        .clickable(role = Role.Button) { showMore = !showMore }
        .padding(horizontal = Grid.u12)
        .testTag("band_more_$index"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(if (showMore) R.string.audio_band_less else R.string.audio_band_more),
        style = EqType.LabelStrong,
        color = if (hasAdvanced) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )
      if (hasAdvanced && !showMore) {
        Box(modifier = Modifier.size(Grid.u6).clip(CircleShape).background(colors.primary))
        Spacer(Modifier.width(Grid.u6))
      }
      Icon(
        imageVector = if (showMore) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(Grid.icon)
      )
    }
    AnimatedVisibility(visible = showMore) {
      Column {
        ChannelSegments(index, band.channel, live) { picked -> onChange { it.copy(channel = picked) } }
        if (band.type.usesSlope) {
          SlopeSegments(index, band.slope, live) { picked -> onChange { it.copy(slope = picked) } }
        }
        if (band.type.usesGain) {
          StepperRow(
            label = stringResource(R.string.audio_band_dyn_range),
            compact = true,
            value = if (kotlin.math.abs(band.dynamicRangeDb) < 0.05f) "0" else "${formatDb(band.dynamicRangeDb)} dB",
            enabled = live,
            onLess = { onChange { it.copy(dynamicRangeDb = (it.dynamicRangeDb - 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
            onMore = { onChange { it.copy(dynamicRangeDb = (it.dynamicRangeDb + 0.5f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)) } },
            onReset = { onChange { it.copy(dynamicRangeDb = 0f) } },
            edit = NumberEdit(
              title = stringResource(R.string.audio_band_dyn_range), unit = "dB", value = band.dynamicRangeDb,
              min = EqBand.MIN_GAIN, max = EqBand.MAX_GAIN, step = 0.5f, decimals = 1, defaultValue = 0f,
              onApply = { v -> onChange { it.copy(dynamicRangeDb = v) } }
            ),
            tag = "band_dyn_range_$index"
          )
          if (kotlin.math.abs(band.dynamicRangeDb) >= 0.05f) {
            StepperRow(
              label = stringResource(R.string.audio_band_dyn_threshold),
              compact = true,
              value = "${formatDb(band.dynamicThresholdDb)} dB",
              enabled = live,
              onLess = { onChange { it.copy(dynamicThresholdDb = (it.dynamicThresholdDb - 1f).coerceIn(-48f, 0f)) } },
              onMore = { onChange { it.copy(dynamicThresholdDb = (it.dynamicThresholdDb + 1f).coerceIn(-48f, 0f)) } },
              onReset = { onChange { it.copy(dynamicThresholdDb = -18f) } },
              edit = NumberEdit(
                title = stringResource(R.string.audio_band_dyn_threshold), unit = "dB", value = band.dynamicThresholdDb,
                min = -48f, max = 0f, step = 1f, decimals = 0, defaultValue = -18f,
                onApply = { v -> onChange { it.copy(dynamicThresholdDb = v) } }
              ),
              tag = "band_dyn_thresh_$index"
            )
          }
        }
      }
    }
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
    StepButton(Icons.Rounded.Remove, stringResource(R.string.audio_stepper_less, spoken), enabled, onLess)
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
    StepButton(Icons.Rounded.Add, stringResource(R.string.audio_stepper_more, spoken), enabled, onMore)
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

/** The filter type as a segmented control: all response shapes in a 48 dp row with 48 dp targets. */
@Composable
private fun TypeSegments(index: Int, type: FilterType, enabled: Boolean, onPick: (FilterType) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val options = FilterType.entries
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .horizontalScroll(rememberScrollState())
      .selectableGroup()
      .testTag("band_type_$index")
  ) {
    options.forEach { option ->
      val chosen = option == type
      val name = filterTypeLabel(option)
      Box(
        modifier = Modifier
          .width(Grid.u48)
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

/** Stereo / Mid-Side channel routing for a parametric band. */
@Composable
private fun ChannelSegments(
  index: Int,
  channel: com.example.data.model.BandChannel,
  enabled: Boolean,
  onPick: (com.example.data.model.BandChannel) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .horizontalScroll(rememberScrollState())
      .selectableGroup()
      .testTag("band_channel_$index")
  ) {
    com.example.data.model.BandChannel.entries.forEach { option ->
      val chosen = option == channel
      val label = bandChannelLabel(option)
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = { onPick(option) })
          .testTag("band_channel_${index}_${option.key}")
          .padding(Grid.u6)
          .clip(Grid.r6)
          .background(if (chosen) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
          .padding(horizontal = Grid.u12),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = label,
          style = if (chosen) EqType.LabelStrong else EqType.Label,
          color = when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            chosen -> colors.primary
            else -> colors.onSurfaceVariant
          },
          maxLines = 1
        )
      }
    }
  }
}

/** Butterworth cascade slope selector (6..48 dB/oct) for High-Pass and Low-Pass filters. */
@Composable
private fun SlopeSegments(
  index: Int,
  slope: com.example.data.model.FilterSlope,
  enabled: Boolean,
  onPick: (com.example.data.model.FilterSlope) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .horizontalScroll(rememberScrollState())
      .selectableGroup()
      .testTag("band_slope_$index")
  ) {
    com.example.data.model.FilterSlope.entries.forEach { option ->
      val chosen = option == slope
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = { onPick(option) })
          .testTag("band_slope_${index}_${option.dbPerOct}")
          .padding(Grid.u6)
          .clip(Grid.r6)
          .background(if (chosen) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
          .padding(horizontal = Grid.u12),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "${option.dbPerOct} dB",
          style = if (chosen) EqType.MonoStrong else EqType.Mono,
          color = when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            chosen -> colors.primary
            else -> colors.onSurfaceVariant
          },
          maxLines = 1
        )
      }
    }
  }
}

/** Turns one band on or off: a switch for screen readers, a power icon lit in the band colour when on. */
@Composable
private fun BandPowerToggle(number: Int, on: Boolean, tint: Color, onToggle: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val bandTitle = stringResource(R.string.audio_band_title, number)
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
