package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Track
import com.example.ui.components.QueueItemMenu
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The menu of one queue row: a button that opens a sheet with move up, move down and remove. */
abstract class QueueItemMenuUiBase(
  private val buttonLabel: String,
  private val moveUp: String,
  private val moveDown: String,
  private val remove: String
) {
  @get:Rule val rule = createComposeRule()

  private val track = Track("t1", "Song", "Artist", "Album", 180_000L, "Rock", "2000s")

  private fun show(canUp: Boolean, canDown: Boolean, calls: MutableList<String>): ThemeSwitch =
    rule.setTarkasContent {
      QueueItemMenu(
        track = track,
        canMoveUp = canUp,
        canMoveDown = canDown,
        onMoveUp = { calls += "up" },
        onMoveDown = { calls += "down" },
        onRemove = { calls += "remove" }
      )
    }

  private fun openSheet() {
    rule.onNodeWithTag("queue_menu_t1").performClick()
    rule.settle()
  }

  @Test fun buttonIsLabelledInTheLanguageAndOpensTheSheet() {
    val theme = show(canUp = true, canDown = true, calls = mutableListOf())
    rule.onNodeWithContentDescription(buttonLabel).assertIsDisplayed()
    rule.onNodeWithTag("queue_menu_up").assertDoesNotExist()
    openSheet()
    rule.forEachTheme(theme) {
      rule.onNodeWithText(moveUp).assertIsDisplayed()
      rule.onNodeWithText(moveDown).assertIsDisplayed()
      rule.onNodeWithText(remove).assertIsDisplayed()
    }
  }

  @Test fun removeCallsTheCallbackOnceAndClosesTheSheet() {
    val calls = mutableListOf<String>()
    show(canUp = true, canDown = true, calls = calls)
    openSheet()
    rule.onNodeWithTag("queue_menu_remove").performClick()
    rule.settle()
    assertEquals(listOf("remove"), calls)
    rule.onNodeWithTag("queue_menu_remove").assertDoesNotExist()
  }

  @Test fun moveButtonsFollowTheirEnabledFlags() {
    val calls = mutableListOf<String>()
    show(canUp = false, canDown = true, calls = calls)
    openSheet()
    rule.onNodeWithTag("queue_menu_up").assertIsNotEnabled()
    rule.onNodeWithTag("queue_menu_down").assertIsEnabled()
    rule.onNodeWithTag("queue_menu_up").performClick()
    assertEquals(emptyList<String>(), calls)
    rule.onNodeWithTag("queue_menu_down").performClick()
    rule.settle()
    assertEquals(listOf("down"), calls)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class QueueItemMenuEnUiTest : QueueItemMenuUiBase("Queue options for Song", "Move up", "Move down", "Remove from queue")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class QueueItemMenuPtUiTest : QueueItemMenuUiBase("Opções da fila para Song", "Mover para cima", "Mover para baixo", "Remover da fila")
