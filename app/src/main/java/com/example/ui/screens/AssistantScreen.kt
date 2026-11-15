package com.example.ui.screens

import com.example.ui.layout.readingColumn
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.AssistantMessage
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.ui.components.GroupDivider
import com.example.ui.components.IconAction
import com.example.ui.components.ToolReportCard
import com.example.ui.components.highlightClickable
import com.example.ui.theme.AppText
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the player is doing now. It decides which ready-made requests make sense (no "next" without a track). */
enum class AssistantPlayback { NONE, PLAYING, PAUSED }

/**
 * A ready-made request: label and command resources (both follow the system language, so a chip shows and
 * sends its text in the interface language), and when it is offered.
 */
private class Suggestion(val label: Int, val command: Int, val offeredWhen: (AssistantPlayback) -> Boolean)

private val AnyTime: (AssistantPlayback) -> Boolean = { true }
private val WithTrack: (AssistantPlayback) -> Boolean = { it != AssistantPlayback.NONE }

/** Curiosities live only in the player and notification, so no chip asks for them. */
private val SuggestionCommands = listOf(
  Suggestion(R.string.assistant_suggestion_next, R.string.assistant_suggestion_command_next, WithTrack),
  Suggestion(R.string.assistant_suggestion_pause, R.string.assistant_suggestion_command_pause) { it == AssistantPlayback.PLAYING },
  Suggestion(R.string.assistant_suggestion_play, R.string.assistant_suggestion_command_play) { it == AssistantPlayback.PAUSED },
  Suggestion(R.string.assistant_suggestion_shuffle, R.string.assistant_suggestion_command_shuffle, AnyTime),
  Suggestion(R.string.assistant_suggestion_repeat, R.string.assistant_suggestion_command_repeat, AnyTime),
  Suggestion(R.string.assistant_suggestion_favorite, R.string.assistant_suggestion_command_favorite, WithTrack),
  Suggestion(R.string.assistant_suggestion_diagnostics, R.string.assistant_suggestion_command_diagnostics, AnyTime),
  Suggestion(R.string.assistant_suggestion_eq_flat, R.string.assistant_suggestion_command_eq_flat, AnyTime),
  Suggestion(R.string.assistant_suggestion_timer, R.string.assistant_suggestion_command_timer, AnyTime),
  Suggestion(R.string.assistant_suggestion_help, R.string.assistant_suggestion_command_help, AnyTime)
)

