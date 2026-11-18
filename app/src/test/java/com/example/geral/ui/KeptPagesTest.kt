package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.KeptPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pages kept composed across switches: state stays, a hidden page is unreachable and paused, others are dropped. */
@RunWith(RobolectricTestRunner::class)
class KeptPagesTest {
  @get:Rule val rule = createComposeRule()

  private var target by mutableStateOf("A")
  private val lifecycles = mutableMapOf<String, Lifecycle>()
  private val disposed = mutableListOf<String>()

  private fun show() {
    rule.setContent {
      KeptPages(target = target, keep = { it != "S" }) { page ->
        var count by remember { mutableIntStateOf(0) }
        lifecycles[page] = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(page) { onDispose { disposed += page } }
        Box(Modifier.testTag("page-$page").clickable { count++ }) { Text("$count", Modifier.testTag("count-$page")) }
      }
    }
    rule.waitForIdle()
  }

  private fun go(page: String) {
    rule.runOnIdle { target = page }
    // Past the 150 ms fade-out and the 90 + 200 ms fade-in.
    rule.mainClock.advanceTimeBy(600)
    rule.waitForIdle()
  }

  @Test fun aKeptPageKeepsItsStateWhenLeftAndShownAgain() {
    show()
    rule.onNodeWithTag("page-A").performClick()
    rule.onNodeWithTag("page-A").performClick()
    go("B")
    go("A")
    rule.onNodeWithTag("count-A", useUnmergedTree = true).assertTextEquals("2")
    assertTrue("A kept: never disposed", "A" !in disposed)
  }

  @Test fun aHiddenPageIsOutOfTheTreeAndHeldAtCreated() {
    show()
    assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("A").currentState)
    go("B")
    assertFalse("hidden page reachable", rule.hasNodeWithTag("page-A"))
    assertTrue(rule.hasNodeWithTag("page-B"))
    assertEquals(Lifecycle.State.CREATED, lifecycles.getValue("A").currentState)
    assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("B").currentState)
    go("A")
    assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("A").currentState)
    assertEquals(Lifecycle.State.CREATED, lifecycles.getValue("B").currentState)
  }

  @Test fun aPageNotKeptIsDroppedAfterItsFadeAndBuiltAgainWhenReopened() {
    show()
    go("S")
    rule.onNodeWithTag("page-S").performClick()
    go("A")
    assertTrue("S dropped", "S" in disposed)
    go("S")
    rule.onNodeWithTag("count-S", useUnmergedTree = true).assertTextEquals("0")
  }
}
