package com.example.windows.assistente

import androidx.compose.foundation.background
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.ui.components.GroupDivider
import com.example.ui.components.IconAction
import com.example.ui.components.TextAction
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.Shapes
import com.example.windows.res.*
import com.example.windows.state.TarkasState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the player is doing now. It decides which ready-made requests make sense (no "next" without a track). */
enum class AssistantPlayback { NONE, PLAYING, PAUSED }

private class Suggestion(val label: StringResource, val command: StringResource, val offeredWhen: (AssistantPlayback) -> Boolean)

private val AnyTime: (AssistantPlayback) -> Boolean = { true }
private val WithTrack: (AssistantPlayback) -> Boolean = { it != AssistantPlayback.NONE }

private val SuggestionCommands = listOf(
  Suggestion(Res.string.assistant_suggestion_next, Res.string.assistant_suggestion_command_next, WithTrack),
  Suggestion(Res.string.assistant_suggestion_pause, Res.string.assistant_suggestion_command_pause) { it == AssistantPlayback.PLAYING },
  Suggestion(Res.string.assistant_suggestion_play, Res.string.assistant_suggestion_command_play) { it == AssistantPlayback.PAUSED },
  Suggestion(Res.string.assistant_suggestion_shuffle, Res.string.assistant_suggestion_command_shuffle, AnyTime),
  Suggestion(Res.string.assistant_suggestion_repeat, Res.string.assistant_suggestion_command_repeat, AnyTime),
  Suggestion(Res.string.assistant_suggestion_favorite, Res.string.assistant_suggestion_command_favorite, WithTrack),
  Suggestion(Res.string.assistant_suggestion_diagnostics, Res.string.assistant_suggestion_command_diagnostics, AnyTime),
  Suggestion(Res.string.assistant_suggestion_eq_flat, Res.string.assistant_suggestion_command_eq_flat, AnyTime),
  Suggestion(Res.string.assistant_suggestion_timer, Res.string.assistant_suggestion_command_timer, AnyTime),
  Suggestion(Res.string.assistant_suggestion_help, Res.string.assistant_suggestion_command_help, AnyTime)
)

@Composable
private fun MessageSender.spokenName(): String = stringResource(
  when (this) {
    MessageSender.USER -> Res.string.assistant_sender_user
    MessageSender.ASSISTANT -> Res.string.assistant_sender_assistant
    MessageSender.TOOL -> Res.string.assistant_sender_tool
    MessageSender.SYSTEM -> Res.string.assistant_sender_system
  }
)

/** Texts that exist only on the desktop (the key is entered here; on Android it is a build setting). */
internal object KeyTexts {
  private val pt get() = Locale.getDefault().language == "pt"
  val title get() = if (pt) "Chave da API Gemini" else "Gemini API key"
  val body get() = if (pt) "Cole a sua chave. Ela é cifrada para o seu usuário do Windows e nunca entra no instalador." else "Paste your key. It is encrypted for your Windows user and is never part of the installer."
  val placeholder get() = if (pt) "Chave da API" else "API key"
  val historyTitle get() = if (pt) "Conversa" else "Conversation"
  val historyEmpty get() = if (pt) "Nenhuma pergunta ainda." else "No questions yet."
  val composerHint get() = if (pt) "Enter envia. Shift+Enter quebra a linha." else "Enter sends. Shift+Enter starts a new line."
  val copyMessage get() = if (pt) "Copiar mensagem" else "Copy message"
  val copyAll get() = if (pt) "Copiar conversa" else "Copy conversation"
  val curiosityHide get() = if (pt) "Fechar curiosidades" else "Close curiosities"
  val save get() = if (pt) "Guardar chave" else "Save key"
  val remove get() = if (pt) "Remover chave" else "Remove key"
  val invalid get() = if (pt) "Essa chave não é válida. Confira o texto colado." else "That key is not valid. Check the pasted text."
}

/**
 * The Assistente area of the Windows app, laid out for the desktop: conversation history on the left, the chat at
 * full width in the middle, and the curiosity settings in a side pane on the right when asked for.
 */
