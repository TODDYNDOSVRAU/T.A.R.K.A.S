package com.example.ui.screens.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.example.R
import com.example.data.model.EqSettings
import com.example.ui.components.EqKnob
import com.example.ui.components.GridSwitch
import com.example.ui.components.TextAction
import com.example.ui.theme.Grid

/**
 * What used to sit around the faders, as 48 dp rows in a sheet: the preamp, bass and treble, the limiter,
 * the spectrum permission and the way into the advanced page. Nothing was removed from the equalizer.
 */
@Composable
internal fun EqSettingsSheet(
  settings: EqSettings,
  spectrumAllowed: Boolean,
  onAllowSpectrum: () -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onOpenAdvanced: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val preampRange = EqSettings.PREAMP_RANGE_DB
  Column(
    modifier = Modifier
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = Grid.margin)
      .padding(bottom = Grid.u24),
    verticalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(Grid.u48), contentAlignment = Alignment.CenterStart) {
      Text(stringResource(R.string.audio_settings_title), style = EqType.Title, color = colors.onSurface)
    }

    StepperRow(
      label = stringResource(R.string.audio_preamp_short),
      value = "${formatDb(settings.preampDb)} dB",
      enabled = settings.enabled,
      onLess = { onUpdateEq { it.copy(preampDb = (it.preampDb - 0.5f).coerceIn(-preampRange, preampRange)) } },
      onMore = { onUpdateEq { it.copy(preampDb = (it.preampDb + 0.5f).coerceIn(-preampRange, preampRange)) } },
      onReset = { onUpdateEq { it.copy(preampDb = 0f) } },
      edit = NumberEdit(
        title = stringResource(R.string.audio_preamp_long), unit = "dB", value = settings.preampDb,
        min = -preampRange, max = preampRange, step = 0.5f, decimals = 1, defaultValue = 0f,
        onApply = { v -> onUpdateEq { it.copy(preampDb = v) } }
      ),
      tag = "fader_preamp_strip"
    )
    ToneRow(stringResource(R.string.audio_tone_bass), settings.bassDb, settings.enabled, "knob_bass") { db -> onUpdateEq { it.copy(bassDb = db) } }
    ToneRow(stringResource(R.string.audio_tone_treble), settings.trebleDb, settings.enabled, "knob_treble") { db -> onUpdateEq { it.copy(trebleDb = db) } }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clip(Grid.r12)
        .toggleable(value = settings.limiterEnabled, role = Role.Switch, onValueChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } })
        .testTag("switch_limiter"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(stringResource(R.string.audio_switch_limiter), style = EqType.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
      // The row is the control (one touch target, one announcement); the switch only draws the state.
      GridSwitch(checked = settings.limiterEnabled)
    }

    if (!spectrumAllowed) {
      Row(modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.audio_spectrum_permission),
          style = EqType.Label,
          color = colors.onSurfaceVariant,
          modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Grid.u12))
        TextAction(stringResource(R.string.audio_spectrum_show), onAllowSpectrum, modifier = Modifier.testTag("audio_allow_spectrum"))
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clip(Grid.r12)
        .clickable(role = Role.Button, onClick = onOpenAdvanced)
        .testTag("eq_settings_advanced"),
      contentAlignment = Alignment.CenterStart
    ) {
      Text(stringResource(R.string.audio_menu_advanced), style = EqType.Body, color = colors.primary)
    }
  }
}

/** A tone control: name, minus, the knob, plus, and the value. The buttons are the alternative to turning the knob. */
@Composable
private fun ToneRow(label: String, db: Float, enabled: Boolean, tag: String, onChange: (Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val range = EqSettings.TONE_RANGE_DB
  Row(modifier = Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = EqType.Body, color = colors.onSurface, maxLines = 1, modifier = Modifier.width(Grid.u96))
    StepButton(Icons.Rounded.Remove, stringResource(R.string.audio_stepper_less, label), enabled) { onChange((db - 1f).coerceIn(-range, range)) }
    EqKnob(
      value = (db + range) / (2f * range),
      onChange = { v -> onChange(snap(v * 2f * range - range)) },
      label = label,
      stateText = "${formatDb(db)} dB",
      size = Grid.u48,
      bipolar = true,
      enabled = enabled,
      steps = (2 * range).toInt() - 1,
      modifier = Modifier.testTag(tag)
    )
    StepButton(Icons.Rounded.Add, stringResource(R.string.audio_stepper_more, label), enabled) { onChange((db + 1f).coerceIn(-range, range)) }
    Text(
      "${formatDb(db)} dB",
      style = EqType.MonoStrong,
      color = if (db == 0f || !enabled) colors.onSurfaceVariant else colors.primary,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.weight(1f)
    )
  }
}
