package com.example.ui.screens.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import com.example.ui.components.EqFader
import com.example.ui.components.EqKnob
import com.example.ui.components.FilterShapeIcon
import com.example.ui.components.IconAction
import com.example.ui.components.TonalButton
import com.example.ui.components.highlightClickable
import com.example.ui.theme.AppText
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/** How the bands are shown: one fader each, or one card each with every parameter. */
internal enum class EqMode { GRAPHIC, PARAMETRIC }

/** Height of the band strip, the same in both modes so switching never moves what is below. */
internal val STRIP_HEIGHT = 300.dp

/** A fader column: the 48 dp touch target, which also fits a "+12.5" readout. */
private val FADER_WIDTH = Size.touch
private val FADER_GAP = Space.xs
private val CARD_WIDTH = 208.dp
private val CARD_GAP = Space.s

/** Numbers that change while a finger moves keep their width, so neighbours never shift. */
private val Tabular = AppText.Caption2.copy(fontFeatureSettings = "tnum")

/** A screen reader's adjust gesture moves gains in 1 dB steps across ±15 dB. */
private const val GAIN_STEPS = 29

/**
 * The band strip: the preamp fixed at the leading edge, then the bands in a horizontal scroll that
 * starts and ends inside the page margin, so the first and last bands are fully visible. The two
 * modes cross-fade.
 */
@Composable
internal fun BandStrip(
  settings: EqSettings,
  mode: EqMode,
  selectedBand: Int,
  graphicView: StripView,
  paramView: StripView,
  onSelectBand: (Int) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  modifier: Modifier = Modifier
) {
  val reduced = rememberReducedMotion()
  Row(modifier = modifier.fillMaxWidth().height(STRIP_HEIGHT)) {
    Spacer(Modifier.width(Space.page))
    FaderColumn(
      value = settings.preampDb / EqSettings.PREAMP_RANGE_DB,
      enabled = settings.enabled,
      name = "Pré",
      readout = formatDb(settings.preampDb),
      spoken = "Pré-amplificação",
      stateText = "${formatDb(settings.preampDb)} dB",
      active = settings.preampDb != 0f,
      modifier = Modifier.testTag("fader_preamp_strip"),
      onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v * EqSettings.PREAMP_RANGE_DB)) } }
    )
    Spacer(Modifier.width(Space.s))
    Box(
      modifier = Modifier
        .padding(vertical = Space.l)
        .width(0.5.dp)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.outline)
    )
    AnimatedContent(
      targetState = mode,
      transitionSpec = {
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else fadeIn(tween(200, 60)) togetherWith fadeOut(tween(120))
      },
      label = "eqMode",
      modifier = Modifier.weight(1f).fillMaxHeight()
    ) { m ->
      when (m) {
        EqMode.GRAPHIC -> GraphicBands(
          settings = settings,
          view = graphicView,
          onBandGain = { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } }
        )
        EqMode.PARAMETRIC -> ParametricBands(
          settings = settings,
          selected = selectedBand,
          view = paramView,
          onSelect = onSelectBand,
          onUpdateBand = onUpdateBand,
          onUpdateEq = onUpdateEq
        )
      }
    }
  }
}

/** The scrolling part of the strip; reports its window to [view] for the graph. */
@Composable
private fun ScrollingBands(
  view: StripView,
  gap: androidx.compose.ui.unit.Dp,
  content: @Composable () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { view.viewport = it.width }
      .horizontalScroll(view.scroll)
      // Padding inside the scroll: the first band starts after the divider and the last one ends at
      // the page margin, instead of being cut at the screen edge.
      .padding(start = Space.s, end = Space.page),
    horizontalArrangement = Arrangement.spacedBy(gap)
  ) { content() }
}

/** The graphic mode: one fader per band, gain only. */
@Composable
private fun GraphicBands(settings: EqSettings, view: StripView, onBandGain: (Int, Float) -> Unit) {
  ScrollingBands(view, FADER_GAP) {
    settings.bands.forEachIndexed { i, band ->
      val usesGain = band.type.usesGain
      FaderColumn(
        value = band.gainDb / EqBand.MAX_GAIN,
        enabled = settings.enabled && usesGain,
        name = formatHz(band.freqHz),
        readout = if (usesGain) formatDb(band.gainDb) else "—",
        spoken = "Banda ${spokenHz(band.freqHz)}",
        stateText = if (usesGain) "${formatDb(band.gainDb)} dB" else "${band.type.label}, sem ganho",
        active = usesGain && band.gainDb != 0f,
        modifier = Modifier.testTag("vertical_fader_band_$i"),
        onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) }
      )
    }
  }
}

