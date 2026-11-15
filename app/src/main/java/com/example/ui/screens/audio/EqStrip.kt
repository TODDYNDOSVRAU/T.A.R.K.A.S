package com.example.ui.screens.audio

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.theme.Grid
import com.example.ui.theme.rememberReducedMotion
import kotlin.math.pow

/** How the bands are shown: one fader each, or one tile each with the editor behind it. */
internal enum class EqMode { GRAPHIC, PARAMETRIC }

/** A screen reader's adjust gesture moves gains in 1 dB steps across the +-15 dB range. */
private const val GAIN_STEPS = 29

/** Height of the value line above the faders and of one label row under them. */
private val LINE = Grid.u24 - Grid.u6

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
    translationY = (1f - progress.value) * 6.dp.toPx()
  }
}

/** A band just deleted, kept for a few seconds so Undo can put it back where it was. */
private class RemovedBand(val index: Int, val band: EqBand, val number: Int, val after: List<EqBand>)

/**
 * The band area under the curve: faders aligned with their frequencies in the graphic mode, a tile grid
 * in the parametric one. A parametric tile opens the band editor. The two views cross-fade: the old one
 * leaves in 150 ms, the new one arrives in 260 ms with a short stagger between its items.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

  Column(modifier = modifier) {
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
          onOpen = { i -> onSelectBand(i); editing = i },
          onAdd = {
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

  val band = settings.bands.getOrNull(editing)
  if (editing >= 0 && band != null && mode == EqMode.PARAMETRIC) {
    ModalBottomSheet(
      onDismissRequest = { editing = -1 },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      BandEditor(
        index = editing,
        band = band,
        eqEnabled = settings.enabled,
        onChange = { t -> onUpdateBand(editing, t) },
        onDelete = { deleteBand(editing) }
      )
    }
  }
}

/**
 * The graphic mode: each fader stands exactly under its frequency, on the same log scale and inset as
 * the curve above, so a fader and its point on the curve share an x. The 15 bands sit about 19 dp apart,
 * so a 48 dp wide target cannot fit: the thumb is 12 x 48, the column 18 wide, the frequency labels
 * alternate between two rows, and the value of the band being moved shows above its thumb.
 */
