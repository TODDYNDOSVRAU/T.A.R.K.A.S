package com.example.windows.assistente

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AssistantScreenUiTest {
  private fun host(sent: MutableList<String>) = @androidx.compose.runtime.Composable {
    TarkasTheme {
      AssistantScreen(
        messages = emptyList(), isProcessing = false, hasGeminiKey = false, onlineAi = true, isListening = false,
        voiceError = null, activeTimers = emptyList(), playback = AssistantPlayback.NONE, reduced = true,
        onSend = { sent += it }, onMic = {}, onCancelTimer = {}, onRetry = {}, onSetOnlineAi = {}, onClear = {},
        onSaveKey = { true }, onClearKey = {}
      )
    }
  }

  @Test fun enterSendsAndShiftEnterBreaksTheLine() = runComposeUiTest {
    val sent = mutableListOf<String>()
    setContent(host(sent))
    onNodeWithTag("assistant_history_pane").assertExists()
    onNodeWithTag("siri_text_input").performTextInput("one")
    onNodeWithTag("siri_text_input").performKeyInput {
      keyDown(Key.ShiftLeft); pressKey(Key.Enter); keyUp(Key.ShiftLeft)
    }
    onNodeWithTag("siri_text_input").performTextInput("two")
    onNodeWithTag("siri_text_input").performKeyInput { pressKey(Key.Enter) }
    assertEquals(listOf("one\ntwo"), sent)
  }

  @Test fun emptyStateShowsFourCardsAndTheCapsule() = runComposeUiTest {
    setContent(host(mutableListOf()))
    onNodeWithTag("assistant_empty_cards").assertExists()
    onAllNodes(SemanticsMatcher("card") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("assistant_card_") == true })
      .assertCountEquals(4)
    onNodeWithTag("assistant_quick_button").assertExists()
    onNodeWithTag("siri_voice_orb").assertExists()
    onNodeWithTag("siri_send_button").assertExists()
  }

  @Test fun statusPillOpensTheControlsPanel() = runComposeUiTest {
    setContent(host(mutableListOf()))
    onAllNodesWithTag("assistant_status_panel").assertCountEquals(0)
    onNodeWithTag("assistant_status_pill").performClick()
    onNodeWithTag("assistant_status_panel").assertExists()
    onNodeWithTag("assistant_curiosity_toggle").assertExists()
  }
}