/**
 * A fader with its name (Caption1) and value (Caption2, tabular) underneath. While a finger holds it
 * the value grows, so it can be read past the thumb; the growth is a draw-time scale, so it never
 * re-measures the text.
 */
@Composable
private fun FaderColumn(
  value: Float,
  enabled: Boolean,
  name: String,
  readout: String,
  spoken: String,
  stateText: String,
  active: Boolean,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  var held by remember { mutableStateOf(false) }
  val grow by animateFloatAsState(
    targetValue = if (held) 1.3f else 1f,
    animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 800f),
    label = "readout"
  )
  Column(
    modifier = modifier.width(FADER_WIDTH).fillMaxHeight().padding(vertical = Space.s),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    EqFader(
      value = value,
      onChange = onChange,
      label = spoken,
      stateText = stateText,
      enabled = enabled,
      steps = GAIN_STEPS,
      onHeldChange = { held = it },
      modifier = Modifier.weight(1f).fillMaxWidth()
    )
    Spacer(Modifier.height(Space.s))
    Text(
      name,
      style = AppText.Caption1,
      color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
      maxLines = 1,
      textAlign = TextAlign.Center
    )
    Text(
      readout,
      style = Tabular,
      color = if (active || held) colors.primary else colors.onSurfaceVariant,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.graphicsLayer {
        scaleX = grow
        scaleY = grow
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
      }
    )
  }
}

/**
 * Stable identities for the parametric bands. The model's bands have no id, and keying the cards by
 * index handed a deleted card's state (knob drag, colour animation) to the band that slid into its
 * place. Each band gets an id when it appears; deleting removes exactly that id.
 */
private class BandKeys {
  private var next = 0L
  private val ids = ArrayList<Long>()

  /** Matches the number of ids to [size]: new bands at the end get new ids, removed ones drop theirs. */
  fun sync(size: Int): List<Long> {
    while (ids.size < size) ids += next++
    while (ids.size > size) ids.removeAt(ids.lastIndex)
    return ids
  }

  fun removeAt(i: Int) {
    if (i in ids.indices) ids.removeAt(i)
  }
}

@Composable
private fun ParametricBands(
  settings: EqSettings,
  selected: Int,
  view: StripView,
  onSelect: (Int) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  val density = LocalDensity.current
  val keys = remember { BandKeys() }
  val ids = keys.sync(settings.paramBands.size)

  // Selecting a band brings its card to the middle of the strip.
  LaunchedEffect(selected) {
    if (selected !in settings.paramBands.indices || view.viewport == 0) return@LaunchedEffect
    val (step, card, start) = with(density) { Triple((CARD_WIDTH + CARD_GAP).toPx(), CARD_WIDTH.toPx(), Space.s.toPx()) }
    val target = (start + selected * step - (view.viewport - card) / 2f).roundToInt().coerceIn(0, view.scroll.maxValue)
    if (abs(target - view.scroll.value) > 4) view.scroll.animateScrollTo(target, spring(dampingRatio = 1f, stiffness = 300f))
  }

  ScrollingBands(view, CARD_GAP) {
    settings.paramBands.forEachIndexed { i, band ->
      key(ids[i]) {
        BandCard(
          index = i,
          band = band,
          selected = i == selected,
          eqEnabled = settings.enabled,
          onSelect = { onSelect(i) },
          onChange = { t -> onSelect(i); onUpdateBand(i, t) },
          onDelete = {
            keys.removeAt(i)
            onUpdateEq { s -> s.copy(paramBands = s.paramBands.filterIndexed { j, _ -> j != i }) }
            onSelect((i - 1).coerceAtLeast(0))
          }
        )
      }
    }
    Box(
      modifier = Modifier.fillMaxHeight(),
      contentAlignment = Alignment.Center
    ) {
      TonalButton(
        text = "Adicionar banda",
        icon = Icons.Rounded.Add,
        onClick = {
          onUpdateEq { s -> s.copy(paramBands = s.paramBands + EqBand(freqHz = 1000f, q = 1.4f)) }
          onSelect(settings.paramBands.size)
        },
        modifier = Modifier.testTag("eq_add_band")
      )
    }
  }
}

/**
 * A parametric band as a card: gain fader on the left; filter shape, Q and frequency on the right.
 * Drawn in theme colours so its text keeps its contrast on the light and the dark theme; the selected
 * card takes the accent wash, as a selected chip does.
 */
