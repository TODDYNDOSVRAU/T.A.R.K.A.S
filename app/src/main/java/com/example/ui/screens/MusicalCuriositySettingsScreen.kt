package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CuriosityConfig
import com.example.data.model.CuriosityDisplayMode
import com.example.data.model.CuriosityTiming
import com.example.data.model.VoicePreset
import com.example.ui.components.TarkasSectionTitle

private data class TimingChoice(val timing: CuriosityTiming, val title: String, val description: String)

private data class DisplayChoice(val mode: CuriosityDisplayMode, val title: String, val description: String)

private val timingChoices = listOf(
  TimingChoice(
    CuriosityTiming.START_OF_TRACK,
    "No início da música",
    "A curiosidade é falada antes da música. O player espera o fim da fala para tocar a faixa."
  ),
  TimingChoice(
    CuriosityTiming.END_OF_TRACK,
    "Ao final da música (padrão)",
    "A curiosidade é falada quando a música termina, antes da próxima faixa."
  ),
  TimingChoice(
    CuriosityTiming.BOTH,
    "No início e no final",
    "Uma introdução curta antes de começar e uma curiosidade complementar ao final."
  ),
  TimingChoice(
    CuriosityTiming.MANUAL_ONLY,
    "Apenas sob comando",
    "O app não fala sozinho. Peça no Assistente quando quiser."
  )
)

private val displayChoices = listOf(
  DisplayChoice(
    CuriosityDisplayMode.VOICE_AND_BANNER,
    "Voz + faixa no player",
    "Mostra um aviso animado enquanto a narração toca."
  ),
  DisplayChoice(
    CuriosityDisplayMode.VOICE_ONLY,
    "Apenas voz",
    "Sem avisos na tela, só o áudio."
  ),
  DisplayChoice(
    CuriosityDisplayMode.VOICE_AND_NOTIFICATION,
    "Voz + notificação",
    "Atualiza a notificação de mídia com o fato falado."
  ),
  DisplayChoice(
    CuriosityDisplayMode.VOICE_AND_MODAL,
    "Voz + janela de detalhes",
    "Abre uma janela com as fontes e o contexto completo."
  )
)