@Composable
fun AssistantPanel(state: AssistenteState, playback: AssistantPlayback, reducedMotion: Boolean) {
  val messages by state.messages.collectAsState()
  val processing by state.processing.collectAsState()
  val hasKey by state.hasKey.collectAsState()
  val onlineAi by state.onlineAi.collectAsState()
  val listening by state.listening.collectAsState()
  val voiceError by state.voiceError.collectAsState()
  val timers by state.timers.collectAsState()
  var sidePane by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxSize()) {
    AssistantScreen(
      messages, processing, hasKey, onlineAi, listening, voiceError, timers, playback, reducedMotion,
      onSend = { state.send(it) },
      onMic = state::listenAndSend,
      onCancelTimer = state::cancelTimer,
      onRetry = state::retry,
      onSetOnlineAi = state::setOnlineAi,
      onClear = state::clearConversation,
      onSaveKey = state::saveKey,
      onClearKey = state::clearKey,
      curiosityOpen = sidePane,
      onToggleCuriosity = { sidePane = !sidePane },
      modifier = Modifier.weight(1f)
    )
    if (sidePane) {
      Box(Modifier.width(Grid.u384).fillMaxHeight().background(MaterialTheme.colorScheme.surface).testTag("assistant_side_pane")) {
        CuriositySettingsScreen(state, onBack = { sidePane = false })
      }
    }
  }
}

@Composable
fun AssistantScreen(
  messages: List<AssistantMessage>,
  isProcessing: Boolean,
  hasGeminiKey: Boolean,
  onlineAi: Boolean,
  isListening: Boolean,
  voiceError: String?,
  activeTimers: List<LocalTimer>,
  playback: AssistantPlayback,
  reduced: Boolean,
  onSend: (String) -> Unit,
  onMic: () -> Unit,
  onCancelTimer: (String) -> Unit,
  onRetry: (String) -> Unit,
  onSetOnlineAi: (Boolean) -> Unit,
  onClear: () -> Unit,
  onSaveKey: (String) -> Boolean,
  onClearKey: () -> Unit,
  modifier: Modifier = Modifier,
  curiosityOpen: Boolean = false,
  onToggleCuriosity: () -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val listState = rememberLazyListState()
  var input by remember { mutableStateOf(TextFieldValue("")) }
  val online = hasGeminiKey && onlineAi

  fun send() {
    val text = input.text.trim()
    if (text.isEmpty()) return
    input = TextFieldValue("")
    onSend(text)
  }

  val lastIndex = (messages.size + (if (isProcessing) 1 else 0) - 1).coerceAtLeast(0)
  LaunchedEffect(messages.size, isProcessing) {
    if (messages.isNotEmpty() || isProcessing) {
      if (reduced) listState.scrollToItem(lastIndex) else listState.animateScrollToItem(lastIndex)
    }
  }
  val jumpScope = rememberCoroutineScope()

  val listeningText = stringResource(Res.string.assistant_listening)
  val thinkingText = stringResource(Res.string.assistant_thinking)
  val lastReply = messages.lastOrNull()?.takeIf { it.sender != MessageSender.USER }
  val lastReplySpoken = lastReply?.let {
    stringResource(Res.string.assistant_message_spoken, it.sender.spokenName(), lightMarkdown(it.text).text)
  }
  val announcement = when {
    isListening -> listeningText
    isProcessing -> thinkingText
    else -> lastReplySpoken.orEmpty()
  }
  val suggestions = remember(playback) { SuggestionCommands.filter { it.offeredWhen(playback) } }

  Row(modifier.fillMaxSize()) {
    HistoryPane(
      messages = messages, online = online, hasKey = hasGeminiKey, onlineAi = onlineAi, timers = activeTimers,
      curiosityOpen = curiosityOpen,
      onJump = { index -> jumpScope.launch { listState.scrollToItem(index) } },
      onCancelTimer = onCancelTimer, onSetOnlineAi = onSetOnlineAi, onClear = onClear,
      onSaveKey = onSaveKey, onClearKey = onClearKey, onToggleCuriosity = onToggleCuriosity
    )
    Box(Modifier.width(Grid.u6 / 6).fillMaxHeight().background(colors.outlineVariant))
    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = Grid.margin)) {
      Box(
        Modifier.size(Grid.u6).semantics {
          liveRegion = LiveRegionMode.Polite
          contentDescription = announcement
        }
      )
      Box(Modifier.weight(1f).fillMaxWidth()) {
        SelectionContainer {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Grid.u12, bottom = Grid.u12),
            verticalArrangement = Arrangement.spacedBy(Grid.u12)
          ) {
            items(messages, key = { it.id }) { message -> MessageBubble(message, onRetry, messages) }
            if (isProcessing) item { ThinkingLine(stringResource(Res.string.assistant_thinking_visible)) }
          }
        }
        if (messages.isEmpty()) {
          Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.assistant_empty_title), style = GridText.Title, color = colors.onSurface, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.assistant_empty_body), style = GridText.Body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Grid.u6))
          }
        }
      }
      if (isListening) Text(listeningText, style = GridText.Small, color = colors.primary, modifier = Modifier.padding(bottom = Grid.u6))
      voiceError?.let { Text(it, style = GridText.Small, color = colors.error, modifier = Modifier.padding(bottom = Grid.u6)) }
      Suggestions(suggestions, onSend)
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = Grid.u12, bottom = Grid.u12),
        verticalAlignment = Alignment.Bottom
      ) {
        ComposerField(input, { input = it }, ::send, Modifier.weight(1f))
        IconAction(
          icon = Icons.Rounded.Mic,
          contentDescription = stringResource(if (isListening) Res.string.assistant_listening else Res.string.assistant_mic_idle),
          onClick = onMic,
          tint = if (isListening) colors.primary else colors.onSurface,
          modifier = Modifier.testTag("siri_voice_orb")
        )
        IconAction(
          icon = Icons.Rounded.ArrowUpward,
          contentDescription = stringResource(Res.string.assistant_send),
          onClick = ::send,
          tint = if (input.text.isBlank()) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.primary,
          filled = true,
          size = Grid.u48,
          modifier = Modifier.testTag("siri_send_button")
        )
      }
      Text(KeyTexts.composerHint, style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = Grid.u6))
    }
  }
}