@Composable
private fun BandCard(
  index: Int,
  band: EqBand,
  selected: Boolean,
  eqEnabled: Boolean,
  onSelect: () -> Unit,
  onChange: ((EqBand) -> EqBand) -> Unit,
  onDelete: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  val fill by animateColorAsState(
    targetValue = if (selected) colors.primary.copy(alpha = 0.14f).compositeOver(colors.surface) else colors.surface,
    animationSpec = if (reduced) snap() else tween(200),
    label = "bandFill"
  )
  val live = eqEnabled && band.enabled
  val number = index + 1

  Column(
    modifier = Modifier
      .width(CARD_WIDTH)
      .fillMaxHeight()
      .clip(Shapes.m)
      .background(fill)
      .clickable(
        interactionSource = null,
        indication = null,
        onClickLabel = "Selecionar banda $number",
        onClick = onSelect
      )
      .padding(start = Space.m, end = Space.xs, bottom = Space.s)
      .testTag("band_card_$index")
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Text(
        "Banda $number",
        style = AppText.Headline,
        color = colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      BandPowerToggle(number, band.enabled) { on -> onChange { it.copy(enabled = on) } }
      IconAction(
        icon = Icons.Rounded.Delete,
        contentDescription = "Excluir banda $number",
        onClick = onDelete,
        tint = colors.onSurfaceVariant,
        size = Size.touch
      )
    }
    Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(end = Space.s)) {
      Column(modifier = Modifier.width(FADER_WIDTH).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          onChange = { v -> onChange { it.copy(gainDb = snap(v * EqBand.MAX_GAIN)) } },
          label = "Ganho da banda $number",
          stateText = if (band.type.usesGain) "${formatDb(band.gainDb)} dB" else "${band.type.label}, sem ganho",
          enabled = live && band.type.usesGain,
          steps = GAIN_STEPS,
          modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Readout("Ganho", if (band.type.usesGain) formatDb(band.gainDb) else "—", active = band.type.usesGain && band.gainDb != 0f)
      }
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        FilterTypeButton(index, band.type, enabled = live) {
          onChange { b -> b.copy(type = FilterType.entries[(b.type.ordinal + 1) % FilterType.entries.size]) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          EqKnob(
            value = qToKnob(band.q),
            onChange = { v -> onChange { it.copy(q = knobToQ(v)) } },
            label = "Q da banda $number",
            stateText = formatQ(band.q),
            defaultValue = qToKnob(EqBand.DEFAULT_Q),
            enabled = live,
            size = 56.dp
          )
          Readout("Q", formatQ(band.q), active = false)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          EqKnob(
            value = freqToX(band.freqHz),
            onChange = { v -> onChange { it.copy(freqHz = xToFreq(v)) } },
            label = "Frequência da banda $number",
            stateText = spokenHz(band.freqHz),
            defaultValue = freqToX(1000f),
            enabled = live,
            size = 56.dp
          )
          Readout("Freq", formatHz(band.freqHz), active = false)
        }
      }
    }
  }
}

/** A control's name and value under it. The value takes the accent only when it is not neutral. */
@Composable
private fun Readout(name: String, value: String, active: Boolean) {
  val colors = MaterialTheme.colorScheme
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = Space.xs)) {
    Text(name, style = AppText.Caption1, color = colors.onSurfaceVariant, maxLines = 1)
    Text(value, style = Tabular, color = if (active) colors.primary else colors.onSurface, maxLines = 1)
  }
}

/** Turns one band on or off: a switch for screen readers, a power icon lit in the accent when on. */
@Composable
private fun BandPowerToggle(number: Int, on: Boolean, onToggle: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = Modifier
      .size(Size.touch)
      .clip(Shapes.pill)
      .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
      .semantics { contentDescription = "Banda $number" },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      Icons.Rounded.PowerSettingsNew,
      contentDescription = null,
      tint = if (on) colors.primary else colors.onSurfaceVariant,
      modifier = Modifier.size(Size.iconSmall)
    )
  }
}

/** The band's filter shape; a tap steps to the next type. */
@Composable
private fun FilterTypeButton(index: Int, type: FilterType, enabled: Boolean, onNext: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Size.touch)
      .clip(Shapes.s)
      .background(colors.surfaceContainer)
      .highlightClickable(enabled = enabled, onClickLabel = "Trocar o tipo de filtro", onClick = onNext)
      .semantics(mergeDescendants = true) { contentDescription = "Filtro ${type.label}" }
      .padding(horizontal = Space.s)
      .testTag("band_type_$index"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    FilterShapeIcon(type, color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
  }
}