/** Who said it, for screen readers ("Você: …", "Assistente: …"). */
@Composable
private fun MessageSender.spokenName(): String = stringResource(
  when (this) {
    MessageSender.USER -> R.string.assistant_sender_user
    MessageSender.ASSISTANT -> R.string.assistant_sender_assistant
    MessageSender.TOOL -> R.string.assistant_sender_tool
    MessageSender.SYSTEM -> R.string.assistant_sender_system
  }
)

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
  // A question said aloud: its reply is read aloud too. Defaults to the typed path.
  onSendVoiceMessage: (String) -> Unit = onSendMessage,
  onStopSpeech: () -> Unit = {},
  onCancelTimer: (String) -> Unit,
  // Asks again after a failed online reply; the argument is the error message's id.
  onRetry: (String) -> Unit = {},
  // Online answers on or off (only offered when a key is set). Saved by the app.
  onlineAi: Boolean = true,
  onSetOnlineAi: (Boolean) -> Unit = {},
  playback: AssistantPlayback = AssistantPlayback.NONE,
  onClearConversation: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  // Saveable: a half-typed request, the voice state and a pending "clear" survive rotation and process death.
  var textInput by rememberSaveable { mutableStateOf("") }
  var isListening by rememberSaveable { mutableStateOf(false) }
  var voiceError by rememberSaveable { mutableStateOf<String?>(null) }
  var enableHelpShown by rememberSaveable { mutableStateOf(false) }
  var confirmClear by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val reduced = rememberReducedMotion()
  val colors = MaterialTheme.colorScheme
  val online = hasGeminiKey && onlineAi
  // Resolved here because launchVoiceInput() below is not composable.
  val voicePrompt = stringResource(R.string.assistant_voice_prompt)
  val voiceUnavailable = stringResource(R.string.assistant_voice_unavailable)
  val voiceFailed = stringResource(R.string.assistant_voice_failed)

  val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    isListening = false
    if (result.resultCode == Activity.RESULT_OK) {
      val query = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
      if (!query.isNullOrBlank()) onSendVoiceMessage(query)
    }
  }

  fun launchVoiceInput() {
    voiceError = null
    // The recogniser must not hear the reply that is being read aloud.
    onStopSpeech()
    try {
      isListening = true
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
      }
      speechLauncher.launch(intent)
    } catch (_: ActivityNotFoundException) {
      isListening = false
      voiceError = voiceUnavailable
    } catch (_: Exception) {
      isListening = false
      voiceError = voiceFailed
    }
  }

  fun send() {
    val text = textInput.trim()
    if (text.isEmpty()) return
    textInput = ""
    onSendMessage(text)
  }

  // Keep the newest message in view: index 0 is the header, messages follow.
  // The last item is the "thinking" line while a reply is pending, otherwise the newest message. Scrolling to
  // the message index alone left that line below the fold after sending (F23).
  val lastIndex = messages.size + (if (isProcessing) 1 else 0)
  LaunchedEffect(messages.size, isProcessing) {
    if (messages.isNotEmpty() || isProcessing) {
      if (reduced) listState.scrollToItem(lastIndex) else listState.animateScrollToItem(lastIndex)
    }
  }

  val listeningText = stringResource(R.string.assistant_listening)
  val thinkingText = stringResource(R.string.assistant_thinking)
  val lastReply = messages.lastOrNull()?.takeIf { it.sender != MessageSender.USER }
  val lastReplySpoken = lastReply?.let {
    stringResource(R.string.assistant_message_spoken, it.sender.spokenName(), lightMarkdown(it.text).text)
  }
  val announcement = when {
    isListening -> listeningText
    isProcessing -> thinkingText
    else -> lastReplySpoken.orEmpty()
  }
  val suggestions = remember(playback) { SuggestionCommands.filter { it.offeredWhen(playback) } }
  val hasUserMessage = messages.any { it.sender == MessageSender.USER }

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
      modifier = Modifier.weight(1f).readingColumn(),
      contentPadding = PaddingValues(start = Grid.margin, end = Grid.margin, top = Grid.u6, bottom = Grid.u12),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      item {
        Column(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(if (online) R.string.assistant_status_online else R.string.assistant_status_local),
              style = GridText.Small,
              color = colors.onSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            // Without a key the only way to turn the online AI on is at build time; say so on demand.
            if (!hasGeminiKey) {
              Box(
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .clip(Shapes.pill)
                  .highlightClickable(onClick = { enableHelpShown = !enableHelpShown })
                  // Padding on the start only: the text ends on the 24 dp margin like the rows below it.
                  .padding(start = Grid.u12)
                  .testTag("assistant_enable_help"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  stringResource(if (enableHelpShown) R.string.assistant_enable_hide else R.string.assistant_enable_action),
                  style = GridText.Small,
                  color = colors.primary
                )
              }
            }
          }
          if (!hasGeminiKey && enableHelpShown) {
            Text(
              stringResource(R.string.assistant_enable_body),
              style = GridText.Small,
              color = colors.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(bottom = Grid.u6)
            )
          }
          if (hasGeminiKey) {
            // The whole row is the switch, so the label and the knob both toggle it.
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Grid.u48)
                .toggleable(value = onlineAi, role = Role.Switch, onValueChange = onSetOnlineAi)
                .testTag("assistant_online_switch"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                stringResource(R.string.assistant_online_label),
                style = GridText.Body,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
              )
              Switch(checked = onlineAi, onCheckedChange = null)
            }
            Text(
              stringResource(if (onlineAi) R.string.assistant_online_disclosure_on else R.string.assistant_online_disclosure_off),
              style = GridText.Small,
              color = colors.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(bottom = Grid.u6)
            )
          }
          if (isListening) {
            Text(listeningText, style = GridText.Small, color = colors.primary, modifier = Modifier.padding(top = Grid.u6))
          }
          if (messages.isEmpty()) {
            Spacer(Modifier.height(Grid.u48))
            Text(
              stringResource(R.string.assistant_empty_title),
              style = GridText.Title,
              color = colors.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
            Text(
              stringResource(R.string.assistant_empty_body),
              style = GridText.Body,
              color = colors.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(top = Grid.u6)
            )
          }
          voiceError?.let {
            Text(it, style = GridText.Small, color = colors.error, modifier = Modifier.padding(top = Grid.u12))
          }
          if (hasUserMessage) {
            ClearConversation(
              confirming = confirmClear,
              onAsk = { confirmClear = true },
              onConfirm = {
                confirmClear = false
                onClearConversation()
              },
              onCancel = { confirmClear = false }
            )
          }
          if (activeTimers.isNotEmpty()) {
            Spacer(Modifier.height(Grid.u12))
            // Drawn like an InsetGroup, without its page margin: the list already applies it.
            Column(
              Modifier
                .fillMaxWidth()
                .clip(Grid.r12)
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

      items(messages, key = { it.id }) { message -> MessageBubble(message, onRetry) }

      if (isProcessing) {
        item {
          ThinkingIndicator(stringResource(R.string.assistant_thinking_visible), reduced)
        }
      }
    }

    Suggestions(suggestions, onSendMessage)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12, bottom = Grid.u12),
      verticalAlignment = Alignment.Bottom
    ) {
      ComposerField(
        value = textInput,
        onValueChange = { textInput = it },
        onSend = ::send,
        modifier = Modifier.weight(1f).testTag("siri_text_input")
      )
      IconAction(
        icon = Icons.Rounded.Mic,
        contentDescription = stringResource(if (isListening) R.string.assistant_listening else R.string.assistant_mic_idle),
        onClick = ::launchVoiceInput,
        tint = colors.primary,
        modifier = Modifier.testTag("siri_voice_orb")
      )
      IconAction(
        icon = Icons.Rounded.ArrowUpward,
        contentDescription = stringResource(R.string.assistant_send),
        onClick = ::send,
        tint = if (textInput.isBlank()) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.primary,
        filled = true,
        size = Grid.u48,
        modifier = Modifier.testTag("siri_send_button")
      )
    }
  }
}