/** Left pane: the questions asked in this conversation (a click scrolls the chat to it) and the controls around it. */
@Composable
private fun HistoryPane(
  messages: List<AssistantMessage>,
  online: Boolean,
  hasKey: Boolean,
  onlineAi: Boolean,
  timers: List<LocalTimer>,
  curiosityOpen: Boolean,
  onJump: (Int) -> Unit,
  onCancelTimer: (String) -> Unit,
  onSetOnlineAi: (Boolean) -> Unit,
  onClear: () -> Unit,
  onSaveKey: (String) -> Boolean,
  onClearKey: () -> Unit,
  onToggleCuriosity: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  var confirmClear by remember { mutableStateOf(false) }
  var keyPanelShown by remember { mutableStateOf(false) }
  val asked = remember(messages) { messages.withIndex().filter { it.value.sender == MessageSender.USER } }
  Column(Modifier.width(Grid.u192).fillMaxHeight().background(colors.surface).padding(Grid.u12).testTag("assistant_history_pane")) {
    Text(KeyTexts.historyTitle, style = GridText.BodyStrong, color = colors.onSurface)
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = Grid.u6)) {
      if (asked.isEmpty()) {
        item { Text(KeyTexts.historyEmpty, style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = Grid.u6)) }
      }
      items(asked, key = { it.value.id }) { (index, message) ->
        val time = remember(message.timestampMs) {
          if (message.timestampMs > 0L) DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(message.timestampMs)) else ""
        }
        Column(
          Modifier.fillMaxWidth().clip(Grid.r6).clickable { onJump(index) }.padding(vertical = Grid.u6, horizontal = Grid.u6)
            .testTag("assistant_history_item")
        ) {
          Text(message.text, style = GridText.Body, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (time.isNotEmpty()) Text(time, style = GridText.Small, color = colors.onSurfaceVariant)
        }
      }
    }
    if (timers.isNotEmpty()) {
      TimerList(timers, onCancelTimer)
      Spacer(Modifier.height(Grid.u6))
    }
    Text(
      stringResource(if (online) Res.string.assistant_status_online else Res.string.assistant_status_local),
      style = GridText.Small, color = colors.onSurfaceVariant
    )
    TextAction(
      text = if (hasKey) KeyTexts.remove else stringResource(if (keyPanelShown) Res.string.assistant_enable_hide else Res.string.assistant_enable_action),
      onClick = { if (hasKey) onClearKey() else keyPanelShown = !keyPanelShown },
      modifier = Modifier.testTag("assistant_enable_help")
    )
    if (!hasKey && keyPanelShown) KeyEntry(onSaveKey)
    if (hasKey) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = Grid.u48)
          .toggleable(value = onlineAi, role = Role.Switch, onValueChange = onSetOnlineAi)
          .testTag("assistant_online_switch"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(stringResource(Res.string.assistant_online_label), style = GridText.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = onlineAi, onCheckedChange = null)
      }
      Text(
        stringResource(if (onlineAi) Res.string.assistant_online_disclosure_on else Res.string.assistant_online_disclosure_off),
        style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = Grid.u6)
      )
    }
    if (asked.isNotEmpty()) {
      ClearConversation(confirmClear, { confirmClear = true }, { confirmClear = false; onClear() }, { confirmClear = false })
    }
    TextAction(
      text = if (curiosityOpen) KeyTexts.curiosityHide else stringResource(Res.string.settings_curiosity_title),
      onClick = onToggleCuriosity,
      modifier = Modifier.testTag("assistant_curiosity_toggle")
    )
  }
}

