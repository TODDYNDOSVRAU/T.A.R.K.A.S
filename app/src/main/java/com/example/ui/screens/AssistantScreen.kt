package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantMessage
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.ui.components.GroupDivider
import com.example.ui.components.IconAction
import com.example.ui.components.ToolReportCard
import com.example.ui.components.highlightClickable
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.rememberReducedMotion
import java.util.Locale

/** Ready-made requests: (label shown, command sent). Curiosities live only in the player and notification. */
private val SuggestionCommands = listOf(
  "Próxima faixa" to "próxima faixa",
  "Aleatório" to "aleatório",
  "Repetir" to "repetir",
  "Favoritar" to "favoritar",
  "Diagnóstico de áudio" to "diagnóstico de áudio",
  "Equalizador flat" to "equalizador flat",
  "Timer de 15 min" to "timer de 15 minutos",
  "Ajuda" to "help"
)

/** Who said it, for screen readers ("Você: …", "Assistente: …"). */
private fun MessageSender.spokenName(): String = when (this) {
  MessageSender.USER -> "Você"
  MessageSender.ASSISTANT -> "Assistente"
  MessageSender.TOOL -> "Ação"
  MessageSender.SYSTEM -> "Aviso"
}

/**
 * The assistant as a plain conversation: messages as bubbles (yours on the right in the accent, the
 * assistant's on the left on a quiet fill), active timers on top, a row of suggestions and the field
 * pinned above the tab bar. Voice input uses the system recogniser behind the mic button.
 *
 * New replies are announced politely to screen readers through one live region outside the list, so
 * they are read even when the list has not scrolled yet.
 */
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
  // Saveable: a half-typed request and the voice state survive rotation and process death.
  var textInput by rememberSaveable { mutableStateOf("") }
  var isListening by rememberSaveable { mutableStateOf(false) }
  var voiceError by rememberSaveable { mutableStateOf<String?>(null) }
  val listState = rememberLazyListState()
  val reduced = rememberReducedMotion()
  val colors = MaterialTheme.colorScheme

  val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    isListening = false
    if (result.resultCode == Activity.RESULT_OK) {
      val query = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
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
      voiceError = "Este aparelho não tem reconhecimento de voz. Digite o pedido abaixo."
    } catch (_: Exception) {
      isListening = false
      voiceError = "Não foi possível abrir o reconhecimento de voz. Digite o pedido abaixo."
    }
  }

  fun send() {
    val text = textInput.trim()
    if (text.isEmpty()) return
    textInput = ""
    onSendMessage(text)
  }

  // Keep the newest message in view: index 0 is the header, messages follow.
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      if (reduced) listState.scrollToItem(messages.size) else listState.animateScrollToItem(messages.size)
    }
  }

  val announcement = when {
    isListening -> "Ouvindo"
    isProcessing -> "Pensando"
    else -> messages.lastOrNull()?.takeIf { it.sender != MessageSender.USER }
      ?.let { "${it.sender.spokenName()}: ${lightMarkdown(it.text).text}" }
      .orEmpty()
  }

  Column(modifier = modifier.fillMaxSize().padding(bottom = LocalBarInset.current)) {
    // The live region: one dot-sized node, so it is announced but adds nothing visible.
    Box(
      Modifier
        .size(1.dp)
        .semantics {
          liveRegion = LiveRegionMode.Polite
          contentDescription = announcement
        }
    )
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(start = Space.page, end = Space.page, top = Space.xs, bottom = Space.m),
      verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
      item {
        Column(Modifier.fillMaxWidth()) {
          Text(
            if (hasGeminiKey) "Comandos locais e IA online (Gemini)." else "Comandos locais. Sem IA online.",
            style = AppText.Footnote,
            color = colors.onSurfaceVariant
          )
          if (messages.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Text(
              "Como posso ajudar?",
              style = AppText.Title3,
              color = colors.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
            Text(
              "Peça para trocar de faixa, ajustar o equalizador ou marcar um timer. Toque no microfone para falar.",
              style = AppText.Subhead,
              color = colors.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
            )
          }
          voiceError?.let {
            Text(it, style = AppText.Footnote, color = colors.error, modifier = Modifier.padding(top = Space.s))
          }
          if (activeTimers.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            // Drawn like an InsetGroup, without its page margin: the list already applies it.
            Column(
              Modifier
                .fillMaxWidth()
                .clip(Shapes.m)
                .background(colors.surface)
            ) {
              activeTimers.forEachIndexed { i, timer ->
                if (i > 0) GroupDivider()
                TimerRow(timer, onCancel = { onCancelTimer(timer.id) })
              }
            }
          }
        }
      }

      items(messages, key = { it.id }) { message -> MessageBubble(message) }

      if (isProcessing) {
        item {
          Text("Pensando…", style = AppText.Footnote, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = Space.s))
        }
      }
    }

    Suggestions(onSendMessage)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Space.page, end = Space.s, top = Space.s, bottom = Space.s),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ComposerField(
        value = textInput,
        onValueChange = { textInput = it },
        onSend = ::send,
        modifier = Modifier.weight(1f).testTag("siri_text_input")
      )
      IconAction(
        icon = Icons.Rounded.Mic,
        contentDescription = if (isListening) "Ouvindo" else "Falar com o assistente",
        onClick = ::launchVoiceInput,
        tint = colors.primary,
        modifier = Modifier.testTag("siri_voice_orb")
      )
      IconAction(
        icon = Icons.Rounded.ArrowUpward,
        contentDescription = "Enviar",
        onClick = ::send,
        tint = if (textInput.isBlank()) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.primary,
        filled = true,
        size = 36.dp,
        modifier = Modifier.testTag("siri_send_button")
      )
    }
  }
}

