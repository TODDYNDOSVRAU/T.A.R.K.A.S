package com.example.windows.equalizador

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqSettings
import com.example.ui.components.GridSwitch
import com.example.ui.theme.Grid
import com.example.ui.theme.studioCard
import com.example.windows.res.Res
import com.example.windows.res.ad_rack_output_protection
import com.example.windows.res.ad_rack_tone_gain
import com.example.windows.res.audio_menu_advanced
import com.example.windows.res.audio_preamp_long
import com.example.windows.res.audio_preamp_short
import com.example.windows.res.audio_stepper_less
import com.example.windows.res.audio_stepper_more
import com.example.windows.res.audio_switch_limiter
import com.example.windows.res.audio_tone_bass
import com.example.windows.res.audio_tone_treble
import com.example.windows.res.tr
import org.jetbrains.compose.resources.stringResource

/** Width of the right rack on a wide window: two cards, 360 dp (docs/WINDOWS-NOVA-UI.md §2). */
internal val RACK_WIDTH = Grid.u384 - Grid.u24

/** Wide enough for the rack beside the curve, and tall enough for the top bar, the curve and a band row. */
internal val TABLET_MIN_WIDTH = Grid.u384 * 2 + Grid.u48 + Grid.u24 // 840
internal val TABLET_MIN_HEIGHT = Grid.u384 + Grid.u96

/**
 * The two cards of the right rack. Where the window is tall enough for both, each card stretches to half
 * of the column (its rows spread evenly), so the rack ends on the same line as the faders beside it;
 * where it is not, the cards keep their own height and the column scrolls.
 */
@Composable
internal fun TabletToneRack(
  settings: EqSettings,
  capabilities: AudioCapabilities,
  compare: CompareState,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onOpenAdvanced: () -> Unit,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(modifier = modifier.width(RACK_WIDTH).fillMaxHeight()) {
    val half = (maxHeight - Grid.u12) / 2
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("eq_tablet_rack"),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      ToneGainRackBlock(settings, onUpdateEq, Modifier.heightIn(min = half))
      OutputProtectionRackBlock(settings, capabilities, compare, onUpdateEq, onOpenAdvanced, Modifier.heightIn(min = half))
    }
  }
}

/**
 * A rack card: its title at the top, then its rows. Extra height is shared evenly around the rows (each
 * [RackGap] takes a share, and none when the card is only as tall as its rows), never left under the last one.
 */
@Composable
private fun RackCard(title: String, tag: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier = modifier.fillMaxWidth().studioCard().padding(Grid.u12).testTag(tag)) {
    Text(title, style = EqType.LabelStrong, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    RackGap()
    content()
  }
}

/** The stretchable space between the rows of a rack card. */
@Composable
private fun ColumnScope.RackGap() = Spacer(Modifier.weight(1f))

/** Tom e Ganho: the preamp, bass and treble that used to sit in the settings sheet. */
@Composable
internal fun ToneGainRackBlock(settings: EqSettings, onUpdateEq: ((EqSettings) -> EqSettings) -> Unit, modifier: Modifier = Modifier) {
  val range = EqSettings.PREAMP_RANGE_DB
  val tone = EqSettings.TONE_RANGE_DB
  RackCard(stringResource(Res.string.ad_rack_tone_gain), "eq_rack_tone_gain", modifier) {
    RackKnobRow(
      label = stringResource(Res.string.audio_preamp_short),
      spoken = stringResource(Res.string.audio_preamp_long),
      db = settings.preampDb,
      range = range,
      stepDb = 0.5f,
      enabled = settings.enabled,
      knobTag = "knob_preamp",
      valueTag = "fader_preamp_strip",
      onChange = { v -> onUpdateEq { it.copy(preampDb = v) } }
    )
    RackGap()
    RackKnobRow(
      label = stringResource(Res.string.audio_tone_bass), spoken = stringResource(Res.string.audio_tone_bass), db = settings.bassDb,
      range = tone, stepDb = 1f, enabled = settings.enabled, knobTag = "knob_bass", valueTag = "value_bass",
      onChange = { v -> onUpdateEq { it.copy(bassDb = v) } }
    )
    RackGap()
    RackKnobRow(
      label = stringResource(Res.string.audio_tone_treble), spoken = stringResource(Res.string.audio_tone_treble), db = settings.trebleDb,
      range = tone, stepDb = 1f, enabled = settings.enabled, knobTag = "knob_treble", valueTag = "value_treble",
      onChange = { v -> onUpdateEq { it.copy(trebleDb = v) } }
    )
    RackGap()
  }
}

