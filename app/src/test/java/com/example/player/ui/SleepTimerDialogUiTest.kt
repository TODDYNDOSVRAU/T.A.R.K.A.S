package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.playback.SleepTimer
import com.example.ui.components.SleepTimerDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sleep timer dialog: a list of options, a "turn off" button only while a timer runs, and a close button. */
abstract class SleepTimerDialogUiBase(
  private val title: String,
  private val thirtyMinutes: String,
  private val endOfTrack: String,
  private val turnOff: String,
  private val close: String
) {
  @get:Rule val rule = createComposeRule()

  private fun show(timer: SleepTimer?, calls: MutableList<String>): ThemeSwitch =
    rule.setTarkasContent {
      SleepTimerDialog(
        timer = timer,
        onMinutes = { calls += "minutes:$it" },
        onEndOfTrack = { calls += "end" },
        onCancel = { calls += "cancel" },
        onDismiss = { calls += "dismiss" }
      )
    }

  // The option list is capped at half the window height (the default Robolectric screen is small), so the last
  // option is reached by scrolling, as a user would.
  @Test fun showsTheOptionsInTheLanguageInEveryTheme() {
    val theme = show(timer = null, calls = mutableListOf())
    rule.settle()
    rule.forEachTheme(theme) {
      rule.onNodeWithText(title).assertIsDisplayed()
      rule.onNodeWithTag("sleep_slider").assertIsDisplayed()
      rule.onNodeWithText(thirtyMinutes).assertIsDisplayed()
      rule.onNodeWithTag("sleep_start").assertIsDisplayed()
      rule.onNodeWithText(endOfTrack).assertIsDisplayed()
      rule.onNodeWithText(close).assertIsDisplayed()
      // No timer is running, so there is nothing to turn off.
      rule.onNodeWithText(turnOff).assertDoesNotExist()
    }
  }

  @Test fun startingWithTheSliderAtItsDefaultCallsTheCallbackThenDismisses() {
    val calls = mutableListOf<String>()
    show(timer = null, calls = calls)
    rule.settle()
    rule.onNodeWithTag("sleep_start").performClick()
    assertEquals(listOf("minutes:30", "dismiss"), calls)
  }

  @Test fun endOfTrackAndCloseCallTheirCallbacks() {
    val calls = mutableListOf<String>()
    show(timer = null, calls = calls)
    rule.settle()
    rule.onNodeWithTag("sleep_end_of_track").performClick()
    rule.onNodeWithText(close).performClick()
    assertEquals(listOf("end", "dismiss", "dismiss"), calls)
  }

  @Test fun runningTimerOffersTurnOff() {
    val calls = mutableListOf<String>()
    show(timer = SleepTimer(endOfTrack = true), calls = calls)
    rule.settle()
    rule.onNodeWithText(turnOff).assertIsDisplayed().performClick()
    assertEquals(listOf("cancel", "dismiss"), calls)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h1400dp")
class SleepTimerDialogEnUiTest : SleepTimerDialogUiBase(
  title = "Sleep timer", thirtyMinutes = "30 minutes", endOfTrack = "At the end of this track",
  turnOff = "Turn off timer", close = "Close"
)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h1400dp")
class SleepTimerDialogPtUiTest : SleepTimerDialogUiBase(
  title = "Timer de sono", thirtyMinutes = "30 minutos", endOfTrack = "No fim desta faixa",
  turnOff = "Desligar timer", close = "Fechar"
)
