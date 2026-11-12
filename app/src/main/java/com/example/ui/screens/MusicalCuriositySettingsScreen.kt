package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.model.CuriosityConfig
import com.example.data.model.CuriosityDisplayMode
import com.example.data.model.CuriosityTiming
import com.example.data.model.VoicePreset
import com.example.ui.components.GroupDivider
import com.example.ui.components.IconAction
import com.example.ui.components.RadioRow
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.ui.components.TonalButton
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/** One "when to speak" option: [short] is the value shown on the Ajustes row. */
internal data class TimingChoice(
  val timing: CuriosityTiming,
  @StringRes val title: Int,
  @StringRes val short: Int,
  @StringRes val description: Int
)

/**
 * The four timings, shared by this page and the first-run setup so both offer the same choices in
 * the same words. The default comes first.
 */
internal val curiosityTimingChoices = listOf(
  TimingChoice(
    CuriosityTiming.END_OF_TRACK,
    R.string.settings_curiosity_timing_end, R.string.settings_curiosity_timing_end_short, R.string.settings_curiosity_timing_end_desc
  ),
  TimingChoice(
    CuriosityTiming.START_OF_TRACK,
    R.string.settings_curiosity_timing_start, R.string.settings_curiosity_timing_start_short, R.string.settings_curiosity_timing_start_desc
  ),
  TimingChoice(
    CuriosityTiming.BOTH,
    R.string.settings_curiosity_timing_both, R.string.settings_curiosity_timing_both_short, R.string.settings_curiosity_timing_both_desc
  ),
  TimingChoice(
    CuriosityTiming.MANUAL_ONLY,
    R.string.settings_curiosity_timing_manual, R.string.settings_curiosity_timing_manual_short, R.string.settings_curiosity_timing_manual_desc
  )
)

/** The timing choices as radio rows; put inside an [InsetGroup]. */
@Composable
internal fun CuriosityTimingRows(timing: CuriosityTiming, onTiming: (CuriosityTiming) -> Unit, tagPrefix: String) {
  Column(Modifier.selectableGroup()) {
    curiosityTimingChoices.forEachIndexed { i, choice ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = stringResource(choice.title),
        subtitle = stringResource(choice.description),
        selected = timing == choice.timing,
        onClick = { onTiming(choice.timing) },
        modifier = Modifier.testTag("$tagPrefix${choice.timing.name}")
      )
    }
  }
}

private val displayChoices = listOf(
  Triple(CuriosityDisplayMode.VOICE_AND_BANNER, R.string.settings_curiosity_display_both, R.string.settings_curiosity_display_both_desc),
  Triple(CuriosityDisplayMode.VOICE_ONLY, R.string.settings_curiosity_display_voice, R.string.settings_curiosity_display_voice_desc)
)

/** Short names for the voice styles (the model's labels are long and mix "&" and "/"). */
@Composable
private fun VoicePreset.title(): String = stringResource(
  when (this) {
    VoicePreset.NATURAL -> R.string.settings_curiosity_preset_natural
    VoicePreset.CALM -> R.string.settings_curiosity_preset_calm
    VoicePreset.DYNAMIC -> R.string.settings_curiosity_preset_dynamic
    VoicePreset.DEEP -> R.string.settings_curiosity_preset_deep
    VoicePreset.UPBEAT -> R.string.settings_curiosity_preset_upbeat
  }
)

private fun pitchLabel(context: Context, pitch: Float): String = when {
  pitch < 0.9f -> context.getString(R.string.settings_curiosity_pitch_low, pitch)
  pitch > 1.1f -> context.getString(R.string.settings_curiosity_pitch_high, pitch)
  else -> context.getString(R.string.settings_curiosity_pitch_natural, pitch)
}

/**
 * Curiosidades musicais, a sub-page of Ajustes: the switch, when to speak, how it shows, and the voice.
 * [backLabel] names the page back returns to.
 */
