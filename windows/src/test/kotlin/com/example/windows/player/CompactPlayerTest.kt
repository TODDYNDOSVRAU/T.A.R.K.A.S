package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.geometry.Offset
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

/** The corner (D10): the 384 x 96 player, the 192 card above it only on the Home, static pill, a leaning card. */
@OptIn(ExperimentalTestApi::class)
class CompactPlayerTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..4).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f }

  private fun androidx.compose.ui.test.ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  @Test fun theNumbersAreTheOnesOfTheContract() {
    assertEquals(384, CompactMath.WidthDp)
    assertEquals(96, CompactMath.HeightDp)
    assertEquals(192, CompactMath.CardDp)
    assertEquals(12, CompactMath.GapDp)
    assertEquals(200, CompactMath.FadeMs)
  }

  @Test fun thePillIs384By96AndHasNoCardOutsideTheHome() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    val pill = bounds("compact_player")
    assertEquals(384f, pill.width, 0.5f)
    assertEquals(96f, pill.height, 0.5f)
    onNodeWithTag("art_card", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test fun onTheHomeTheCardSits192AbovePillWithA12Gap() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 324.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = true, reduced = false) } } }
    waitForIdle()
    val card = bounds("art_card"); val pill = bounds("compact_player")
    assertEquals(192f, card.width, 0.5f)
    assertEquals(192f, card.height, 0.5f)
    assertEquals(12f, pill.top - card.bottom, 0.5f)
    // The pill is a capsule: the card lines up with its content (the round cover), 24 dp in, not with the curved edge.
    assertEquals(24f, card.left - pill.left, 0.5f)
    assertEquals(card.left, bounds("compact_open").left, 0.5f)
    assertEquals(CompactMath.CardInsetDp.toFloat(), card.left - pill.left, 0.5f)
    // 192 + 12 + 96; the shell's 324 adds its own 24 dp margin below.
    assertEquals(300f, bounds("compact_corner").height, 0.5f)
  }

  @Test fun everythingInsideThePillStaysInsideIt() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    val pill = bounds("compact_player")
    for (tag in listOf("compact_title", "compact_artist", "compact_prev", "compact_play", "compact_next", "compact_progress", "compact_elapsed", "compact_total", "compact_volume")) {
      val r = bounds(tag)
      assertTrue("$tag $r outside $pill", r.left >= pill.left - 0.5f && r.right <= pill.right + 0.5f && r.top >= pill.top - 0.5f && r.bottom <= pill.bottom + 0.5f)
    }
    for (tag in listOf("compact_prev", "compact_play", "compact_next")) { val b = bounds(tag); assertEquals(48f, b.width, 0.5f); assertEquals(48f, b.height, 0.5f) }
  }

  @Test fun theButtonsDriveThePlayer() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[1], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    onNodeWithTag("compact_next").performClick(); waitForIdle()
    assertEquals("t3", f.controller.ui.value.current?.id)
    onNodeWithTag("compact_prev").performClick(); waitForIdle()
    assertEquals("t2", f.controller.ui.value.current?.id)
    onNodeWithTag("compact_play").performClick(); waitForIdle()
    assertFalse(f.controller.ui.value.current == null)
  }

  @Test fun theTitleAndTheCardOpenTheExpandedPlayer() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 324.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = true, reduced = false) } } }
    waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
    onNodeWithTag("compact_open", useUnmergedTree = true).performClick(); waitForIdle()
    assertTrue(ExpandedPlayer.open.value)
    ExpandedPlayer.close(); waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
    onNodeWithTag("art_card_cover", useUnmergedTree = true).performClick(); waitForIdle()
    assertTrue(ExpandedPlayer.open.value)
  }

  @Test fun withNothingPlayingTheButtonsAreOffAndNothingOpens() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture()
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 324.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = true, reduced = false) } } }
    waitForIdle()
    onNodeWithTag("compact_open", useUnmergedTree = true).performClick(); waitForIdle()
    onNodeWithTag("art_card_cover", useUnmergedTree = true).performClick(); waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
  }

  @Test fun theSeekAndVolumeLinesAreThere() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    assertEquals(96f, bounds("compact_volume").width, 0.5f)
    assertTrue(bounds("compact_progress").width > 96f)
    onNodeWithTag("compact_elapsed", useUnmergedTree = true).assertExists()
    onNodeWithTag("compact_total", useUnmergedTree = true).assertExists()
  }

  /** The pill is static (D10): it sits at one place whatever the pointer does, and the card tilt is the only 3D. */
  @Test fun thePillDoesNotMoveWhenThePointerLeansTheCard() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 324.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = true, reduced = false) } } }
    waitForIdle()
    val before = bounds("compact_player")
    onNodeWithTag("art_card").performMouseInput { moveTo(Offset(180f, 20f)) }
    mainClock.advanceTimeBy(400)
    waitForIdle()
    assertEquals(before, bounds("compact_player"))
    assertEquals(192f, bounds("art_card").width, 0.5f)
  }
}