@Composable
private fun GraphicBands(settings: EqSettings, onBandGain: (Int, Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  var held by remember { mutableIntStateOf(-1) }
  var entry by remember { mutableIntStateOf(-1) }
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val plot = maxWidth - Grid.inset * 2
    val faderHeight = maxHeight - LINE - LINE * 2
    val zeroLine = colors.onSurface.copy(alpha = 0.24f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val y = LINE.toPx() + faderHeight.toPx() / 2f
      drawLine(
        color = zeroLine,
        start = Offset(Grid.inset.toPx(), y),
        end = Offset(size.width - Grid.inset.toPx(), y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
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
        Box(modifier = Modifier.height(LINE).wrapContentWidth(unbounded = true), contentAlignment = Alignment.Center) {
          if (held == i && usesGain) {
            Text(formatDb(band.gainDb), style = EqType.MonoStrong, color = colors.primary, maxLines = 1)
          }
        }
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) },
          label = stringResource(R.string.audio_band_spoken, spokenHz(band.freqHz)),
          stateText = if (usesGain) "${formatDb(band.gainDb)} dB" else stringResource(R.string.audio_band_no_gain, filterTypeLabel(band.type)),
          enabled = settings.enabled && usesGain,
          steps = GAIN_STEPS,
          onHeldChange = { isHeld -> held = if (isHeld) i else if (held == i) -1 else held },
          tint = bandColor(i),
          handleWidth = Grid.u12,
          handleHeight = Grid.u48,
          marks = false,
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("vertical_fader_band_$i")
        )
        // Even bands label on the first row, odd ones on the second, so neighbours never overlap.
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

/** The parametric mode: three tiles per row, then a dashed tile that adds a band. */
@Composable
private fun ParametricTiles(settings: EqSettings, selected: Int, onOpen: (Int) -> Unit, onAdd: () -> Unit) {
  val cells = settings.bands.size + 1
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Grid.u24)
  ) {
    for (rowStart in 0 until cells step 3) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        for (c in rowStart until rowStart + 3) {
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

@Composable
private fun BandTile(index: Int, band: EqBand, selected: Boolean, eqEnabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val live = eqEnabled && band.enabled
  val number = index + 1
  val gainText = if (band.type.usesGain) "${formatDb(band.gainDb)} dB" else stringResource(R.string.audio_band_no_gain, filterTypeLabel(band.type))
  val tileState = if (live) gainText else gainText + ", " + stringResource(R.string.audio_band_off)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .pressScale(source)
      .clip(Grid.r12)
      .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.audio_band_select, number), onClick = onClick)
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
  val label = stringResource(R.string.audio_band_add)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .pressScale(source)
      .clip(Grid.r12)
      // The dashed outline is drawn inside the box, so it never grows past its neighbours.
      .drawBehind {
        val w = 1.dp.toPx()
        drawRoundRect(
          color = edge,
          topLeft = Offset(w / 2f, w / 2f),
          size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
          cornerRadius = CornerRadius(12.dp.toPx()),
          style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())))
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
 * Everything one parametric band has: power and delete, the filter type as a segmented control, and
 * steppers for frequency, gain and Q. A tap on a value opens number entry; a double tap restores the default.
 */
@Composable
private fun BandEditor(index: Int, band: EqBand, eqEnabled: Boolean, onChange: ((EqBand) -> EqBand) -> Unit, onDelete: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val number = index + 1
  val live = eqEnabled && band.enabled
  Column(
    modifier = Modifier
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = Grid.margin)
      .padding(bottom = Grid.u24),
    verticalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.audio_band_title, number), style = EqType.Title, color = colors.onSurface, modifier = Modifier.weight(1f))
      BandPowerToggle(number, band.enabled, bandColor(index)) { on -> onChange { it.copy(enabled = on) } }
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
      label = stringResource(R.string.audio_freq_short),
      value = "${formatHz(band.freqHz)} Hz".replace("k Hz", " kHz"),
      enabled = live,
      onLess = { onChange { it.copy(freqHz = (it.freqHz / SIXTH_OCTAVE).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) } },
      onMore = { onChange { it.copy(freqHz = (it.freqHz * SIXTH_OCTAVE).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)) } },
      onReset = { onChange { it.copy(freqHz = 1000f) } },
      edit = freqEdit,
      tag = "band_freq_$index"
    )
    if (band.type.usesGain) {
      StepperRow(
        label = stringResource(R.string.audio_gain),
        value = "${formatDb(band.gainDb)} dB",
        enabled = live,
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
    }
    StepperRow(
      label = "Q",
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
  tag: String
) {
  val colors = MaterialTheme.colorScheme
  var editing by remember { mutableStateOf(false) }
  if (editing && edit != null) NumberEntryFor(edit) { editing = false }
  Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = EqType.Body, color = colors.onSurface, maxLines = 1, modifier = Modifier.width(Grid.u96))
    StepButton(Icons.Rounded.Remove, stringResource(R.string.audio_stepper_less, label), enabled, onLess)
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
          contentDescription = "$label $value"
          onClick { if (enabled && edit != null) { editing = true; true } else false }
        }
        .testTag(tag),
      contentAlignment = Alignment.Center
    ) {
      Text(value, style = EqType.MonoStrong, color = if (enabled) colors.onSurface else colors.onSurfaceVariant, maxLines = 1)
    }
    StepButton(Icons.Rounded.Add, stringResource(R.string.audio_stepper_more, label), enabled, onMore)
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

/** The filter type as a segmented control: three shapes on the first row, two filters on the second. */
@Composable
private fun TypeSegments(index: Int, type: FilterType, enabled: Boolean, onPick: (FilterType) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val rows = listOf(
    listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF),
    listOf(FilterType.HIGH_PASS, FilterType.LOW_PASS)
  )
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(Grid.r12)
      .background(colors.surfaceContainer)
      .selectableGroup()
      .testTag("band_type_$index")
  ) {
    rows.forEach { row ->
      Row(modifier = Modifier.fillMaxWidth()) {
        row.forEachIndexed { position, option ->
          val chosen = option == type
          // The touch target is the full 48 dp row; the highlight is inset 6 inside it.
          Box(
            modifier = Modifier
              .weight(1f)
              .height(Grid.u48)
              .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = { onPick(option) })
              .testTag("band_type_${index}_${option.key}")
          ) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(
                  start = if (position == 0) Grid.u6 else Grid.u6 / 2,
                  end = if (position == row.size - 1) Grid.u6 else Grid.u6 / 2,
                  top = Grid.u6,
                  bottom = Grid.u6
                )
                .clip(Grid.r6)
                .background(if (chosen) colors.primary.copy(alpha = 0.14f) else Color.Transparent),
              contentAlignment = Alignment.Center
            ) {
              Text(
                filterTypeLabel(option),
                style = if (chosen) EqType.LabelStrong else EqType.Label,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
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