@Composable
private fun TimerRow(timer: LocalTimer, onCancel: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Size.row)
      .padding(start = Space.l, end = Space.xs),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      if (timer.isCompleted) Icons.Rounded.Alarm else Icons.Rounded.Timer,
      contentDescription = null,
      tint = colors.primary,
      modifier = Modifier.size(Size.iconSmall)
    )
    Spacer(Modifier.width(Space.m))
    Text(
      "${timer.label}: ${timer.formattedTime()}",
      style = AppText.Body,
      color = colors.onSurface,
      modifier = Modifier.weight(1f)
    )
    IconAction(
      icon = Icons.Rounded.Close,
      contentDescription = "Cancelar timer ${timer.label}",
      onClick = onCancel,
      tint = colors.onSurfaceVariant
    )
  }
}

@Composable
private fun MessageBubble(message: AssistantMessage) {
  val colors = MaterialTheme.colorScheme
  val isUser = message.sender == MessageSender.USER
  val text = lightMarkdown(message.text)
  val shape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = if (isUser) 18.dp else 6.dp,
    bottomEnd = if (isUser) 6.dp else 18.dp
  )
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
  ) {
    Text(
      text = text,
      style = AppText.Body,
      color = if (isUser) colors.onPrimary else colors.onSurface,
      modifier = Modifier
        .widthIn(max = 320.dp)
        .clip(shape)
        .background(if (isUser) colors.primary else colors.surfaceContainer)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .semantics { contentDescription = "${message.sender.spokenName()}: ${text.text}" }
        .testTag(if (isUser) "message_user" else "message_assistant")
    )
    message.toolReport?.let { report ->
      Spacer(Modifier.height(Space.xs))
      ToolReportCard(report = report, modifier = Modifier.widthIn(max = 320.dp))
    }
  }
}

/** Small flat chips in one scrolling row; each one sends its request. */
@Composable
private fun Suggestions(onSendMessage: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(Space.s),
    contentPadding = PaddingValues(horizontal = Space.page),
    modifier = Modifier.fillMaxWidth()
  ) {
    items(SuggestionCommands) { (label, command) ->
      // The 48 dp touch area is the outer box; the visible pill stays small inside it.
      Box(
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clip(Shapes.pill)
          .highlightClickable(onClick = { onSendMessage(command) })
          .testTag("assistant_action_${command.take(8)}"),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(Shapes.pill)
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 7.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(label, style = AppText.Subhead, color = colors.onSurface, maxLines = 1)
        }
      }
    }
  }
}

/** The message field: the same quiet fill as the search field, sending with the keyboard's action key. */
@Composable
private fun ComposerField(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val placeholder = "Peça algo ou digite \"ajuda\""
  Box(
    modifier = modifier
      .heightIn(min = 44.dp)
      .clip(Shapes.s)
      .background(colors.surfaceContainer)
      .padding(horizontal = Space.m, vertical = Space.s),
    contentAlignment = Alignment.CenterStart
  ) {
    if (value.isEmpty()) {
      Text(placeholder, style = AppText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = AppText.Body.copy(color = colors.onSurface),
      cursorBrush = SolidColor(colors.primary),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { onSend() }),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = "Mensagem para o assistente" }
    )
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
