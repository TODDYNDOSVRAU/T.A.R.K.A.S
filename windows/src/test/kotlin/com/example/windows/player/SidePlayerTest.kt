package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
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

/** The vertical player of the shell's side column: both widths, the 576 dp window height, and its buttons. */
@OptIn(ExperimentalTestApi::class)
class SidePlayerTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..4).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f }

  @Test fun coverShrinksWithTheHeightAndNeverGoesBelowItsFloor() {
    assertEquals(168, SidePlayerMath.coverDp(192f, 576f))
    assertEquals(360, SidePlayerMath.coverDp(384f, 576f))
    assertEquals(168, SidePlayerMath.coverDp(192f, 900f))
    assertEquals(120, SidePlayerMath.coverDp(192f, 312f))
    assertEquals(0, SidePlayerMath.coverDp(192f, 200f))
  }

  private fun androidx.compose.ui.test.ComposeUiTest.inside(w: Int, h: Int, tags: List<String>) {
    val box = Rect(0f, 0f, w.toFloat(), h.toFloat())
    for (tag in tags) {
      val r = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
      assertTrue("$tag $r is outside $box", r.left >= -0.5f && r.top >= -0.5f && r.right <= box.right + 0.5f && r.bottom <= box.bottom + 0.5f)
    }
  }

  @Test fun fitsBothWidthsAtTheSmallestWindowHeight() {
    for (w in listOf(192, 384)) runDesktopComposeUiTest(width = w, height = 576) {
      val f = Fixture()
      f.controller.play(list[0], list)
      setContent { TarkasTheme { Box(Modifier.size(w.dp, 576.dp), contentAlignment = Alignment.TopStart) { SidePlayerBody(f.hooks, f.assets, Modifier) } } }
      waitForIdle()
      inside(w, 576, listOf("side_cover", "side_title", "side_artist", "side_progress", "side_elapsed", "side_total", "side_prev", "side_play", "side_next"))
      val buttons = listOf("side_prev", "side_play", "side_next").map { onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
      for (b in buttons) { assertEquals(48f, b.width, 0.5f); assertEquals(48f, b.height, 0.5f) }
    }
  }

  @Test fun showsTheRealTrackAndTheButtonsDriveThePlayer() = runDesktopComposeUiTest(width = 192, height = 576) {
    val f = Fixture()
    f.controller.play(list[1], list)
    setContent { TarkasTheme { Box(Modifier.size(192.dp, 576.dp)) { SidePlayerBody(f.hooks, f.assets, Modifier) } } }
    waitForIdle()
    onNodeWithTag("side_title", useUnmergedTree = true).assertExists()
    onNodeWithTag("side_next").performClick(); waitForIdle()
    assertEquals("t3", f.controller.ui.value.current?.id)
    onNodeWithTag("side_prev").performClick(); waitForIdle()
    assertEquals("t2", f.controller.ui.value.current?.id)
    onNodeWithTag("side_play").performClick(); waitForIdle()
    assertFalse(f.controller.ui.value.current == null)
  }

  @Test fun theCoverOpensTheExpandedPlayer() = runDesktopComposeUiTest(width = 384, height = 576) {
    val f = Fixture()
    f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(384.dp, 576.dp)) { SidePlayerBody(f.hooks, f.assets, Modifier) } } }
    waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
    onNodeWithTag("side_cover", useUnmergedTree = true).performClick()
    waitForIdle()
    assertTrue(ExpandedPlayer.open.value)
  }

  @Test fun withNothingPlayingTheButtonsAreOffAndTheCoverDoesNotOpenAnything() = runDesktopComposeUiTest(width = 192, height = 576) {
    val f = Fixture()
    setContent { TarkasTheme { Box(Modifier.size(192.dp, 576.dp)) { SidePlayerBody(f.hooks, f.assets, Modifier) } } }
    waitForIdle()
    onNodeWithTag("side_cover", useUnmergedTree = true).performClick()
    waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
  }
}
