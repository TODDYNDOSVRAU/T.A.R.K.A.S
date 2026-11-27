package com.example.windows.geral

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.example.ui.theme.Grid
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ajustes search reads like the Biblioteca's: placeholder while empty, a 48 dp clear button once there is text. */
@OptIn(ExperimentalTestApi::class)
class SearchFieldUiTest {
  @Test fun clearAppearsWithTextAndEmptiesTheQuery() = runComposeUiTest {
    var q by mutableStateOf("")
    setContent { TarkasTheme { SearchField(q, { q = it }, "Buscar nos ajustes") } }
    onNodeWithText("Buscar nos ajustes").assertIsDisplayed()
    onNodeWithTag("settings_search_clear").assertDoesNotExist()      // nothing to clear yet
    onNodeWithTag("settings_search_input").performClick()
    onNodeWithTag("settings_search_input").performTextInput("tema")
    waitForIdle()
    assertEquals("tema", q)
    onNodeWithTag("settings_search_clear").assertWidthIsEqualTo(Grid.u48).assertHeightIsEqualTo(Grid.u48)
    onNodeWithTag("settings_search_clear").performClick()
    waitForIdle()
    assertEquals("", q)
    onNodeWithTag("settings_search_clear").assertDoesNotExist()
    onNodeWithText("Buscar nos ajustes").assertIsDisplayed()          // the placeholder comes back
  }
}
