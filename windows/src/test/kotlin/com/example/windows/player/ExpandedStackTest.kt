package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** D6: the stack of queue cards lives in the expanded player, beside the queue list, and only when both fit. */
@OptIn(ExperimentalTestApi::class)
class ExpandedStackTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..5).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f; ExpandedPlayer.tab.value = PlayerPane.QUEUE }

  @Test fun theStackNeedsTwiceItsOwnWidth() {
    assertFalse(ExpandMath.stackFits(383f))
    assertTrue(ExpandMath.stackFits(384f))
    assertTrue(ExpandMath.stackFits(600f))
  }

  @Test fun aWideWindowShowsTheStackBesideTheQueue() = runDesktopComposeUiTest(width = 1600, height = 900) {
    val f = Fixture(); f.controller.play(list[0], list); ExpandedPlayer.show()
    setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.fillMaxSize()) { ExpandedPlayerPanel(f.hooks, f.assets) } } }
    mainClock.advanceTimeBy(600); waitForIdle()
    val box = onNodeWithTag("exp_queue_stack", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertEquals(192f, box.width, 0.5f)
    assertTrue("the stack is cut by the window: $box", box.right <= 1600f && box.bottom <= 900f)
  }

  @Test fun theLyricsTabDoesNotShowTheStack() = runDesktopComposeUiTest(width = 1600, height = 900) {
    val f = Fixture(); f.controller.play(list[0], list); ExpandedPlayer.show(); ExpandedPlayer.tab.value = PlayerPane.LYRICS
    setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.fillMaxSize()) { ExpandedPlayerPanel(f.hooks, f.assets) } } }
    mainClock.advanceTimeBy(600); waitForIdle()
    onNodeWithTag("exp_queue_stack", useUnmergedTree = true).assertDoesNotExist()
  }
}