/** Where the person pastes the Gemini key. The text is never echoed back once it is saved. */
@Composable
private fun KeyEntry(onSave: (String) -> Boolean) {
  val colors = MaterialTheme.colorScheme
  var text by remember { mutableStateOf("") }
  var invalid by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(bottom = Grid.u12)) {
    Text(KeyTexts.title, style = GridText.Body, color = colors.onSurface)
    Text(KeyTexts.body, style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = Grid.u6))
    Box(
      Modifier.fillMaxWidth().heightIn(min = Grid.u48).clip(Grid.r12).background(colors.surfaceContainer).padding(Grid.u12),
      contentAlignment = Alignment.CenterStart
    ) {
      if (text.isEmpty()) Text(KeyTexts.placeholder, style = GridText.Body, color = colors.onSurfaceVariant)
      BasicTextField(
        value = text,
        onValueChange = { text = it; invalid = false },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        textStyle = GridText.Body.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth().testTag("assistant_key_field")
      )
    }
    if (invalid) Text(KeyTexts.invalid, style = GridText.Small, color = colors.error, modifier = Modifier.padding(top = Grid.u6))
    TextAction(
      text = KeyTexts.save,
      onClick = { if (onSave(text)) text = "" else invalid = true },
      enabled = text.isNotBlank(),
      modifier = Modifier.testTag("assistant_key_save")
    )
  }
}

@Composable
private fun TimerList(timers: List<LocalTimer>, onCancel: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(timers.size) {
    while (true) { delay(1000); now = System.currentTimeMillis() }
  }
  Column(Modifier.fillMaxWidth().clip(Grid.r12).background(colors.surface)) {
    timers.forEachIndexed { i, timer ->
      if (i > 0) GroupDivider()
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48).padding(start = Grid.u12, end = Grid.u6),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(if (timer.completed) Icons.Rounded.Alarm else Icons.Rounded.Timer, null, tint = colors.primary, modifier = Modifier.size(Grid.u24))
        Spacer(Modifier.width(Grid.u12))
        Text(
          stringResource(Res.string.assistant_timer_line, timer.label, timer.formattedTime(now)),
          style = GridText.Body, color = colors.onSurface, modifier = Modifier.weight(1f)
        )
        IconAction(Icons.Rounded.Close, stringResource(Res.string.assistant_timer_cancel, timer.label), { onCancel(timer.id) }, tint = colors.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun ClearConversation(confirming: Boolean, onAsk: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  if (!confirming) {
    TextAction(stringResource(Res.string.assistant_clear), onAsk, Modifier.testTag("assistant_clear"))
  } else {
    Row(Modifier.fillMaxWidth().padding(top = Grid.u6), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(Res.string.assistant_clear_confirm), style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
      TextAction(stringResource(Res.string.assistant_clear_no), onCancel)
      TextAction(stringResource(Res.string.assistant_clear_yes), onConfirm, Modifier.testTag("assistant_clear_yes"))
    }
  }
}

@Composable
private fun ThinkingLine(label: String) {
  Text(label, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Grid.u12).semantics { contentDescription = label })
}

@Composable
private fun MessageBubble(message: AssistantMessage, onRetry: (String) -> Unit, all: List<AssistantMessage>) {
  val clipboard = LocalClipboardManager.current
  val colors = MaterialTheme.colorScheme
  val isUser = message.sender == MessageSender.USER
  val text = lightMarkdown(message.text)
  val spoken = stringResource(Res.string.assistant_message_spoken, message.sender.spokenName(), text.text)
  val time = remember(message.timestampMs) {
    if (message.timestampMs > 0L) DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(message.timestampMs)) else ""
  }
  val shape = RoundedCornerShape(
    topStart = Grid.u24, topEnd = Grid.u24,
    bottomStart = if (isUser) Grid.u24 else Grid.u6, bottomEnd = if (isUser) Grid.u6 else Grid.u24
  )
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bubbleMax = maxWidth * 3f / 4f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
      ContextMenuArea(items = {
        listOf(
          ContextMenuItem(KeyTexts.copyMessage) { clipboard.setText(AnnotatedString(text.text)) },
          ContextMenuItem(KeyTexts.copyAll) { clipboard.setText(AnnotatedString(all.joinToString("\n\n") { it.text })) }
        )
      }) {
        Text(
          text = text, style = GridText.Body,
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
        Text(time, style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u6, start = Grid.u6, end = Grid.u6))
      }
      message.toolReport?.let { report ->
        Spacer(Modifier.height(Grid.u6))
        ToolReportCard(report, Modifier.widthIn(max = bubbleMax))
      }
      if (message.failure?.retryable == true) {
        TextAction(stringResource(Res.string.assistant_retry), { onRetry(message.id) }, Modifier.testTag("assistant_retry"))
      }
    }
  }
}