@Composable
fun MusicalCuriositySettingsScreen(
  config: CuriosityConfig,
  isSpeaking: Boolean,
  currentSpokenText: String?,
  onConfigChange: (CuriosityConfig) -> Unit,
  onTestVoice: () -> Unit,
  onStopVoice: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler {
    onBack()
  }

  val colors = MaterialTheme.colorScheme
  val hairline = colors.outline.copy(alpha = 0.5f)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {

    // Back link and large title, like the Playlists page
    item {
      Column {
        Row(
          modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onBack)
            .padding(end = 12.dp)
            .testTag("btn_back_curiosity_settings"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Voltar",
            tint = colors.primary,
            modifier = Modifier.size(32.dp)
          )
          Text("Ajustes", fontSize = 20.sp, color = colors.primary)
        }
        Text(
          text = "Curiosidades musicais",
          fontWeight = FontWeight.Bold,
          fontSize = 34.sp,
          letterSpacing = (-0.5).sp,
          color = colors.onBackground,
          modifier = Modifier.padding(top = 4.dp)
        )
        Text(
          text = "Fatos curtos falados pelo assistente, só quando há fonte verificada.",
          fontSize = 15.sp,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
      }
    }

    // Master switch
    item {
      Column(modifier = Modifier.testTag("card_master_curiosity_switch")) {
        HorizontalDivider(color = hairline)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Curiosidades faladas", fontSize = 17.sp, color = colors.onSurface)
            Text(
              text = if (config.enabled) "Ativadas" else "Desativadas, sem narração automática",
              fontSize = 15.sp,
              color = colors.onSurfaceVariant
            )
          }
          Switch(
            checked = config.enabled,
            onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = colors.onPrimary,
              checkedTrackColor = colors.primary
            ),
            modifier = Modifier.testTag("switch_curiosity_enabled")
          )
        }
        HorizontalDivider(color = hairline)
      }
    }

    // Shown while the voice sample or a narration is playing
    if (isSpeaking && !currentSpokenText.isNullOrBlank()) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(colors.primary),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Falando agora", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.primary)
            Text(currentSpokenText, fontSize = 15.sp, color = colors.onSurfaceVariant, maxLines = 2)
          }
          IconButton(onClick = onStopVoice, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Stop, contentDescription = "Parar", tint = colors.primary)
          }
        }
      }
    }

    // When it appears
    item { TarkasSectionTitle("Quando falar", modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
    item {
      Column {
        HorizontalDivider(color = hairline)
        timingChoices.forEachIndexed { index, choice ->
          ChoiceRow(
            title = choice.title,
            description = choice.description,
            selected = config.timing == choice.timing,
            onClick = { onConfigChange(config.copy(timing = choice.timing)) },
            modifier = Modifier.testTag("timing_option_${choice.timing.name}")
          )
          HorizontalDivider(color = hairline)
        }
      }
    }

    // How it appears
    item { TarkasSectionTitle("Como aparece", modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
    item {
      Column {
        HorizontalDivider(color = hairline)
        displayChoices.forEach { choice ->
          ChoiceRow(
            title = choice.title,
            description = choice.description,
            selected = config.displayMode == choice.mode,
            onClick = { onConfigChange(config.copy(displayMode = choice.mode)) },
            modifier = Modifier.testTag("display_mode_${choice.mode.name}")
          )
          HorizontalDivider(color = hairline)
        }
      }
    }

    // Voice
    item { TarkasSectionTitle("Voz", modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
    item {
      Column {
        Text(
          text = "Estilo",
          fontSize = 15.sp,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          VoicePreset.values().forEach { preset ->
            val selected = config.voicePreset == preset
            Box(
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.primary else colors.surfaceVariant)
                .clickable {
                  onConfigChange(
                    config.copy(
                      voicePreset = preset,
                      pitch = preset.pitch,
                      speechRate = preset.speechRate
                    )
                  )
                }
                .testTag("preset_${preset.name}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = preset.label.split("/").first().trim(),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) colors.onPrimary else colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.size(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Velocidade da fala", fontSize = 17.sp, color = colors.onSurface)
          Text("%.2fx".format(config.speechRate), fontSize = 17.sp, color = colors.primary)
        }
        Slider(
          value = config.speechRate,
          onValueChange = { onConfigChange(config.copy(speechRate = it)) },
          valueRange = 0.7f..1.5f,
          steps = 7,
          colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
          modifier = Modifier.testTag("slider_speech_rate")
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Tom da voz", fontSize = 17.sp, color = colors.onSurface)
          Text(
            text = when {
              config.pitch < 0.9f -> "Grave (%.2fx)".format(config.pitch)
              config.pitch > 1.1f -> "Agudo (%.2fx)".format(config.pitch)
              else -> "Natural (%.2fx)".format(config.pitch)
            },
            fontSize = 17.sp,
            color = colors.primary
          )
        }
        Slider(
          value = config.pitch,
          onValueChange = { onConfigChange(config.copy(pitch = it)) },
          valueRange = 0.7f..1.5f,
          steps = 7,
          colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
          modifier = Modifier.testTag("slider_pitch")
        )

        Spacer(modifier = Modifier.size(8.dp))

        Button(
          onClick = { if (isSpeaking) onStopVoice() else onTestVoice() },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isSpeaking) colors.error else colors.primary,
            contentColor = if (isSpeaking) colors.onError else colors.onPrimary
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("btn_test_curiosity_voice")
        ) {
          Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isSpeaking) "Parar demonstração" else "Ouvir demonstração da voz",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
          )
        }
      }
    }

    // Short summaries
    item {
      Column(modifier = Modifier.padding(top = 20.dp)) {
        HorizontalDivider(color = hairline)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Só resumos curtos", fontSize = 17.sp, color = colors.onSurface)
            Text(
              text = "Limita a narração a uma ou duas frases para não interromper a música.",
              fontSize = 15.sp,
              lineHeight = 20.sp,
              color = colors.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Switch(
            checked = config.shortSummaryOnly,
            onCheckedChange = { onConfigChange(config.copy(shortSummaryOnly = it)) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = colors.onPrimary,
              checkedTrackColor = colors.primary
            ),
            modifier = Modifier.testTag("switch_short_summary")
          )
        }
        HorizontalDivider(color = hairline)
      }
    }
  }
}

/** A list row that shows a check mark when chosen, like a picker list. */
@Composable
private fun ChoiceRow(
  title: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 17.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = description,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      if (selected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Selecionado",
          tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