/**
 * One gain control of the rack, the same row for the preamp, bass and treble: name, minus, the knob, plus
 * and the value. A tap on the value types it, a double tap on the value or the knob returns it to 0 dB,
 * the wheel and the arrow keys step the knob. (The older [ToneRow] stays for the phone sheet.)
 */
@Composable
private fun RackKnobRow(
  label: String,
  spoken: String,
  db: Float,
  range: Float,
  stepDb: Float,
  enabled: Boolean,
  knobTag: String,
  valueTag: String,
  onChange: (Float) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  var editing by remember { mutableStateOf(false) }
  if (editing) {
    NumberEntryFor(
      NumberEdit(
        title = spoken, unit = "dB", value = db, min = -range, max = range, step = stepDb, decimals = 1, defaultValue = 0f,
        onApply = onChange
      )
    ) { editing = false }
  }
  val valueText = "${formatDb(db)} dB"
  Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = EqType.Body, color = colors.onSurface, maxLines = 1, modifier = Modifier.width(Grid.u96))
    StepButton(Icons.Rounded.Remove, tr(Res.string.audio_stepper_less, spoken), enabled) { onChange((db - stepDb).coerceIn(-range, range)) }
    EqKnob(
      value = (db + range) / (2f * range),
      onChange = { v ->
        onChange(snap(v * 2f * range - range).coerceIn(-range, range))
      },
      label = spoken,
      stateText = valueText,
      size = Grid.u48,
      bipolar = true,
      enabled = enabled,
      steps = (2f * range / stepDb).toInt() - 1,
      modifier = Modifier.testTag(knobTag)
    )
    StepButton(Icons.Rounded.Add, tr(Res.string.audio_stepper_more, spoken), enabled) { onChange((db + stepDb).coerceIn(-range, range)) }
    Box(
      modifier = Modifier
        .weight(1f)
        .height(Grid.u48)
        .pointerInput(enabled, db) {
          if (!enabled) return@pointerInput
          detectTapGestures(onTap = { editing = true }, onDoubleTap = { onChange(0f) })
        }
        .semantics {
          role = Role.Button
          contentDescription = "$spoken $valueText"
          onClick { if (enabled) { editing = true; true } else false }
        }
        .testTag(valueTag),
      contentAlignment = Alignment.Center
    ) {
      Text(
        valueText,
        style = EqType.MonoStrong,
        color = if (db == 0f || !enabled) colors.onSurfaceVariant else colors.primary,
        maxLines = 1
      )
    }
  }
}

/** Saída e Proteção: the limiter, the headroom bar with what the output stage is doing, and the way into Advanced. */
@Composable
internal fun OutputProtectionRackBlock(
  settings: EqSettings,
  capabilities: AudioCapabilities,
  compare: CompareState,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onOpenAdvanced: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  RackCard(stringResource(Res.string.ad_rack_output_protection), "eq_rack_output_protection", modifier) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clip(Grid.r12)
        .toggleable(value = settings.limiterEnabled, role = Role.Switch, onValueChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } })
        .testTag("switch_limiter"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(stringResource(Res.string.audio_switch_limiter), style = EqType.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
      GridSwitch(checked = settings.limiterEnabled)
    }
    RackGap()
    PathBar(settings, capabilities, compare, showCompare = false)
    RackGap()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clip(Grid.r12)
        .clickable(role = Role.Button, onClick = onOpenAdvanced)
        .testTag("eq_settings_advanced"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(stringResource(Res.string.audio_menu_advanced), style = EqType.Body, color = colors.primary, maxLines = 1, modifier = Modifier.weight(1f))
      Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Grid.icon))
    }
    RackGap()
  }
}