/** What an action did, in a quiet card under the reply. */
@Composable
internal fun ToolReportCard(report: ToolExecutionReport, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(Grid.r12)
      .background(colors.surfaceContainer)
      .padding(horizontal = Grid.u12, vertical = Grid.u6)
      .semantics(mergeDescendants = true) {}
      .testTag("tool_report_${report.toolName}"),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      if (report.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
      contentDescription = stringResource(if (report.success) Res.string.player_common_done else Res.string.player_common_not_done),
      tint = if (report.success) colors.primary else colors.error,
      modifier = Modifier.size(Grid.icon)
    )
    Spacer(Modifier.width(Grid.u6))
    Column {
      Text(stringResource(toolTitle(report.toolName, report.success)), style = GridText.Small.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
      if (report.resultSummary.isNotBlank()) Text(report.resultSummary, style = GridText.Small, color = colors.onSurfaceVariant)
    }
  }
}

internal fun toolTitle(toolName: String, success: Boolean): StringResource = when (toolName) {
  "analyze_and_research_music" -> Res.string.player_common_tool_research
  "get_current_track" -> Res.string.player_common_tool_current_track
  "get_audio_capabilities" -> Res.string.player_common_tool_audio_capabilities
  "apply_audio_profile" -> Res.string.player_common_tool_audio_profile
  "create_timer" -> Res.string.player_common_tool_timer_created
  "cancel_timer" -> Res.string.player_common_tool_timer_cancelled
  else -> if (success) Res.string.player_common_tool_done else Res.string.player_common_tool_failed
}

@Composable
private fun Suggestions(suggestions: List<Suggestion>, onSend: (String) -> Unit) {
  val colors = MaterialTheme.colorScheme
  LazyRow(horizontalArrangement = Arrangement.spacedBy(Grid.u12), modifier = Modifier.fillMaxWidth()) {
    items(suggestions) { suggestion ->
      val command = stringResource(suggestion.command)
      Box(
        modifier = Modifier
          .heightIn(min = Grid.u48)
          .clip(Shapes.pill)
          .highlightClickable(onClick = { onSend(command) })
          .background(colors.surfaceContainer)
          .padding(horizontal = Grid.u12, vertical = Grid.u12)
          .testTag("assistant_action_${command.take(8)}"),
        contentAlignment = Alignment.Center
      ) {
        Text(stringResource(suggestion.label), style = GridText.Body, color = colors.onSurface, maxLines = 1)
      }
    }
  }
}

@Composable
private fun ComposerField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val placeholder = stringResource(Res.string.assistant_composer_placeholder)
  val fieldDescription = stringResource(Res.string.assistant_composer_description)
  Box(
    modifier = modifier.heightIn(min = Grid.u48).clip(Grid.r12).background(colors.surfaceContainer).padding(horizontal = Grid.u12, vertical = Grid.u12),
    contentAlignment = Alignment.CenterStart
  ) {
    if (value.text.isEmpty()) Text(placeholder, style = GridText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      maxLines = 8,
      textStyle = GridText.Body.copy(color = colors.onSurface),
      cursorBrush = SolidColor(colors.primary),
      modifier = Modifier.fillMaxWidth().testTag("siri_text_input")
        .onPreviewKeyEvent { event ->
          val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
          when {
            !enter -> false
            event.type != KeyEventType.KeyDown -> true
            event.isShiftPressed -> { onValueChange(insertNewLine(value)); true }
            else -> { onSend(); true }
          }
        }
        .semantics { contentDescription = fieldDescription }
    )
  }
}

/** Shift+Enter: a line break at the cursor, replacing the selection. */
internal fun insertNewLine(value: TextFieldValue): TextFieldValue {
  val start = value.selection.min
  val end = value.selection.max
  return TextFieldValue(value.text.replaceRange(start, end, "\n"), TextRange(start + 1))
}

/** Renders **bold** and *italic* markers instead of showing the asterisks. */
internal fun lightMarkdown(text: String): AnnotatedString = buildAnnotatedString {
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
