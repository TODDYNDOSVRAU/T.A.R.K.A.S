package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CuriosityConfig
import com.example.data.model.CuriosityDisplayMode
import com.example.data.model.CuriosityTiming
import com.example.data.model.VoicePreset
import com.example.ui.theme.AppleMusicCyan
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.SpaceGrotesk

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

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // Header with back navigation and title
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("btn_back_curiosity_settings")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = AppleMusicPink,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Curiosidades Musicais",
              fontFamily = SpaceGrotesk,
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
          Text(
            text = "Configurações de fala por IA e momento de exibição",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // 1. Master Switch: Ativar Curiosidades Faladas pela IA
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .testTag("card_master_curiosity_switch"),
        colors = CardDefaults.cardColors(
          containerColor = if (config.enabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
          else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                  if (config.enabled) Brush.linearGradient(listOf(AppleMusicPink, AppleMusicPurple))
                  else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
              Text(
                text = "Curiosidades Faladas por IA",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (config.enabled)
                  "Ativadas • Fatos curtos e relevantes falados pela IA"
                else "Desativadas • Sem narração automática",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
              )
            }
          }

          Switch(
            checked = config.enabled,
            onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = AppleMusicPink
            ),
            modifier = Modifier.testTag("switch_curiosity_enabled")
          )
        }
      }
    }

    // Voice test indicator card (visible if voice sample is speaking)
    if (isSpeaking && !currentSpokenText.isNullOrBlank()) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, AppleMusicPink.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
              initialValue = 0.9f,
              targetValue = 1.15f,
              animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
              ),
              label = "scale"
            )

            Box(
              modifier = Modifier
                .size(34.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(AppleMusicPink, AppleMusicPurple))),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "IA Falando agora...",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = AppleMusicPink
              )
              Text(
                text = currentSpokenText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
              )
            }

            IconButton(
              onClick = onStopVoice,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Parar",
                tint = AppleMusicPink
              )
            }
          }
        }
      }
    }

    // 2. Section: Quando a curiosidade vai aparecer (Timing)
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "QUANDO A CURIOSIDADE VAI APARECER",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val timings = listOf(
              Triple(
                CuriosityTiming.START_OF_TRACK,
                "No início da música",
                "A curiosidade começa a tocar antes da música. O player aguarda o fim da fala para iniciar a faixa."
              ),
              Triple(
                CuriosityTiming.END_OF_TRACK,
                "Ao final da música (Padrão)",
                "A curiosidade é falada quando a música termina, antes de saltar para a próxima faixa."
              ),
              Triple(
                CuriosityTiming.BOTH,
                "No início e no final",
                "Breve introdução antes de começar e curiosidade complementar ao final."
              ),
              Triple(
                CuriosityTiming.MANUAL_ONLY,
                "Apenas sob comando",
                "A IA não fala sozinha; responda apenas quando você pedir no Assistente."
              )
            )

            timings.forEach { (timing, label, desc) ->
              val selected = config.timing == timing
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onConfigChange(config.copy(timing = timing)) }
                  .testTag("timing_option_${timing.name}")
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                      )
                      .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    if (selected) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(Color.White)
                      )
                    }
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column {
                    Text(
                      text = label,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                      fontSize = 13.sp,
                      color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = desc,
                      fontSize = 11.sp,
                      color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                      lineHeight = 15.sp
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // 3. Section: Preferência de como aparece a curiosidade
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "PREFERÊNCIA DE COMO APARECE",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf(
              Pair(
                CuriosityDisplayMode.VOICE_AND_BANNER,
                Pair("Voz + Banner no Player", "Exibe badge flutuante animado enquanto a IA narra.")
              ),
              Pair(
                CuriosityDisplayMode.VOICE_ONLY,
                Pair("Apenas Voz da IA", "Sem cards visuais; foco total no áudio imersivo.")
              ),
              Pair(
                CuriosityDisplayMode.VOICE_AND_NOTIFICATION,
                Pair("Voz + Notificação do Sistema", "Atualiza a notificação de mídia nativa com o fato falado.")
              ),
              Pair(
                CuriosityDisplayMode.VOICE_AND_MODAL,
                Pair("Voz + Modal de Detalhes", "Abre janela com fontes e contexto histórico completo.")
              )
            )

            modes.forEach { (mode, pair) ->
              val (title, description) = pair
              val selected = config.displayMode == mode

              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onConfigChange(config.copy(displayMode = mode)) }
                  .testTag("display_mode_${mode.name}")
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                      )
                      .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    if (selected) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(Color.White)
                      )
                    }
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column {
                    Text(
                      text = title,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                      fontSize = 13.sp,
                      color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = description,
                      fontSize = 11.sp,
                      color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Section: Configurações de Voz (Preset, Tom, Velocidade, Teste)
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "CONFIGURAÇÕES DE VOZ",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Estilo de Tom da Voz",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Presets chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              VoicePreset.values().forEach { preset ->
                val selected = config.voicePreset == preset
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = if (selected) AppleMusicPink else MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier
                    .weight(1f)
                    .clickable {
                      onConfigChange(
                        config.copy(
                          voicePreset = preset,
                          pitch = preset.pitch,
                          speechRate = preset.speechRate
                        )
                      )
                    }
                    .testTag("preset_${preset.name}")
                ) {
                  Text(
                    text = preset.label.split("/").first().trim(),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Speech Rate Slider
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Velocidade da Fala",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "%.2fx".format(config.speechRate),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Slider(
              value = config.speechRate,
              onValueChange = {
                onConfigChange(config.copy(speechRate = it))
              },
              valueRange = 0.7f..1.5f,
              steps = 7,
              colors = SliderDefaults.colors(
                thumbColor = AppleMusicPink,
                activeTrackColor = AppleMusicPink
              ),
              modifier = Modifier.testTag("slider_speech_rate")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch Slider
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Tom da Voz (Pitch)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = when {
                  config.pitch < 0.9f -> "Grave (%.2fx)".format(config.pitch)
                  config.pitch > 1.1f -> "Agudo (%.2fx)".format(config.pitch)
                  else -> "Natural (%.2fx)".format(config.pitch)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Slider(
              value = config.pitch,
              onValueChange = {
                onConfigChange(config.copy(pitch = it))
              },
              valueRange = 0.7f..1.5f,
              steps = 7,
              colors = SliderDefaults.colors(
                thumbColor = AppleMusicPurple,
                activeTrackColor = AppleMusicPurple
              ),
              modifier = Modifier.testTag("slider_pitch")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Test voice button
            Button(
              onClick = {
                if (isSpeaking) {
                  onStopVoice()
                } else {
                  onTestVoice()
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_test_curiosity_voice")
            ) {
              Icon(
                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isSpeaking) "Parar Demonstração" else "Ouvir Demonstração da Voz",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      }
    }

    // 5. Section: Relevância & Resumo curto
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Curiosidades Básicas & Curtas",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Limita a narração a 1-2 frases concisas para não interromper a imersão musical.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
          }

          Switch(
            checked = config.shortSummaryOnly,
            onCheckedChange = { onConfigChange(config.copy(shortSummaryOnly = it)) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = AppleMusicPink
            )
          )
        }
      }
    }
  }
}
