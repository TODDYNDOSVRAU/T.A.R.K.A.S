package com.example.windows.geral

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** D8: the nickname field saves once per edit (Enter, focus loss, page closed), never once per keystroke. */
@OptIn(ExperimentalTestApi::class)
class NicknameFieldUiTest {
  @Test fun showsTheWindowsNameAsPlaceholderAndSavesOnEnter() = runComposeUiTest {
    val saves = mutableListOf<String>()
    setContent { TarkasTheme { NicknameField(saved = "", fallback = "Todyn", onCommit = { saves += it }) } }
    onNodeWithText("Todyn").assertIsDisplayed()          // empty field shows who the greeting falls back to
    onNodeWithText("0/24").assertIsDisplayed()
    onNodeWithTag("settings_nickname").performClick()
    onNodeWithTag("settings_nickname").performTextInput("Ana")
    onNodeWithText("3/24").assertIsDisplayed()
    // Typing alone does not save; only a commit that changes something reaches the state.
    assertEquals(emptyList<String>(), saves)
    onNodeWithTag("settings_nickname").performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
    assertEquals("Ana", saves.last())
  }

  @Test fun closingThePageSavesWhatWasTyped() = runComposeUiTest {
    val saves = mutableListOf<String>()
    var shown by mutableStateOf(true)
    setContent { TarkasTheme { if (shown) NicknameField(saved = "", fallback = "", onCommit = { saves += it }) } }
    onNodeWithTag("settings_nickname").performClick()
    onNodeWithTag("settings_nickname").performTextInput("Lu")
    shown = false
    waitForIdle()
    assertEquals(saves.toString(), "Lu", saves.last())
  }

  @Test fun inputIsCappedAtTheLimit() = runComposeUiTest {
    setContent { TarkasTheme { NicknameField(saved = "", fallback = "", onCommit = {}) } }
    onNodeWithTag("settings_nickname").performClick()
    onNodeWithTag("settings_nickname").performTextInput("x".repeat(60))
    onNodeWithText("24/24").assertIsDisplayed()
  }
}

class CommitGateTest {
  @Test fun sendsChangesOnceAndNothingAfterClose() {
    val sent = mutableListOf<String>()
    val g = CommitGate("")
    g.offer("") { sent += it }          // unchanged: nothing
    g.offer("Ana") { sent += it }
    g.offer("Ana") { sent += it }       // repeat: nothing
    g.sync("Lu")                        // restored elsewhere
    g.offer("Lu") { sent += it }        // equals the new baseline: nothing
    g.close()
    g.offer("") { sent += it }          // after close: nothing
    assertEquals(listOf("Ana"), sent)
  }
}