@Composable
fun MusicalCuriositySettingsScreen(
  config: CuriosityConfig,
  isSpeaking: Boolean,
  currentSpokenText: String?,
  speechStage: String? = null,
  onConfigChange: (CuriosityConfig) -> Unit,
  onTestVoice: () -> Unit,
  onStopVoice: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  backLabel: String = stringResource(R.string.settings_back_settings)
) {
  BackHandler(onBack = onBack)
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item {
      SubPageHeader(
        title = context.getString(R.string.settings_curiosity_title),
        backLabel = backLabel,
        onBack = onBack,
        modifier = Modifier.testTag("btn_back_curiosity_settings")
      )
    }

    settingsGroup(
      header = null,
      footer = context.getString(R.string.settings_curiosity_master_footer),
      tag = "card_master_curiosity_switch"
    ) {
      SwitchRow(
        title = stringResource(R.string.settings_curiosity_spoken),
        checked = config.enabled,
        onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
        modifier = Modifier.testTag("switch_curiosity_enabled")
      )
    }

    settingsGroup(context.getString(R.string.settings_curiosity_when)) {
      CuriosityTimingRows(config.timing, { onConfigChange(config.copy(timing = it)) }, tagPrefix = "timing_option_")
    }

    settingsGroup(context.getString(R.string.settings_curiosity_how)) {
      Column(Modifier.selectableGroup()) {
        displayChoices.forEachIndexed { i, (mode, title, description) ->
          if (i > 0) GroupDivider()
          RadioRow(
            title = stringResource(title),
            subtitle = stringResource(description),
            selected = config.displayMode == mode,
            onClick = { onConfigChange(config.copy(displayMode = mode)) },
            modifier = Modifier.testTag("display_mode_${mode.name}")
          )
        }
      }
    }

    settingsGroup(
      context.getString(R.string.settings_curiosity_voice_style),
      footer = context.getString(R.string.settings_curiosity_voice_style_footer)
    ) {
      Column(Modifier.selectableGroup()) {
        VoicePreset.entries.forEachIndexed { i, preset ->
          if (i > 0) GroupDivider()
          RadioRow(
            title = preset.title(),
            selected = config.voicePreset == preset,
            onClick = { onConfigChange(config.copy(voicePreset = preset, pitch = preset.pitch, speechRate = preset.speechRate)) },
            modifier = Modifier.testTag("preset_${preset.name}")
          )
        }
      }
    }

    settingsGroup(
      context.getString(R.string.settings_curiosity_voice),
      footer = context.getString(R.string.settings_curiosity_voice_footer)
    ) {
      VoiceSlider(
        title = stringResource(R.string.settings_curiosity_speech_rate),
        value = config.speechRate,
        label = { "%.2f×".format(it) },
        onValueFinished = { onConfigChange(config.copy(speechRate = it)) },
        modifier = Modifier.testTag("slider_speech_rate")
      )
      GroupDivider()
      VoiceSlider(
        title = stringResource(R.string.settings_curiosity_pitch),
        value = config.pitch,
        label = { pitchLabel(context, it) },
        onValueFinished = { onConfigChange(config.copy(pitch = it)) },
        modifier = Modifier.testTag("slider_pitch")
      )
    }

    // Shown while the voice sample plays. A real narration is never written here: curiosities appear
    // only in the player and the notification.
    if (isSpeaking && speechStage == "test" && !currentSpokenText.isNullOrBlank()) {
      settingsGroup(header = null) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.row)
            .padding(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
          ) {
            Text(stringResource(R.string.settings_curiosity_speaking_now), style = AppText.Footnote, color = colors.primary)
            Text(currentSpokenText, style = AppText.Subhead, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
          }
          IconAction(icon = Icons.Rounded.Stop, contentDescription = stringResource(R.string.settings_curiosity_stop_demo_cd), onClick = onStopVoice, tint = colors.primary)
        }
      }
    }

    item {
      TonalButton(
        text = if (isSpeaking) context.getString(R.string.settings_curiosity_stop_demo) else context.getString(R.string.settings_curiosity_play_demo),
        icon = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
        onClick = { if (isSpeaking) onStopVoice() else onTestVoice() },
        modifier = Modifier
          .padding(start = Space.page, end = Space.page, top = Space.xl)
          .fillMaxWidth()
          .testTag("btn_test_curiosity_voice")
      )
    }
  }
}

/**
 * A slider with its title and current value. The value follows the finger in local state and is
 * saved once, when the finger lifts: saving on every frame wrote the preferences dozens of times per
 * drag. A new value from outside (choosing a style) resets the local one.
 */
@Composable
private fun VoiceSlider(
  title: String,
  value: Float,
  label: (Float) -> String,
  onValueFinished: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  var local by remember(value) { mutableFloatStateOf(value) }
  Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = AppText.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(Space.s))
      Text(label(local), style = AppText.Body, color = colors.onSurfaceVariant)
    }
    Slider(
      value = local,
      onValueChange = { local = it },
      onValueChangeFinished = { if (local != value) onValueFinished(local) },
      valueRange = 0.7f..1.5f,
      steps = 7,
      colors = SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.surfaceContainerHigh
      ),
      modifier = modifier.semantics {
        contentDescription = title
        stateDescription = label(local)
      }
    )
  }
}
