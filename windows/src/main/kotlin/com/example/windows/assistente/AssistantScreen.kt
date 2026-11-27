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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.WidthClass
import com.example.windows.shell.LocalCornerInsets
import com.example.ui.theme.studioCard
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Shape
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
  val quick get() = if (pt) "Pedidos rápidos" else "Quick requests"
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
      Box(
        Modifier.padding(end = Grid.margin, top = Grid.u12, bottom = LocalBarInset.current + Grid.u12)
          .width(Grid.u384).fillMaxHeight().studioCard().testTag("assistant_side_pane")
      ) {
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
  // Keyed on the last message id, not the count: the history is capped, so the count stops changing at the limit.
  LaunchedEffect(messages.lastOrNull()?.id, isProcessing) {
    if (messages.isNotEmpty() || isProcessing) {
      if (reduced) listState.scrollToItem(lastIndex) else listState.animateScrollToItem(lastIndex)
    }
  }
  val jumpScope = rememberCoroutineScope()

  val listeningText = stringResource(Res.string.assistant_listening)
  val thinkingText = stringResource(Res.string.assistant_thinking)
  val lastReply = messages.lastOrNull()?.takeIf { it.sender != MessageSender.USER }
  val lastReplySpoken = lastReply?.let {
    stringResource(
      Res.string.assistant_message_spoken, it.sender.spokenName(),
      lightMarkdown(it.failure?.let { f -> stringResource(failureResource(f)) } ?: it.text).text
    )
  }
  val announcement = when {
    isListening -> listeningText
    isProcessing -> thinkingText
    else -> lastReplySpoken.orEmpty()
  }
  val suggestions = remember(playback) { SuggestionCommands.filter { it.offeredWhen(playback) } }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val width = WidthClass.of(maxWidth)
    val compact = width == WidthClass.COMPACT
    // The mini-player floats over the bottom-left corner. When the input would start under it, the chat column
    // stops above it (the pages' LocalBarInset rule); wide windows, where the column starts after the lane, keep the full height.
    val leftPane = if (width == WidthClass.EXPANDED) HistoryLeft else 0.dp
    val rest = maxWidth - leftPane
    val inputLeft = leftPane + (rest - minOf(rest, ChatMaxWidth)) / 2 + Grid.margin
    val lift = if (inputLeft < LocalCornerInsets.current.lane) LocalBarInset.current else 0.dp
    Row(Modifier.fillMaxSize()) {
      if (width == WidthClass.EXPANDED) {
        HistoryPane(messages) { index -> jumpScope.launch { listState.scrollToItem(index) } }
      }
      Column(Modifier.weight(1f).fillMaxHeight()) {
        TopBar(
          online = online, hasKey = hasGeminiKey, onlineAi = onlineAi, hasMessages = messages.isNotEmpty(),
          curiosityOpen = curiosityOpen, onSetOnlineAi = onSetOnlineAi, onClear = onClear,
          onSaveKey = onSaveKey, onClearKey = onClearKey, onToggleCuriosity = onToggleCuriosity
        )
        Column(
          Modifier.weight(1f).widthIn(max = ChatMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally)
            .testTag("assistant_chat_column").padding(start = Grid.margin, end = Grid.margin, bottom = lift)
        ) {
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
            if (messages.isEmpty()) EmptyState(suggestions.take(4), compact, onSend, Modifier.align(Alignment.Center))
          }
          if (activeTimers.isNotEmpty()) {
            TimerList(activeTimers, onCancelTimer)
            Spacer(Modifier.height(Grid.u6))
          }
          if (isListening) Text(listeningText, style = GridText.Small, color = colors.primary, modifier = Modifier.padding(bottom = Grid.u6))
          voiceError?.let { Text(it, style = GridText.Small, color = colors.error, modifier = Modifier.padding(bottom = Grid.u6)) }
          Capsule(
            input = input, onInput = { input = it }, onSend = ::send, onMic = onMic, isListening = isListening,
            suggestions = suggestions, onQuick = onSend, button = if (compact) CapsuleButtonCompact else CapsuleButton
          )
          Text(KeyTexts.composerHint, style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u12))
        }
      }
    }
  }
}

private val ChatMaxWidth = 760.dp
/** Where the chat region starts when the history card is shown: 24 margin + 192 card + 12 gap. */
private val HistoryLeft = Grid.margin + Grid.u192 + Grid.u12
private val CapsuleButton = 40.dp
private val CapsuleButtonCompact = 36.dp

/** One line, at least 40 dp: the status pill (opens the key, online and curiosity controls) and Clear with Yes/No. */
@Composable
private fun TopBar(
  online: Boolean,
  hasKey: Boolean,
  onlineAi: Boolean,
  hasMessages: Boolean,
  curiosityOpen: Boolean,
  onSetOnlineAi: (Boolean) -> Unit,
  onClear: () -> Unit,
  onSaveKey: (String) -> Boolean,
  onClearKey: () -> Unit,
  onToggleCuriosity: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  var expanded by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }
  var keyPanelShown by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u6)) {
    Row(Modifier.fillMaxWidth().heightIn(min = CapsuleButton), verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.heightIn(min = CapsuleButton).clip(Shapes.pill).background(colors.surfaceContainer).edge(Shapes.pill)
          .clickable { expanded = !expanded }.padding(horizontal = Grid.u12).testTag("assistant_status_pill"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(if (online) Res.string.assistant_status_online else Res.string.assistant_status_local),
          style = GridText.Small, color = if (online) colors.primary else colors.onSurfaceVariant
        )
        Icon(
          if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
          tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = Grid.u6).size(18.dp)
        )
      }
      Spacer(Modifier.weight(1f))
      if (hasMessages) ClearConversation(confirmClear, { confirmClear = true }, { confirmClear = false; onClear() }, { confirmClear = false })
    }
    if (expanded) {
      Column(Modifier.fillMaxWidth().padding(top = Grid.u6).studioCard().padding(Grid.u12).testTag("assistant_status_panel")) {
        TextAction(
          text = if (hasKey) KeyTexts.remove else stringResource(if (keyPanelShown) Res.string.assistant_enable_hide else Res.string.assistant_enable_action),
          onClick = { if (hasKey) onClearKey() else keyPanelShown = !keyPanelShown },
          modifier = Modifier.testTag("assistant_enable_help")
        )
        if (!hasKey && keyPanelShown) KeyEntry(onSaveKey)
        if (hasKey) {
          Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48)
              .toggleable(value = onlineAi, role = Role.Switch, onValueChange = onSetOnlineAi).testTag("assistant_online_switch"),
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
        TextAction(
          text = if (curiosityOpen) KeyTexts.curiosityHide else stringResource(Res.string.settings_curiosity_title),
          onClick = onToggleCuriosity,
          modifier = Modifier.testTag("assistant_curiosity_toggle")
        )
      }
    }
  }
}