/** "Limpar conversa", and a confirmation before it removes the saved conversation. */
@Composable
private fun ClearConversation(confirming: Boolean, onAsk: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  if (!confirming) {
    TextButton(
      onClick = onAsk,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_clear")
    ) {
      Text(stringResource(R.string.assistant_clear), style = GridText.Small, color = colors.primary)
    }
  } else {
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = Grid.u6),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        stringResource(R.string.assistant_clear_confirm),
        style = GridText.Small,
        color = colors.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )
      TextButton(onClick = onCancel, modifier = Modifier.minimumInteractiveComponentSize()) {
        Text(stringResource(R.string.assistant_clear_no), style = GridText.Small, color = colors.onSurfaceVariant)
      }
      TextButton(onClick = onConfirm, modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_clear_yes")) {
        Text(stringResource(R.string.assistant_clear_yes), style = GridText.Small, color = colors.error)
      }
    }
  }
}

/** Three dots that pulse while a reply is pending; under reduced motion they stay still. */
@Composable
private fun ThinkingIndicator(label: String, reduced: Boolean) {
  val colors = MaterialTheme.colorScheme
  val transition = rememberInfiniteTransition(label = "assistant_thinking")
  Row(
    modifier = Modifier.padding(start = Grid.u12).semantics { contentDescription = label },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = GridText.Small, color = colors.onSurfaceVariant)
    Spacer(Modifier.width(Grid.u12))
    for (i in 0 until 3) {
      val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 450, delayMillis = i * 150),
          repeatMode = RepeatMode.Reverse
        ),
        label = "assistant_dot_$i"
      )
      Box(
        Modifier
          .size(Grid.u6)
          .clip(CircleShape)
          .background(colors.onSurfaceVariant.copy(alpha = if (reduced) 0.6f else alpha))
      )
      if (i < 2) Spacer(Modifier.width(Grid.u6))
    }
  }
}

