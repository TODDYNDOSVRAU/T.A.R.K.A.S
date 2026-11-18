package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.example.ui.theme.TarkasTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals

/** The theme modes the app saves, in the order the tests run them. */
val UiThemeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)

/** The saved theme mode shown by [setTarkasContent], and the background colour the theme resolved for it. */
class ThemeSwitch(initial: String) {
  var mode by mutableStateOf(initial)
  var background: Color = Color.Unspecified
}

/**
 * Shows [content] inside [TarkasTheme] with the saved theme mode, the way the app does. The animation
 * clock is frozen (`autoAdvance = false`): the test moves it with `mainClock.advanceTimeBy`.
 */
fun ComposeContentTestRule.setTarkasContent(initial: String = ThemeMode.LIGHT, content: @Composable () -> Unit): ThemeSwitch {
  mainClock.autoAdvance = false
  val switch = ThemeSwitch(initial)
  setContent {
    TarkasTheme(themeMode = switch.mode) {
      // Read the colour where the theme is applied, after every composition, so a switch is seen.
      val background = MaterialTheme.colorScheme.background
      androidx.compose.runtime.SideEffect { switch.background = background }
      content()
    }
  }
  return switch
}

/**
 * Runs [check] once per theme mode on the same composition: switches the mode, lets the frozen clock
 * move 500 ms, asserts the theme really changed (light mode draws a light background, dark and
 * AMOLED a dark one) and then calls [check].
 */
fun ComposeContentTestRule.forEachTheme(switch: ThemeSwitch, check: (mode: String) -> Unit) {
  for (mode in UiThemeModes) {
    runOnIdle { switch.mode = mode }
    // Each pass needs a frame to recompose before the clock can move the transition.
    repeat(4) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()
    assertEquals("background of $mode (${switch.background}, mode ${switch.mode})", mode != ThemeMode.LIGHT, switch.background.luminance() < 0.5f)
    check(mode)
  }
}

/** Whether a node with this test tag is in the composition. */
fun ComposeContentTestRule.hasNodeWithTag(tag: String): Boolean =
  onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

/** Lets a sheet or dialog open: a few frames of the frozen clock, each followed by the idle wait. */
fun ComposeContentTestRule.settle() {
  repeat(4) {
    mainClock.advanceTimeBy(500)
    waitForIdle()
  }
}
