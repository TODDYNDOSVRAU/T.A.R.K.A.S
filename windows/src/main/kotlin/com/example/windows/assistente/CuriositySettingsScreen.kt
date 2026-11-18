package com.example.windows.assistente

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.components.GroupDivider
import com.example.ui.components.InsetGroup
import com.example.ui.components.RadioRow
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.ui.components.TonalButton
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.geral.readingColumn
import com.example.windows.res.*
import org.jetbrains.compose.resources.stringResource

/** Page of the Route.CuriositySettings route. [onBack] returns to the page that opened it. */
@Composable
fun CuriositySettingsScreen(state: AssistenteState, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val config by state.config.collectAsState()
  val speaking by state.speaking.collectAsState()
  val spokenText by state.spokenText.collectAsState()
  val colors = MaterialTheme.colorScheme

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).readingColumn()) {
    SubPageHeader(stringResource(Res.string.settings_curiosity_title), stringResource(Res.string.player_common_back), onBack)

    InsetGroup {
      SwitchRow(
        title = stringResource(Res.string.settings_curiosity_title),
        checked = config.enabled,
        onCheckedChange = { state.setConfig(config.copy(enabled = it)) },
        subtitle = stringResource(if (config.enabled) Res.string.settings_curiosity_state_on else Res.string.settings_curiosity_state_off),
        modifier = Modifier.testTag("curiosity_master")
      )
    }
    Footer(stringResource(Res.string.settings_curiosity_master_footer))

    if (config.enabled) {
      Header(stringResource(Res.string.settings_curiosity_when))
      InsetGroup {
        CuriosityTiming.entries.forEachIndexed { i, timing ->
          if (i > 0) GroupDivider()
          val (title, desc) = timingTexts(timing)
          RadioRow(stringResource(title), config.timing == timing, { state.setConfig(config.copy(timing = timing)) }, subtitle = stringResource(desc))
        }
      }

      Header(stringResource(Res.string.settings_curiosity_how))
      InsetGroup {
        CuriosityDisplayMode.entries.forEachIndexed { i, mode ->
          if (i > 0) GroupDivider()
          val both = mode == CuriosityDisplayMode.VOICE_AND_BANNER
          RadioRow(
            stringResource(if (both) Res.string.settings_curiosity_display_both else Res.string.settings_curiosity_display_voice),
            config.displayMode == mode,
            { state.setConfig(config.copy(displayMode = mode)) },
            subtitle = stringResource(if (both) Res.string.settings_curiosity_display_both_desc else Res.string.settings_curiosity_display_voice_desc)
          )
        }
      }

      Header(stringResource(Res.string.settings_curiosity_voice_style))
      InsetGroup {
        VoicePreset.entries.forEachIndexed { i, preset ->
          if (i > 0) GroupDivider()
          val selected = config.voicePreset == preset
          RadioRow(stringResource(presetName(preset)), selected, {
            state.setConfig(config.copy(voicePreset = preset, pitch = preset.pitch, speechRate = preset.speechRate))
          })
        }
      }
      Footer(stringResource(Res.string.settings_curiosity_voice_style_footer))

      Header(stringResource(Res.string.settings_curiosity_voice))
      InsetGroup {
        VoiceSlider(stringResource(Res.string.settings_curiosity_speech_rate), config.speechRate, "curiosity_rate") {
          state.setConfig(config.copy(speechRate = it))
        }
        GroupDivider()
        VoiceSlider(stringResource(Res.string.settings_curiosity_pitch), config.pitch, "curiosity_pitch") {
          state.setConfig(config.copy(pitch = it))
        }
      }
      Footer(stringResource(Res.string.settings_curiosity_voice_footer))

      Column(Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12)) {
        if (speaking) {
          spokenText?.let {
            Text(stringResource(Res.string.settings_curiosity_speaking_now), style = GridText.Small, color = colors.onSurfaceVariant)
            Text(it, style = GridText.Body, color = colors.onSurface, modifier = Modifier.padding(vertical = Grid.u6))
          }
          TonalButton(stringResource(Res.string.settings_curiosity_stop_demo), state::stopSpeech, Modifier.fillMaxWidth().testTag("curiosity_stop"))
        } else {
          TonalButton(stringResource(Res.string.settings_curiosity_play_demo), state::testVoice, Modifier.fillMaxWidth().testTag("curiosity_demo"))
        }
      }
    }
  }
}

@Composable
private fun VoiceSlider(label: String, value: Float, tag: String, onFinished: (Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  var live by remember(value) { mutableStateOf(value) }
  Column(Modifier.fillMaxWidth().padding(horizontal = Grid.inset, vertical = Grid.u6)) {
    Text("$label  ${"%.2f".format(live)}", style = GridText.Body, color = colors.onSurface)
    Slider(
      value = live.coerceIn(0.7f, 1.5f),
      onValueChange = { live = it },
      onValueChangeFinished = { onFinished(live) },
      valueRange = 0.7f..1.5f,
      steps = 7,
      modifier = Modifier.testTag(tag)
    )
  }
}

@Composable
private fun Header(text: String) {
  Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Grid.margin + Grid.inset, top = Grid.u24, bottom = Grid.u6))
}

@Composable
private fun Footer(text: String) {
  Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Grid.margin + Grid.inset, vertical = Grid.u6))
}

internal fun timingTexts(t: CuriosityTiming) = when (t) {
  CuriosityTiming.END_OF_TRACK -> Res.string.settings_curiosity_timing_end to Res.string.settings_curiosity_timing_end_desc
  CuriosityTiming.START_OF_TRACK -> Res.string.settings_curiosity_timing_start to Res.string.settings_curiosity_timing_start_desc
  CuriosityTiming.BOTH -> Res.string.settings_curiosity_timing_both to Res.string.settings_curiosity_timing_both_desc
  CuriosityTiming.MANUAL_ONLY -> Res.string.settings_curiosity_timing_manual to Res.string.settings_curiosity_timing_manual_desc
}

internal fun presetName(p: VoicePreset) = when (p) {
  VoicePreset.NATURAL -> Res.string.settings_curiosity_preset_natural
  VoicePreset.CALM -> Res.string.settings_curiosity_preset_calm
  VoicePreset.DYNAMIC -> Res.string.settings_curiosity_preset_dynamic
  VoicePreset.DEEP -> Res.string.settings_curiosity_preset_deep
  VoicePreset.UPBEAT -> Res.string.settings_curiosity_preset_upbeat
}
