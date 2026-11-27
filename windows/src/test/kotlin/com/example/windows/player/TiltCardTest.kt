package com.example.windows.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The composable over a real pointer: it leans, it sleeps once settled, and it never eats a click. */
@OptIn(ExperimentalTestApi::class)
class TiltCardTest {
  @Test fun hoveringLeansTheCardAndLeavingBringsItBack() = runDesktopComposeUiTest(width = 300, height = 300) {
    val e = TiltEngine()
    setContent { TarkasTheme { TiltCard(Modifier.size(192.dp), engine = e) { Box(Modifier.fillMaxSize()) } } }
    onNodeWithTag("tilt_card").performMouseInput { moveTo(Offset(190f, 2f)) }
    mainClock.advanceTimeBy(1500); waitForIdle()
    assertTrue("ry ${e.ry.x}", e.ry.x > StackTuning.MaxRy * 0.8f)
    assertTrue("rx ${e.rx.x}", e.rx.x > StackTuning.MaxRx * 0.8f)
    onNodeWithTag("tilt_card").performMouseInput { moveTo(Offset(-5f, -5f)) }
    mainClock.advanceTimeBy(3000); waitForIdle()
    assertEquals(0f, e.rx.x, 0.02f)
    assertEquals(0f, e.ry.x, 0.02f)
  }

  @Test fun noFramesRunOnceTheSpringsAreAtRest() = runDesktopComposeUiTest(width = 300, height = 300) {
    val e = TiltEngine()
    setContent { TarkasTheme { TiltCard(Modifier.size(192.dp), engine = e) { Box(Modifier.fillMaxSize()) } } }
    mainClock.advanceTimeBy(1000); waitForIdle()
    val idle = e.frames
    mainClock.advanceTimeBy(2000); waitForIdle()
    assertEquals("a card nobody touches asks for no frame", idle, e.frames)
    onNodeWithTag("tilt_card").performMouseInput { moveTo(Offset(190f, 2f)) }
    mainClock.advanceTimeBy(4000); waitForIdle()
    val settled = e.frames
    assertTrue("it ran while moving", settled > idle)
    mainClock.advanceTimeBy(2000); waitForIdle()
    assertEquals("and stopped at rest", settled, e.frames)
  }

  @Test fun reducedMotionKeepsTheCardFlat() = runDesktopComposeUiTest(width = 300, height = 300) {
    val e = TiltEngine()
    setContent { TarkasTheme { TiltCard(Modifier.size(192.dp), reduced = true, engine = e) { Box(Modifier.fillMaxSize()) } } }
    onNodeWithTag("tilt_card").performMouseInput { moveTo(Offset(190f, 2f)) }
    mainClock.advanceTimeBy(1500); waitForIdle()
    assertEquals(0f, e.rx.x, 0f)
    assertEquals(0f, e.ry.x, 0f)
  }

  @Test fun aClickInsideStillArrivesWhileTheCardLeans() = runDesktopComposeUiTest(width = 300, height = 300) {
    var clicks = 0
    val e = TiltEngine()
    setContent {
      TarkasTheme { TiltCard(Modifier.size(192.dp), engine = e) { Box(Modifier.fillMaxSize().testTag("inner").clickable { clicks++ }) } }
    }
    onNodeWithTag("tilt_card").performMouseInput { moveTo(Offset(150f, 40f)) }
    mainClock.advanceTimeBy(500); waitForIdle()
    onNodeWithTag("inner").performMouseInput { click(Offset(96f, 96f)) }
    waitForIdle()
    assertEquals(1, clicks)
    assertTrue("it was leaning", e.ry.x != 0f)
  }

  @Test fun theArrowsLeanAndEscapeBringsItBack() = runDesktopComposeUiTest(width = 300, height = 300) {
    val e = TiltEngine()
    setContent { TarkasTheme { TiltCard(Modifier.size(192.dp), keyboard = true, engine = e) { Box(Modifier.fillMaxSize()) } } }
    onNodeWithTag("tilt_card").requestFocus()
    onNodeWithTag("tilt_card").performKeyInput { pressKey(Key.DirectionRight) }
    mainClock.advanceTimeBy(1500); waitForIdle()
    assertTrue("ry ${e.ry.x}", e.ry.x > StackTuning.MaxRy * 0.8f)
    onNodeWithTag("tilt_card").performKeyInput { pressKey(Key.Escape) }
    mainClock.advanceTimeBy(3000); waitForIdle()
    assertEquals(0f, e.ry.x, 0.02f)
  }
}
