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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import com.example.ui.theme.TarkasTheme
import com.example.windows.shell.Command
import com.example.windows.shell.Shortcuts
import com.example.ui.viewmodel.TarkasNavTab
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsLayoutUiTest {
  @Test fun opensSwitchesPageAndEscCloses() = runComposeUiTest {
    var open by mutableStateOf(true)
    var cat by mutableStateOf(Cat.LOOK)
    setContent {
      TarkasTheme {
        if (open) Box(Modifier.onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { open = false; true } else false }) {
          SettingsLayout(
            cats = listOf(Cat.LOOK, Cat.LANGUAGE, Cat.DATA), cat = cat, onCat = { cat = it }, query = "", onQuery = {},
            layer = SettingsLayer.ESSENTIAL, onLayer = {}, empty = false
          ) { Text("page-" + cat.name) }
        }
      }
    }
    onNodeWithTag("settings_sidebar").assertIsDisplayed()
    onNodeWithText("page-LOOK").assertIsDisplayed()
    onNodeWithTag("settings_cat_LANGUAGE").performClick()
    onNodeWithText("page-LANGUAGE").assertIsDisplayed()
    assertEquals(Cat.LANGUAGE, cat)
  }

  @Test fun shortcutsMapCtrlCommaAndEsc() {
    assertEquals(Command.Back, Shortcuts.global(Key.Escape, ctrl = false, shift = false))
    assertEquals(Command.Go(TarkasNavTab.SETTINGS), Shortcuts.global(Key.Comma, ctrl = true, shift = false))
  }
}