@Composable
private fun TimerRow(timer: LocalTimer, onCancel: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Grid.u48)
      .padding(start = Grid.u12, end = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      if (timer.isCompleted) Icons.Rounded.Alarm else Icons.Rounded.Timer,
      contentDescription = null,
      tint = colors.primary,
      modifier = Modifier.size(Grid.u24)
    )
    Spacer(Modifier.width(Grid.u12))
    Text(
      stringResource(R.string.assistant_timer_line, timer.label, timer.formattedTime()),
      style = GridText.Body,
      color = colors.onSurface,
      modifier = Modifier.weight(1f)
    )
    IconAction(
      icon = Icons.Rounded.Close,
      contentDescription = stringResource(R.string.assistant_timer_cancel, timer.label),
      onClick = onCancel,
      tint = colors.onSurfaceVariant
    )
  }
}

@Composable
private fun MessageBubble(message: AssistantMessage, onRetry: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val isUser = message.sender == MessageSender.USER
  val text = lightMarkdown(message.text)
  val spoken = stringResource(R.string.assistant_message_spoken, message.sender.spokenName(), text.text)
  val time = remember(message.timestampMs) {
    if (message.timestampMs > 0L) DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(message.timestampMs)) else ""
  }
  val shape = RoundedCornerShape(
    topStart = Grid.u24,
    topEnd = Grid.u24,
    bottomStart = if (isUser) Grid.u24 else Grid.u6,
    bottomEnd = if (isUser) Grid.u6 else Grid.u24
  )
  // A bubble takes what its text needs, up to most of the row; long replies wrap instead of stopping at a fixed width.
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bubbleMax = maxWidth * 0.82f
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
      SelectionContainer {
        Text(
          text = text,
          style = GridText.Body,
          color = if (isUser) colors.onPrimary else colors.onSurface,
          modifier = Modifier
            .widthIn(max = bubbleMax)
            .clip(shape)
            .background(if (isUser) colors.primary else colors.surfaceContainer)
            .padding(horizontal = Grid.u12, vertical = Grid.u12)
            .semantics { contentDescription = spoken }
            .testTag(if (isUser) "message_user" else "message_assistant")
        )
      }
      if (time.isNotEmpty()) {
        Text(
          time,
          style = GridText.Small,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(top = Grid.u6, start = Grid.u6, end = Grid.u6)
        )
      }
      message.toolReport?.let { report ->
        Spacer(Modifier.height(Grid.u6))
        ToolReportCard(report = report, modifier = Modifier.widthIn(max = bubbleMax))
      }
      if (message.failure?.retryable == true) {
        TextButton(
          onClick = { onRetry(message.id) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("assistant_retry")
        ) {
          Text(stringResource(R.string.assistant_retry), style = GridText.Small)
        }
      }
    }
  }
}

/** Small flat chips in one scrolling row; each one sends its request. */
@Composable
private fun Suggestions(suggestions: List<Suggestion>, onSendMessage: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    contentPadding = PaddingValues(horizontal = Grid.margin),
    modifier = Modifier.fillMaxWidth()
  ) {
    items(suggestions) { suggestion ->
      val command = stringResource(suggestion.command)
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
            .heightIn(min = Grid.u48)
            .clip(Shapes.pill)
            .background(colors.surfaceContainer)
            .padding(horizontal = Grid.u12, vertical = Grid.u12),
          contentAlignment = Alignment.Center
        ) {
          Text(stringResource(suggestion.label), style = GridText.Body, color = colors.onSurface, maxLines = 1)
        }
      }
    }
  }
}

/**
 * The message field: the same quiet fill as the search field. It grows to four lines for longer messages;
 * the send button sends it (Enter starts a new line).
 */
@Composable
private fun ComposerField(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val placeholder = stringResource(R.string.assistant_composer_placeholder)
  val fieldDescription = stringResource(R.string.assistant_composer_description)
  Box(
    modifier = modifier
      .heightIn(min = Grid.u48)
      .clip(Grid.r12)
      .background(colors.surfaceContainer)
      .padding(horizontal = Grid.u12, vertical = Grid.u12),
    contentAlignment = Alignment.CenterStart
  ) {
    if (value.isEmpty()) {
      Text(placeholder, style = GridText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      maxLines = 4,
      textStyle = GridText.Body.copy(color = colors.onSurface),
      cursorBrush = SolidColor(colors.primary),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { onSend() }),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = fieldDescription }
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