/** Left pane at the widest size: the questions asked here; a click scrolls the chat to the question. */
@Composable
private fun HistoryPane(messages: List<AssistantMessage>, onJump: (Int) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val asked = remember(messages) { messages.withIndex().filter { it.value.sender == MessageSender.USER } }
  Column(
    Modifier.padding(start = Grid.margin, top = Grid.u12, end = Grid.u12, bottom = LocalBarInset.current + Grid.u12)
      .width(Grid.u192).fillMaxHeight().studioCard().testTag("assistant_history_pane").padding(Grid.u12)
  ) {
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
  }
}

/** Empty chat: the title and up to four quick-request cards, three per row (two when compact). */
@Composable
private fun EmptyState(cards: List<Suggestion>, compact: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(stringResource(Res.string.assistant_empty_title), style = GridText.Title, color = colors.onSurface, textAlign = TextAlign.Center)
    Text(stringResource(Res.string.assistant_empty_body), style = GridText.Body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u24))
    val columns = if (compact) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(Grid.u12), modifier = Modifier.testTag("assistant_empty_cards")) {
      cards.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12), modifier = Modifier.fillMaxWidth()) {
          row.forEach { card ->
            val command = stringResource(card.command)
            Column(
              Modifier.weight(1f).heightIn(min = Grid.u48 + Grid.u12).studioCard().clickable { onSend(command) }.padding(Grid.u12)
                .testTag("assistant_card_${command.take(8)}")
            ) {
              Text(stringResource(card.label), style = GridText.BodyStrong, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
              val label = stringResource(card.label)
              // The sent request is the subtitle only when it says more than the title does (not "Repetir / repetir").
              if (!command.equals(label, ignoreCase = true)) {
                Text(command, style = GridText.Small, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
            }
          }
          repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

/** The input capsule: + (quick requests), the field, the microphone and send. Enter sends. */
@Composable
private fun Capsule(
  input: TextFieldValue,
  onInput: (TextFieldValue) -> Unit,
  onSend: () -> Unit,
  onMic: () -> Unit,
  isListening: Boolean,
  suggestions: List<Suggestion>,
  onQuick: (String) -> Unit,
  button: Dp
) {
  val colors = MaterialTheme.colorScheme
  var menu by remember { mutableStateOf(false) }
  Row(
    Modifier.fillMaxWidth().studioCard(26.dp).padding(3.dp).testTag("assistant_capsule"),
    verticalAlignment = Alignment.Bottom
  ) {
    Box {
      CapsuleIcon(Icons.Rounded.Add, KeyTexts.quick, { menu = true }, colors.onSurface, button, Modifier.testTag("assistant_quick_button"))
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        suggestions.forEach { s ->
          val command = stringResource(s.command)
          DropdownMenuItem(text = { Text(stringResource(s.label), style = GridText.Body) }, onClick = { menu = false; onQuick(command) })
        }
      }
    }
    ComposerField(input, onInput, onSend, Modifier.weight(1f).heightIn(min = button))
    CapsuleIcon(
      Icons.Rounded.Mic,
      stringResource(if (isListening) Res.string.assistant_listening else Res.string.assistant_mic_idle),
      onMic, if (isListening) colors.primary else colors.onSurface, button, Modifier.testTag("siri_voice_orb")
    )
    CapsuleIcon(
      Icons.Rounded.ArrowUpward, stringResource(Res.string.assistant_send), onSend,
      if (input.text.isBlank()) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.onPrimary, button,
      Modifier.testTag("siri_send_button"), fill = if (input.text.isBlank()) null else colors.primary
    )
  }
}

@Composable
private fun CapsuleIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color, size: Dp, modifier: Modifier = Modifier, fill: Color? = null) {
  Box(
    modifier.size(size).clip(CircleShape).let { if (fill != null) it.background(fill) else it }
      .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
    contentAlignment = Alignment.Center
  ) { Icon(icon, null, tint = tint, modifier = Modifier.size(Grid.u24)) }
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
  // A failed online reply is stored without text; it is named here, in the language shown now.
  val text = lightMarkdown(message.failure?.let { stringResource(failureResource(it)) } ?: message.text)
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
            .let { if (isUser) it else it.edge(shape) }
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
      .edge(Grid.r12)
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
          .edge(Shapes.pill)
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
    modifier = modifier.padding(horizontal = Grid.u12, vertical = Grid.u6),
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

/**
 * The hairline the cards of the app carry (`studioCard`): in the light theme the container surface is nearly the
 * background (1.03:1), so without an edge the bubbles, chips and the status pill vanish into the page.
 */
@Composable
private fun Modifier.edge(shape: Shape): Modifier = border(Grid.hairline, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
