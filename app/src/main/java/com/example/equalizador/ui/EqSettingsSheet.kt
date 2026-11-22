package com.example.ui.screens.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import com.example.data.model.CrossfeedPreset
import com.example.data.model.CrossfeedSettings
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

    CrossfeedSection(
      crossfeed = settings.crossfeed,
      enabled = settings.enabled,
      onChange = { next -> onUpdateEq { it.copy(crossfeed = next) } }
    )

    RtaSection(
      mode = settings.rtaMode,
      onChange = { next -> onUpdateEq { it.copy(rtaMode = next) } }
    )

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
internal fun ToneRow(label: String, db: Float, enabled: Boolean, tag: String, onChange: (Float) -> Unit) {
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

/** Purist binaural crossfeed (BS2B / Chu Moy / Jan Meier / Custom) on the 48 dp T.A.L. grid. */
@Composable
internal fun CrossfeedSection(
  crossfeed: CrossfeedSettings,
  enabled: Boolean,
  onChange: (CrossfeedSettings) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(Grid.u6)) {
    Box(modifier = Modifier.fillMaxWidth().height(Grid.u24), contentAlignment = Alignment.CenterStart) {
      Text(stringResource(R.string.audio_crossfeed_title), style = EqType.LabelStrong, color = colors.onSurfaceVariant)
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .horizontalScroll(rememberScrollState())
        .selectableGroup()
        .testTag("crossfeed_presets"),
      horizontalArrangement = Arrangement.spacedBy(Grid.u6),
      verticalAlignment = Alignment.CenterVertically
    ) {
      CrossfeedPreset.entries.forEach { preset ->
        val selected = crossfeed.preset == preset
        Box(
          modifier = Modifier
            .height(Grid.u48)
            .clip(Grid.r12)
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
            .selectable(
              selected = selected,
              enabled = enabled,
              role = Role.RadioButton,
              onClick = { onChange(crossfeed.copy(preset = preset)) }
            )
            .padding(horizontal = Grid.u12)
            .testTag("crossfeed_${preset.key}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = crossfeedPresetLabel(preset),
            style = if (selected) EqType.LabelStrong else EqType.Label,
            color = when {
              !enabled -> colors.onSurface.copy(alpha = 0.38f)
              selected -> colors.primary
              else -> colors.onSurface
            },
            maxLines = 1
          )
        }
      }
    }
    if (crossfeed.preset == CrossfeedPreset.CUSTOM) {
      StepperRow(
        label = stringResource(R.string.audio_crossfeed_cutoff),
        value = formatHz(crossfeed.cutoffHz),
        enabled = enabled,
        onLess = { onChange(crossfeed.copy(cutoffHz = (crossfeed.cutoffHz - 50f).coerceIn(CrossfeedSettings.MIN_CUTOFF_HZ, CrossfeedSettings.MAX_CUTOFF_HZ))) },
        onMore = { onChange(crossfeed.copy(cutoffHz = (crossfeed.cutoffHz + 50f).coerceIn(CrossfeedSettings.MIN_CUTOFF_HZ, CrossfeedSettings.MAX_CUTOFF_HZ))) },
        onReset = { onChange(crossfeed.copy(cutoffHz = 700f)) },
        edit = NumberEdit(
          title = stringResource(R.string.audio_crossfeed_cutoff), unit = "Hz", value = crossfeed.cutoffHz,
          min = CrossfeedSettings.MIN_CUTOFF_HZ, max = CrossfeedSettings.MAX_CUTOFF_HZ, step = 50f, decimals = 0, defaultValue = 700f,
          onApply = { v -> onChange(crossfeed.copy(cutoffHz = v)) }
        ),
        tag = "crossfeed_cutoff"
      )
      StepperRow(
        label = stringResource(R.string.audio_crossfeed_feed),
        value = "${formatNumber(crossfeed.feedDb, 1)} dB",
        enabled = enabled,
        onLess = { onChange(crossfeed.copy(feedDb = (crossfeed.feedDb - 0.5f).coerceIn(CrossfeedSettings.MIN_FEED_DB, CrossfeedSettings.MAX_FEED_DB))) },
        onMore = { onChange(crossfeed.copy(feedDb = (crossfeed.feedDb + 0.5f).coerceIn(CrossfeedSettings.MIN_FEED_DB, CrossfeedSettings.MAX_FEED_DB))) },
        onReset = { onChange(crossfeed.copy(feedDb = 4.5f)) },
        edit = NumberEdit(
          title = stringResource(R.string.audio_crossfeed_feed), unit = "dB", value = crossfeed.feedDb,
          min = CrossfeedSettings.MIN_FEED_DB, max = CrossfeedSettings.MAX_FEED_DB, step = 0.5f, decimals = 1, defaultValue = 4.5f,
          onApply = { v -> onChange(crossfeed.copy(feedDb = v)) }
        ),
        tag = "crossfeed_feed"
      )
    }
  }
}

/** Selects which internal DSP FFT tap (Post-EQ, Pre-EQ, Both, or Off) draws behind the EQ curve. */
@Composable
internal fun RtaSection(
  mode: com.example.data.model.RtaMode,
  onChange: (com.example.data.model.RtaMode) -> Unit
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(Grid.u6)) {
    Box(modifier = Modifier.fillMaxWidth().height(Grid.u24), contentAlignment = Alignment.CenterStart) {
      Text(stringResource(R.string.audio_rta_title), style = EqType.LabelStrong, color = colors.onSurfaceVariant)
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .horizontalScroll(rememberScrollState())
        .selectableGroup()
        .testTag("rta_modes"),
      horizontalArrangement = Arrangement.spacedBy(Grid.u6),
      verticalAlignment = Alignment.CenterVertically
    ) {
      com.example.data.model.RtaMode.entries.forEach { option ->
        val selected = mode == option
        Box(
          modifier = Modifier
            .height(Grid.u48)
            .clip(Grid.r12)
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
            .selectable(
              selected = selected,
              role = Role.RadioButton,
              onClick = { onChange(option) }
            )
            .padding(horizontal = Grid.u12)
            .testTag("rta_${option.key}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = rtaModeLabel(option),
            style = if (selected) EqType.LabelStrong else EqType.Label,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1
          )
        }
      }
    }
  }
}
