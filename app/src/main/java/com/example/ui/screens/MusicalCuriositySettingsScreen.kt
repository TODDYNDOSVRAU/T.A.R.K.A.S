package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
internal data class TimingChoice(val timing: CuriosityTiming, val title: String, val short: String, val description: String)

/**
 * The four timings, shared by this page and the first-run setup so both offer the same choices in
 * the same words. The default comes first.
 */
internal val curiosityTimingChoices = listOf(
  TimingChoice(CuriosityTiming.END_OF_TRACK, "Ao final da música", "No final", "Antes da próxima faixa. É o padrão."),
  TimingChoice(CuriosityTiming.START_OF_TRACK, "No início da música", "No início", "A faixa começa depois da fala."),
  TimingChoice(CuriosityTiming.BOTH, "No início e no final", "Início e final", "Uma introdução curta e um fato ao final."),
  TimingChoice(CuriosityTiming.MANUAL_ONLY, "Só quando eu pedir", "Só ao pedir", "Toque na lâmpada do player para ouvir.")
)

/** The timing choices as radio rows; put inside an [InsetGroup]. */
@Composable
internal fun CuriosityTimingRows(timing: CuriosityTiming, onTiming: (CuriosityTiming) -> Unit, tagPrefix: String) {
  Column(Modifier.selectableGroup()) {
    curiosityTimingChoices.forEachIndexed { i, choice ->
      if (i > 0) GroupDivider()
      RadioRow(
        title = choice.title,
        subtitle = choice.description,
        selected = timing == choice.timing,
        onClick = { onTiming(choice.timing) },
        modifier = Modifier.testTag("$tagPrefix${choice.timing.name}")
      )
    }
  }
}

private val displayChoices = listOf(
  Triple(CuriosityDisplayMode.VOICE_AND_BANNER, "Voz e aviso no player", "Um aviso aparece no player durante a fala."),
  Triple(CuriosityDisplayMode.VOICE_ONLY, "Só voz", "Nada aparece na tela, só o áudio.")
)

/** Short names for the voice styles (the model's labels are long and mix "&" and "/"). */
private fun VoicePreset.title(): String = when (this) {
  VoicePreset.NATURAL -> "Natural"
  VoicePreset.CALM -> "Calma"
  VoicePreset.DYNAMIC -> "Locutor de rádio"
  VoicePreset.DEEP -> "Grave"
  VoicePreset.UPBEAT -> "Animada"
}

private fun pitchLabel(pitch: Float): String = when {
  pitch < 0.9f -> "Grave, %.2f×".format(pitch)
  pitch > 1.1f -> "Agudo, %.2f×".format(pitch)
  else -> "Natural, %.2f×".format(pitch)
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
  backLabel: String = "Ajustes"
) {
  BackHandler(onBack = onBack)
  val colors = MaterialTheme.colorScheme

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item {
      SubPageHeader(
        title = "Curiosidades musicais",
        backLabel = backLabel,
        onBack = onBack,
        modifier = Modifier.testTag("btn_back_curiosity_settings")
      )
    }

    settingsGroup(
      header = null,
      footer = "Fatos curtos sobre a faixa, falados em voz alta. Só para faixas com uma curiosidade de fonte verificada.",
      tag = "card_master_curiosity_switch"
    ) {
      SwitchRow(
        title = "Curiosidades faladas",
        checked = config.enabled,
        onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
        modifier = Modifier.testTag("switch_curiosity_enabled")
      )
    }

    settingsGroup("Quando falar") {
      CuriosityTimingRows(config.timing, { onConfigChange(config.copy(timing = it)) }, tagPrefix = "timing_option_")
    }

    settingsGroup("Como aparece") {
      Column(Modifier.selectableGroup()) {
        displayChoices.forEachIndexed { i, (mode, title, description) ->
          if (i > 0) GroupDivider()
          RadioRow(
            title = title,
            subtitle = description,
            selected = config.displayMode == mode,
            onClick = { onConfigChange(config.copy(displayMode = mode)) },
            modifier = Modifier.testTag("display_mode_${mode.name}")
          )
        }
      }
    }

    settingsGroup("Estilo da voz", footer = "Cada estilo ajusta a velocidade e o tom. Você pode refinar os dois abaixo.") {
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
      "Voz",
      footer = "A narração usa uma ou duas frases da curiosidade, para não interromper a música. Faixas sem curiosidade verificada tocam sem narração."
    ) {
      VoiceSlider(
        title = "Velocidade da fala",
        value = config.speechRate,
        label = { "%.2f×".format(it) },
        onValueFinished = { onConfigChange(config.copy(speechRate = it)) },
        modifier = Modifier.testTag("slider_speech_rate")
      )
      GroupDivider()
      VoiceSlider(
        title = "Tom da voz",
        value = config.pitch,
        label = ::pitchLabel,
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
            Text("Falando agora", style = AppText.Footnote, color = colors.primary)
            Text(currentSpokenText, style = AppText.Subhead, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
          }
          IconAction(icon = Icons.Rounded.Stop, contentDescription = "Parar a demonstração", onClick = onStopVoice, tint = colors.primary)
        }
      }
    }

    item {
      TonalButton(
        text = if (isSpeaking) "Parar demonstração" else "Ouvir demonstração da voz",
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
