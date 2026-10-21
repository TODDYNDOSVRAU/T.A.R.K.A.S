package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssistantMessage
import com.example.data.model.LocalReminder
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionReport
import com.example.ui.components.ToolReportCard
import com.example.ui.theme.AppleMusicCyan
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.SpaceGrotesk
import java.util.Locale

@Composable
fun AssistantScreen(
  messages: List<AssistantMessage>,
  isProcessing: Boolean,
  hasGeminiKey: Boolean,
  activeTimers: List<LocalTimer>,
  reminders: List<LocalReminder>,
  onSendMessage: (String) -> Unit,
  onCancelTimer: (String) -> Unit,
  curiosityConfig: com.example.data.model.CuriosityConfig? = null,
  onOpenCuriositySettings: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var isTextMode by remember { mutableStateOf(false) }
  var textInput by remember { mutableStateOf("") }
  var isListening by remember { mutableStateOf(false) }

  // Speech Recognizer Launcher for Audio/Microphone
  val speechLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    isListening = false
    if (result.resultCode == Activity.RESULT_OK) {
      val data = result.data
      val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
      val query = matches?.firstOrNull()
      if (!query.isNullOrBlank()) {
        onSendMessage(query)
      }
    }
  }

  fun launchVoiceInput() {
    try {
      isListening = true
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ouvindo... Peça uma análise, curioso sobre a música, ou ajuste o som")
      }
      speechLauncher.launch(intent)
    } catch (_: Exception) {
      isListening = false
      isTextMode = true
    }
  }

  val lastAssistantMsg = messages.lastOrNull { it.sender == MessageSender.ASSISTANT }
  val lastUserMsg = messages.lastOrNull { it.sender == MessageSender.USER }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // 1. Siri / Google Assistant Orb & Voice Area
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {

        // Mode switch pills (Áudio / Microfone vs Teclado)
        Row(
          modifier = Modifier.padding(bottom = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (!isTextMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .clickable { isTextMode = false }
              .testTag("btn_mode_voice")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (!isTextMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Modo Voz", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (!isTextMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isTextMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .clickable { isTextMode = true }
              .testTag("btn_mode_text")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isTextMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Modo Texto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isTextMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          // Help chip
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { onSendMessage("help") }
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.HelpOutline,
              contentDescription = "Ajuda e Comandos",
              modifier = Modifier
                .padding(6.dp)
                .size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Animated Siri Orb
        val infiniteTransition = rememberInfiniteTransition(label = "SiriOrb")
        val pulseScale by infiniteTransition.animateFloat(
          initialValue = 0.95f,
          targetValue = 1.08f,
          animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
          ),
          label = "pulse"
        )
        val glowAlpha by infiniteTransition.animateFloat(
          initialValue = 0.4f,
          targetValue = 0.85f,
          animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
          ),
          label = "glow"
        )

        Box(
          modifier = Modifier
            .size(140.dp)
            .scale(if (isListening || isProcessing) pulseScale else 1f)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                listOf(
                  AppleMusicPink.copy(alpha = glowAlpha),
                  AppleMusicPurple.copy(alpha = 0.6f),
                  AppleMusicCyan.copy(alpha = 0.4f),
                  Color.Transparent
                )
              )
            )
            .clickable { launchVoiceInput() }
            .testTag("siri_voice_orb"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .background(Color(0xFF0D0D10)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isProcessing) Icons.Default.GraphicEq else Icons.Default.Mic,
              contentDescription = "Falar com Assistente",
              tint = if (isListening) AppleMusicPink else Color.White,
              modifier = Modifier.size(36.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = if (isListening) "Ouvindo você..." else if (isProcessing) "Pesquisando e analisando..." else "Toque no orbe para falar",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        Text(
          text = "ou digite seu comando abaixo (ex: 'analisar música')",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // 2. Siri / Assistant Response Card
    if (lastAssistantMsg != null) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .testTag("assistant_siri_response_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            // If there was a user request, display it as a query badge
            if (lastUserMsg != null) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(bottom = 10.dp)
              ) {
                Text(
                  text = "\"${lastUserMsg.text}\"",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }

            Text(
              text = lastAssistantMsg.text,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 14.sp,
              lineHeight = 20.sp
            )

            lastAssistantMsg.toolReport?.let { report ->
              Spacer(modifier = Modifier.height(8.dp))
              ToolReportCard(report = report)
            }
          }
        }
      }
    }

    // 2. Curiosidades Musicais Feature Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .clickable { onOpenCuriositySettings() }
          .testTag("btn_assistant_curiosidades_musicais"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AppleMusicPink, AppleMusicPurple))),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Curiosidades Musicais",
                  fontFamily = SpaceGrotesk,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (curiosityConfig?.enabled != false) AppleMusicPink.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                  modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                  Text(
                    text = if (curiosityConfig?.enabled != false) "ATIVADO" else "DESATIVADO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (curiosityConfig?.enabled != false) AppleMusicPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              val timingText = curiosityConfig?.timing?.label ?: "Ao final da música"
              Text(
                text = "Narração por IA: $timingText • Configurar voz e momento",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
          ) {
            Text(
              text = "Ajustar",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }
      }
    }

    // 3. Quick Action Chips (Contextual Shortcuts)
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Ações Rápidas",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
          val actions = listOf(
            Pair("Curiosidades Musicais", Icons.Default.AutoAwesome),
            Pair("Analisar música tocando", Icons.Default.Search),
            Pair("Equalizador 15 bandas", Icons.Default.Equalizer),
            Pair("Diagnóstico de áudio", Icons.Default.GraphicEq),
            Pair("Timer de 15 minutos", Icons.Default.Timer),
            Pair("help", Icons.AutoMirrored.Filled.HelpOutline)
          )
          items(actions) { (act, icon) ->
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier
                .clickable {
                  if (act == "Curiosidades Musicais") {
                    onOpenCuriositySettings()
                  } else {
                    onSendMessage(act)
                  }
                }
                .testTag("assistant_action_${act.take(6)}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(act, fontSize = 12.sp, fontWeight = FontWeight.Medium)
              }
            }
          }
        }
      }
    }

    // 4. Active Timers Section (if any)
    if (activeTimers.isNotEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("TEMPORIZADORES LOCAIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            activeTimers.forEach { timer ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = if (timer.isCompleted) Icons.Default.Alarm else Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "${timer.label}: ${timer.formattedTime()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
                IconButton(onClick = { onCancelTimer(timer.id) }, modifier = Modifier.size(28.dp)) {
                  Icon(imageVector = Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }

    // 5. Text Input field (always accessible or in Text Mode)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = textInput,
          onValueChange = { textInput = it },
          placeholder = { Text("Digite comando ou 'help'...", fontSize = 13.sp) },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("siri_text_input")
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
          onClick = {
            if (textInput.isNotBlank()) {
              val send = textInput.trim()
              textInput = ""
              onSendMessage(send)
            }
          },
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .testTag("siri_send_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Enviar",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(60.dp))
    }
  }
}
