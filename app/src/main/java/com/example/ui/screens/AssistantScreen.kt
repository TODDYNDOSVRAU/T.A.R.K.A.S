package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssistantMessage
import com.example.ui.theme.LocalBarInset
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.ui.components.ToolReportCard
import com.example.ui.components.GlassChip
import com.example.ui.theme.glassControl
import com.example.ui.theme.glassContentColor
import com.example.ui.theme.SpaceGrotesk
import java.util.Locale

@Composable
fun AssistantScreen(
  messages: List<AssistantMessage>,
  isProcessing: Boolean,
  hasGeminiKey: Boolean,
  activeTimers: List<LocalTimer>,
  onSendMessage: (String) -> Unit,
  onCancelTimer: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var textInput by remember { mutableStateOf("") }
  var isListening by remember { mutableStateOf(false) }
  var voiceError by remember { mutableStateOf<String?>(null) }
  val listState = rememberLazyListState()
  val colors = MaterialTheme.colorScheme

  // System speech recognizer for the microphone button
  val speechLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    isListening = false
    if (result.resultCode == Activity.RESULT_OK) {
      val query = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
      if (!query.isNullOrBlank()) onSendMessage(query)
    }
  }

  fun launchVoiceInput() {
    voiceError = null
    try {
      isListening = true
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Peça uma análise, troque de faixa ou ajuste o som")
      }
      speechLauncher.launch(intent)
    } catch (_: ActivityNotFoundException) {
      isListening = false
      voiceError = "Este aparelho não tem reconhecimento de voz. Digite o comando abaixo."
    } catch (_: Exception) {
      isListening = false
      voiceError = "Não foi possível abrir o reconhecimento de voz. Digite o comando abaixo."
    }
  }

  // Keep the newest message in view: index 0 is the header, messages follow.
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
  }

  Column(modifier = modifier.fillMaxSize().padding(bottom = LocalBarInset.current)) {
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

      // Header: orb, status, active timers and curiosity settings
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // The pulse only runs while the assistant is listening or thinking; an idle orb costs nothing.
          val pulseScale: State<Float> = if (isListening || isProcessing) {
            rememberOrbPulse()
          } else {
            remember { mutableStateOf(1f) }
          }

          Box(
            modifier = Modifier
              .size(112.dp)
              .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
              }
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  listOf(
                    colors.primary.copy(alpha = 0.85f),
                    colors.secondary.copy(alpha = 0.45f),
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
                .size(62.dp)
                .clip(CircleShape)
                .background(colors.background)
                .glassControl(CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isProcessing) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Falar com o assistente",
                tint = colors.primary,
                modifier = Modifier.size(30.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = when {
              isListening -> "Ouvindo…"
              isProcessing -> "Pensando…"
              else -> "Toque no orbe para falar"
            },
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colors.onBackground
          )
          Text(
            text = if (hasGeminiKey) "Comandos locais e IA (Gemini)" else "Comandos locais, sem IA online",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant
          )
          if (voiceError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = voiceError ?: "", fontSize = 12.sp, color = colors.error)
          }

          if (activeTimers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
              modifier = Modifier.fillMaxWidth().glassControl(RoundedCornerShape(20.dp))
            ) {
              Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                activeTimers.forEach { timer ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = if (timer.isCompleted) Icons.Default.Alarm else Icons.Default.Timer,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "${timer.label}: ${timer.formattedTime()}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.onSurface
                      )
                    }
                    IconButton(onClick = { onCancelTimer(timer.id) }, modifier = Modifier.size(32.dp)) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar timer",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Conversation
      items(messages, key = { it.id }) { message ->
        MessageBubble(message)
      }
    }

    // Suggestions and input stay pinned above the bottom bar
    QuickActions(onSendMessage = onSendMessage)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TextField(
        value = textInput,
        onValueChange = { textInput = it },
        placeholder = { Text("Peça algo ou digite \"ajuda\"", fontSize = 17.sp) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
          .weight(1f)
          .glassControl(RoundedCornerShape(28.dp))
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
          .size(56.dp)
          .glassControl(CircleShape, selected = true)
          .testTag("siri_send_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "Enviar",
          tint = glassContentColor(selected = true),
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
private fun rememberOrbPulse(): State<Float> {
  val transition = rememberInfiniteTransition(label = "AssistantOrb")
  return transition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )
}

@Composable
private fun MessageBubble(message: AssistantMessage) {
  val colors = MaterialTheme.colorScheme
  val isUser = message.sender == MessageSender.USER

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
  ) {
    val bubbleShape = RoundedCornerShape(
      topStart = 20.dp,
      topEnd = 20.dp,
      bottomStart = if (isUser) 20.dp else 6.dp,
      bottomEnd = if (isUser) 6.dp else 20.dp
    )
    // The user's bubbles are the "selected" glass (accent tint), the assistant's the plain glass.
    Box(
      modifier = Modifier
        .widthIn(max = 320.dp)
        .glassControl(bubbleShape, selected = isUser)
        .testTag(if (isUser) "message_user" else "message_assistant")
    ) {
      Text(
        text = lightMarkdown(message.text),
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = if (isUser) glassContentColor(selected = true) else colors.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
      )
    }
    message.toolReport?.let { report ->
      Spacer(modifier = Modifier.height(4.dp))
      ToolReportCard(report = report, modifier = Modifier.widthIn(max = 320.dp))
    }
  }
}

@Composable
private fun QuickActions(onSendMessage: (String) -> Unit) {
  // Curiosities live only in the player and the notification, so no chip here asks for one.
  val actions: List<Triple<String, ImageVector, String>> = listOf(
    Triple("Próxima faixa", Icons.Default.SkipNext, "próxima faixa"),
    Triple("Aleatório", Icons.Default.Shuffle, "aleatório"),
    Triple("Repetir", Icons.Default.Repeat, "repetir"),
    Triple("Favoritar", Icons.Default.Favorite, "favoritar"),
    Triple("Diagnóstico de áudio", Icons.Default.GraphicEq, "diagnóstico de áudio"),
    Triple("Equalizador flat", Icons.Default.Equalizer, "equalizador flat"),
    Triple("Timer de 15 min", Icons.Default.Timer, "timer de 15 minutos"),
    Triple("Ajuda", Icons.AutoMirrored.Filled.HelpOutline, "help")
  )
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(horizontal = 20.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    items(actions) { (label, icon, command) ->
      GlassChip(
        label = label,
        icon = icon,
        onClick = { onSendMessage(command) },
        modifier = Modifier.testTag("assistant_action_${command.take(8)}")
      )
    }
  }
}

/** Renders **bold** and *italic* markers instead of showing the asterisks. */
private fun lightMarkdown(text: String): AnnotatedString = buildAnnotatedString {
  var bold = false
  var italic = false
  var i = 0
  while (i < text.length) {
    when {
      text.startsWith("**", i) -> { bold = !bold; i += 2 }
      text[i] == '*' -> { italic = !italic; i += 1 }
      else -> {
        val start = i
        while (i < text.length && text[i] != '*') i++
        withStyle(
          SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
          )
        ) { append(text.substring(start, i)) }
      }
    }
  }
}
