package com.example.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.ui.components.UpdateNotes
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The release notes block of the update popup. It has no callback: the tests cover what it draws. */
abstract class UpdateNotesUiBase(private val expectedTitle: String) {
  @get:Rule val rule = createComposeRule()

  @Test fun showsTheTitleAndNotesAsPlainText() {
    val theme = rule.setTarkasContent {
      UpdateNotes(rawNotes = "## Changes\n- **Faster** library\n- Lyrics", tag = "v1.17", maxHeight = 160.dp, modifier = Modifier)
    }
    rule.settle()
    rule.forEachTheme(theme) {
      rule.onNodeWithTag("update_notes").assertIsDisplayed()
      rule.onNodeWithText(expectedTitle).assertIsDisplayed()
      // Markdown marks are gone: the heading hashes and the bold stars, with dashes turned into bullets.
      rule.onNodeWithText("Changes\n• Faster library\n• Lyrics").assertIsDisplayed()
    }
  }

  @Test fun emptyNotesDrawNothing() {
    rule.setTarkasContent { UpdateNotes(rawNotes = "  \n ", tag = "v1.17", maxHeight = 160.dp) }
    rule.settle()
    assertFalse(rule.hasNodeWithTag("update_notes"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class UpdateNotesEnUiTest : UpdateNotesUiBase("What is new in v1.17")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class UpdateNotesPtUiTest : UpdateNotesUiBase("Novidades em v1.17")
